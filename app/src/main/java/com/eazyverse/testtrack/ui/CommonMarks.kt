package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            style = MaterialTheme.typography.labelLarge,
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
        Initial(letter, size)
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
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp),
        fontWeight = FontWeight.SemiBold,
        color = tone,
    )
}

/**
 * A [Pill] for a row that is busy, saying which kind of busy it is.
 *
 * Where an action would sit, and shaped like the states beside it, so a list mid round reads as one
 * list rather than a column of buttons with holes punched in it. Sized the same as [Pill], for the
 * same reason.
 *
 * The word is the point. A bare spinner says something is happening and leaves which row is
 * capturing and which is uploading to be worked out from position, which is exactly the thing
 * somebody watching a round wants to know.
 */
@Composable
fun Working(text: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Status.neutralSoft)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            Modifier.size(14.dp),
            strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A cooldown in whole hours, or in days once it is a day or more — nobody needs the minutes. */
fun formatWait(ms: Long): String {
    val hours = (ms / 3_600_000L).coerceAtLeast(1L)
    return if (hours < 24) "${hours}h" else "${(hours + 23) / 24}d"
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

/**
 * [Meter]'s own reasoning, drawn as a ring instead of a row of boxes.
 *
 * Meant for a spot too small for a row to sit comfortably in — a hero number, the leading mark on
 * a row that already has a name and a chevron to fit beside it. Still one tick per thing to be
 * done and not a swept fraction, for the same reason [Meter] counts rather than fills: a handful
 * of apps reads as a handful of ticks, not sixty percent of a circle.
 */
@Composable
fun SegmentedRing(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 6.dp,
    // A physical size rather than a fixed number of degrees. Degrees alone read fine on a large
    // ring and vanish on a small one — the same angle sweeps far less actual arc length once the
    // radius shrinks, which is what made a 44dp ring's gaps disappear into a solid-looking circle
    // even though the math said they were there.
    gap: Dp = 3.dp,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    if (total <= 0) return
    val strokePx = with(LocalDensity.current) { strokeWidth.toPx() }
    val gapPx = with(LocalDensity.current) { gap.toPx() }

    Canvas(modifier) {
        val radius = (size.minDimension - strokePx) / 2f
        val topLeft = Offset((size.width - radius * 2) / 2f, (size.height - radius * 2) / 2f)
        val arcSize = Size(radius * 2, radius * 2)
        val anglePerSegment = 360f / total
        // This ring's own radius decides how many degrees that physical gap needs, capped so a
        // very large count can't demand a gap wider than the segment it is supposed to separate.
        val gapDegrees =
            Math
                .toDegrees((gapPx / radius).toDouble())
                .toFloat()
                .coerceAtMost(anglePerSegment * 0.6f)
        val sweep = anglePerSegment - gapDegrees
        repeat(total) { index ->
            drawArc(
                color = if (index < done) fillColor else trackColor,
                startAngle = -90f + index * anglePerSegment + gapDegrees / 2f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                // Butt, not round — a round cap protrudes past its own path endpoint by about
                // half the stroke width, which is what was quietly eating the gap above back
                // down to nothing: two rounded ends meeting across a few dp of empty path left
                // no empty pixels between them. Butt paints exactly the sweep it is given.
                style = Stroke(width = strokePx, cap = StrokeCap.Butt),
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
