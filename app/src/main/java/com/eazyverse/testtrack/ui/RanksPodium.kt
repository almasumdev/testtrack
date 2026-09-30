package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Crown
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular

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
 * anybody looks at one. The winner is raised, larger and crowned, and the blocks are in proportion
 * so the standing reads from the silhouette before a number does. No light or box behind it: it
 * stands on the page like everything else.
 */
@Composable
fun Podium(top: List<PodiumPlace>) {
    Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Step(top[1], Medal.silver, seat = 62.dp, face = 54.dp)
            Spacer(Modifier.width(8.dp))
            Step(top[0], Medal.gold, seat = 88.dp, face = 68.dp, crowned = true)
            Spacer(Modifier.width(8.dp))
            Step(top[2], Medal.bronze, seat = 46.dp, face = 54.dp)
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
        Points(row.points, MaterialTheme.typography.titleLarge, medal)
        Text(
            row.work,
            Modifier.padding(horizontal = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        listOf(medal.copy(alpha = 0.24f), Color.Transparent),
                    ),
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(medal))
            Text(
                "${row.place}",
                Modifier.padding(top = 9.dp),
                style = MaterialTheme.typography.headlineSmall.tabular(),
                fontWeight = FontWeight.Black,
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
 * A score with its unit.
 *
 * A bare number at the end of a row was read as a count of something, and the line beside it
 * already counted apps, so "18" next to "18 apps opened" looked like the same fact twice. The unit
 * says it is the total, and the reviews folded into it are why the two can differ.
 *
 * The unit is smaller and quieter than the figure, and sits on its baseline, so the number is still
 * the thing read first.
 */
@Composable
fun Points(
    value: Int,
    style: TextStyle,
    color: Color,
) {
    Row {
        Text(
            "$value",
            Modifier.alignByBaseline(),
            style = style.tabular(),
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            "pts",
            Modifier.alignByBaseline(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The board, before it has arrived: three faces on three blocks, and rows under them.
 *
 * The same silhouette the podium will have, so nothing jumps when it lands. A sentence in the
 * corner said the board was being counted and nothing about what was coming.
 */
@Composable
fun RanksLoading() {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 34.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            LoadingStep(face = 54.dp, seat = 62.dp)
            Spacer(Modifier.width(8.dp))
            LoadingStep(face = 68.dp, seat = 88.dp)
            Spacer(Modifier.width(8.dp))
            LoadingStep(face = 54.dp, seat = 46.dp)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Spacer(Modifier.height(30.dp))
        repeat(SKELETON_ROWS) { i ->
            if (i > 0) RowDivider()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Skeleton(width = 12.dp, height = 12.dp, corner = 4.dp)
                Spacer(Modifier.width(11.dp))
                Skeleton(width = 36.dp, height = 36.dp, corner = 18.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Skeleton(width = LoadingNames[i % LoadingNames.size].dp, height = 13.dp)
                    Spacer(Modifier.height(6.dp))
                    Skeleton(width = 104.dp, height = 11.dp)
                }
                Skeleton(width = 38.dp, height = 14.dp)
            }
        }
    }
}

private val LoadingNames = listOf(84, 112, 70, 96, 122, 78, 104, 90)

@Composable
private fun RowScope.LoadingStep(
    face: Dp,
    seat: Dp,
) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Skeleton(width = face, height = face, corner = face / 2)
        Spacer(Modifier.height(10.dp))
        Skeleton(width = 58.dp, height = 12.dp)
        Spacer(Modifier.height(8.dp))
        Skeleton(width = 40.dp, height = 20.dp)
        Spacer(Modifier.height(12.dp))
        Skeleton(height = seat, corner = 12.dp)
    }
}

/**
 * Gold, silver and bronze, and the one time this app reaches outside its own palette.
 *
 * A board with no gold on it is a list, and first place stops being worth anything. The values
 * live in the tokens with everything else, darker on the light ground so bronze stays readable.
 */
private object Medal {
    val gold: Color
        @Composable get() = Tt.colors.gold

    val silver: Color
        @Composable get() = Tt.colors.silver

    val bronze: Color
        @Composable get() = Tt.colors.bronze
}
