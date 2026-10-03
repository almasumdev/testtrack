package com.eazyverse.testtrack.domain

import com.eazyverse.testtrack.domain.UsageSessions.Event
import com.eazyverse.testtrack.domain.UsageSessions.Kind.PAUSED
import com.eazyverse.testtrack.domain.UsageSessions.Kind.RESUMED
import com.eazyverse.testtrack.domain.UsageSessions.Kind.SCREEN_OFF
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every event ordering a phone has been seen (or is known) to produce, with the time it should
 * come to. Seconds throughout, for reading; the function works in milliseconds.
 */
class UsageSessionsTest {
    private val app = "com.example.app"
    private val other = "com.other"

    private fun on(
        at: Int,
        activity: String = "Main",
        pkg: String = app,
    ) = Event(RESUMED, pkg, activity, at * 1000L)

    private fun off(
        at: Int,
        activity: String = "Main",
        pkg: String = app,
    ) = Event(PAUSED, pkg, activity, at * 1000L)

    private fun dark(at: Int) = Event(SCREEN_OFF, "android", "", at * 1000L)

    private fun seconds(
        events: List<Event>,
        since: Int = 0,
        now: Int = 1000,
    ) = UsageSessions.foregroundMs(events, app, since * 1000L, now * 1000L) / 1000

    @Test
    fun a_plain_visit_counts_from_open_to_close() {
        assertEquals(30, seconds(listOf(on(10), off(40))))
    }

    @Test
    fun a_visit_still_open_runs_to_now() {
        assertEquals(35, seconds(listOf(on(10)), now = 45))
    }

    @Test
    fun splash_to_main_in_order_counts_the_whole_visit() {
        val events = listOf(on(10, "Splash"), off(12, "Splash"), on(12, "Main"), off(50, "Main"))
        assertEquals(40, seconds(events))
    }

    @Test
    fun splash_to_main_with_main_resumed_first_counts_the_whole_visit() {
        // The ordering that used to lose everything after the splash: main resumes, then the
        // splash pauses, and the old pairing closed the visit there.
        val events = listOf(on(10, "Splash"), on(12, "Main"), off(13, "Splash"), off(50, "Main"))
        assertEquals(40, seconds(events))
    }

    @Test
    fun splash_to_main_still_open_counts_to_now() {
        val events = listOf(on(10, "Splash"), on(12, "Main"), off(13, "Splash"))
        assertEquals(35, seconds(events, now = 45))
    }

    @Test
    fun a_visit_already_open_when_the_window_starts_counts_from_the_window() {
        assertEquals(30, seconds(listOf(on(5), off(50)), since = 20))
    }

    @Test
    fun time_before_the_window_never_counts() {
        assertEquals(0, seconds(listOf(on(5), off(15)), since = 20))
    }

    @Test
    fun the_screen_going_off_ends_the_visit() {
        assertEquals(20, seconds(listOf(on(10), dark(30), off(300))))
    }

    @Test
    fun coming_back_after_the_screen_was_off_counts_again() {
        assertEquals(30, seconds(listOf(on(10), dark(30), on(100), off(110))))
    }

    @Test
    fun several_visits_add_up() {
        assertEquals(25, seconds(listOf(on(10), off(20), on(30), off(45))))
    }

    @Test
    fun other_apps_do_not_count() {
        assertEquals(10, seconds(listOf(on(10, pkg = other), off(90, pkg = other), on(100), off(110))))
    }

    @Test
    fun a_pause_with_no_open_before_it_counts_nothing() {
        // A lone pause from a visit that started long before anything was read.
        assertEquals(0, seconds(listOf(off(40))))
    }

    @Test
    fun events_arriving_out_of_order_are_sorted_first() {
        assertEquals(30, seconds(listOf(off(40), on(10))))
    }

    @Test
    fun a_window_that_has_not_started_is_zero() {
        assertEquals(0, seconds(listOf(on(10), off(40)), since = 50, now = 40))
    }
}
