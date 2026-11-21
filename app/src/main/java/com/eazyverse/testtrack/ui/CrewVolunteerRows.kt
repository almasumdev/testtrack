package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.HandHeart
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.SpotState
import com.eazyverse.testtrack.viewmodel.VolunteerSpot

/*
 * The Open tab's second offer, under the crew taking seats: running crews with volunteer spots.
 * No app needed; a volunteer opens a crew's apps every night and is paid for each whole night.
 */

/** What the Open tab's volunteer list needs: the list (null while it loads) and its two taps. */
class VolunteerSection(
    val spots: List<VolunteerSpot>? = null,
    /** The list could not be read: the section stays away. */
    val failed: Boolean = false,
    val onVolunteer: (VolunteerSpot) -> Unit = {},
    /** A crew the reader volunteers in already: its page. */
    val onOpen: (VolunteerSpot) -> Unit = {},
)

/** The heading, its one line, then a row for each running crew, or one card when none has a spot. */
internal fun LazyListScope.volunteerSpots(section: VolunteerSection) {
    val spots = section.spots
    if (section.failed && spots == null) return
    val open = spots.orEmpty().count { it.state == SpotState.OPEN }
    item(key = "volunteer-head") {
        Column {
            // The list's 8dp gap and these 12 make the usual 20 above a heading.
            CrewHeading("Volunteer in a running crew", if (open > 0) "$open" else null, top = 12.dp)
            Text(
                "No app needed. Open a running crew's apps every night and earn " +
                    "${CrewRules.VOLUNTEER_NIGHT_TIX} tix a night.",
                Modifier.padding(horizontal = Gutter).padding(bottom = 2.dp),
                style = TextStyles.DialogNote,
                color = Tt.colors.text4,
            )
        }
    }
    item(key = "volunteer-spots") {
        LoadSwap(spots == null) { waiting ->
            Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val list = spots.orEmpty()
                when {
                    waiting -> repeat(2) { VolunteerSpotRowLoading() }
                    list.none { it.state == SpotState.OPEN || it.state == SpotState.VOLUNTEERING } -> NoSpotsCard()
                    else -> list.forEach { spot -> key(spot.crewId) { VolunteerSpotRow(spot, section.onVolunteer, section.onOpen) } }
                }
            }
        }
    }
}

/** One running crew: its name and night, its apps' icons, then where the reader stands with it. */
@Composable
internal fun VolunteerSpotRow(
    spot: VolunteerSpot,
    onVolunteer: (VolunteerSpot) -> Unit,
    onOpen: (VolunteerSpot) -> Unit,
) {
    val mine = spot.state == SpotState.VOLUNTEERING
    CrewCard(
        if (spot.state == SpotState.FULL) Modifier.alpha(0.55f) else Modifier,
        padding = PaddingValues(16.dp),
        onClick = if (mine) ({ onOpen(spot) }) else null,
    ) {
        CrewTileHeading(spot.name, "Night ${spot.day} of ${spot.runDays}")
        if (spot.icons.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SpotIcons(spot.icons)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val (chip, tone, icon) =
                when (spot.state) {
                    SpotState.OPEN -> Triple(if (spot.free == 1) "1 spot" else "${spot.free} spots", Tone.OK, Lucide.Users)
                    SpotState.FULL -> Triple("Full", Tone.QUIET, null)
                    SpotState.VOLUNTEERING -> Triple("You volunteer here", Tone.OK, Lucide.Check)
                    SpotState.MEMBER -> Triple("Your app is in it", Tone.QUIET, null)
                    SpotState.OUT -> Triple("You were taken out", Tone.BAD, null)
                    SpotState.LEFT -> Triple("You left this crew", Tone.QUIET, null)
                }
            StatusChip(chip, tone, icon)
            Spacer(Modifier.width(8.dp))
            Text(
                when (spot.state) {
                    SpotState.OPEN -> "Up to ${spot.tix} tix"
                    SpotState.FULL -> "Spots open when someone leaves"
                    SpotState.VOLUNTEERING -> ""
                    SpotState.MEMBER -> "Members can't volunteer"
                    SpotState.OUT, SpotState.LEFT -> "Can't join again"
                },
                Modifier.weight(1f),
                style = TextStyles.NoteLine,
                color = Tt.colors.text3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when (spot.state) {
                SpotState.OPEN -> RowPill("Volunteer", PillKind.SOFT, leading = Lucide.HandHeart) { onVolunteer(spot) }
                SpotState.VOLUNTEERING -> Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = Tt.colors.text4)
                else -> Unit
            }
        }
    }
}

/** The crew's apps by their Play icons alone, in the strip every crew tile uses. */
@Composable
internal fun SpotIcons(icons: List<String>) {
    CrewAppIconStrip(icons.size, "${icons.size} apps") { side ->
        icons.forEachIndexed { i, icon ->
            key(i) {
                if (icon.isBlank()) {
                    Box(Modifier.size(side).clip(RoundedCornerShape(side * 0.3f)).background(Tt.colors.tile))
                } else {
                    AppMark("", "", icon, size = side)
                }
            }
        }
    }
}

/** Nothing has a spot: the small robot state in the list's place. */
@Composable
private fun NoSpotsCard() {
    RobotState(
        Prop.Kite,
        "No free spots",
        "Spots open when members leave. Check back tomorrow.",
        fill = false,
        compact = true,
    )
}

@Composable
private fun VolunteerSpotRowLoading() {
    CrewCard(padding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Skeleton(width = 78.dp, height = 23.dp)
            Skeleton(width = 70.dp, height = 17.dp)
        }
        Spacer(Modifier.height(14.dp))
        CrewRunAppIconsLoading()
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(width = 64.dp, height = 24.dp, corner = 12.dp)
            Spacer(Modifier.width(8.dp))
            Skeleton(height = 14.dp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Skeleton(width = 104.dp, height = 34.dp, corner = 17.dp)
        }
    }
}
