package com.eazyverse.testtrack.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
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
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatPerson
import com.eazyverse.testtrack.domain.ChatRow
import com.eazyverse.testtrack.domain.ChatSelect
import com.eazyverse.testtrack.domain.ChatState
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.domain.instantOf
import com.eazyverse.testtrack.domain.shortDate
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.CrewChatViewModel
import com.eazyverse.testtrack.viewmodel.delete
import com.eazyverse.testtrack.viewmodel.retry
import com.eazyverse.testtrack.viewmodel.send

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
    var picked by remember { mutableStateOf(emptySet<String>()) }
    var deleting by remember { mutableStateOf<List<ChatLine>>(emptyList()) }
    var profile by remember { mutableStateOf<CrewTester?>(null) }
    var picture by remember { mutableStateOf<ChatLine?>(null) }
    var timeShown by remember { mutableStateOf<String?>(null) }
    var jump by remember { mutableStateOf<ChatJump?>(null) }
    val hour24 = DateFormat.is24HourFormat(context)
    val pickPicture = rememberChatPicturePicker(vm)
    val writable = vm.canPost && vm.state == ChatState.OPEN
    val picks = remember(picked, vm.lines, writable) { ChatSelect.picks(picked, vm.lines, writable) }
    // A picked message that goes (or can no longer be picked) drops out; with none left the bar closes.
    LaunchedEffect(picks.ids) { if (picks.ids != picked) picked = picks.ids }
    BackHandler(enabled = picks.active) { picked = emptySet() }

    val name = crew?.name ?: "Crew"
    val view =
        ChatView(
            crewName = name,
            loading = vm.loading,
            failed = vm.failed,
            state = vm.state,
            canPost = vm.canPost,
            closedLine = closedLine(vm.state, vm.whyNot, name, crew?.detailsUntil),
            rows =
                remember(vm.lines, vm.newFromRev, vm.newToRev, hour24) {
                    ChatThread.rows(vm.lines, vm.newFromRev, vm.newToRev, hour24 = hour24)
                },
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
            picks = picks,
            timeShown = timeShown,
            jump = jump,
            seeking = vm.seeking,
            members = crew != null && !crew.pruned && (crew.phase == CrewPhase.RUN || crew.phase == CrewPhase.RECORD),
        )
    val actions =
        ChatActions(
            onBack = onBack,
            onRetry = vm::retry,
            onMute = vm::toggleMute,
            onMembers = onMembers,
            onField = { next ->
                vm.edit(vm.draft.edit(next.text))
                field = next
            },
            onAt = {
                val start = field.selection.min
                val before = field.text.substring(0, start)
                val gap = if (before.isEmpty() || before.last().isWhitespace()) "" else " "
                val next = before + gap + "@" + field.text.substring(field.selection.max)
                vm.edit(vm.draft.edit(next))
                field = TextFieldValue(next, TextRange(start + gap.length + 1))
            },
            onMention = { next, cursor ->
                vm.edit(next)
                field = TextFieldValue(next.text, TextRange(cursor))
            },
            onSend = vm::send,
            onPickPicture = pickPicture,
            onClearReply = vm::clearReply,
            onSelect = { picked = ChatSelect.toggle(picked, it.id) },
            onClearSelection = { picked = emptySet() },
            onReply = { if (ChatSelect.canReply(it, writable)) vm.reply(it) },
            onReplyPicked = {
                ChatSelect.lines(picked, vm.lines, writable).singleOrNull()?.let(vm::reply)
                picked = emptySet()
            },
            onCopyPicked = {
                val lines = ChatSelect.lines(picked, vm.lines, writable)
                clipboard.setText(AnnotatedString(ChatSelect.copyText(lines, view::plain)))
                picked = emptySet()
            },
            onDeletePicked = {
                val lines = ChatSelect.lines(picked, vm.lines, writable)
                // Messages that never sent only leave this phone, so they go without asking.
                if (picks.ask) {
                    deleting = lines
                } else {
                    picked = emptySet()
                    lines.forEach(vm::delete)
                }
            },
            onFace = { seat -> profile = chatProfileOf(seat, vm.people[seat], crew) },
            onRetryLine = { vm.retry(it, context) },
            onPicture = { picture = it },
            onTime = { timeShown = if (timeShown == it.id) null else it.id },
            onQuote = { quote -> vm.seek(quote) { jump = ChatJump(it, (jump?.n ?: 0) + 1) } },
            onLoadOlder = vm::loadOlder,
        )
    CrewChatBody(view, actions, field)

    if (deleting.isNotEmpty()) {
        val many = deleting.size > 1
        Ask(
            title = if (many) "Delete ${deleting.size} messages?" else "Delete this message?",
            body =
                if (many) {
                    "They go for everyone in $name. They still count toward today's messages."
                } else {
                    "It goes for everyone in $name. It still counts toward today's messages."
                },
            confirm = "Delete",
            dismiss = "Keep ${if (many) "them" else "it"}",
            destructive = true,
            onConfirm = {
                val gone = deleting
                deleting = emptyList()
                picked = emptySet()
                gone.forEach(vm::delete)
            },
            onDismiss = { deleting = emptyList() },
        )
    }
    profile?.let { CrewMemberProfile(it) { profile = null } }
    picture?.let { line ->
        val shown = line.picture ?: return@let
        val who = if (line.mine) "You" else view.authorName(line)
        PictureViewer(
            shown.source(),
            title = who,
            subtitle = sentAt(line, hour24),
            caption = view.plain(line.text),
            footnote = ChatThread.pictureLeft(shown.expiresAt),
        ) { picture = null }
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
                if (view.picks.active) {
                    PickBar(view.picks, actions)
                } else {
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
                }
            }
        },
        bottomBar = {
            // The chat box is the page's floor: snacks sit just above it.
            Box(Modifier.snackFloor()) {
                when {
                    view.failed -> Unit
                    view.loading -> ChatComposerLoading()
                    !view.canPost || view.state != ChatState.OPEN -> ChatClosedBar(view.closedLine)
                    else -> ChatComposer(view, actions, field)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                view.failed -> RobotOffline("Chat didn't load", onRetry = actions.onRetry)
                view.loading -> ChatSkeleton()
                !view.talk -> ChatEmpty(view, actions)
                else -> ChatThreadList(view, actions)
            }
            if (view.seeking) SeekingNote(Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
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
                .padding(horizontal = ChatGutter),
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
    // A tapped quote: the original moves to the middle, then lights up.
    var flash by remember { mutableStateOf<ChatJump?>(null) }
    JumpWhenAsked(state, rows, view.jump) { flash = it }
    val shown = if (flash == null) view else view.copy(flash = flash)
    LazyColumn(
        Modifier.fillMaxSize(),
        state = state,
        reverseLayout = true,
        // Offline stays over the thread, so the newest message ends above it.
        contentPadding = PaddingValues(start = ChatGutter, end = ChatGutter, top = 4.dp, bottom = 10.dp + snackRoom()),
    ) {
        items(rows, key = { it.key }) { row -> ChatRowView(row, shown, actions) }
        if (view.loadingOlder) item(key = "older") { ChatOlderSkeleton() }
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
            val day = instantOf(detailsUntil)?.let { shortDate(it) }
            "$crewName has ended, so this chat is read only." + (day?.let { " It goes with the crew's details on $it." } ?: "")
        }
    }

/** A member as their profile shows them, from the crew when it is held, else from the chat. */
internal fun chatProfileOf(
    seatId: String,
    person: ChatPerson?,
    crew: Crew?,
): CrewTester {
    val member = (crew?.members.orEmpty() + crew?.volunteers.orEmpty()).firstOrNull { it.id == seatId }
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
        volunteer = person?.volunteer == true || member?.volunteer == true,
        firstNight = member?.firstNight ?: 1,
    )
}
