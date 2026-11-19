package com.eazyverse.testtrack.fixture

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.domain.ChatDraft
import com.eazyverse.testtrack.domain.ChatLimits
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatQuote
import com.eazyverse.testtrack.domain.ChatRow
import com.eazyverse.testtrack.domain.ChatState
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.domain.DraftProblem
import com.eazyverse.testtrack.domain.MentionSpan
import com.eazyverse.testtrack.domain.SendState
import com.eazyverse.testtrack.fixture.GallerySamples.Companion.DANIEL
import com.eazyverse.testtrack.fixture.GallerySamples.Companion.KENJI
import com.eazyverse.testtrack.fixture.GallerySamples.Companion.ME
import com.eazyverse.testtrack.fixture.GallerySamples.Companion.NADIA
import com.eazyverse.testtrack.fixture.GallerySamples.Companion.SOFIA
import com.eazyverse.testtrack.fixture.GallerySamples.Companion.app
import com.eazyverse.testtrack.fixture.GallerySamples.Companion.daysAgo
import com.eazyverse.testtrack.fixture.GallerySamples.Companion.seat
import com.eazyverse.testtrack.fixture.GallerySamples.Companion.today
import com.eazyverse.testtrack.fixture.GallerySamples.Companion.yesterday
import com.eazyverse.testtrack.ui.ChatActions
import com.eazyverse.testtrack.ui.ChatClosedBar
import com.eazyverse.testtrack.ui.ChatComposer
import com.eazyverse.testtrack.ui.ChatComposerLoading
import com.eazyverse.testtrack.ui.ChatRowView
import com.eazyverse.testtrack.ui.ChatView
import com.eazyverse.testtrack.ui.CrewChatBody
import com.eazyverse.testtrack.ui.closedLine

/** One state in the gallery: its label, and the real composable drawing it. */
internal class Shot(
    val label: String,
    val content: @Composable () -> Unit,
)

internal class Section(
    val id: String,
    val title: String,
    val shots: List<Shot>,
)

/** What a tap in the gallery opens over it. */
internal sealed interface Overlay {
    data class Message(
        val line: ChatLine,
    ) : Overlay

    data class Delete(
        val line: ChatLine,
    ) : Overlay

    data object Mention : Overlay

    data class Profile(
        val seat: String,
    ) : Overlay

    data class Viewer(
        val line: ChatLine,
    ) : Overlay

    data object Editor : Overlay
}

/** The gallery's actions: holding a message, a face or a picture opens what the chat page would. */
internal fun galleryActions(
    show: (Overlay) -> Unit,
    onTime: (ChatLine) -> Unit = {},
) = ChatActions(
    onLongPress = { show(Overlay.Message(it)) },
    onFace = { show(Overlay.Profile(it)) },
    onPicture = { show(Overlay.Viewer(it)) },
    onTime = onTime,
)

/** Rows of the thread, with the thread's own side margins. */
@Composable
internal fun Rows(
    view: ChatView,
    actions: ChatActions,
    rows: List<ChatRow> = view.rows,
) {
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 6.dp)) {
        rows.forEach { ChatRowView(it, view, actions) }
    }
}

private fun one(line: ChatLine) = listOf(ChatRow.Line(line, first = true, last = true))

private fun group(lines: List<ChatLine>) = lines.mapIndexed { i, line -> ChatRow.Line(line, first = i == 0, last = i == lines.lastIndex) }

