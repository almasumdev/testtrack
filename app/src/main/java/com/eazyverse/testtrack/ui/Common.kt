package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.ui.theme.LocalStatusColors
import androidx.compose.foundation.Image as ComposeImage

/**
 * The pieces every screen is built from, so consistency is the path of least resistance.
 */

/**
 * The page gutter. Every screen, every row, no exceptions.
 *
 * Sixteen, which is what the console settled on. The two apps are read one after the other by the
 * same person on the same phone, and a four point step in the margin between them is the kind of
 * difference nobody names but everybody feels.
 */
val Gutter = 16.dp

/** State colours, from the theme. Never used for a control — that is what primary is for. */
object Status {
    val posted: Color @Composable get() = LocalStatusColors.current.posted
    val postedSoft: Color @Composable get() = LocalStatusColors.current.postedSoft
    val short: Color @Composable get() = LocalStatusColors.current.short
    val shortSoft: Color @Composable get() = LocalStatusColors.current.shortSoft
    val missed: Color @Composable get() = LocalStatusColors.current.missed
    val missedSoft: Color @Composable get() = LocalStatusColors.current.missedSoft
    val upcoming: Color @Composable get() = LocalStatusColors.current.upcoming
    val neutralSoft: Color @Composable get() = LocalStatusColors.current.neutralSoft
}

// ---- structure ---------------------------------------------------------------------------

@Composable
fun SectionLabel(text: String, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        trailing?.let {
            Spacer(Modifier.weight(1f))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A group of rows, on the page rather than on a card.
 *
 * This used to be a raised surface with rounded corners, and the surface was doing the organising:
 * rows inside were separated by their own padding and the panel edge drew the boundary once. It
 * worked, and it made every list on the screen a floating slab, indented from both margins, with
 * the page showing through between them.
 *
 * The console went the other way and it reads better: rows sit on the page ground at the full
 * width, and what separates them is [RowDivider]. Nothing is raised, so nothing has to be aligned
 * against anything else, and a list that runs to fourteen apps has one less edge in it.
 */
@Composable
fun Rows(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), content = content)
}

/**
 * A dashed rule between rows.
 *
 * Dashed rather than solid, which is a small thing that stops mattering the moment you see
 * fourteen of them: a solid rule between every row is fourteen hard lines competing with the
 * content they are meant to organise, and a dashed one recedes far enough to be structure rather
 * than furniture.
 *
 * Inset by the gutter so it lines up with the text either side of it and never touches the screen
 * edge.
 */
@Composable
fun RowDivider() {
    val ink = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .height(1.dp)
    ) {
        drawLine(
            color = ink,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.5.dp.toPx(), 3.dp.toPx()))
        )
    }
}

/**
 * The other actions on a screen, beside its one [Primary].
 *
 * Outlined, and short enough to sit two to a row. Four of these stacked full width is four equally
 * loud invitations and half a phone of button, when what they actually are is the things you might
 * do before the one thing you came to do.
 */
