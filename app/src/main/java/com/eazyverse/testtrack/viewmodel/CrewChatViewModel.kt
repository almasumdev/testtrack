package com.eazyverse.testtrack.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Wifi
import com.eazyverse.testtrack.data.ChatNotifications
import com.eazyverse.testtrack.domain.ChatAllowance
import com.eazyverse.testtrack.domain.ChatDraft
import com.eazyverse.testtrack.domain.ChatKind
import com.eazyverse.testtrack.domain.ChatLimits
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatMention
import com.eazyverse.testtrack.domain.ChatPerson
import com.eazyverse.testtrack.domain.ChatPicture
import com.eazyverse.testtrack.domain.ChatPolling
import com.eazyverse.testtrack.domain.ChatQuote
import com.eazyverse.testtrack.domain.ChatSignals
import com.eazyverse.testtrack.domain.ChatState
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.domain.DraftProblem
import com.eazyverse.testtrack.domain.SendState
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiChatLeft
import com.eazyverse.testtrack.net.ApiChatPage
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.chat
import com.eazyverse.testtrack.net.muteChat
import com.eazyverse.testtrack.net.readChat
import com.eazyverse.testtrack.ui.Snack
import com.eazyverse.testtrack.ui.SnackTone
import com.eazyverse.testtrack.ui.Snacks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/** Said when a tapped quote's message was deleted, or is no longer kept. */
private const val GONE_LINE = "That message is gone."

/** The offline snack's key: it stays until the chat is back, or the page is gone. */
private const val OFFLINE = "chat-offline"

/**
 * One crew's chat: what is held of it, the poll that keeps it current while it is on screen, and
 * what is being written.
 *
 * Polled by a cursor, never over the live socket: the first read takes the newest page, and each
 * poll after asks only for what changed since the last `rev`. The poll runs only while the chat is
 * on screen ([start] on resume, [stop] on pause), one request at a time, and slows down while
 * nothing changes. Sending, deleting and pictures are in CrewChatSend.kt.
 */
class CrewChatViewModel : ViewModel() {
    var crewId by mutableStateOf("")
        private set

    /** The first read has not answered yet. */
    var loading by mutableStateOf(true)
        private set

    /** The first read failed: the chat shows the robot and Try again, with no composer. */
    var failed by mutableStateOf(false)
        private set

    var state by mutableStateOf(ChatState.OPEN)
        private set
    var canPost by mutableStateOf(false)
        private set

    /** Why posting is closed: blank, ended, out or paused. */
    var whyNot by mutableStateOf("")
        private set
    var lines by mutableStateOf<List<ChatLine>>(emptyList())
        internal set
    var people by mutableStateOf<Map<String, ChatPerson>>(emptyMap())
        private set
    var muted by mutableStateOf(false)
        private set
    var messagesLeft by mutableStateOf(0)
        internal set
    var picturesLeft by mutableStateOf(0)
        internal set
    var limits by mutableStateOf(ChatLimits())
        internal set
    var botName by mutableStateOf("Crew bot")
        private set

    /** Older messages wait on the server. */
    var moreBefore by mutableStateOf(false)
        private set
    var loadingOlder by mutableStateOf(false)
        private set

    /** Older pages are being read to find a message a tapped quote points at. */
    var seeking by mutableStateOf(false)
        private set

    /**
     * Where the "new" line goes and what it counts: what was unread as the chat was opened, from the
     * read mark to the newest then. Kept while it is open, so it does not grow as the reader watches.
     */
    var newFromRev by mutableStateOf<Long?>(null)
        private set
    var newToRev by mutableStateOf(Long.MAX_VALUE)
        private set

    /** The user ids of the members this reader blocked, loaded when the chat opens. */
    var blocked by mutableStateOf<Set<String>>(emptySet())
        internal set

    /** Counts block and unblock made here, so an older read of the list does not overwrite a newer change. */
    internal var blockedChanges = 0

    /** The reader has not agreed to the chat rules yet. A send then asks first. */
    var rulesNeeded by mutableStateOf(false)
        internal set

    /** The rules version the server asks for; 0 until it says. */
    internal var rulesVersion = 0

    /** The rules dialog should be showing: [acceptRules] or [declineRules] answers it. */
    var rulesAsk by mutableStateOf(false)
        internal set

    /** What goes ahead once the rules are agreed, and what happens to a send they were asked for if not. */
    internal var afterRules: (() -> Unit)? = null
    internal var rulesDeclined: (() -> Unit)? = null

    var draft by mutableStateOf(ChatDraft())
        private set

    /** The message being answered, shown over the field. */
    var replyTo by mutableStateOf<ChatLine?>(null)
        private set

