package com.eazyverse.testtrack.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * The pieces Value Guard is built from, drawn in Compose.
 *
 * Same shapes, same spacing and the same rules about where colour goes, so the two apps read as one
 * family: things you tap are solid tiles, things you only read are dashed panels, the accent is
 * spent on what you act on, and the ground under everything is a quiet contour map rather than a
 * flat fill.
 */

// ---- the ground ----------------------------------------------------------------------------

/** Which layout of hills and light a page stands on. Each tab has its own. */
enum class Scene { CIRCLE, CREW, RANKS, APPS, PROFILE }

private class Glow(
    val x: Float,
    val y: Float,
    val radius: Float,
)

private class Hill(
    val x: Float,
    val y: Float,
    val size: Float,
    val seed: Int = 0,
)

private class SceneSpec(
    val glows: List<Glow>,
    val hills: List<Hill>,
)

// Value Guard's own layouts: rings low and to the sides where the margins are, clear of the text.
private val Scenes =
    mapOf(
        Scene.CIRCLE to SceneSpec(listOf(Glow(1f, .08f, .7f)), listOf(Hill(1.04f, .86f, .46f), Hill(-.05f, .22f, .26f, 2))),
        Scene.CREW to SceneSpec(listOf(Glow(0f, .9f, .65f)), listOf(Hill(1.04f, .1f, .36f, 1), Hill(-.04f, .9f, .34f, 3))),
        Scene.RANKS to SceneSpec(listOf(Glow(.1f, .08f, .75f)), listOf(Hill(-.06f, .24f, .3f, 1), Hill(1.06f, .14f, .28f, 4))),
        Scene.APPS to SceneSpec(listOf(Glow(1f, 0f, .8f)), listOf(Hill(1.05f, .52f, .3f, 4), Hill(-.05f, .95f, .28f, 1))),
        Scene.PROFILE to SceneSpec(listOf(Glow(0f, .1f, .75f)), listOf(Hill(-.05f, .88f, .44f, 5), Hill(1.05f, .3f, .2f, 2))),
    )

private const val RINGS = 7
private const val STEPS = 90

/**
 * The page ground: the solid colour, a pool of light, and a couple of contour hills.
 *
 * Behind everything and under nothing: cards on top of it are opaque, so no line is ever read
 * through text. Changing tab crossfades to that tab's layout, so the ground moves with you instead
 * of snapping.
 */
@Composable
fun Backdrop(
    scene: Scene,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = Tt.colors
    Box(modifier.fillMaxSize().background(t.bg)) {
        Crossfade(scene, animationSpec = tween(560), label = "backdrop") { current ->
            val spec = Scenes.getValue(current)
            Canvas(Modifier.fillMaxSize()) {
                spec.glows.forEach { g ->
                    val center = Offset(g.x * size.width, g.y * size.height)
                    val radius = g.radius * size.width
                    drawCircle(
                        Brush.radialGradient(listOf(t.glow, t.glow.copy(alpha = 0f)), center, radius),
                        radius,
                        center,
                    )
                }
                spec.hills.forEach { hill(it, t.contourDeep, t.contour) }
            }
        }
        content()
    }
}

private fun DrawScope.hill(
    h: Hill,
    line: Color,
    peak: Color,
) {
    val center = Offset(h.x * size.width, h.y * size.height)
    val extent = h.size * size.width
    val stroke = max(1.4.dp.toPx(), extent * .012f)
    repeat(RINGS) { r ->
        val radius = extent * (.14f + r * .13f)
        val path = Path()
        for (k in 0..STEPS) {
            val a = k.toFloat() / STEPS * PI.toFloat() * 2f
            // Two slow waves round the ring, shifted per ring and per hill, so the lines drift like
            // terrain instead of circling like a target.
            val wobble = 1f + .07f * sin(a * 3 + h.seed * 1.7f + r * .5f) + .04f * sin(a * 5 + h.seed)
            val x = center.x + cos(a) * radius * wobble
            val y = center.y + sin(a) * radius * wobble
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        // The outer rings fade, so a hill has no hard edge.
        drawPath(path, line.copy(alpha = 1f - r / (RINGS + 1f)), style = Stroke(stroke))
    }
    drawCircle(peak, extent * .07f, center)
}

// ---- marks -----------------------------------------------------------------------------------

/**
 * An icon on its own tinted tile: the mark every row and header in Value Guard leads with.
 *
 * A rounded square rather than a circle, the glyph at about half the tile. The danger version
 * swaps to the red ground so a destructive row says so before its words are read.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    size: Dp = 36.dp,
    danger: Boolean = false,
) {
    val t = Tt.colors
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.305f))
            .background(if (danger) t.badBg else t.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.52f), tint = if (danger) t.bad else t.tileInk)
    }
}

/** A count in a small filled pill, beside a heading. */
@Composable
fun CountBadge(count: Int) {
    val t = Tt.colors
    Box(
        Modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(CircleShape)
            .background(t.badgeFill)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium.tabular(),
            fontWeight = FontWeight.Bold,
            color = t.onAccent,
        )
    }
}

