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

/**
 * The crews taking seats, and what taking one means.
 *
 * Nearest to full at the top, because that one starts soonest and is the one that most needs
 * finishing. The order is doing a job that used to be done by a rule: there was one open crew at a
 * time so that people could not spread themselves thin across several half empty ones, and the
 * cost was that an older crew losing a seat froze everybody in the newer one. Several open, sorted,
 * puts people in the same place by making it the obvious choice rather than the only one.
 *
 * No roster here. Who is already in a crew you have not joined is a list of strangers, and it was
 * the longest thing on the screen: fourteen rows answering a question nobody asked, above the two
 * lines that actually decide anything. What takes its place is the deal itself, which is what
 * somebody standing here is trying to work out.
 */
@Composable
fun CrewOpenTab(
    vm: CrewViewModel,
    onTakeSeat: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = Gutter),
    ) {
        itemsIndexed(vm.opens, key = { _, crew -> crew.id }) { index, crew ->
            val taken = crew.seats.size
            val free = CrewRules.SEATS - taken
            val yours = crew.seats.any { it.mine }

            Box(Modifier.padding(start = Gutter, end = Gutter, bottom = 12.dp)) {
                CrewCard(padding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            crew.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.weight(1f))
                        // Only on the one at the top, and only while it is genuinely close. A
                        // badge on every card is a badge on nothing.
                        if (index == 0 && free <= 3) {
                            Pill("Starts soonest", Status.posted, Status.postedSoft)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    SeatTiles(crew.seats)
                    Spacer(Modifier.height(11.dp))
                    Text(
                        "$free of ${CrewRules.SEATS} seats left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    when {
                        yours ->
                            Text(
                                "You are in this one. It is in your Lobby.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        // One seat across every open crew rather than one per crew. Offering a
                        // second before the first has started is offering fourteen more apps a
                        // night for a fortnight that has not begun.
                        vm.holdsSeat ->
                            Text(
                                // Fills, not starts. A crew that is full has stopped taking
                                // seats, so there is nothing left to hold you back at that
                                // point, and telling somebody to wait for a fortnight to begin
                                // when they only have to wait for it to fill is a week wrong.
                                "You are in ${vm.holding}. Take another seat once it is full.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        else -> Primary("Take a seat") { onTakeSeat(crew.id) }
                    }
                }
            }
        }

        item { CrewHeading("How a crew works") }
        itemsIndexed(RULES) { index, rule ->
            if (index > 0) RowDivider()
            RuleRow(index + 1, rule)
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
        "Your app goes in with ${CrewRules.SEATS - 1} others. Two of them install it before " +
            "your seat counts.",
        "The crew starts at 12:01 the night it fills, in your own time zone.",
        "You open all ${CrewRules.NIGHTLY} of the other apps, every day, for " +
            "${CrewRules.RUN_DAYS} days.",
        "Miss two days in a row and your app leaves the crew, and everyone stops testing it.",
        "One seat at a time. Take another once this crew is full.",
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
