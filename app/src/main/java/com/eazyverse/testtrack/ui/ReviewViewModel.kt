package com.eazyverse.testtrack.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.AuthRepo
import com.eazyverse.testtrack.data.Device
import com.eazyverse.testtrack.data.DriveFile
import com.eazyverse.testtrack.data.DriveRepo
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.ProofStore
import com.eazyverse.testtrack.data.ReviewCaptureService
import com.eazyverse.testtrack.data.ReviewKeep
import com.eazyverse.testtrack.data.Session
import com.eazyverse.testtrack.data.UploadResult
import com.eazyverse.testtrack.data.UsageRepo
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.ApiReview
import com.eazyverse.testtrack.viewmodel.LiveReload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/**
 * One guided review, driven against the API.
 *
 * The phone reports only what it can actually see: whether the package is there, who installed it,
 * and how long it has been in the foreground. What that adds up to is the server's decision, every
 * time. A client that decides for itself when it has done enough is a client somebody will edit.
 */
class ReviewViewModel : ViewModel() {
    var job by mutableStateOf<ReviewJob?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    /**
     * The last call could not reach TestTrack. The page goes on showing the last saved answer
     * (see [ReviewKeep]) and says it is offline, rather than an error over a page that still works.
     */
    var offline by mutableStateOf(false)
        private set

    /** Why there is nothing to show at all: no saved review and no way to fetch one. */
    var failure by mutableStateOf<String?>(null)
        private set

    /**
     * Whether TestTrack may read usage time. Asked for here, at the step that needs it, rather than
     * at sign-up, and read again on every return so switching it on moves the page straight on.
     */
    var usageAccess by mutableStateOf(true)
        private set

    /** Open the Usage access list so the reviewer can switch TestTrack on. */
    fun openUsageSettings(context: Context) = UsageRepo.openSettings(context)

    /**
     * The screenshot for step four, however it came, kept on the phone until it is sent. See
     * [ProofStore]: leaving the page and coming back finds it, and the note, where they were.
     */
    var proof by mutableStateOf<Uri?>(null)
        private set

    /** The optional note that goes with the proof, kept as it is typed. */
    var note by mutableStateOf("")
        private set

    /** The application, for keeping files from a collector that has no screen to ask. */
    private var app: Context? = null

    /** A capture that landed before the review was known; kept once it is. */
    private var pending: File? = null

    init {
        viewModelScope.launch {
            ReviewCaptureService.proof.collect { file ->
                if (file != null) {
                    ReviewCaptureService.takeProof()
                    pending = file
                    adoptPending()
                }
            }
        }
    }

    /** Move a capture from the cache into the review's keeping. */
    private suspend fun adoptPending() {
        val file = pending ?: return
        val context = app ?: return
        val id = job?.review?.id ?: return
        pending = null
        val kept = withContext(Dispatchers.IO) { ProofStore.adopt(context, id, file) } ?: return
        showProof(context, id, kept)
    }

    private fun showProof(
        context: Context,
        id: String,
        file: File,
    ) {
        proof = Uri.fromFile(file)
        // Picking proof opens the private-feedback submission step; acceptance is server-owned.
        ProofStore.markLeft(context, id)
        job = job?.copy(leftReview = true)
    }

    /** A screenshot chosen from the gallery: copied in, so it outlasts the picker's grant. */
    fun keepPicked(
        context: Context,
        uri: Uri,
    ) {
        val id = job?.review?.id ?: return
        val appContext = context.applicationContext
        viewModelScope.launch {
            val kept = withContext(Dispatchers.IO) { ProofStore.keep(appContext, id, uri) }
            if (kept == null) {
                message = "That image could not be read. Try another."
            } else {
                showProof(appContext, id, kept)
            }
        }
    }

    fun editNote(text: String) {
        note = text
        val id = job?.review?.id ?: return
        app?.let { ProofStore.saveNote(it, id, text) }
    }

    /** Whether the one-time guide has been read. */
    fun guideSeen(context: Context) = prefs(context).getBoolean(GUIDE_SEEN, false)

