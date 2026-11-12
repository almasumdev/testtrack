package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
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
 * The open crew's header keeps to the 40dp of its icon: the text column is never taller, with a
 * long crew name, with or without an estimate, and in a narrow card.
 */
@RunWith(AndroidJUnit4::class)
class CrewOpenHeaderHeightTest {
    @get:Rule
    val compose = createComposeRule()

    private fun crew(
        name: String,
        taken: Int = 6,
        full: String? = "2026-11-16T12:00:00+00:00",
        basis: String? = "pace",
    ) = Crew(
        id = name,
        name = name,
        phase = CrewPhase.OPEN,
        filling = true,
        taken = taken,
        filled = taken,
        likelyFullAt = full,
        likelyBasis = basis,
    )

    @Test
    fun the_text_column_fits_the_icon_in_every_state() {
        val long = "A crew whose name is far too long to fit on one line beside its seats"
        val crews =
            listOf(
                crew("Crew 07"),
                crew(long),
                crew("Crew 07", taken = 14),
                crew("Crew 07", full = null, basis = "new"),
                crew(long, full = null, basis = null),
                null,
            )
        compose.setContent {
            TestTrackTheme {
                Column(Modifier.width(300.dp)) {
                    crews.forEach { CrewOpenHeader(it) }
                }
            }
        }
        val columns = compose.onAllNodesWithTag(CREW_HEAD_TAG)
        assertEquals(crews.size, columns.fetchSemanticsNodes().size)
        for (i in crews.indices) {
            val height = columns[i].getUnclippedBoundsInRoot().height
            assertTrue("header $i is $height", height <= 40.dp)
        }
    }
}
