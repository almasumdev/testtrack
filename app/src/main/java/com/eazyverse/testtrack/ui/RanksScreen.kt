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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
            if (podium) {
                item(key = PODIUM_KEY) {
                    Column {
                        Podium(rows.take(PODIUM).map { it.onThePodium(mine?.id) })
                        // A floor for them to stand on, edge to edge. Without it the blocks stop
                        // in mid air and the first row of the list reads as the ground.
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                        )
                    }
                }
            }
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

/** The wire row, turned into the six values the podium draws. */
private fun BoardRow.onThePodium(mineId: String?) =
    PodiumPlace(
        name = if (id == mineId) "You" else name,
        photo = photoUrl,
        place = place,
        points = points,
        work = work(this),
    )

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
