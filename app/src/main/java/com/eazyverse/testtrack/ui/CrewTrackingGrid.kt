package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CalendarCheck
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular
import com.eazyverse.testtrack.viewmodel.AppNights

/*
 * The run at a glance, the way a contribution graph reads: one square per tester per night, and
 * under each night how many testers it counted for, against Google's 12. Each row starts with the
 * tester's face rather than their name, so the squares get the width; a tap on the face says who
 * it is. A night already over with fewer than 12 is said by number, since that is the night the
 * owner's run is short on. What each square means is behind the (i), in [SquaresKey].
 */

/** The face column: a 24 face and a little air before the squares. */
private val FaceColumn = 26.dp

@Composable
internal fun TrackingGrid(
    pictures: AppNights,
    testers: List<CrewTester> = remember(pictures) { testersOf(pictures) },
    finished: Boolean = false,
    /** The reader's own night, lit down the grid; 0 for none. */
    today: Int = 0,
    onFace: (CrewTester) -> Unit = {},
    /** Opens what the squares mean; no (i) without it. */
    onKey: (() -> Unit)? = null,
) {
    val t = Tt.colors
    val short = remember(pictures) { shortNights(pictures) }
    val counts = remember(pictures) { nightCounts(pictures) }
    val nights = pictures.runDays
    val lit = t.accent.copy(alpha = 0.16f)
    CrewCard(padding = PaddingValues(horizontal = 6.dp, vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                finished -> StatusChip("$nights nights done", Tone.QUIET, Lucide.CalendarCheck)
                short.isEmpty() -> StatusChip("On track for Google", Tone.OK)
                else -> StatusChip("Short on ${pluralOf(short.size, "night")} ${short.joinToString()}", Tone.WARN)
            }
            Spacer(Modifier.weight(1f))
            if (onKey != null) {
                // 32 drawn, 48 to touch, without the row growing to 48.
                Box(Modifier.size(32.dp).wrapContentSize(unbounded = true)) {
                    IconButton(onClick = onKey) {
                        Icon(Lucide.Info, "What the squares mean", Modifier.size(18.dp), tint = t.accentInk)
                    }
                }
            }
        }
        GridRow(lead = {}) {
            for (day in 1..nights) {
                val now = day == today
                Text(
                    "$day",
                    Modifier
                        .weight(1f)
                        .then(if (now) Modifier.background(lit, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)) else Modifier),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp, lineHeight = 14.sp),
                    fontWeight = if (now) FontWeight.Bold else FontWeight.Normal,
                    color = if (now) t.accentInk else t.text4,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        testers.forEach { tester ->
            GridRow(
                lead = {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(onClickLabel = "See who this is") { onFace(tester) }
                            .semantics { contentDescription = tester.name }
                            .then(if (tester.out) Modifier.alpha(0.45f) else Modifier),
                    ) {
                        Avatar(
                            tester.photo,
                            initialOf(tester.name),
                            size = 24.dp,
                            letterStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        )
                    }
                },
            ) {
                tester.squares.forEachIndexed { i, square ->
                    Box(
                        Modifier
                            .weight(1f)
                            .then(if (i + 1 == today) Modifier.background(lit) else Modifier)
                            .padding(horizontal = 0.5.dp, vertical = 1.dp),
                    ) {
                        Cell(square, Modifier.fillMaxWidth(), "${tester.name}, night ${i + 1}")
                    }
                }
            }
        }
        GridRow(lead = {}) {
            counts.forEachIndexed { i, n ->
                val day = i + 1
                val now = day == today
                Text(
                    n?.toString().orEmpty(),
                    Modifier
                        .weight(1f)
                        .then(
                            if (now) Modifier.background(lit, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)) else Modifier,
                        ),
                    style =
                        MaterialTheme.typography.labelSmall
                            .tabular()
                            .copy(fontSize = 9.5.sp, lineHeight = 14.sp),
                    fontWeight = if (day in short) FontWeight.Bold else FontWeight.Normal,
                    color = if (day in short) t.bad else t.text4,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Info, null, Modifier.size(12.dp), tint = t.text4)
            Spacer(Modifier.width(4.dp))
            Text(
                "Tap a face to see who it is",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp),
                color = t.text4,
            )
        }
    }
}

/** One line of the grid: the face column, then the nights. */
@Composable
private fun GridRow(
    lead: @Composable () -> Unit,
    nights: @Composable RowScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(FaceColumn), contentAlignment = Alignment.CenterStart) { lead() }
        Row(Modifier.weight(1f), content = nights)
    }
}

/** One square: its fill, its edge, and its edge's weight, or a dashed edge for a night not joined yet. */
private data class SquareLook(
    val fill: Color,
    val edge: Color,
    val width: Dp = 1.dp,
    val dashed: Boolean = false,
)

