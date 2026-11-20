package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDashed
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
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
private val HeadEditStyle = tileStyle(16f, 17f, FontWeight.SemiBold)
private val CountStyle = tileStyle(11.5f, 14f, FontWeight.Normal)
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
        fun warn(text: String) = SeatStatus(text, false)
    }
}

/**
 * A part's heading on the seat page: 16 Bold, plain text, no box, and at its far end one short
 * status, or whatever [end] draws there. 8dp under it; [gap] is 0 when the part is only its heading.
 */
@Composable
internal fun SeatHeading(
    text: String,
    status: SeatStatus? = null,
    gap: Dp = 8.dp,
    end: (@Composable () -> Unit)? = null,
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
        if (end != null) {
            Spacer(Modifier.width(8.dp))
            end()
        }
    }
}

/**
 * The app the seat is for, as a My apps tile without the box: its icon with the closed-test flask on
 * the corner, the name the crew sees on up to two lines, its developer, and a pencil. Tapping the row
 * edits the name in place: a field of up to two lines with a 2dp underline under the last, the count on
 * the developer's line, and a check that saves it. Done, the check or leaving the field saves it and goes
 * back to resting; a blank name stays open, with the app's own name as its hint. [focusAsk] goes up each
 * time the page's hint asks for the name, which puts the cursor in it. Resting, the text is at most
 * 18 + 18 + 2 + 14 = 52dp; editing, 17 + 17 + 4 + 14 = 52dp: the icon's height either way.
 */
