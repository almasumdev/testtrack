package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.domain.ChatDraft
import com.eazyverse.testtrack.domain.ChatKind
import com.eazyverse.testtrack.domain.ChatLimits
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatPerson
import com.eazyverse.testtrack.domain.ChatPiece
import com.eazyverse.testtrack.domain.ChatQuote
import com.eazyverse.testtrack.domain.ChatRow
import com.eazyverse.testtrack.domain.ChatState
import com.eazyverse.testtrack.domain.ChatText
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.domain.DraftProblem
import com.eazyverse.testtrack.domain.SendState
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.CrewChatViewModel
import com.eazyverse.testtrack.viewmodel.delete
import com.eazyverse.testtrack.viewmodel.retry
import com.eazyverse.testtrack.viewmodel.send
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the chat page draws, as plain values. */
internal data class ChatView(
    val crewName: String,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val state: ChatState = ChatState.OPEN,
    val canPost: Boolean = true,
    /** Said in place of the composer when posting is closed. */
    val closedLine: String = "",
    val rows: List<ChatRow> = emptyList(),
    val people: Map<String, ChatPerson> = emptyMap(),
    val mySeat: String? = null,
    val muted: Boolean = false,
    val messagesLeft: Int = 0,
    val picturesLeft: Int = 0,
    val limits: ChatLimits = ChatLimits(),
    val botName: String = "Crew bot",
    val draft: ChatDraft = ChatDraft(),
    val replyTo: ChatLine? = null,
    val problem: DraftProblem = DraftProblem.NONE,
    val loadingOlder: Boolean = false,
    /** The message held down, lifted while its sheet is up. */
    val lifted: String? = null,
    /** The crew has a members page to go to. */
    val members: Boolean = false,
    val message: String? = null,
) {
    /** Anything said by a member or the team, rather than only the bot's news. */
    val talk: Boolean get() = rows.any { it is ChatRow.Line && it.line.kind != ChatKind.BOT }

    fun name(seatId: String?): String = seatId?.let { people[it]?.name }?.ifBlank { null } ?: "Someone"

    fun mentionLabel(
        line: ChatLine,
        app: Boolean,
        seatId: String,
    ): String =
        line.mentions
            .firstOrNull { it.app == app && it.seatId == seatId }
            ?.label
            ?.ifBlank { null }
            ?: people[seatId]?.let { if (app) it.appName else it.name }?.ifBlank { null }
            ?: if (app) "an app" else "someone"

    /** Words with each mention as "@Label", for a quote, a bot line or the clipboard. */
    fun plain(text: String): String =
        ChatText.plain(text) { app, id -> people[id]?.let { if (app) shortAppName(it.appName) else it.name } ?: "someone" }

    /** The message names the reader, or the reader's app. */
    fun mentionsMe(line: ChatLine): Boolean =
        mySeat != null &&
            (line.mentions.any { it.seatId == mySeat } || ChatText.pieces(line.text).any { it is ChatPiece.Mention && it.seatId == mySeat })

    fun quoteOf(quote: ChatQuote): QuoteView =
        QuoteView(
            who = if (quote.authorSeatId != null && quote.authorSeatId == mySeat) "You" else name(quote.authorSeatId),
            text =
                when {
                    quote.deleted -> "Message deleted"
                    quote.excerpt.isBlank() && quote.picture -> "Picture"
                    else -> plain(quote.excerpt)
                },
            deleted = quote.deleted,
        )
}

/** A quoted message, ready to draw. */
internal data class QuoteView(
    val who: String,
    val text: String,
    val deleted: Boolean = false,
)

/** What the chat page can do. */
internal class ChatActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMute: () -> Unit = {},
    val onMembers: () -> Unit = {},
    val onField: (TextFieldValue) -> Unit = {},
    val onSend: () -> Unit = {},
    val onPickPicture: () -> Unit = {},
    val onClearReply: () -> Unit = {},
    val onLongPress: (ChatLine) -> Unit = {},
    val onFace: (String) -> Unit = {},
    val onRetryLine: (ChatLine) -> Unit = {},
    val onPicture: (ChatLine) -> Unit = {},
    val onLoadOlder: () -> Unit = {},
    val onClearMessage: () -> Unit = {},
)

/**
 * A crew's chat on its own page: the wiring. The poll runs while it is on screen; leaving reads
 * the crews again, so the tiles drop what was just read.
 */
