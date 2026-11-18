package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrewDemoTest {
    @Test
    fun only_seeded_member_packages_receive_a_preview_copy() {
        assertEquals(HereCopy(true, true, Long.MAX_VALUE), demoCrewCopy("dev.seedapps.member12", enabled = true))
        assertEquals(HereCopy(true, true, 1L), demoCrewCopy("dev.seedapps.member100", enabled = true))
        listOf("", "dev.seedapps.member", "dev.seedapps.member1.real", "dev.seedapps.readyfb", "com.eazyverse.bluefy").forEach {
            assertNull(demoCrewCopy(it, enabled = true))
        }
    }

    @Test
    fun disabled_preview_never_overrides_an_install() {
        assertNull(demoCrewCopy("dev.seedapps.member12", enabled = false))
    }

    @Test
    fun preview_keeps_completed_reported_late_and_sending_apps_out_of_open_all() {
        val ready = CrewApp("ready", "Demo", "dev.seedapps.member12", "Tester", versionCode = 10, updateRequired = true)
        val apps =
            listOf(
                ready,
                ready.copy(id = "done", done = true),
                ready.copy(id = "reported", reported = true),
                ready.copy(id = "late", feedbackLate = true),
                ready.copy(id = "sending"),
            )
        val selected = openAllOf(apps, { demoCrewCopy(it, enabled = true)!! }, { it.id == "sending" })
        assertEquals(listOf("ready"), selected.map { it.id })
        assertTrue(selected.single().updateRequired)
    }
}
