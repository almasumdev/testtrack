package com.eazyverse.testtrack.viewmodel

import android.app.Activity
import android.net.Uri
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.ui.ReviewViewModel
import com.eazyverse.testtrack.ui.Snacks

// The review page's answers that end or reshape the reviewer's part, beside the view model.

/** The reviewer's one answer to a refusal: words, a picture, or both. */
fun ReviewViewModel.reply(
    activity: Activity,
    text: String,
    picture: Uri?,
) {
    val current = job ?: return
    run(waits = true, onOffline = { Snacks.offline { reply(activity, text, picture) } }) {
        keep(current.copy(review = replyWithPicture(activity, current, text, picture)))
    }
}

/** It would not install: the developer is told why, the slot goes back, nothing counts against you. */
fun ReviewViewModel.report(
    reason: String,
    note: String,
    onDone: () -> Unit,
) {
    val current = job ?: return
    run(waits = true, onOffline = { Snacks.offline { report(reason, note, onDone) } }) {
        keep(current.copy(review = Api.reportPost(current.review.postId, reason, note)))
        dropProof(current.review.id)
        onDone()
    }
}

/** Give the slot back so somebody else can take it. Whatever was kept for it goes too. */
fun ReviewViewModel.abandon(onDone: () -> Unit) {
    val current = job ?: return
    run(waits = true, onOffline = { Snacks.offline { abandon(onDone) } }) {
        keep(current.copy(review = Api.abandon(current.review.id)))
        dropProof(current.review.id)
        onDone()
    }
}