@Composable
fun Secondary(
    label: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier.height(42.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            color = if (destructive) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * A small bordered action, for the handful that are neither the screen's one primary nor a row.
 *
 * As full width text links stacked one above the other they read as a menu of equally weighted
 * choices while pushing the list, which is the reason the screen exists, below the fold.
 */
@Composable
fun Chip(label: String, onClick: () -> Unit) {
    Text(
        label,
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * Long enough to be seen, short enough not to be waited on.
 *
 * A pull that finishes in eighty milliseconds flashes the skeleton and puts it away again, which
 * reads as a glitch rather than as a refresh. Held to a floor so the answer to "did that do
 * anything" is always yes.
 */
const val MIN_REFRESH = 550L

suspend fun holdShimmer(startedAt: Long) {
    val left = MIN_REFRESH - (System.currentTimeMillis() - startedAt)
    if (left > 0) kotlinx.coroutines.delay(left)
}

/**
 * Nothing here yet, said properly.
 *
 * A bare sentence in the middle of a screen reads like a failure. An icon, a title and a line of
 * explanation reads like a state the product expected.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null,
                Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** A sentence where content would be, when a full empty state would be too much. */
@Composable
fun Blank(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = Gutter, vertical = 14.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Inline, in the error colour. Dialogs are for destructive confirmation only. */
@Composable
fun Failure(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = Gutter, vertical = 14.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
}

/**
 * The one filled action a screen gets.
 *
 * [mark] is for the single case where a button is asking somebody else's service rather than doing
 * something itself, and the mark is how anyone recognises which one. Drawn as an Icon and left to
 * take the default tint, which is the same colour the label is painted in and follows it into the
 * disabled state as well. It gives way to the spinner while the button is busy: two things sitting
 * in the same place is one too many.
 */
@Composable
fun Primary(
    label: String,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    enabled: Boolean = true,
    tall: Boolean = false,
    mark: Painter? = null,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().height(if (tall) 52.dp else 46.dp)
    ) {
        if (busy) {
            androidx.compose.material3.CircularProgressIndicator(
                Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(10.dp))
        } else if (mark != null) {
            Icon(mark, null, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

// ---- marks -------------------------------------------------------------------------------

/**
 * A launcher icon, or a lettered tile when the app is not installed.
 *
 * This is the single biggest thing separating a list of apps from a list of strings, and it costs
 * nothing — the launcher `<queries>` filter is already declared for the install streak, and an app
 * with a launcher icon to draw is by definition one that filter can see.
 */
@Composable
fun AppIcon(pkg: String, label: String, size: Dp = 44.dp) {
    val context = LocalContext.current
    // The revision is a key here too. An app that was absent cached a null icon, and without this
    // the row stays a grey placeholder for the rest of the session after it is installed.
    val bitmap: ImageBitmap? = remember(pkg, InstalledApps.revision) {
        InstalledApps.icon(context, pkg)?.let { drawable ->
            runCatching {
                val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 108
                val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 108
                drawable.toBitmap(w, h).asImageBitmap()
            }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(size * 0.3f)

    if (bitmap != null) {
        ComposeImage(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape)
        )
    } else {
        Box(
            Modifier.size(size).clip(shape).background(Status.neutralSoft),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A tester's initial in a tinted disc — the only stand-in for an avatar we have. */
@Composable
fun Initial(letter: String, size: Dp = 34.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** Scannable down a column of twelve without reading any of them. */
@Composable
fun Pill(text: String, tone: Color, background: Color) {
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = tone
    )
}

/** Today, at a glance, without reading a word. */
@Composable
fun Ring(done: Int, total: Int, size: Dp = 36.dp) {
    val track = Status.upcoming
    val fill = MaterialTheme.colorScheme.primary
    val fraction = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.13f
            val inset = stroke / 2f
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    this.size.width - stroke, this.size.height - stroke
                ),
                style = Stroke(stroke)
            )
            if (fraction > 0f) {
                drawArc(
                    color = fill, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        this.size.width - stroke, this.size.height - stroke
                    ),
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            if (total <= 0) "–" else "$done",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
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
fun Meter(done: Int, total: Int, modifier: Modifier = Modifier, height: Dp = 10.dp) {
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
                    .background(if (on) filled else Color.Transparent)
            )
        }
    }
}

/** A horizontal proportion bar. Iris, because progress is something you act on. */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(
        modifier.fillMaxWidth().height(height).clip(CircleShape).background(Status.upcoming)
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

// ---- loading -----------------------------------------------------------------------------

/**
 * A placeholder in the shape of what is arriving.
 *
 * Never a spinner in the middle of an empty screen: a skeleton says how much is coming and where
 * it will be, and because results are cached this is only ever seen on genuinely first sight.
 */
@Composable
fun Skeleton(width: Dp? = null, height: Dp = 12.dp, corner: Dp = 6.dp, modifier: Modifier = Modifier) {
    val base = Status.upcoming
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)

    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "shift"
    )

    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(
                    listOf(base, highlight, base),
                    start = Offset(shift, 0f),
                    end = Offset(shift + 400f, 0f)
                )
            )
    )
}

/**
 * Rows in the shape of the list that is coming.
 *
 * No dividers, because the list it stands in for has none — a skeleton that draws rules the real
 * content will not is a promise the arriving screen breaks. Enough rows to fill the space too: a
 * cohort is fourteen apps, and three placeholders under a full-height panel reads as "finished,
 * and nearly empty" rather than "still loading".
 */
/**
 * A whole screen, before any of it has arrived.
 *
 * One skeleton for the page, never one per section, because sections do not land together. A
 * header drawn over a list that is still loading states things the list is about to contradict —
 * a group page whose apps have not turned up reads as "everything's done for today", in green,
 * and then flips to "start testing · 5 left". The header was not wrong; it was early.
 *
 * It stands in for content and not for structure: no section labels, no headings. A label is a
 * claim that the section exists, and some of them will not. Content landing shifts the layout a
 * little; a heading over something that never appears is worse.
 */
@Composable
fun SkeletonPage(rows: Int = 8, showAction: Boolean = true, showTrailing: Boolean = true) {
    Spacer(Modifier.height(8.dp))
    Column(Modifier.padding(horizontal = Gutter)) {
        Skeleton(width = 148.dp, height = 34.dp, corner = 10.dp)
        Spacer(Modifier.height(12.dp))
        Skeleton(width = 210.dp, height = 12.dp)
        Spacer(Modifier.height(18.dp))
        Skeleton(height = 6.dp, corner = 3.dp)
        if (showAction) {
            Spacer(Modifier.height(26.dp))
            Skeleton(height = 52.dp, corner = 16.dp)
        }
    }
    Spacer(Modifier.height(28.dp))
    Rows { SkeletonRows(rows, showTrailing) }
}

@Composable
fun SkeletonRows(count: Int = 7, showTrailing: Boolean = true) {
    // Widths cycle rather than march, so a long run does not look like a wedge.
    val titles = listOf(118, 92, 140, 104, 126, 86, 132)
    val metas = listOf(168, 132, 190, 150, 118, 176, 142)

    repeat(count) { index ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Skeleton(width = 44.dp, height = 44.dp, corner = 13.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Skeleton(width = titles[index % titles.size].dp, height = 12.dp)
                Spacer(Modifier.height(7.dp))
                Skeleton(width = metas[index % metas.size].dp, height = 10.dp)
            }
            if (showTrailing) {
                Spacer(Modifier.width(12.dp))
                Skeleton(width = 46.dp, height = 20.dp, corner = 8.dp)
            }
        }
    }
}

// ---- dialogs -----------------------------------------------------------------------------

/**
 * The one dialog shape this app uses.
 *
 * Material's default container is `surfaceContainerHigh`, a grey nothing else here is painted in,
 * so every dialog arrived looking like a piece of another product dropped on top of this one. This
 * takes the panel's own surface and corner, which is what the rest of the app is built from.
 *
 * Destructive actions are named rather than coloured red by default. "Sign out" is not a warning,
 * and a dialog where the confirm button is always alarming teaches people to ignore the alarm — so
 * [destructive] is opt-in, for the ones that genuinely take something away.
 *
 * [body] covers the ordinary case of a sentence or two. [content] is for the few that need a field
 * or an image, and both may be given: the prose comes first.
 */
@Composable
fun Ask(
    title: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    body: String? = null,
    dismiss: String? = "Cancel",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        },
        text = if (body == null && content == null) null else {
            {
                Column {
                    body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    content?.let {
                        if (body != null) Spacer(Modifier.height(14.dp))
                        it()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(
                    confirm,
                    fontWeight = FontWeight.SemiBold,
                    color = when {
                        !confirmEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        destructive -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        },
        dismissButton = dismiss?.let {
            {
                TextButton(onClick = onDismiss) {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}

/** A field inside [Ask], matching the shape the rest of the app uses for one. */
@Composable
fun DialogField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    error: String? = null,
    supporting: String? = null,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}
