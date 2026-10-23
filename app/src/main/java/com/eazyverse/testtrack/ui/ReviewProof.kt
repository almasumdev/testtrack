package com.eazyverse.testtrack.ui

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.HardDriveUpload
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The capture half of the review: the one-time guide, the ask for "Display over other apps", and
 * step four: the screenshot with its note, and the two ways to get one.
 */

/**
 * How the Google Play step works, shown once: the first time somebody taps "Open Google Play".
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
        title = if (live) "Review it on Google Play" else "Send feedback on Google Play",
        confirm = "Got it",
        dismiss = null,
        onConfirm = onOk,
        onDismiss = onDismiss,
    ) {
        DialogSteps(
            (
                if (live) {
                    listOf(
                        "The app's Google Play page opens.",
                        "Rate it and write your review, in your own words.",
                        "Once it is posted, tap the blue camera button.",
                        "You come straight back with the screenshot.",
                    )
                } else {
                    listOf(
                        "The app's Google Play page opens.",
                        "Under the testing section, send the developer private feedback.",
                        "Once it is sent, tap the blue camera button.",
                        "You come straight back with the screenshot.",
                    )
                }
            ).map { AnnotatedString(it) },
        )
        Spacer(Modifier.height(8.dp))
        DialogNote("Android asks to let TestTrack capture the screen. It takes one picture, then stops.")
    }
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
            "The camera button floats over Google Play. Allow “Display over other apps” for " +
                "TestTrack, then come back and tap Open Google Play again.",
        confirm = "Open settings",
        onConfirm = onOpen,
        onDismiss = onDismiss,
    )
}

/**
 * Step four: the screenshot and its note as one card, the way a photo and its caption go
 * together, with the two ways to get or replace the screenshot in one row under it.
 *
 * The same shape with or without a screenshot: without one the picture's place is a dashed
 * frame, so adding one changes what is in the frame and nothing on the page moves.
 */
@Composable
fun ProofArea(
    shown: Uri?,
    note: String,
    onNote: (String) -> Unit,
    onCapture: () -> Unit,
    onGallery: () -> Unit,
    onInspect: (Uri) -> Unit = {},
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(CardRadius)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(t.glass)
                .border(1.dp, t.glassBorder, shape)
                .padding(10.dp),
        ) {
            Crossfade(shown, label = "proof") { picture ->
                if (picture == null) EmptyShot() else Shot(picture) { onInspect(picture) }
            }
            Spacer(Modifier.width(12.dp))
            NoteField(note, onNote, Modifier.weight(1f).height(THUMB_HEIGHT))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProofAction(
                Lucide.Camera,
                if (shown == null) "Capture on Play" else "Capture again",
                onCapture,
                Modifier.weight(1f),
            )
            ProofAction(Lucide.Image, "Gallery", onGallery, Modifier.weight(1f))
        }
        if (shown != null) {
            Text(
                "Inspect and hide personal details",
                Modifier
                    .fillMaxWidth()
                    .clickable { onInspect(shown) }
                    .padding(vertical = 12.dp),
                color = t.accentInk,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        // Its own line under the buttons, with room above it, so it reads as a note about where
        // the picture goes rather than as part of the last button.
        Spacer(Modifier.height(if (shown == null) 14.dp else 2.dp))
        Row(verticalAlignment = Alignment.Top) {
            Icon(Lucide.Info, null, Modifier.padding(top = 2.dp).size(15.dp), tint = t.text3)
            Spacer(Modifier.width(8.dp))
            Text(
                "Saved to your Google Drive and shared by link. The developer gets the link, and so do " +
                    "the members who decide it if it is ever disputed.",
                style = MaterialTheme.typography.bodySmall,
                color = t.text3,
            )
        }
    }
}

@Composable
private fun Shot(
    picture: Uri,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    AsyncImage(
        model = picture,
        contentDescription = "Your review screenshot",
        modifier =
            Modifier
                .size(width = THUMB_WIDTH, height = THUMB_HEIGHT)
                .clip(ThumbShape)
                .clickable(onClick = onClick)
                .border(1.dp, t.glassBorder, ThumbShape),
        contentScale = ContentScale.Crop,
    )
}

/** Where the screenshot will go: a dashed frame, read rather than pressed; the buttons do that. */
@Composable
private fun EmptyShot() {
    val t = Tt.colors
    Box(
        Modifier
            .size(width = THUMB_WIDTH, height = THUMB_HEIGHT)
            .drawBehind {
                drawRoundRect(
                    color = t.glassBorder,
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style =
                        Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                        ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.Image, contentDescription = "No screenshot yet", Modifier.size(22.dp), tint = t.text4)
    }
}

/** The note, typed straight into the card beside the picture rather than into a box of its own. */
@Composable
private fun NoteField(
    note: String,
    onNote: (String) -> Unit,
    modifier: Modifier,
) {
    val t = Tt.colors
    val style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 20.sp, color = t.text)
    BasicTextField(
        value = note,
        onValueChange = { onNote(it.take(NOTE_MAX)) },
        // A shade apart from the card, with room inside, so it reads as the place to type.
        modifier =
            modifier
                .clip(ThumbShape)
                .background(t.glass2.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        textStyle = style,
        cursorBrush = SolidColor(t.accent),
        decorationBox = { field ->
            Box {
                if (note.isEmpty()) Text("A note for the developer (optional)", style = style.copy(color = t.text3))
                field()
            }
        },
    )
}

/** One of the two ways to a screenshot. */
@Composable
private fun ProofAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(t.glass2)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = t.accentInk)
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = t.accentInk,
            maxLines = 1,
        )
    }
}

/**
 * Drive is not connected yet. Said under the screenshot, before Send is tapped, with the one
 * button that fixes it: the screenshot is kept in the reviewer's own Drive and the owner sees it
 * by link, so nothing can be sent without it.
 */
@Composable
fun DriveConnectCard(
    busy: Boolean,
    onConnect: () -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(CardRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(t.glass2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.HardDriveUpload, null, Modifier.size(18.dp), tint = t.accentInk)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Connect Google Drive",
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp, lineHeight = 19.sp),
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Saved in your Drive. Anyone with the link can view it, including the developer and authorized dispute jurors.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                color = t.text2,
            )
        }
        Spacer(Modifier.width(10.dp))
        ProofAction(Lucide.HardDrive, if (busy) "Connecting" else "Connect", onClick = { if (!busy) onConnect() })
    }
}

private const val NOTE_MAX = 600

private val THUMB_WIDTH = 72.dp
private val THUMB_HEIGHT = 128.dp
private val ThumbShape = RoundedCornerShape(10.dp)