internal fun bubbleSection(
    s: GallerySamples,
    show: (Overlay) -> Unit,
): Section {
    // Each shot keeps its own tapped time, so a tap shows a time line as the page does.
    fun rows(
        label: String,
        rows: List<ChatRow>,
        open: String? = null,
        edit: (ChatView) -> ChatView = { it },
    ) = Shot(label) {
        var shown by remember { mutableStateOf(open) }
        val a = galleryActions(show) { line -> shown = if (shown == line.id) null else line.id }
        Rows(edit(s.view(rows)).copy(timeShown = shown), a)
    }
    val held = s.msg("held", KENJI, "Hold me down to see the lift.")
    // As people really write: a burst within minutes sits under one time line, a pause starts another.
    val spread =
        listOf(
            s.msg("tl0", DANIEL, "Last year's crew was a lot quieter.", daysAgo(380, 21, 10)),
            s.msg("tl1", SOFIA, "Pocket Recipes 2.1 is on the test track now.", daysAgo(26, 20, 55)),
            s.msg("tl2", KENJI, "Up early, opened all nine.", daysAgo(3, 3, 45)),
            s.msg("tl3", ME, "See you all tomorrow.", yesterday(22, 10)),
            s.msg("tl3b", KENJI, "Good night!", yesterday(22, 11)),
            s.msg("tl4", NADIA, "Morning, Tiny Tasks got an update.", today(8, 2)),
            s.msg("tl5", NADIA, "The reminder works now.", today(8, 2)),
            s.msg("tl6", KENJI, "Nice, updating now.", today(8, 3)),
            s.msg("tl7", ME, "Same, I'll check it tonight.", today(8, 4)),
            s.msg("tl8", NADIA, "Tell me if it comes late again.", today(8, 5)),
            s.msg("tl9", KENJI, "Steps Daily opens faster too.", today(8, 6)),
            s.msg("tl10", SOFIA, "Anyone else see the new Play testers page?", today(9, 40)),
            s.msg("tl11", ME, "Yes, since this morning.", today(9, 41)),
        )
    val tapped =
        listOf(
            s.msg("tt1", NADIA, "Quick one about Tiny Tasks", today(8, 2)),
            s.msg("tt2", NADIA, "The reminder came 5 minutes late", today(8, 6)),
            s.msg("tt3", NADIA, "Otherwise it works well", today(8, 9)),
            s.msg("tt4", ME, "Good catch, I'll look at it", today(8, 11)),
        )
    return Section(
        "bubbles",
        "Bubbles",
        listOf(
            rows("Others, on its own", one(s.msg("b1", KENJI, "Opened yours, all good tonight."))),
            rows("Yours, on its own", one(s.msg("b2", ME, "Thanks, opened all of yours too."))),
            rows(
                "Others, a group (first, middle, last)",
                group(
                    listOf(
                        s.msg("g1", NADIA, "Quick one about Tiny Tasks"),
                        s.msg("g2", NADIA, "The reminder came 5 minutes late"),
                        s.msg("g3", NADIA, "Otherwise it works well"),
                    ),
                ),
            ),
            rows(
                "Yours, a group (first, middle, last)",
                group(
                    listOf(
                        s.msg("g4", ME, "Good catch"),
                        s.msg("g5", ME, "I'll look at the reminder timing"),
                        s.msg("g6", ME, "Update goes out tomorrow"),
                    ),
                ),
            ),
            rows("Long text", one(s.msg("b3", SOFIA, s.longText))),
            rows("Long text, yours", one(s.msg("b3m", ME, s.longText))),
            rows("Mentions you (tinted fill)", one(s.msg("b4", KENJI, "${seat(ME)} did yours open fine tonight?"))),
            rows("Mentions someone else", one(s.msg("b5", KENJI, "${seat(SOFIA)} thanks, which phone is that?"))),
            rows("Yours, mentioning someone", one(s.msg("b6", ME, "${seat(KENJI)} fixed it on my side, try again."))),
            rows("App mention, others", one(s.msg("b7", NADIA, "${app(ME)} asks me to sign in on first open, is that expected?"))),
            rows("App mention, yours", one(s.msg("b8", ME, "Has anyone tried ${app(KENJI)} with the screen off?"))),
            rows(
                "App mention mid-paragraph (line height)",
                one(
                    s.msg(
                        "b9",
                        SOFIA,
                        "Opened four apps tonight. The reminder in ${app(NADIA)} came late again, and ${seat(ME)} yours asked " +
                            "me to sign in twice before it let me through to the list.",
                    ),
                ),
            ),
            rows(
                "Reply, others quoting you",
                one(
                    s.msg(
                        "r1",
                        KENJI,
                        "That fixed it, thanks.",
                        quote = ChatQuote("q1", ME, "Tap Not now at the bottom to skip the sign-in."),
                    ),
                ),
            ),
            rows(
                "Reply, yours quoting others",
                one(
                    s.msg(
                        "r2",
                        ME,
                        "All good on mine, opened it a minute ago.",
                        quote = ChatQuote("q2", KENJI, "${seat(ME)} did yours open fine tonight?"),
                    ),
                ),
            ),
            rows(
                "Reply to a picture",
                one(s.msg("r3", NADIA, "Same screen on my phone.", quote = ChatQuote("q3", SOFIA, "", picture = true))),
            ),
            rows(
                "Reply to a deleted message",
                one(s.msg("r4", ME, "No worries.", quote = ChatQuote("q4", DANIEL, "", deleted = true))),
            ),
            rows("Sending", one(s.msg("s1", ME, "Will check Steps Daily again after the update.", send = SendState.SENDING))),
            rows(
                "Failed, with the reason",
                one(s.msg("s2", ME, "Opened all nine tonight.", send = SendState.FAILED, error = "No connection. Check your internet.")),
            ),
            rows("Deleted, others", one(s.msg("d1", DANIEL, "", deleted = true))),
            rows("Deleted, yours", one(s.msg("d2", ME, "", deleted = true))),
            rows("Held down (lifted while its sheet is up)", one(held)) { it.copy(lifted = held.id) },
            rows(
                "Team post, with the robot",
                one(s.team("tp", "Play is slow to show new testers today. If an app says it isn't available, wait an hour and try again.")),
            ),
            rows("Bot line, good news", one(s.bot("bn", "Night 6 started. 9 apps to open tonight.", "night"))),
            rows("Bot line, plain", one(s.bot("bp", "Daniel joined on night 2, so his nights end a day later.", "joined"))),
            rows("Time lines: older, yesterday, six messages in a burst under one line, then a pause", s.laid(spread)),
            rows("Tapped time open over a message (tap another to move it)", s.laid(tapped), open = "tt2"),
            rows(
                "The new line",
                listOf(
                    ChatRow.Line(s.msg("n0", ME, "See you all tomorrow.", yesterday(22, 10)), first = true),
                    ChatRow.New(4),
                    ChatRow.Line(s.msg("n1", KENJI, "Morning, Steps Daily got an update.", today(8, 2)), first = true),
                ),
            ),
        ),
    )
}

