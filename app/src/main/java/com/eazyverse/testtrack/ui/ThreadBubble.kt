package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CheckCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Maximize2
import com.composables.icons.lucide.User
import com.eazyverse.testtrack.domain.textUseLabel
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.proofRemoved

/*
 * A review's back and forth, drawn the way the crew chat draws messages: the reviewer on the left,
 * the developer on the right. One person's turn is a group: a picture and words each in a bubble
 * of their own, 4dp apart, with the corners toward the author tucked where they join, the face
 * beside the last of them and one time line under the lot.
 */

/** Which side a message sits on: the reviewer's on the left, the developer's on the right. */
enum class ThreadSide { LEFT, RIGHT }

/** A message's fill: theirs on the soft surface, the reader's own in the accent, the other side on the tile. */
enum class ThreadTone { THEIRS, MINE, SIDE }

/**
 * The face beside a left-hand group: a person, the app's own icon standing for its developer (who
 * stays anonymous on a reviewer's pages: never their name or photo), or none.
 */
enum class ThreadFace { PERSON, APP, NONE }

/** The app whose icon stands for its developer, for [ThreadFace.APP]. */
data class ThreadApp(
    val packageName: String,
    val name: String,
    val icon: String = "",
)

/** One bubble in a group. */
sealed interface ThreadPart {
    /** A screenshot, cropped to its top, where a review on Play sits. */
    data class Picture(
        val ref: String,
        val width: Dp = 150.dp,
        val height: Dp = 200.dp,
    ) : ThreadPart

    /**
     * Words, with an optional footer inside the bubble: [footStart] on the left (in green with a
     * check when [footOk], such as "145 characters"), [footEnd] muted on the right ("In their own words").
     */
    data class Words(
        val text: String,
        val footStart: String = "",
        val footEnd: String = "",
        val footOk: Boolean = false,
    ) : ThreadPart
}

/**
 * A review's text as a bubble: with the count in green once it is long enough for what was asked,
 * and whose words it is on the right. Null for no text.
 */
internal fun reviewWords(
    written: String,
    needs: Int,
    use: String?,
): ThreadPart.Words? {
    if (written.isBlank()) return null
    val length = plainLength(written)
    return ThreadPart.Words(
        written,
        footStart = if (needs > 0) "$length characters" else "",
        footEnd = textUseLabel(use).orEmpty(),
        footOk = needs > 0 && length >= needs,
    )
}

private val Face = 28.dp
private val FaceGap = 8.dp
private val BubbleGap = 4.dp

/** The widest a bubble grows: four fifths of the page between its gutters. */
@Composable
private fun threadBubbleMax(): Dp = ((LocalConfiguration.current.screenWidthDp.dp - Gutter * 2) * 0.8f)

@Composable
private fun threadInk(tone: ThreadTone): BubbleInk =
    when (tone) {
        ThreadTone.THEIRS -> bubbleInk(mine = false)
        ThreadTone.MINE -> bubbleInk(mine = true)
        ThreadTone.SIDE -> BubbleInk(Tt.colors.tile, Tt.colors.text)
    }

/**
 * One person's turn: [parts] as separate bubbles, the name over the first (left side), the face
 * bottom-aligned with the last, and one [time] line under the group at the bubbles' edge. [sent]
 * puts the green double check before the time, for the reader's own. A tap on a picture opens it
 * whole in [PictureViewer], titled with [who] and [time]. Inside [Thread] a new group starts 12dp below.
 */