    /** This account's seat in the crew, which its messages are sent as. */
    val mySeat: String? get() = people.values.firstOrNull { it.mine }?.seatId

    /** The crew's own apps, which may be named by package without counting as links. */
    val packages: Set<String> get() = people.values.mapNotNull { it.packageName.ifBlank { null } }.toSet()

    /** What is wrong with the message as written, if anything. */
    val problem: DraftProblem get() = ChatAllowance.problem(draft, limits, packages)

    /** What a line still unsent needs to be tried again, by its id. */
    internal val pending = mutableMapOf<String, ChatPending>()

    private var cursor = 0L
    private var loaded = false
    private var emptyPolls = 0
    private var failures = 0
    private var poller: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /** Which crew. Changing it starts over. */
    fun bind(id: String) {
        if (id == crewId) return
        stop()
        crewId = id
        loading = true
        failed = false
        lines = emptyList()
        people = emptyMap()
        draft = ChatDraft()
        replyTo = null
        blocked = emptySet()
        blockedChanges++
        rulesAsk = false
        afterRules = null
        rulesDeclined = null
        cursor = 0
        loaded = false
        newFromRev = null
        newToRev = Long.MAX_VALUE
    }

    /** On screen: read, then keep reading. A push for this crew is not drawn meanwhile. */
    fun start(
        context: Context,
        id: String,
    ) {
        bind(id)
        if (crewId.isBlank()) return
        ChatSignals.openCrew = crewId
        ChatNotifications.cancel(context.applicationContext, crewId)
        if (poller?.isActive == true) return
        // Back on screen: Settings may have unblocked someone since. The first read does its own.
        if (loaded) loadBlocked()
        poller = viewModelScope.launch { loop() }
    }

    /** Off screen: no polls, and pushes for this crew are drawn again. */
    fun stop() {
        poller?.cancel()
        poller = null
        if (ChatSignals.openCrew == crewId) ChatSignals.openCrew = null
    }

    /** After a failed first read. */
    fun retry() {
        failed = false
        loading = true
        poller?.cancel()
        poller = viewModelScope.launch { loop() }
    }

    /** Poll now rather than when the wait is up: after a send, or a push for this crew. */
    internal fun pollSoon() {
        emptyPolls = 0
        wake.trySend(Unit)
    }

    private suspend fun loop() {
        if (!loaded) {
            if (!first()) return
        }
        while (state != ChatState.GONE) {
            val wait = ChatPolling.nextDelayMs(limits.pollSeconds, limits.pollMaxSeconds, emptyPolls, failures)
            withTimeoutOrNull(wait) { wake.receive() }
            poll()
        }
    }

    /** The newest page. False when it failed and the loop should stop. */
    private suspend fun first(): Boolean =
        try {
            val page = Api.chat(crewId)
            apply(page, fresh = true)
            loaded = true
            loading = false
            failed = false
            markRead(page)
            loadBlocked()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            loading = false
            failed = true
            false
        }

    private suspend fun poll() {
        try {
            val page = Api.chat(crewId, since = cursor)
            val brought = page.messages.isNotEmpty() || page.reset
            apply(page, fresh = page.reset)
            if (failures >= ChatPolling.OFFLINE_AFTER) {
                Snacks.show(Snack("Back online.", SnackTone.DONE, key = OFFLINE, icon = Lucide.Wifi))
            }
            failures = 0
            emptyPolls = if (brought || page.moreAfter) 0 else emptyPolls + 1
            if (page.moreAfter) wake.trySend(Unit)
            if (page.messages.any { !it.mine && it.kind != "bot" }) markRead(page)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failures++
            if (failures == ChatPolling.OFFLINE_AFTER) {
                Snacks.show(Snack("You're offline. The chat catches up once you're back.", SnackTone.OFFLINE, sticky = true, key = OFFLINE))
            }
        }
    }

    internal fun apply(
        page: ApiChatPage,
        fresh: Boolean,
    ) {
        if (page.people.isNotEmpty()) people = page.people.associate { it.seatId to it.toPerson() }
        state = ChatState.of(page.state).takeIf { it != ChatState.OFF } ?: ChatState.OPEN
        canPost = page.canPost
        whyNot = page.whyNot
        muted = page.muted
        limits = page.limits.toLimits()
        // A poll that leaves the allowance out keeps what is held rather than resetting it.
        if (fresh || page.left.messages >= 0) left(page.left)
        botName = page.bot.name.ifBlank { botName }
        // A short poll may leave the rules out: it can ask for them, never take the ask back.
        if (fresh || page.rulesNeeded) rulesNeeded = page.rulesNeeded
        if (page.rulesVersion > 0) rulesVersion = page.rulesVersion
        val incoming = page.messages.map { it.toLine(limits.pictureHours) }
        lines = ChatThread.merge(lines, incoming, reset = fresh)
        if (page.rev > 0) cursor = page.rev
        if (fresh) {
            moreBefore = page.moreBefore
            if (newFromRev == null) {
                newFromRev = page.readRev
                newToRev = page.rev
            }
        }
    }

