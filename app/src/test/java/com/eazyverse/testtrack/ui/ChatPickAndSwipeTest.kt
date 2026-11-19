package com.eazyverse.testtrack.ui

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.eazyverse.testtrack.domain.ChatKind
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatPerson
import com.eazyverse.testtrack.domain.ChatPicks
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

/** Holding a message picks it, a drag toward the middle replies, and a deleted bubble is sized like any other. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-port-mdpi", application = Application::class)
class ChatPickAndSwipeTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.systemDefault()

    private fun at(minute: Int): Instant =
        LocalDate
            .now(zone)
            .atTime(8, minute)
            .atZone(zone)
            .toInstant()

    private val people =
        mapOf(
            "me" to ChatPerson("me", "Arjun Mehta", mine = true),
            "kenji" to ChatPerson("kenji", "Kenji Sato"),
        )

    private val theirs = ChatLine(id = "t", authorSeatId = "kenji", text = "Did yours open fine?", at = at(2))
    private val mine = ChatLine(id = "m", authorSeatId = "me", mine = true, text = "Yes, all good.", at = at(5))

    private class Log {
        val selected = mutableListOf<String>()
        val replied = mutableListOf<String>()
        val timed = mutableListOf<String>()
    }

    private fun show(
        lines: List<ChatLine>,
        log: Log,
        picks: ChatPicks = ChatPicks(),
    ) {
        compose.setContent {
            TestTrackTheme {
                val view =
                    ChatView(crewName = "Crew 05", rows = ChatThread.rows(lines, null, zone = zone), people = people, mySeat = "me")
                        .copy(picks = picks)
                val actions =
                    ChatActions(
                        onSelect = { log.selected += it.id },
                        onReply = { log.replied += it.id },
                        onTime = { log.timed += it.id },
                    )
                Column { view.rows.forEach { ChatRowView(it, view, actions) } }
            }
        }
    }

    private fun drag(
        text: String,
        vararg steps: Pair<Float, Float>,
    ) {
        compose.onNodeWithText(text).performTouchInput {
            down(center)
            steps.forEach { moveBy(Offset(it.first, it.second)) }
            up()
        }
    }

    @Test
    fun a_hold_picks_the_message() {
        val log = Log()
        show(listOf(theirs), log)
        compose.onNodeWithText(theirs.text).performTouchInput { longClick() }
        assertEquals(listOf("t"), log.selected)
    }

    @Test
    fun while_picking_a_tap_picks_and_does_not_show_the_time() {
        val log = Log()
        show(listOf(theirs), log, ChatPicks(ids = setOf("m"), copy = true))
        compose.onNodeWithText(theirs.text).performClick()
        assertEquals(listOf("t"), log.selected)
        assertEquals(emptyList<String>(), log.timed)
    }

    @Test
    fun a_swipe_toward_the_middle_past_the_threshold_replies() {
        val log = Log()
        show(listOf(theirs, mine), log)
        drag(theirs.text, 30f to 0f, 90f to 0f)
        drag(mine.text, -30f to 0f, -90f to 0f)
        assertEquals(listOf("t", "m"), log.replied)
    }

    @Test
    fun a_short_swipe_the_wrong_way_or_up_and_down_does_not_reply() {
        val log = Log()
        show(listOf(theirs, mine), log)
        drag(theirs.text, 30f to 0f, 20f to 0f)
        drag(theirs.text, -30f to 0f, -90f to 0f)
        drag(mine.text, 30f to 0f, 90f to 0f)
        drag(theirs.text, 10f to 40f, 90f to 10f)
        assertEquals(emptyList<String>(), log.replied)
    }

    @Test
    fun no_swipe_on_a_deleted_message_or_a_bot_line() {
        val log = Log()
        val gone = theirs.copy(id = "g", deleted = true, text = "")
        val bot = ChatLine(id = "b", kind = ChatKind.BOT, text = "Night 6 started.", event = "night", at = at(1))
        show(listOf(bot, gone), log)
        drag("Night 6 started.", 30f to 0f, 90f to 0f)
        drag("Message deleted", 30f to 0f, 90f to 0f)
        assertEquals(emptyList<String>(), log.replied)
    }

    @Test
    fun no_swipe_while_picking() {
        val log = Log()
        show(listOf(theirs), log, ChatPicks(ids = setOf("t"), copy = true))
        drag(theirs.text, 30f to 0f, 90f to 0f)
        assertEquals(emptyList<String>(), log.replied)
    }

    @Test
    fun a_deleted_bubble_is_as_tall_as_a_one_line_bubble_and_keeps_the_gap_to_the_next() {
        val gone = theirs.copy(id = "g", deleted = true, text = "")
        val next = theirs.copy(id = "n", text = "Anyway, all fine here.", at = at(3))
        show(listOf(gone, next), Log())
        val deleted = compose.onNodeWithText("Message deleted").getUnclippedBoundsInRoot()
        val after = compose.onNodeWithText(next.text).getUnclippedBoundsInRoot()
        assertEquals(BubbleMinHeight, after.height)
        assertEquals(BubbleMinHeight, deleted.height)
        // One run by one author: 2.5dp apart, as between any two bubbles in a group (whole pixels on screen).
        assertEquals(groupGap(first = false).value, (after.top - deleted.bottom).value, 0.5f)
        assertEquals(2.5.dp, groupGap(first = false))
        assertEquals(12.dp, groupGap(first = true))
    }
}
