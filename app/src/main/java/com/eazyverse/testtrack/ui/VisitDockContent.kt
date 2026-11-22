package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.composables.icons.lucide.KeyRound
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minimize2
import com.eazyverse.testtrack.ui.theme.Tt
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * The testing control, as drawn over the app being tested.
 *
 * Plain values in, taps out: the window that hosts it (VisitDockWindow) owns the dragging and the
 * sticking to an edge, and the round owns the clock. Flat on the top or bottom of the screen,
 * upright on a side, and it opens the same way it lies.
 */

/**
 * Transparent room the window keeps around the card so its shadow is not cut off by the window's
 * edge. Kept small on purpose: the room is part of the window, and a touch in it is caught by the
 * control instead of reaching the app underneath. It is enough for [DockCardShadow].
 */
val DockShadowRoom = 12.dp

/** The card's shadow depth: a soft lift whose reach (about this far down, less to the sides) fits in [DockShadowRoom]. */
val DockCardShadow = 6.dp

/**
 * The gap between the card and the window's edge before the shadow room was added. The window
 * placement still counts it as part of the gap to the screen edge, so the card has not moved.
 */
val DockCardGap = 8.dp

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
    /** Its picture, taken somewhere in the second ten seconds: taken, could not be, or not yet. */
    val picture: Boolean? = null,
    /** How long each app is open for, which the ring fills over. */
    val totalSeconds: Int = 20,
    /** The app's sign-in for testers: labelled parts, or the whole note when it has no labels. */
    val signIn: List<Pair<String, String>>,
    val upright: Boolean,
    val open: Boolean,
    /**
     * Where along the card's own width and height its docked edge holds it, from 0 (left, top) to 1
     * (right, bottom): the point that stays put while the card grows and shrinks.
     */
    val anchorX: Float = 0f,
    val anchorY: Float = 0f,
)

/** The card's sizes in pixels, closed and open, for the window to size itself by before a morph. */
data class DockSizes(
    val closedW: Int,
    val closedH: Int,
    val openW: Int,
    val openH: Int,
)

/** One calm beat for opening and closing: the card changes size, then the inside fades. */
internal const val DOCK_MORPH_MS = 240

/** The share of the beat the card spends changing size; the rest is the open content's fade. */
private const val SIZE_END = 0.7f

/** The share of the beat, at its closed end, the closed content is on screen for. */
private const val CLOSED_FADE = 0.3f

private val ClosedShape = RoundedCornerShape(24.dp)
private val OpenShape = RoundedCornerShape(22.dp)

private fun sizeProgress(t: Float): Float = FastOutSlowInEasing.transform((t / SIZE_END).coerceIn(0f, 1f))

/**
 * Opening and closing is one animated value, [morph], from 0 (closed) to 1 (open). The card grows
 * from the edge it is docked to over the first part, then the open content fades in; closing is the
 * same run backwards, the content fading out first.
 *
 * Both the closed and the open content are always measured. The stage reports the larger of the two
 * while the value is between, so the window (which wraps this) stays one size for the whole beat,
 * and the card is placed inside it on the docked edge at the point [DockUi.anchorX] and
 * [DockUi.anchorY] give. That is the same place the window's own placement would give a card of the
 * size it has at that moment, so the card never leaves its edge.
 */
@Composable
fun VisitDockContent(
    ui: DockUi,
    onToggleOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onCopy: (String) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onSizes: (DockSizes) -> Unit = {},
    onSettled: () -> Unit = {},
) {
    val drag =
        Modifier.pointerInput(Unit) {
            detectDragGestures(onDragEnd = onDragEnd, onDragCancel = onDragEnd) { change, amount ->
                change.consume()
                onDrag(amount)
            }
        }
    val morph =
        animateFloatAsState(
            if (ui.open) 1f else 0f,
            tween(DOCK_MORPH_MS, easing = LinearEasing),
            label = "dock morph",
            finishedListener = { onSettled() },
        )
    Box(Modifier.padding(DockShadowRoom).then(drag)) {
        Layout(
            content = {
                DockChrome(morph)
                Box(Modifier.clip(ClosedShape)) { ClosedBody(ui, onToggleOpen, onPlayPause) }
                Box(Modifier.clip(OpenShape)) {
                    if (ui.upright) {
                        OpenUpright(ui, onToggleOpen, onPlayPause, onCopy)
                    } else {
                        OpenFlat(ui, onToggleOpen, onPlayPause, onCopy)
                    }
                    MinimiseButton(onToggleOpen, Modifier.align(Alignment.TopEnd))
                }
            },
        ) { measurables, constraints ->
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val closed = measurables[1].measure(loose)
            val open = measurables[2].measure(loose)
            onSizes(DockSizes(closed.width, closed.height, open.width, open.height))
            val t = morph.value
            val s = sizeProgress(t)
            val cardW = lerp(closed.width, open.width, s)
            val cardH = lerp(closed.height, open.height, s)
            // At rest the stage is exactly the card; in between it is the larger of the two sizes.
            val restW =
                if (t <= 0f) {
                    closed.width
                } else if (t >= 1f) {
                    open.width
                } else {
                    max(closed.width, open.width)
                }
            val restH =
                if (t <= 0f) {
                    closed.height
                } else if (t >= 1f) {
                    open.height
                } else {
                    max(closed.height, open.height)
                }
            val w = max(restW, constraints.minWidth).coerceAtMost(constraints.maxWidth)
            val h = max(restH, constraints.minHeight).coerceAtMost(constraints.maxHeight)
            val card = measurables[0].measure(Constraints.fixed(cardW, cardH))
            val closedAlpha = 1f - (t / CLOSED_FADE).coerceIn(0f, 1f)
            val openAlpha = ((t - SIZE_END) / (1f - SIZE_END)).coerceIn(0f, 1f)
            layout(w, h) {
                val cx = (ui.anchorX * (w - cardW)).roundToInt()
                val cy = (ui.anchorY * (h - cardH)).roundToInt()
                card.place(cx, cy)
                // Content that is not showing is not placed, so it takes no touch either.
                if (closedAlpha > 0f) {
                    val x = cx + (ui.anchorX * (cardW - closed.width)).roundToInt()
                    val y = cy + (ui.anchorY * (cardH - closed.height)).roundToInt()
                    if (closedAlpha >= 1f) closed.place(x, y) else closed.placeWithLayer(x, y) { alpha = closedAlpha }
                }
                if (openAlpha > 0f) {
                    val x = cx + (ui.anchorX * (cardW - open.width)).roundToInt()
                    val y = cy + (ui.anchorY * (cardH - open.height)).roundToInt()
                    if (openAlpha >= 1f) open.place(x, y) else open.placeWithLayer(x, y) { alpha = openAlpha }
                }
            }
        }
    }
}