@Composable
internal fun SeatAppHead(
    app: AppEntry,
    name: String,
    onRename: (String) -> Unit,
    focusAsk: Int,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    // Scales with the text, but only up to 1.3x, as a My apps tile does, so the column keeps fitting.
    val icon = AppTileIcon * LocalDensity.current.fontScale.coerceIn(1f, 1.3f)
    var editing by rememberSaveable { mutableStateOf(false) }
    val open = editing || name.isBlank()
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var field by remember { mutableStateOf(TextFieldValue(name, TextRange(name.length))) }
    var wantFocus by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    LaunchedEffect(name) {
        if (field.text != name) field = TextFieldValue(name, TextRange(name.length))
    }
    // Only a new ask focuses it, not the count it already had when the head came back on screen.
    var asked by remember { mutableIntStateOf(focusAsk) }
    LaunchedEffect(focusAsk) {
        if (focusAsk != asked) {
            asked = focusAsk
            editing = true
            wantFocus = true
        }
    }
    LaunchedEffect(open, wantFocus) {
        if (open && wantFocus) {
            wantFocus = false
            runCatching { focus.requestFocus() }
        }
    }
    val done = {
        onRename(field.text.trim())
        editing = false
        focusManager.clearFocus()
    }
    val developer = app.developer.ifBlank { app.pkg }
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (open) {
                    Modifier
                } else {
                    Modifier.clickable(onClickLabel = "Change the name the crew sees") {
                        editing = true
                        wantFocus = true
                    }
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(icon)) {
            AppMark(app.pkg, app.name, app.icon, size = icon, ownIcon = true)
            SourceMark(live = false, Modifier.align(Alignment.BottomEnd).offset(5.dp, 5.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (open) {
                val line = t.primary
                BasicTextField(
                    value = field,
                    onValueChange = { typed ->
                        val text = typed.text.take(FeedRules.APP_NAME_MAX)
                        field = if (text == typed.text) typed else TextFieldValue(text, TextRange(text.length))
                        onRename(text)
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focus)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    hadFocus = true
                                } else if (hadFocus) {
                                    // Leaving the field saves it, trimmed, and goes back to resting.
                                    hadFocus = false
                                    onRename(field.text.trim())
                                    editing = false
                                }
                            }.drawBehind {
                                // Under the last line, whether the name takes one line or two.
                                val high = 2.dp.toPx()
                                drawRect(line, Offset(0f, size.height - high), Size(size.width, high))
                            }.padding(bottom = 4.dp),
                    maxLines = 2,
                    textStyle = HeadEditStyle.copy(color = t.text),
                    cursorBrush = SolidColor(t.primary),
                    keyboardOptions =
                        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { done() }),
                    decorationBox = { inner ->
                        Box {
                            if (field.text.isEmpty()) {
                                Text(app.name, style = HeadEditStyle, color = t.text4, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            inner()
                        }
                    },
                )
                // The developer and the count share a baseline, with no gap under the underline.
                Row {
                    Text(
                        developer,
                        Modifier.weight(1f).alignByBaseline(),
                        style = DeveloperStyle,
                        color = t.text3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${field.text.length}/${FeedRules.APP_NAME_MAX}",
                        Modifier.alignByBaseline(),
                        style = CountStyle,
                        color = t.text4,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            } else {
                Text(name, style = HeadNameStyle, color = t.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(developer, style = DeveloperStyle, color = t.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(4.dp))
        if (open) {
            IconButton(onClick = done, modifier = Modifier.size(40.dp)) {
                Icon(Lucide.Check, "Save the name", Modifier.size(20.dp), tint = t.accentInk)
            }
        } else {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Lucide.Pencil, null, Modifier.size(18.dp), tint = t.text4)
            }
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
private const val TAIL = "tail"

/**
 * A line whose icon is its first glyph, so a wrapped line runs back to the left edge instead of
 * hanging under the icon. Every warning, notice and hint on the seat page is drawn this way.
 * [trailing] is set after the last word, in a slot [trailingWidth] wide, such as an (i).
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
    trailing: (@Composable () -> Unit)? = null,
    trailingWidth: Dp = 24.dp,
) {
    val density = LocalDensity.current
    val slot = with(density) { (iconSize + gap).toSp() }
    val high = with(density) { iconSize.toSp() }
    val lead =
        LEAD to
            InlineTextContent(Placeholder(slot, high, PlaceholderVerticalAlign.TextCenter)) {
                Box(Modifier.fillMaxSize()) {
                    Icon(icon, null, Modifier.size(iconSize).align(Alignment.CenterStart), tint = tint)
                }
            }
    val tail =
        trailing?.let { mark ->
            val wide = with(density) { trailingWidth.toSp() }
            val tall = with(density) { TrailingHeight.toSp() }
            TAIL to
                InlineTextContent(Placeholder(wide, tall, PlaceholderVerticalAlign.TextCenter)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { mark() }
                }
        }
    val inline = listOfNotNull(lead, tail).toMap()
    Text(
        buildAnnotatedString {
            appendInlineContent(LEAD, " ")
            append(text)
            if (tail != null) appendInlineContent(TAIL, " ")
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

/**
 * The page before its app has arrived, in the page's own shapes from top to bottom: the head, a
 * heading, the tile rows, the sign-in choice, the price and its three rules, the boxes, the hint and
 * the button. A taller screen gets more tile rows under them, so the whole page is filled.
 */
@Composable
internal fun SeatPageSkeleton(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clipToBounds().semantics { contentDescription = "Loading the seat page" }) {
        val extra = ((maxHeight - SkeletonPageHeight) / SkeletonRowStep).toInt().coerceAtLeast(0) + 1
        Column(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) {
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
            SkeletonHeading()
            SkeletonTileRow(150.dp, 100.dp)
            Spacer(Modifier.height(16.dp))
            SkeletonTileRow(120.dp, 110.dp)
            Spacer(Modifier.height(16.dp))
            Skeleton(width = 180.dp, height = 12.dp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { Skeleton(height = 48.dp, corner = 12.dp) }
                Box(Modifier.weight(1f)) { Skeleton(height = 48.dp, corner = 12.dp) }
            }
            Spacer(Modifier.height(16.dp))
            SkeletonTileRow(110.dp, 90.dp)
            Spacer(Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth(0.9f)) { Skeleton(height = 11.dp) }
                Box(Modifier.fillMaxWidth(0.8f)) { Skeleton(height = 11.dp) }
                Box(Modifier.fillMaxWidth(0.55f)) { Skeleton(height = 11.dp) }
            }
            Spacer(Modifier.height(32.dp))
            SkeletonHeading()
            repeat(2) {
                Row(Modifier.height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Skeleton(width = 22.dp, height = 22.dp)
                    Spacer(Modifier.width(12.dp))
                    Skeleton(width = 170.dp, height = 13.dp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Skeleton(width = 160.dp, height = 12.dp) }
            Spacer(Modifier.height(12.dp))
            Skeleton(height = 52.dp, corner = 14.dp)
            repeat(extra) {
                Spacer(Modifier.height(24.dp))
                SkeletonTileRow(if (it % 2 == 0) 140.dp else 110.dp, if (it % 2 == 0) 95.dp else 120.dp)
            }
        }
    }
}

/** The slot an [IconLine]'s trailing mark gets: a line's height, so the line does not grow. */
private val TrailingHeight = 18.dp

/** Roughly the height of the page's own shapes above, before the extra rows; one extra row and its gap. */
private val SkeletonPageHeight = 700.dp
private val SkeletonRowStep = 64.dp

@Composable
private fun SkeletonHeading() {
    Skeleton(width = 110.dp, height = 16.dp)
    Spacer(Modifier.height(11.dp))
}

@Composable
private fun SkeletonTileRow(
    top: Dp,
    under: Dp,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Skeleton(width = 40.dp, height = 40.dp, corner = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Skeleton(width = top, height = 14.dp)
            Spacer(Modifier.height(8.dp))
            Skeleton(width = under, height = 11.dp)
        }
    }
}
