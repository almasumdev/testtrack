package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.composables.icons.lucide.ImagePlus
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.domain.ChatAllowance
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.delay

// The composer's picture button: the pictures left on its corner, and why it is grey when tapped.

/** The picture button at the start of the pill, with the pictures left on its corner. */
@Composable
internal fun PictureButton(
    view: ChatView,
    actions: ChatActions,
    ring: Color,
) {
    val t = Tt.colors
    // Pictures are off in this chat: no button, and the pill keeps the same margin it has at its end.
    if (view.limits.picturesPerDay <= 0) {
        Spacer(Modifier.width(16.dp))
        return
    }
    val canAttach = ChatAllowance.canAttach(view.messagesLeft, view.picturesLeft, view.canPost)
    var tip by remember { mutableStateOf(false) }
    Box {
        FieldButton(
            Lucide.ImagePlus,
            if (canAttach) "Send a picture" else "No pictures left today",
            48.dp,
            48.dp,
            21.dp,
            if (canAttach) t.tileInk else t.text4,
            onClick = { if (canAttach) actions.onPickPicture() else tip = true },
        )
        ChatAllowance.pictureBadge(view.messagesLeft, view.picturesLeft, view.limits, view.canPost)?.let {
            CountBadge(it, PartSize.SMALL, ring, Modifier.align(Alignment.TopEnd).offset(x = (-3).dp, y = 6.dp))
        }
        if (tip) PictureTip(pictureTip(view)) { tip = false }
    }
}

/** Why the picture button is grey, said briefly when it is tapped. */
private fun pictureTip(view: ChatView): String =
    when {
        view.messagesLeft <= 0 -> "No messages left today. More after midnight."
        else -> "No pictures left today. You get ${view.limits.picturesPerDay} more after midnight."
    }

/** A short note over the picture button, gone after a few seconds or a tap. */
@Composable
private fun PictureTip(
    text: String,
    onGone: () -> Unit,
) {
    val t = Tt.colors
    LaunchedEffect(text) {
        delay(3_000)
        onGone()
    }
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    Popup(popupPositionProvider = remember(gap) { AboveAnchor(gap) }, onDismissRequest = onGone) {
        Text(
            text,
            Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(t.inverse)
                .clickable(onClick = onGone)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = chatStyle(13f, 17f),
            color = t.onInverse,
        )
    }
}

/** Puts a popup just over what it belongs to, kept inside the window. */
private class AboveAnchor(
    private val gap: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset =
        IntOffset(
            anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width).coerceAtLeast(0),
            (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(0),
        )
}
