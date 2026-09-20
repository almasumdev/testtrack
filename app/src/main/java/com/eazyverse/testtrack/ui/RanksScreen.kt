package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Crown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trophy
import com.eazyverse.testtrack.net.BoardRow
import kotlinx.coroutines.launch

/**
 * Who is carrying their crews.
 *
 * It opened on a card explaining the scoring, above a list. That is a reference page pretending to
 * be a board: nobody arrives here wanting the arithmetic, they arrive wanting to know who is
 * winning and where they are. The weighting belongs next to the level and the tix it explains.
 *
 * What is ranked is work, not apps submitted. Opening somebody else's app on a testing night is
 * the thing a crew cannot do without, and it is the thing nobody gets credit for anywhere else.
 */
@Composable
fun RanksScreen(vm: RanksViewModel = viewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load(context) }

    RanksBody(
        rows = vm.rows,
        mine = vm.mine,
        loading = vm.loading,
        offline = vm.offline,
    )
}

/** The three at the top get a podium; everybody after them gets a row. */
private const val PODIUM = 3
private const val PODIUM_KEY = "podium"
private const val HEADING_KEY = "heading"

/** Room under the last row for the pinned bar, which is over the list and not beside it. */
private val PinRoom = 78.dp

/**
 * The board, drawn.
 *
 * Handed its values rather than a view model, so every state it can be in can be put in front of
 * a test: empty, fewer than a podium, a reader far down the list.
 */
@Composable
fun RanksBody(
    rows: List<BoardRow>,
    mine: BoardRow?,
    loading: Boolean = false,
    offline: Boolean = false,
) {
    // Not the signed-out case. The board reads without an account, and somebody deciding whether
    // this is worth joining is exactly who most needs to see that people are doing the work.
    if (offline) {
        EmptyState(
            icon = Lucide.Trophy,
            title = "The board did not load",
            subtitle = "Check your connection and pull to try again.",
        )
        return
    }
    if (loading && rows.isEmpty()) {
        Blank("Counting.")
        return
    }
    if (rows.isEmpty()) {
        EmptyState(
            icon = Lucide.Trophy,
            title = "Nobody has started yet",
            subtitle = "The first night anybody tests, this fills up.",
        )
        return
    }

    val podium = rows.size >= PODIUM
    val rest = if (podium) rows.drop(PODIUM) else rows
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()

    /*
     * Which item in the list is the reader's own, if any.
     *
     * The top three sit inside one podium item rather than three rows of their own, so being
     * first on the board means the thing to watch for is the podium and not an id. Null means the
     * board does not hold them at all, which is the case the pinned bar was written for.
     */
    val anchor: Any? =
        when {
            mine == null -> null
            rows.none { it.id == mine.id } -> null
            podium && mine.place <= PODIUM -> PODIUM_KEY
            else -> mine.id
        }

    // Pinned only while the real row is off screen, and gone the moment it arrives. Nobody should
    // be looking at themselves in two places on the same list.
    val pinned by remember(anchor, mine) {
        derivedStateOf {
            // Nothing is visible before the first layout pass, which would read as "your row is
            // off screen" and flash the bar up for a frame every time the tab is opened.
            val onScreen = list.layoutInfo.visibleItemsInfo
            mine != null && onScreen.isNotEmpty() && onScreen.none { it.key == anchor }
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(top = 4.dp, bottom = if (mine == null) Gutter else PinRoom),
        ) {
            if (podium) item(key = PODIUM_KEY) { Podium(rows.take(PODIUM), mine?.id) }
            if (rest.isNotEmpty()) {
                item(key = HEADING_KEY) {
                    CrewHeading(if (podium) "${PODIUM + 1} to ${rows.size}" else "Top ${rows.size}")
                }
            }
            itemsIndexed(rest, key = { _, row -> row.id }) { index, row ->
                if (index > 0) RowDivider()
                BoardRowView(row, mine = row.id == mine?.id)
            }
        }

        if (mine != null) {
            AnimatedVisibility(
                visible = pinned,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                PinnedMine(
                    row = mine,
                    // Only offered where there is somewhere to go. Off the board entirely, this
                    // bar is the only place their standing exists.
                    onJump =
                        if (anchor == null) {
                            null
                        } else {
                            { scope.launch { list.animateScrollToItem(seatOf(anchor, mine, rest, podium)) } }
                        },
                )
            }
        }
    }
}

