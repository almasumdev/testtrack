package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDashed
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The pieces the Take a seat page is drawn from that are its own: the app on top, a heading with its
 * one status, the hint over the button, a line with its icon inline, and the page-shaped loading
 * skeleton. The parts of the page
 * are in CrewSeatSections and CrewSeatDetails.
 */

private val HeadingStyle = tileStyle(16f, 19f, FontWeight.Bold)
private val HeadNameStyle = tileStyle(16f, 18f, FontWeight.SemiBold)
private val StatusStyle = tileStyle(12.5f, 14f, FontWeight.SemiBold)
private val DeveloperStyle = tileStyle(12.5f, 14f, FontWeight.Medium)
private val HintStyle = tileStyle(12.5f, 16f, FontWeight.SemiBold)
private val HintQuietStyle = tileStyle(12.5f, 16f, FontWeight.Medium)

/** The one short status at the far end of a heading: done (green) or something wrong (amber). */
internal class SeatStatus(
    val text: String,
    val ok: Boolean,
) {
    companion object {
        fun ok(text: String) = SeatStatus(text, true)

        fun warn(text: String) = SeatStatus(text, false)
    }
}

/**
 * A part's heading on the seat page: 16 Bold, plain text, no box, and at its far end one short
 * status. 8dp under it; [gap] is 0 when the part is only its heading.
 */
@Composable
internal fun SeatHeading(
    text: String,
    status: SeatStatus? = null,
    gap: Dp = 8.dp,
) {
    val t = Tt.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 19.dp).padding(bottom = gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = HeadingStyle, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (status != null) {
            Spacer(Modifier.width(8.dp))
            val tone = if (status.ok) t.ok else t.warn
            Icon(if (status.ok) Lucide.CircleCheck else Lucide.CircleAlert, null, Modifier.size(14.dp), tint = tone)
            Spacer(Modifier.width(4.dp))
            Text(status.text, style = StatusStyle, color = tone, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * The app the seat is for, as a My apps tile without the box: its icon with the closed-test flask on
 * the corner, its name on up to two lines and its developer. The text is at most 18 + 18 + 2 + 14 =
 * 52dp, the icon's height.
 */
@Composable
internal fun SeatAppHead(app: AppEntry) {
    // Scales with the text, but only up to 1.3x, as a My apps tile does, so the column keeps fitting.
    val icon = AppTileIcon * LocalDensity.current.fontScale.coerceIn(1f, 1.3f)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(icon)) {
            AppMark(app.pkg, app.name, app.icon, size = icon, ownIcon = true)
            SourceMark(live = false, Modifier.align(Alignment.BottomEnd).offset(5.dp, 5.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, style = HeadNameStyle, color = Tt.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                app.developer.ifBlank { app.pkg },
                style = DeveloperStyle,
                color = Tt.colors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The line over the button: the first thing missing, or what the seat takes, centred on one line.
 * A step or a warning can be tapped to scroll to its part.
 */
@Composable
internal fun SeatHintRow(
    hint: SeatHint,
    onSpot: (SeatSpot) -> Unit,
) {
    val t = Tt.colors
    val spot = hint.spot
    val tone =
        when (hint.kind) {
            SeatHintKind.TODO -> t.text2
            SeatHintKind.WARN -> t.warn
            SeatHintKind.READY, SeatHintKind.BUSY -> t.text3
        }
    val style = if (hint.kind == SeatHintKind.READY || hint.kind == SeatHintKind.BUSY) HintQuietStyle else HintStyle
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 20.dp)
            .then(if (spot != null) Modifier.clickable(role = Role.Button) { onSpot(spot) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val mark =
            when (hint.kind) {
                SeatHintKind.TODO -> Lucide.CircleDashed to t.accentInk
                SeatHintKind.WARN -> Lucide.TriangleAlert to t.warn
                SeatHintKind.READY -> Lucide.Coins to t.text3
                SeatHintKind.BUSY -> null
            }
        if (mark == null) {
            Text(hint.text, style = style, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        } else {
            IconLine(mark.first, mark.second, AnnotatedString(hint.text), style, tone, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

private const val LEAD = "lead"

/**
 * A line whose icon is its first glyph, so a wrapped line runs back to the left edge instead of
 * hanging under the icon. Every warning, notice and hint on the seat page is drawn this way.
 */
@Composable
internal fun IconLine(
    icon: ImageVector,
    tint: Color,
    text: AnnotatedString,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 14.dp,
    gap: Dp = 6.dp,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    val density = LocalDensity.current
    val slot = with(density) { (iconSize + gap).toSp() }
    val high = with(density) { iconSize.toSp() }
    val inline =
        mapOf(
            LEAD to
                InlineTextContent(Placeholder(slot, high, PlaceholderVerticalAlign.TextCenter)) {
                    Box(Modifier.fillMaxSize()) {
                        Icon(icon, null, Modifier.size(iconSize).align(Alignment.CenterStart), tint = tint)
                    }
                },
        )
    Text(
        buildAnnotatedString {
            appendInlineContent(LEAD, " ")
            append(text)
        },
        modifier,
        style = style,
        color = color,
        textAlign = textAlign ?: TextAlign.Unspecified,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = inline,
    )
}

/** The page before its app has arrived, in the page's own shapes: the head, the group, then the details. */
@Composable
internal fun SeatPageSkeleton() {
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "Loading the seat page" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(width = AppTileIcon, height = AppTileIcon, corner = AppTileIcon * 0.3f)
            Spacer(Modifier.width(12.dp))
            Column {
                Skeleton(width = 130.dp, height = 14.dp)
                Spacer(Modifier.height(8.dp))
                Skeleton(width = 90.dp, height = 11.dp)
            }
        }
        Spacer(Modifier.height(24.dp))
        Skeleton(width = 120.dp, height = 16.dp)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth(0.7f)) { Skeleton(height = 12.dp) }
        Spacer(Modifier.height(12.dp))
        Skeleton(width = 220.dp, height = 14.dp)
        Spacer(Modifier.height(8.dp))
        Skeleton(width = 120.dp, height = 11.dp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { Skeleton(height = 34.dp, corner = 10.dp) }
            Box(Modifier.weight(1f)) { Skeleton(height = 34.dp, corner = 10.dp) }
        }
        Spacer(Modifier.height(32.dp))
        Skeleton(width = 100.dp, height = 16.dp)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(width = 40.dp, height = 40.dp, corner = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Skeleton(width = 150.dp, height = 14.dp)
                Spacer(Modifier.height(8.dp))
                Skeleton(width = 100.dp, height = 11.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(width = 40.dp, height = 40.dp, corner = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Skeleton(width = 120.dp, height = 14.dp)
                Spacer(Modifier.height(8.dp))
                Skeleton(width = 110.dp, height = 11.dp)
            }
        }
    }
}