    fun markGuideSeen(context: Context) = prefs(context).edit().putBoolean(GUIDE_SEEN, true).apply()

    /** The camera button floats over the tested app, requiring "Display over other apps". */
    fun canFloat(context: Context) = Settings.canDrawOverlays(context)

    fun openOverlaySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * Put the camera button up with the consent just given, then open the app's Play page, where
     * the review or the private feedback is left. Proof opens on the way out, so somebody who comes
     * back without pressing it can still pick a screenshot.
     */
    fun startCapture(
        context: Context,
        code: Int,
        data: Intent,
        pkg: String,
    ) {
        ReviewCaptureService.start(context, code, data)
        openPlay(context, pkg)
        markLeft()
    }

    /** Remove a waiting capture button when returning without a picture. */
    fun dropStrayButton(context: Context) {
        if (ReviewCaptureService.running.value == ReviewCaptureService.State.WAITING) {
            ReviewCaptureService.cancel(context)
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("review_prefs", Context.MODE_PRIVATE)

    /** True once the package is present but Play did not put it there. */
    var sideloaded by mutableStateOf(false)
        private set

    fun clearMessage() {
        message = null
    }

    /** Take the post on, or pick up wherever this account already was on it. */
    fun start(
        context: Context,
        post: ApiPost,
    ) {
        app = context.applicationContext
        failure = null
        // The last saved answer, drawn at once. The fetch below then brings it up to date, or
        // leaves it standing, marked offline, when there is no connection.
        val saved = if (job == null) ReviewKeep.get(context, post.id) else null
        if (saved != null) {
            job = ReviewJob(post, saved, leftReview = leftFor(context, saved.id))
            restore(context, saved.id)
        }
        run(onOffline = { if (job == null) failure = "TestTrack could not be reached. Check your connection." }) {
            val existing = post.myReviewId ?: saved?.id
            val mine = if (existing == null) null else Api.myReviews().firstOrNull { it.id == existing }
            offline = false
            val fingerprint = if (mine == null) Device.fingerprint(context) else null
            if (mine == null && fingerprint == null) {
                failure = "This phone will not identify itself, so a review from it cannot count."
                return@run
            }
            val review = mine ?: Api.claim(post.id, fingerprint!!)
            // Looked at before it is drawn, so the page opens on the step the phone is really at.
            // Drawing the server's last word first and then the phone's moved the page under the
            // reviewer a moment after it appeared.
            keep(look(context, ReviewJob(post, review, leftReview = leftFor(context, review.id)), settle = false))
            restore(context, review.id)
            adoptPending()
        }
    }

    /** Try the first load again, from the page shown when it failed. */
    fun retry(
        context: Context,
        post: ApiPost,
    ) = start(context, post)

    /** Step four's own saved work for this review: the screenshot and the note. */
    private fun restore(
        context: Context,
        id: String,
    ) {
        ProofStore.proof(context, id)?.let { proof = Uri.fromFile(it) }
        note = ProofStore.note(context, id)
    }

    private fun leftFor(
        context: Context,
        id: String,
    ) = ProofStore.proof(context, id) != null || ProofStore.left(context, id)

    // While the page shows: the review's own news and its post's changes, read back into the page.
    private val live =
        LiveReload("review", viewModelScope) {
            (it.channel.startsWith("user:") && it.type.startsWith("review.")) || (it.type == "post.changed" && it.ref == job?.post?.id)
        }

    fun startLive() = live.start { reloadReview() }

    internal suspend fun fetchReview(id: String) = runCatching { Api.myReviews().firstOrNull { it.id == id } }.getOrNull()

    fun stopLive() = live.stop()

    /** Show a job the server has just confirmed, and save it as the last word. */
    internal fun keep(next: ReviewJob) {
        job = next
        app?.let { ReviewKeep.put(it, next.post.id, next.review) }
    }

    /**
     * Look at the phone and tell the server what is there.
     *
     * Called every time the screen resumes, which is what moves the stepper on when somebody comes
     * back from the app they are reviewing. Nothing drags them out of it and nothing has to be
     * pressed to record the time.
     */
    fun refresh(context: Context) {
        val current = job ?: return
        if (current.settled) return
        run {
            val next = look(context, current, settle = true)
            // A screenshot may have landed while this was looking; keep what it said.
            keep(next.copy(leftReview = next.leftReview || job?.leftReview == true))
        }
    }

    /**
     * What the phone sees for [current], reported to the server. [settle] waits a moment for usage
     * events still being written, which matters on a return from the app and not on first open.
     */
    private suspend fun look(
        context: Context,
        current: ReviewJob,
        settle: Boolean,
    ): ReviewJob {
        if (current.settled) return current
        usageAccess = UsageRepo.hasAccess(context)
        val info = InstalledApps.info(context, current.post.packageName)

        // Present, but sideloaded. Worth saying rather than silently not advancing, because the
        // reviewer can see the app on their own home screen and would otherwise think the check
        // was broken.
        sideloaded = info.installed && info.installer != PLAY

        var review = current.review
        if (review.state == CLAIMED && info.installed && !sideloaded) {
            review = Api.installed(review.id)
            offline = false
        }
        if (review.state == INSTALLED || review.state == USED) {
            val used =
                UsageRepo.measure(
                    context,
                    current.post.packageName,
                    windowStart(context, review),
                    needMs = review.neededMs,
                    source = "review",
                    settle = settle,
                )
            if (used > review.usedMs) {
                review = Api.used(review.id, used)
                offline = false
            }
        }
        return current.copy(review = review)
    }

    /** On to the proof: the reviewer has been to Google Play for it, or will screenshot it themselves. */
    fun markLeft() {
        val current = job ?: return
        app?.let { ProofStore.markLeft(it, current.review.id) }
        job = current.copy(leftReview = true)
    }

    /** Whether the reviewer's Drive can take the screenshot. */
    val driveConnected: Boolean get() = Session.driveConnected

    /** Whether Drive is connected, asked of Google without showing anything. */
    fun checkDrive(activity: Activity) {
        viewModelScope.launch { Session.updateDriveConnected(AuthRepo.hasDriveAccess(activity)) }
    }

    /**
     * The evidence, and the end of the reviewer's part.
     *
     * Two steps, in this order, because the second needs what the first gives back: the
     * screenshot goes to the reviewer's Drive and answers with its id, and the review is
     * submitted carrying it. Uploading first means a submit that fails leaves a spare file in
     * their Drive rather than a review claiming proof that was never stored, and the id is kept
     * so trying again does not upload it twice.
     */
    fun submit(
        activity: Activity,
        proof: Uri,
        note: String,
    ) {
        val current = job ?: return
        val context = activity.applicationContext
        // No message on success: the page itself turns into the sent screen.
        run(onOffline = { message = "You're offline. Your screenshot and note are saved; send them once you're back." }) {
            val token =
                AuthRepo.driveTokenOrNull(activity)
                    ?: throw ApiError(400, "drive", "Connect Google Drive to send your saved feedback.")
            val id = current.review.id
            val ref =
                ProofStore.uploaded(context, id) ?: toDrive(activity, current, proof).also { ProofStore.markUploaded(context, id, it) }
            keep(current.copy(review = Api.submit(id, ref, note, driveToken = token)))
            offline = false
            ProofStore.forget(context, current.review.id)
            this.proof = null
            this.note = ""
        }
    }

    /**
     * Put the screenshot in the reviewer's own Drive, shared by link, and return the reference
     * the review is sent with. The picture belongs to its uploader; only its reference and a
     * transient metadata-verification grant reach our server. Crew visits send usage records.
     *
     * Without a Drive grant nothing is sent. The page shows how to connect, and the screenshot
     * and note stay kept.
     */
    private suspend fun toDrive(
        activity: Activity,
        current: ReviewJob,
        uri: Uri,
    ): String {
        val context = activity.applicationContext
        val token = AuthRepo.driveTokenOrNull(activity)
        if (token == null) {
            Session.updateDriveConnected(false)
            throw ApiError(0, "drive", "Connect Google Drive to send. Your screenshot is saved there and shared with the owner by link.")
        }
        val mime = context.contentResolver.getType(uri) ?: ProofStore.mime(context, current.review.id)
        val name = "review_${current.post.packageName}_${current.review.id.take(8)}.${if (mime == "image/png") "png" else "jpg"}"
        val read = {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw ApiError(400, "unreadable", "That image could not be read.")
        }
        val fileId =
            ProofStore.reserved(context, current.review.id)
                ?: DriveRepo.reserveId(token).also { ProofStore.reserve(context, current.review.id, it) }
        return when (val result = DriveRepo.upload(token, name, mime, fileId = fileId, bytes = read)) {
            is UploadResult.Ok -> DRIVE_PREFIX + result.file.id
            is UploadResult.Failed -> throw ApiError(0, "drive", result.reason)
        }
    }

    /** Give the slot back so somebody else can take it. Whatever was kept for it goes too. */
    fun abandon(onDone: () -> Unit) {
        val current = job ?: return
        run {
            keep(current.copy(review = Api.abandon(current.review.id)))
            app?.let { ProofStore.forget(it, current.review.id) }
            proof = null
            note = ""
            onDone()
        }
    }

    /**
     * When the clock started, according to the server.
     *
     * Measured from the moment the job was taken on rather than from whenever this screen
     * happened to open, so closing the app and coming back tomorrow does not reset the count.
     * Falls back to an hour ago if the timestamp cannot be read, which under-reports rather than
     * over-reports: the server only ever takes the larger figure, so a short answer costs nothing
     * but a long one would be a free pass.
     */
    private fun claimedAt(review: ApiReview): Long =
        runCatching { Instant.parse(review.createdAt).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis() - 3_600_000L)

    /**
     * Where the usage window opens: the server's claim time or the moment this phone first saw the
     * review, whichever is earlier on this phone's clock.
     *
     * The claim time alone is the server's clock. On a phone running a few minutes behind it, the
     * window opened in the phone's future and every reading came back zero until the clocks
     * caught up, which on some phones they never did. The first sighting is the phone's own clock,
     * remembered across restarts, so the window can never open later than the reviewer started.
     */
    private fun windowStart(
        context: Context,
        review: ApiReview,
    ): Long {
        val prefs = context.getSharedPreferences("review_clock", Context.MODE_PRIVATE)
        val key = "first_seen_${review.id}"
        val seen =
            prefs.getLong(key, 0L).takeIf { it > 0L }
                ?: System.currentTimeMillis().also { prefs.edit().putLong(key, it).apply() }
        return minOf(claimedAt(review), seen)
    }

    /**
     * One place every call goes through, so no screen has to think about failure.
     *
     * The API's message is written for a person and is shown as it arrives. Nothing here reads it;
     * where a particular refusal needs particular handling, it matches on the code instead.
     */
    private fun run(
        onOffline: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiError) {
                if (job == null) failure = e.message else message = e.message
            } catch (_: Exception) {
                // Not an error to show: the page carries on from what it saved, marked offline.
                offline = true
                onOffline()
            } finally {
                busy = false
            }
        }
    }

    private companion object {
        const val PLAY = "com.android.vending"
        const val GUIDE_SEEN = "guide_seen"
        const val CLAIMED = "claimed"
        const val INSTALLED = "installed"
        const val USED = "used"
    }
}

/** A proof kept in the reviewer's own Drive is sent as this followed by the file's id. */
const val DRIVE_PREFIX = "drive:"

/**
 * Where a proof's picture is fetched from. Drive, shared by link, for every proof sent now; our
 * own server, behind the session, for the older ones that were uploaded there.
 */
fun proofSource(ref: String): String =
    if (ref.startsWith(DRIVE_PREFIX)) DriveFile(ref.removePrefix(DRIVE_PREFIX), "").thumbUrl else Api.proofUrl(ref)
