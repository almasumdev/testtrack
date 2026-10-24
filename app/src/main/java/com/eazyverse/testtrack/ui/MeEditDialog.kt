package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * Changing how you appear to everybody else.
 *
 * The old Firestore profile could do this and the version that replaced it could not, so an
 * account was stuck with whatever Google last called it. Two fields, because two is all an
 * account is allowed to change: the email came from Google and the balance comes from the ledger.
 *
 * The picker is permissionless, it hands back one item the person chose and never gives the app
 * the library, which is why there is no permission prompt anywhere in this flow.
 */
@Composable
fun MeEditDialog(
    name: String,
    /** The picture there is now, as the profile carries it; blank for none. */
    photo: String = "",
    busy: Boolean = false,
    /** Why the last save did not go through, in words a person can act on; null when it did. */
    error: String? = null,
    onSave: (String, Bitmap?) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf(name) }
    // Picked, then framed: the photo goes to the crop first and only the square comes back here.
    var framing by remember { mutableStateOf<Uri?>(null) }
    var picked by remember { mutableStateOf<Bitmap?>(null) }
    val t = Tt.colors

    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) framing = uri
        }

    val choose = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Ask(
        title = "Edit profile",
        body = "Your name and photo are shown to everyone on TestTrack.",
        confirm = if (busy) "Saving" else "Save",
        confirmEnabled = typed.trim().isNotBlank() && !busy,
        onConfirm = { onSave(typed, picked) },
        onDismiss = { if (!busy) onDismiss() },
    ) {
        // The picture in the middle with its camera badge, and the one thing to do with it under it.
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(72.dp)
                    .semantics { contentDescription = "Choose a picture" }
                    // A round press the size of the photo, drawn without clipping the box, which
                    // would cut off the camera badge that sits on its edge.
                    .clickable(
                        interactionSource = null,
                        indication = ripple(bounded = false, radius = 36.dp),
                        enabled = !busy,
                        onClick = choose,
                    ),
            ) {
                val chosen = picked
                if (chosen != null) {
                    val image = remember(chosen) { chosen.asImageBitmap() }
                    Image(image, null, Modifier.size(72.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Avatar(
                        photo,
                        typed
                            .trim()
                            .take(1)
                            .uppercase()
                            .ifBlank { "?" },
                        size = 72.dp,
                        letterStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
                    )
                }
                // Saving a new photo takes a moment on mobile data; the photo itself says so.
                // Drawn before the camera badge, which sits half off the photo's edge, so the dim
                // covers the photo only and the badge stays whole on top.
                if (busy && picked != null) {
                    Box(Modifier.matchParentSize().clip(CircleShape).background(t.scrim), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp, color = t.onAccent)
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(t.glass)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(t.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Camera, null, Modifier.size(12.dp), tint = t.onAccent)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Change photo",
                Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = !busy, onClick = choose).padding(4.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = t.accentInk,
            )
            if (error != null) {
                Text(
                    error,
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = t.bad,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        DialogField(
            value = typed,
            onValueChange = { typed = it.take(NAME_MAX) },
            label = "Name",
            placeholder = "Rafiul",
            error = if (typed.isNotEmpty() && typed.isBlank()) "That is only spaces." else null,
            supporting = "It doesn't have to be your real name.",
            maxLength = NAME_MAX,
        )
    }

    framing?.let { uri ->
        PhotoCrop(
            uri,
            onUse = {
                picked = it
                framing = null
            },
            onCancel = { framing = null },
        )
    }
}

private const val NAME_MAX = 40
