package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp

// The chat's loading shapes: a whole thread while the chat loads, two rows while older ones do.

/** One shape in the loading thread. */
private sealed interface Bone {
    /** A time line over a new sender group. */
    data object Time : Bone

    /** A bubble: someone else's on the left, yours on the right, [share] of the row wide. */
    data class Bubble(
        val mine: Boolean,
        val share: Float,
        val first: Boolean = true,
        val last: Boolean = true,
        val lines: Int = 1,
    ) : Bone

    /** A picture someone sent. */
    data object Picture : Bone
}

/**
 * Top to bottom, as a real thread reads: runs by others and by you, a few time lines and a
 * picture. Far taller than any screen on purpose; the page shows the newest end and clips the rest.
 */
private val Thread: List<Bone> =
    listOf(
        Bone.Bubble(mine = false, share = 0.56f, last = false),
        Bone.Bubble(mine = false, share = 0.38f, first = false),
        Bone.Bubble(mine = true, share = 0.48f),
        Bone.Time,
        Bone.Bubble(mine = false, share = 0.62f, last = false, lines = 2),
        Bone.Bubble(mine = false, share = 0.42f, first = false),
        Bone.Bubble(mine = true, share = 0.54f, last = false),
        Bone.Bubble(mine = true, share = 0.32f, first = false),
        Bone.Bubble(mine = false, share = 0.46f),
        Bone.Time,
        Bone.Bubble(mine = true, share = 0.66f, lines = 2),
        Bone.Bubble(mine = false, share = 0.5f, last = false),
        Bone.Bubble(mine = false, share = 0.7f, first = false, last = false, lines = 2),
        Bone.Bubble(mine = false, share = 0.36f, first = false),
        Bone.Bubble(mine = true, share = 0.42f),
        Bone.Time,
        Bone.Bubble(mine = false, share = 0.58f, last = false),
        Bone.Bubble(mine = false, share = 0.4f, first = false),
        Bone.Picture,
        Bone.Bubble(mine = true, share = 0.5f, last = false),
        Bone.Bubble(mine = true, share = 0.3f, first = false),
        Bone.Bubble(mine = false, share = 0.64f, lines = 2),
    )

/**
 * The chat while it loads: a whole thread of shapes filling the space, newest at the bottom like
 * the thread itself, cut off at the top whatever the height.
 */
@Composable
internal fun ChatSkeleton(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().clipToBounds()) {
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .wrapContentHeight(Alignment.Bottom, unbounded = true)
                .padding(start = ChatGutter, end = ChatGutter, bottom = 10.dp),
        ) {
            Thread.forEach { bone ->
                when (bone) {
                    Bone.Time -> TimeBone()
                    Bone.Picture -> PictureBone()
                    is Bone.Bubble -> BubbleBone(bone)
                }
            }
        }
    }
}

/** Two rows in the thread's own shapes, as older messages load in at the top. */
@Composable
internal fun ChatOlderSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        BubbleBone(Bone.Bubble(mine = false, share = 0.56f))
        BubbleBone(Bone.Bubble(mine = true, share = 0.44f))
    }
}

@Composable
private fun TimeBone() {
    Box(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
        Skeleton(width = 64.dp, height = 10.dp, corner = 5.dp)
    }
}

@Composable
private fun BubbleBone(bone: Bone.Bubble) {
    Row(
        Modifier.fillMaxWidth().padding(top = groupGap(bone.first)),
        horizontalArrangement = if (bone.mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!bone.mine) Face(bone.last)
        Column(Modifier.fillMaxWidth(bone.share)) {
            // The name over the first of someone else's run.
            if (!bone.mine && bone.first) {
                Skeleton(width = 72.dp, height = 9.dp, corner = 4.5.dp, modifier = Modifier.padding(start = 12.dp, bottom = 5.dp))
            }
            Skeleton(
                height = if (bone.lines > 1) 52.dp else BubbleMinHeight,
                corner = 0.dp,
                modifier = Modifier.clip(bubbleShape(bone.mine, bone.first, bone.last)),
            )
        }
    }
}

@Composable
private fun PictureBone() {
    Row(Modifier.fillMaxWidth().padding(top = groupGap(first = true)), verticalAlignment = Alignment.Bottom) {
        Face(last = true)
        Skeleton(
            width = 196.dp,
            height = 148.dp,
            corner = 0.dp,
            modifier = Modifier.clip(bubbleShape(mine = false, first = true, last = true)),
        )
    }
}

/** The face beside the last of someone's run, or the room it takes beside the others. */
@Composable
private fun Face(last: Boolean) {
    if (last) Skeleton(width = 28.dp, height = 28.dp, corner = 14.dp) else Spacer(Modifier.width(28.dp))
    Spacer(Modifier.width(8.dp))
}
