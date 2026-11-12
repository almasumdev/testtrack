package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt
import kotlin.math.roundToInt

/*
 * What a tile's alert mark opens: a card on the sheet colour with a caret pointing at the mark, a
 * row for each problem and a link to the app's page. Drawn as one path so the 1dp line runs round
 * the caret. See docs/design/my-apps/my-apps.html, Problem callout, Option A.
 */

private val CalloutMaxWidth = 260.dp
private val CalloutScreenGap = 16.dp
private val CaretWidth = 16.dp
private val CaretHeight = 8.dp
private val CaretTip = 1.5.dp

// The mark's 20dp icon sits 6dp inside its 32dp target; the callout is measured from the icon.
private val MarkInset = 6.dp
private val MarkGap = 4.dp
private val DockGap = 8.dp

private val TitleStyle = tileStyle(13.5f, 17f, FontWeight.SemiBold)
private val SentenceStyle = tileStyle(13f, 18f, FontWeight.Normal)

/** Where the callout goes, in window pixels, and where its caret points. */
internal data class CalloutPlacement(
    val x: Int,
    val y: Int,
    /** The caret's centre from the callout's left edge, before the shape keeps it off the corners. */
    val caretX: Float,
    /** The caret is on the top edge, pointing up at the mark; false when the callout sits above it. */
    val caretUp: Boolean,
)

/** The callout's width: 260dp, or what is left between the screen's sides on a narrow one. */
internal fun calloutWidthPx(
    windowWidthPx: Int,
    density: Float,
): Int = minOf(CalloutMaxWidth.value * density, windowWidthPx - 2 * CalloutScreenGap.value * density).roundToInt()

/**
 * Pure placement: right edge 16dp in from the window's, under the mark with the caret up, or above it
 * with the caret down when the card would run into the dock. [popup] includes the caret's 8dp.
 */
internal fun placeCallout(
    anchor: IntRect,
    window: IntSize,
    popup: IntSize,
    density: Float,
    dockRoomPx: Int,
): CalloutPlacement {
    val gap = (CalloutScreenGap.value * density).roundToInt()
    val inset = MarkInset.value * density
    val between = MarkGap.value * density
    val x = (window.width - gap - popup.width).coerceAtLeast(gap)
    val below = (anchor.bottom - inset + between).roundToInt()
    val flip = below + popup.height > window.height - dockRoomPx - (DockGap.value * density).roundToInt()
    val y = if (flip) (anchor.top + inset - between).roundToInt() - popup.height else below
    return CalloutPlacement(x, y, (anchor.left + anchor.right) / 2f - x, !flip)
}

/** The caret's centre kept 22dp (corner plus half the caret) from either end of the card. */
internal fun clampCaret(
    caretX: Float,
    width: Float,
    density: Float,
): Float {
    val keep = (TileRadius.value + CaretWidth.value / 2) * density
    return if (width <= 2 * keep) width / 2 else caretX.coerceIn(keep, width - keep)
}

/** The card with its caret on the top edge when [caretUp], else on the bottom edge. */
internal class CalloutShape(
    private val caretX: Float,
    private val caretUp: Boolean,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val d = density.density
        val w = size.width
        val h = size.height
        val r = TileRadius.value * d
        val half = CaretWidth.value * d / 2
        val ch = CaretHeight.value * d
        val tip = CaretTip.value * d
        val cx = clampCaret(caretX, w, d)
        val top = if (caretUp) ch else 0f
        val bottom = if (caretUp) h else h - ch
        val path =
            Path().apply {
                moveTo(r, top)
                if (caretUp) {
                    lineTo(cx - half, top)
                    lineTo(cx - tip, tip)
                    quadraticTo(cx, 0f, cx + tip, tip)
                    lineTo(cx + half, top)
                }
                lineTo(w - r, top)
                arcTo(Rect(w - 2 * r, top, w, top + 2 * r), -90f, 90f, false)
                lineTo(w, bottom - r)
                arcTo(Rect(w - 2 * r, bottom - 2 * r, w, bottom), 0f, 90f, false)
                if (!caretUp) {
                    lineTo(cx + half, bottom)
                    lineTo(cx + tip, bottom + ch - tip)
                    quadraticTo(cx, bottom + ch, cx - tip, bottom + ch - tip)
                    lineTo(cx - half, bottom)
                }
                lineTo(r, bottom)
                arcTo(Rect(0f, bottom - 2 * r, 2 * r, bottom), 90f, 90f, false)
                lineTo(0f, top + r)
                arcTo(Rect(0f, top, 2 * r, top + 2 * r), 180f, 90f, false)
                close()
            }
        return Outline.Generic(path)
    }
}

