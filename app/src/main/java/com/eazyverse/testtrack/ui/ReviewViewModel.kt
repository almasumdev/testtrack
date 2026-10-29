package com.eazyverse.testtrack.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.AuthRepo
import com.eazyverse.testtrack.data.Device
import com.eazyverse.testtrack.data.DriveFile
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.ProofStore
import com.eazyverse.testtrack.data.ProofUpload
import com.eazyverse.testtrack.data.ReviewCaptureService
import com.eazyverse.testtrack.data.ReviewClock
import com.eazyverse.testtrack.data.ReviewKeep
import com.eazyverse.testtrack.data.Session
import com.eazyverse.testtrack.data.UsageRepo
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.viewmodel.LiveReload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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
        // leaves it standing, marked offline, when there is no connection. One that was given up or
        // ran out, which the feed no longer counts as this account's, is not: only opened, it is
        // taken again from the start.
        val left = post.myReviewId == null
        val saved = if (job == null) ReviewKeep.get(context, post.id)?.takeUnless { left && it.state in ENDED } else null
        if (saved != null) {
            job = ReviewJob(post, saved, leftReview = leftFor(context, saved.id))
            restore(context, saved.id)
        }
        run(onOffline = { if (job == null) failure = "TestTrack could not be reached. Check your connection." }) {
            val existing = post.myReviewId ?: job?.review?.takeUnless { left && it.state in ENDED }?.id ?: saved?.id
            val mine = if (existing == null) null else Api.myReviews().firstOrNull { it.id == existing }
            offline = false
            val fingerprint = if (mine == null) Device.fingerprint(context) else null
            if (mine == null && fingerprint == null) {
                failure = "This phone will not identify itself, so a review from it cannot count."
                return@run
            }
            // Not taken yet: the page is a preview until Install is tapped (take), and the server
            // hears nothing about it before then, so opening a request and backing out changes
            // nothing anywhere.
            if (mine == null) {
                job = ReviewJob(post, previewOf(post))
                return@run
            }
            val review = mine
            // Looked at before it is drawn, so the page opens on the step the phone is really at.
            // Drawing the server's last word first and then the phone's moved the page under the
            // reviewer a moment after it appeared. A refusal while looking (every slot taken while
            // they were installing) still opens the page, said, so it can be given up.
            val first = ReviewJob(post, review, leftReview = leftFor(context, review.id))
            val looked =
                try {
                    look(context, first, settle = false)
                } catch (e: ApiError) {
                    message = e.message
                    first
                }
            keep(looked)
            restore(context, review.id)
            adoptPending()
        }
    }

    /**
     * Install tapped: the review is taken on now, and then [then] runs (to its Play page). Already
     * taken, [then] runs at once. A refusal (full, or this phone ruled out) is said on the page.
     */
    fun take(
        context: Context,
        then: () -> Unit,
    ) {
        val current = job ?: return
        if (current.review.id.isNotEmpty()) {
            then()
            return
        }
        run(waits = true) {
            val fingerprint = Device.fingerprint(context)
            if (fingerprint == null) {
                failure = "This phone will not identify itself, so a review from it cannot count."
                return@run
            }
            val review = Api.claim(current.post.id, fingerprint)
            // Taken again after it was given up: same id, so its clock starts again.
            ReviewClock.restart(context, review.id)
            keep(ReviewJob(current.post, review, leftReview = leftFor(context, review.id)))
            restore(context, review.id)
            then()
        }
    }

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

    /** How many times [keep] has run, so a reload can tell it was overtaken. */
    internal var keeps = 0
        private set

    /** Show a job the server has just confirmed, and save it as the last word. */
    internal fun keep(next: ReviewJob) {
        keeps++
        // With the phone's own record of the first day of use, where it has one (ReviewClock).
        job = app?.let { next.copy(firstUsedHere = ReviewClock.firstUse(it, next.review.id)) } ?: next
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
        if (current.settled || current.review.id.isEmpty()) return current
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
            val use = ReviewClock.measure(context, current.post.packageName, review.id, review.createdAt, review.neededMs, settle)
            // A new day of use is news even when the total has not moved since the last report, and
            // so is a live review's hours passing: the server opens its last step on being asked.
            val due = review.state == INSTALLED && review.returnsAt?.let(::isPast) == true
            if (use.ms > review.usedMs || use.days > review.daysUsed || due) {
                review = Api.used(review.id, maxOf(use.ms, review.usedMs).coerceAtMost(MAX_USED_MS), use.days)
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
        run(waits = true, onOffline = { message = "You're offline. Your screenshot and note are saved; send them once you're back." }) {
            val token =
                AuthRepo.driveTokenOrNull(activity)
                    ?: throw ApiError(400, "drive", "Connect Google Drive to send your saved feedback.")
            val id = current.review.id
            val ref =
                ProofStore.uploaded(context, id)
                    ?: (DRIVE_PREFIX + ProofUpload.send(activity, id, current.post.packageName, proof))
                        .also { ProofStore.markUploaded(context, id, it) }
            keep(current.copy(review = Api.submit(id, ref, note, driveToken = token)))
            offline = false
            dropProof(current.review.id)
        }
    }

    /** It would not install: the developer is told why, the slot goes back, nothing counts against you. */
    fun report(
        reason: String,
        note: String,
        onDone: () -> Unit,
    ) {
        val current = job ?: return
        run(waits = true) {
            keep(current.copy(review = Api.reportPost(current.review.postId, reason, note)))
            dropProof(current.review.id)
            message = "Thanks. The developer is told why, and nothing counts against you."
            onDone()
        }
    }

    /** Give the slot back so somebody else can take it. Whatever was kept for it goes too. */
    fun abandon(onDone: () -> Unit) {
        val current = job ?: return
        run(waits = true) {
            keep(current.copy(review = Api.abandon(current.review.id)))
            dropProof(current.review.id)
            onDone()
        }
    }

    /** The review is sent or over: its kept screenshot and note go. */
    private fun dropProof(id: String) {
        app?.let { ProofStore.forget(it, id) }
        proof = null
        note = ""
    }

    /**
     * One place every call goes through, so no screen has to think about failure.
     *
     * The API's message is written for a person and is shown as it arrives. Nothing here reads it;
     * where a particular refusal needs particular handling, it matches on the code instead.
     */
    private fun run(
        /** Something the reviewer asked for, which waits for a refresh under way rather than being dropped. */
        waits: Boolean = false,
        onOffline: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        if (busy && !waits) return
        viewModelScope.launch {
            while (busy) snapshotFlow { busy }.first { !it }
            busy = true
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                if (job == null) failure = e.message else message = e.message
            } catch (_: IOException) {
                // Not an error to show: the page carries on from what it saved, marked offline.
                offline = true
                onOffline()
            } catch (e: Exception) {
                // Reached us, but something on this side could not be read: not a lost connection.
                Log.w("Review", "step failed", e)
                val said = "Something went wrong. Try again in a moment."
                if (job == null) failure = said else message = said
            } finally {
                busy = false
            }
        }
    }

    private companion object {
        const val PLAY = "com.android.vending"
        const val CLAIMED = "claimed"
        const val INSTALLED = "installed"
        const val USED = "used"

        /** Given up or run out: over, whatever the phone kept of it. */
        val ENDED = setOf("abandoned", "expired")

        /** The most the server takes in one report: a day. A lost pause event can read more. */
        const val MAX_USED_MS = 86_400_000L
    }
}

/** A proof kept in the reviewer's own Drive is sent as this followed by the file's id. */
const val DRIVE_PREFIX = "drive:"

/**
 * Where a proof's picture is fetched from. Drive, shared by link, for every proof sent now; our
 * own server, behind the session, for older ones uploaded there; a file for one still on the phone.
 */
fun proofSource(ref: String): String =
    when {
        ref.startsWith(DRIVE_PREFIX) -> DriveFile(ref.removePrefix(DRIVE_PREFIX), "").thumbUrl
        ref.startsWith("file:") -> ref
        else -> Api.proofUrl(ref)
    }
