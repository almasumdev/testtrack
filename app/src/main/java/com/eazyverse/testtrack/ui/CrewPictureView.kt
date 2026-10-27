package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.ui.theme.Tt
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * A crew picture, small in a row or a grid and whole when tapped: whose it is, which night, when,
 * the seconds the phone measured, and whether TestTrack took it or the member picked it by hand.
 */

/**
 * How a crew picture is drawn: [ProofImage], unless something else is provided. Only the video
 * frames provide one, since ProofImage's loader rides the API's client and has nowhere to go there.
 */
val LocalPictureImage = staticCompositionLocalOf<(@Composable (String, Boolean) -> Unit)?> { null }

@Composable
private fun Picture(
    ref: String,
    whole: Boolean = false,
) {
    val local = LocalPictureImage.current
    if (local != null) local(ref, whole) else ProofImage(ref, whole)
}

/** One night's picture, as the viewer captions it. */
data class NightPicture(
    val ref: String,
    val who: String,
    val day: Int,
    val at: String,
    val usedMs: Long,
    val byHand: Boolean,
    val app: String,
)

/** A picture cropped to its top, with a yellow edge when it was picked by hand. */
@Composable
internal fun PictureThumb(
    ref: String,
    byHand: Boolean,
    width: Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(width, width * 4 / 3)
            .clip(shape)
            .border(if (byHand) 2.dp else 1.dp, if (byHand) Tt.colors.warn else Tt.colors.glassBorder, shape)
            .clickable(onClickLabel = "Open the picture", onClick = onClick),
    ) { Picture(ref) }
}

/** The picture whole, over everything, with what it proves under it. */
@Composable
internal fun PictureViewer(
    picture: NightPicture,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Tt.colors.inverse)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                IconButton(onClick = onDismiss) { Icon(Lucide.X, "Close", tint = Tt.colors.onInverse) }
            }
            Text(
                "Night ${picture.day}, ${picture.who}",
                Modifier.padding(start = 16.dp, end = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Tt.colors.onInverse,
            )
            val seconds = (picture.usedMs / 1000).toInt()
            Text(
                "Opened ${picture.app}${clockAt(picture.at)}" +
                    (if (seconds > 0) ", $seconds seconds in the app. " else ". ") +
                    if (picture.byHand) "Picked by hand." else "Taken by TestTrack.",
                Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Tt.colors.onInverse.copy(alpha = 0.8f),
            )
            Box(Modifier.padding(horizontal = 16.dp)) { Picture(picture.ref, whole = true) }
        }
    }
}

/** " at 9:14 pm", in this phone's own time, or nothing if the time did not come. */
internal fun clockAt(iso: String): String =
    runCatching {
        val local = OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
        " at " + local.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())).lowercase()
    }.getOrDefault("")
