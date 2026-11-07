package com.eazyverse.testtrack.viewmodel

import com.eazyverse.testtrack.domain.Case
import com.eazyverse.testtrack.domain.Evidence
import com.eazyverse.testtrack.domain.InstallReport
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.RequestReview
import com.eazyverse.testtrack.net.ApiActivityEvent
import com.eazyverse.testtrack.net.ApiDispute
import com.eazyverse.testtrack.net.ApiMyPost
import com.eazyverse.testtrack.ui.FeedEvent
import com.eazyverse.testtrack.ui.FeedEventKind
import java.time.Instant

/*
 * Circle's cases and the reader's own requests, from the server's words into the domain's.
 *
 * Only mappings that read nothing but their argument. One that fills a gap from the feed the
 * view model holds (a review's app name, a reward an older server left out) stays beside it.
 */

fun caseOf(it: ApiDispute) =
    Case(
        id = it.id,
        appName = it.appName,
        reason = it.reason,
        written = it.written,
        proofRef = it.proofRef,
        stake = it.stake,
        votesCast = it.votesCast,
        votesNeeded = it.votesNeeded,
        packageName = it.packageName,
        reward = it.reward,
        isLive = it.isLive,
        icon = it.icon,
        feedback = it.feedback,
        needsChars = it.needsChars,
        textUse = it.textUse,
        noBox = it.noBox,
        recheckedRef = it.recheckedRef,
        evidence = Evidence(it.ownerProofRef, it.reply, it.replyProofRef),
    )

fun requestOf(it: ApiMyPost) =
    Request(
        id = it.id,
        appName = it.appName,
        packageName = it.packageName,
        isLive = it.isLive,
        reward = it.reward,
        bought = it.reviewsBought,
        done = it.reviewsDone,
        open = it.slotsOpen,
        closed = it.closed,
        refundable = it.refundable,
        icon = it.icon,
        unlisted = it.unlistedAt != null,
        reports =
            it.reports.map { r -> InstallReport(r.label, r.note) },
        feedback = it.feedback,
        needsChars = it.needsChars,
        readyTexts = it.readyTexts,
        reviews =
            it.reviews.map { r ->
                RequestReview(
                    id = r.id,
                    state = r.state,
                    reviewerName = r.reviewerName,
                    proofRef = r.proofRef,
                    written = r.written,
                    reason = r.reason,
                    outcome = r.outcome,
                    decideBy = millis(r.decideBy),
                    textUse = r.textUse,
                    noBox = r.noBox,
                    recheckedRef = r.recheckedRef,
                    checked = r.checkedAt != null,
                    evidence = Evidence(r.ownerProofRef, r.reply, r.replyProofRef),
                )
            },
    )

/** An ISO time from the server as epoch milliseconds, or null when absent or unreadable. */
fun millis(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

/** One line of the activity feed, or null for a kind this build does not know. */
internal fun feedEventOf(it: ApiActivityEvent): FeedEvent? {
    val kind = FeedEventKind.of(it.kind) ?: return null
    return FeedEvent(
        id = it.id,
        kind = kind,
        headline = it.headline,
        detail = it.detail,
        happenedAt = millis(it.happenedAt) ?: return null,
        faces = it.faces,
        moreFaces = it.moreFaces,
        tix = it.tix,
    )
}