// ---- surfaces --------------------------------------------------------------------------------

/**
 * Something to read, not to press: a soft gradient, a light in one corner, a dashed edge.
 *
 * The dashed edge is the whole point. Solid tiles in this app are things you tap; a panel says
 * "this is information" before anybody tries it and finds nothing happens.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(PanelRadius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(t.panelTop, t.panelBottom)))
            .drawBehind {
                val center = Offset(size.width - 50.dp.toPx(), 40.dp.toPx())
                val radius = size.width * .5f
                drawCircle(
                    Brush.radialGradient(listOf(t.panelGlow, t.panelGlow.copy(alpha = 0f)), center, radius),
                    radius,
                    center,
                )
                val inset = .75.dp.toPx()
                drawRoundRect(
                    color = t.panelOutline,
                    topLeft = Offset(inset, inset),
                    size =
                        androidx.compose.ui.geometry
                            .Size(size.width - inset * 2, size.height - inset * 2),
                    cornerRadius = CornerRadius(PanelRadius.toPx() - inset),
                    style =
                        Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                        ),
                )
            }.padding(padding),
        content = content,
    )
}

val PanelRadius = 22.dp

/** Where a tile sits in its group, which decides which of its corners are round. */
enum class TilePlace { ONLY, FIRST, MIDDLE, LAST }

private fun TilePlace.shape(): RoundedCornerShape {
    val outer = 22.dp
    val inner = 6.dp
    return when (this) {
        TilePlace.ONLY -> RoundedCornerShape(outer)
        TilePlace.FIRST -> RoundedCornerShape(outer, outer, inner, inner)
        TilePlace.MIDDLE -> RoundedCornerShape(inner)
        TilePlace.LAST -> RoundedCornerShape(inner, inner, outer, outer)
    }
}

/**
 * A run of tiles, three points apart, with no rules between them.
 *
 * Value Guard's settings groups. Each row is its own tile, only the outer corners of the run are
 * round, and the gap is what separates them; a divider inside a group was a line doing a job the
 * gap already does.
 */
@Composable
fun TileGroup(
    count: Int,
    modifier: Modifier = Modifier,
    tile: @Composable (index: Int, place: TilePlace) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(count) { i ->
            val place =
                when {
                    count == 1 -> TilePlace.ONLY
                    i == 0 -> TilePlace.FIRST
                    i == count - 1 -> TilePlace.LAST
                    else -> TilePlace.MIDDLE
                }
            tile(i, place)
        }
    }
}

/**
 * One settings row: an icon tile, a title with an optional line under it, a value, a chevron.
 *
 * Sixty points at the least, so it takes a thumb comfortably. [danger] turns the title and the tile
 * red and drops the chevron; [trailing] replaces the chevron with anything else, a switch or an
 * external-link mark.
 */
@Composable
fun TileRow(
    icon: ImageVector,
    title: String,
    place: TilePlace,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val t = Tt.colors
    val shape = place.shape()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 14.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, danger = danger)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                fontWeight = FontWeight.SemiBold,
                color = if (danger) t.bad else t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                    color = t.text3,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = t.text3)
        }
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        } else if (!danger && onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = t.text4)
        }
    }
}

/**
 * A section's heading: sentence case, one step under a title, with its count in a badge beside it.
 *
 * It was small grey capitals, which is a label, and a label over a list says "form" rather than
 * "here are your things". A count sits in a badge next to the words it counts, as Value Guard's
 * "Needs attention" does; anything that is not a count stays quiet text at the far end.
 */
@Composable
fun CrewHeading(
    text: String,
    trailing: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 19.sp),
            fontWeight = FontWeight.Bold,
            color = Tt.colors.text,
        )
        val count = trailing?.toIntOrNull()
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            CountBadge(count)
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null && count == null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = Tt.colors.text3,
            )
        }
    }
}

/** The corner a card gets. Value Guard's list cards, a step under its 22 panels. */
val CardRadius = 18.dp

/** One figure and what it counts. */
@Composable
fun Tally(
    modifier: Modifier,
    value: String,
    label: String,
) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Tt.colors.accentInk,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