@Composable
internal fun ThreadGroup(
    parts: List<ThreadPart>,
    modifier: Modifier = Modifier,
    side: ThreadSide = ThreadSide.LEFT,
    face: ThreadFace = if (side == ThreadSide.LEFT) ThreadFace.PERSON else ThreadFace.NONE,
    who: String = if (face == ThreadFace.APP) "Developer" else "",
    time: String = "",
    sent: Boolean = false,
    app: ThreadApp? = null,
    tone: ThreadTone = if (side == ThreadSide.RIGHT) ThreadTone.MINE else ThreadTone.THEIRS,
    picture: @Composable (String) -> Unit = { ProofImage(it, whole = true) },
) {
    if (parts.isEmpty()) return
    val t = Tt.colors
    val right = side == ThreadSide.RIGHT
    val faced = !right && face != ThreadFace.NONE
    val indent = if (faced) Face + FaceGap else 0.dp
    var viewing by remember { mutableStateOf<String?>(null) }
    Column(
        modifier.fillMaxWidth().layoutId(ThreadKind.GROUP),
        horizontalAlignment = if (right) Alignment.End else Alignment.Start,
    ) {
        if (who.isNotBlank()) {
            Text(
                who,
                Modifier.padding(start = if (right) 0.dp else indent + 12.dp, end = if (right) 12.dp else 0.dp, bottom = 3.dp),
                style = chatStyle(12f, 16f),
                fontWeight = FontWeight.SemiBold,
                color = t.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        parts.forEachIndexed { i, part ->
            if (i > 0) Spacer(Modifier.height(BubbleGap))
            val last = i == parts.lastIndex
            Row(verticalAlignment = Alignment.Bottom) {
                if (faced) {
                    if (last) ThreadFaceMark(face, app) else Spacer(Modifier.width(Face))
                    Spacer(Modifier.width(FaceGap))
                }
                val shape = bubbleShape(mine = right, first = i == 0, last = last)
                when (part) {
                    is ThreadPart.Picture -> PictureBubble(part, shape, picture) { viewing = part.ref }
                    is ThreadPart.Words -> WordsBubble(part, shape, tone)
                }
            }
        }
        if (time.isNotBlank()) {
            Row(
                Modifier.padding(start = if (right) 0.dp else indent + 2.dp, end = 2.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (sent) {
                    Icon(Lucide.CheckCheck, "Sent", Modifier.size(12.dp), tint = t.ok)
                    Spacer(Modifier.width(4.dp))
                }
                Text(time, style = chatStyle(11.5f, 15f), fontWeight = FontWeight.Normal, color = t.text3, maxLines = 1)
            }
        }
    }
    viewing?.let { ref ->
        PictureViewer(PictureSource.Proof(ref), title = who.ifBlank { null }, subtitle = time.ifBlank { null }) { viewing = null }
    }
}

/** The 28dp face: a person's round, or the app's icon standing for its developer. */
@Composable
private fun ThreadFaceMark(
    face: ThreadFace,
    app: ThreadApp?,
) {
    val t = Tt.colors
    if (face == ThreadFace.APP && app != null) {
        Box(Modifier.size(Face).semantics { contentDescription = "Developer" }) {
            AppMark(app.packageName, app.name, app.icon, size = Face)
        }
        return
    }
    Box(
        Modifier.size(Face).clip(CircleShape).background(t.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.User, null, Modifier.size(15.dp), tint = t.tileInk)
    }
}

/**
 * Inside a picture bubble: the picture draws edge to edge, leaving the corners to the bubble, and
 * calls [onGone] when the server says its file is no longer there.
 */
internal class ProofFrame(
    val onGone: () -> Unit,
)

/** The picture bubble a [ProofImage] sits in, if any. */
internal val LocalProofFrame = compositionLocalOf<ProofFrame?> { null }

private val RemovedHeight = 96.dp

/**
 * A screenshot that is its bubble: cut to the bubble's corners with only a hairline round it, and
 * the expand mark on its corner. A picture the server has deleted is a short box saying so instead.
 */
@Composable
private fun PictureBubble(
    part: ThreadPart.Picture,
    shape: RoundedCornerShape,
    picture: @Composable (String) -> Unit,
    onOpen: () -> Unit,
) {
    val t = Tt.colors
    var gone by remember(part.ref) { mutableStateOf(proofRemoved(part.ref)) }
    if (gone) {
        Box(
            Modifier
                .size(part.width, RemovedHeight)
                .clip(shape)
                .border(1.dp, t.cardLine, shape)
                .semantics(mergeDescendants = true) {},
        ) { PictureMissing("Picture removed") }
        return
    }
    val frame = remember(part.ref) { ProofFrame(onGone = { gone = true }) }
    Box(
        Modifier
            .size(part.width, part.height)
            .clip(shape)
            .background(t.surface3)
            .clickable(onClickLabel = "Open the screenshot", onClick = onOpen)
            .border(1.dp, t.cardLine, shape),
    ) {
        CompositionLocalProvider(LocalProofFrame provides frame) {
            Box(Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)) { picture(part.ref) }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(26.dp)
                .clip(CircleShape)
                .background(t.photoScrim),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Maximize2, null, Modifier.size(14.dp), tint = t.onPhoto)
        }
    }
}

/** Words in a bubble of their own, with the footer inside it. */
@Composable
private fun WordsBubble(
    part: ThreadPart.Words,
    shape: RoundedCornerShape,
    tone: ThreadTone,
) {
    val t = Tt.colors
    val ink = threadInk(tone)
    val quiet = if (tone == ThreadTone.MINE) ink.ink.copy(alpha = 0.75f) else t.text3
    Column(
        Modifier
            .widthIn(max = threadBubbleMax())
            .width(IntrinsicSize.Max)
            .clip(shape)
            .background(ink.fill)
            .padding(horizontal = BubblePadX, vertical = BubblePadY),
    ) {
        Text(part.text, style = chatStyle(15f, 20f), color = ink.ink)
        if (part.footStart.isNotBlank() || part.footEnd.isNotBlank()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (part.footOk) {
                        Icon(Lucide.Check, null, Modifier.size(12.dp), tint = t.ok)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        part.footStart,
                        style = chatStyle(12f, 16f),
                        fontWeight = if (part.footOk) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (part.footOk) t.ok else quiet,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(part.footEnd, style = chatStyle(12f, 16f), color = quiet, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
