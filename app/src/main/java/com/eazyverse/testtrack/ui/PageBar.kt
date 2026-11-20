package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt

/** Material's own padding at each end of its top bar, before the 48dp buttons. */
private val BarPadding = 4.dp

/**
 * The bar's own height, Material's small top bar. A subtitle never adds to it: with one, the title
 * drops from 22/28 to 20/24 and the subtitle takes 12.5/16 under it, 40dp of type in the same 64dp.
 */
private val BarHeight = TopAppBarDefaults.TopAppBarExpandedHeight

/** The title over a subtitle, and the subtitle: single lines, tight, so both sit inside [BarHeight]. */
private val TitleOver = tileStyle(20f, 24f, FontWeight.Bold)
private val SubtitleQuiet = tileStyle(12.5f, 16f, FontWeight.Medium)
private val SubtitleLoud = tileStyle(12.5f, 16f, FontWeight.SemiBold)

/** How a bar's subtitle speaks: a plain fact, running short, settled, or gone wrong. */
enum class BarTone { NORMAL, WARN, OK, BAD }

/** The touch target every bar button gets. */
private val BarButton = 48.dp

/**
 * How far the back arrow's glyph sits from the screen's left edge: the bar's 4dp, the 12dp of the
 * 48dp button around its 24dp icon, and the 6dp the arrow leaves empty inside its own box. The
 * last thing on the right ends exactly this far from the right edge, so the two ends mirror.
 */
internal val BarEdge = 22.dp

/**
 * The bar across the top of every page you go back from.
 *
 * Material's own top app bar, set up the way the review page's always was: transparent, no window
 * insets of its own (every page is already inset where it is routed, and the stock bar adding the
 * status bar again is what sat some arrows lower than others), a bold title, and anything extra
 * in the actions slot. Whatever ends the actions sits [BarEdge] from the right edge, the same as
 * the back arrow's glyph from the left: a pill by its own edge, an icon through [PageBarIcon],
 * which also moves out the empty room inside its button and glyph. The back mark is always
 * Material's arrow_back_ios_new, or Lucide's X in the same slot when [close] is set (a selection
 * to let go of).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** A small mark after the title, such as a muted chat's bell. */
    titleEnd: (@Composable () -> Unit)? = null,
    /** An X in place of the back arrow: the bar closes something on the page rather than leaving it. */
    close: Boolean = false,
    /** One quiet line under the title, such as how long is left; the bar stays the same height. */
    subtitle: String? = null,
    subtitleTone: BarTone = BarTone.NORMAL,
    /** A figure at the bar's end, before [actions], such as "1 of 3" in a run of pages. */
    count: String? = null,
    /** The title and subtitle as skeleton bars, while the page loads. */
    loading: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        windowInsets = WindowInsets(0, 0, 0, 0),
        expandedHeight = BarHeight,
        title = {
            when {
                loading -> BarSkeleton()
                subtitle != null ->
                    Column {
                        BarTitle(title, TitleOver, titleEnd)
                        BarSubtitle(subtitle, subtitleTone)
                    }
                // Alone, the title keeps the bar's own titleLarge (22/28), as every page had it.
                else -> BarTitle(title, null, titleEnd)
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                if (close) {
                    Icon(Lucide.X, "Close", Modifier.size(24.dp))
                } else {
                    Icon(painterResource(R.drawable.ic_arrow_back_ios_new), "Back")
                }
            }
        },
        actions = {
            Row(Modifier.padding(end = BarEdge - BarPadding), verticalAlignment = Alignment.CenterVertically) {
                if (count != null) PageBarCount(count)
                actions()
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
    )
}

@Composable
private fun BarTitle(
    title: String,
    style: TextStyle?,
    end: (@Composable () -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            Modifier.weight(1f, fill = false),
            style = style ?: LocalTextStyle.current,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        end?.invoke()
    }
}

@Composable
private fun BarSubtitle(
    text: String,
    tone: BarTone,
) {
    val t = Tt.colors
    Text(
        text,
        style = if (tone == BarTone.NORMAL) SubtitleQuiet else SubtitleLoud,
        color =
            when (tone) {
                BarTone.NORMAL -> t.text3
                BarTone.WARN -> t.warn
                BarTone.OK -> t.ok
                BarTone.BAD -> t.bad
            },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The title and subtitle while the page loads, in their own sizes. */
@Composable
private fun BarSkeleton() {
    Column {
        Skeleton(width = 150.dp, height = 16.dp)
        Spacer(Modifier.height(6.dp))
        Skeleton(width = 110.dp, height = 11.dp)
    }
}

/** A figure at the bar's end, such as "1 of 3": it ends [BarEdge] from the edge, as the back arrow starts. */
@Composable
fun PageBarCount(text: String) {
    Text(
        text,
        style = tileStyle(14f, 18f, FontWeight.Bold).copy(fontFeatureSettings = "tnum"),
        color = Tt.colors.text2,
        maxLines = 1,
    )
}

/**
 * An icon in the bar's actions. Its 48dp button is moved toward the edge by the room the button
 * and the glyph leave empty on that side, so the glyph itself ends [BarEdge] from the edge, as the
 * back arrow's starts at the other. [content] replaces the icon, such as a spinner while it works.
 */
@Composable
fun PageBarIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Tt.colors.text2,
    size: Dp = 22.dp,
    enabled: Boolean = true,
    content: @Composable () -> Unit = { Icon(icon, label, Modifier.size(size), tint = tint) },
) {
    IconButton(onClick, Modifier.offset(x = barEndShift(icon, size)).then(modifier), enabled) { content() }
}

/**
 * How far to move a 48dp bar button holding [icon] at [size] so the glyph, not the button, ends
 * on [BarEdge]. For an action that wraps its button with more (a menu anchored to it), put this
 * on the wrapper instead of using [PageBarIcon].
 */
@Composable
internal fun barEndShift(
    icon: ImageVector,
    size: Dp,
): Dp =
    remember(icon, size) {
        // Read off the drawing itself: Lucide's glyphs leave anything from 1 to 10 units of a
        // 24-unit box empty on the right, so one fixed nudge cannot fit them all.
        val drawn = glyphRight(icon.root) ?: icon.viewportWidth
        val empty = (icon.viewportWidth - drawn).coerceAtLeast(0f) / icon.viewportWidth
        (BarButton - size) / 2 + size * empty
    }

/** The rightmost point any path in [group] paints, its stroke included, in viewport units. */
private fun glyphRight(group: VectorGroup): Float? =
    group
        .mapNotNull { node ->
            when (node) {
                is VectorGroup -> glyphRight(node)
                is VectorPath -> {
                    val bounds = PathParser().addPathNodes(node.pathData).toPath().getBounds()
                    if (bounds.width == 0f && bounds.height == 0f) {
                        null
                    } else {
                        bounds.right + if (node.stroke != null) node.strokeLineWidth / 2 else 0f
                    }
                }
            }
        }.maxOrNull()
