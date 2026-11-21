package com.eazyverse.testtrack.viewmodel

import com.eazyverse.testtrack.net.ApiMyPrivateTest
import com.eazyverse.testtrack.net.ApiPrivateResults
import com.eazyverse.testtrack.net.ApiPrivateTester

/*
 * Private testers, as the screens draw them: an ask made of the reader, and the people the reader
 * asked about an app of their own. Pure values, read from the wire, so all of it tests on a desk.
 */

/** Where one ask stands. Only [INVITED] and [ACCEPTED] are still open. */
enum class PrivateState {
    INVITED,
    ACCEPTED,
    DECLINED,
    REMOVED,
    LEFT,
    ENDED,
    ;

    val open: Boolean get() = this == INVITED || this == ACCEPTED

    companion object {
        fun of(wire: String) =
            when (wire) {
                "accepted" -> ACCEPTED
                "declined" -> DECLINED
                "removed" -> REMOVED
                "left" -> LEFT
                "ended" -> ENDED
                else -> INVITED
            }
    }
}

/** One ask made of the reader: an app to open while its owner's crew runs. */
data class PrivateTest(
    val id: String,
    val seatId: String,
    val crewId: String,
    val state: PrivateState,
    val appName: String,
    /** Blank until the reader accepts: only somebody testing it needs to find it on Play. */
    val packageName: String = "",
    val icon: String = "",
    val notes: String = "",
    val ownerName: String = "",
    val crewName: String = "",
    /** The reader's own night of the run, and the first that counts; both 0 before accepting. */
    val day: Int = 0,
    val firstNight: Int = 0,
    val runDays: Int = 14,
    val openedToday: Boolean = false,
    val openedDays: Int = 0,
) {
    /** Whole days until the crew's run is over. */
    val daysLeft: Int get() = (runDays - day).coerceAtLeast(0)

    /** The nights so far it could have been opened on, tonight included. */
    val nightsIn: Int get() = if (firstNight <= 0 || day < firstNight) 0 else minOf(day, runDays) - firstNight + 1

    /** Its crew's last night is behind it: what stopped it, when it stops with nothing said. */
    val runOver: Boolean get() = day >= runDays
}

/** One of a private tester's own nights. */
data class PrivateNight(
    val day: Int,
    val opened: Boolean,
)

/** One person the reader asked to test an app of theirs. */
data class PrivateTester(
    val id: String,
    val seatId: String,
    val name: String,
    val photo: String = "",
    val email: String = "",
    val state: PrivateState = PrivateState.INVITED,
    val endReason: String = "",
    /** The night they accepted on, and their own night now; 0 until they accept. */
    val firstNight: Int = 0,
    val day: Int = 0,
    val nights: List<PrivateNight> = emptyList(),
    val openedDays: Int = 0,
) {
    /** Opened it on their own night tonight. */
    val openedTonight: Boolean get() = nights.any { it.day == day && it.opened }
}

/** Everyone the reader asked about one app, oldest first. */
data class PrivateResults(
    val seatId: String,
    val appName: String,
    val runDays: Int,
    val testers: List<PrivateTester>,
) {
    /** The list as drawn: somebody taken off leaves it with their nights. */
    val shown: List<PrivateTester> get() = testers.filter { it.state != PrivateState.REMOVED }
    val testing: Int get() = testers.count { it.state == PrivateState.ACCEPTED }
    val invited: Int get() = testers.count { it.state == PrivateState.INVITED }
}

/** Why an invite was refused, said in the sheet beside the address it was refused for. */
data class InviteRefusal(
    val email: String,
    /** no_account, own_account, already_testing or declined_before. */
    val code: String,
    val message: String,
) {
    /** No account at all: the field itself is wrong. The rest are about a person who exists. */
    val noAccount: Boolean get() = code == NO_ACCOUNT

    companion object {
        const val NO_ACCOUNT = "no_account"
        const val OWN_ACCOUNT = "own_account"
        const val ALREADY_TESTING = "already_testing"
        const val DECLINED_BEFORE = "declined_before"

        /** The refusals the sheet says beside the address; any other goes in a snack. */
        val INLINE = setOf(NO_ACCOUNT, OWN_ACCOUNT, ALREADY_TESTING, DECLINED_BEFORE)
    }
}

/** An address shaped like one: something, an @, and a dotted domain. Whether it is an account is the server's. */
fun looksLikeEmail(text: String): Boolean = EMAIL.matches(text.trim())

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** The counts line on the owner's row into the page: who tests, who has not answered. */
fun privateCountsLine(results: PrivateResults?): String {
    val testing = results?.testing ?: 0
    val invited = results?.invited ?: 0
    val parts = listOfNotNull("$testing testing".takeIf { testing > 0 }, "$invited invited".takeIf { invited > 0 })
    return if (parts.isEmpty()) "Add people by email. Only you see them." else parts.joinToString(", ") + ". Only you see them."
}

internal fun ApiMyPrivateTest.toTest() =
    PrivateTest(
        id = id,
        seatId = seatId,
        crewId = crewId,
        state = PrivateState.of(state),
        appName = appName,
        packageName = packageName,
        icon = icon,
        notes = notes,
        ownerName = ownerName,
        crewName = crewName,
        day = day,
        firstNight = firstNight,
        runDays = runDays,
        openedToday = openedToday,
        openedDays = openedDays,
    )

internal fun ApiPrivateTester.toTester() =
    PrivateTester(
        id = id,
        seatId = seatId,
        name = name.ifBlank { email },
        photo = photo,
        email = email,
        state = PrivateState.of(state),
        endReason = endReason,
        firstNight = firstNight,
        day = day,
        nights = nights.map { PrivateNight(it.day, it.opened) },
        openedDays = openedDays,
    )

internal fun ApiPrivateResults.toResults() = PrivateResults(seatId, appName, runDays, testers.map { it.toTester() })

/**
 * The reader's asks after a fresh read: what the server says, and any accepted one that has
 * dropped out of it since the last read, kept as [PrivateState.ENDED] so its tile can say testing
 * stopped rather than vanish. One the reader stopped themselves is gone already.
 */
internal fun mergeMine(
    before: List<PrivateTest>?,
    fresh: List<PrivateTest>,
): List<PrivateTest> {
    val ids = fresh.map { it.id }.toSet()
    val stopped =
        before
            .orEmpty()
            .filter { it.id !in ids && (it.state == PrivateState.ACCEPTED || it.state == PrivateState.ENDED) }
            .map { it.copy(state = PrivateState.ENDED, openedToday = false) }
    return fresh + stopped
}
