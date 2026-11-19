package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.ImageOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.proofSource
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/*
 * A chat picture on its own, over everything, as chat apps show one: on black, pinched or double
 * tapped to zoom and moved while zoomed, with who sent it and when along the top. Pulling it up or
 * down at its normal size lets it go.
 */

/** How far a pull goes before letting go closes the picture. */
private val CloseAfter = 120.dp

/** [line]'s picture whole. [who] is how its sender is named; [caption] its words, if any. */
@Composable
internal fun ChatImageViewer(
    line: ChatLine,
    who: String,
    caption: String,
    onDismiss: () -> Unit,
) {
    if (line.picture == null) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) { ViewerPage(line, who, caption, onDismiss) }
}

@Composable
private fun ViewerPage(
    line: ChatLine,
    who: String,
    caption: String,
    onDismiss: () -> Unit,
) {
    val picture = line.picture ?: return
    val t = Tt.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val closeAfter = with(LocalDensity.current) { CloseAfter.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val local = picture.local.isNotBlank()
    val request =
        remember(picture.id, picture.local) {
            ImageRequest
                .Builder(context)
                .data(if (local) picture.local else proofSource(picture.id))
                .size(Size.ORIGINAL)
                .build()
        }
    val painter =
        if (local) rememberAsyncImagePainter(request) else rememberAsyncImagePainter(request, imageLoader = rememberChatLoader())
    val state = painter.state
    val known = painter.intrinsicSize
    val ratio =
        when {
            state is AsyncImagePainter.State.Success && known.width > 0f && known.height > 0f -> known.width / known.height
            picture.width > 0 && picture.height > 0 -> picture.width.toFloat() / picture.height
            else -> 0.75f
        }
    val fade = 1f - (abs(pull) / (closeAfter * 3f)).coerceIn(0f, 0.6f)
    val settle: () -> Unit = { scope.launch { animate(pull, 0f) { value, _ -> pull = value } } }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(t.photo.copy(alpha = fade))
            .pointerInput(Unit) {
                // A zoomed picture keeps its own drags, so this only sees a pull at normal size.
                detectVerticalDragGestures(
                    onDragEnd = { if (abs(pull) > closeAfter) onDismiss() else settle() },
                    onDragCancel = settle,
                ) { change, dy ->
                    change.consume()
                    pull += dy
                }
            },
    ) {
        // As wide as the screen, or as tall, whichever the picture meets first.
        val wide = ratio >= maxWidth / maxHeight
        Box(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer { translationY = pull }
                .then(if (wide) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                .aspectRatio(ratio, matchHeightConstraintsFirst = !wide),
        ) {
            when (state) {
                is AsyncImagePainter.State.Error -> ViewerMissing()
                is AsyncImagePainter.State.Success -> ZoomableImage(painter, "Picture from $who", Modifier.fillMaxSize())
                else -> BoxWithConstraints(Modifier.fillMaxSize()) { Skeleton(maxWidth, maxHeight, 0.dp) }
            }
        }
        ViewerBar(who, line, Modifier.align(Alignment.TopCenter).alpha(fade), onDismiss)
        ViewerFoot(caption, ChatThread.pictureLeft(picture.expiresAt), Modifier.align(Alignment.BottomCenter).alpha(fade))
    }
}

/** Close, then who sent it and when. */
@Composable
private fun ViewerBar(
    who: String,
    line: ChatLine,
    modifier: Modifier,
    onDismiss: () -> Unit,
) {
    val t = Tt.colors
    val day = line.at?.atZone(ZoneId.systemDefault())?.toLocalDate()
    val sent =
        listOfNotNull(day?.let { ChatThread.dayLabel(it, LocalDate.now()) }, ChatThread.clock(line.at).ifBlank { null })
            .joinToString(" at ")
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(t.photoScrim, Color.Transparent)))
            .statusBarsPadding()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss) { Icon(Lucide.X, "Close", Modifier.size(24.dp), tint = t.onPhoto) }
        Spacer(Modifier.width(4.dp))
        Column(Modifier.weight(1f)) {
            Text(
                who,
                style = chatStyle(16f, 21f),
                fontWeight = FontWeight.SemiBold,
                color = t.onPhoto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sent.isNotBlank()) Text(sent, style = chatStyle(12.5f, 17f), color = t.onPhoto.copy(alpha = 0.75f), maxLines = 1)
        }
    }
}

/** The caption under the picture, and how long it is kept. */
@Composable
private fun ViewerFoot(
    caption: String,
    left: String,
    modifier: Modifier,
) {
    if (caption.isBlank() && left.isBlank()) return
    val t = Tt.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, t.photoScrim)))
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (caption.isNotBlank()) {
            Text(caption, style = chatStyle(15f, 21f), color = t.onPhoto, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        if (left.isNotBlank()) {
            if (caption.isNotBlank()) Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Clock, null, Modifier.size(12.dp), tint = t.onPhoto.copy(alpha = 0.75f))
                Spacer(Modifier.width(4.dp))
                Text(left, style = chatStyle(12f, 16f), color = t.onPhoto.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun ViewerMissing() {
    val t = Tt.colors
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Lucide.ImageOff, null, Modifier.size(28.dp), tint = t.onPhoto.copy(alpha = 0.75f))
        Spacer(Modifier.height(8.dp))
        Text("Couldn't load the picture", style = chatStyle(14f, 19f), color = t.onPhoto.copy(alpha = 0.75f))
    }
}
