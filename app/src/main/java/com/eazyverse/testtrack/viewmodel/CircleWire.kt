package com.eazyverse.testtrack.viewmodel

import com.eazyverse.testtrack.domain.Case
import com.eazyverse.testtrack.domain.InstallReport
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.RequestReview
import com.eazyverse.testtrack.net.ApiDispute
import com.eazyverse.testtrack.net.ApiMyPost
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
                )
            },
    )

/** An ISO time from the server as epoch milliseconds, or null when absent or unreadable. */
fun millis(iso: String?): Long? = iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
