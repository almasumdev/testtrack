package com.eazyverse.testtrack.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatPerson
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

/** A tap on a message inside a run shows its own time line over it, and a second tap hides it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-port-mdpi", application = Application::class)
class ChatTappedTimeTest {
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

    private fun line(
        id: String,
        text: String,
        time: Instant,
    ) = ChatLine(id = id, authorSeatId = "kenji", text = text, at = time)

    private val lines =
        listOf(
            line("a", "Thanks, which phone is that?", at(8, 2)),
            line("b", "@[seat:me] did yours open fine tonight?", at(8, 6)),
            line("c", "Steps Daily opens faster too.", at(8, 9)),
        )

    private fun label(time: Instant) = ChatThread.timeLabel(time, LocalDate.now(zone), zone)

    private fun show(open: String? = null) {
        compose.setContent {
            TestTrackTheme {
                var shown by remember { mutableStateOf(open) }
                val view =
                    ChatView(crewName = "Crew 05", rows = ChatThread.rows(lines, null, zone = zone), people = people, mySeat = "me")
                        .copy(timeShown = shown)
                val actions = ChatActions(onTime = { shown = if (shown == it.id) null else it.id })
                Column { view.rows.forEach { ChatRowView(it, view, actions) } }
            }
        }
    }

    @Test
    fun a_tap_on_a_message_in_a_run_shows_its_time_and_a_second_tap_hides_it() {
        show()
        val time = label(at(8, 6))
        compose.onNodeWithText(time).assertDoesNotExist()
        compose.onNodeWithText("did yours open fine tonight?", substring = true).performClick()
        compose.onNodeWithText(time).assertIsDisplayed()
        compose.onNodeWithText("did yours open fine tonight?", substring = true).performClick()
        compose.onNodeWithText(time).assertDoesNotExist()
    }

    @Test
    fun one_time_shows_at_a_time() {
        show()
        compose.onNodeWithText("did yours open fine tonight?", substring = true).performClick()
        compose.onNodeWithText("Steps Daily opens faster too.").performClick()
        compose.onNodeWithText(label(at(8, 9))).assertIsDisplayed()
        compose.onNodeWithText(label(at(8, 6))).assertDoesNotExist()
    }

    @Test
    fun an_open_time_shows_from_the_start() {
        show(open = "b")
        compose.onNodeWithText(label(at(8, 6))).assertIsDisplayed()
    }

    /**
     * On the page the list grows upward, so a time line opening over a message at the top edge
     * would land out of sight. The tapped message scrolls down far enough to show it.
     */
    @Test
    fun on_the_page_a_message_at_the_top_edge_shows_its_time_in_sight() {
        val later = (0 until 12).map { i -> line("m$i", "Line $i from me", at(8, 10 + i)).copy(authorSeatId = "me", mine = true) }
        val all = lines + later
        compose.setContent {
            TestTrackTheme {
                var shown by remember { mutableStateOf<String?>(null) }
                val view =
                    ChatView(crewName = "Crew 05", rows = ChatThread.rows(all, null, zone = zone), people = people, mySeat = "me")
                        .copy(timeShown = shown)
                val actions = ChatActions(onTime = { shown = if (shown == it.id) null else it.id })
                Box(Modifier.fillMaxWidth().height(420.dp)) { CrewChatBody(view, actions) }
            }
        }
        val words = hasText("did yours open fine tonight?", substring = true)
        val list = compose.onNode(hasScrollAction())
        list.performScrollToNode(words)
        // Move it up to the list's top edge, where the page first shows Kenji's second message.
        val listTop = list.fetchSemanticsNode().boundsInRoot.top
        val wordsTop =
            compose
                .onNode(words)
                .fetchSemanticsNode()
                .boundsInRoot.top
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -(wordsTop - listTop - 12f)) }
        compose.onNode(words).performClick()
        compose.waitForIdle()
        // Wholly in sight: nothing of it cut off by the list's edge.
        val shown = compose.onNodeWithText(label(at(8, 6)))
        shown.assertIsDisplayed()
        val time = shown.fetchSemanticsNode()
        assertEquals(time.size.height.toFloat(), time.boundsInRoot.height, 0.5f)
    }
}
