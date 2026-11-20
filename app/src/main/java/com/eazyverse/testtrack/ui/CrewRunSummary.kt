package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BookOpen
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.domain.pluralOf
import com.eazyverse.testtrack.ui.theme.Tt

/** Tonight across every run, using the existing eligibility rules and the shared list-tile frame. */
@Composable
internal fun CrewRunSummary(
    runs: List<Crew>,
    tonight: List<CrewApp>,
    done: Int,
    actions: CrewActions,
) {
    val going = runs.filter { !it.out && !it.nightsDone }
    val left = tonight.size
    val total = left + done
    val ready = openAllOf(tonight, { actions.copyOf(it) }, actions.sendingPicture).size
    val finished = going.isEmpty() && runs.any { it.nightsDone }
    val out = going.isEmpty() && !finished
    val nights = going.maxOfOrNull { (it.runDays - it.day + if (left > 0) 1 else 0).coerceAtLeast(0) } ?: 0
    val reported = runs.any { crew -> crew.apps.any { it.reported } }
    var guide by rememberSaveable { mutableStateOf(false) }

    CrewCard(padding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Lucide.Moon, 32.dp, corner = 10.dp, glyph = 16.dp, tint = Tone.ACCENT.ink())
            Spacer(Modifier.width(9.dp))
            Text("Tonight", Modifier.weight(1f), style = tileStyle(15f, 20f, FontWeight.SemiBold), color = Tt.colors.text)
            InlineAction("Nightly guide", leading = Lucide.BookOpen) { guide = true }
        }
        Spacer(Modifier.height(8.dp))
        if (left > 0 && going.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("$left", style = tileStyle(48f, 50f, FontWeight.Medium), color = Tt.colors.text)
                    Spacer(Modifier.height(4.dp))
                    Text("apps left tonight", style = TextStyles.PageLine, color = Tt.colors.text2)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$done", style = tileStyle(29f, 32f, FontWeight.Medium), color = Tt.colors.text)
                        Text(" / $total", style = tileStyle(17f, 26f, FontWeight.Normal), color = Tt.colors.text3)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(if (reported) "completed" else "opened", style = TextStyles.PageLine, color = Tt.colors.text3)
                }
            }
        } else {
            Text(
                when {
                    finished -> "Your nights are done"
                    out -> "Nothing more owed"
                    left > 0 -> "$left ${pluralOf(left, "app")} left"
                    total == 0 -> "Nothing owed tonight"
                    else -> "You're done tonight"
                },
                style = tileStyle(28f, 33f, FontWeight.SemiBold),
                color = Tt.colors.text,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    finished -> "The crew closes when every member finishes."
                    out -> "Your app is no longer in these runs."
                    else ->
                        "${going.size} ${pluralOf(going.size, "crew")}, " +
                            "$nights ${pluralOf(nights, "night")} to go"
                },
                style = TextStyles.PageLine,
                color = Tt.colors.text3,
            )
        }
        if (going.isNotEmpty() && total > 0) {
            Spacer(Modifier.height(18.dp))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val facts =
                    listOf(
                        Lucide.Clock to "${CrewRules.OPEN_SECONDS}s per app",
                        Lucide.Users to "${going.size} ${pluralOf(going.size, "crew")}",
                        Lucide.Moon to "$nights ${pluralOf(nights, "night")} left",
                    )
                facts.forEach { (icon, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(icon, null, Modifier.size(14.dp), tint = Tt.colors.text3)
                        Text(label, style = tileStyle(12f, 17f, FontWeight.Normal), color = Tt.colors.text3)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Primary(
            if (ready > 0) "Run all $ready" else "Run all",
            enabled = ready > 0,
            mark = rememberVectorPainter(Lucide.Play),
        ) { actions.onOpenAll(null) }
    }
    if (guide) CrewNightlyGuide { guide = false }
}

/** The approved top-card help stays within Run; it does not change or open a crew detail page. */
@Composable
private fun CrewNightlyGuide(onDismiss: () -> Unit) {
    PickerSheet(
        title = "Nightly guide",
        onDismiss = onDismiss,
        leading = { IconTile(Lucide.BookOpen, 36.dp, corner = 12.dp, glyph = 18.dp, tint = Tone.ACCENT.ink()) },
        skipHalf = true,
        fitMax = 0.9f,
    ) { close ->
        Column(
            Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReviewBullet(
                body =
                    "Open each app for ${CrewRules.OPEN_SECONDS} seconds. " +
                        "It needs at least ${CrewRules.VISIT_SECONDS} seconds in front and a picture to count.",
                highlights = listOf("${CrewRules.OPEN_SECONDS} seconds", "${CrewRules.VISIT_SECONDS} seconds", "a picture"),
            )
            ReviewBullet(
                body = "Install from Google Play, allow usage access, and approve screen sharing for the round.",
                highlights = listOf("Google Play", "usage access", "screen sharing"),
            )
            ReviewBullet(
                body = "Finish tonight's owed apps before your local midnight. Pending pictures retry automatically.",
                highlights = listOf("your local midnight"),
            )
            Secondary("Done", Modifier.fillMaxWidth()) { close {} }
            Spacer(Modifier.height(8.dp))
        }
    }
}
