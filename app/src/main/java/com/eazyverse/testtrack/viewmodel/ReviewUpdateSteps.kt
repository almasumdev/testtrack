package com.eazyverse.testtrack.viewmodel

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.ProofStore
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.ui.ReviewJob
import com.eazyverse.testtrack.ui.ReviewViewModel
import com.eazyverse.testtrack.ui.Snack
import com.eazyverse.testtrack.ui.SnackTone
import com.eazyverse.testtrack.ui.Snacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * The reviewer's one swapped screenshot, for a review already sent. Beside its view model, as
 * ReviewCaptureSteps is, so the class itself stays the part that talks to the server.
 */

/**
 * Open the Send an update page on a draft of its own: what was kept before belongs to the first
 * submission, so it goes (with the id it was uploaded under), unless the draft is already an update's.
 */
fun ReviewViewModel.openUpdate() {
    val id = job?.takeIf { it.canUpdate }?.review?.id ?: return
    val context = app ?: return
    viewModelScope.launch {
        gate.replace({
            proof = ProofStore.proof(context, id)?.let(Uri::fromFile)
            note = ProofStore.note(context, id)
        }) { withContext(Dispatchers.IO) { ProofStore.beginUpdate(context, id) } }
        if (job?.canUpdate == true) updateOpen = true
    }
}

/** Back to the waiting page ("Keep what I sent"): what was kept for the update goes. */
fun ReviewViewModel.cancelUpdate(context: Context) {
    val id = job?.review?.id ?: return
    closeUpdate(context.applicationContext, id)
}

/**
 * Close the update page now and clear its draft through the proof gate, so a picture still being
 * written finishes first and is then deleted with the rest, never left behind. A write queued after
 * this is turned away (see [ReviewViewModel.draftOpen]).
 */
private fun ReviewViewModel.closeUpdate(
    context: Context,
    id: String,
) {
    updateOpen = false
    viewModelScope.launch {
        gate.replace({
            proof = null
            note = ""
        }) { withContext(Dispatchers.IO) { ProofStore.forget(context, id) } }
    }
}

/**
 * The page the server has just confirmed no longer takes an update (sent, or the owner answered
 * first): the update page closes and what was kept goes. One that closes with no update sent says why.
 */
internal fun ReviewViewModel.leaveUpdateIfDone(next: ReviewJob) {
    if (!updateOpen || next.canUpdate) return
    app?.let { closeUpdate(it, next.review.id) } ?: run { updateOpen = false }
    if (next.review.proofUpdatedAt == null) Snacks.info(ANSWERED_FIRST)
}

private const val ANSWERED_FIRST = "The developer answered before your update went."

/**
 * Send the new screenshot kept at this moment, with the note. The picture is the one kept inside
 * the gate, never one a version with details hidden is still replacing. The owner having answered
 * meanwhile (409) closes the page at once: the server will not take this update, so nothing is
 * kept and nothing is retried, and the review is read back when it can be.
 */
fun ReviewViewModel.sendUpdate(
    activity: Activity,
    note: String,
) {
    val current = job ?: return
    val id = current.review.id
    run(waits = true, onOffline = {
        val saved = "You're offline. Your screenshot and note are saved."
        Snacks.show(Snack(saved, SnackTone.OFFLINE, sticky = true, key = ReviewViewModel.OFFLINE))
    }) {
        // A second tap queued behind the first one: by now it went, or the owner answered.
        val open = job ?: return@run
        if (open.review.id != id || !open.canUpdate) return@run
        sending = true
        val sent =
            try {
                gate.send({ ProofStore.proof(activity, id) }) { updateProof(activity, open, Uri.fromFile(it), note) }
            } catch (e: ApiError) {
                if (e.status != 409) throw e
                answeredFirst(open)
                return@run
            } finally {
                sending = false
            }
        if (sent == null) {
            proof = null
            Snacks.error("Add your screenshot again.")
            return@run
        }
        // Sent: keep() closes the page and clears the draft.
        keep(open.copy(review = sent))
        Snacks.dismiss(ReviewViewModel.OFFLINE)
        offline = false
        Snacks.done("Update sent. The developer sees your new screenshot.")
    }
}

/** The owner answered first: shut the update page whatever the network does, then read the review back if it can be. */
private suspend fun ReviewViewModel.answeredFirst(job: ReviewJob) {
    keep(job.copy(review = job.review.copy(canUpdate = false)))
    fetchReview(job.review.id)?.let { keep(job.copy(review = it)) }
}
