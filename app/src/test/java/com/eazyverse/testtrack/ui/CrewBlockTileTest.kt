package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which tile the app page's crew block draws: the Crew tab's own tile for that crew. */
class CrewBlockTileTest {
    private fun tile(
        state: String?,
        phase: CrewPhase? = null,
        hasRecord: Boolean = false,
        recordsLoading: Boolean = false,
        crewsLoading: Boolean = false,
    ) = crewBlockTile(state, phase, hasRecord, recordsLoading, crewsLoading)

    @Test
    fun noSeatOrSentBackIsTheStartRow() {
        assertEquals(CrewBlockTile.START, tile(null))
        assertEquals(CrewBlockTile.START, tile("rejected"))
        assertEquals(CrewBlockTile.START, tile("rejected", phase = CrewPhase.LOBBY))
    }

    @Test
    fun takenOutOfARunningCrewIsTheRunTile() {
        assertEquals(CrewBlockTile.RUN, tile("removed", phase = CrewPhase.RUN))
        assertEquals(CrewBlockTile.RUN, tile("removed", phase = CrewPhase.RUN, hasRecord = true))
        assertEquals(CrewBlockTile.RUN, tile("finished", phase = CrewPhase.RUN))
    }

    @Test
    fun overWithoutARunningCrewIsTheRecordTile() {
        assertEquals(CrewBlockTile.RECORD, tile("removed", hasRecord = true))
        assertEquals(CrewBlockTile.RECORD, tile("finished", phase = CrewPhase.RECORD, hasRecord = true))
    }

    @Test
    fun overWithNeitherWaitsThenFallsBack() {
        assertEquals(CrewBlockTile.LOADING, tile("removed", recordsLoading = true))
        assertEquals(CrewBlockTile.LOADING, tile("removed", crewsLoading = true))
        assertEquals(CrewBlockTile.SEAT, tile("removed"))
        assertEquals(CrewBlockTile.SEAT, tile("finished", phase = CrewPhase.RECORD))
    }

    @Test
    fun aSeatInPlayFollowsItsCrew() {
        assertEquals(CrewBlockTile.RUN, tile("testing", phase = CrewPhase.RUN))
        assertEquals(CrewBlockTile.LOBBY, tile("waiting", phase = CrewPhase.LOBBY))
        assertEquals(CrewBlockTile.LOADING, tile("testing", crewsLoading = true))
        assertEquals(CrewBlockTile.SEAT, tile("testing"))
    }
}