@Composable
private fun lookOf(square: Square): SquareLook {
    val t = Tt.colors
    return when (square) {
        Square.PICTURE -> SquareLook(Status.posted, Status.posted)
        Square.BY_HAND -> SquareLook(Status.posted, t.warn, 1.5.dp)
        Square.OPENED -> SquareLook(t.okBg, t.ok.copy(alpha = 0.45f))
        Square.WAITING -> SquareLook(t.accent.copy(alpha = 0.35f), t.accent)
        Square.WAITING_LONG -> SquareLook(t.badBg, t.accent)
        Square.MISSED -> SquareLook(t.badBg, t.bad)
        Square.NOT_OWED -> SquareLook(t.glass2, t.glassBorder)
        Square.TONIGHT -> SquareLook(Color.Transparent, t.accent, 1.5.dp)
        Square.TO_COME -> SquareLook(Color.Transparent, t.glassBorder)
        Square.BEFORE -> SquareLook(Color.Transparent, t.glassBorderStrong, dashed = true)
    }
}

/** What a square is called, in the key and to a screen reader. */
private fun Square.said(finished: Boolean): String =
    when (this) {
        Square.PICTURE -> if (finished) "Picture kept" else "Picture in"
        Square.BY_HAND -> "Picked by hand"
        Square.OPENED -> "Opened"
        Square.WAITING -> "On its way"
        Square.WAITING_LONG -> "Waiting for hours"
        Square.MISSED -> "Missed"
        Square.NOT_OWED -> "Not owed"
        Square.TONIGHT -> "Tonight"
        Square.TO_COME -> if (finished) "After they left" else "To come"
        Square.BEFORE -> "Not joined yet"
    }

@Composable
private fun Cell(
    square: Square,
    modifier: Modifier,
    said: String,
) {
    val look = lookOf(square)
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(look.fill)
            .then(if (look.dashed) Modifier.dashedEdge(look.edge, 4.dp) else Modifier.border(look.width, look.edge, shape))
            .semantics { contentDescription = "$said: ${square.said(false).lowercase()}" },
    )
}

/** A 1dp dashed rounded edge, which [border] cannot draw. */
private fun Modifier.dashedEdge(
    color: Color,
    corner: Dp,
) = drawBehind {
    val stroke = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(corner.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))),
    )
}

/** The squares a key lists, in the order the grid is read. */
private fun keyOf(finished: Boolean): List<Square> =
    if (finished) {
        listOf(Square.OPENED, Square.PICTURE, Square.BY_HAND, Square.MISSED, Square.NOT_OWED, Square.BEFORE, Square.TO_COME)
    } else {
        listOf(
            Square.PICTURE,
            Square.BY_HAND,
            Square.WAITING,
            Square.WAITING_LONG,
            Square.TONIGHT,
            Square.MISSED,
            Square.NOT_OWED,
            Square.BEFORE,
            Square.TO_COME,
        )
    }

/**
 * What the squares mean, from the grid's (i): how to read it, each square in two columns, and the
 * page's own notes under a hairline, such as how many must keep opening the app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SquaresKey(
    finished: Boolean,
    notes: List<Pair<ImageVector, String>>,
    onDismiss: () -> Unit,
) {
    val t = Tt.colors
    PickerSheet("What the squares mean", onDismiss = onDismiss, skipHalf = true) {
        Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text(
                if (finished) {
                    "Each square is one tester on one night. The number under a night is how many opened it."
                } else {
                    "Each square is one tester on one night. The number under a night is how many it counted for. " +
                        "Google asks for ${CrewRules.FLOOR}."
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                color = t.text3,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                maxItemsInEachRow = 2,
                verticalArrangement = Arrangement.spacedBy(9.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                keyOf(finished).forEach { square ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        val look = lookOf(square)
                        val shape = RoundedCornerShape(3.dp)
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(shape)
                                .background(look.fill)
                                .then(
                                    if (look.dashed) {
                                        Modifier.dashedEdge(
                                            look.edge,
                                            3.dp,
                                        )
                                    } else {
                                        Modifier.border(look.width, look.edge, shape)
                                    },
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            square.said(finished),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                            color = t.text2,
                        )
                    }
                }
            }
            if (notes.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 10.dp), thickness = 1.dp, color = t.cardLine)
                notes.forEachIndexed { i, (icon, text) ->
                    Row(Modifier.padding(top = if (i > 0) 6.dp else 0.dp), verticalAlignment = Alignment.Top) {
                        Icon(icon, null, Modifier.padding(top = 2.dp).size(13.dp), tint = t.text3)
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                            color = t.text3,
                        )
                    }
                }
            }
        }
    }
}
