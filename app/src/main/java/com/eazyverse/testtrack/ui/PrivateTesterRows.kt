package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.domain.pluralOf
import com.eazyverse.testtrack.viewmodel.PrivateState
import com.eazyverse.testtrack.viewmodel.PrivateTest
import com.eazyverse.testtrack.viewmodel.PrivateTester

/*
 * Private testers in the shapes the screens draw: the owner's grid and tiles, built on the crew's
 * own [CrewTester], and the lines the person asked reads. Pure values, tested on a desk.
 *
 * No night is owed here, so nothing is ever a miss: a night opened is the solid square, a night
 * not opened is the grey one a crew uses for a night not owed.
 */

/** One square per night of the run; none at all for somebody who has not said yes. */
internal fun privateSquares(
    tester: PrivateTester,
    runDays: Int,
): List<Square> {
    if (tester.firstNight <= 0) return emptyList()
    val opened =
        tester.nights
            .filter { it.opened }
            .map { it.day }
            .toSet()
    val listed = tester.nights.map { it.day }.toSet()
    val testing = tester.state == PrivateState.ACCEPTED
    return (1..runDays).map { day ->
        when {
            day < tester.firstNight -> Square.BEFORE
            day in opened -> Square.PICTURE
            testing && day == tester.day -> Square.TONIGHT
            day in listed -> Square.NOT_OWED
            else -> Square.TO_COME
        }
    }
}

/** The short line under their name on the owner's list. */
internal fun privateLineOf(tester: PrivateTester): Pair<String, Tone> =
    when (tester.state) {
        PrivateState.INVITED -> "Invited, no answer yet" to Tone.QUIET
        PrivateState.DECLINED -> "Declined your invite" to Tone.BAD
        PrivateState.LEFT -> "Stopped testing" to Tone.QUIET
        PrivateState.ENDED -> "Testing ended" to Tone.QUIET
        PrivateState.REMOVED -> "Removed" to Tone.QUIET
        PrivateState.ACCEPTED -> if (tester.openedTonight) "Opened tonight" to Tone.OK else "Not opened tonight" to Tone.QUIET
    }

/** A private tester as the grid and the tile draw a crew's tester. */
internal fun privateTesterOf(
    tester: PrivateTester,
    runDays: Int,
): CrewTester {
    val (line, tone) = privateLineOf(tester)
    return CrewTester(
        id = tester.id,
        name = tester.name,
        photo = tester.photo,
        email = tester.email,
        day = tester.day,
        days = tester.openedDays,
        line = line,
        tone = tone,
        squares = privateSquares(tester, runDays),
        openedTonight = tester.openedTonight,
        // Not testing now: their line says it all, with no day beside it.
        over = tester.state != PrivateState.ACCEPTED,
        firstNight = tester.firstNight.coerceAtLeast(1),
    )
}

/** Beside the name: the nights they have been in since they said yes; blank before. */
internal fun privateNightsLabel(tester: PrivateTester): String =
    tester.nights.size.let { if (it > 0) "$it ${pluralOf(it, "night")}" else "" }

/** The owner's list in the order it reads: testing (opened tonight first), asked, then the rest. */
internal fun privateOrder(testers: List<PrivateTester>): List<PrivateTester> =
    testers.sortedWith(
        compareBy(
            {
                when (it.state) {
                    PrivateState.ACCEPTED -> 0
                    PrivateState.INVITED -> 1
                    else -> 2
                }
            },
            { !it.openedTonight },
        ),
    )

/** The grid's chip: how many of those testing now opened the app tonight. */
internal fun privateChipOf(testers: List<PrivateTester>): Pair<String, Tone> {
    val testing = testers.filter { it.state == PrivateState.ACCEPTED }
    if (testing.isEmpty()) return "Nobody testing now" to Tone.QUIET
    return "${testing.count { it.openedTonight }} of ${testing.size} opened tonight" to Tone.ACCENT
}

/** When testing ends: with the crew, said in days. */
internal fun privateEndsLine(
    crewName: String,
    daysLeft: Int,
): String =
    if (daysLeft <= 0) {
        "Testing ends with $crewName after tonight."
    } else {
        "Testing ends with $crewName in $daysLeft ${pluralOf(daysLeft, "day")}."
    }

/** Why an ask the reader said yes to stopped, as far as the phone can tell. */
internal fun PrivateTest.stoppedLine(): String = if (runOver) "$crewName ended, so testing stopped" else "Testing for $ownerName stopped"

/** How it is going, for the person testing: nights opened, and who sees them. */
internal fun PrivateTest.openedLine(): String =
    if (nightsIn <= 0) {
        "Open it tonight and $ownerName sees it. Nobody else does."
    } else {
        "You opened it on $openedDays of $nightsIn ${pluralOf(nightsIn, "night")}. $ownerName sees each one, nobody else does."
    }
