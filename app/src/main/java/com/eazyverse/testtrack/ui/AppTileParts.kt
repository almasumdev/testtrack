package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Barlow
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.launch

/*
 * The pieces of a My apps tile: its frame, the mark on the icon's corner, the status line and the
 * mark for a problem. The tile itself is AppCard; the loading tile draws the same frame.
 */

/** Tags a whole tile, and the text column inside it, so a test can hold the column to the icon's height. */
internal const val APP_TILE_TAG = "app-tile"
internal const val APP_TEXT_TAG = "app-text"

internal val AppTileIcon = 52.dp
internal val AppTilePadding = 12.dp

// The text column is 19 + 19 + 14 = 52dp at most, the icon's height, with the font's own padding
// trimmed the way Requests trims it, so a long name cannot make a tile taller than a short one.
@Suppress("DEPRECATION")
private fun tileStyle(
    size: Float,
    height: Float,
    weight: FontWeight,
) = TextStyle(
    fontFamily = Barlow,
    fontSize = size.sp,
    lineHeight = height.sp,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = TicketLineHeight,
)

internal val AppTileName = tileStyle(16f, 19f, FontWeight.SemiBold)
private val AppTileStatus = tileStyle(12.5f, 14f, FontWeight.Medium)
private val AppTileStatusBold = tileStyle(12.5f, 14f, FontWeight.SemiBold)
private val TooltipText = tileStyle(13f, 18f, FontWeight.Normal)

/** The tile's outline: at least 76dp tall, on the page colour with a 1dp line. Click and padding go after it. */
@Composable
internal fun Modifier.appTileFrame(): Modifier {
    val t = Tt.colors
    val shape = RoundedCornerShape(TileRadius)
    return this
        .fillMaxWidth()
        .heightIn(min = 76.dp)
        .clip(shape)
        .background(t.bg)
        .border(1.dp, t.cardLine, shape)
}

/**
 * Where people find the app, on the icon's bottom right corner: a Play triangle on the accent for a
 * live app, a flask on the soft tile for a closed test. A ring in the page colour cuts it out.
 */
@Composable
internal fun SourceMark(
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    Box(
        modifier
            .size(20.dp)
            .background(if (live) t.accent else t.tile, CircleShape)
            .border(2.dp, t.bg, CircleShape)
            .semantics { contentDescription = if (live) "Live on Google Play" else "Closed test" },
        contentAlignment = Alignment.Center,
    ) {
        if (live) {
            Image(rememberPlayTriangle(t.onAccent), null, Modifier.size(10.dp))
        } else {
            Icon(Lucide.FlaskConical, null, Modifier.size(11.dp), tint = t.tileInk)
        }
    }
}

// Lucide's play is an outline; this one is solid. Drawn in the colour it is given, so nothing here
// writes a colour of its own.
@Composable
private fun rememberPlayTriangle(color: Color): ImageVector =
    remember(color) {
        ImageVector
            .Builder("Play", 10.dp, 10.dp, 24f, 24f)
            .addPath(
                PathParser().parsePathString(PLAY_PATH).toNodes(),
                fill = SolidColor(color),
            ).build()
    }

private const val PLAY_PATH = "M7 4.5v15a1 1 0 0 0 1.5.86l12.6-7.5a1 1 0 0 0 0-1.72L8.5 3.64A1 1 0 0 0 7 4.5Z"

/**
 * The status line: an optional 12dp icon, the phrase, and after it a crew's name. The phrase never
 * gives way; only the crew's name ends in an ellipsis when the line is short of room.
 */
@Composable
internal fun StatusRow(status: AppStatusLine) {
    val t = Tt.colors
    val tone =
        when (status.tone) {
            StatusTone.ACCENT -> t.accentInk
            StatusTone.BODY -> t.text2
            StatusTone.QUIET -> t.text3
            StatusTone.FAINT -> t.text4
        }
    val style = if (status.tone == StatusTone.ACCENT) AppTileStatusBold else AppTileStatus
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (status.mark) {
            StatusMark.DONE -> Icon(Lucide.CircleCheck, null, Modifier.size(12.dp), tint = t.ok)
            StatusMark.WAITING -> Icon(Lucide.Hourglass, null, Modifier.size(12.dp), tint = tone)
            StatusMark.NONE -> Unit
        }
        if (status.mark != StatusMark.NONE) Spacer(Modifier.width(4.dp))
        Text(status.text, style = style, color = tone, maxLines = 1, softWrap = false)
        if (status.crew.isNotEmpty()) {
            Text(
                status.crew,
                Modifier.weight(1f, fill = false),
                style = style,
                color = tone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The mark for an app with something wrong: a 20dp alert in a 32dp target. Tapping it shows what is
 * wrong, a sentence for each problem, until the next tap elsewhere; it does not open the app.
 * Red, or amber when the only thing is that the app is not verified yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProblemMark(
    sentences: List<String>,
    warnOnly: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    val tip = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberRichTooltipPositionProvider(),
        tooltip = {
            RichTooltip(colors = TooltipDefaults.richTooltipColors(containerColor = t.card, contentColor = t.text)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    sentences.forEach { Text(it, style = TooltipText, color = t.text) }
                }
            }
        },
        state = tip,
        enableUserInput = false,
    ) {
        Box(
            modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable { scope.launch { tip.show() } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Lucide.CircleAlert,
                sentences.joinToString(" "),
                Modifier.size(20.dp),
                tint = if (warnOnly) t.warn else t.bad,
            )
        }
    }
}
