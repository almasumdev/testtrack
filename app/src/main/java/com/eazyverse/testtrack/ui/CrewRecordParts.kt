package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowLeftRight
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.CalendarCheck
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Rocket
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.UserX
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.ui.theme.Tt
import java.time.Instant

/*
 * A finished crew's page, card by card: the hero, the reader's app, their nights, the other apps,
 * and the note left once the crew's details are cleared. Each draws what it is given and no more.
 */

/**
 * What came of the run, at the top of a finished crew's page: a quiet chip, the outcome in its
 * tone, when the details clear, then the claim and the question it still asks, or their answers.
 */
@Composable
internal fun RecordHero(
    record: CrewRecord,
    detailsUntil: String?,
    pruned: Boolean,
    busy: RecordBusy?,
    onAnswer: (Boolean) -> Unit,
    onClaim: () -> Unit,
    onOpenCrew: ((String) -> Unit)?,
    now: Instant,
) {
    val (headline, tone) = recordHeadline(record)
    val (chip, icon) =
        when (record.outcome) {
            RecordOutcome.FINISHED -> "${record.runDays} nights done" to Lucide.CalendarCheck
            RecordOutcome.LEFT -> "You left" to Lucide.LogOut
            RecordOutcome.REMOVED -> "Taken out" to Lucide.UserX
            RecordOutcome.MOVED -> (record.leftDay?.let { "Moved on day $it" } ?: "Moved") to Lucide.ArrowLeftRight
            RecordOutcome.CANCELLED -> "Cancelled" to Lucide.Ban
        }
    Column {
        CrewCard(padding = PaddingValues(16.dp)) {
            StatusChip(chip, Tone.QUIET, icon)
            Spacer(Modifier.height(10.dp))
            Text(
                headline,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 23.sp, lineHeight = 27.sp),
                fontWeight = FontWeight.Bold,
                color = if (tone == Tone.QUIET) Tt.colors.text else tone.ink(),
            )
            if (record.ran()) {
                detailsClear(detailsUntil, pruned, now)?.let { (text, last) ->
                    Spacer(Modifier.height(12.dp))
                    StatusChip(text, if (last) Tone.WARN else Tone.QUIET, Lucide.Clock)
                }
            }
            if (canClaimPerfect(record)) {
                Spacer(Modifier.height(12.dp))
                RecordAsk(icon = Lucide.Star, title = "Perfect run", hint = "You kept every night.", reward = null) {
                    AskPill("Claim ${record.perfectClaimAmount} tix", RecordBusy.CLAIM, busy, onClaim)
                }
            }
            if (asksProdAccess(record, now)) {
                Spacer(Modifier.height(12.dp))
                RecordAsk(
                    icon = Lucide.Rocket,
                    title = "Got production access?",
                    hint = "Answer once Google replies to your apply.",
                    reward = record.prodAccessReward.takeIf { it > 0 }?.let { "+$it tix" },
                ) {
                    AskPill("Yes", RecordBusy.YES, busy) { onAnswer(true) }
                    AskPill("No", RecordBusy.NO, busy) { onAnswer(false) }
                }
            }
            val results = recordResultChips(record, now)
            if (results.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    results.forEach { result ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RecordResult(result)
                        }
                    }
                }
            }
            val movedTo = record.movedToCrewId
            val movedNumber = record.movedToCrewNumber
            if (record.outcome == RecordOutcome.MOVED && movedTo != null && movedNumber != null && onOpenCrew != null) {
                Spacer(Modifier.height(16.dp))
                Secondary("Open ${crewNameOf(movedNumber)}", Modifier.fillMaxWidth()) { onOpenCrew(movedTo) }
            }
        }
        if (record.charged && record.outcome == RecordOutcome.MOVED) {
            QuietNote("Your seat went with it, so no tix were spent twice.", Modifier.padding(top = 10.dp))
        }
        if (record.charged && record.outcome == RecordOutcome.CANCELLED) {
            val back = record.tixRefunded.takeIf { it > 0 } ?: record.tixSpent.takeIf { it > 0 } ?: CrewRules.SEAT_COST
            QuietNote("Your $back tix came back.", Modifier.padding(top = 10.dp))
        }
    }
}

/** The run reached the reader's app: it finished, or left or was taken out part-way. */
internal fun CrewRecord.ran() = outcome == RecordOutcome.FINISHED || outcome == RecordOutcome.LEFT || outcome == RecordOutcome.REMOVED

