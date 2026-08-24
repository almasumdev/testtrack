package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.data.*

private val CELL = 26.dp
private val CELL_GAP = 2.dp
private val NAME_COLUMN = 96.dp

/**
 * Legend for the grid.
 *
 * Present because the cells encode state, and state must never be carried by colour alone — each
 * swatch here shows the shape that goes with it, and every cell also announces itself to
 * accessibility services.
 */
@Composable
fun Legend() {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendKey(DayState.POSTED, "reported")
        LegendKey(DayState.SHORT, "under ${Proof.MIN_USAGE_MS / 1000}s")
        LegendKey(DayState.MISSED, "missed")
        LegendKey(DayState.UPCOMING, "to come")
        LegendKey(DayState.NOT_JOINED, "not joined yet")
        LegendKey(DayState.REPORTED, "excused")
    }
}

@Composable
private fun LegendKey(
    state: DayState,
    label: String,
) {
    Row(Modifier.padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Cell(state, 14.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun Grid(
    vm: DashboardViewModel,
    onCell: (Tester, Proof) -> Unit,
) {
    val horizontal = rememberScrollState()

    Column(Modifier.horizontalScroll(horizontal).padding(horizontal = Gutter)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(NAME_COLUMN))
            repeat(vm.runDays) { day ->
                Box(Modifier.size(CELL + CELL_GAP), Alignment.Center) {
                    Text(
                        "${day + 1}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        vm.testers.forEach { tester -> GridRow(tester, vm, departed = false, onCell = onCell) }

        if (vm.testers.isEmpty()) {
            Text(
                "No other members in this group yet.",
                Modifier.padding(vertical = 24.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Kept out of the roster above, not filtered out entirely — the days they covered before
        // leaving are still real, and dropping the row along with the person is what this whole
        // feature exists to stop. Muted rather than styled like an active row, so it reads as
        // history rather than as somebody who still owes today.
        if (vm.departed.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            vm.departed.forEach { tester -> GridRow(tester, vm, departed = true, onCell = onCell) }
        }
    }
}

@Composable
private fun GridRow(
    tester: Tester,
    vm: DashboardViewModel,
    departed: Boolean,
    onCell: (Tester, Proof) -> Unit,
) {
    Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(NAME_COLUMN).padding(end = 8.dp)) {
            Text(
                tester.shortName,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (departed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 1,
            )
            if (departed) {
                Text(
                    "Left the group",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        repeat(vm.runDays) { day ->
            val state = vm.stateOf(tester.uid, day)
            val proof = vm.proofs[tester.uid to day]
            Box(
                Modifier
                    .padding(end = CELL_GAP)
                    .semantics {
                        contentDescription = "${tester.shortName}, day ${day + 1}, " +
                            (if (departed) "left the group, " else "") + state.spoken()
                    }.then(
                        if (proof != null) {
                            Modifier.clickable { onCell(tester, proof) }
                        } else {
                            Modifier
                        },
                    ),
            ) { Cell(state, CELL) }
        }
    }
}

private fun DayState.spoken() =
    when (this) {
        DayState.POSTED -> "reported"
        DayState.SHORT -> "reported but under ten seconds"
        DayState.MISSED -> "missed"
        DayState.UPCOMING -> "still to come"
        DayState.NOT_JOINED -> "before they joined"
        DayState.REPORTED -> "excused while the app is reported"
    }

/**
 * One day for one tester.
 *
 * Fill, outline, a struck-through slash and a lone dot separate the five states without relying
 * on hue, so the grid still reads under colour-vision deficiency, in greyscale, and when printed.
 */
@Composable
private fun Cell(
    state: DayState,
    size: androidx.compose.ui.unit.Dp,
) {
    val posted = Status.posted
    val missed = Status.missed
    val upcoming = Status.upcoming
    val reported = Status.short

    Canvas(Modifier.size(size)) {
        val radius = CornerRadius(this.size.minDimension * 0.28f)
        val strokeWidth = this.size.minDimension * 0.12f

        when (state) {
            DayState.POSTED ->
                drawRoundRect(posted, cornerRadius = radius)

            DayState.SHORT ->
                drawRoundRect(
                    posted,
                    cornerRadius = radius,
                    style = Stroke(strokeWidth),
                )

            DayState.MISSED -> {
                drawRoundRect(missed.copy(alpha = 0.18f), cornerRadius = radius)
                val inset = this.size.minDimension * 0.26f
                drawLine(
                    color = missed,
                    start = Offset(inset, this.size.height - inset),
                    end = Offset(this.size.width - inset, inset),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            DayState.UPCOMING ->
                drawRoundRect(upcoming, cornerRadius = radius)

            // No fill, no slash — this day isn't owed at all, which is a different fact from
            // "owed and not yet here" (UPCOMING) or "owed and missed" (MISSED). A single faint
            // dot says "nothing to report here" without reading as either of those.
            DayState.NOT_JOINED -> {
                drawRoundRect(
                    upcoming.copy(alpha = 0.35f),
                    cornerRadius = radius,
                    style = Stroke(strokeWidth * 0.6f),
                )
                drawCircle(upcoming, radius = this.size.minDimension * 0.06f)
            }

            // A horizontal bar, not MISSED's diagonal slash — this day was never anybody's to
            // lose. The app itself was the problem, not the tester, so the mark reads as "on
            // hold" rather than "failed."
            DayState.REPORTED -> {
                drawRoundRect(reported.copy(alpha = 0.18f), cornerRadius = radius)
                val inset = this.size.minDimension * 0.26f
                drawLine(
                    color = reported,
                    start = Offset(inset, this.size.height / 2f),
                    end = Offset(this.size.width - inset, this.size.height / 2f),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
