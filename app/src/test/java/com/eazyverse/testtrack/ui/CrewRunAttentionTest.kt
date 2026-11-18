package com.eazyverse.testtrack.ui

import org.junit.Assert.*
import org.junit.Test

class CrewRunAttentionTest {
    private val app = CrewApp("ready", "Demo", "demo.app", "Tester")
    private val actions = CrewActions({}, {}, {}, {}, {}, {}, localVersion = { 1L })

    @Test fun ready_and_settled_apps_do_not_create_an_update() {
        assertNull(crewRunAttention(listOf(app, app.copy(id = "done", done = true), app.copy(id = "report", reported = true)), actions))
    }

    @Test fun a_grace_update_stays_ready() {
        assertNull(crewRunAttention(listOf(app.copy(versionCode = 2, updateRequired = false)), actions))
    }

    @Test fun feedback_summary_counts_only_distinct_blocked_apps() {
        val blocked = app.copy(id = "late", feedbackLate = true)
        val note = crewRunAttention(listOf(app, blocked, blocked), actions)!!
        assertEquals(CrewRunAttentionKind.FEEDBACK, note.kind)
        assertEquals(listOf(blocked), note.apps)
        assertEquals("1 app needs feedback", note.title)
    }

    @Test fun required_updates_keep_the_actual_apps_for_the_picker() {
        val apps =
            listOf(
                app.copy(id = "one", versionCode = 2, updateRequired = true),
                app.copy(id = "two", versionCode = 3, updateRequired = true),
            )
        val note = crewRunAttention(apps, actions)!!
        assertEquals(CrewRunAttentionKind.UPDATE, note.kind)
        assertEquals(apps, note.apps)
    }

    @Test fun mixed_blockers_do_not_include_ready_apps() {
        val feedback = app.copy(id = "late", feedbackLate = true)
        val update = app.copy(id = "old", versionCode = 2, updateRequired = true)
        val note = crewRunAttention(listOf(app, feedback, update), actions)!!
        assertEquals(CrewRunAttentionKind.MIXED, note.kind)
        assertEquals(listOf(feedback, update), note.apps)
    }
}
