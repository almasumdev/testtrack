package com.eazyverse.testtrack.viewmodel

import android.content.Context
import com.eazyverse.testtrack.data.ReviewChecks
import com.eazyverse.testtrack.net.ApiReview

/** [ReviewChecks], for Circle, whose model does not reach the data layer itself. */
object PhoneChecks {
    suspend fun report(
        context: Context,
        reviews: List<ApiReview>,
    ) = ReviewChecks.report(context, reviews)
}