/** The reader's app in one compact row. Opens who tested it, until the details are cleared. */
@Composable
internal fun YourAppRow(
    record: CrewRecord,
    onOpen: (() -> Unit)?,
) {
    CrewCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppMark(record.packageName, record.appName, record.appIcon, size = 40.dp, ownIcon = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    record.appName,
                    style = tileStyle(15f, 19f, FontWeight.SemiBold),
                    color = Tt.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    yourAppLine(record),
                    style = tileStyle(13f, 17f, FontWeight.Normal),
                    color = Tt.colors.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onOpen != null) {
                Spacer(Modifier.width(8.dp))
                Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = Tt.colors.text4)
            }
        }
    }
}

/**
 * The reader's nights: a chip saying how they went, a square per night with its number under it,
 * then what each square means. Once the details are cleared only the chip and the counts are left.
 */
@Composable
internal fun NightsCard(
    record: CrewRecord,
    showStrip: Boolean,
) {
    val (summary, tone) = nightsSummary(record)
    val icon =
        when {
            record.outcome == RecordOutcome.LEFT -> Lucide.LogOut
            record.outcome == RecordOutcome.REMOVED -> Lucide.UserX
            tone == Tone.OK -> Lucide.CircleCheck
            else -> null
        }
    CrewCard(padding = PaddingValues(14.dp)) {
        StatusChip(summary, tone, icon)
        if (showStrip && record.nights.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            NightsStrip(record.nights)
        }
        NightsLegend(record, withStripOnly = showStrip && record.nights.isNotEmpty())
    }
}

/** One square per night, in order, each with its night's number under it. */
@Composable
internal fun NightsStrip(nights: List<NightMark>) {
    val described = nights.mapIndexed { i, mark -> "Night ${i + 1} ${nightLabel(mark, RecordOutcome.FINISHED)}" }
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = described.joinToString(", ") }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            nights.forEach { NightCell(it, Modifier.weight(1f).aspectRatio(1f)) }
        }
        Spacer(Modifier.height(3.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            nights.indices.forEach { i ->
                Text(
                    "${i + 1}",
                    Modifier.weight(1f),
                    style = tileStyle(9.5f, 12f, FontWeight.Normal),
                    color = Tt.colors.text4,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** What each square means, with how many of each; [withStripOnly] adds the marks only the strip has. */
@Composable
private fun NightsLegend(
    record: CrewRecord,
    withStripOnly: Boolean,
) {
    val counts = nightCountsOf(record)
    val shown = NightMark.entries.filter { (counts[it] ?: 0) > 0 && (withStripOnly || !it.stripOnly()) }
    if (shown.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEach { mark ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                NightCell(mark, Modifier.size(13.dp), corner = 3.dp, withIcon = false)
                Spacer(Modifier.width(5.dp))
                val label = nightLabel(mark, record.outcome)
                Text(
                    if (mark.stripOnly()) label else "${counts[mark]} $label",
                    style = TextStyles.NoteLine,
                    color = Tt.colors.text3,
                )
            }
        }
    }
}

/** Nights that are not the reader's own: shown as empty squares, never counted. */
private fun NightMark.stripOnly() = this == NightMark.AFTER_LEFT || this == NightMark.NOT_IN

/** One night's square, in its state's colours, with its small mark. */
@Composable
private fun NightCell(
    mark: NightMark,
    modifier: Modifier,
    corner: Dp = 4.dp,
    withIcon: Boolean = true,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(corner)
    val base = modifier.clip(shape)
    val (fill, edge, width) =
        when (mark) {
            NightMark.KEPT -> Triple(t.ok, Color.Transparent, 0.dp)
            NightMark.SHORT -> Triple(t.warnBg, t.warn, 1.5.dp)
            NightMark.MISSED -> Triple(t.badBg, t.bad, 1.5.dp)
            NightMark.LET_OFF -> Triple(t.glass2, t.glassBorder, 1.dp)
            NightMark.REPORTED -> Triple(t.warnBg, t.warnBorder, 1.dp)
            NightMark.AFTER_LEFT, NightMark.NOT_IN -> Triple(Color.Transparent, Color.Transparent, 0.dp)
        }
    val drawn =
        if (mark.stripOnly()) {
            val dash = t.glassBorder
            base.drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = dash,
                    topLeft =
                        androidx.compose.ui.geometry
                            .Offset(stroke / 2, stroke / 2),
                    size =
                        androidx.compose.ui.geometry
                            .Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(corner.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
                )
            }
        } else {
            base.background(fill).then(if (width > 0.dp) Modifier.border(width, edge, shape) else Modifier)
        }
    Box(drawn, contentAlignment = Alignment.Center) {
        // Short: half filled from the bottom, the part opened.
        if (mark == NightMark.SHORT) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
                    .align(Alignment.BottomCenter)
                    .background(t.warn),
            )
        }
        if (withIcon) {
            nightIcon(mark)?.let { (icon, size, tint) -> Icon(icon, null, Modifier.size(size), tint = tint) }
        }
    }
}

