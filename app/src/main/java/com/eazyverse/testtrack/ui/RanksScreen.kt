package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trophy
import com.eazyverse.testtrack.net.BoardRow

/**
 * Who is carrying their crews.
 *
 * A board is only worth showing if somebody can find themselves on it, so this account's own row
 * is pinned under the list whatever place it is in. Being fortieth and able to see it is useful;
 * being fortieth and looking at ten strangers is not.
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
        openPoints = vm.openPoints,
        reviewPoints = vm.reviewPoints,
        loading = vm.loading,
        offline = vm.offline,
    )
}

/**
 * The board, drawn.
 *
 * Handed its values rather than a view model, so every state it can be in can be put in front of
 * a test: empty, one row, a reader far down the list.
 */
@Composable
fun RanksBody(
    rows: List<BoardRow>,
    mine: BoardRow?,
    openPoints: Int,
    reviewPoints: Int,
    loading: Boolean = false,
    offline: Boolean = false,
) {
    // No longer the signed-out case. The board reads without an account, and somebody deciding
    // whether this is worth joining is exactly who most needs to see that people are doing the
    // work. Reaching here now means the server genuinely could not be got at.
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

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(top = 4.dp, bottom = Gutter),
        ) {
            item {
                Box(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
                    CrewCard {
                        Text(
                            "How it counts",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "An app opened on a testing night is worth $openPoints. A review a " +
                                "developer accepted is worth $reviewPoints, because it took you " +
                                "to Play and back and it carries your name.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (rows.isEmpty()) {
                item {
                    EmptyState(
                        icon = Lucide.Trophy,
                        title = "Nobody has started yet",
                        subtitle = "The first night anybody tests, this fills up.",
                    )
                }
            } else {
                item { CrewHeading("Top ${rows.size}") }
                items(rows, key = { it.id }) { row ->
                    if (row != rows.first()) RowDivider()
                    BoardRowView(row, mine = row.id == mine?.id)
                }
            }
        }

        // Pinned, because the whole point of a board is knowing where you are on it. Only when
        // the list does not already show it, so nobody sees themselves twice.
        if (mine != null && rows.none { it.id == mine.id }) {
            RowDivider()
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 4.dp),
            ) {
                BoardRowView(mine, mine = true)
            }
        }
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
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Place(row.place)
        Spacer(Modifier.width(10.dp))
        Initial(row.name.take(1).uppercase(), size = 32.dp)
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
                buildString {
                    append(if (row.opens == 1) "1 app opened" else "${row.opens} apps opened")
                    if (row.reviews > 0) {
                        append(if (row.reviews == 1) " · 1 review" else " · ${row.reviews} reviews")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "${row.points}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * The place, as a disc.
 *
 * The top three are filled because a podium is the one thing a leaderboard is expected to say out
 * loud. Everybody else gets the number plainly, which keeps the row readable at forty as well as
 * at one.
 */
@Composable
private fun Place(place: Int) {
    val podium = place in 1..3
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (podium) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$place",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color =
                if (podium) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
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
