package com.eazyverse.testtrack.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.Device
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.UsageRepo
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.ApiReview
import kotlinx.coroutines.launch
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
     * Whether TestTrack may read usage time. Asked for here, at the step that needs it, rather than
     * at sign-up, and read again on every return so switching it on moves the page straight on.
     */
    var usageAccess by mutableStateOf(true)
        private set

    /** Open the Usage access list so the reviewer can switch TestTrack on. */
    fun openUsageSettings(context: Context) = UsageRepo.openSettings(context)

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
    ) = run {
        val existing = post.myReviewId
        val mine = if (existing == null) null else Api.myReviews().firstOrNull { it.id == existing }
        if (mine != null) {
            job = ReviewJob(post, mine)
            return@run
        }

        val fingerprint = Device.fingerprint(context)
        if (fingerprint == null) {
            message = "This phone will not identify itself, so a review from it cannot count."
            return@run
        }
        job = ReviewJob(post, Api.claim(post.id, fingerprint))
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
        usageAccess = UsageRepo.hasAccess(context)

        run {
            val info = InstalledApps.info(context, current.post.packageName)

            // Present, but sideloaded. Worth saying rather than silently not advancing, because
            // the reviewer can see the app on their own home screen and would otherwise think
            // the check was broken.
            sideloaded = info.installed && info.installer != PLAY

            var review = current.review
            if (review.state == CLAIMED && info.installed && !sideloaded) {
                review = Api.installed(review.id)
            }
            if (review.state == INSTALLED || review.state == USED) {
                val used = UsageRepo.foregroundMsSince(context, current.post.packageName, windowStart(context, review))
                if (used > review.usedMs) review = Api.used(review.id, used)
            }
            job = current.copy(review = review)
        }
    }

    /**
     * Note that the reviewer has been handed to the Play review page.
     *
     * Which is as much as the app can ever know about a Play review, and all it is used for: the
     * proof step opens. What gets judged is the screenshot.
     */
    fun markLeft() {
        job = job?.copy(leftReview = true)
    }

    /**
     * The evidence, and the end of the reviewer's part.
     *
     * Two calls, in this order, because the second needs what the first gives back: the image
     * goes up and answers with a reference, and the review is submitted carrying it. Uploading
     * first also means a submit that fails leaves an orphan file rather than a review claiming
     * proof that was never stored.
     */
    fun submit(
        context: Context,
        proof: Uri?,
        written: String,
    ) {
        val current = job ?: return
        run {
            val ref = if (proof == null) "" else upload(context, proof)
            job = current.copy(review = Api.submit(current.review.id, ref, written))
            message = "Sent. The developer has two days, then it approves itself."
        }
    }

    /**
     * Read the chosen image and send it.
     *
     * Read through the resolver rather than as a file path: what the picker hands back is a grant
     * to one item, not a location on disk, and on a modern Android there is no path behind it to
     * open. The type comes from the resolver too, since the server checks it and a guess from the
     * file extension is a guess.
     */
    private suspend fun upload(
        context: Context,
        uri: Uri,
    ): String {
        val resolver = context.contentResolver
        val bytes =
            resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw ApiError(400, "unreadable", "That image could not be read.")
        return Api.upload(bytes, resolver.getType(uri) ?: "image/jpeg").id
    }

    /** Give the slot back so somebody else can take it. */
    fun abandon(onDone: () -> Unit) {
        val current = job ?: return
        run {
            Api.abandon(current.review.id)
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
    private fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiError) {
                message = e.message
            } catch (_: Exception) {
                message = "We could not reach TestTrack. Check your connection."
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
    }
}
