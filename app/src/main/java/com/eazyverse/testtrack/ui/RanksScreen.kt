package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.net.BoardRow
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular
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

    // Counted afresh on every return, not once when the tab was first opened. A board read at
    // breakfast and still on screen at midnight was this app answering "how am I doing", and it
    // took closing the whole thing to get a newer answer.
    // And while it shows, whenever points move. See RanksViewModel.
    WhileShown(
        onShow = {
            vm.load(context)
            vm.startLive(context)
        },
        onHide = vm::stopLive,
    )

    RefreshBox(
        // Bound to the pull alone. A resume refreshes too, and playing the gesture's arc for one
        // nobody made is the indicator lying about who asked.
        isRefreshing = vm.pulling,
        onRefresh = { vm.load(context, fromPull = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LoadSwap(DebugLooks.holdLoading || (vm.loading && vm.rows.isEmpty() && !vm.offline)) { waiting ->
            if (waiting) {
                RanksLoading()
            } else {
                RanksBody(rows = vm.rows, mine = vm.mine, offline = vm.offline)
            }
        }
    }
}

/** The three at the top get a podium; everybody after them gets a row. */
private const val PODIUM = 3
private const val HEADING_KEY = "heading"

/** Room under the last row for the pinned bar, which is over the list and not beside it. */
private val PinRoom = 92.dp

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
    if (offline && rows.isEmpty()) {
        // The robot, like every other screen that has nothing to show, rather than a lone tile.
        RobotState(
            Prop.Plug,
            "The board did not load",
            "Check your connection and pull to try again.",
            failed = true,
        )
        return
    }
    if (DebugLooks.holdLoading || (loading && rows.isEmpty())) {
        RanksLoading()
        return
    }
    if (rows.isEmpty()) {
        RobotState(
            Prop.Trophy,
            "Nobody has started yet",
            "Open the apps in your crew, or get a review accepted in Circle, and you'll be the " +
                "first one here.",
        )
        return
    }

    val podium = rows.size >= PODIUM
    val rest = if (podium) rows.drop(PODIUM) else rows

    // Sized to the deepest place the screen will actually print, counting the reader's own even
    // when it is off the board. A column wide enough for three digits, holding a 4, is most of a
    // centimetre of nothing between the edge of the screen and the first thing on the row.
    val room = placeRoom(maxOf(rows.lastOrNull()?.place ?: 0, mine?.place ?: 0))
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val listed = mine != null && rows.any { it.id == mine.id }

    /** The podium does not scroll, so anybody on it is on screen for as long as the tab is. */
    val onPodium = listed && podium && mine != null && mine.place <= PODIUM

    /** The row in the scrolling part that is the reader's own, where they are in it at all. */
    val anchor: Any? = if (listed && !onPodium) mine?.id else null

    // Pinned only while the real row is off screen, and gone the moment it arrives. Nobody should
    // be looking at themselves in two places on the same list.
    val pinned by remember(anchor, onPodium, mine) {
        derivedStateOf {
            when {
                mine == null -> false
                onPodium -> false
                // Not on the board at all, so this bar is the only place their standing exists.
                anchor == null -> true
                else -> {
                    // Nothing is visible before the first layout pass, which would read as "your
                    // row is off screen" and flash the bar up for a frame on every open.
                    val onScreen = list.layoutInfo.visibleItemsInfo
                    onScreen.isNotEmpty() && onScreen.none { it.key == anchor }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        /*
         * Above the scroll, not in it.
         *
         * The three at the top are the one thing on this page that stays true whatever else
         * somebody is looking at, and scrolling them away to reach row twelve takes with them the
         * only reference the rest of the numbers have. The podium holds its place and the list
         * runs underneath it.
         */
        if (podium) {
            // Straight on the page, with no box or light of its own: the podium is the top of the
            // board, not a separate block sitting above it.
            Box(Modifier.padding(start = Gutter, end = Gutter, top = 4.dp, bottom = 8.dp)) {
                Podium(rows.take(PODIUM).map { it.onThePodium(mine?.id) })
            }
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(top = 4.dp, bottom = (if (mine == null) Gutter else PinRoom) + dockRoom()),
            ) {
                if (rest.isNotEmpty()) {
                    // Named for what it is rather than as a range of places. "4 to 40" was
                    // arithmetic about the list; the count of how many are on it is the part worth
                    // saying, and the places are already down the left of every row.
                    item(key = HEADING_KEY) {
                        CrewHeading("Standings", trailing = "Top ${rows.size}")
                    }
                }
                // Each person a tile of their own, three points apart, as Value Guard lists things:
                // the gap separates them, and only the ends of the run are rounded.
                itemsIndexed(rest, key = { _, row -> row.id }) { index, row ->
                    val place =
                        when {
                            rest.size == 1 -> TilePlace.ONLY
                            index == 0 -> TilePlace.FIRST
                            index == rest.lastIndex -> TilePlace.LAST
                            else -> TilePlace.MIDDLE
                        }
                    Box(Modifier.padding(start = Gutter, end = Gutter, top = if (index > 0) 3.dp else 0.dp)) {
                        BoardRowView(row, mine = row.id == mine?.id, room = room, shape = place.shape())
                    }
                }
            }

            if (mine != null) {
                PinnedBar(
                    row = mine,
                    showing = pinned,
                    room = room,
                    // Only offered where there is somewhere to go. Off the board entirely, this
                    // bar is the only place their standing exists.
                    onJump =
                        if (anchor == null) {
                            null
                        } else {
                            { scope.launch { list.animateScrollToItem(seatOf(mine, rest)) } }
                        },
                )
            }
        }
    }
}

/**
 * The bar, arriving and leaving.
 *
 * Its own composable rather than three more lines in the body, and not only for length: inside
 * the body both a Column and a Box are in scope, and AnimatedVisibility has an overload for each,
 * so the call cannot say which one it means. Here there is only the Box.
 */
@Composable
private fun BoxScope.PinnedBar(
    row: BoardRow,
    showing: Boolean,
    room: Dp,
    onJump: (() -> Unit)?,
) {
    AnimatedVisibility(
        visible = showing,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        PinnedMine(row = row, room = room, onJump = onJump)
    }
}

/** Where the reader's own row sits in the scrolling part, counting the heading above it. */
private fun seatOf(
    mine: BoardRow,
    rest: List<BoardRow>,
): Int {
    val heading = if (rest.isNotEmpty()) 1 else 0
    return (heading + rest.indexOfFirst { it.id == mine.id }).coerceAtLeast(0)
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
    room: Dp,
    onJump: (() -> Unit)?,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(CardRadius)
    Box(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, bottom = 10.dp + dockRoom())) {
        Box(
            Modifier
                .shadow(16.dp, shape, ambientColor = t.shadow.copy(alpha = 0.3f), spotColor = t.shadow.copy(alpha = 0.3f))
                .then(if (onJump == null) Modifier else Modifier.clickable(onClick = onJump)),
        ) {
            BoardRowView(row, mine = true, room = room, jump = onJump != null, shape = shape)
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
    room: Dp,
    jump: Boolean = false,
    shape: Shape = RoundedCornerShape(CardRadius),
) {
    /*
     * The face is exactly as tall as the two lines next to it.
     *
     * It was 34dp, which is a number somebody typed, and at every font scale but one it either
     * overhung the name and the work under it or fell short of them. Measured off the two styles
     * that draw those lines, it is the same height as what it sits beside on any phone, however
     * large its owner has set the type.
     */
    val face =
        with(LocalDensity.current) {
            MaterialTheme.typography.bodyMedium.lineHeight
                .toDp() +
                MaterialTheme.typography.bodySmall.lineHeight
                    .toDp() +
                1.dp
        }

    val t = Tt.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            // Yours in the accent's own container tone, everybody else on the card surface.
            .background(if (mine) t.container else t.glass)
            .border(1.dp, if (mine) t.accent.copy(alpha = 0.35f) else t.glassBorder, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // Zero is somebody who has not earned a point yet: not on the board, not first of one.
            if (row.place > 0) "${row.place}" else "–",
            Modifier.width(room),
            style = MaterialTheme.typography.labelLarge.tabular(),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            color =
                if (mine) {
                    Tt.colors.accentInk
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Spacer(Modifier.width(11.dp))
        Avatar(row.photoUrl, row.name.take(1).uppercase(), size = face)
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
                if (row.place > 0) work(row) else "Not on the board yet",
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
                color = Tt.colors.accentInk,
            )
            Spacer(Modifier.width(12.dp))
        }
        Points(row.points, MaterialTheme.typography.titleSmall, Tt.colors.accentInk)
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

/**
 * How much of the row the place gets, given the deepest one on it.
 *
 * Right aligned so the digits stack into a column, which means the width has to be the width of
 * the longest number and not a guess at one. Roughly a digit each, with a little air.
 */
private fun placeRoom(widest: Int): Dp =
    when {
        widest >= 100 -> 27.dp
        widest >= 10 -> 19.dp
        else -> 11.dp
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