internal fun pictureSection(
    s: GallerySamples,
    show: (Overlay) -> Unit,
): Section {
    val a = galleryActions(show)

    fun pic(
        label: String,
        line: ChatLine,
    ) = Shot(label) { Rows(s.view(one(line)), a) }
    return Section(
        "pictures",
        "Pictures",
        listOf(
            pic("Portrait, others", s.msg("p1", DANIEL, "", picture = s.sent(s.portrait, 900, 1600))),
            pic("Portrait, yours", s.msg("p2", ME, "", picture = s.sent(s.portrait, 900, 1600))),
            pic("Landscape", s.msg("p3", SOFIA, "", picture = s.sent(s.landscape, 1600, 900))),
            pic("Tall (cropped to its tallest)", s.msg("p4", NADIA, "", picture = s.sent(s.tall, 900, 2600))),
            pic(
                "With a caption, others",
                s.msg("p5", SOFIA, "${app(KENJI)} closed on me when I tapped Start.", picture = s.sent(s.landscape, 1600, 900)),
            ),
            pic("With a caption, yours", s.msg("p6", ME, "This is the new home screen.", picture = s.sent(s.portrait, 900, 1600))),
            pic("Sending", s.msg("p7", ME, "", picture = s.local(s.portrait, 900, 1600), send = SendState.SENDING)),
            pic(
                "Failed",
                s.msg(
                    "p8",
                    ME,
                    "",
                    picture = s.local(s.landscape, 1600, 900),
                    send = SendState.FAILED,
                    error = "The picture didn't upload. Try again.",
                ),
            ),
            pic("Expired, others", s.msg("p9", DANIEL, "", picture = s.expired)),
            pic("Expired, yours with a caption", s.msg("p10", ME, "Yesterday's crash screen.", picture = s.expired)),
            pic(
                "Reply with a picture",
                s.msg(
                    "p11",
                    KENJI,
                    "Mine looks like this.",
                    picture = s.sent(s.portrait, 900, 1600),
                    quote = ChatQuote("q5", SOFIA, "", picture = true),
                ),
            ),
            pic("Picture that mentions you", s.msg("p12", NADIA, "${seat(ME)} is this right?", picture = s.sent(s.landscape, 1600, 900))),
        ),
    )
}

/** The chat page in a box of [height], as it draws [view]. */
@Composable
internal fun PageBox(
    view: ChatView,
    actions: ChatActions,
    height: Dp = 560.dp,
) {
    Box(Modifier.fillMaxWidth().height(height)) {
        CrewChatBody(view, actions, TextFieldValue(view.draft.text, TextRange(view.draft.text.length)))
    }
}

