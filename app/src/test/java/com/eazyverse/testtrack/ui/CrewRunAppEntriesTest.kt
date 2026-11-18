package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CrewRunAppEntriesTest {
    private val ready = CrewApp("ready", "Ready", "demo.ready", "Tester")
    private val done = ready.copy(id = "done", done = true)
    private val optional = ready.copy(id = "optional")
    private val left = ready.copy(id = "left", packageName = "demo.left")

    @Test
    fun completed_apps_keep_their_place_in_the_same_list() {
        val crew = Crew("c", "Crew", CrewPhase.RUN, apps = listOf(done, ready))
        assertEquals(listOf("done", "ready"), crewRunAppEntries(crew) { true }.map { it.app.id })
    }

    @Test
    fun each_seat_appears_once_and_retains_its_obligation() {
        val crew = Crew("c", "Crew", CrewPhase.RUN, apps = listOf(ready, done), optional = listOf(ready, optional), left = listOf(left))
        val entries = crewRunAppEntries(crew) { true }
        assertEquals(listOf("ready", "done", "optional", "left"), entries.map { it.app.id })
        assertEquals(
            listOf(CrewRunAppKind.REQUIRED, CrewRunAppKind.REQUIRED, CrewRunAppKind.OPTIONAL, CrewRunAppKind.LEFT),
            entries.map {
                it.kind
            },
        )
    }

    @Test
    fun an_uninstalled_app_that_left_disappears_but_a_required_app_stays() {
        val crew = Crew("c", "Crew", CrewPhase.RUN, apps = listOf(ready), left = listOf(left))
        assertEquals(listOf("ready"), crewRunAppEntries(crew) { false }.map { it.app.id })
    }

    @Test
    fun leaving_an_uninstall_prompt_keeps_the_row_until_the_package_is_removed() {
        val crew = Crew("c", "Crew", CrewPhase.RUN, apps = listOf(ready), left = listOf(left))
        val installed = mutableSetOf(left.packageName)

        fun visibleIds() = crewRunAppEntries(crew) { it in installed }.map { it.app.id }

        assertEquals(listOf("ready", "left"), visibleIds())
        // Returning from a cancelled Android prompt leaves installation unchanged.
        assertEquals(listOf("ready", "left"), visibleIds())
        installed.remove(left.packageName)
        assertEquals(listOf("ready"), visibleIds())
    }
}
