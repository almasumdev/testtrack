package com.eazyverse.testtrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.delay

/*
 * The testing control, as drawn over the app being tested.
 *
 * Plain values in, taps out: the window that hosts it (VisitDockWindow) owns the dragging and the
 * sticking to an edge, and the round owns the clock. Flat on the top or bottom of the screen,
 * upright on a side, and it opens the same way it lies.
 */

/** Everything the control shows. */
data class DockUi(
    val app: String,
    /** Its place in tonight's round, from zero, and how many there are. */
    val index: Int,
    val total: Int,
    val secondsLeft: Int,
    val paused: Boolean,
    /** Ten seconds are in: the ring is full and the button goes on to the next app. */
    val counted: Boolean,
    /** The app's sign-in for testers: labelled parts, or the whole note when it has no labels. */
    val signIn: List<Pair<String, String>>,
    val upright: Boolean,
    val open: Boolean,
)

@Composable
fun VisitDockContent(
    ui: DockUi,
    onToggleOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onCopy: (String) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(if (ui.open) 22.dp else 24.dp)
    val drag =
        Modifier.pointerInput(Unit) {
            detectDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, amount ->
                change.consume()
                onDrag(amount)
            }
        }
    Box(
        Modifier
            .padding(8.dp)
            .shadow(10.dp, shape)
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape)
            .then(drag),
    ) {
        when {
            ui.open && ui.upright -> OpenUpright(ui, onToggleOpen, onPlayPause, onCopy)
            ui.open -> OpenFlat(ui, onToggleOpen, onPlayPause, onCopy)
            ui.upright ->
                Column(
                    Modifier.clickable(onClick = onToggleOpen).padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Ring(ui)
                    Text("${ui.index + 1} of ${ui.total}", fontSize = 10.5.sp, color = t.text4)
                    if (ui.signIn.isNotEmpty()) Icon(Lucide.KeyRound, "Has a sign-in", Modifier.size(15.dp), tint = t.accent)
                    PlayPause(ui, onPlayPause)
                }
            else ->
                Row(
                    Modifier.clickable(onClick = onToggleOpen).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Ring(ui)
                    Column {
                        Text(
                            ui.app,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = t.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${ui.index + 1} of ${ui.total}", fontSize = 10.5.sp, color = t.text4)
                    }
                    if (ui.signIn.isNotEmpty()) Icon(Lucide.KeyRound, "Has a sign-in", Modifier.size(15.dp), tint = t.accent)
                    PlayPause(ui, onPlayPause)
                }
        }
    }
}

@Composable
private fun OpenFlat(
    ui: DockUi,
    onToggleOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onCopy: (String) -> Unit,
) {
    Column(Modifier.width(276.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggleOpen), verticalAlignment = Alignment.CenterVertically) {
            Ring(ui)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    ui.app,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Tt.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(status(ui), fontSize = 11.sp, color = Tt.colors.text4)
            }
        }
        RoundDots(ui)
        ui.signIn.forEach { (label, value) -> SignInLine(label, value, upright = false, onCopy = onCopy) }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BigPlayPause(ui, onPlayPause) }
    }
}

@Composable
private fun OpenUpright(
    ui: DockUi,
    onToggleOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onCopy: (String) -> Unit,
) {
    Column(
        Modifier.width(232.dp).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.clickable(onClick = onToggleOpen), horizontalAlignment = Alignment.CenterHorizontally) {
            Ring(ui)
            Spacer(Modifier.height(6.dp))
            Text(
                ui.app,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Tt.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(status(ui), fontSize = 10.5.sp, color = Tt.colors.text4)
        }
        RoundDots(ui)
        ui.signIn.forEach { (label, value) -> SignInLine(label, value, upright = true, onCopy = onCopy) }
        BigPlayPause(ui, onPlayPause)
    }
}

private fun status(ui: DockUi) =
    when {
        ui.counted -> "Counted · ${ui.index + 1} of ${ui.total}"
        ui.paused -> "Paused · ${ui.index + 1} of ${ui.total}"
        else -> "${ui.secondsLeft}s to go · ${ui.index + 1} of ${ui.total}"
    }

/** The ten seconds as a ring: filling while the app is open, full and green once it counts. */
@Composable
private fun Ring(
    ui: DockUi,
    size: Dp = 34.dp,
) {
    val accent = Tt.colors.accent
    val done = Status.posted
    val track = Tt.colors.glass2
    val fraction = if (ui.counted) 1f else (10 - ui.secondsLeft).coerceIn(0, 10) / 10f
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, useCenter = false, style = stroke)
            drawArc(if (ui.counted) done else accent, -90f, 360f * fraction, useCenter = false, style = stroke)
        }
        if (ui.counted) {
            Icon(Lucide.Check, "Counted", Modifier.size(15.dp), tint = done)
        } else {
            Text("${ui.secondsLeft}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Tt.colors.text)
        }
    }
}

/** Where tonight's round is: done green, this one blue, the rest empty. */
@Composable
private fun RoundDots(ui: DockUi) {
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
private fun SignInLine(
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
                Text(label, fontSize = 10.sp, color = Tt.colors.text4)
                Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Lucide.Copy, null, Modifier.size(13.dp), tint = if (copied) Status.posted else Tt.colors.accent)
        }
    } else {
        Row(tap, verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(60.dp), fontSize = 10.5.sp, color = Tt.colors.text4)
            Text(
                value,
                Modifier.weight(1f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Lucide.Copy, null, Modifier.size(13.dp), tint = if (copied) Status.posted else Tt.colors.accent)
        }
    }
}

/** The one button: pause while the clock runs, play to carry on, and play for the next app once it counts. */
@Composable
private fun PlayPause(
    ui: DockUi,
    onClick: () -> Unit,
) {
    val play = ui.counted || ui.paused
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (ui.counted) Status.posted else Tt.colors.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (play) Lucide.Play else Lucide.Pause,
            when {
                ui.counted -> "Next app"
                ui.paused -> "Play"
                else -> "Pause"
            },
            Modifier.size(15.dp),
            tint = Tt.colors.onAccent,
        )
    }
}

@Composable
private fun BigPlayPause(
    ui: DockUi,
    onClick: () -> Unit,
) {
    val label =
        when {
            ui.counted -> if (ui.index + 1 < ui.total) "Next app" else "Finish"
            ui.paused -> "Play"
            else -> "Pause"
        }
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (ui.counted) Status.posted else Tt.colors.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (ui.counted || ui.paused) Lucide.Play else Lucide.Pause, null, Modifier.size(14.dp), tint = Tt.colors.onAccent)
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Tt.colors.onAccent)
    }
}

/** The labelled parts of an app's sign-in note, or the whole note when it has none. */
fun signInParts(notes: String): List<Pair<String, String>> {
    if (notes.isBlank()) return emptyList()
    val read = readSignIn(notes) ?: return listOf("Sign-in" to notes.trim())
    return listOfNotNull(
        read.username.takeIf { it.isNotBlank() }?.let { "Username" to it },
        read.password.takeIf { it.isNotBlank() }?.let { "Password" to it },
        read.also.takeIf { it.isNotBlank() }?.let { "Also" to it },
    )
}
