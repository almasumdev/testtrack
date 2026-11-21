package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Uninstall all on a finished crew: when it opens, how it is worded, and how a run moves. */
class CrewUninstallTest {
    private val utc = ZoneId.of("UTC")

    private fun record(
        doneAt: String? = null,
        endedAt: String? = null,
    ) = CrewRecord(crewId = "c1", doneAt = doneAt, endedAt = endedAt)

    private fun at(iso: String) = Instant.parse(iso)

    private fun app(
        pkg: String,
        left: Boolean = false,
    ) = RecordApp(name = pkg.substringAfterLast('.'), packageName = pkg, icon = "", left = left)

    private fun run(vararg pkgs: String) = beginUninstall("c1", pkgs.toList())!!

    // The unlock date

    @Test
    fun opensTwoDaysAfterTheCrewFinished() {
        val r = record(doneAt = "2026-11-22T00:00:00Z", endedAt = "2026-11-21T00:00:00Z")
        assertEquals(at("2026-11-24T00:00:00Z"), uninstallOpensAt(r))
    }

    @Test
    fun fallsBackToTheEndOnlyWhenDoneAtIsMissing() {
        assertEquals(at("2026-11-23T00:00:00Z"), uninstallOpensAt(record(endedAt = "2026-11-21T00:00:00Z")))
        assertEquals(at("2026-11-24T00:00:00Z"), uninstallOpensAt(record(doneAt = "2026-11-22T00:00:00Z", endedAt = "bad")))
        assertNull(uninstallOpensAt(record()))
    }

    @Test
    fun isLockedBeforeAndOpenFromTheMoment() {
        val r = record(doneAt = "2026-11-22T00:00:00Z")
        assertFalse(uninstallOpen(r, at("2026-11-23T23:59:59Z")))
        assertTrue(uninstallOpen(r, at("2026-11-24T00:00:00Z")))
        assertTrue(uninstallOpen(r, at("2026-12-30T00:00:00Z")))
    }

    @Test
    fun aRecordWithNoKnownEndIsNotHeldBack() {
        assertTrue(uninstallOpen(record(), at("2026-11-22T00:00:00Z")))
        assertNull(uninstallOpensLine(record(), at("2026-11-22T00:00:00Z"), utc))
    }

    // Its words

    @Test
    fun theLockedLineNamesTheDateAndTheDays() {
        val r = record(doneAt = "2026-11-22T00:00:00Z")
        assertEquals("Opens Nov 24, in 2 days", uninstallOpensLine(r, at("2026-11-22T10:00:00Z"), utc))
        assertEquals("Opens Nov 24, in 1 day", uninstallOpensLine(r, at("2026-11-23T10:00:00Z"), utc))
    }

    @Test
    fun theLastDayIsToday() {
        val r = record(doneAt = "2026-11-22T18:00:00Z")
        assertEquals("Opens today", uninstallOpensLine(r, at("2026-11-24T09:00:00Z"), utc))
    }

    @Test
    fun noLineOnceItIsOpen() {
        val r = record(doneAt = "2026-11-22T00:00:00Z")
        assertNull(uninstallOpensLine(r, at("2026-11-24T00:00:00Z"), utc))
    }

    // What is left

    @Test
    fun countsOnlyAppsStillOnThePhoneOnce() {
        val apps = listOf(app("a.one"), app("a.two"), app("a.three"), app("a.one"), app(""))
        val here = setOf("a.one", "a.three")
        assertEquals(listOf("a.one", "a.three"), appsLeft(apps) { it in here }.map { it.packageName })
    }

    @Test
    fun noAppsLeftIsAnEmptyList() {
        assertTrue(appsLeft(listOf(app("a.one"))) { false }.isEmpty())
    }

    // A run

    @Test
    fun aRunWithNothingToRemoveDoesNotStart() {
        assertNull(beginUninstall("c1", emptyList()))
    }

    @Test
    fun aRunAsksAboutTheFirstApp() {
        val run = run("a", "b", "c")
        assertEquals("a", run.current)
        assertEquals(UninstallStep.ASKING, run.step)
        assertEquals(3, run.total)
    }

    @Test
    fun anAppThatIsGoneIsTickedOffThenTheNextIsAsked() {
        val ticked = run("a", "b").settled("a", gone = true)
        assertEquals(UninstallStep.TICK, ticked.step)
        assertEquals(setOf("a"), ticked.removed)
        val next = ticked.advanced { it == "b" }
        assertEquals("b", next.current)
        assertEquals(UninstallStep.ASKING, next.step)
    }

    @Test
    fun anAppStillThereMeansTheyKeptItAndTheRunPauses() {
        val paused = run("a", "b").settled("a", gone = false)
        assertEquals(UninstallStep.KEPT, paused.step)
        assertEquals(setOf("a"), paused.kept)
        assertEquals("a", paused.current)
        val next = paused.advanced { true }
        assertEquals("b", next.current)
        assertEquals(UninstallStep.ASKING, next.step)
    }

