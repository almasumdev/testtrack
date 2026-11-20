package com.eazyverse.testtrack.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.ui.theme.LocalStatusColors
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * The page gutter. Every screen, every row, no exceptions.
 *
 * Sixteen, which is what the console settled on. The two apps are read one after the other by the
 * same person on the same phone, and a four point step in the margin between them is the kind of
 * difference nobody names but everybody feels.
 */
val Gutter = 16.dp

/** State colours, from the theme. Never used for a control: that is what primary is for. */
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
 * [isRefreshing] doubles as the signal for a refresh this screen started on its own, opening it
 * or coming back to it, not only one the reader pulled for themselves; the indicator is the one
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

/**
 * The other actions on a screen, beside its one [Primary].
 *
 * A soft filled pill, never an outline, because an outlined button beside a
 * filled one reads as the same action switched off. The same 52 as every button, and two to a
 * row still fits. A destructive one takes the red ground with red words rather than a red fill,
 * which stays for the dialog that actually confirms it. [busy] shows a spinner before the label
 * and holds the button until it is done.
 */
@Composable
fun Secondary(
    label: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    val dismissKeyboard = rememberDismissKeyboard()
    Button(
        onClick = {
            dismissKeyboard()
            onClick()
        },
        enabled = enabled && !busy,
        shape = RoundedCornerShape(percent = 50),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = if (destructive) t.badBg else t.glass2,
                contentColor = if (destructive) t.bad else t.text,
                disabledContainerColor = t.glass2.copy(alpha = 0.45f),
                disabledContentColor = t.text.copy(alpha = 0.45f),
            ),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        modifier = modifier.heightIn(min = ButtonHeight),
    ) {
        if (busy) {
            androidx.compose.material3.CircularProgressIndicator(
                Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.5.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
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
 * The one filled action a screen gets.
 *
 * [mark] is for the single case where a button is asking somebody else's service rather than doing
 * something itself, and the mark is how anyone recognises which one. Drawn as an Icon and left to
 * take the default tint, which is the same colour the label is painted in and follows it into the
 * disabled state as well. It gives way to the spinner while the button is busy: two things sitting
 * in the same place is one too many.
 *
 * At least 52 high and free to grow: at a large font size the label takes a second line rather
 * than being cut off by a fixed height.
 */
@Composable
fun Primary(
    label: String,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    enabled: Boolean = true,
    mark: Painter? = null,
    /** Red, for the button that confirms something that costs the reader. */
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val dismissKeyboard = rememberDismissKeyboard()
    Button(
        onClick = {
            dismissKeyboard()
            onClick()
        },
        enabled = enabled && !busy,
        colors =
            if (destructive) {
                ButtonDefaults.buttonColors(containerColor = Tt.colors.bad, contentColor = Tt.colors.onBad)
            } else {
                ButtonDefaults.buttonColors()
            },
        // A full pill, the main button: the one filled shape on a screen, so it is the
        // one place the eye lands.
        shape = RoundedCornerShape(percent = 50),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = ButtonHeight),
    ) {
        if (busy) {
            androidx.compose.material3.CircularProgressIndicator(
                Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = if (destructive) Tt.colors.onBad else MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(10.dp))
        } else if (mark != null) {
            Icon(mark, null, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * Compact help and row actions: shared label, optional icon, and a full touch target. While [busy]
 * a small loader takes the label's place at the same size, and the button takes no taps.
 */
@Composable
internal fun InlineAction(
    label: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    enabled: Boolean = true,
    busy: Boolean = false,
    /** The words' ink: the accent's, or red for an action that costs something. */
    color: Color = Tt.colors.accentInk,
    onClick: () -> Unit,
) {
    val dismissKeyboard = rememberDismissKeyboard()
    // Match the 32dp header mark without reserving blank space. Compose still expands touch bounds.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        TextButton(
            onClick = {
                dismissKeyboard()
                onClick()
            },
            modifier = modifier.heightIn(min = 32.dp),
            enabled = enabled && !busy,
            shape = RoundedCornerShape(percent = 50),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = Tt.colors.text4),
        ) {
            if (leading != null) {
                Icon(leading, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Box(contentAlignment = Alignment.Center) {
                Text(
                    label,
                    Modifier.alpha(if (busy) 0f else 1f),
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = color, strokeWidth = 2.dp)
            }
        }
    }
}

/** Every button's height, filled, soft or outlined: the garden look's one pill. */
val ButtonHeight = 52.dp

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
    /** False on a page that keeps no boxes: the glyph and the words on the page, no hairline. */
    boxed: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (boxed) {
                    Modifier
                        .clip(shape)
                        // Forty per cent, not full strength. At full strength a hairline reads as a box that
                        // has been drawn around something, which is louder than the sentence inside it.
                        .border(1.dp, tone.copy(alpha = 0.42f), shape)
                        .padding(horizontal = 13.dp, vertical = 12.dp)
                } else {
                    Modifier
                },
            ),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, null, Modifier.padding(top = 1.dp).size(16.dp), tint = tone)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), content = content)
    }
}
