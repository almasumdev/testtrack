package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.domain.ChatRow
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/*
 * A tap on a reply's quote goes to the message it quotes, as in Messenger: the thread scrolls the
 * original into the middle, then washes its bubble with a tint that fades out. The page finds the
 * original first (reading older pages if it has to); this file only moves to it and lights it.
 */

/** A request to go to message [id]. [n] tells two taps on the same quote apart, so each one moves. */
internal data class ChatJump(
    val id: String,
    val n: Int = 0,
)

/** The tint on a message gone to: held at full for a moment, then faded out, about a second in all. */
private const val FLASH_HOLD_MS = 300
private const val FLASH_FADE_MS = 750
private const val FLASH_MS = FLASH_HOLD_MS + FLASH_FADE_MS

/** Marks the bubble while its tint shows, for tests. */
internal const val JUMP_FLASH_TAG = "chat-jump-flash"

/**
 * Scrolls [state] so the row of [jump]'s message sits in the middle of the list, then hands it to
 * [onFlash] to light, and null once the tint has faded, so the bubble does not light again when it
 * is scrolled away and back. A message not among [rows] is left alone: the page only asks for one
 * it holds.
 */
@Composable
internal fun JumpWhenAsked(
    state: LazyListState,
    rows: List<ChatRow>,
    jump: ChatJump?,
    onFlash: (ChatJump?) -> Unit,
) {
    val held by rememberUpdatedState(rows)
    val flash by rememberUpdatedState(onFlash)
    LaunchedEffect(jump) {
        val asked = jump ?: return@LaunchedEffect
        val index = held.indexOfFirst { it.key == asked.id }
        if (index < 0) return@LaunchedEffect
        // Older pages just read may not be laid out yet.
        snapshotFlow { state.layoutInfo.totalItemsCount }.first { it > index }
        if (state.layoutInfo.visibleItemsInfo.none { it.index == index }) state.animateScrollToItem(index)
        val info = state.layoutInfo
        info.visibleItemsInfo.firstOrNull { it.index == index }?.let { item ->
            val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
            state.animateScrollBy((item.offset + item.size / 2 - middle).toFloat())
        }
        flash(asked)
        delay(FLASH_MS + 100L)
        flash(null)
    }
}

/** The tint over a bubble that was just gone to: full at once, held a moment, then fading out. */
@Composable
internal fun Modifier.jumpFlash(
    flash: ChatJump?,
    id: String,
): Modifier {
    val glow = remember { Animatable(0f) }
    val mine = flash?.takeIf { it.id == id }
    LaunchedEffect(mine) {
        if (mine != null) {
            glow.snapTo(1f)
            glow.animateTo(0f, tween(FLASH_FADE_MS, delayMillis = FLASH_HOLD_MS, easing = FastOutLinearInEasing))
        }
    }
    val tint = Tt.colors.jumpTint
    val lit = mine != null && glow.value > 0f
    return (if (lit) testTag(JUMP_FLASH_TAG) else this).drawWithContent {
        drawContent()
        if (glow.value > 0f) drawRect(tint.copy(alpha = tint.alpha * glow.value))
    }
}

/** While older pages are read to find a quoted message: a small pill at the top of the thread. */
@Composable
internal fun SeekingNote(modifier: Modifier = Modifier) {
    val t = Tt.colors
    Row(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(t.glass2)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = t.accentInk)
        Spacer(Modifier.width(8.dp))
        Text("Finding the message", style = chatStyle(12.5f, 17f), fontWeight = FontWeight.Medium, color = t.text2)
    }
}
