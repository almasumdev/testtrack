package com.eazyverse.testtrack.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The capture half of the review: the one-time guide, the ask for "Display over other apps", and
 * step four: the ways to a screenshot, the screenshot, and its optional note.
 */

/**
 * How the review step works, shown once: the first time somebody taps "Open Play to review".
 * Saying it before Play opens is the difference between a button they expect and one that
 * appears from nowhere over somebody else's app.
 */
@Composable
fun ReviewGuideDialog(
    live: Boolean,
    onOk: () -> Unit,
    onDismiss: () -> Unit,
) {
    Ask(
        title = "How to leave your review",
        body =
            "1. Play opens on the app's page.\n" +
                (if (live) "2. Rate it and write your review.\n" else "2. Tap “Send feedback” and write it.\n") +
                "3. Tap the blue camera button at the bottom right.\n" +
                "4. You come straight back here with the screenshot.\n\n" +
                "Android will ask to allow screen capture. It is only for that one screenshot.",
        confirm = "Got it",
        dismiss = null,
        onConfirm = onOk,
        onDismiss = onDismiss,
    )
}

/** The camera button floats over Play, which needs the one grant Android only gives in Settings. */
@Composable
fun OverlayPermissionDialog(
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    Ask(
        title = "Allow the camera button",
        body =
            "The button floats over Play, so TestTrack needs “Display over other apps”. Switch it " +
                "on for TestTrack, then come back and tap Open Play again.",
        confirm = "Open settings",
        onConfirm = onOpen,
        onDismiss = onDismiss,
    )
}

/**
 * Step four, both halves: the two ways to get a screenshot, or the one that was got.
 *
 * The two swap with a fade rather than a jump, since one replaces the other in the same place.
 * Either way the reviewer can still go the other way: capture again, or pick from the gallery.
 */
@Composable
fun ProofArea(
    shown: Uri?,
    captured: Boolean,
    note: String,
    onNote: (String) -> Unit,
    onCapture: () -> Unit,
    onGallery: () -> Unit,
) {
    AnimatedContent(
        targetState = shown,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) using SizeTransform(clip = false) },
        contentKey = { it == null },
        label = "proof",
    ) { picture ->
        if (picture == null) {
            ProofChoices(onCapture, onGallery)
        } else {
            ProofForm(picture, captured, note, onNote, onCapture, onGallery)
        }
    }
}

/**
 * The screenshot that was got, how it was got, and an optional note.
 *
 * Big enough to see it is the right picture, and tappable to see it whole. The note is not the
 * review (that is on Play); it is anything worth saying to the developer alongside it.
 */
@Composable
private fun ProofForm(
    proof: Uri,
    captured: Boolean,
    note: String,
    onNote: (String) -> Unit,
    onCapture: () -> Unit,
    onGallery: () -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(CardRadius)
    val thumb = RoundedCornerShape(10.dp)
    var viewing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(t.glass)
                .border(1.dp, t.glassBorder, shape)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = proof,
                contentDescription = "Your review screenshot",
                modifier =
                    Modifier
                        .size(width = 62.dp, height = 110.dp)
                        .clip(thumb)
                        .border(1.dp, t.glassBorder, thumb)
                        .clickable { viewing = true },
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Lucide.CircleCheck, null, Modifier.size(17.dp), tint = t.ok)
                    Spacer(Modifier.width(6.dp))
                    Text("Screenshot added", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Text(
                    if (captured) "Captured on Play · tap to view" else "From your gallery · tap to view",
                    style = MaterialTheme.typography.bodySmall,
                    color = t.text3,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProofAction(Lucide.Camera, "Capture", onCapture)
                    ProofAction(Lucide.Image, "Gallery", onGallery)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { onNote(it.take(NOTE_MAX)) },
            modifier = Modifier.fillMaxWidth().height(88.dp),
            placeholder = { Text("Anything else for the developer? (optional)", fontSize = 14.sp) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            shape = RoundedCornerShape(CardRadius),
            colors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = t.glass,
                    focusedContainerColor = t.glass,
                    unfocusedBorderColor = t.glassBorder,
                ),
        )
    }
    if (viewing) ProofViewer(proof) { viewing = false }
}

/** A small way to replace the screenshot, one per source. */
@Composable
private fun ProofAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(t.glass2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(15.dp), tint = t.accentInk)
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            fontWeight = FontWeight.SemiBold,
            color = t.accentInk,
        )
    }
}

/** The screenshot whole, over everything; any tap puts it away. */
@Composable
private fun ProofViewer(
    proof: Uri,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().clickable(onClick = onClose).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = proof,
                contentDescription = "Your review screenshot, full size",
                modifier = Modifier.clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

private const val NOTE_MAX = 600

/**
 * Step four with no screenshot yet: the two ways to get one, right on the page.
 *
 * Coming back from Play without pressing the camera button (or after saying no to screen capture)
 * lands here. The automatic way is offered first because it is one tap on Play; the gallery is for
 * a screenshot taken by hand.
 */
@Composable
private fun ProofChoices(
    onCapture: () -> Unit,
    onGallery: () -> Unit,
) {
    TileGroup(count = 2) { index, place ->
        if (index == 0) {
            TileRow(
                Lucide.Camera,
                "Capture on Play",
                place,
                subtitle = "Opens Play with the camera button",
                onClick = onCapture,
            )
        } else {
            TileRow(
                Lucide.Image,
                "Choose from gallery",
                place,
                subtitle = "A screenshot you took yourself",
                onClick = onGallery,
            )
        }
    }
}
