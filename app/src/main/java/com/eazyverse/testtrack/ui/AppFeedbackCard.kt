package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.User
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The Feedback block on an app's page once a request exists: one face for every review bought, so
 * the owner sees who is in and what waits for them. Drawn from the counts the server sends; there
 * are no names here, so every face is the same neutral person.
 */

private val ReviewFaceSize = 34.dp

/**
 * A block's heading with a small tag at the far end saying when that block is for. The tag stays
 * once the block is running; the line that explains it only shows on the empty start tile.
 */
@Composable
internal fun BlockHeading(
    title: String,
    tag: String,
) {
    val t = Tt.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 19.sp),
            fontWeight = FontWeight.Bold,
            color = t.text,
        )
        Text(
            tag,
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(t.glass2)
                .padding(horizontal = 9.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            fontWeight = FontWeight.SemiBold,
            color = t.text3,
        )
    }
}

/**
 * The running feedback card: faces for the reviews, then one line and the action that fits it.
 * Tapping the card opens the request; [onCheck] and [onFix] open it too, on the part that needs
 * the owner, and [onAskAgain] starts a new request once this one is finished.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeedbackCard(
    feedback: AppFeedbackStatus,
    canAskAgain: Boolean,
    onOpen: () -> Unit,
    onCheck: () -> Unit,
    onFix: () -> Unit,
    onAskAgain: () -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(TileRadius)
    val kind = feedback.cardKind()
    val shown = feedback.faces()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.bg)
            .border(1.dp, t.cardLine, shape)
            .clickable(onClick = onOpen)
            .padding(16.dp),
    ) {
        Text(
            "Reviews",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.sp),
            fontWeight = FontWeight.SemiBold,
            color = t.text,
        )
        Spacer(Modifier.height(12.dp))
        val spoken = feedback.facesSpoken()
        FlowRow(
            Modifier
                .fillMaxWidth()
                .alpha(if (kind == FeedbackCardKind.OFF_CIRCLE) 0.45f else 1f)
                .clearAndSetSemantics { contentDescription = spoken },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            shown.faces.forEach { ReviewFaceMark(it) }
            if (shown.more > 0) MoreFaces(shown.more)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when (kind) {
                FeedbackCardKind.TO_CHECK -> {
                    Headline("${feedback.toCheck} waiting for you", t.text, Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    CheckPill(onCheck)
                }
                FeedbackCardKind.OFF_CIRCLE -> {
                    Headline("Testers can't install it", t.bad, Modifier.weight(1f), Lucide.EyeOff)
                    Box(Modifier.offset(x = 8.dp)) { InlineAction("Fix it", onClick = onFix) }
                }
                FeedbackCardKind.ALL_IN -> {
                    Headline("All ${feedback.bought} accepted", t.ok, Modifier.weight(1f))
                    if (canAskAgain) Box(Modifier.offset(x = 8.dp)) { InlineAction("Ask again", onClick = onAskAgain) }
                }
                FeedbackCardKind.CLOSED -> {
                    Headline("Closed with ${feedback.done} of ${feedback.bought} reviews", t.text3, Modifier.weight(1f))
                    if (canAskAgain) Box(Modifier.offset(x = 8.dp)) { InlineAction("Ask again", onClick = onAskAgain) }
                }
                FeedbackCardKind.WAITING -> Headline("Testers are on it", t.text3, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Headline(
    text: String,
    tone: Color,
    modifier: Modifier,
    icon: ImageVector? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(15.dp), tint = tone)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
            fontWeight = FontWeight.Medium,
            color = tone,
        )
    }
}

/** The filled button that takes the owner to the reviews waiting for them. */
@Composable
private fun CheckPill(onClick: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Button(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 32.dp),
            shape = RoundedCornerShape(percent = 50),
            elevation = null,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                "Check",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, lineHeight = 16.sp),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** One review: a neutral person with a small mark for where it stands, or a dashed ring for a free slot. */
@Composable
private fun ReviewFaceMark(face: ReviewFace) {
    val t = Tt.colors
    val bg = t.bg
    val warn = t.warn
    val dashed = t.glassBorderStrong
    Box(
        Modifier
            .size(ReviewFaceSize)
            .drawBehind {
                when (face) {
                    // A 2dp gap in the page colour, then a 2dp amber ring around the face.
                    ReviewFace.TO_CHECK -> {
                        drawCircle(warn, radius = size.minDimension / 2 + 4.dp.toPx())
                        drawCircle(bg, radius = size.minDimension / 2 + 2.dp.toPx())
                    }
                    ReviewFace.OPEN -> {
                        val w = 1.5.dp.toPx()
                        drawCircle(
                            dashed,
                            radius = (size.minDimension - w) / 2,
                            style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                        )
                    }
                    ReviewFace.ACCEPTED -> Unit
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (face == ReviewFace.OPEN) return@Box
        Box(Modifier.size(ReviewFaceSize).clip(CircleShape).background(t.tile), contentAlignment = Alignment.Center) {
            Icon(Lucide.User, null, Modifier.size(17.dp), tint = t.tileInk)
        }
        val accepted = face == ReviewFace.ACCEPTED
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(4.dp, 4.dp)
                .size(17.dp)
                .background(bg, CircleShape)
                .padding(2.dp)
                .background(if (accepted) t.ok else t.warn, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (accepted) Lucide.Check else Lucide.Eye,
                null,
                Modifier.size(if (accepted) 9.dp else 10.dp),
                tint = if (accepted) t.bg else t.onWarn,
            )
        }
    }
}

/** What is left over when a request has more reviews than there is room to draw. */
@Composable
private fun MoreFaces(count: Int) {
    val t = Tt.colors
    Box(Modifier.size(ReviewFaceSize).clip(CircleShape).background(t.glass2), contentAlignment = Alignment.Center) {
        Text(
            "+$count",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
            fontWeight = FontWeight.Bold,
            color = t.text2,
        )
    }
}
