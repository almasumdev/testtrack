package com.eazyverse.testtrack.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.Device
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.ProofStore
import com.eazyverse.testtrack.data.ReviewCaptureService
import com.eazyverse.testtrack.data.ReviewClock
import com.eazyverse.testtrack.data.ReviewKeep
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.claimTimed
import com.eazyverse.testtrack.viewmodel.LiveReload
import com.eazyverse.testtrack.viewmodel.ProofGate
import com.eazyverse.testtrack.viewmodel.keepEdited
import com.eazyverse.testtrack.viewmodel.look
import com.eazyverse.testtrack.viewmodel.replyWithPicture
import com.eazyverse.testtrack.viewmodel.requirePlayInstall
import com.eazyverse.testtrack.viewmodel.sendProof
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * One guided review, run against the API.
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

    /**
     * The last call could not reach TestTrack. The page goes on showing the last saved answer
     * (see [ReviewKeep]) and says it is offline, rather than an error over a page that still works.
     */
    var offline by mutableStateOf(false)
        internal set

    /** Why there is nothing to show at all: no saved review and no way to fetch one. */
    var failure by mutableStateOf<String?>(null)
        private set

    /**
     * Whether TestTrack may read usage time. Asked for here, at the step that needs it, rather than
     * at sign-up, and read again on every return so switching it on moves the page straight on.
     */
    var usageAccess by mutableStateOf(true)
        internal set

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
    private var pending: File? by mutableStateOf(null)

    /** Writing and sending the kept screenshot take turns here. See [ProofGate]. */
    private val gate = ProofGate()

    /** A new version of the screenshot is being kept, or one is waiting to be: Send waits for it. */
    val replacing: Boolean get() = gate.replacing || (pending != null && app != null && job?.review?.id?.isNotEmpty() == true)

    /**
     * [job] as the page draws it. Coming back from the screenshot button, the picture is still
     * being kept for a moment, and until it is the job reads as the Play step: the page would flash
     * "Open Google Play" before the proof page. While it is kept the page stays on the proof step
     * (its picture box says Saving picture); if it could not be kept the page returns to the Play step.
     */
    val pageJob: ReviewJob?
        get() {
            val current = job ?: return null
            val keeping = replacing && !current.leftReview && !current.settled && ReviewStep.of(current.review) == ReviewStep.LEAVE
            return if (keeping) current.copy(leftReview = true) else current
        }

    /** The screenshot is going to TestTrack with the review: the page says so over the picture. */
    var sending by mutableStateOf(false)
        private set

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

    /**
     * Move a capture from the cache into the review's keeping. One that replaces a picture kept
     * before (one with details hidden, say) is said, so nobody sends it thinking it is the old one.
     */
    private suspend fun adoptPending() {
        val file = pending ?: return
        val context = app ?: return
        val id = job?.review?.id ?: return
        pending = null
        gate.replace({ showProof(context, id) }) {
            val before = ProofStore.proof(context, id)
            val kept = withContext(Dispatchers.IO) { ProofStore.adopt(context, id, file) }
            if (kept != null && before != null) Snacks.info("Your new screenshot replaced the one before. Hide anything personal again.")
        }
    }

    /**
     * Puts what [ProofStore] keeps for [id] on the page. Called inside the gate, in the same turn
     * as the write, so the page shows the stored picture and never one it replaced.
     */
    private fun showProof(
        context: Context,
        id: String,
    ) {
        val kept = ProofStore.proof(context, id)
        proof = kept?.let(Uri::fromFile)
        if (kept == null) return
        // Picking proof opens the private-feedback submission step; acceptance is server-owned.
        ProofStore.markLeft(context, id)
        job = job?.copy(leftReview = true)
    }

    /**
     * A screenshot from the editor kept as the review's picture (see [keepEdited]). The editor
     * waits for this before it closes. Run in the page's scope, so it finishes if the editor goes.
     */
    suspend fun keepPicked(
        context: Context,
        uri: Uri,
    ) {
        val id = job?.review?.id ?: error("No review to keep it for")
        val appContext = context.applicationContext
        val open = { job?.review?.id == id && job?.settled == false }
        viewModelScope.async { gate.keepEdited(appContext, id, uri, open) { showProof(appContext, id) } }.await()
    }

    /** The × on the picture: the screenshot goes, the note and the step stay. */
    fun removeProof() {
        val id = job?.review?.id ?: return
        val context = app ?: return
        viewModelScope.launch {
            gate.replace({ showProof(context, id) }) { withContext(Dispatchers.IO) { ProofStore.dropPicture(context, id) } }
        }
    }

    fun editNote(text: String) {
        note = text
        val id = job?.review?.id ?: return
        app?.let { ProofStore.saveNote(it, id, text) }
    }

    /** True once the package is present but Play did not put it there. */
    var sideloaded by mutableStateOf(false)
        internal set

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
            requirePlayInstall(context)
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
                val here = InstalledApps.info(context, post.packageName)
                job = ReviewJob(post, previewOf(post), onPhone = here.fromPlay)
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
                    Snacks.error(e.message)
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
            val review = Api.claimTimed(current.post.id, fingerprint, FeedRules.LIVE_TIMING)
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
        proof = ProofStore.proof(context, id)?.let(Uri::fromFile)
        note = ProofStore.note(context, id)
        job = job?.let { it.copy(noBox = it.noBox || ProofStore.noBox(context, id)) }
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

    /** Play showed no review box: on to the proof step, to send it here instead, for half. */
    fun markNoBox(): Boolean {
        val context = app ?: return false
        if (!requirePlayInstall(context)) return false
        val current = job?.takeIf { it.live && it.step == ReviewStep.LEAVE } ?: return false
        ProofStore.markNoBox(context, current.review.id)
        job = current.copy(leftReview = true, noBox = true)
        return true
    }

    /** Show a job the server has just confirmed, and save it as the last word. */
    internal fun keep(next: ReviewJob) {
        keeps++
        // With the phone's own record of the first day of use, where it has one (ReviewClock).
        val marked = next.copy(noBox = next.noBox || next.review.noBox || app?.let { ProofStore.noBox(it, next.review.id) } == true)
        job = app?.let { marked.copy(firstUsedHere = ReviewClock.firstUse(it, next.review.id)) } ?: marked
        app?.let { ReviewKeep.put(it, next.post.id, next.review) }
    }

    /**
     * Look at the phone and tell the server what is there.
     *
     * Called every time the screen resumes, which is what moves the stepper on when somebody comes
     * back from the app they are reviewing. Nothing drags them out of it and nothing has to be
     * pressed to record the time. [waits] queues it behind a look already under way rather than
     * dropping it: an install that lands during the resume look must still be looked at.
     */
    fun refresh(
        context: Context,
        waits: Boolean = false,
    ) {
        if (job?.settled != false) return
        run(waits = waits) {
            val current = job?.takeUnless { it.settled } ?: return@run
            val next = look(context, current, settle = true)
            // A screenshot may have landed while this was looking; keep what it said.
            keep(next.copy(leftReview = next.leftReview || job?.leftReview == true))
        }
    }

    /** On to the proof: the reviewer has been to Google Play for it, or will screenshot it themselves. */
    fun markLeft() {
        if (!requirePlayInstall(app ?: return)) return
        val current = job ?: return
        app?.let { ProofStore.markLeft(it, current.review.id) }
        job = current.copy(leftReview = true)
    }

    /**
     * The evidence, and the end of the reviewer's part.
     *
     * Two steps, in this order, because the second needs what the first gives: the screenshot
     * goes to TestTrack under an id, and the review is submitted carrying it. Uploading first
     * means a submit that fails leaves a spare picture on the server rather than a review
     * claiming proof that was never stored, and the id is kept so trying again does not send
     * it twice.
     */
    fun submit(
        activity: Activity,
        note: String,
    ) {
        val current = job ?: return
        val id = current.review.id
        // No message on success: the page itself turns into the sent screen.
        run(waits = true, onOffline = {
            Snacks.show(Snack("You're offline. Your screenshot and note are saved.", SnackTone.OFFLINE, sticky = true, key = OFFLINE))
        }) {
            // A second tap queued behind the first one: by now the review is sent or another one is open.
            if (job?.review?.id != id || job?.settled != false) return@run
            if (!requirePlayInstall(activity)) return@run
            // The picture is the one kept at this moment, read inside the gate: never one a
            // version with details hidden is still replacing, whatever the page showed.
            sending = true
            val sent =
                try {
                    gate.send({ ProofStore.proof(activity, id) }) { sendProof(activity, current, Uri.fromFile(it), note) }
                } finally {
                    sending = false
                }
            if (sent == null) {
                proof = null
                Snacks.error("Add your screenshot again.")
                return@run
            }
            keep(current.copy(review = sent))
            Snacks.dismiss(OFFLINE)
            offline = false
            dropProof(id)
        }
    }

    /** The reviewer's one answer to a refusal: words, a picture, or both. */
    fun reply(
        activity: Activity,
        text: String,
        picture: Uri?,
    ) {
        val current = job ?: return
        run(waits = true) {
            keep(current.copy(review = replyWithPicture(activity, current, text, picture)))
            Snacks.done("Sent. The developer and the members will see your answer.")
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
            Snacks.done("Thanks. The developer is told why. Nothing counts against you.")
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
                if (job == null) failure = e.message else Snacks.error(e.message)
            } catch (_: IOException) {
                // Not an error to show: the page carries on from what it saved, marked offline.
                offline = true
                onOffline()
            } catch (e: Exception) {
                // Reached us, but something on this side could not be read: not a lost connection.
                Log.w("Review", "step failed", e)
                val said = "Something went wrong. Try again in a moment."
                if (job == null) failure = said else Snacks.error(said)
            } finally {
                busy = false
            }
        }
    }

    // Offline is about this review: it goes with the page.
    override fun onCleared() {
        Snacks.dismiss(OFFLINE)
        super.onCleared()
    }

    private companion object {
        /** The offline snack's key, taken away once a send goes or the page closes. */
        const val OFFLINE = "review-offline"

        /** Given up or run out: over, whatever the phone kept of it. */
        val ENDED = setOf("abandoned", "expired")
    }
}
