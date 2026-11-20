package com.eazyverse.testtrack.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.User
import com.eazyverse.testtrack.ui.theme.Tt

// The pieces the testers group sheet is built from, kept apart from the sheet (GroupSheet.kt),
// which decides which of them each state shows.

/**
 * The account the check is about, in the same place in every state so the eye has an anchor.
 * 64 high, one hairline, the initial in a ringed circle, and under the address where things
 * stand. While [running] a thin line moves along the foot of the row.
 */
@Composable
internal fun GroupAccountRow(
    email: String,
    status: String,
    tone: Color,
    icon: ImageVector?,
    running: Boolean,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(TileRadius)
    val name = email.ifBlank { "This Google account" }
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .border(1.dp, t.cardLine, shape),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).border(1.dp, t.ring, CircleShape)) {
                Avatar(
                    photo = "",
                    letter = initialOf(name),
                    size = 36.dp,
                    letterStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = t.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, Modifier.size(13.dp), tint = tone)
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                        color = tone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (running) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                color = t.primary,
                trackColor = t.track,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
            )
        }
    }
}

/** The three steps of joining. After the group was opened the first is ticked and the second is the one to do. */
@Composable
internal fun GroupSteps(opened: Boolean) {
    val t = Tt.colors
    Column(Modifier.fillMaxWidth()) {
        StepRow(1, "Open the group with this account", now = !opened, done = opened)
        StepRow(2, bold("Tap ", "Join group", "", t.text), now = opened)
        Text(
            "After you join, Google may say you can't see posts. That's normal, you're in.",
            Modifier.padding(start = 34.dp).offset(y = (-5).dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
            color = t.text3,
        )
        StepRow(3, "Come back here and we'll check again", now = false)
    }
}

/** What to check after a second no: one plain row, no card. */
@Composable
internal fun GroupHints() {
    HintRow(Lucide.User, "Showed a different account?", AnnotatedString("Switch to this one at the top, then join"))
}

@Composable
private fun HintRow(
    icon: ImageVector,
    ask: String,
    answer: AnnotatedString,
) {
    val t = Tt.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ask,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 17.sp),
                fontWeight = FontWeight.SemiBold,
                color = t.text,
            )
            Text(
                answer,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 15.sp),
                color = t.text3,
            )
        }
    }
}

/** [lead], then [word] in semibold and [ink], then [tail]. */
private fun bold(
    lead: String,
    word: String,
    tail: String,
    ink: Color,
): AnnotatedString =
    buildAnnotatedString {
        append(lead)
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = ink)) { append(word) }
        append(tail)
    }
