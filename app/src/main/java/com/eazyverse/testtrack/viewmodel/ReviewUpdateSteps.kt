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

/** Open the Send an update page. Its picture and note are kept like the first ones. */
fun ReviewViewModel.openUpdate() {
    if (job?.canUpdate == true) updateOpen = true
}

/** Back to the waiting page ("Keep what I sent"): what was kept for the update goes. */
fun ReviewViewModel.cancelUpdate(context: Context) {
    val id = job?.review?.id ?: return
    val app = context.applicationContext
    viewModelScope.launch {
        gate.replace({ proof = null }) { withContext(Dispatchers.IO) { ProofStore.forget(app, id) } }
        updateOpen = false
        note = ""
    }
}

/**
 * The page the server has just confirmed no longer takes an update (sent, or the owner answered
 * first): the update page closes and what was kept goes. One that closes with no update sent says why.
 */
internal fun ReviewViewModel.leaveUpdateIfDone(next: ReviewJob) {
    if (!updateOpen || next.canUpdate) return
    updateOpen = false
    dropProof(next.review.id)
    if (next.review.proofUpdatedAt == null) Snacks.info("The developer answered before your update went.")
}

/**
 * Send the new screenshot kept at this moment, with the note. The picture is the one kept inside
 * the gate, never one a version with details hidden is still replacing. The owner having answered
 * meanwhile (409) is read back from the server, which closes the page.
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
                if (e.status == 409) fetchReview(id)?.let { keep(open.copy(review = it)) }
                throw e
            } finally {
                sending = false
            }
        if (sent == null) {
            proof = null
            Snacks.error("Add your screenshot again.")
            return@run
        }
        keep(open.copy(review = sent))
        Snacks.dismiss(ReviewViewModel.OFFLINE)
        offline = false
        Snacks.done("Update sent. The developer sees your new screenshot.")
        dropProof(id)
    }
}
