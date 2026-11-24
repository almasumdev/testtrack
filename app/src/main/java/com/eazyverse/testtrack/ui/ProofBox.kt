package com.eazyverse.testtrack.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The Send page's picture box, across the page. Empty, it is a dashed frame that says what goes in
 * it and offers the two ways to get one; a tap anywhere on it captures. Filled, the picture fills
 * it, with the ways to change it laid over the picture's corners.
 */

private val BoxHeight = 160.dp
private val BoxShape = RoundedCornerShape(14.dp)

/**
 * The screenshot to send, or the place for it. [onGallery] null leaves only Capture, where the
 * picture must be taken now on Play. Filled: [onRemove] is the ×, [onHideDetails] the chip that
 * covers anything personal, [onRetake] captures again, and [onOpen] a tap on the picture itself.
 */
@Composable
internal fun ProofBox(
    picture: Uri?,
    onCapture: () -> Unit,
    onGallery: (() -> Unit)?,
    onRemove: () -> Unit,
    onHideDetails: () -> Unit,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
    /** Which part of a tall screenshot the box shows. */
    alignment: Alignment = Alignment.TopCenter,
    /** What is happening to the picture ("Saving picture"): it is shown with a loader over it. */
    working: String? = null,
    /** [working] is the send: the picture is only dimmed, as the Send button already shows the progress. */
    dimOnly: Boolean = false,
) {
    if (picture == null && working == null) {
        EmptyProofBox(onCapture, onGallery, modifier)
    } else {
        FilledProofBox(picture, onRemove, onHideDetails, onRetake, onOpen, alignment, working, dimOnly, modifier)
    }
}

@Composable
private fun EmptyProofBox(
    onCapture: () -> Unit,
    onGallery: (() -> Unit)?,
    modifier: Modifier,
) {
    val t = Tt.colors
    Column(
        modifier
            .fillMaxWidth()
            .height(BoxHeight)
            .dashed(t.glassBorderStrong, 14.dp, width = 1.5.dp)
            .clip(BoxShape)
            .clickable(role = Role.Button, onClickLabel = "Capture on Play", onClick = onCapture),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.semantics { contentDescription = "No screenshot yet" }) {
            IconTile(Lucide.Camera, 40.dp, glyph = 20.dp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RowPill(
                "Capture on Play",
                PillKind.SOFT,
                leading = Lucide.Camera,
                size = PillSize.SMALL,
                touchTarget = false,
                onClick = onCapture,
            )
            if (onGallery != null) {
                RowPill(
                    "Gallery",
                    PillKind.GHOST,
                    leading = Lucide.Image,
                    size = PillSize.SMALL,
                    touchTarget = false,
                    onClick = onGallery,
                )
            }
        }
    }
}

@Composable
private fun FilledProofBox(
    picture: Uri?,
    onRemove: () -> Unit,
    onHideDetails: () -> Unit,
    onRetake: () -> Unit,
    onOpen: (() -> Unit)?,
    alignment: Alignment,
    working: String?,
    dimOnly: Boolean,
    modifier: Modifier,
) {
    val t = Tt.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(BoxHeight)
            .clip(BoxShape),
    ) {
        if (picture == null) {
            Skeleton(height = BoxHeight, corner = 14.dp)
        } else {
            SubcomposeAsyncImage(
                model = picture,
                contentDescription = "Your review screenshot",
                loading = { Skeleton(height = BoxHeight, corner = 14.dp) },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(if (onOpen != null) Modifier.clickable(onClickLabel = "Open the screenshot", onClick = onOpen) else Modifier),
                contentScale = ContentScale.Crop,
                alignment = alignment,
            )
            Box(Modifier.fillMaxSize().border(1.dp, t.cardLine, BoxShape))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(t.photoScrim)
                    .clickable(role = Role.Button, onClickLabel = "Remove the screenshot", onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.X, "Remove", Modifier.size(14.dp), tint = t.onPhoto)
            }
            PhotoChip(
                "Hide details",
                Modifier.align(Alignment.BottomStart).padding(8.dp),
                icon = Lucide.EyeOff,
                size = PartSize.SMALL,
                onClick = onHideDetails,
            )
            PhotoChip(
                "Retake",
                Modifier.align(Alignment.BottomEnd).padding(8.dp),
                icon = Lucide.Camera,
                size = PartSize.SMALL,
                onClick = onRetake,
            )
        }
        if (working != null) WorkingOver(working, dimOnly)
    }
}

/**
 * The loader over the picture while it is kept, and the words for it; while it is sent ([dimOnly])
 * only a grey dim, no loader and no words. Takes the taps beneath either way.
 */
@Composable
private fun WorkingOver(
    words: String,
    dimOnly: Boolean,
) {
    val t = Tt.colors
    Box(
        Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .semantics { contentDescription = words },
    ) {
        if (dimOnly) {
            Box(Modifier.fillMaxSize().background(t.photoScrim.copy(alpha = 0.32f)))
        } else {
            OverMark {
                CircularProgressIndicator(Modifier.size(26.dp), color = t.onPhoto, strokeWidth = 2.5.dp, trackColor = Color.Transparent)
            }
            Text(
                words,
                style = MaterialTheme.typography.labelMedium,
                color = t.onPhoto,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .clip(CircleShape)
                        .background(t.photoScrim)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}