/** Places the callout and tells its content where the caret goes. */
internal class CalloutPositionProvider(
    private val density: Float,
    private val dockRoomPx: Int,
) : PopupPositionProvider {
    var caretX by mutableFloatStateOf(0f)
        private set
    var caretUp by mutableStateOf(true)
        private set

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val placed = placeCallout(anchorBounds, windowSize, popupContentSize, density, dockRoomPx)
        caretX = placed.caretX
        caretUp = placed.caretUp
        return IntOffset(placed.x, placed.y)
    }
}

/**
 * The callout for one app's problems, anchored to the mark it is called from. Stays composed until
 * its fade out ends. A tap outside, Back or [onClose] closes it; a tap outside does nothing else.
 */
@Composable
internal fun ProblemCallout(
    app: AppEntry,
    problems: List<AppProblem>,
    open: Boolean,
    onClose: () -> Unit,
    onOpen: () -> Unit,
) {
    val shown = remember { MutableTransitionState(false) }
    shown.targetState = open
    if (!shown.currentState && !shown.targetState) return

    val density = LocalDensity.current.density
    val dockPx = (dockRoom().value * density).roundToInt()
    val provider = remember(density, dockPx) { CalloutPositionProvider(density, dockPx) }
    val width = minOf(CalloutMaxWidth, (LocalConfiguration.current.screenWidthDp - 32).dp)
    val widthPx = width.value * density
    val originX = clampCaret(provider.caretX, widthPx, density) / widthPx
    val originY = if (provider.caretUp) 0f else 1f
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onClose,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        AnimatedVisibility(
            visibleState = shown,
            enter =
                fadeIn(tween(150, easing = FastOutSlowInEasing)) +
                    scaleIn(tween(150, easing = FastOutSlowInEasing), 0.92f, TransformOrigin(originX, originY)),
            exit = fadeOut(tween(100)),
        ) {
            CalloutCard(app, problems, width, provider.caretX, provider.caretUp, onClose, onOpen)
        }
    }
}

@Composable
private fun CalloutCard(
    app: AppEntry,
    problems: List<AppProblem>,
    width: Dp,
    caretX: Float,
    caretUp: Boolean,
    onClose: () -> Unit,
    onOpen: () -> Unit,
) {
    val t = Tt.colors
    val shape = remember(caretX, caretUp) { CalloutShape(caretX, caretUp) }
    Column(
        Modifier
            .width(width)
            .background(t.sheet, shape)
            .border(1.dp, t.glassBorder, shape)
            .semantics {
                paneTitle = "What's wrong with ${app.name}"
                liveRegion = LiveRegionMode.Polite
            }.padding(
                start = 14.dp,
                end = 14.dp,
                top = 12.dp + if (caretUp) CaretHeight else 0.dp,
                bottom = 12.dp + if (caretUp) 0.dp else CaretHeight,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        problems.forEach { problem ->
            Row {
                Icon(
                    Lucide.CircleAlert,
                    null,
                    Modifier.padding(top = 1.dp).size(16.dp),
                    tint = if (problem == AppProblem.NOT_VERIFIED) t.warn else t.bad,
                )
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(problem.title(app), style = TitleStyle, color = t.text)
                    Text(problem.sentence(app), Modifier.padding(top = 1.dp), style = SentenceStyle, color = t.text2)
                }
            }
        }
        Row(
            Modifier
                .align(Alignment.End)
                .clickable(role = Role.Button) {
                    onClose()
                    onOpen()
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Open app", style = TitleStyle, color = t.accentInk)
            Icon(Lucide.ChevronRight, null, Modifier.size(16.dp), tint = t.accentInk)
        }
    }
}
