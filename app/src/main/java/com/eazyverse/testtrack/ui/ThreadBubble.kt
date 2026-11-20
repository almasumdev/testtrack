package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.User
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * A review's back and forth, drawn the way the crew chat draws messages: the reviewer on the left,
 * the developer on the right, a quiet line between them for what happened. The same corners, fills
 * and type as a chat bubble, without a chat line behind it.
 */

/** Which side a message sits on: the reviewer's on the left, the developer's on the right. */
enum class ThreadSide { LEFT, RIGHT }

/** A message's fill: theirs on the soft surface, the reader's own in the accent, the other side on the tile. */
enum class ThreadTone { THEIRS, MINE, SIDE }

/** The face before a left-hand message: a person, the developer's app grid, or none. */
enum class ThreadFace { PERSON, DEVELOPER, NONE }

@Composable
private fun threadInk(tone: ThreadTone): BubbleInk =
    when (tone) {
        ThreadTone.THEIRS -> bubbleInk(mine = false)
        ThreadTone.MINE -> bubbleInk(mine = true)
        ThreadTone.SIDE -> BubbleInk(Tt.colors.tile, Tt.colors.text)
    }

/** A screenshot in a bubble: a phone's shape, cropped to its top, where a review on Play sits. */
private val PictureWidth = 156.dp
private val PictureHeight = 232.dp
private val PictureShape = RoundedCornerShape(14.dp)

/**
 * One message: who wrote it over it, the words and the screenshot in one bubble, and a quiet line
 * under it such as how long the text is. A tap on the picture calls [onPicture] with its ref.
 * [picture] draws a ref; it is given the bubble's width and cut to the bubble's height.
 */
@Composable
internal fun ThreadBubble(
    side: ThreadSide,
    who: String = "",
    text: String = "",
    pictureRef: String = "",
    meta: String = "",
    tone: ThreadTone = ThreadTone.THEIRS,
    modifier: Modifier = Modifier,
    face: ThreadFace = if (side == ThreadSide.LEFT) ThreadFace.PERSON else ThreadFace.NONE,
    picture: @Composable (String) -> Unit = { ProofImage(it, whole = true) },
    onPicture: ((String) -> Unit)? = null,
) {
    val t = Tt.colors
    val right = side == ThreadSide.RIGHT
    val ink = threadInk(tone)
    val shape = bubbleShape(mine = right, first = true, last = false)
    val hasPicture = pictureRef.isNotBlank()
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = if (right) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!right && face != ThreadFace.NONE) {
            val developer = face == ThreadFace.DEVELOPER
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (developer) t.glass2 else t.tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (developer) Lucide.LayoutGrid else Lucide.User,
                    null,
                    Modifier.size(15.dp),
                    tint = if (developer) t.text2 else t.tileInk,
                )
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(
            Modifier.widthIn(max = bubbleMax(0.82f)),
            horizontalAlignment = if (right) Alignment.End else Alignment.Start,
        ) {
            if (who.isNotBlank()) {
                Text(
                    who,
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 3.dp),
                    style = chatStyle(12f, 16f),
                    fontWeight = FontWeight.SemiBold,
                    color = t.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                Modifier
                    .clip(shape)
                    .background(ink.fill)
                    .then(
                        when {
                            !hasPicture -> Modifier.padding(horizontal = BubblePadX, vertical = BubblePadY)
                            text.isBlank() -> Modifier.padding(4.dp)
                            else -> Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 7.dp)
                        },
                    ),
            ) {
                if (hasPicture) {
                    Box(
                        Modifier
                            .size(PictureWidth, PictureHeight)
                            .clip(PictureShape)
                            .background(t.surface3)
                            .then(
                                if (onPicture != null) {
                                    Modifier.clickable(onClickLabel = "Open the screenshot") { onPicture(pictureRef) }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) { picture(pictureRef) }
                    }
                }
                if (text.isNotBlank()) {
                    Text(
                        text,
                        if (hasPicture) Modifier.width(PictureWidth).padding(start = 8.dp, end = 8.dp, top = 6.dp) else Modifier,
                        style = chatStyle(14.5f, 20f),
                        color = ink.ink,
                    )
                }
            }
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    Modifier.padding(start = 10.dp, end = 10.dp, top = 3.dp),
                    style = chatStyle(11.5f, 15f),
                    color = t.text4,
                )
            }
        }
    }
}

/** What happened between two messages, in a quiet pill across the middle; amber for a refusal. */
@Composable
internal fun ThreadNote(
    text: String,
    warn: Boolean = false,
) {
    val t = Tt.colors
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(if (warn) t.warnBg else t.glass2)
                .padding(horizontal = 12.dp, vertical = 3.dp),
            style = chatStyle(12.5f, 17f),
            fontWeight = if (warn) FontWeight.SemiBold else FontWeight.Normal,
            color = if (warn) t.warn else t.text2,
        )
    }
}

/** A thread's messages, 10dp apart. */
@Composable
internal fun Thread(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

/** A heading over a part of the page, with an optional quiet tag at its far end such as "Optional". */
@Composable
internal fun ThreadHeading(
    title: String,
    modifier: Modifier = Modifier,
    tag: String = "",
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 19.sp),
            fontWeight = FontWeight.Bold,
            color = Tt.colors.text,
        )
        if (tag.isNotBlank()) HeadingTag(tag)
    }
}

/** A screenshot from a thread, whole, over everything; it scrolls when it is taller than the screen. */
@Composable
internal fun ProofViewer(
    ref: String,
    onDismiss: () -> Unit,
    picture: @Composable (String) -> Unit = { ProofImage(it, whole = true) },
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Tt.colors.inverse)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                IconButton(onClick = onDismiss) { Icon(Lucide.X, "Close", tint = Tt.colors.onInverse) }
            }
            Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) { picture(ref) }
        }
    }
}
