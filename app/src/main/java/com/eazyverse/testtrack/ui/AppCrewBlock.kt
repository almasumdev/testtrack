package com.eazyverse.testtrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users

/** Seat states whose crew is over for this app: its tile is the Record tab's once the crew is over. */
private val RecordStates = setOf("finished", "removed")

/**
 * The app's crew test, drawn with the Crew tab's own tiles from the Crew tab's model: the Lobby tile
 * while it waits, checks or starts, the Run tile while it is tested, the Record tile once it is over.
 * The tiles leave out their app line here, since the page is the app. A seat taken out of a crew that
 * is still running keeps the Run tile, as on the Run tab. With no seat, or one sent back, it is the
 * start row with Put in. Tapping a tile opens its crew.
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
    val crew = seat?.let { s -> crews.crews.firstOrNull { it.id == s.crewId } }
    val record = seat?.let { s -> crews.records.items?.firstOrNull { it.crewId == s.crewId } }
    val tile =
        crewBlockTile(
            state = seat?.state,
            phase = crew?.phase,
            hasRecord = record != null,
            recordsLoading = crews.records.items == null && !crews.records.failed,
            crewsLoading = crews.loading || crews.reloading,
        )
    when (tile) {
        CrewBlockTile.START ->
            AppStartRow(
                Lucide.Users,
                "Find a crew",
                if (seat == null) CREW_START_LINE else crewStatus(app),
                onClick = putIn,
                pill = "Put in",
            )
        CrewBlockTile.RUN ->
            crew?.let { CrewListRow(it, onChat = { onOpenChat(it.id) }, showApp = false) { onOpenCrew(it.id) } }
        CrewBlockTile.LOBBY ->
            crew?.let { CrewLobbyRow(it, onChat = { onOpenChat(it.id) }, showApp = false) { onOpenCrew(it.id) } }
        CrewBlockTile.RECORD ->
            record?.let {
                // Only a crew still in the crew list can be opened; the others answer 403 now.
                val over = crew?.takeIf { c -> c.phase == CrewPhase.RECORD }
                RecordTile(
                    record = it,
                    crew = over,
                    busy = crews.records.busy[it.crewId],
                    onOpen = over?.let { c -> { onOpenCrew(c.id) } },
                    onAnswer = { answer -> crews.records.answerProdAccess(it.crewId, answer) },
                    onClaim = { crews.records.claimPerfect(it.crewId) },
                    showApp = false,
                    onPutIn = if (seat.state == "removed") putIn else null,
                )
            }
        CrewBlockTile.LOADING -> CrewRunRowLoading()
        CrewBlockTile.SEAT -> {
            val canOpen = seat?.state !in RecordStates || crew?.phase == CrewPhase.RECORD
            SeatRow(app, canOpen) { seat?.let { onOpenCrew(it.crewId) } }
        }
    }
    // Not crew.mine: an owner can hold two seats in one crew, and this page is one app's.
    crew?.seats?.firstOrNull { it.mine && it.packageName == app.pkg }?.let {
        SeatReports(it, "What members wrote, newest first. Who wrote it isn't shown.", title = true)
    }
}

/** Which tile the app page's crew block draws. */
internal enum class CrewBlockTile { START, LOBBY, RUN, RECORD, LOADING, SEAT }

/**
 * Picks the crew block's tile, the way the Crew tab would show that crew.
 *
 * A crew still in the crew list wins: an app taken out of a crew that is still running has no
 * record yet, and the Run tab shows it as the Run tile with "Your app is out", so the page does too.
 * A seat that is over falls back to its record, and only then to the seat in words. [phase] is the
 * phase of the seat's crew in the crew list, or null when the list does not have it.
 */
internal fun crewBlockTile(
    state: String?,
    phase: CrewPhase?,
    hasRecord: Boolean,
    recordsLoading: Boolean,
    crewsLoading: Boolean,
): CrewBlockTile =
    when {
        state == null || state == "rejected" -> CrewBlockTile.START
        phase == CrewPhase.RUN -> CrewBlockTile.RUN
        phase == CrewPhase.LOBBY -> CrewBlockTile.LOBBY
        state in RecordStates && hasRecord -> CrewBlockTile.RECORD
        state in RecordStates && (recordsLoading || crewsLoading) -> CrewBlockTile.LOADING
        state !in RecordStates && crewsLoading -> CrewBlockTile.LOADING
        else -> CrewBlockTile.SEAT
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
