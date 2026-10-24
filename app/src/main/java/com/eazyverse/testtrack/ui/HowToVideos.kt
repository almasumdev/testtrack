package com.eazyverse.testtrack.ui

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.composables.icons.lucide.CirclePlay
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.HowToWatch

/*
 * The how-to videos, each shown where it explains something: My apps while it is empty, the top of
 * Crew's guide, and Circle's Feedback until somebody has taken part. All three are listed under
 * You, Support, for when the card that brought one up has gone.
 *
 * Every video is on YouTube in English and Bangla, and a tap opens it there: nothing is played in
 * the app and nothing is bundled but the thumbnails. Both languages are always offered, side by
 * side, because the app itself is English and its Bangla readers are the reason the second exists.
 */

internal enum class HowTo(
    /** The fixed id the analytics event carries. */
    val id: String,
    val title: String,
    val line: String,
    /** The English video's length, the one the thumbnail is of. */
    val length: String,
    @DrawableRes val thumb: Int,
    val english: String,
    val bangla: String,
) {
    APPS(
        "apps",
        "How to add your app",
        "From your phone, or by its Play link",
        "1:43",
        R.drawable.howto_apps,
        "https://youtu.be/9vUQ_V2vsbE",
        "https://youtu.be/cfB18sYCrmc",
    ),
    CREW(
        "crew",
        "How Crew works",
        "Taking a seat, the nightly round, and the finish",
        "3:31",
        R.drawable.howto_crew,
        "https://youtu.be/xw8X9rgKfJ8",
        "https://youtu.be/VUX1XmD64uo",
    ),
    CIRCLE(
        "circle",
        "How Circle works",
        "Review apps for tix, then ask for feedback on yours",
        "5:21",
        R.drawable.howto_circle,
        "https://youtu.be/J8oyi1F6Lp4",
        "https://youtu.be/sbWoK4Z7f6E",
    ),
}

/** What the Bangla button says, in Bangla: it is for the people who read it. */
private const val IN_BANGLA = "বাংলায় দেখুন"

private val ChipHeight = Modifier.height(34.dp)

/** Opens [video] in YouTube, or the browser on a phone without it. [fromList] is the Support sheet. */
internal fun watch(
    context: Context,
    video: HowTo,
    bangla: Boolean,
    fromList: Boolean,
) {
    HowToWatch.opened(video.id, bangla, fromList)
    openUrl(context, if (bangla) video.bangla else video.english)
}

/**
 * One video, as a card's body: its thumbnail, what it shows, and a button for each language. The
 * caller gives it the surface it sits on, so it is the same glass as the cards around it. [onHide]
 * puts a close mark in the corner, for a card that can be put away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HowToBody(
    video: HowTo,
    fromList: Boolean = false,
    onHide: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val t = Tt.colors
    Box(Modifier.fillMaxWidth()) {
        Row {
            Thumb(video) { watch(context, video, bangla = false, fromList) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    // Clear of the close mark in the corner, when there is one.
                    Modifier.padding(end = if (onHide != null) 24.dp else 0.dp),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = t.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    video.line,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                    color = t.text3,
                )
                Spacer(Modifier.height(8.dp))
                // A row while both fit, two lines at a large font size rather than a cut label. One
                // height for both: Bangla script stands taller than the Latin beside it.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionChip(
                        "Watch",
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.primaryContainer,
                        ChipHeight,
                    ) { watch(context, video, bangla = false, fromList) }
                    ActionChip(IN_BANGLA, t.text, t.glass2, ChipHeight) { watch(context, video, bangla = true, fromList) }
                }
            }
        }
        if (onHide != null) {
            // Drawn small and touched large: the 40 dp target reaches out over the card's padding.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-12).dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Hide", onClick = onHide),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.X, "Hide", Modifier.size(16.dp), tint = t.text4)
            }
        }
    }
}

/** The video's own thumbnail, with a play mark and its length, as YouTube shows one. */
@Composable
private fun Thumb(
    video: HowTo,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .size(width = 112.dp, height = 63.dp)
            .clip(shape)
            .clickable(onClickLabel = "Watch", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(video.thumb), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(t.scrim),
            contentAlignment = Alignment.Center,
        ) {
            // Nudged right: a triangle's weight sits left of its box.
            Icon(Lucide.Play, null, Modifier.size(14.dp).offset(x = 1.dp), tint = t.onAccent)
        }
        Text(
            video.length,
            Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(t.scrim)
                .padding(horizontal = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = t.onAccent,
        )
    }
}

/**
 * The video for an empty screen, as two quiet links under its one button: the button stays the
 * thing to do, and the video is there for whoever wants to see how first.
 */
@Composable
internal fun HowToLinks(video: HowTo) {
    val context = LocalContext.current
    val ink = ButtonDefaults.textButtonColors(contentColor = Tt.colors.accentInk)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TextButton(onClick = { watch(context, video, bangla = false, fromList = false) }, colors = ink) {
            Icon(Lucide.CirclePlay, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Watch ${video.title.replaceFirstChar { it.lowercase() }}", fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
        }
        TextButton(onClick = { watch(context, video, bangla = true, fromList = false) }, colors = ink) {
            Icon(Lucide.CirclePlay, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(IN_BANGLA, fontWeight = FontWeight.SemiBold, fontSize = 14.5.sp)
        }
    }
}

/** All three videos, in the order somebody needs them, for the Support row under You. */
@Composable
internal fun HowToSheet(onDismiss: () -> Unit) {
    PickerSheet(
        title = "How-to videos",
        subtitle = "Every tap, step by step. They open in YouTube.",
        onDismiss = onDismiss,
        skipHalf = true,
    ) {
        HowTo.entries.forEachIndexed { index, video ->
            if (index > 0) {
                Spacer(Modifier.height(14.dp))
                CardRule()
                Spacer(Modifier.height(14.dp))
            }
            HowToBody(video, fromList = true)
        }
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * Whether Circle's card has been put away: by its close mark, or for good once this phone's account
 * has a review or a request of its own, by when the video has done its job.
 */
internal object HowToCards {
    private const val PREFS = "testtrack.howto"
    private const val CIRCLE_GONE = "circle_gone"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun circleGone(context: Context): Boolean = prefs(context).getBoolean(CIRCLE_GONE, false)

    fun hideCircle(context: Context) = prefs(context).edit { putBoolean(CIRCLE_GONE, true) }
}