    @Test
    fun theRunEndsWhenNoneIsLeftToAsk() {
        val end = run("a").settled("a", gone = false).advanced { true }
        assertEquals(UninstallStep.ENDED, end.step)
        assertNull(end.current)
    }

    @Test
    fun anAppRemovedSomeOtherWayIsTickedOffWithoutAsking() {
        val next = run("a", "b", "c").settled("a", gone = true).advanced { it == "c" }
        assertEquals("c", next.current)
        assertEquals(setOf("a", "b"), next.removed)
    }

    @Test
    fun stoppingEndsTheRunAndALateAnswerOnlyCountsTheRemoval() {
        val stopped = run("a", "b").stopped()
        assertEquals(UninstallStep.ENDED, stopped.step)
        assertEquals(stopped, stopped.settled("a", gone = false))
        val late = stopped.settled("a", gone = true)
        assertEquals(setOf("a"), late.removed)
        assertEquals(UninstallStep.ENDED, late.step)
    }

    @Test
    fun theRestCarriesOnFromTheCountOfTheFirstRun() {
        val first = run("a", "b", "c").settled("a", gone = true).advanced { true }.settled("b", gone = false)
        val rest = beginUninstall("c1", listOf("b", "c"), earlier = first.removed)!!
        assertEquals(listOf("a", "b", "c"), rest.order)
        assertEquals(setOf("a"), rest.removed)
        assertEquals("b", rest.current)
        assertEquals(3, rest.total)
    }

    // The card and the rows

    @Test
    fun theCardFollowsTheRun() {
        val run = run("a", "b")
        assertEquals(UninstallCard.LOCKED, uninstallCard(open = false, run = null, left = 2))
        assertEquals(UninstallCard.READY, uninstallCard(open = true, run = null, left = 2))
        assertEquals(UninstallCard.NONE_LEFT, uninstallCard(open = true, run = null, left = 0))
        assertEquals(UninstallCard.NONE_LEFT, uninstallCard(open = false, run = null, left = 0))
        assertEquals(UninstallCard.RUNNING, uninstallCard(true, run, 2))
        assertEquals(UninstallCard.KEPT_ONE, uninstallCard(true, run.settled("a", false), 2))
        assertEquals(UninstallCard.ALL_REMOVED, uninstallCard(true, run.stopped(), 0))
        assertEquals(UninstallCard.PARTIAL, uninstallCard(true, run.stopped(), 1))
    }

    @Test
    fun theMeterHasASquarePerAppInTheRun() {
        val paused = run("a", "b", "c", "d").settled("a", true).advanced { it != "a" }.settled("b", false)
        assertEquals(
            listOf(UninstallMark.REMOVED, UninstallMark.KEPT, UninstallMark.TODO, UninstallMark.TODO),
            uninstallMarks(paused),
        )
        assertEquals(listOf(UninstallMark.NOW, UninstallMark.TODO), uninstallMarks(run("a", "b")))
    }

    @Test
    fun rowsNotOnThePhoneAreSeparateAndNotCounted() {
        val apps = listOf(app("a"), app("b"), app("c", left = true))
        val rows = uninstallRows(apps, open = true, installed = { it != "b" }, run = null)
        assertEquals(listOf("a", "c"), rows.live.map { it.app.packageName })
        assertEquals(listOf("b"), rows.gone.map { it.app.packageName })
        assertEquals(UninstallRowState.EARLIER, rows.gone.single().state)
        assertTrue(rows.live.all { it.state == UninstallRowState.OPEN })
    }

    @Test
    fun lockedRowsHaveNoUninstall() {
        val rows = uninstallRows(listOf(app("a")), open = false, installed = { true }, run = null)
        assertEquals(UninstallRowState.LOCKED, rows.live.single().state)
    }

    @Test
    fun rowsInARunAreRemovedAskingOrWaiting() {
        val apps = listOf(app("a"), app("b"), app("c"))
        val going = run("a", "b", "c").settled("a", true).advanced { it != "a" }
        val rows = uninstallRows(apps, true, { it != "a" }, going).live
        assertEquals(listOf("a", "b", "c"), rows.map { it.app.packageName })
        assertEquals(
            listOf(UninstallRowState.REMOVED, UninstallRowState.ASKING, UninstallRowState.WAITING),
            rows.map { it.state },
        )
    }

    @Test
    fun afterTheRunWhatIsStillHereComesFirst() {
        val apps = listOf(app("a"), app("b"), app("c"))
        val done =
            run("a", "b", "c")
                .settled("a", true)
                .advanced { it != "a" }
                .settled("b", false)
                .advanced { it != "a" }
                .settled("c", true)
                .stopped()
        val list = uninstallRows(apps, true, { it == "b" }, done)
        assertEquals(listOf("b", "a", "c"), list.live.map { it.app.packageName })
        assertEquals(UninstallRowState.KEPT, list.live.first().state)
        assertNotNull(list.live.firstOrNull { it.state == UninstallRowState.REMOVED })
    }
}
