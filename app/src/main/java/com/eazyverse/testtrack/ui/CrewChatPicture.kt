package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.composables.icons.lucide.ImageOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatPicture
import com.eazyverse.testtrack.domain.SendState
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.proofSource

/*
 * A picture message, drawn the way chat apps do: the picture fills the bubble edge to edge in its
 * own shape, with no time over it. A caption sits under it in the same bubble. While it goes it is
 * dimmed under a spinning ring; if it did not go, a retry mark sits on it. Pictures are kept a day,
 * then a plain box says so.
 */

/** The widest a picture grows, as a share of the screen. */
private const val PICTURE_SHARE = 0.72f

/** A picture's shape before it has loaded, when the server did not say: a phone screenshot's. */
private const val GUESS_RATIO = 0.75f

/** Height kept between these shares of the width, so a long screenshot is cropped, not a tower. */
private const val TALLEST = 1.4f
private const val FLATTEST = 0.55f

@Composable
internal fun ChatPictureBubble(
    line: ChatLine,
    picture: ChatPicture,
    first: Boolean,
    last: Boolean,
    view: ChatView,
    quoteTap: QuoteTap? = null,
) {
    val t = Tt.colors
    val shape = bubbleShape(line.mine, first, last)
    val ink = bubbleInk(line.mine)
    val you = !line.mine && view.mentionsMe(line)
    val caption = line.text.isNotBlank()
    val width = bubbleMax(PICTURE_SHARE).coerceAtMost(320.dp)
    // What the picture turned out to be, once it has loaded.
    var loaded by remember(picture.id, picture.local) { mutableFloatStateOf(0f) }
    val ratio =
        when {
            loaded > 0f -> loaded
            picture.width > 0 && picture.height > 0 -> picture.width.toFloat() / picture.height
            else -> GUESS_RATIO
        }
    val height = if (picture.expired) 140.dp else (width / ratio).coerceIn(width * FLATTEST, width * TALLEST)
    Column(
        Modifier
            .width(width)
            .clip(shape)
            .background(ink.fill)
            .mentionWash(you)
            .bubbleEdge(line, shape),
    ) {
        line.quote?.let { quote ->
            Quote(
                view.quoteOf(quote),
                Modifier.padding(4.dp).fillMaxWidth(),
                fill = if (line.mine) ink.ink.copy(alpha = 0.16f) else t.bg,
                maxWidth = width - 8.dp,
                ink = if (line.mine) ink.ink else null,
                tap = quoteTap,
            )
        }
        Box(Modifier.width(width).height(height)) {
            if (picture.expired) {
                ExpiredPicture()
            } else {
                PictureImage(picture, line.mine) { loaded = it }
                when (line.send) {
                    SendState.SENDING ->
                        OverMark {
                            CircularProgressIndicator(
                                Modifier.size(26.dp),
                                color = t.onPhoto,
                                strokeWidth = 2.5.dp,
                                trackColor = Color.Transparent,
                            )
                        }
                    SendState.FAILED -> OverMark { Icon(Lucide.RotateCcw, "Try again", Modifier.size(22.dp), tint = t.onPhoto) }
                    SendState.SENT -> Unit
                }
            }
        }
        if (caption) {
            ChatMessageText(line, view, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), ink = ink.ink)
        }
    }
}

/** The picture itself: the phone's copy while it goes, else the server's, filling its box. */
@Composable
private fun PictureImage(
    picture: ChatPicture,
    mine: Boolean,
    onShape: (Float) -> Unit,
) {
    val context = LocalContext.current
    val local = picture.local.isNotBlank()
    val loader = rememberChatLoader()
    val model =
        remember(picture.id, picture.local) {
            ImageRequest
                .Builder(context)
                .data(if (local) picture.local else proofSource(picture.id))
                .crossfade(true)
                .build()
        }
    val shown: (Size) -> Unit = { size -> if (size.width > 0f && size.height > 0f) onShape(size.width / size.height) }
    val description = if (mine) "Your picture" else "A picture"
    if (local) {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = description,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = { PictureLoading() },
            onSuccess = { shown(it.painter.intrinsicSize) },
        )
    } else {
        SubcomposeAsyncImage(
            model = model,
            contentDescription = description,
            imageLoader = loader,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            loading = { PictureLoading() },
            error = { PictureMissing("Couldn't load") },
            onSuccess = { shown(it.painter.intrinsicSize) },
        )
    }
}

@Composable
private fun PictureLoading() {
    BoxWithConstraints(Modifier.fillMaxSize()) { Skeleton(maxWidth, maxHeight, 0.dp) }
}

/** Dims the picture and puts [mark] in a dark ring at its middle. */
@Composable
private fun OverMark(mark: @Composable () -> Unit) {
    val t = Tt.colors
    Box(Modifier.fillMaxSize().background(t.photoScrim.copy(alpha = 0.32f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(t.photoScrim), contentAlignment = Alignment.Center) { mark() }
    }
}

/** A picture past its day: a plain box in its place. */
@Composable
private fun ExpiredPicture() {
    PictureMissing("Picture expired")
}

@Composable
internal fun PictureMissing(label: String) {
    val t = Tt.colors
    Column(
        Modifier.fillMaxSize().background(t.surface3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Lucide.ImageOff, null, Modifier.size(24.dp), tint = t.text3)
        Spacer(Modifier.height(6.dp))
        Text(label, style = chatStyle(13f, 17f), fontWeight = FontWeight.SemiBold, color = t.text3)
    }
}
