package com.eazyverse.testtrack.ui

import org.junit.Assert.*
import org.junit.Test

class CrewRunAppStateTest {
    private val app = CrewApp("app", "Demo", "demo.app", "Developer")
    private val seat = CrewSeat("app", "Demo", "Developer", "demo.app")
    private val actions = CrewActions({}, {}, {}, {}, {}, {})

    private fun state(
        app: CrewApp = this.app,
        actions: CrewActions = this.actions,
    ) = crewRunAppState(app, seat, CrewRunAppKind.REQUIRED, actions)

    @Test fun an_upload_in_progress_does_not_offer_another_capture_or_retry() {
        val sending = CrewActions({}, {}, {}, {}, {}, {}, sendingPicture = { true })
        val status = state(app.copy(pictureDue = true), sending)
        assertEquals("Uploading screenshot", status.text)
        assertNull(status.action)
        assertTrue(status.explanation!!.contains("count once the upload finishes"))
    }

    @Test fun failed_and_old_uploads_retry_the_saved_picture() {
        var retries = 0
        listOf(false, true).forEach { late ->
            val uploading =
                CrewActions(
                    {},
                    {},
                    {},
                    {},
                    {},
                    {},
                    sendingPicture = { true },
                    latePicture = { late },
                    picturesStuck = "Offline",
                    onRetryPictures = { retries++ },
                )
            val status = state(actions = uploading)
            assertEquals(if (late) "Upload waiting" else "Upload failed", status.text)
            assertEquals("Retry", status.action)
            assertTrue(status.explanation!!.contains("saved on this phone"))
            status.run()
        }
        assertEquals(2, retries)
    }

    @Test fun only_a_grace_update_keeps_open_available() {
        var opened = false
        val installed = CrewActions({ opened = true }, {}, {}, {}, {}, {}, localVersion = { 1L })
        val grace = state(app.copy(versionCode = 2), installed)
        assertEquals("Update", grace.action)
        assertEquals("Open", grace.alternate)
        grace.runAlternate()
        assertTrue(opened)
        assertNull(state(app.copy(versionCode = 2, updateRequired = true), installed).alternate)
    }

    @Test fun overdue_feedback_has_one_named_action_and_an_explanation() {
        var written = false
        val feedback = CrewActions({}, {}, {}, {}, {}, {}, onFeedback = { written = true })
        val status = state(app.copy(feedbackLate = true), feedback)
        assertEquals("Feedback overdue", status.text)
        assertEquals("Write", status.action)
        assertTrue(status.feedbackAction)
        assertNotNull(status.explanation)
        status.run()
        assertTrue(written)
    }

    @Test fun a_report_for_tonight_offers_withdraw_and_install_still_comes_first() {
        var withdrawn = ""
        val installed = CrewActions({}, {}, {}, {}, {}, {}, localVersion = { 1L }, onWithdrawReport = { withdrawn = it.id })
        val reported = state(app.copy(reported = true), installed)
        assertEquals("Reported for tonight", reported.text)
        assertEquals("Withdraw", reported.action)
        reported.run()
        assertEquals("app", withdrawn)
        val missing = CrewActions({}, {}, {}, {}, {}, {}, isInstalled = { false }, onWithdrawReport = { withdrawn = "no" })
        assertEquals("Install", state(app.copy(reported = true), missing).action)
    }

    @Test fun optional_apps_keep_open_when_feedback_is_late() {
        val status = crewRunAppState(app.copy(feedbackLate = true), seat, CrewRunAppKind.OPTIONAL, actions)
        assertEquals("Open", status.action)
        assertFalse(status.feedbackAction)
    }
}
