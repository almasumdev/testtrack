package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.eazyverse.testtrack.data.*

/**
 * The dashboard's own shape, since its content is not a list — the thumbnail strip is the tallest
 * thing on the page and a generic row skeleton under it would collapse the moment it landed.
 */
@Composable
fun DashboardSkeleton() {
    Spacer(Modifier.height(10.dp))
    Column(Modifier.padding(horizontal = Gutter)) {
        Skeleton(width = 132.dp, height = 34.dp, corner = 10.dp)
        Spacer(Modifier.height(12.dp))
        Skeleton(width = 196.dp, height = 12.dp)
        Spacer(Modifier.height(18.dp))
        SkeletonMeter()
    }
    Spacer(Modifier.height(30.dp))
    Row(
        Modifier.padding(horizontal = Gutter),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Skeleton(width = 86.dp, height = 152.dp, corner = 10.dp)
                Spacer(Modifier.height(8.dp))
                Skeleton(width = 54.dp, height = 9.dp)
            }
        }
    }
    Spacer(Modifier.height(30.dp))
    Rows { SkeletonGroupList(8) }
}

/**
 * The one number that matters, stated rather than plotted.
 *
 * A count out of thirteen is not a chart — the bar underneath is there to make "well behind" and
 * "nearly there" readable at a glance, not to carry the value.
 */
@Composable
fun Headline(vm: DashboardViewModel) {
    val reported = vm.reportedToday().size
    val expected = vm.testers.size
    val day = vm.day

    Column(Modifier.padding(start = Gutter, end = Gutter, top = 12.dp)) {
        Text(
            if (day != null) "$reported of $expected" else "Not started",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (day != null) {
                "reported today · day ${day + 1} of ${vm.runDays}"
            } else {
                "the run begins when the group reaches ${TestGroup.THRESHOLD} members"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (day != null) {
            Spacer(Modifier.height(16.dp))
            // One box per tester, the same as home, the group screen and setup. A solid bar says
            // "most of them"; twelve boxes with two gaps says which day this actually was, and
            // this is the screen where that is the whole question.
            Meter(done = reported, total = expected)
        }
    }
}

@Composable
fun Reporters(
    vm: DashboardViewModel,
    onOpen: (Pair<Tester, Proof>) -> Unit,
) {
    val reported = vm.reportedToday()

    SectionLabel("Today")
    if (reported.isEmpty()) {
        Blank("Nobody has reported yet today.")
        return
    }

    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        reported.forEach { entry ->
            val (tester, proof) = entry
            Column(
                Modifier.width(86.dp).clickable { onOpen(entry) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (proof.imageUrl.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = proof.imageUrl,
                        contentDescription = "${tester.shortName}'s screenshot",
                        contentScale = ContentScale.Crop,
                        loading = { Skeleton(width = 86.dp, height = 152.dp, corner = 10.dp) },
                        error = { Skeleton(width = 86.dp, height = 152.dp, corner = 10.dp) },
                        modifier =
                            Modifier
                                .size(86.dp, 152.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Status.upcoming),
                    )
                } else {
                    Box(
                        Modifier
                            .size(86.dp, 152.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Status.upcoming),
                        contentAlignment = Alignment.Center,
                    ) { Initial(tester.initial) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    tester.shortName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
                Text(
                    formatDuration(proof.usageMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun Behind(vm: DashboardViewModel) {
    val behind = vm.behind()
    if (vm.day == null || behind.isEmpty()) return

    SectionLabel("Still to report")
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        behind.forEach { tester ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Initial(tester.initial, 26.dp)
                Spacer(Modifier.width(8.dp))
                Text(tester.shortName, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Spacer(Modifier.height(22.dp))
}

/**
 * What testers have found worth saying, newest first — see [Feedback] and
 * [Repo.submitFeedback]. The one place any of it reaches the owner; there is no push for this,
 * only the console the way every other decision here arrives.
 */
@Composable
fun FeedbackList(vm: DashboardViewModel) {
    SectionLabel("Feedback", "${vm.feedback.size}")
    Rows {
        vm.feedback.forEachIndexed { index, note ->
            if (index > 0) RowDivider()
            val from = vm.testers.firstOrNull { it.uid == note.testerUid }?.shortName ?: "A tester"
            Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        from,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Day ${note.day + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    note.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun PerTester(vm: DashboardViewModel) {
    if (vm.testers.isEmpty()) return
    val best = vm.testers.maxOfOrNull { vm.totalUsage(it.uid) }?.coerceAtLeast(1L) ?: 1L
    val elapsed = (vm.day?.plus(1)) ?: 0

    SectionLabel("Every tester")
    Rows {
        vm.testers
            .sortedByDescending { vm.daysPosted(it.uid) }
            .forEachIndexed { index, tester ->
                if (index > 0) RowDivider()
                val posted = vm.daysPosted(tester.uid)
                val usage = vm.totalUsage(tester.uid)

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Initial(tester.initial)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            tester.shortName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "$posted of $elapsed days · ${formatDuration(usage)} total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Meter(usage.toFloat() / best, height = 4.dp)
                    }
                }
            }
    }

    // A day fraction here would be unfair rather than just imprecise: "elapsed" is the whole
    // cohort's current day, not however long this person was actually a member for, and there is
    // deliberately no record of that in the grid — see stateOf. "Left the group" is the honest
    // version of that same fact.
    if (vm.departed.isNotEmpty()) {
        Spacer(Modifier.height(22.dp))
        SectionLabel("Left the group")
        Rows {
            vm.departed.forEachIndexed { index, tester ->
                if (index > 0) RowDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Initial(tester.initial)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            tester.shortName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${vm.daysPosted(tester.uid)} day" +
                                (if (vm.daysPosted(tester.uid) == 1) "" else "s") +
                                " covered before leaving",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