@Composable
private fun nightIcon(mark: NightMark): Triple<ImageVector, Dp, Color>? =
    when (mark) {
        NightMark.KEPT -> Triple(Lucide.Check, 11.dp, Tt.colors.onPrimary)
        NightMark.MISSED -> Triple(Lucide.X, 11.dp, Tt.colors.bad)
        NightMark.LET_OFF -> Triple(Lucide.Moon, 10.dp, Tt.colors.text4)
        NightMark.REPORTED -> Triple(Lucide.Flag, 10.dp, Tt.colors.warn)
        else -> null
    }

/**
 * The other apps in the crew as icons, those that left faded, with a line over them and what the
 * fade means under them. The icons are only faces: nothing here opens.
 */
@Composable
internal fun RecordAppsCard(apps: List<RecordApp>) {
    CrewCard(padding = PaddingValues(14.dp)) {
        Text(appsSummary(apps), style = tileStyle(14f, 19f, FontWeight.SemiBold), color = Tt.colors.text)
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            apps.forEach { app ->
                Box(Modifier.size(36.dp).alpha(if (app.left) 0.4f else 1f)) {
                    AppMark(app.packageName, app.name, app.icon, size = 36.dp)
                }
            }
        }
        if (apps.any { it.left }) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .alpha(0.4f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Tt.colors.text4),
                )
                Spacer(Modifier.width(6.dp))
                Text("Left the crew", style = tileStyle(12f, 16f, FontWeight.Normal), color = Tt.colors.text4)
            }
        }
    }
}

/** Once the details are cleared: when, and that the result above stays. */
@Composable
internal fun ClearedNote(
    on: String?,
    days: Long?,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(TileRadius)
    val after = days?.let { "$it ${if (it == 1L) "day" else "days"}" } ?: "a few days"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = t.glassBorder,
                    topLeft =
                        androidx.compose.ui.geometry
                            .Offset(stroke / 2, stroke / 2),
                    size =
                        androidx.compose.ui.geometry
                            .Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(TileRadius.toPx()),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            }.clip(shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Lucide.Info, null, Modifier.padding(top = 1.dp).size(16.dp), tint = t.text4)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (on != null) "Details cleared on $on" else "Details cleared",
                style = tileStyle(13f, 18f, FontWeight.SemiBold),
                color = t.text2,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Nights, testers, apps and pictures are cleared $after after a crew ends. The result above stays.",
                style = TextStyles.PageLine,
                color = t.text3,
            )
        }
    }
}

/** A finished crew's page before its record has come: the hero, your app, your nights, the apps. */
@Composable
internal fun RecordPageLoading() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 8.dp, start = Gutter, end = Gutter)
            .semantics(mergeDescendants = true) { contentDescription = "Loading the crew" },
    ) {
        CrewCard(padding = PaddingValues(16.dp)) {
            Skeleton(width = 130.dp, height = 24.dp, corner = 12.dp)
            Spacer(Modifier.height(12.dp))
            Skeleton(width = 240.dp, height = 26.dp)
            Spacer(Modifier.height(14.dp))
            Skeleton(width = 140.dp, height = 24.dp, corner = 12.dp)
        }
        SkeletonHead(80.dp)
        CrewCard(padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Skeleton(width = 40.dp, height = 40.dp, corner = 12.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Skeleton(width = 200.dp, height = 15.dp)
                    Spacer(Modifier.height(6.dp))
                    Skeleton(width = 140.dp, height = 12.dp)
                }
            }
        }
        SkeletonHead(90.dp)
        CrewCard(padding = PaddingValues(14.dp)) {
            Skeleton(width = 120.dp, height = 24.dp, corner = 12.dp)
            Spacer(Modifier.height(10.dp))
            Skeleton(height = 20.dp, corner = 5.dp)
        }
        SkeletonHead(60.dp)
        CrewCard(padding = PaddingValues(14.dp)) {
            Skeleton(width = 220.dp, height = 16.dp)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(CrewRules.NIGHTLY - 1) { Skeleton(width = 36.dp, height = 36.dp, corner = 10.dp) }
            }
        }
    }
}

@Composable
private fun SkeletonHead(width: Dp) {
    Box(Modifier.padding(top = 20.dp, bottom = 10.dp)) { Skeleton(width = width, height = 18.dp) }
}
