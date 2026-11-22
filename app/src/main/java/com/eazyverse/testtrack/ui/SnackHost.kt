package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.WifiOff
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The one place snacks are drawn: a full-size layer over every page, placed once at the app's root.
 *
 * It sits 8 above the highest [snackFloor] on screen, the keyboard or the navigation bar, whichever
 * is highest. When a page goes, it holds where it was until the next page reports its floor, then
 * moves there once, so it never drops and climbs back. Its time runs on across pages.
 */
@Composable
fun SnackHost(
    modifier: Modifier = Modifier,
    sheetLevel: Int? = null,
) {
    // The root host steps aside while a sheet is open, since the sheet is a window of its own and
    // would hide it. The top sheet's host is the one that draws and keeps time; one below it waits.
    val covered = if (sheetLevel == null) SnackPlace.overlays > 0 else sheetLevel != SnackPlace.overlays - 1
    val density = LocalDensity.current
    val ime = WindowInsets.ime.getBottom(density)
    val bars = WindowInsets.navigationBars.getBottom(density)
    val gap = with(density) { SnackGap.toPx() }
    var hostBottom by remember { mutableFloatStateOf(0f) }
    // A sheet's host has its own window, where the page's floors mean nothing.
    val floor = if (sheetLevel == null) SnackPlace.floors.values.minOrNull() else null
    val target = maxOf(floor?.let { hostBottom - it } ?: 0f, ime.toFloat(), bars.toFloat()) + gap
    val targetNow by rememberUpdatedState(target)
    val lift = remember { Animatable(target) }
    LaunchedEffect(Unit) {
        var was = false
        snapshotFlow { targetNow to (Snacks.shown != null) }.collectLatest { (to, showing) ->
            val appearing = showing && !was
            was = showing
            when {
                // Once the last one has faded, so it never jumps on its way out.
                !showing -> {
                    delay(OUT_MS.toLong())
                    lift.snapTo(to)
                }
                // A new one starts where it belongs.
                appearing -> lift.snapTo(to)
                else -> {
                    // Going down: perhaps only a page between floors. Wait for the next one first.
                    if (to < lift.value) delay(FLOOR_GRACE_MS)
                    lift.animateTo(to, tween(MOVE_MS))
                }
            }
        }
    }

    val shown = Snacks.shown
    var pressed by remember { mutableStateOf(false) }
    val a11y = LocalAccessibilityManager.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(shown?.id, covered) {
        if (covered) return@LaunchedEffect
        val item = shown ?: return@LaunchedEffect
        if (item.left < 0) {
            when (item.snack.tone) {
                SnackTone.DONE -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                SnackTone.ERROR -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
                else -> Unit
            }
        }
        if (item.snack.sticky) return@LaunchedEffect
        if (item.left < 0) {
            val base = item.snack.baseMillis
            item.left =
                a11y?.calculateRecommendedTimeoutMillis(
                    base,
                    containsIcons = true,
                    containsText = true,
                    containsControls = item.snack.action != null,
                ) ?: base
        }
        // With TalkBack on, one with a button waits for the reader.
        if (item.left == Long.MAX_VALUE) return@LaunchedEffect
        while (item.left > 0) {
            if (pressed) {
                snapshotFlow { pressed }.first { !it }
                continue
            }
            val step = minOf(item.left, TICK_MS)
            delay(step)
            item.left -= step
        }
        Snacks.gone(item.id)
    }

    if (covered) return
    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { hostBottom = it.boundsInRoot().bottom },
    ) {
        AnimatedContent(
            shown,
            Modifier
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, -lift.value.roundToInt()) }
                .padding(horizontal = SnackSide),
            transitionSpec = {
                (fadeIn(tween(MOVE_MS)) + slideInVertically(tween(MOVE_MS)) { it / 2 }) togetherWith fadeOut(tween(OUT_MS))
            },
            contentKey = { it?.look },
            label = "snack",
        ) { item ->
            if (item == null) {
                Spacer(Modifier.fillMaxWidth())
            } else {
                SwipeAway(item, onPress = { pressed = it }) {
                    SnackView(item.snack) {
                        Snacks.gone(item)
                        item.snack.action
                            ?.onClick
                            ?.invoke()
                    }
                }
            }
        }
    }
}

/** Swiped left or right far enough, it goes; otherwise it springs back. A press pauses its time. */
@Composable
private fun SwipeAway(
    item: Shown,
    onPress: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dx = remember(item.id) { Animatable(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val onPressNow by rememberUpdatedState(onPress)
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged {
                width = it.width.toFloat().coerceAtLeast(1f)
                SnackPlace.height = it.height.toFloat()
            }.offset { IntOffset(dx.value.roundToInt(), 0) }
            .graphicsLayer { alpha = 1f - (abs(dx.value) / width).coerceIn(0f, 1f) }
            .pointerInput(item.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPressNow(true)
                    waitForUpOrCancellation()
                    onPressNow(false)
                }
            }.draggable(
                rememberDraggableState { delta -> scope.launch { dx.snapTo(dx.value + delta) } },
                Orientation.Horizontal,
                onDragStopped = { velocity ->
                    onPressNow(false)
                    if (abs(dx.value) > width * SWIPE_SHARE || abs(velocity) > FLING_PX) {
                        dx.animateTo(if (dx.value + velocity / 8 < 0) -width else width, tween(OUT_MS))
                        Snacks.gone(item)
                    } else {
                        dx.animateTo(0f, tween(OUT_MS))
                    }
                },
            ).semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction("Dismiss") {
                            Snacks.gone(item)
                            true
                        },
                    )
            },
    ) { content() }
}

/** The bar itself: the tone's mark, up to two lines, and an optional button. No close mark. */
@Composable
private fun SnackView(
    snack: Snack,
    onAction: () -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(14.dp)
    val (mark, ink) =
        when (snack.tone) {
            SnackTone.INFO -> Lucide.Info to t.inverseAccent
            SnackTone.DONE -> Lucide.CircleCheck to t.snackOk
            SnackTone.ERROR -> Lucide.CircleAlert to t.snackBad
            SnackTone.OFFLINE -> Lucide.WifiOff to t.snackWarn
        }
    val urgent = snack.tone == SnackTone.ERROR || snack.tone == SnackTone.OFFLINE
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .shadow(10.dp, shape, ambientColor = t.shadow, spotColor = t.shadow)
            .clip(shape)
            .background(t.inverse)
            .semantics { liveRegion = if (urgent) LiveRegionMode.Assertive else LiveRegionMode.Polite }
            .padding(start = 14.dp, end = if (snack.action != null) 4.dp else 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(snack.icon ?: mark, null, Modifier.padding(top = 14.5.dp).size(18.dp), tint = ink)
        Spacer(Modifier.width(10.dp))
        Text(
            snack.text,
            Modifier.weight(1f).padding(vertical = 14.dp),
            style = tileStyle(14f, 19f, FontWeight.Medium),
            color = t.onInverse,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        snack.action?.let { action ->
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .align(Alignment.CenterVertically)
                    .padding(vertical = 4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(action.label, style = tileStyle(14.5f, 19f, FontWeight.Bold), color = t.inverseAccent, maxLines = 1)
            }
        }
    }
}

/** 8 above the floor. */
private val SnackGap = 8.dp

/** 16 from each side. */
private val SnackSide = 16.dp

/** How long a dropped floor is held for the next page's to arrive. */
private const val FLOOR_GRACE_MS = 120L
private const val MOVE_MS = 200
private const val OUT_MS = 160
private const val TICK_MS = 100L
private const val SWIPE_SHARE = 0.35f
private const val FLING_PX = 1_500f
