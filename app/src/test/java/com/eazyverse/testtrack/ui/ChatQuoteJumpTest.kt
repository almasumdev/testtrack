package com.eazyverse.testtrack.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatPerson
import com.eazyverse.testtrack.domain.ChatQuote
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A tap on a reply's quote scrolls the quoted message into the middle and lights it; the rest of the bubble still shows the time. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-port-mdpi", application = Application::class)
class ChatQuoteJumpTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.systemDefault()

    private fun at(
        hour: Int,
        minute: Int,
    ): Instant =
        LocalDate
            .now(zone)
            .atTime(hour, minute)
            .atZone(zone)
            .toInstant()

    private val people =
        mapOf(
            "me" to ChatPerson("me", "Arjun Mehta", mine = true),
            "kenji" to ChatPerson("kenji", "Kenji Sato"),
        )

    private val original = "Which phone did the crash happen on?"
    private val answer = "A Pixel 7, it closed on the first screen."

    /** A little talk, the original, a long run of talk, then the reply that quotes it at the bottom. */
    private val lines: List<ChatLine> =
        buildList {
            repeat(8) { i -> add(ChatLine(id = "e$i", authorSeatId = "kenji", text = "Earlier line $i", at = at(7, 58))) }
            add(ChatLine(id = "q", authorSeatId = "kenji", text = original, at = at(8, 0)))
            repeat(20) { i ->
                val mine = i % 2 == 0
                val who = if (mine) "me" else "kenji"
                add(ChatLine(id = "m$i", authorSeatId = who, mine = mine, text = "Line $i of the talk", at = at(8, 1)))
            }
            add(
                ChatLine(
                    id = "reply",
                    authorSeatId = "me",
                    mine = true,
                    text = answer,
                    quote = ChatQuote("q", "kenji", original),
                    at = at(8, 2),
                ),
            )
        }

    private fun show() {
        compose.setContent {
            TestTrackTheme {
                var jump by remember { mutableStateOf<ChatJump?>(null) }
                var shown by remember { mutableStateOf<String?>(null) }
                val view =
                    ChatView(crewName = "Crew 05", rows = ChatThread.rows(lines, null, zone = zone), people = people, mySeat = "me")
                        .copy(jump = jump, timeShown = shown)
                val actions =
                    ChatActions(
                        onQuote = { jump = ChatJump(it.id, (jump?.n ?: 0) + 1) },
                        onTime = { shown = if (shown == it.id) null else it.id },
                    )
                Box(Modifier.fillMaxWidth().height(420.dp)) { CrewChatBody(view, actions) }
            }
        }
    }

    @Test
    fun a_tap_on_the_quote_scrolls_to_the_original_and_lights_it() {
        show()
        // Far up the thread: only the quote shows its words.
        compose.onNodeWithText(original).assertIsDisplayed()
        assertEquals(1, compose.onAllNodes(hasText(original)).fetchSemanticsNodes().size)

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText(original).performClick()
        compose.mainClock.advanceTimeUntil(5_000) {
            compose.onAllNodes(hasText(original) and hasTestTag(JUMP_FLASH_TAG)).fetchSemanticsNodes().isNotEmpty()
        }

        val lit = compose.onNodeWithTag(JUMP_FLASH_TAG)
        lit.assertIsDisplayed()
        lit.assert(hasText(original))
        val list = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        val bubble = lit.fetchSemanticsNode().boundsInRoot
        assertEquals(list.center.y, bubble.center.y, 40f)

        // The tint fades out in about a second.
        compose.mainClock.advanceTimeBy(1_500)
        compose.onNodeWithTag(JUMP_FLASH_TAG).assertDoesNotExist()
    }

    @Test
    fun a_tap_on_the_rest_of_the_reply_shows_its_time_and_does_not_jump() {
        show()
        // On the words under the quote, not on the quote at the bubble's top.
        compose.onNodeWithText(answer).performTouchInput { click(Offset(centerX, bottom - 8f)) }
        compose.waitForIdle()
        compose.onNodeWithText(ChatThread.timeLabel(at(8, 2), LocalDate.now(zone), zone)).assertIsDisplayed()
        compose.onNodeWithTag(JUMP_FLASH_TAG).assertDoesNotExist()
    }
}