/** Where the reader's own row sits in the lazy list, counting the podium and the heading. */
private fun seatOf(
    anchor: Any?,
    mine: BoardRow,
    rest: List<BoardRow>,
    podium: Boolean,
): Int {
    if (anchor == PODIUM_KEY) return 0
    val ahead = (if (podium) 1 else 0) + (if (rest.isNotEmpty()) 1 else 0)
    return (ahead + rest.indexOfFirst { it.id == mine.id }).coerceAtLeast(0)
}

/**
 * The three at the top, on blocks.
 *
 * Second, first, third, because that is the shape a podium has and left to right is not how
 * anybody looks at one. The winner is raised, larger and crowned, and the blocks are in
 * proportion so the standing is legible from the silhouette before a number is read.
 */
@Composable
private fun Podium(
    top: List<BoardRow>,
    mineId: String?,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Step(top[1], Medal.silver, seat = 50.dp, face = 48.dp, mineId = mineId)
        Spacer(Modifier.width(9.dp))
        Step(top[0], Medal.gold, seat = 70.dp, face = 60.dp, mineId = mineId, crowned = true)
        Spacer(Modifier.width(9.dp))
        Step(top[2], Medal.bronze, seat = 38.dp, face = 48.dp, mineId = mineId)
    }
}

/** One place on the podium: the face, what they did, and the block they stand on. */
@Composable
private fun RowScope.Step(
    row: BoardRow,
    medal: Color,
    seat: Dp,
    face: Dp,
    mineId: String?,
    crowned: Boolean = false,
) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        if (crowned) {
            Icon(Lucide.Crown, null, Modifier.size(17.dp), tint = medal)
            Spacer(Modifier.height(4.dp))
        }
        // The medal is a ring around the face rather than a badge beside it: one object, with the
        // colour doing the ranking instead of sitting next to it.
        Box(
            Modifier
                .size(face)
                .clip(CircleShape)
                .background(medal)
                .padding(2.5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(row.photoUrl, row.name.take(1).uppercase(), size = face - 5.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (row.id == mineId) "You" else row.name,
            Modifier.padding(horizontal = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            "${row.points}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = medal,
        )
        Text(
            work(row),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(9.dp))

        /*
         * The block: the app's own surface, with the medal only on its top edge and its numeral.
         *
         * It was a wash of the medal colour fading downward, which is the exact thing the theme
         * file warns about. Gold and bronze have almost no blue in them, and over four hundred
         * square points of navy the eye has enough of it to judge against the ground underneath,
         * so it read as a stain rather than as a colour. Three navy blocks of different heights
         * with a gold, silver and bronze edge read as a podium and as this app at the same time.
         */
        Box(
            Modifier
                .fillMaxWidth()
                .height(seat)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.fillMaxWidth().height(3.dp).background(medal))
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

/**
 * The reader's own line, held at the bottom of the screen.
 *
 * On its own surface with an edge and a shadow, because the one thing it must never look like is
 * the next row of the list. [onJump] is null when the board does not hold them at all, and then
 * this bar is the only place their standing exists.
 */
@Composable
private fun PinnedMine(
    row: BoardRow,
    onJump: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(14.dp)
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onJump == null) Modifier else Modifier.clickable(onClick = onJump)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        BoardRowView(row, mine = true, jump = onJump != null, washed = false)
    }
}

/**
 * One person's line.
 *
 * The place is the first thing read and the points are the last, with the work that earned them
 * between: a number on its own says nothing about what to do differently tomorrow.
 */
@Composable
private fun BoardRowView(
    row: BoardRow,
    mine: Boolean,
    jump: Boolean = false,
    washed: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (mine && washed) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                },
            ).padding(horizontal = Gutter, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${row.place}",
            Modifier.width(26.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            color =
                if (mine) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Spacer(Modifier.width(12.dp))
        Avatar(row.photoUrl, row.name.take(1).uppercase(), size = 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (mine) "You" else row.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (mine) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                work(row),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        if (jump) {
            Text(
                "Jump",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            "${row.points}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** What earned the points, under the name. */
private fun work(row: BoardRow): String =
    buildString {
        append(if (row.opens == 1) "1 app opened" else "${row.opens} apps opened")
        if (row.reviews > 0) {
            append(if (row.reviews == 1) " · 1 review" else " · ${row.reviews} reviews")
        }
    }

/*
 * There is no title bar here any more.
 *
 * Ranks, My apps and Profile each carried their own name across the top in title size, directly
 * under a header that is on every screen and above a bar whose lit tab says the same word. Three
 * places telling somebody where they are, one of which cost a line of the screen. Circle and Crew
 * never had one, because their tabs were already doing it, so this also makes the five tabs
 * behave the same way as each other.
 */
