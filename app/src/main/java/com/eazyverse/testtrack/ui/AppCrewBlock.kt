package com.eazyverse.testtrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users

/** Seat states whose crew is over for this app: its tile is the Record tab's. */
private val RecordStates = setOf("finished", "removed")

/**
 * The app's crew test, drawn with the Crew tab's own tiles from the Crew tab's model: the Lobby tile
 * while it waits, checks or starts, the Run tile while it is tested, the Record tile once it is over.
 * The tiles leave out their app line here, since the page is the app. With no seat, or one sent back,
 * it is the start row with Put in. Tapping a tile opens its crew.
 */
@Composable
internal fun AppCrewBlock(
    app: AppEntry,
    crews: CrewViewModel,
    onOpenCrew: (String) -> Unit,
    onOpenChat: (String) -> Unit,
    onPutIn: () -> Unit,
) {
    val context = LocalContext.current
    val seat = app.crew
    // The Crew tab may not have been opened yet; read once for a seat it has nothing on.
    LaunchedEffect(seat?.crewId) {
        val unread = crews.crews.none { it.id == seat?.crewId } || crews.records.items == null
        if (seat != null && !crews.reloading && unread) crews.load(context)
    }
    val putIn = if (app.canCrew) onPutIn else null
    when {
        seat == null || seat.state == "rejected" ->
            AppStartRow(
                Lucide.Users,
                "Find a crew",
                if (seat == null) CREW_START_LINE else crewStatus(app),
                onClick = putIn,
                pill = "Put in",
            )
        seat.state in RecordStates -> {
            val record = crews.records.items?.firstOrNull { it.crewId == seat.crewId }
            // Only a crew still in the crew list can be opened; the others answer 403 now.
            val crew = crews.crews.firstOrNull { it.id == seat.crewId && it.phase == CrewPhase.RECORD }
            when {
                record != null ->
                    RecordTile(
                        record = record,
                        crew = crew,
                        busy = crews.records.busy[record.crewId],
                        onOpen = crew?.let { { onOpenCrew(it.id) } },
                        onAnswer = { crews.records.answerProdAccess(record.crewId, it) },
                        onClaim = { crews.records.claimPerfect(record.crewId) },
                        showApp = false,
                        onPutIn = if (seat.state == "removed") putIn else null,
                    )
                crews.records.items == null && !crews.records.failed -> CrewRunRowLoading()
                else -> SeatRow(app, crew != null) { onOpenCrew(seat.crewId) }
            }
        }
        else -> {
            val crew = crews.crews.firstOrNull { it.id == seat.crewId }
            when {
                crew?.phase == CrewPhase.RUN ->
                    CrewListRow(crew, onChat = { onOpenChat(crew.id) }, showApp = false) { onOpenCrew(crew.id) }
                crew?.phase == CrewPhase.LOBBY ->
                    CrewLobbyRow(crew, onChat = { onOpenChat(crew.id) }, showApp = false) { onOpenCrew(crew.id) }
                crews.loading || crews.reloading -> CrewRunRowLoading()
                else -> SeatRow(app, true) { onOpenCrew(seat.crewId) }
            }
        }
    }
}

/** The seat in words, for a crew the Crew tab's model has no tile for, such as one it could not read. */
@Composable
private fun SeatRow(
    app: AppEntry,
    canOpen: Boolean,
    onOpen: () -> Unit,
) {
    AppStartRow(
        Lucide.Users,
        app.crew?.crewName?.ifBlank { null } ?: "Crew test",
        crewStatus(app),
        onClick = if (canOpen) onOpen else null,
    )
}
