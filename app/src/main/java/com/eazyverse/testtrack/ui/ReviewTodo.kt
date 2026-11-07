package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * Everything a review asks, said once as plain bullet points on the first step, before any work is
 * done: how long to use the app, what to write on Play, the screenshot, what it pays and how long
 * there is. No card around it. It follows the request: a closed test, a live app, stars only or
 * written feedback.
 */

/** The bullet points for this review, in the order they are done. */
internal fun todoLines(job: ReviewJob): List<String> {
    val minutes = (if (job.live) FeedRules.LIVE_USE_SECONDS else FeedRules.CLOSED_USE_SECONDS) / 60
    val onPlay =
        if (job.needsChars > 0) {
            "Review it on Google Play: stars and at least ${job.needsChars} characters"
        } else {
            "Give it a star rating on Google Play"
        }
    return buildList {
        add("Install it from Play")
        add("Use it for $minutes ${if (minutes == 1) "minute" else "minutes"}")
        if (job.live) add("Open it again tomorrow, after ${FeedRules.LIVE_RETURN_HOURS} hours")
        add(onPlay)
        add("Take a screenshot of your review with the capture button")
    }
}

/** What it pays and how long there is to finish, under the list. */
internal fun todoPayLine(job: ReviewJob): String {
    val within = if (job.live) "${FeedRules.LIVE_CLAIM_HOURS / 24} days" else "${FeedRules.CLAIM_HOURS / 24} day"
    val pays = if (job.post.reward > 0) "You get ${job.post.reward} tix. " else ""
    return "${pays}Finish within $within."
}

@Composable
internal fun WhatYouWillDo(job: ReviewJob) {
    if (job.settled || job.step != ReviewStep.INSTALL) return
    val t = Tt.colors
    Spacer(Modifier.height(18.dp))
    Text(
        "What you'll do",
        Modifier.widthIn(max = 300.dp).fillMaxWidth(),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = t.text,
    )
    Spacer(Modifier.height(6.dp))
    todoLines(job).forEach { line ->
        Row(Modifier.widthIn(max = 300.dp).fillMaxWidth().padding(vertical = 2.dp)) {
            Text("•", style = MaterialTheme.typography.bodyMedium, color = t.text2)
            Spacer(Modifier.padding(start = 8.dp))
            Text(line, style = MaterialTheme.typography.bodyMedium, color = t.text2)
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        todoPayLine(job),
        Modifier.widthIn(max = 300.dp).fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = t.accentInk,
    )
    if (job.live) {
        Spacer(Modifier.height(4.dp))
        Text(
            "No review box on Play? You can send it here for half.",
            Modifier.widthIn(max = 300.dp).fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = t.text3,
        )
    }
}
