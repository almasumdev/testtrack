package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users

/**
 * The crew taking seats, and what taking one means.
 *
 * One card: the crew nearest to starting, how full it is, and one way in. When none is filling
 * the card says the next app starts one, because that is what happens, and a tab with nothing on
 * it would read as there being nowhere to go.
 *
 * No roster here. Who is already in a crew you have not joined is a list of strangers, and the
 * server does not send it to anybody outside the crew anyway. What takes its place is the deal
 * itself, which is what somebody standing here is trying to work out.
 */
@Composable
fun CrewOpenTab(
    vm: CrewViewModel,
    actions: CrewActions,
    guide: CrewGuide = CrewGuide(CrewReady(app = null, member = true, permissions = true)),
) {
    val crew = vm.open
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = Gutter + dockRoom()),
    ) {
        // The rules below are the app's own and need nothing from the server; only the card waits.
        item {
            LoadSwap(vm.firstLoad) { waiting ->
                if (waiting) {
                    CrewOpenCardLoading()
                    return@LoadSwap
                }
                Box(Modifier.padding(start = Gutter, end = Gutter, bottom = 4.dp)) {
                    val free = CrewRules.SEATS - (crew?.taken ?: 0)
                    CrewHero(
                        chip =
                            when {
                                crew == null -> "Nothing filling"
                                free <= 3 -> "Starts soonest"
                                else -> "Taking seats"
                            },
                        tone = if (crew != null && free <= 3) Tone.OK else Tone.ACCENT,
                        icon = Lucide.Users,
                        headline = crew?.let { "${it.name}, $free of ${CrewRules.SEATS} seats left" } ?: "No crew is filling",
                        line =
                            when {
                                vm.inOpen -> "You are in this one. It is in your Lobby."
                                vm.blocked != null -> vm.blocked.orEmpty()
                                crew == null -> "The next app put in starts one."
                                else ->
                                    "It starts at midnight, your time, the day it is full. A seat costs ${CrewRules.SEAT_COST} tix, " +
                                        "back in full if you take it out while the crew fills or finish without missing a day."
                            },
                        lineTone = if (vm.inOpen) Tone.ACCENT else null,
                        meter = { SeatCountTiles(filled = crew?.filled ?: 0, taken = crew?.taken ?: 0, icons = crew?.icons.orEmpty()) },
                        action =
                            when {
                                vm.inOpen || vm.blocked != null -> null
                                vm.signedOut -> "Sign in to take a seat"
                                else -> "Take a seat"
                            },
                        onAction = actions.onTakeSeat,
                    )
                }
            }
        }

        crewGuide(guide.ready, guide.hasApps, guide.onApp, guide.onJoin, guide.onAllow)
    }
}

/** What the guide under the card needs from outside the crew: My apps, the group, the phone. */
class CrewGuide(
    val ready: CrewReady,
    val hasApps: Boolean = true,
    val onApp: () -> Unit = {},
    val onJoin: () -> Unit = {},
    val onAllow: () -> Unit = {},
)
