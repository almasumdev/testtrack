package com.eazyverse.testtrack.ui

import org.junit.Assert.*
import org.junit.Test

class CrewLobbyPlacesTest {
    private val other = CrewSeat("other", "Other", "Tester", state = SeatState.CONFIRMED)
    private val mine = CrewSeat("mine", "Mine", "You", state = SeatState.CHECKING, mine = true)

    @Test fun the_readers_checking_app_stays_in_the_checking_group() {
        val crew = Crew("c", "Crew", CrewPhase.LOBBY, filled = 1, taken = 2, seats = listOf(mine, other))
        val places = crewLobbyPlaces(crew)
        assertEquals(other, places[0].seat)
        assertEquals(mine, places[1].seat)
        assertEquals(SeatState.CHECKING, places[1].state)
        assertEquals(13, places.count { it.state == null })
    }

    @Test fun confirmed_reader_is_first_without_reordering_other_members() {
        val own = mine.copy(state = SeatState.CONFIRMED)
        val crew = Crew("c", "Crew", CrewPhase.LOBBY, filled = 2, taken = 2, seats = listOf(other, own))
        assertEquals(listOf(own, other), crewLobbyPlaces(crew).mapNotNull { it.seat })
    }

    @Test fun absent_metadata_does_not_make_taken_seats_look_open() {
        val crew = Crew("c", "Crew", CrewPhase.LOBBY, filled = 3, taken = 5, seats = listOf(other))
        val places = crewLobbyPlaces(crew)
        assertEquals(3, places.count { it.state == SeatState.CONFIRMED })
        assertEquals(2, places.count { it.state == SeatState.CHECKING })
        assertEquals(10, places.count { it.state == null })
        assertNull(places[1].seat)
    }

    @Test fun rejected_and_removed_seats_never_supply_an_icon() {
        val crew =
            Crew(
                "c",
                "Crew",
                CrewPhase.LOBBY,
                filled = 1,
                taken = 1,
                seats = listOf(mine.copy(state = SeatState.REJECTED), other, other.copy(id = "old", state = SeatState.REMOVED)),
            )
        assertEquals(listOf(other), crewLobbyPlaces(crew).mapNotNull { it.seat })
        assertEquals(CrewRules.SEATS, crewLobbyPlaces(crew).size)
    }
}
