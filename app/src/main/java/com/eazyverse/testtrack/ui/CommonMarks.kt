package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.Photo
import androidx.compose.foundation.Image as ComposeImage

// ---- marks -------------------------------------------------------------------------------

/**
 * A launcher icon, or a lettered tile when the app is not installed.
 *
 * This is the single biggest thing separating a list of apps from a list of strings, and it costs
 * nothing — the launcher `<queries>` filter is already declared for the install streak, and an app
 * with a launcher icon to draw is by definition one that filter can see.
 */
@Composable
fun AppIcon(
    pkg: String,
    label: String,
    size: Dp = 44.dp,
    /**
     * Overrides the square. A row that sets its own height wants the icon to match whatever the
     * text beside it came out at, and [size] is then only the corner radius it is rounded by.
     */
    modifier: Modifier = Modifier.size(size),
    /** Drawn when the app is not on the phone; the grey lettered square when not given. */
    fallback: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    // The revision is a key here too. An app that was absent cached a null icon, and without this
    // the row stays a grey placeholder for the rest of the session after it is installed.
    val bitmap: ImageBitmap? =
        remember(pkg, InstalledApps.revision) {
            InstalledApps.iconBitmap(context, pkg)
        }
    val shape = RoundedCornerShape(size * 0.3f)

    if (bitmap != null) {
        ComposeImage(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else if (fallback != null) {
        fallback()
    } else {
        Box(
            modifier.clip(shape).background(Status.neutralSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A tester's initial in a tinted disc — the only stand-in for an avatar we have. */
@Composable
fun Initial(
    letter: String,
    size: Dp = 34.dp,
    /** Larger faces need a larger letter; a row-sized one in an 84dp circle reads as a dot. */
    letterStyle: TextStyle? = null,
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            style = letterStyle ?: MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * Somebody's face, or their letter where there is no face.
 *
 * The fallback is not an error state and is not drawn like one. Most accounts will never set a
 * picture, so [Initial] is the ordinary case and this is the exception that improves on it.
 *
 * Decoding is remembered against the encoded string rather than done on every recomposition,
 * because this is drawn in list rows and the string is the only thing that can change it.
 */
@Composable
fun Avatar(
    photo: String,
    letter: String,
    size: Dp = 34.dp,
    loading: Boolean = false,
    letterStyle: TextStyle? = null,
) {
    // Nothing has been fetched yet, so there is no letter to show either — the name it would come
    // from is on the same document as the picture. A shimmer says "coming", where a letter would
    // say "this is who they are" and then change its mind.
    if (loading) {
        Skeleton(width = size, height = size, corner = size / 2)
        return
    }

    val bitmap = remember(photo) { Photo.decode(photo)?.asImageBitmap() }
    if (bitmap == null) {
        Initial(letter, size, letterStyle)
    } else {
        ComposeImage(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * Scannable down a column of twelve without reading any of them.
 *
 * Sized to match [ActionChip][com.eazyverse.testtrack.ui.ActionChip] and [Working] exactly — same
 * corner, same padding, same bumped-up text size — so a row does not visibly grow or shrink as it
 * moves between an actionable state and a settled one. It used to be a size of its own, smaller all
 * round, and a list mid scroll read as buttons of two different sizes rather than one list.
 */
@Composable
fun Pill(
    text: String,
    tone: Color,
    background: Color,
) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp),
        fontWeight = FontWeight.SemiBold,
        color = tone,
    )
}

/**
 * Progress as a fixed number of boxes, one per thing to be done.
 *
 * The console's meter, and the same reasoning: five steps is a small enough number to be counted,
 * and a bar filling by fifths says less than five boxes with two of them lit. A proportion bar is
 * for quantities nobody counts, which is what the overload below is for.
 *
 * Every box carries its own edge, drawn inside its bounds, and a filled one is painted inside that
 * edge rather than over it. So all of them sit on one grid at one size whether they are done or
 * not, and nothing grows by a pixel as it fills.
 */
@Composable
fun Meter(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    if (total <= 0) return
    val box = RoundedCornerShape(3.dp)
    val filled = MaterialTheme.colorScheme.primary
    val edge = MaterialTheme.colorScheme.outlineVariant

    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { index ->
            val on = index < done
            Box(
                Modifier
                    .weight(1f)
                    .height(height)
                    .border(1.dp, if (on) filled else edge, box)
                    // Inside the edge, not across it. The border is the box; the fill is what is
                    // in it.
                    .padding(1.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (on) filled else Color.Transparent),
            )
        }
    }
}

/** A horizontal proportion bar. Iris, because progress is something you act on. */
@Composable
fun Meter(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Status.upcoming),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * A [TextButton] enforces Material's ~40dp minimum touch height no matter what contentPadding
 * says, which is too tall for a row meant to hold five or six of these side by side. Built on a
 * plain clickable [Box] instead — the same shape the [reportDetails]-reading "Reported" chip
 * already used — so the padding below is the actual, final size.
 */
@Composable
fun ActionChip(
    text: String,
    tone: Color,
    background: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = tone,
            maxLines = 1,
        )
    }
}
