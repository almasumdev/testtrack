package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.HandHeart
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserX
import com.eazyverse.testtrack.domain.pluralOf
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * A volunteer's own parts of a crew's page: their spot and what it has paid, and what the page
 * says once the spot is over. Plus the small hand that marks a volunteer's face anywhere.
 */

/** The hand on a volunteer's face: a filled accent round with a ring in the card's colour. */
@Composable
internal fun VolunteerBadge(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(t.accent)
            .border(1.5.dp, t.bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(Lucide.HandHeart, null, Modifier.size(size * 0.62f), tint = t.onAccent) }
}

/** Figures side by side in a tinted block: what a volunteer has done and earned. */
@Composable
internal fun VolunteerStats(
    stats: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    TintBox(modifier) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            stats.forEachIndexed { i, (value, label) ->
                if (i > 0) Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(value, style = tileStyle(20f, 24f, FontWeight.Bold), color = Tt.colors.text)
                    Text(label, style = tileStyle(12.5f, 16f, FontWeight.Normal), color = Tt.colors.text3)
                }
            }
        }
    }
}

/** The volunteer's spot during the run: what each night pays, the way out, and the nights so far. */
@Composable
internal fun VolunteerCard(
    crew: Crew,
    actions: CrewActions,
) {
    val spot = crew.volunteer ?: return
    // Tonight is still to go until it is paid, so the two always add up to the volunteer's nights.
    val toGo = (crew.ownNights - (crew.ownDay - 1).coerceAtLeast(0)).coerceAtLeast(0)
    CrewCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Lucide.HandHeart, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Volunteering", style = tileStyle(16f, 19f, FontWeight.SemiBold), color = Tt.colors.text)
                Text(
                    "${CrewRules.VOLUNTEER_NIGHT_TIX} tix for each night you finish",
                    style = tileStyle(12.5f, 16f, FontWeight.Normal),
                    color = Tt.colors.text3,
                )
            }
            InlineAction(
                "Leave",
                leading = Lucide.LogOut,
                color = Tt.colors.bad,
                busy = actions.busySeat == spot.id,
            ) { actions.onLeaveVolunteer(crew) }
        }
        VolunteerStats(
            listOf(
                "${spot.nightsPaid}" to "${pluralOf(spot.nightsPaid, "night")} done",
                "${spot.tixEarned} tix" to "earned",
                "$toGo" to "${pluralOf(toGo, "night")} to go",
            ),
            Modifier.padding(top = 12.dp),
        )
    }
}

/** The spot is over before the run: taken out, left, closed by an app coming back, or by the team. */
@Composable
internal fun VolunteerEndedHero(
    crew: Crew,
    actions: CrewActions,
) {
    val spot = crew.volunteer ?: return
    val missed = spot.lastNight?.let { "Night ${it + 1} wasn't finished." } ?: "A night wasn't finished."
    val (chip, headline, line) =
        when (spot.state) {
            "out" -> Triple("Out", "You're out of ${crew.name}", spot.endReason.ifBlank { missed })
            "released" ->
                Triple(
                    "Spot closed",
                    "Your volunteer spot in ${crew.name} closed",
                    "An app came back into the run. Nights you finished are paid.",
                )
            "removed" ->
                Triple(
                    "Taken out",
                    "The TestTrack team took you out of ${crew.name}",
                    spot.endReason.ifBlank { "Nights you finished are paid." },
                )
            else -> Triple("Left", "You left ${crew.name}", "Nights you finished are paid.")
        }
    Column {
        CrewHero(
            chip = chip,
            tone = if (spot.state == "out" || spot.state == "removed") Tone.BAD else Tone.QUIET,
            icon = if (spot.state == "left") Lucide.LogOut else Lucide.UserX,
            headline = headline,
            line = line,
            extra = {
                VolunteerStats(
                    listOf(
                        "${spot.nightsPaid}" to "${pluralOf(spot.nightsPaid, "night")} done",
                        "${spot.tixEarned} tix" to "earned and kept",
                    ),
                    Modifier.padding(top = 12.dp),
                )
                Spacer(Modifier.height(14.dp))
                Primary("Find another crew", Modifier.fillMaxWidth(), mark = rememberVectorPainter(Lucide.HandHeart)) {
                    actions.onFindCrew()
                }
            },
        )
        IconLine(
            Lucide.Info,
            Tt.colors.text4,
            AnnotatedString(
                if (spot.state == "out") {
                    "Volunteers are out after one missed night, so ${crew.name} can't take you back."
                } else {
                    "You can't volunteer in ${crew.name} again."
                },
            ),
            tileStyle(12.5f, 17f, FontWeight.Normal),
            Tt.colors.text4,
            Modifier.padding(top = 10.dp),
        )
    }
}

/** The run tab's foot for a crew the reader volunteers in, where a member's app would be. */
@Composable
internal fun CrewTileVolunteer(
    crew: Crew,
    trailing: (@Composable () -> Unit)? = null,
) {
    val spot = crew.volunteer ?: return
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(Lucide.HandHeart, 28.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            if (spot.ended) "No longer volunteering here" else "Volunteering, ${CrewRules.VOLUNTEER_NIGHT_TIX} tix a night",
            if (trailing != null) Modifier.weight(1f) else Modifier,
            style = TextStyles.PageLine,
            color = Tt.colors.text2,
            maxLines = 2,
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
