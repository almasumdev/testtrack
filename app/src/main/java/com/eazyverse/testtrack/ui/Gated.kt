package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserRound

/**
 * A page a stranger can reach and cannot read.
 *
 * Six of these exist, and they are deliberately not [EmptyState]. Empty and unknown are different
 * facts: a member with no crews genuinely has none, and the app should say so; a reader with no
 * account has crews or has not, and the app cannot tell. Every one of these screens used to claim
 * the first when it meant the second, so Lobby told a stranger "No crew waiting to start" as
 * though it had checked.
 *
 * Three things hold the set together. Each page builds its own mark out of solid shapes rather
 * than borrowing one line icon in a circle, so what is missing is legible before a word is read.
 * The whole thing is centred in the space between the header and the bar rather than stacked at
 * the top, because on a tall phone the top aligned version left two thirds of the screen dead.
 * And the way out is one small pill that opens the same sheet as the header chip: no Google mark
 * and no second pitch, because the sheet is about to make that case properly and a page that
 * makes it first is the same sentence twice.
 *
 * [aside] is for the two screens with a public sibling. Lobby and Waiting are both one tab away
 * from something that reads perfectly well without an account, and saying so is the difference
 * between a locked page and a locked section.
 */
@Composable
fun Gated(
    heading: String,
    body: String,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    aside: String = "",
    mark: @Composable () -> Unit,
) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        mark()
        Spacer(Modifier.height(26.dp))
        Text(
            heading,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 258.dp),
        )
        Spacer(Modifier.height(24.dp))

        // As wide as its two words and no wider. Full width it read as submitting a form, on a
        // screen where nothing has been filled in and nothing is being sent.
        Button(
            onClick = onSignIn,
            shape = RoundedCornerShape(percent = 50),
            contentPadding = PaddingValues(horizontal = 28.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            modifier = Modifier.height(44.dp),
        ) {
            Text(
                "Get started",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        if (aside.isNotBlank()) {
            Spacer(Modifier.height(18.dp))
            Text(
                aside,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 250.dp),
            )
        }
    }
}

/**
 * The three tones every mark is built from.
 *
 * Two dark steps and exactly one lit piece, so six marks read as one family and none of them
 * reads as two things. They are taken off the scheme rather than written down as hex, which is
 * the whole reason these work on the light theme as well.
 */
private object Ink {
    val deep: Color
        @Composable get() = MaterialTheme.colorScheme.outlineVariant

    val mid: Color
        @Composable get() = MaterialTheme.colorScheme.primary.copy(alpha = 0.34f)

    val lit: Color
        @Composable get() = MaterialTheme.colorScheme.primary
}

@Composable
private fun Tile(
    tone: Color,
    side: Int,
    radius: Int,
) {
    Box(
        Modifier
            .size(side.dp)
            .clip(RoundedCornerShape(radius.dp))
            .background(tone),
    )
}

/**
 * My apps: the tab's own icon, at the size of a mark.
 *
 * Four tiles is already what the bar draws for this tab, so nothing new is being invented. One
 * lit and one half lit says the page fills up, which a grey outline in a circle does not.
 */
@Composable
fun AppsMark() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(Ink.lit, side = 44, radius = 14)
            Tile(Ink.deep, side = 44, radius = 14)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(Ink.deep, side = 44, radius = 14)
            Tile(Ink.mid, side = 44, radius = 14)
        }
    }
}

/**
 * Profile: the header's ring, drawn large with the smallest true arc on it.
 *
 * Not an empty ring. Level one is where everybody genuinely starts, so this is the only mark in
 * the set showing a real value rather than an absence, and the only page where signing in visibly
 * starts something instead of unlocking something.
 */
@Composable
fun LevelMark() {
    val track = Ink.deep
    val lit = Ink.lit
    Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 9.dp.toPx()
            val inset = width / 2f
            val box = Size(size.width - width, size.height - width)
            val at = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, at, box, style = Stroke(width))
            // A shade over a fifteenth of the way round, which is one night of fourteen plus the
            // sign up. Enough to read as started; not enough to read as progress somebody made.
            drawArc(lit, -90f, 82f, false, at, box, style = Stroke(width, cap = StrokeCap.Round))
        }
        Box(
            Modifier
                .size(61.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.UserRound, null, Modifier.size(25.dp), tint = lit)
        }
    }
}

/**
 * Crew, Lobby: the fifteen seats, with the one that would be yours lit.
 *
 * The seat grid is the whole page once a crew is real, so the empty version of the page is the
 * grid and nothing else. Lit in the middle of the second row rather than first, because a seat in
 * a crew is a place among fourteen others and not the head of a queue.
 */
@Composable
fun SeatsMark() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(5) { column ->
                    val seat = row * 5 + column
                    Tile(
                        tone =
                            when (seat) {
                                6 -> Ink.lit
                                14 -> Ink.mid
                                else -> Ink.deep
                            },
                        side = 18,
                        radius = 6,
                    )
                }
            }
        }
    }
}

/**
 * Crew, Running: the night track, at night one.
 *
 * Upright bars rather than a grid, so it reads as time going by and not as more seats. Seven
 * rather than fourteen: this is a mark and not a readout, and fourteen bars at this width is a
 * chart nobody asked for.
 */
@Composable
fun NightsMark() {
    // Air between them roughly the width of a bar. Tighter than this and seven separate nights
    // read as one hatched block.
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        repeat(7) { night ->
            Box(
                Modifier
                    .width(8.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        when (night) {
                            0 -> Ink.lit
                            6 -> Ink.mid
                            else -> Ink.deep
                        },
                    ),
            )
        }
    }
}

/**
 * Crew, Record: finished crews, filed.
 *
 * Three cards front to back, and the only mark in the set that grows toward the reader, because
 * this is the one page that never empties again once it has anything on it.
 */
@Composable
fun RecordMark() {
    // Each card shows about two thirds of itself. At the first set of proportions the front one
    // covered all but a sliver of the two behind it and the three read as a single slab.
    Box(Modifier.size(width = 108.dp, height = 76.dp)) {
        Card(tone = Ink.deep, width = 68, height = 32, down = 0)
        Card(tone = Ink.mid, width = 88, height = 32, down = 22)
        Card(tone = Ink.lit, width = 108, height = 32, down = 44)
    }
}

@Composable
private fun BoxScope.Card(
    tone: Color,
    width: Int,
    height: Int,
    down: Int,
) {
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .offset(y = down.dp)
            .size(width = width.dp, height = height.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tone),
    )
}

/**
 * Circle, Waiting: a written review.
 *
 * Everything that lands on this tab is somebody's writing about somebody's app, and a note with
 * its first line lit says that faster than the word review does. The proportions are the real
 * card's: a title line, then the body somebody typed.
 */
@Composable
fun NoteMark() {
    Column(
        Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Ink.deep)
            .padding(start = 16.dp, end = 16.dp, top = 17.dp, bottom = 19.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Written(tone = Ink.lit, thickness = 9, share = 0.68f)
        Written(tone = Ink.mid, thickness = 8, share = 1f)
        Written(tone = Ink.mid, thickness = 8, share = 1f)
        Written(tone = Ink.mid, thickness = 8, share = 0.46f)
    }
}

@Composable
private fun Written(
    tone: Color,
    thickness: Int,
    share: Float,
) {
    Box(
        Modifier
            .fillMaxWidth(share)
            .height(thickness.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(tone),
    )
}
