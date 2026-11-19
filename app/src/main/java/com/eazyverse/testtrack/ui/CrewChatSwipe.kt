package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Reply
import com.eazyverse.testtrack.domain.ChatSwipe
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.launch

/*
 * Swipe to reply, Messenger's way: a message dragged toward the middle follows the finger with
 * growing resistance, a reply mark fades in behind it, a light tick says the threshold is passed,
 * and letting go there replies. The rules live in ChatSwipe; this draws and listens.
 */

/** How far one message is being dragged, in dp of finger travel toward the middle. */
internal class ReplySwipe {
    var drag by mutableFloatStateOf(0f)
}

/** The mark's width, and how far past the screen edge it waits before the message moves in. */
private const val MARK_DP = 32f
private const val MARK_GAP_DP = 8f

/**
 * Lets this row be dragged toward the middle (right for others' messages, left for your own).
 * Only a clearly sideways drag starts it, so scrolling the thread and a tap on a bubble keep working.
 * Put it before anything that moves the row, so the finger is measured against the screen.
 */
@Composable
internal fun Modifier.swipeToReply(
    swipe: ReplySwipe,
    enabled: Boolean,
    mine: Boolean,
    onReply: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val reply by rememberUpdatedState(onReply)
    val sign = if (mine) -1f else 1f
    return this
        .pointerInput(enabled, mine) {
            if (!enabled) return@pointerInput
            val slop = viewConfiguration.touchSlop
            val unit = density
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var dx = 0f
                var dy = 0f
                var swiping = false
                var released = false
                var ticked = false
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    // The list's own scroll, or something else, has taken the touch.
                    if (!swiping && change.isConsumed) break
                    if (change.previousPressed && !change.pressed) {
                        released = true
                        break
                    }
                    if (!change.pressed) break
                    val delta = change.positionChange()
                    dx += delta.x
                    dy += delta.y
                    if (!swiping) {
                        when (ChatSwipe.start(dx, dy, slop, mine)) {
                            ChatSwipe.Start.WAIT -> continue
                            ChatSwipe.Start.SKIP -> break
                            ChatSwipe.Start.SWIPE -> swiping = true
                        }
                    }
                    change.consume()
                    swipe.drag = ((ChatSwipe.toward(dx, mine) - slop) / unit).coerceAtLeast(0f)
                    val past = ChatSwipe.passed(swipe.drag)
                    if (past && !ticked) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    ticked = past
                }
                if (swiping) {
                    val go = released && ChatSwipe.passed(swipe.drag)
                    scope.launch {
                        animate(swipe.drag, 0f, animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) { v, _ ->
                            swipe.drag = v.coerceAtLeast(0f)
                        }
                    }
                    if (go) reply()
                }
            }
        }.graphicsLayer { translationX = sign * ChatSwipe.travel(swipe.drag) * density }
}

/** The reply mark, behind the row: it comes in from the screen edge as the message moves, fading and growing. */
@Composable
internal fun BoxScope.ReplyMark(
    swipe: ReplySwipe,
    mine: Boolean,
) {
    val t = Tt.colors
    val sign = if (mine) -1f else 1f
    Box(
        Modifier
            .align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
            .graphicsLayer {
                val p = ChatSwipe.progress(swipe.drag)
                // Trails the message's edge, then rests a little in from the screen's.
                val inward = (ChatSwipe.travel(swipe.drag) - MARK_DP - MARK_GAP_DP).coerceAtMost(MARK_GAP_DP / 2)
                translationX = sign * inward * density
                alpha = p
                scaleX = 0.6f + 0.4f * p
                scaleY = 0.6f + 0.4f * p
            }.size(MARK_DP.dp)
            .clip(CircleShape)
            .background(t.glass2),
        contentAlignment = Alignment.Center,
    ) { Icon(Lucide.Reply, null, Modifier.size(16.dp), tint = t.text2) }
}
