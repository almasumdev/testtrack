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
import com.eazyverse.testtrack.domain.clockTime
import com.eazyverse.testtrack.domain.instantOf
import com.eazyverse.testtrack.ui.theme.Tt

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

/**
 * What [picture] proves, under it whole: when the app was opened, for how long, and who took it.
 * The time is in the phone's 12 or 24 hour clock, as [hour24] says.
 */
internal fun nightProves(
    picture: NightPicture,
    hour24: Boolean = false,
): String {
    val seconds = (picture.usedMs / 1000).toInt()
    return "Opened ${picture.app}${clockAt(picture.at, hour24)}" +
        (if (seconds > 0) ", $seconds seconds in the app. " else ". ") +
        if (picture.byHand) "Picked by hand." else "Taken by TestTrack."
}

/** " at 9:14 PM" (or " at 21:14"), in this phone's own time, or nothing if the time did not come. */
internal fun clockAt(
    iso: String,
    hour24: Boolean = false,
): String = instantOf(iso)?.let { " at " + clockTime(it, hour24).lowercase() }.orEmpty()
