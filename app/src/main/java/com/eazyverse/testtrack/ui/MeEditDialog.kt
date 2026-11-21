package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * Changing how you appear to everybody else.
 *
 * The old Firestore profile could do this and the version that replaced it could not, so an
 * account was stuck with whatever Google last called it. Two fields, because two is all an
 * account is allowed to change: the email came from Google and the balance comes from the ledger.
 *
 * The picker is permissionless, it hands back one item the person chose and never gives the app
 * the library, which is why there is no permission prompt anywhere in this flow. The photo is one
 * row, not a face with a camera badge: the whole row is the target and says what a tap does.
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

    val choose = rememberImagePicker { uri -> if (uri != null) framing = uri }
    Ask(
        title = "Edit profile",
        confirm = if (busy) "Saving" else "Save",
        confirmEnabled = typed.trim().isNotBlank(),
        busy = busy,
        onConfirm = { onSave(typed, picked) },
        onDismiss = onDismiss,
    ) {
        PhotoRow(
            photo = photo,
            picked = picked,
            letter = initialOf(typed),
            saving = busy && picked != null,
            enabled = !busy,
            onClick = choose,
        )
        if (error != null) {
            Text(
                error,
                Modifier.padding(top = 6.dp),
                style = TextStyles.DialogNote,
                color = t.bad,
            )
        }
        Spacer(Modifier.height(14.dp))
        OutlinedField(
            value = typed,
            onValueChange = { typed = it.take(NAME_MAX) },
            label = "Name",
            placeholder = "Rafiul",
            error = if (typed.isNotEmpty() && typed.isBlank()) "That is only spaces." else null,
            hint = "Shown to everyone. Any name works.",
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

/**
 * The picture and the one thing to do with it, as one row and one tap: the face at 48dp, then what
 * a tap does and where the picture comes from. While a new picture saves, the face dims under a
 * spinner and the row says so.
 */
@Composable
private fun PhotoRow(
    photo: String,
    picked: Bitmap?,
    letter: String,
    saving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(1.dp, t.cardLine, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(PhotoSize)) {
            if (picked != null) {
                val image = remember(picked) { picked.asImageBitmap() }
                Image(image, null, Modifier.size(PhotoSize).clip(CircleShape), contentScale = ContentScale.Crop)
            } else {
                Avatar(photo, letter, size = PhotoSize, letterStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp))
            }
            if (saving) {
                Box(Modifier.matchParentSize().clip(CircleShape).background(t.scrim), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = t.onAccent)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    saving -> "Saving photo"
                    picked != null || photo.isNotBlank() -> "Change photo"
                    else -> "Add a photo"
                },
                style = PhotoLabel,
                color = if (saving) t.text3 else t.accentInk,
            )
            Text("From your gallery", Modifier.padding(top = 1.dp), style = PhotoFrom, color = t.text3)
        }
    }
}

private val PhotoSize = 48.dp
private val PhotoLabel = tileStyle(14.5f, 19f, FontWeight.Bold)
private val PhotoFrom = tileStyle(12.5f, 16f, FontWeight.Normal)

private const val NAME_MAX = 40
