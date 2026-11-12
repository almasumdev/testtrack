package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A My apps tile keeps to the 52dp of its icon: the text column is never taller, so a long name or
 * a long crew name cannot stretch the tile, and no tile is shorter than 76dp.
 */
@RunWith(AndroidJUnit4::class)
class AppTileHeightTest {
    @get:Rule
    val compose = createComposeRule()

    private val long = "An app with a name far too long to fit on the two lines a tile gives it, however it is read"

    private fun app(
        name: String,
        crew: AppCrewStatus? = null,
        feedback: AppFeedbackStatus? = null,
        verifiedBy: String? = "play",
        live: Boolean = false,
    ) = AppEntry(
        id = name + crew?.state + feedback?.state,
        pkg = "com.example.app",
        name = name,
        crew = crew,
        feedback = feedback,
        verifiedBy = verifiedBy,
        live = live,
    )

    @Test
    fun the_text_column_fits_the_icon_in_every_state() {
        val crewName = "A crew with a very long name indeed"
        val apps =
            listOf(
                app("Lapse"),
                app(long),
                app(long, crew = AppCrewStatus("testing", "c", crewName, day = 30, runDays = 30)),
                app("Lapse", crew = AppCrewStatus("starting", "c", crewName)),
                app(long, crew = AppCrewStatus("rejected", "c", crewName)),
                app("Lapse", crew = AppCrewStatus("removed", "c", crewName), verifiedBy = null),
                app(long, feedback = AppFeedbackStatus("collecting", "p", 117, 150, toCheck = 120), live = true),
                app("Lapse", feedback = AppFeedbackStatus("done", "p", 5, 5), live = true),
                app("Lapse", feedback = AppFeedbackStatus("closed", "p", 1, 5, unlisted = true)),
                app(long, live = true, verifiedBy = null),
            )
        compose.setContent {
            TestTrackTheme {
                Column {
                    apps.forEach { AppCard(it, onOpen = {}) }
                }
            }
        }
        val columns = compose.onAllNodesWithTag(APP_TEXT_TAG)
        val tiles = compose.onAllNodesWithTag(APP_TILE_TAG)
        assertEquals(apps.size, columns.fetchSemanticsNodes().size)
        for (i in apps.indices) {
            val column = columns[i].getUnclippedBoundsInRoot().height
            assertTrue("column $i is $column", column <= 52.dp)
            val tile = tiles[i].getUnclippedBoundsInRoot().height
            assertEquals("tile $i is $tile", 76.dp, tile)
        }
    }
}
