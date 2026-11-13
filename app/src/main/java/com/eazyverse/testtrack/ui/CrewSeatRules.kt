package com.eazyverse.testtrack.ui

import android.content.Context
import androidx.core.content.edit

/*
 * What the seat sheet decides about an app, as plain functions a test can call: whether it can go
 * in, what it still needs, and the words said about its last check on Play.
 *
 * The server's answer wins where it gives one. An older server gives none of the new fields, so
 * every rule here has a fallback from what My apps already carries.
 */

/** The two boxes an owner confirms in Play Console. Both are required for a seat. */
data class SeatBoxes(
    val countries: Boolean = false,
    val devices: Boolean = false,
) {
    val both: Boolean get() = countries && devices
}

/** What the server says about an app beyond [AppEntry]; every field null or false from an older server. */
data class SeatFacts(
    /** The boxes the server keeps; null when it does not keep them yet. */
    val boxes: SeatBoxes? = null,
    /** Epoch milliseconds of the last check on Play, and what it said. */
    val checkedAt: Long? = null,
    val checkedOk: Boolean? = null,
    /** live, waiting or appeal; null when the server did not say or nothing blocks. */
    val blockReason: String? = null,
    val blockCrew: String? = null,
)

/** Why an app can't take a seat now. */
enum class SeatBlockKind { LIVE, WAITING, APPEAL }

data class SeatBlock(
    val kind: SeatBlockKind,
    val crew: String = "",
) {
    /** The one line a greyed row says. */
    val line: String
        get() =
            when (kind) {
                SeatBlockKind.LIVE -> "Live on Play, so it can't go in"
                SeatBlockKind.WAITING -> "Waits in ${crew.ifBlank { "its crew" }}"
                SeatBlockKind.APPEAL -> "Taken out of ${crew.ifBlank { "its crew" }}, appeal open"
            }
}

/** The crew an appeal for this app is waiting in, if there is one. */
fun appealCrewFor(
    crews: List<Crew>,
    pkg: String,
): String? =
    crews.firstNotNullOfOrNull { crew ->
        crew.mine?.takeIf { it.packageName == pkg && it.appeal?.waiting == true }?.let { crew.name }
    }

/**
 * Whether [app] can't take a seat, and why. The server's reason when it gives one, else worked
 * out from the app: live apps never go in, an app in a crew waits there, and one taken out with
 * an appeal open waits for the team.
 */
fun seatBlock(
    app: AppEntry,
    facts: SeatFacts?,
    appealCrew: String?,
): SeatBlock? {
    val crewName = app.crew?.crewName.orEmpty()
    when (facts?.blockReason) {
        "live" -> return SeatBlock(SeatBlockKind.LIVE)
        "waiting" -> return SeatBlock(SeatBlockKind.WAITING, facts.blockCrew?.ifBlank { null } ?: crewName)
        "appeal" -> return SeatBlock(SeatBlockKind.APPEAL, facts.blockCrew?.ifBlank { null } ?: appealCrew ?: crewName)
    }
    return when {
        app.live -> SeatBlock(SeatBlockKind.LIVE)
        appealCrew != null -> SeatBlock(SeatBlockKind.APPEAL, appealCrew)
        !app.canCrew -> SeatBlock(SeatBlockKind.WAITING, crewName)
        else -> null
    }
}

/** What an app that can be picked still needs before its seat details. */
enum class SeatNeed { GROUP, BOXES, NONE }

fun seatNeed(
    app: AppEntry,
    boxes: SeatBoxes,
): SeatNeed =
    when {
        !app.verified -> SeatNeed.GROUP
        !boxes.both -> SeatNeed.BOXES
        else -> SeatNeed.NONE
    }

/** The status line a pickable row says: what it needs next, or that it is ready. */
fun SeatNeed.line(): String =
    when (this) {
        SeatNeed.GROUP -> "Add the group to its closed track"
        SeatNeed.BOXES -> "Confirm two things in Play Console"
        SeatNeed.NONE -> "Ready"
    }

/**
 * "2 steps before it can go in": the steps still to do, with the seat details always one of them.
 * Step one is done once Play shows the app, step two once both boxes are ticked.
 */
fun stepsLeft(
    verified: Boolean,
    boxes: SeatBoxes,
): Int = (if (verified) 0 else 1) + (if (boxes.both) 0 else 1) + 1

fun stepsLeftLine(left: Int): String = if (left == 1) "1 step before it can go in" else "$left steps before it can go in"

/** The amber line under an unticked box, and where to fix it. */
object SeatBoxCopy {
    const val COUNTRIES = "Available in all countries"
    const val DEVICES = "Not limited to some devices"
    const val COUNTRIES_WHY = "Members in other countries can't install it, and two can't-install answers send your seat back."
    const val DEVICES_WHY = "Members on excluded phones can't install it, and two can't-install answers send your seat back."
    const val COUNTRIES_HOW = "In Play Console: Closed testing, Countries/regions, add all countries."
    const val DEVICES_HOW = "In Play Console: Device catalog, remove excluded devices."
}

private const val PLAY_DOESNT_SHOW = "Play doesn't show it to the group yet. A new rollout can take a few hours to show."

/**
 * The line under the group address: when Play was last asked and what it said. [message] is the
 * server's own words after a refused seat, which replace the stock sentence.
 */
fun lastCheckLine(
    checkedAt: Long?,
    ok: Boolean?,
    now: Long,
    message: String? = null,
): String {
    if (checkedAt == null && message == null) return "Not checked yet."
    val ago = checkedAt?.let { "Checked ${agoWords(now - it)}: " }.orEmpty()
    return when {
        message != null -> message
        ok == false -> "$ago$PLAY_DOESNT_SHOW"
        ok == true -> "${ago}Play shows it to the group."
        else -> "${ago.removeSuffix(": ")}."
    }
}

/** A duration as the last check says it: "just now", "4 min ago", "3 h ago", "2 days ago". */
fun agoWords(millis: Long): String {
    val minutes = (millis / 60_000L).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} h ago"
        minutes < 48 * 60 -> "1 day ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}

/**
 * The boxes kept on the phone, per package, for a server that does not keep them yet. Only the
 * fallback: once the server answers with its own, those are the ones shown.
 */
object SeatBoxStore {
    private const val PREFS = "seat_boxes"

    private fun key(pkg: String) = "pkg:$pkg"

    fun read(
        context: Context,
        pkg: String,
    ): SeatBoxes? {
        val bits = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key(pkg), -1)
        return if (bits < 0) null else SeatBoxes(bits and 1 != 0, bits and 2 != 0)
    }

    fun write(
        context: Context,
        pkg: String,
        boxes: SeatBoxes,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putInt(key(pkg), (if (boxes.countries) 1 else 0) or (if (boxes.devices) 2 else 0))
        }
    }

    /** Everything kept, as package to boxes. */
    fun all(context: Context): Map<String, SeatBoxes> =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .all
            .mapNotNull { (k, v) ->
                val bits = v as? Int ?: return@mapNotNull null
                if (!k.startsWith("pkg:") || bits < 0) return@mapNotNull null
                k.removePrefix("pkg:") to SeatBoxes(bits and 1 != 0, bits and 2 != 0)
            }.toMap()
}
