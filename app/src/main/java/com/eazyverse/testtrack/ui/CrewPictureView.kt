package com.eazyverse.testtrack.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * A crew picture, small in a row or a grid, and what [PictureViewer] says over it when tapped: whose it
 * is, which night, when, the seconds the phone measured, and whether TestTrack took it or the member
 * picked it by hand.
 */

/**
 * How a crew picture is drawn: [ProofImage], unless something else is provided. Only the video
 * frames provide one, since ProofImage's loader rides the API's client and has nowhere to go there.
 */
val LocalPictureImage = staticCompositionLocalOf<(@Composable (String, Boolean) -> Unit)?> { null }

@Composable
private fun Picture(ref: String) {
    val local = LocalPictureImage.current
    if (local != null) local(ref, false) else ProofImage(ref)
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
    /** Goes first, such as a 48 touch area round a small thumb. */
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .size(width, width * 4 / 3)
            .clip(shape)
            .border(if (byHand) 2.dp else 1.dp, if (byHand) Tt.colors.warn else Tt.colors.glassBorder, shape)
            .clickable(onClickLabel = "Open the picture", onClick = onClick),
    ) { Picture(ref) }
}

/** "Night 4, Sofia Alvarez", over [picture] whole in [PictureViewer]. */
internal fun nightTitle(picture: NightPicture): String = "Night ${picture.day}, ${picture.who}"

/** What [picture] proves, under it whole: when the app was opened, for how long, and who took it. */
internal fun nightProves(picture: NightPicture): String {
    val seconds = (picture.usedMs / 1000).toInt()
    return "Opened ${picture.app}${clockAt(picture.at)}" +
        (if (seconds > 0) ", $seconds seconds in the app. " else ". ") +
        if (picture.byHand) "Picked by hand." else "Taken by TestTrack."
}

/** " at 9:14 pm", in this phone's own time, or nothing if the time did not come. */
internal fun clockAt(iso: String): String =
    runCatching {
        val local = OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
        " at " + local.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())).lowercase()
    }.getOrDefault("")