/** The card itself: shadow, glass and border, sized by the stage; its corners ease from pill to card. */
@Composable
private fun DockChrome(morph: State<Float>) {
    val t = Tt.colors
    val shape = RoundedCornerShape(lerp(24.dp, 22.dp, sizeProgress(morph.value)))
    Box(
        Modifier
            .shadow(DockCardShadow, shape)
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape),
    )
}

/** The small control: the ring, the app and the play button, flat on the top or bottom, upright on a side. */
@Composable
private fun ClosedBody(
    ui: DockUi,
    onToggleOpen: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val t = Tt.colors
    if (ui.upright) {
        // No side or bottom padding: the play button's own 48 touch area is that margin.
        Column(
            Modifier.clickable(onClick = onToggleOpen).padding(top = 8.dp, bottom = 1.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Ring(ui)
            Text("${ui.index + 1} of ${ui.total}", style = TextStyles.DeveloperStyle, color = t.text4)
            if (ui.signIn.isNotEmpty()) Icon(Lucide.KeyRound, "Has a sign-in", Modifier.size(15.dp), tint = t.accentInk)
            PlayPause(ui, onPlayPause)
        }
    } else {
        // No end padding, for the same reason.
        Row(
            Modifier.clickable(onClick = onToggleOpen).padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Ring(ui)
            Column {
                Text(
                    ui.app,
                    style = MaterialTheme.typography.titleSmall,
                    color = t.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${ui.index + 1} of ${ui.total}", style = TextStyles.DeveloperStyle, color = t.text4)
            }
            if (ui.signIn.isNotEmpty()) Icon(Lucide.KeyRound, "Has a sign-in", Modifier.size(15.dp), tint = t.accentInk)
            PlayPause(ui, onPlayPause)
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
            // End room so a long name stops before the minimise icon in the corner.
            Column(Modifier.weight(1f).padding(end = 24.dp)) {
                Text(
                    ui.app,
                    style = MaterialTheme.typography.titleSmall,
                    color = Tt.colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(status(ui), style = TextStyles.DeveloperStyle, color = Tt.colors.text4)
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
                style = MaterialTheme.typography.titleSmall,
                color = Tt.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(status(ui), style = TextStyles.DeveloperStyle, color = Tt.colors.text4)
        }
        RoundDots(ui)
        ui.signIn.forEach { (label, value) -> SignInLine(label, value, upright = true, onCopy = onCopy) }
        BigPlayPause(ui, onPlayPause)
    }
}

/** Quiet corner icon that closes the open card: 16 to look at, 48 to touch, no fill. */
@Composable
private fun MinimiseButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.Minimize2, "Make smaller", Modifier.size(16.dp), tint = Tt.colors.text4)
    }
}

private fun status(ui: DockUi) =
    when {
        ui.counted && ui.picture == true -> "Counted with its picture, app ${ui.index + 1} of ${ui.total}"
        ui.counted && ui.picture == false -> "No picture yet, app ${ui.index + 1} of ${ui.total}"
        ui.counted -> "Taking its picture, app ${ui.index + 1} of ${ui.total}"
        ui.paused -> "Held, ${ui.secondsLeft}s to go, app ${ui.index + 1} of ${ui.total}"
        else -> "${ui.secondsLeft}s to go, app ${ui.index + 1} of ${ui.total}"
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