@Composable
fun CrewChatScreen(
    vm: CrewChatViewModel,
    crews: CrewViewModel,
    id: String,
    onBack: () -> Unit,
    onMembers: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(id) { if (crews.crews.none { it.id == id }) crews.load(context) }
    WhileShown(onShow = { vm.start(context, id) }, onHide = vm::stop)
    DisposableEffect(Unit) { onDispose { crews.load(context) } }

    val crew = crews.crews.firstOrNull { it.id == id }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    LaunchedEffect(vm.draft.text) {
        if (field.text != vm.draft.text) field = TextFieldValue(vm.draft.text, TextRange(vm.draft.text.length))
    }
    var mentionAt by remember { mutableStateOf<Int?>(null) }
    var pressed by remember { mutableStateOf<ChatLine?>(null) }
    var deleting by remember { mutableStateOf<ChatLine?>(null) }
    var profile by remember { mutableStateOf<CrewTester?>(null) }
    var picture by remember { mutableStateOf<ChatLine?>(null) }
    val pickPicture = rememberChatPicturePicker(vm)

    val name = crew?.name ?: "Crew"
    val view =
        ChatView(
            crewName = name,
            loading = vm.loading,
            failed = vm.failed,
            state = vm.state,
            canPost = vm.canPost,
            closedLine = closedLine(vm.state, vm.whyNot, name, crew?.detailsUntil),
            rows = remember(vm.lines, vm.newFromRev, vm.newToRev) { ChatThread.rows(vm.lines, vm.newFromRev, vm.newToRev) },
            people = vm.people,
            mySeat = vm.mySeat,
            muted = vm.muted,
            messagesLeft = vm.messagesLeft,
            picturesLeft = vm.picturesLeft,
            limits = vm.limits,
            botName = vm.botName,
            draft = vm.draft,
            replyTo = vm.replyTo,
            problem = vm.problem,
            loadingOlder = vm.loadingOlder,
            lifted = pressed?.id,
            members = crew != null && !crew.pruned && (crew.phase == CrewPhase.RUN || crew.phase == CrewPhase.RECORD),
            message = vm.message,
        )
    val actions =
        ChatActions(
            onBack = onBack,
            onRetry = vm::retry,
            onMute = vm::toggleMute,
            onMembers = onMembers,
            onField = { next ->
                val at = ChatDraft.atTyped(field.text, next.text, next.selection.start)
                vm.edit(vm.draft.edit(next.text))
                field = next
                if (at != null && vm.people.values.any { it.inCrew && !it.mine }) mentionAt = at
            },
            onSend = vm::send,
            onPickPicture = pickPicture,
            onClearReply = vm::clearReply,
            onLongPress = { pressed = it },
            onFace = { seat -> profile = chatProfileOf(seat, vm.people[seat], crew) },
            onRetryLine = { vm.retry(it, context) },
            onPicture = { picture = it },
            onLoadOlder = vm::loadOlder,
            onClearMessage = vm::clearMessage,
        )
    CrewChatBody(view, actions, field)

    mentionAt?.let { at ->
        ChatMentionSheet(
            people = vm.people.values.filter { it.inCrew && !it.mine },
            crewName = name,
            onPick = { person, app ->
                val (next, cursor) = vm.draft.mention(at, if (app) shortAppName(person.appName) else person.name, app, person.seatId)
                vm.edit(next)
                field = TextFieldValue(next.text, TextRange(cursor))
            },
            onDismiss = { mentionAt = null },
        )
    }
    pressed?.let { line ->
        ChatMessageSheet(
            line = line,
            title = if (line.mine) "Your message" else "${view.name(line.authorSeatId).substringBefore(' ')}'s message",
            canReply = vm.canPost && line.send == SendState.SENT,
            onReply = { vm.reply(line) },
            onCopy = { clipboard.setText(AnnotatedString(view.plain(line.text))) },
            onDelete = { deleting = line },
            onDismiss = { pressed = null },
        )
    }
    deleting?.let { line ->
        Ask(
            title = "Delete this message?",
            body = "It goes for everyone in $name. It still counts toward your ${vm.limits.messagesPerDay} today.",
            confirm = "Delete",
            dismiss = "Keep it",
            destructive = true,
            onConfirm = {
                deleting = null
                vm.delete(line)
            },
            onDismiss = { deleting = null },
        )
    }
    profile?.let { CrewMemberProfile(it) { profile = null } }
    picture?.let { line ->
        val who = if (line.mine) "You" else view.name(line.authorSeatId)
        ChatImageViewer(line, who, view.plain(line.text)) { picture = null }
    }
}

