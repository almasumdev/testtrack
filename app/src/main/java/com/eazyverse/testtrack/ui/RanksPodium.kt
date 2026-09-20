package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Crown
import com.composables.icons.lucide.Lucide

/**
 * One of the three, as the podium needs them.
 *
 * Not the wire row. A screen may not reach into net (see docs/app-rules.md), and the podium wants
 * a name already resolved to "You" where it should be and a line of work already written, neither
 * of which the server sends. Six values and no fetching.
 */
data class PodiumPlace(
    val name: String,
    val photo: String,
    val place: Int,
    val points: Int,
    val work: String,
)

/**
 * The three at the top, on blocks.
 *
 * Second, first, third, because that is the shape a podium has and left to right is not how
 * anybody looks at one. The winner is raised, larger and crowned, standing in a wash of their own
 * colour, and the blocks are in proportion so the standing reads from the silhouette before a
 * number does.
 */
@Composable
fun Podium(top: List<PodiumPlace>) {
    val lit = Medal.gold
    Box(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 8.dp)) {
        // The winner stands in a little light. Radial and very faint, thrown from behind the
        // first face: it is what stops three columns of the same weight reading as a table.
        Canvas(Modifier.matchParentSize()) {
            val heart = Offset(size.width / 2f, size.height * 0.26f)
            val reach = size.width * 0.46f
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(lit.copy(alpha = 0.17f), Color.Transparent),
                        center = heart,
                        radius = reach,
                    ),
                radius = reach,
                center = heart,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Step(top[1], Medal.silver, seat = 56.dp, face = 52.dp)
            Spacer(Modifier.width(8.dp))
            Step(top[0], Medal.gold, seat = 78.dp, face = 66.dp, crowned = true)
            Spacer(Modifier.width(8.dp))
            Step(top[2], Medal.bronze, seat = 42.dp, face = 52.dp)
        }
    }
}

/** One place on the podium: the face, what they did, and the block they stand on. */
@Composable
private fun RowScope.Step(
    row: PodiumPlace,
    medal: Color,
    seat: Dp,
    face: Dp,
    crowned: Boolean = false,
) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        if (crowned) {
            Icon(Lucide.Crown, null, Modifier.size(18.dp), tint = medal)
            Spacer(Modifier.height(5.dp))
        }
        Face(row, medal, face)
        Spacer(Modifier.height(9.dp))
        Text(
            row.name,
            Modifier.padding(horizontal = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            "${row.points}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = medal,
        )
        Text(
            row.work,
            Modifier.padding(horizontal = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))

        /*
         * The block: the app's own surface first, then the medal laid over it as a wash that
         * fades downward, and the colour at full strength only along the top edge.
         *
         * The wash on its own, straight onto the background, was the thing the theme file warns
         * about. Gold and bronze carry almost no blue, and over a whole block of navy the eye has
         * enough to judge against the ground underneath, so it read as a stain. Put the surface
         * under it and the navy is still there to see, with the colour sitting on top of it.
         */
        Box(
            Modifier
                .fillMaxWidth()
                .height(seat)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .background(
                    Brush.verticalGradient(
                        listOf(medal.copy(alpha = 0.20f), Color.Transparent),
                    ),
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.fillMaxWidth().height(2.5.dp).background(medal))
            Text(
                "${row.place}",
                Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = medal,
            )
        }
    }
}

/**
 * A podium face: the medal as a ring around it rather than a badge beside it.
 *
 * One object, with the colour doing the ranking instead of sitting next to it. Where somebody has
 * no picture the letter is drawn in the medal too, so the whole mark is one colour and reads at a
 * glance from across the podium.
 */
@Composable
private fun Face(
    row: PodiumPlace,
    medal: Color,
    size: Dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.5.dp, medal, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (row.photo.isBlank()) {
            Text(
                row.name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = medal,
            )
        } else {
            Avatar(row.photo, row.name.take(1).uppercase(), size = size - 7.dp)
        }
    }
}

/**
 * Gold, silver and bronze, and the one time this app reaches outside its own scheme.
 *
 * A board with no gold on it is a list, and first place stops being worth anything. Darkened on
 * the light theme rather than reused: the dark values are mixed to sit on navy, and a bronze
 * nobody can read is worse than no bronze.
 */
private object Medal {
    private val pale: Boolean
        @Composable get() = MaterialTheme.colorScheme.background.luminance() > 0.5f

    val gold: Color
        @Composable get() = if (pale) Color(0xFF9A7B1F) else Color(0xFFE8B84B)

    val silver: Color
        @Composable get() = if (pale) Color(0xFF6B7488) else Color(0xFFAFB9D0)

    val bronze: Color
        @Composable get() = if (pale) Color(0xFF8A5A34) else Color(0xFFC58455)
}
