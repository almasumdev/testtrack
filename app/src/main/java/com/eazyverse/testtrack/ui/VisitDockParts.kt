package com.eazyverse.testtrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular
import kotlinx.coroutines.delay

/*
 * The pieces of the testing control the card is made of: the ring, the round's dots, the sign-in
 * lines and the two play buttons. Beside [VisitDockContent], which lays them out and morphs the card.
 */

/** The app's twenty seconds as a ring: filling while it is open, full and green once it counts. */
@Composable
internal fun Ring(
    ui: DockUi,
    size: Dp = 34.dp,
) {
    val accent = Tt.colors.accent
    val done = Status.posted
    val track = Tt.colors.glass2
    val total = ui.totalSeconds.coerceAtLeast(1)
    val fraction = if (ui.counted) 1f else (total - ui.secondsLeft).coerceIn(0, total) / total.toFloat()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, useCenter = false, style = stroke)
            drawArc(if (ui.counted) done else accent, -90f, 360f * fraction, useCenter = false, style = stroke)
        }
        if (ui.counted) {
            Icon(Lucide.Check, "Counted", Modifier.size(15.dp), tint = done)
        } else {
            Text(
                "${ui.secondsLeft}",
                style = MaterialTheme.typography.labelMedium.tabular(),
                fontWeight = FontWeight.Bold,
                color = Tt.colors.text,
            )
        }
    }
}

/** Where tonight's round is: done green, this one blue, the rest empty. */
@Composable
internal fun RoundDots(ui: DockUi) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(ui.total.coerceAtMost(14)) { i ->
            val color =
                when {
                    i < ui.index -> Status.posted
                    i == ui.index -> Tt.colors.accent
                    else -> Tt.colors.glass2
                }
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

/** One part of the sign-in. A tap copies it, and the line turns green for a moment. */
@Composable
internal fun SignInLine(
    label: String,
    value: String,
    upright: Boolean,
    onCopy: (String) -> Unit,
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_200)
            copied = false
        }
    }
    val bg by animateColorAsState(if (copied) Status.postedSoft else Tt.colors.glass2, label = "copied")
    val ink = if (copied) Status.posted else Tt.colors.text
    val tap =
        Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .semantics { contentDescription = "Copy $label" }
            .clickable {
                onCopy(value)
                copied = true
            }.padding(horizontal = 9.dp, vertical = 7.dp)
    if (upright) {
        Row(tap, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = TextStyles.DialogNote, color = Tt.colors.text4)
                Text(
                    value,
                    style = TextStyles.PageLine,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Lucide.Copy, null, Modifier.size(13.dp), tint = if (copied) Status.posted else Tt.colors.accent)
        }
    } else {
        Row(tap, verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(60.dp), style = TextStyles.DialogNote, color = Tt.colors.text4)
            Text(
                value,
                Modifier.weight(1f),
                style = TextStyles.PageLine,
                fontWeight = FontWeight.SemiBold,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Lucide.Copy, null, Modifier.size(13.dp), tint = if (copied) Status.posted else Tt.colors.accent)
        }
    }
}

/** The one button: pause while the clock runs, play to carry on, play for the next app once it counts, back for a lone app. */
@Composable
internal fun PlayPause(
    ui: DockUi,
    onClick: () -> Unit,
) {
    val back = ui.single && ui.counted
    val play = ui.counted || ui.paused
    // 34 to look at, 48 to touch.
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(34.dp)
            .clip(CircleShape)
            .background(if (ui.counted) Status.posted else Tt.colors.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = Tt.colors.inkOn(if (ui.counted) Status.posted else Tt.colors.accent)
        if (back) {
            // A lone app has no next one: the same round button, with the page back arrow, and no words.
            Icon(painterResource(R.drawable.ic_arrow_back_ios_new), "Back to TestTrack", Modifier.size(15.dp), tint = tint)
        } else {
            Icon(
                if (play) Lucide.Play else Lucide.Pause,
                when {
                    ui.counted -> "Next app"
                    ui.paused -> "Play"
                    else -> "Pause"
                },
                Modifier.size(15.dp),
                tint = tint,
            )
        }
    }
}

@Composable
internal fun BigPlayPause(
    ui: DockUi,
    onClick: () -> Unit,
) {
    val label =
        when {
            ui.single && ui.counted -> "Back to TestTrack"
            ui.counted -> if (ui.index + 1 < ui.total) "Next app" else "Finish"
            ui.paused -> "Play"
            else -> "Pause"
        }
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(percent = 50))
            .background(if (ui.counted) Status.posted else Tt.colors.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = Tt.colors.inkOn(if (ui.counted) Status.posted else Tt.colors.accent)
        if (ui.single && ui.counted) {
            Icon(painterResource(R.drawable.ic_arrow_back_ios_new), null, Modifier.size(16.dp), tint = ink)
        } else {
            Icon(if (ui.counted || ui.paused) Lucide.Play else Lucide.Pause, null, Modifier.size(16.dp), tint = ink)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ink)
    }
}