    /** What is left today; a server that does not say leaves the whole day's allowance. */
    internal fun left(left: ApiChatLeft) {
        messagesLeft = if (left.messages < 0) limits.messagesPerDay else left.messages
        picturesLeft = if (left.pictures < 0) limits.picturesPerDay else left.pictures
    }

    /** Everything up to the page's `rev` is read. Quiet: a failure is tried with the next poll. */
    private fun markRead(page: ApiChatPage) {
        if (page.rev <= page.readRev) return
        val rev = page.rev
        viewModelScope.launch {
            try {
                Api.readChat(crewId, rev)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    /** The page before the oldest held, when the list is scrolled up to it. */
    fun loadOlder() {
        if (ChatThread.oldest(lines) == null || !moreBefore || loadingOlder) return
        loadingOlder = true
        viewModelScope.launch {
            try {
                older()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Tried again on the next scroll to the top.
            } finally {
                loadingOlder = false
            }
        }
    }

    /** Reads the page before the oldest held into [lines]. False with nothing held to read before. */
    private suspend fun older(): Boolean {
        val before = ChatThread.oldest(lines) ?: return false
        val page = Api.chat(crewId, before = before)
        if (page.people.isNotEmpty()) people = people + page.people.associate { it.seatId to it.toPerson() }
        lines = ChatThread.merge(lines, page.messages.map { it.toLine(limits.pictureHours) })
        moreBefore = page.moreBefore
        return true
    }

    /**
     * Finds the message [quote] points at for [onFound], reading older pages until it is held. One
     * deleted, or no longer on the server (the chat keeps only so much), gets a line saying so.
     */
    fun seek(
        quote: ChatQuote,
        onFound: (String) -> Unit,
    ) {
        if (seeking) return
        val held = lines.firstOrNull { it.id == quote.id }
        when {
            quote.deleted || held?.deleted == true -> Snacks.info(GONE_LINE)
            held != null -> onFound(held.id)
            !moreBefore -> Snacks.info(GONE_LINE)
            else -> {
                seeking = true
                loadingOlder = true
                viewModelScope.launch {
                    try {
                        while (lines.none { it.id == quote.id } && moreBefore) {
                            if (!older()) break
                        }
                        val found = lines.firstOrNull { it.id == quote.id }
                        if (found == null || found.deleted) Snacks.info(GONE_LINE) else onFound(found.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        Snacks.offline { seek(quote, onFound) }
                    } finally {
                        seeking = false
                        loadingOlder = false
                    }
                }
            }
        }
    }

    /** Mute or unmute. Shown at once, and put back if the server says no. */
    fun toggleMute() {
        val want = !muted
        muted = want
        viewModelScope.launch {
            try {
                val done = Api.muteChat(crewId, want)
                muted = done.muted
                limits = done.limits.toLimits()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                muted = !want
                if (e is ApiError) Snacks.error(e.message) else Snacks.offline(::toggleMute)
            }
        }
    }

    fun edit(next: ChatDraft) {
        draft = next
    }

    fun reply(line: ChatLine) {
        if (!line.deleted) replyTo = line
    }

    fun clearReply() {
        replyTo = null
    }

    internal fun clearDraft() {
        draft = ChatDraft()
        replyTo = null
    }

    /** A line written here before the server has it. */
    internal fun local(
        id: String,
        text: String,
        picture: ChatPicture? = null,
    ) = ChatLine(
        id = id,
        kind = ChatKind.USER,
        authorSeatId = mySeat,
        mine = true,
        text = text,
        mentions =
            draft.spans.map { span ->
                val who = people[span.seatId]
                ChatMention(
                    app = span.app,
                    seatId = span.seatId,
                    label = if (span.app) who?.appName.orEmpty() else who?.name.orEmpty(),
                )
            },
        quote =
            replyTo?.let {
                ChatQuote(it.id, it.authorSeatId, it.text, picture = it.picture != null)
            },
        picture = picture,
        at = Instant.now(),
        send = SendState.SENDING,
    )

    override fun onCleared() {
        stop()
        // Offline is about this chat: it goes with the page.
        Snacks.dismiss(OFFLINE)
        super.onCleared()
    }
}
