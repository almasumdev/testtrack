package com.eazyverse.testtrack.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The capture half of the review: the one-time guide, the ask for "Display over other apps", and
 * step four's proof with its optional note.
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
 * Step four: the screenshot that was taken, a way to take it again, and an optional note.
 *
 * The note is not the review (that is on Play); it is anything worth saying to the developer
 * alongside it, and an empty one is fine.
 */
@Composable
fun ProofForm(
    proof: Uri,
    note: String,
    onNote: (String) -> Unit,
    onRetake: () -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(16.dp)
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
                        .size(width = 54.dp, height = 96.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .border(1.dp, t.glassBorder, RoundedCornerShape(9.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Review screenshot", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Attached as your proof", style = MaterialTheme.typography.bodySmall, color = t.text3)
            }
            Chip("Retake", onRetake)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { onNote(it.take(NOTE_MAX)) },
            modifier = Modifier.fillMaxWidth().height(96.dp),
            placeholder = { Text("Anything else for the developer? (optional)", fontSize = 14.sp) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            shape = RoundedCornerShape(16.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = t.glass,
                    focusedContainerColor = t.glass,
                    unfocusedBorderColor = t.glassBorder,
                ),
        )
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
fun ProofChoices(
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
