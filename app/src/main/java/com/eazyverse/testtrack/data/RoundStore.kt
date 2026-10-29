package com.eazyverse.testtrack.data

import android.content.Context
import androidx.core.content.edit
import com.eazyverse.testtrack.domain.DockEdge
import com.eazyverse.testtrack.domain.DockSpot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Tonight's round, kept on the phone rather than only in memory.
 *
 * Android may close TestTrack while somebody is inside the app being tested, which used to lose
 * the visit however long they stayed. The round is written here as it moves, so whatever opens
 * TestTrack next can still measure the app that was on screen and record it. Usage records last
 * days, so a visit measured an hour later is measured the same.
 *
 * Also where the testing control was left on screen, so it opens there on the next app.
 */
object RoundStore {
    private const val PREFS = "testtrack.round"
    private const val ROUND = "round"
    private const val EDGE = "dock_edge"
    private const val ALONG = "dock_along"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Stop(
        val seatId: String,
        val packageName: String,
        val label: String,
        val notes: String = "",
        /** The member's own night in that app's crew, which its picture is kept and sent for. */
        val day: Int = 0,
    )

    /** The apps of the round, which one is on screen, and since when. */
    @Serializable
    data class Round(
        val stops: List<Stop>,
        val index: Int,
        val since: Long,
        /** Why each app the server refused was skipped, for the round's last line. */
        val skipped: List<String> = emptyList(),
        /** Whether the round takes pictures, so one found after a restart knows its camera went. */
        val pictures: Boolean = false,
        /** When the round began; 0 from a build before it was kept. */
        val startedAt: Long = 0L,
    ) {
        val current: Stop? get() = stops.getOrNull(index)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(
        context: Context,
        round: Round?,
    ) = prefs(context).edit {
        if (round == null) remove(ROUND) else putString(ROUND, json.encodeToString(Round.serializer(), round))
    }

    fun load(context: Context): Round? =
        prefs(context).getString(ROUND, null)?.let { runCatching { json.decodeFromString(Round.serializer(), it) }.getOrNull() }

    /** Where the control was left: the right edge, a little below the middle, the first time. */
    fun spot(context: Context): DockSpot {
        val p = prefs(context)
        val edge = runCatching { DockEdge.valueOf(p.getString(EDGE, null) ?: "") }.getOrDefault(DockEdge.RIGHT)
        return DockSpot(edge, p.getFloat(ALONG, 0.6f))
    }

    fun keepSpot(
        context: Context,
        spot: DockSpot,
    ) = prefs(context).edit {
        putString(EDGE, spot.edge.name)
        putFloat(ALONG, spot.along)
    }
}
