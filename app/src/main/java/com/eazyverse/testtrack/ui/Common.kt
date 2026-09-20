package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.ui.theme.LocalStatusColors

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

/**
 * A pull to refresh, in Material's own stock appearance.
 *
 * One place for it rather than four, so every screen's gesture looks and feels identical.
 * [isRefreshing] doubles as the signal for a refresh this screen started on its own — opening it
 * or coming back to it — not only one the reader pulled for themselves; the indicator is the one
 * place that state needs to show up, rather than a second one competing with a page's own numbers
 * for attention.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        content = content,
    )
}

// ---- structure ---------------------------------------------------------------------------

@Composable
fun SectionLabel(
    text: String,
    trailing: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 22.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.let {
            Spacer(Modifier.weight(1f))
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
fun Rows(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
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
            .height(1.dp),
    ) {
        drawLine(
            color = ink,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.5.dp.toPx(), 3.dp.toPx())),
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
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = modifier.height(42.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            // Dimmed rather than merely inert. A control that ignores taps while still looking
            // pressable reads as the app having missed them.
            color =
                when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    destructive -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
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
fun Chip(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        label,
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
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
    modifier: Modifier = Modifier,
    /**
     * The way out, where there is one.
     *
     * Added for the screens a signed-out reader can now reach. "Sign in to see your apps" with
     * nothing to press is a dead end, and the reader has to work out for themselves that the
     * answer is on a different tab.
     */
    actionLabel: String = "",
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Primary(actionLabel, onClick = onAction)
        }
    }
}

/** A sentence where content would be, when a full empty state would be too much. */
@Composable
fun Blank(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = Gutter, vertical = 14.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Inline, in the error colour. Dialogs are for destructive confirmation only. */
@Composable
fun Failure(text: String) {
    Text(
        text,
        Modifier.padding(horizontal = Gutter, vertical = 14.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
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
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().height(if (tall) 52.dp else 46.dp),
    ) {
        if (busy) {
            androidx.compose.material3.CircularProgressIndicator(
                Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(10.dp))
        } else if (mark != null) {
            Icon(mark, null, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Something the reader needs to know, drawn as an edge rather than a fill.
 *
 * Anything larger than a chip gets this instead of a coloured ground. A hairline in the tone, the
 * tone on the glyph, and the words in ordinary text: it says the same thing and leaves the surface
 * alone. See [StatusColors] for why a filled one did not.
 *
 * It also matches how the rest of the app is built. Every card here is bordered rather than
 * raised, so a notice that is bordered rather than filled is the same idea one size down.
 */
@Composable
fun Notice(
    tone: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            // Forty per cent, not full strength. At full strength a hairline reads as a box that
            // has been drawn around something, which is louder than the sentence inside it.
            .border(1.dp, tone.copy(alpha = 0.42f), shape)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, Modifier.padding(top = 1.dp).size(16.dp), tint = tone)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), content = content)
    }
}
