package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Every kind the server sends has a card, and a kind this build does not know is skipped. */
@RunWith(AndroidJUnit4::class)
class ActivityCardsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun every_kind_the_server_sends_draws_a_card() {
        val wire = listOf("started", "halfway", "milestone", "streak", "joined", "helped", "first_review", "announcement")
        val events =
            wire.mapIndexed { i, kind ->
                FeedEvent(
                    id = kind,
                    kind = requireNotNull(FeedEventKind.of(kind)),
                    headline = "Headline $i",
                    detail = "Detail $i",
                    happenedAt = System.currentTimeMillis() - i * 3_600_000L,
                    faces = listOf("A"),
                )
            }
        compose.setContent {
            TestTrackTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { events.forEach { FeedEventCard(it) } }
            }
        }
        events.indices.forEach { compose.onNodeWithText("Headline $it").performScrollTo().assertIsDisplayed() }
        assertEquals(FeedEventKind.FIRST_REVIEW, FeedEventKind.of("first_review"))
        assertNull(FeedEventKind.of("something-new"))
    }
}
