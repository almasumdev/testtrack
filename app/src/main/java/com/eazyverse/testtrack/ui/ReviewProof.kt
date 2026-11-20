package com.eazyverse.testtrack.ui

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.EyeOff
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
        DialogNote(
            "Android asks to let TestTrack capture the screen. It takes one picture, then stops. " +
                "If the button is in the way, drag it to any edge.",
        )
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
                "TestTrack. When you return, capture continues if permission is granted.",
        confirm = "Open settings",
        onConfirm = onOpen,
        onDismiss = onDismiss,
    )
}

/**
 * Step four: the screenshot in a phone-shaped frame with the ways to get it beside it, then the
 * text in an outlined box that grows to six lines, with its count under it.
 *
 * The same shape with or without a screenshot: without one the picture's place is a dashed
 * frame, so adding one changes what is in the frame and nothing on the page moves.
 */
@Composable
fun ProofArea(
    shown: Uri?,
    /** Null when only a screenshot is asked for, as when showing a review again. */
    note: String?,
    onNote: (String) -> Unit,
    /** Characters of pasted text the request asks for; 0 when the note is optional. */
    needs: Int = 0,
    onCapture: () -> Unit,
    /** Null where the picture must be taken now, on Play, rather than picked. */
    onGallery: (() -> Unit)?,
    onInspect: (Uri) -> Unit = {},
    /** The box's label and the hint inside it once it has focus. */
    label: String = if (needs > 0) "Your feedback for the developer" else "A note for the developer (optional)",
    placeholder: String? = null,
    /** The developer's ready review, put in the box with one tap; null without one. */
    ready: String? = null,
) {
    val t = Tt.colors
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Crossfade(shown, label = "proof") { picture ->
                if (picture == null) EmptyShot() else Shot(picture) { onInspect(picture) }
            }
            Column(Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RowPill(
                    if (shown == null) "Capture on Play" else "Capture again",
                    PillKind.SOFT,
                    leading = Lucide.Camera,
                    leadingSize = 15.dp,
                    onClick = onCapture,
                )
                if (onGallery != null) RowPill("Gallery", PillKind.SOFT, leading = Lucide.Image, leadingSize = 15.dp, onClick = onGallery)
                if (shown != null) InlineAction("Hide details", leading = Lucide.EyeOff) { onInspect(shown) }
                if (onGallery == null) {
                    InfoLine(Lucide.Info, AnnotatedString("It has to be taken now, on Play."))
                }
            }
        }
        if (note != null) {
            Spacer(Modifier.height(18.dp))
            val have = plainLength(note)
            OutlinedField(
                value = note,
                onValueChange = { onNote(it.take(NOTE_MAX)) },
                label = label,
                placeholder = placeholder,
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                imeAction = androidx.compose.ui.text.input.ImeAction.Default,
                maxLength = NOTE_MAX,
                hint =
                    when {
                        needs <= 0 -> null
                        have >= needs -> "$have characters, enough"
                        else -> "$have of $needs characters"
                    },
                hintTone = if (needs > 0 && have >= needs) t.ok else null,
            )
            if (ready != null && note.trim() != ready.trim()) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.offset(x = (-8).dp)) { InlineAction("Put the ready review in") { onNote(ready.take(NOTE_MAX)) } }
            }
        }
        Spacer(Modifier.height(if (note != null && ready == null) 16.dp else 10.dp))
        InfoLine(
            Lucide.EyeOff,
            AnnotatedString("The developer sees this picture. Hide anything personal."),
            title = "Who sees it",
        ) {
            CalloutLine(AnnotatedString("If they dispute it, ${FeedRules.JURORS.words()} members and the TestTrack team see it too."))
        }
    }
}

@Composable
private fun Shot(
    picture: Uri,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    SubcomposeAsyncImage(
        model = picture,
        contentDescription = "Your review screenshot",
        loading = { Skeleton(THUMB_WIDTH, THUMB_HEIGHT, 10.dp) },
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

private const val NOTE_MAX = 600

private val THUMB_WIDTH = 72.dp
private val THUMB_HEIGHT = 128.dp
private val ThumbShape = RoundedCornerShape(10.dp)
