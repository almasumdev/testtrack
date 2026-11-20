package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
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
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Barlow
import com.eazyverse.testtrack.ui.theme.PlayBlue
import com.eazyverse.testtrack.ui.theme.PlayGreen
import com.eazyverse.testtrack.ui.theme.PlayRed
import com.eazyverse.testtrack.ui.theme.PlayYellow
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The pieces of a My apps tile: its frame, the mark on the icon's corner, the status line and the
 * mark for a problem (its callout is in ProblemCallout). The tile itself is AppCard; the loading tile draws the same frame.
 */

/** Tags a whole tile, and the text column inside it, so a test can hold the column to the icon's height. */
internal const val APP_TILE_TAG = "app-tile"
internal const val APP_TEXT_TAG = "app-text"

internal val AppTileIcon = 52.dp
internal val AppTilePadding = 12.dp

// The text column is 19 + 19 + 14 = 52dp at most, the icon's height, with the font's own padding
// trimmed the way Requests trims it, so a long name cannot make a tile taller than a short one.
@Suppress("DEPRECATION")
internal fun tileStyle(
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

/** The tile's outline: at least 76dp tall, on the page colour with a 1dp line. Click and padding go after it. */
@Composable
internal fun Modifier.appTileFrame(): Modifier =
    this
        .fillMaxWidth()
        .heightIn(min = 76.dp)
        .tileFrame()

/**
 * Where people find the app, on the icon's bottom right corner: the Play logo for a live app, a
 * flask of coloured liquid for a closed test, both on a light disc. A ring in the page colour cuts
 * it out.
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
            .background(t.sheet, CircleShape)
            .border(2.dp, t.bg, CircleShape)
            .semantics { contentDescription = if (live) "Live on Google Play" else "Closed test" },
        contentAlignment = Alignment.Center,
    ) {
        if (live) {
            Image(rememberPlayLogo(), null, Modifier.size(12.dp))
        } else {
            Image(rememberFlask(t.flaskLiquid, t.flaskGlass), null, Modifier.size(12.dp))
        }
    }
}

// The Play logo as four facets on a 24 grid: blue on the left, green above, red below and the
// yellow tip between them. Colours come from the brand constants in Tokens.kt.
@Composable
private fun rememberPlayLogo(): ImageVector =
    remember {
        ImageVector
            .Builder("PlayLogo", 12.dp, 12.dp, 24f, 24f)
            .facet(PLAY_BLUE_FACET, PlayBlue)
            .facet(PLAY_GREEN_FACET, PlayGreen)
            .facet(PLAY_RED_FACET, PlayRed)
            .facet(PLAY_YELLOW_FACET, PlayYellow)
            .build()
    }

private fun ImageVector.Builder.facet(
    path: String,
    color: Color,
) = addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(color))

private const val PLAY_BLUE_FACET = "M4 2L13 12L4 22Z"
private const val PLAY_GREEN_FACET = "M4 2L16.5 9.35L13 12Z"
private const val PLAY_RED_FACET = "M4 22L13 12L16.5 14.65Z"
private const val PLAY_YELLOW_FACET = "M16.5 9.35L21 12L16.5 14.65L13 12Z"

// A flask: the liquid is filled first, then the glass outline is stroked over it.
@Composable
private fun rememberFlask(
    liquid: Color,
    glass: Color,
): ImageVector =
    remember(liquid, glass) {
        ImageVector
            .Builder("Flask", 12.dp, 12.dp, 24f, 24f)
            .addPath(PathParser().parsePathString(FLASK_LIQUID).toNodes(), fill = SolidColor(liquid))
            .addPath(
                PathParser().parsePathString(FLASK_GLASS).toNodes(),
                stroke = SolidColor(glass),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ).build()
    }

private const val FLASK_LIQUID = "M7.9 14.3Q10 13.2 12 14.3T16.1 14.3L19.1 20.3Q19.4 21.6 18.1 21.6H5.9Q4.6 21.6 4.9 20.3Z"
private const val FLASK_GLASS =
    "M10 3v6.5a2 2 0 0 1-.2.9L4.8 20.4a1 1 0 0 0 .9 1.4h12.6a1 1 0 0 0 .9-1.4L14.2 10.4a2 2 0 0 1-.2-.9V3M8.6 3h6.8"

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
 * The mark for an app with something wrong: a 20dp alert in a 32dp target. Tapping it opens a
 * callout saying what is wrong; it never opens the app's page. Red, or amber when the only thing is
 * that the app is not verified yet. While its callout is open it sits on a soft circle of that tone.
 */
@Composable
internal fun ProblemMark(
    app: AppEntry,
    problems: List<AppProblem>,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    val warnOnly = problems == listOf(AppProblem.NOT_VERIFIED)
    val soft = if (warnOnly) t.warnBg else t.badBg
    Box(
        modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (open) soft else Color.Transparent)
            .semantics { contentDescription = problems.joinToString(". ") { it.title(app) } }
            .clickable(onClickLabel = "Show what's wrong", role = Role.Button) { onOpenChange(!open) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.CircleAlert, null, Modifier.size(20.dp), tint = if (warnOnly) t.warn else t.bad)
        ProblemCallout(app, problems, open, onClose = { onOpenChange(false) }, onOpen = onOpen)
    }
}

/**
 * One start row on the app page, a Crew test or Get reviews block: an icon tile, a title with a line
 * under it, and an optional action at the end. It is a [CrewCard], so it has the corner and hairline of
 * every other tile on the page and the Circle rows. The line wraps to two rows of words instead of
 * being cut. [pill] is a soft [RowPill] that does what tapping the row does; [action] is anything else.
 * Both are held to [ActionMaxWidth] so they never take the line's room. No chevron either way.
 */
@Composable
internal fun AppStartRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    pill: String? = null,
) {
    val t = Tt.colors
    CrewCard(
        modifier.heightIn(min = 60.dp),
        padding = PaddingValues(12.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = t.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                    color = t.text3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (pill != null && onClick != null) {
                Spacer(Modifier.width(12.dp))
                RowPill(
                    pill,
                    PillKind.SOFT,
                    modifier = Modifier.widthIn(max = ActionMaxWidth),
                    size = PillSize.SMALL,
                    touchTarget = false,
                    onClick = onClick,
                )
            } else if (action != null) {
                Spacer(Modifier.width(12.dp))
                action()
            }
        }
    }
}

/** The most room an [AppStartRow] gives its action, so the line beside it keeps its words. */
internal val ActionMaxWidth = 96.dp
