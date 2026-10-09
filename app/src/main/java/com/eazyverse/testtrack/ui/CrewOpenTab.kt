package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

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
        if (vm.firstLoad) {
            item { CrewOpenCardLoading() }
        } else {
            item {
                Box(Modifier.padding(start = Gutter, end = Gutter, bottom = 12.dp)) {
                    CrewCard(padding = PaddingValues(16.dp)) {
                        val free = CrewRules.SEATS - (crew?.taken ?: 0)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // The app's own words, so they are there before the answer is; the crew's
                            // name is news and goes in the line under the seats.
                            Text(
                                "Open crew",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.weight(1f))
                            // Only while it is genuinely close. A badge on every card is a badge on nothing.
                            if (crew != null && free <= 3) {
                                Pill("Starts soonest", Status.posted, Status.postedSoft)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        SeatCountTiles(filled = crew?.filled ?: 0, taken = crew?.taken ?: 0)
                        Spacer(Modifier.height(11.dp))
                        Text(
                            if (crew == null) {
                                "No crew is filling right now. The next app starts one."
                            } else {
                                "${crew.name} · $free of ${CrewRules.SEATS} seats left"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        when {
                            vm.inOpen ->
                                Text(
                                    "You are in this one. It is in your Lobby.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Tt.colors.accentInk,
                                )
                            // One waiting app at a time. Offering a second before the first crew is
                            // full is offering fourteen more apps a day for a fortnight that has not
                            // been set.
                            vm.blocked != null ->
                                Text(
                                    vm.blocked.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            vm.signedOut -> Primary("Sign in to take a seat", onClick = actions.onTakeSeat)
                            else -> Primary("Take a seat", onClick = actions.onTakeSeat)
                        }
                    }
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
