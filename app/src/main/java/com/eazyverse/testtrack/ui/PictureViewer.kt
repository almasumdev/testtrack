package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
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
import com.eazyverse.testtrack.domain.ChatPicture
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.proofRemoved
import com.eazyverse.testtrack.viewmodel.proofSource
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/*
 * The one place a picture is shown whole, over everything, as chat apps show one: on black, pinched
 * or double tapped to zoom and moved while zoomed, with whose it is and when along the top and its
 * words along the bottom. Pulling it up or down at its normal size lets it go. Chat pictures, a
 * thread's screenshots, a case's and a crew night's pictures all open here.
 */

/** How far a pull goes before letting go closes the picture. */
private val CloseAfter = 120.dp

/** Where a picture whole comes from. [width] and [height] are its pixels, when known, for its shape. */
internal sealed interface PictureSource {
    val width: Int
    val height: Int

    /** A picture by its ref on our server, fetched with the session; a Drive or file ref works too. */
    data class Proof(
        val ref: String,
        override val width: Int = 0,
        override val height: Int = 0,
    ) : PictureSource

    /** A picture still on this phone, such as a chat picture not sent yet. */
    data class OnPhone(
        val path: String,
        override val width: Int = 0,
        override val height: Int = 0,
    ) : PictureSource
}

/**
 * [source] whole. [title] is whose it is, [subtitle] when it was sent, both along the top;
 * [caption] its words and [footnote] a quiet line with a clock, both along the bottom. Any of them
 * may be left out.
 */
@Composable
internal fun PictureViewer(
    source: PictureSource,
    title: String? = null,
    subtitle: String? = null,
    caption: String? = null,
    footnote: String? = null,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) { ViewerPage(source, title.orEmpty(), subtitle.orEmpty(), caption.orEmpty(), footnote.orEmpty(), onDismiss) }
}

/** A chat picture as [PictureViewer] takes it: from the phone while it is still going. */
internal fun ChatPicture.source(): PictureSource =
    if (local.isNotBlank()) PictureSource.OnPhone(local, width, height) else PictureSource.Proof(id, width, height)

/** "Today at 8:15 PM" for when [line] was sent, or blank with no time. */
internal fun sentAt(line: ChatLine): String {
    val day = line.at?.atZone(ZoneId.systemDefault())?.toLocalDate()
    return listOfNotNull(day?.let { ChatThread.dayLabel(it, LocalDate.now()) }, ChatThread.clock(line.at).ifBlank { null })
        .joinToString(" at ")
}

/**
 * [proof] drawn for [ref], opening it whole in [PictureViewer] on a tap, titled [title]. A picture
 * the server has removed draws with no tap.
 */
@Composable
internal fun OpensWhole(
    ref: String,
    title: String?,
    proof: @Composable (String) -> Unit,
) {
    var open by remember(ref) { mutableStateOf(false) }
    if (proofRemoved(ref)) {
        proof(ref)
        return
    }
    Box(Modifier.clickable(onClickLabel = "Open the screenshot") { open = true }) { proof(ref) }
    if (open) PictureViewer(PictureSource.Proof(ref), title = title) { open = false }
}

@Composable
private fun ViewerPage(
    source: PictureSource,
    title: String,
    subtitle: String,
    caption: String,
    footnote: String,
    onDismiss: () -> Unit,
) {
    val t = Tt.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val closeAfter = with(LocalDensity.current) { CloseAfter.toPx() }
    var pull by remember { mutableFloatStateOf(0f) }
    val request =
        remember(source) {
            val data =
                when (source) {
                    is PictureSource.OnPhone -> source.path
                    is PictureSource.Proof -> proofSource(source.ref)
                }
            ImageRequest
                .Builder(context)
                .data(data)
                .size(Size.ORIGINAL)
                .build()
        }
    val painter =
        when (source) {
            is PictureSource.OnPhone -> rememberAsyncImagePainter(request)
            is PictureSource.Proof -> rememberAsyncImagePainter(request, imageLoader = rememberChatLoader())
        }
    val state = painter.state
    val known = painter.intrinsicSize
    val ratio =
        when {
            state is AsyncImagePainter.State.Success && known.width > 0f && known.height > 0f -> known.width / known.height
            source.width > 0 && source.height > 0 -> source.width.toFloat() / source.height
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
                is AsyncImagePainter.State.Success ->
                    ZoomableImage(painter, if (title.isNotBlank()) "Picture from $title" else "The picture", Modifier.fillMaxSize())
                else -> BoxWithConstraints(Modifier.fillMaxSize()) { Skeleton(maxWidth, maxHeight, 0.dp) }
            }
        }
        ViewerBar(title, subtitle, Modifier.align(Alignment.TopCenter).alpha(fade), onDismiss)
        ViewerFoot(caption, footnote, Modifier.align(Alignment.BottomCenter).alpha(fade))
    }
}

/** Close, then whose it is and when. */
@Composable
private fun ViewerBar(
    title: String,
    subtitle: String,
    modifier: Modifier,
    onDismiss: () -> Unit,
) {
    val t = Tt.colors
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
            if (title.isNotBlank()) {
                Text(
                    title,
                    style = chatStyle(16f, 21f),
                    fontWeight = FontWeight.SemiBold,
                    color = t.onPhoto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = chatStyle(12.5f, 17f), color = t.onPhoto.copy(alpha = 0.75f), maxLines = 1)
            }
        }
    }
}

/** The caption under the picture, and the quiet line after it, such as how long it is kept. */
@Composable
private fun ViewerFoot(
    caption: String,
    footnote: String,
    modifier: Modifier,
) {
    if (caption.isBlank() && footnote.isBlank()) return
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
        if (footnote.isNotBlank()) {
            if (caption.isNotBlank()) Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Lucide.Clock, null, Modifier.size(12.dp), tint = t.onPhoto.copy(alpha = 0.75f))
                Spacer(Modifier.width(4.dp))
                Text(footnote, style = chatStyle(12f, 16f), color = t.onPhoto.copy(alpha = 0.75f))
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
