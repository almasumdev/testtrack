package com.eazyverse.testtrack.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * This review as the server has it now, kept with what the phone already knows: what moves the
 * page from "Sent to the developer" to "Approved" while it is open, rather than on the next visit.
 */
internal fun ReviewViewModel.reloadReview() {
    val id = job?.review?.id ?: return
    if (busy) return
    val asked = keeps
    viewModelScope.launch {
        val fresh = fetchReview(id) ?: return@launch
        // Something was done to it while this was asking (a send, a give up): what that answered
        // is newer, and this would put the page back a step.
        if (busy || keeps != asked) return@launch
        job?.let { keep(it.copy(review = fresh)) }
    }
}
