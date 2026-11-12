package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHeightIsEqualTo
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
 * A My reviews ticket is the 76dp of a Requests ticket in every state: its text column is never
 * taller than the 52dp mark beside it, so a long name or a long line cannot stretch the tile.
 */
@RunWith(AndroidJUnit4::class)
class MineCardHeightTest {
    @get:Rule
    val compose = createComposeRule()

    private val states =
        listOf("claimed", "installed", "used", "checking", "submitted", "disputed", "approved", "rejected", "abandoned", "expired")

    @Test
    fun the_text_column_fits_the_mark_in_every_state() {
        val long = "An app with a name far too long to fit on the one line a ticket gives it"
        compose.setContent {
            TestTrackTheme {
                Column {
                    states.forEach { state ->
                        for (live in listOf(false, true)) {
                            MineCard(
                                MyReview(
                                    id = "$state-$live",
                                    postId = "p",
                                    appName = long,
                                    state = state,
                                    reward = 30,
                                    reason = "The screenshot is of another app, and this reason is long as well.",
                                    isLive = live,
                                    noBox = live,
                                    checkBy = "2026-11-13T08:00:00Z",
                                    decidedAt = "2026-11-09T07:00:00Z",
                                    submittedAt = "2026-11-09T07:00:00Z",
                                ),
                                onOpen = {},
                            )
                        }
                    }
                }
            }
        }
        val columns = compose.onAllNodesWithTag(MINE_TEXT_TAG)
        val cards = compose.onAllNodesWithTag(MINE_CARD_TAG)
        val count = states.size * 2
        assertEquals(count, columns.fetchSemanticsNodes().size)
        for (i in 0 until count) {
            val column = columns[i].getUnclippedBoundsInRoot().height
            assertTrue("column $i is $column", column <= 52.dp)
            cards[i].assertHeightIsEqualTo(76.dp)
        }
    }
}
