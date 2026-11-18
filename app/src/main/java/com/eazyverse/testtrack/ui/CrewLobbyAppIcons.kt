package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Smartphone
import com.eazyverse.testtrack.ui.theme.Tt

internal data class CrewLobbyPlace(
    val state: SeatState?,
    val seat: CrewSeat? = null,
)

/** Counts remain the server's; artwork fills only known live seats, with the reader first in its group. */
internal fun crewLobbyPlaces(crew: Crew): List<CrewLobbyPlace> {
    val confirmed = crew.filled.coerceIn(0, CrewRules.SEATS)
    val taken = crew.taken.coerceIn(confirmed, CrewRules.SEATS)

    fun members(state: SeatState) =
        crew.seats
            .distinctBy { it.id }
            .filter { it.state == state }
            .sortedByDescending { it.mine }
    val accepted = members(SeatState.CONFIRMED)
    val checking = members(SeatState.CHECKING)
    return List(confirmed) { CrewLobbyPlace(SeatState.CONFIRMED, accepted.getOrNull(it)) } +
        List(taken - confirmed) { CrewLobbyPlace(SeatState.CHECKING, checking.getOrNull(it)) } +
        List(CrewRules.SEATS - taken) { CrewLobbyPlace(null) }
}

/** Plain accepted app icons; only seats still being checked retain an attention cue. */
@Composable
internal fun CrewLobbyAppIcons(crew: Crew) {
    val places = remember(crew.filled, crew.taken, crew.seats) { crewLobbyPlaces(crew) }
    val accepted = places.count { it.state == SeatState.CONFIRMED }
    val checking = places.count { it.state == SeatState.CHECKING }
    val mine = places.any { it.seat?.mine == true }
    CrewAppIconStrip(
        CrewRules.SEATS,
        "$accepted confirmed, $checking checking, ${CrewRules.SEATS - accepted - checking} open seats." +
            if (mine) " The dot marks your app." else "",
    ) { side ->
        places.forEachIndexed { index, place ->
            key(place.seat?.id ?: "place-$index") {
                val shape = RoundedCornerShape(side * 0.3f)
                val elevationTint =
                    when (place.state) {
                        SeatState.CHECKING -> Tone.WARN.ink()
                        else -> Color.Unspecified
                    }
                Box(Modifier.size(side).crewIconElevation(elevationTint)) {
                    val seat = place.seat
                    if (seat != null) {
                        AppMark(seat.packageName, seat.appName, seat.icon, size = side, ownIcon = seat.mine)
                    } else {
                        Box(
                            Modifier
                                .size(side)
                                .clip(shape)
                                .background(Tt.colors.tile),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (place.state != null) Icon(Lucide.Smartphone, null, Modifier.size(side * 0.65f), tint = Tt.colors.text3)
                        }
                    }
                    if (seat?.mine == true) {
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size((side * 0.3f).coerceAtMost(6.dp))
                                .clip(CircleShape)
                                .background(Tt.colors.accentInk)
                                .border(1.dp, Tt.colors.bg, CircleShape),
                        )
                    }
                }
            }
        }
    }
}
