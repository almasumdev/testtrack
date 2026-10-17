package com.eazyverse.testtrack.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

/**
 * This review as the server has it now, kept with what the phone already knows: what moves the
 * page from "Sent to the developer" to "Approved" while it is open, rather than on the next visit.
 */
internal fun ReviewViewModel.reloadReview() {
    val id = job?.review?.id ?: return
    viewModelScope.launch {
        val fresh = fetchReview(id) ?: return@launch
        job?.let { keep(it.copy(review = fresh)) }
    }
}
