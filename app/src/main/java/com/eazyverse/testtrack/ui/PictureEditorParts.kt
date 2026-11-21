package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The picture editor's simpler pieces: how to hide details, the footer with one button that
 * finishes, and what shows when the picture can't be opened.
 */

/**
 * The [PictureEditor] for a picture that is kept rather than sent: [title] and [copy] under it with
 * one wide [primaryLabel] button. The button flattens the picture at full size, boxes burned in, and
 * hands it to [write], whose answer goes to [onDone]. The editor stays open, its button spinning,
 * until [onDone] returns: closing it sooner would leave the picture kept before in place, details
 * and all, while the new one is still on its way. A throw from either says [failText] and the
 * editor stays open, so nothing falls back to the picture as it was without the person knowing.
 */
@Composable
internal fun PictureEditor(
    source: Uri,
    tools: Set<PictureTool>,
    title: String,
    copy: String,
    primaryLabel: String,
    failText: String,
    unreadable: String,
    write: suspend (Bitmap) -> Uri,
    onDone: suspend (Uri) -> Unit,
    onClose: () -> Unit,
) {
    PictureEditor(source, tools, onClose, unreadable = unreadable) { footer ->
        EditorFooter(title, copy, footer.failed, primaryLabel, footer.busy) {
            footer.finish(failText) { picture ->
                val flat = withContext(Dispatchers.Default) { picture.flatten() }
                val written =
                    try {
                        write(flat)
                    } finally {
                        flat.recycle()
                    }
                onDone(written)
                true
            }
        }
    }
}

/** While hiding: how it works, and how many boxes there are. */
@Composable
internal fun HideBar(hidden: Int) {
    val t = Tt.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(
            "Drag a box over names, emails and photos. Pinch to zoom in.",
            style = MaterialTheme.typography.bodyMedium,
            color = t.onPhoto,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when (hidden) {
                0 -> "Nothing hidden yet"
                1 -> "1 hidden area"
                else -> "$hidden hidden areas"
            },
            style = MaterialTheme.typography.bodySmall,
            color = t.onPhoto.copy(alpha = 0.7f),
        )
    }
}

/** Under the picture with no tool open: [title], [copy], [problem] if any, and one wide button. */
@Composable
internal fun EditorFooter(
    title: String,
    copy: String,
    problem: String?,
    label: String,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = t.onPhoto)
        Spacer(Modifier.height(4.dp))
        Text(copy, style = MaterialTheme.typography.bodySmall, color = t.onPhoto.copy(alpha = 0.75f))
        if (problem != null) {
            Spacer(Modifier.height(8.dp))
            Row {
                Icon(Lucide.CircleAlert, null, Modifier.padding(top = 1.dp).size(15.dp), tint = t.photoBad)
                Spacer(Modifier.width(6.dp))
                Text(problem, style = MaterialTheme.typography.bodySmall, color = t.photoBad)
            }
        }
        Spacer(Modifier.height(12.dp))
        Primary(label, busy = busy, busyKeepsFill = true, onClick = onClick)
    }
}

/** The picture couldn't be opened: [text] in the middle, and close. */
@Composable
internal fun Unreadable(
    text: String,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Box(Modifier.padding(horizontal = 12.dp).height(EditorTopBar), contentAlignment = Alignment.Center) {
            EditorButton(Lucide.X, "Close", onClick = onClose)
        }
        Text(
            text,
            Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Tt.colors.onPhoto,
            textAlign = TextAlign.Center,
        )
    }
}
