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

/** The small mark before a tile's status line: ready, a step still to do, locked, or waiting. */
enum class SeatMark { READY, TODO, LOCK, WAIT }

/** Why an app can't take a seat now. */
enum class SeatBlockKind { LIVE, WAITING, APPEAL }

data class SeatBlock(
    val kind: SeatBlockKind,
    val crew: String = "",
) {
    /** The mark before [line]: a lock for a live app, an hourglass while it waits. */
    val mark: SeatMark get() = if (kind == SeatBlockKind.LIVE) SeatMark.LOCK else SeatMark.WAIT

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

/** What an app that can be picked still needs: Play showing it to the group, or nothing. */
enum class SeatNeed { GROUP, NONE }

fun seatNeed(app: AppEntry): SeatNeed = if (app.verified) SeatNeed.NONE else SeatNeed.GROUP

/** The mark before [line]: a tick once ready, a dashed ring while a step is left. */
val SeatNeed.mark: SeatMark get() = if (this == SeatNeed.NONE) SeatMark.READY else SeatMark.TODO

/** The status line a pickable row says: what it needs next, or that it is ready. */
fun SeatNeed.line(): String =
    when (this) {
        SeatNeed.GROUP -> "Add the group to its closed track"
        SeatNeed.NONE -> "Ready to go in"
    }

/**
 * Why a box matters and where to fix it, said in the callout from the box's info button.
 * The "how" lines alternate plain and bold, starting plain, so the names to look for stand out.
 */
object SeatBoxCopy {
    const val COUNTRIES = "Available in all countries"
    const val DEVICES = "Not limited to some devices"
    const val COUNTRIES_WHY = "Members in other countries can't install it. If two of them report that, you lose your seat."
    const val DEVICES_WHY = "Members on excluded phones can't install it. If two of them report that, you lose your seat."
    val COUNTRIES_HOW = arrayOf("In Play Console, open ", "Closed testing", ", then ", "Countries/regions", ", and add all countries.")
    val DEVICES_HOW = arrayOf("In Play Console, open ", "Device catalog", " and remove excluded devices.")
}

/** The part of the seat page a hint or a refused seat is about, so the page can scroll to it. NAME also puts the cursor in the name. */
enum class SeatSpot { GROUP, DETAILS, NAME, BOXES }

/** How the line over the button looks: a step to do, something wrong, all done, or working. */
enum class SeatHintKind { TODO, WARN, READY, BUSY }

/** The line over the seat button: what it says, how it looks, and where tapping it goes (null for none). */
data class SeatHint(
    val kind: SeatHintKind,
    val text: String,
    val spot: SeatSpot?,
)

/**
 * The first thing still missing before the seat can be taken, in the order the page shows it: the
 * group, the name, the sign-in, the tix, the appeal, the boxes. When nothing is missing it says what
 * the seat takes from the balance. While the seat is being checked on Play that is all it says.
 *
 * [tix] is null until the server has said; [shortServer] is the server saying tix are short even if
 * the balance on this phone did not. [also] is the sign-in's free note, which stands in for a
 * username and password, as the button's own rule allows.
 */
fun seatHint(
    groupReady: Boolean,
    groupFailed: Boolean,
    name: String,
    signInNeeded: Boolean,
    user: String,
    pass: String,
    also: String = "",
    tix: Int?,
    shortServer: Boolean,
    held: Boolean,
    boxesReady: Boolean,
    busy: Boolean,
    cost: Int,
): SeatHint {
    val signInOpen = signInNeeded && also.isBlank()
    return when {
        busy -> SeatHint(SeatHintKind.BUSY, "Checking it on Play the way the crew will see it", null)
        !groupReady && groupFailed -> SeatHint(SeatHintKind.WARN, "Play doesn't show it to the group yet", SeatSpot.GROUP)
        !groupReady -> SeatHint(SeatHintKind.TODO, "Verify the testers group first", SeatSpot.GROUP)
        name.isBlank() -> SeatHint(SeatHintKind.TODO, "Give it a name the crew sees", SeatSpot.NAME)
        signInOpen && user.isBlank() -> SeatHint(SeatHintKind.TODO, "Add the test account's username", SeatSpot.DETAILS)
        signInOpen && pass.isBlank() -> SeatHint(SeatHintKind.TODO, "Add the test account's password", SeatSpot.DETAILS)
        tix != null && tix < cost -> SeatHint(SeatHintKind.WARN, "You need ${cost - tix} more tix", SeatSpot.DETAILS)
        shortServer -> SeatHint(SeatHintKind.WARN, "Not enough tix for a seat", SeatSpot.DETAILS)
        held -> SeatHint(SeatHintKind.WARN, "Wait for the team to answer your appeal", SeatSpot.DETAILS)
        !boxesReady -> SeatHint(SeatHintKind.TODO, "Tick both Play Console boxes", SeatSpot.BOXES)
        else -> SeatHint(SeatHintKind.READY, "$cost tix come off your ${tix ?: "balance"}", null)
    }
}

private const val PLAY_DOESNT_SHOW = "Play doesn't show it to the group yet. A new rollout can take a few hours to show."

/**
 * The line under the group address, only when the check failed: when Play was last asked and what it
 * said. [message] is the server's own words after a refused seat, which replace the stock sentence.
 * Null when there is nothing wrong to say, so the page shows no line at all.
 */
fun lastCheckLine(
    checkedAt: Long?,
    ok: Boolean?,
    now: Long,
    message: String? = null,
): String? {
    if (message != null) return message
    if (ok != false) return null
    val ago = checkedAt?.let { "Checked ${agoWords(now - it)}: " }.orEmpty()
    return "$ago$PLAY_DOESNT_SHOW"
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