/** The chat page, drawn from [view]: the bar, the thread, and the composer or why there is none. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CrewChatBody(
    view: ChatView,
    actions: ChatActions,
    field: TextFieldValue = TextFieldValue(view.draft.text),
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                PageBar(
                    "${view.crewName} chat",
                    actions.onBack,
                    titleEnd =
                        if (view.muted) {
                            {
                                Spacer(Modifier.size(6.dp))
                                Icon(Lucide.BellOff, "Muted", Modifier.size(16.dp), tint = Tt.colors.text3)
                            }
                        } else {
                            null
                        },
                ) { if (!view.failed && !view.loading) ChatMenu(view, actions) }
                PageMessage(view.message, actions.onClearMessage)
            }
        },
        bottomBar = {
            when {
                view.failed -> Unit
                view.loading -> ChatComposerLoading()
                !view.canPost || view.state != ChatState.OPEN -> ChatClosedBar(view.closedLine)
                else -> ChatComposer(view, actions, field)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                view.failed -> RobotOffline("Chat didn't load", onRetry = actions.onRetry)
                view.loading -> ChatSkeleton(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
                !view.talk -> ChatEmpty(view, actions)
                else -> ChatThreadList(view, actions)
            }
        }
    }
}

/** No member has said anything yet: the bot's news, if any, then a word to start in the space left. */
@Composable
private fun ChatEmpty(
    view: ChatView,
    actions: ChatActions,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 12.dp),
        ) {
            view.rows.forEach { ChatRowView(it, view, actions) }
            Spacer(Modifier.weight(1f))
            ChatEmptyNote(view.canPost, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.weight(1f))
        }
    }
}

/** The thread, newest at the bottom, reading older pages as it is scrolled up. */
@Composable
private fun ChatThreadList(
    view: ChatView,
    actions: ChatActions,
) {
    val state = rememberLazyListState()
    val rows = view.rows.asReversed()
    val nearTop by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearTop, rows.size) { if (nearTop) actions.onLoadOlder() }
    // Something new at the bottom: follow it when the reader was there, or when it is their own.
    val newest = view.rows.lastOrNull()
    LaunchedEffect(newest?.key) {
        val own = (newest as? ChatRow.Line)?.line?.mine == true
        if (own || state.firstVisibleItemIndex <= 1) state.animateScrollToItem(0)
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        reverseLayout = true,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
    ) {
        items(rows, key = { it.key }) { row -> ChatRowView(row, view, actions) }
        if (view.loadingOlder) item(key = "older") { ChatSkeleton(Modifier.fillMaxWidth()) }
    }
}

/** Why the composer is not there, in words. */
internal fun closedLine(
    state: ChatState,
    whyNot: String,
    crewName: String,
    detailsUntil: String?,
): String =
    when {
        state == ChatState.GONE -> "This chat was deleted with the crew's details."
        whyNot == "out" -> "Your app is out of this run, so you can read the chat but not post."
        whyNot == "paused" -> "The team has paused your messages in this chat."
        else -> {
            val day =
                detailsUntil?.let {
                    runCatching {
                        OffsetDateTime
                            .parse(it)
                            .atZoneSameInstant(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
                    }.getOrNull()
                }
            "$crewName has ended, so this chat is read only." + (day?.let { " It goes with the crew's details on $it." } ?: "")
        }
    }

/** A member as their profile shows them, from the crew when it is held, else from the chat. */
internal fun chatProfileOf(
    seatId: String,
    person: ChatPerson?,
    crew: Crew?,
): CrewTester {
    val member = crew?.members?.firstOrNull { it.id == seatId }
    val seat = crew?.seats?.firstOrNull { it.id == seatId }
    val phase = crew?.phase
    val over = phase != CrewPhase.RUN || (member?.day ?: 0) <= 0
    val (line, tone) =
        when {
            phase == CrewPhase.RECORD -> "The crew has finished" to Tone.QUIET
            phase == CrewPhase.LOBBY || member == null -> "Waiting for the run" to Tone.QUIET
            member.nightDone -> "Done tonight" to Tone.OK
            else -> "${member.doneToday} of ${member.owedToday} opened tonight" to Tone.ACCENT
        }
    return CrewTester(
        id = seatId,
        name = person?.name?.ifBlank { null } ?: member?.name.orEmpty(),
        photo = person?.photo?.ifBlank { null } ?: member?.photo.orEmpty(),
        email = member?.email?.ifBlank { null } ?: person?.email.orEmpty(),
        appName = person?.appName?.ifBlank { null } ?: member?.appName ?: seat?.appName.orEmpty(),
        packageName = person?.packageName?.ifBlank { null } ?: member?.packageName ?: seat?.packageName.orEmpty(),
        icon = person?.appIcon?.ifBlank { null } ?: member?.icon ?: seat?.icon.orEmpty(),
        mine = person?.mine == true || member?.mine == true,
        day = member?.day ?: person?.day ?: 0,
        out = member?.out ?: (person?.inCrew == false),
        line = line,
        tone = tone,
        over = over,
    )
}
