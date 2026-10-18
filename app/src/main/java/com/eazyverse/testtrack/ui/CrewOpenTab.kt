package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
                        headline = crew?.let { "${it.name} · $free of ${CrewRules.SEATS} seats left" } ?: "No crew is filling",
                        line =
                            when {
                                vm.inOpen -> "You are in this one. It is in your Lobby."
                                vm.blocked != null -> vm.blocked.orEmpty()
                                crew == null -> "The next app put in starts one."
                                else ->
                                    "It starts at midnight the day it is full. A seat costs ${CrewRules.SEAT_COST} tix, " +
                                        "back in full if your app leaves before the run."
                            },
                        lineTone = if (vm.inOpen) Tone.ACCENT else null,
                        meter = { SeatCountTiles(filled = crew?.filled ?: 0, taken = crew?.taken ?: 0) },
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

        item { CrewHeading("How a crew works") }
        itemsIndexed(RULES) { index, rule ->
            ListTile(index, RULES.size) {
                RuleRow(index + 1, rule)
            }
        }
    }
}

/**
 * What somebody is agreeing to, in the order they will meet it.
 *
 * Numbered rather than bulleted, because a rule you can refer to by number is one that can be
 * pointed at when somebody asks why their app was taken out.
 */
private val RULES =
    listOf(
        "Your app goes in with ${CrewRules.SEATS - 1} others. We check it on Play the way the " +
            "crew will see it, so add the testers group to your closed test first.",
        "The crew starts at midnight the day it is full, in your own time zone.",
        "You open all ${CrewRules.NIGHTLY} of the other apps, every day, for " +
            "${CrewRules.RUN_DAYS} days.",
        "Miss a day and you get a warning. Miss two days in a row and your app leaves the crew.",
        "If an app will not install, report it. ${CrewRules.REPORTS_TO_RETURN} reports before " +
            "the start send it back to its owner to fix.",
        "One app waiting at a time. Put the next one in once this crew is full.",
    )

/** One numbered rule, in the shape the submit screen already uses. */
@Composable
private fun RuleRow(
    number: Int,
    rule: String,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 11.dp)) {
        Text(
            "$number.",
            Modifier.width(24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            rule,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
