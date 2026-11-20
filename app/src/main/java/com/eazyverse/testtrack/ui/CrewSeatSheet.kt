package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AppsViewModel

/**
 * Which app goes in: the picker for Take a seat, opened after the testers group is checked. It only
 * lists apps. Tapping one that can go in closes the sheet and hands its id to [onPick], which opens
 * the seat screen ([CrewSeatScreen]) where its group check, details and the button are.
 *
 * The crew itself is not chosen; the server puts the app into whichever is nearest to starting.
 */
@Composable
internal fun CrewSeatSheet(
    crews: CrewViewModel,
    apps: AppsViewModel,
    onPick: (String) -> Unit,
    onAddApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val open = crews.open?.name
    val loading = apps.loading || apps.reading

    LaunchedEffect(Unit) { apps.attach(context) }

    PickerSheet(
        title = "Which app goes in?",
        subtitle =
            when {
                loading && apps.apps.isEmpty() -> "Getting your apps."
                apps.offline && apps.apps.isEmpty() -> "Couldn't load your apps."
                apps.apps.isEmpty() -> "You haven't added an app yet."
                else -> open?.let { "Take a seat in $it" } ?: "Start a new crew"
            },
        onDismiss = onDismiss,
        tall = false,
        skipHalf = true,
        fitMax = 0.9f,
    ) { close ->
        CrewSeatList(
            crews = crews,
            apps = apps,
            waiting = loading && apps.apps.isEmpty(),
            onPick = { picked -> close { onPick(picked.id) } },
            onAdd = { close(onAddApp) },
            onRetry = { apps.load(context) },
        )
    }
}

/** One app in the list, with what decides its row: why it can't go in, or what it needs next. */
private class SeatRow(
    val app: AppEntry,
    val block: SeatBlock?,
    val need: SeatNeed,
) {
    /** Ready ones first, then those that still need the group, then those that can't go in. */
    val rank: Int get() =
        if (block != null) {
            2
        } else if (need == SeatNeed.NONE) {
            0
        } else {
            1
        }
}

/** Your apps, ready first, then those that can't go in, and a way to add another. */
@Composable
private fun ColumnScope.CrewSeatList(
    crews: CrewViewModel,
    apps: AppsViewModel,
    waiting: Boolean,
    onPick: (AppEntry) -> Unit,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
) {
    val t = Tt.colors
    val scroll = rememberScrollState()
    val failed = !waiting && apps.apps.isEmpty() && apps.offline
    Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) {
        when {
            waiting ->
                // Three tiles, not SKELETON_ROWS: this sheet sizes itself to its content, and ten
                // would open it tall and then shrink it once the apps arrive.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeatTileSkeleton(120.dp, 160.dp)
                    SeatTileSkeleton(96.dp, 182.dp)
                    SeatTileSkeleton(134.dp, 146.dp)
                }
            failed ->
                Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.Top) {
                    Icon(Lucide.TriangleAlert, null, Modifier.padding(top = 1.dp).size(16.dp), tint = t.warn)
                    Spacer(Modifier.width(8.dp))
                    Text("Check your connection, then try again.", style = TextStyles.PageLine, color = t.text2)
                }
            apps.apps.isEmpty() ->
                Text(
                    "Add the app you want tested. It needs a closed test on Play.",
                    Modifier.padding(horizontal = 2.dp),
                    style = TextStyles.PageLine,
                    color = t.text2,
                )
            else -> {
                val rows =
                    apps.apps
                        .map {
                            SeatRow(
                                it,
                                seatBlock(it, apps.facts[it.id], appealCrewFor(crews.crews, it.pkg)),
                                seatNeed(it),
                            )
                        }.sortedBy { it.rank }
                val (blocked, ready) = rows.partition { it.block != null }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ready.forEach { row ->
                        SeatAppTile(
                            app = row.app,
                            line = row.need.line(),
                            mark = row.need.mark,
                            blocked = false,
                            onClick = { onPick(row.app) },
                        )
                    }
                }
                if (blocked.isNotEmpty()) {
                    Text(
                        "Can't go in now",
                        Modifier.padding(start = 2.dp, top = 16.dp, bottom = 8.dp),
                        style = TextStyles.PageLine.copy(fontSize = 13.sp, lineHeight = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = t.text3,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        blocked.forEach { row ->
                            val block = row.block ?: return@forEach
                            SeatAppTile(app = row.app, line = block.line, mark = block.mark, blocked = true)
                        }
                    }
                }
            }
        }
    }
    // A hairline over the bar only while there is more list than shows.
    if (scroll.canScrollForward || scroll.canScrollBackward) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.hair))
    }
    Spacer(Modifier.height(12.dp))
    if (failed) {
        SeatBar("Try again", Lucide.RefreshCw, onClick = onRetry)
    } else {
        SeatBar("Add a new app", Lucide.Plus, onClick = onAdd)
    }
    Spacer(Modifier.height(16.dp))
}