internal fun pageSection(
    s: GallerySamples,
    show: (Overlay) -> Unit,
): Section {
    val a = galleryActions(show)
    val until =
        java.time.Instant
            .now()
            .plusSeconds(9L * 24 * 3600)
            .toString()
    val done = s.thread.filter { it.send == SendState.SENT }
    val ended = closedLine(ChatState.READ_ONLY, "ended", s.view().crewName, until)
    return Section(
        "page",
        "Chat page",
        listOf(
            Shot("Full page, a realistic thread") { PageBox(s.view(s.threadRows()), a, 780.dp) },
            Shot("Muted (bell after the title)") { PageBox(s.view(s.threadRows()).copy(muted = true), a, 420.dp) },
            Shot("Loading skeleton") { PageBox(s.view().copy(loading = true), a) },
            Shot("Didn't load") { PageBox(s.view().copy(failed = true), a, 480.dp) },
            Shot("Empty chat") { PageBox(s.view(), a) },
            Shot("Empty, with the bot's news") {
                PageBox(s.view(one(s.bot("e1", "Crew 05 is full. Night 1 starts at midnight.", "started"))), a)
            },
            Shot("Empty, read only") {
                PageBox(s.view().copy(state = ChatState.READ_ONLY, canPost = false, closedLine = ended), a)
            },
            Shot("Read only, crew ended") {
                PageBox(s.view(ChatThread.rows(done, null)).copy(state = ChatState.READ_ONLY, canPost = false, closedLine = ended), a)
            },
            Shot("A message over the page") {
                PageBox(s.view(s.threadRows()).copy(message = "That picture couldn't be opened. Try another one."), a, 420.dp)
            },
        ),
    )
}

internal fun composerSection(
    s: GallerySamples,
    show: (Overlay) -> Unit,
): Section {
    val a = galleryActions(show)

    fun comp(
        label: String,
        edit: (ChatView) -> ChatView,
    ) = Shot(label) {
        val view = edit(s.view())
        ChatComposer(view, a, TextFieldValue(view.draft.text, TextRange(view.draft.text.length)))
    }
    val mention = "Thanks @Kenji Sato for the tip"
    val reply = s.msg("rp", KENJI, "${seat(ME)} did yours open fine tonight?")
    val picture = s.msg("rpp", SOFIA, "", picture = s.sent(s.landscape, 1600, 900))
    val name = s.view().crewName
    return Section(
        "composer",
        "Composer",
        listOf(
            comp("Normal, empty") { it },
            comp("With text") { it.copy(draft = ChatDraft("Opened yours, all good tonight.")) },
            comp("With a mention picked") { it.copy(draft = ChatDraft(mention, listOf(MentionSpan(7, 18, false, KENJI)))) },
            comp("Five lines, the most it grows") { it.copy(draft = ChatDraft(s.longText)) },
            comp("Reply bar") { it.copy(replyTo = reply) },
            comp("Reply bar, to a picture") { it.copy(replyTo = picture) },
            comp("Link blocked") { it.copy(draft = ChatDraft("Try the beta at example.com/beta"), problem = DraftProblem.LINK) },
            comp("Too long") { it.copy(draft = ChatDraft(s.longText.repeat(2)), problem = DraftProblem.TOO_LONG) },
            comp("Too many mentions") { it.copy(draft = ChatDraft("Thanks all of you"), problem = DraftProblem.TOO_MANY_MENTIONS) },
            comp("3 messages left") { it.copy(messagesLeft = 3) },
            comp("1 message left") { it.copy(messagesLeft = 1, picturesLeft = 1) },
            comp("0 messages left (field shut, picture grey)") { it.copy(messagesLeft = 0) },
            comp("Picture badge 3") { it.copy(picturesLeft = 3) },
            comp("Picture badge 1") { it.copy(picturesLeft = 1) },
            comp("Picture badge 0 (grey, tap it for the tip)") { it.copy(picturesLeft = 0) },
            comp("Pictures off in this chat (grey)") { it.copy(picturesLeft = 0, limits = ChatLimits(picturesPerDay = 0)) },
            comp("Picture badge 9+") { it.copy(picturesLeft = 12, limits = ChatLimits(messagesPerDay = 30, picturesPerDay = 20)) },
            comp("No badge: as many pictures as messages") {
                it.copy(picturesLeft = 10, limits = ChatLimits(messagesPerDay = 10, picturesPerDay = 10))
            },
            Shot("While the chat loads") { ChatComposerLoading() },
            Shot("Closed: crew ended") { ChatClosedBar(closedLine(ChatState.READ_ONLY, "ended", name, null)) },
            Shot("Closed: your app is out") { ChatClosedBar(closedLine(ChatState.READ_ONLY, "out", name, null)) },
            Shot("Closed: messages paused") { ChatClosedBar(closedLine(ChatState.READ_ONLY, "paused", name, null)) },
            Shot("Closed: chat deleted") { ChatClosedBar(closedLine(ChatState.GONE, "", name, null)) },
        ),
    )
}
