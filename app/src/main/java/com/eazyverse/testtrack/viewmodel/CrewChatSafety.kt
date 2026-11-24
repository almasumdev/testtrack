package com.eazyverse.testtrack.viewmodel

import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatReport
import com.eazyverse.testtrack.domain.ChatReportReason
import com.eazyverse.testtrack.domain.ChatSelect
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ChatReportIn
import com.eazyverse.testtrack.net.acceptChatRules
import com.eazyverse.testtrack.net.block
import com.eazyverse.testtrack.net.blocks
import com.eazyverse.testtrack.net.chat
import com.eazyverse.testtrack.net.reportChat
import com.eazyverse.testtrack.net.reportPerson
import com.eazyverse.testtrack.net.unblock
import com.eazyverse.testtrack.ui.Snacks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * The chat's safety side: reporting, blocking and the chat rules. A block, an unblock or a report
 * changes what an old message shows without changing its `rev`, so each is followed by a full
 * read of the chat from the top, never by waiting for a poll.
 */

private const val THANKS = "Thanks. The team will look at it."
private const val REPORTED_NOT_BLOCKED = "We got your report. Blocking them didn't work, try again."

/** What a line waiting for the rules says if they are not agreed to. */
internal const val RULES_DECLINED_LINE = "Agree to the chat rules to send."

/**
 * Who this reader blocked, read when the chat opens and each time it comes back on screen. Quiet:
 * the stubs themselves come with the chat. With [rereadIfChanged] a list that differs from what was
 * held reads the chat again, since an unblocked member's old messages are stubs until then.
 */
internal fun CrewChatViewModel.loadBlocked(rereadIfChanged: Boolean = false) {
    val seen = blockedChanges
    val mine = ++blockedReads
    viewModelScope.launch {
        try {
            val list = Api.blocks().mapTo(LinkedHashSet()) { it.userId }
            // A block or unblock made meanwhile is newer than this list, and so is a later read: they stand.
            if (seen != blockedChanges || mine != blockedReads) return@launch
            val differs = list != blocked
            blocked = list
            if (differs && rereadIfChanged) reloaded(changed = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}

/** The newest page again, replacing what is held. */
private suspend fun CrewChatViewModel.reload() {
    apply(Api.chat(crewId), fresh = true)
}

/** [reload], and where [changed] says the screen must follow, a Try again when it cannot. */
private suspend fun CrewChatViewModel.reloaded(changed: Boolean) {
    try {
        reload()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        if (changed) Snacks.offline { viewModelScope.launch { reloaded(true) } }
    }
}

private fun failure(
    e: Exception,
    retry: () -> Unit,
) {
    if (e is ApiError) Snacks.error(e.message) else Snacks.offline(retry)
}

/**
 * Tells the team about [line], with [reason] and a [note] ("Something else" needs one). With
 * [alsoBlock] its author is blocked as well. The answer is a thank-you, since nothing on screen
 * shows a report; then the chat is read again.
 */
fun CrewChatViewModel.report(
    line: ChatLine,
    reason: ChatReportReason,
    note: String,
    alsoBlock: Boolean = false,
) {
    if (!ChatSelect.canReport(line) || !ChatReport.ready(reason, note)) return
    val author = line.authorId?.takeIf { alsoBlock }
    viewModelScope.launch {
        try {
            Api.reportChat(crewId, line.id, ChatReportIn(reason.code, ChatReport.note(note)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(e) { report(line, reason, note, alsoBlock) }
            return@launch
        }
        val blockedNow = author == null || tryBlock(author)
        reloaded(changed = author != null)
        if (blockedNow) Snacks.done(THANKS) else Snacks.error(REPORTED_NOT_BLOCKED) { block(author.orEmpty()) }
    }
}

/** Tells the team about a person in this chat, by [userId] from its people. */
fun CrewChatViewModel.reportPerson(
    userId: String,
    reason: ChatReportReason,
    note: String,
) {
    if (userId.isBlank() || !ChatReport.ready(reason, note)) return
    viewModelScope.launch {
        try {
            Api.reportPerson(crewId, userId, ChatReportIn(reason.code, ChatReport.note(note)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(e) { reportPerson(userId, reason, note) }
            return@launch
        }
        reloaded(changed = false)
        Snacks.done(THANKS)
    }
}

/** Their messages go quiet in every crew chat. The stubs show once the chat is read again. */
fun CrewChatViewModel.block(userId: String) {
    if (userId.isBlank()) return
    viewModelScope.launch {
        try {
            Api.block(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(e) { block(userId) }
            return@launch
        }
        blocked = blocked + userId
        blockedChanges++
        reloaded(changed = true)
    }
}

/** Their messages show again, once the chat is read again. */
fun CrewChatViewModel.unblock(userId: String) {
    if (userId.isBlank()) return
    viewModelScope.launch {
        try {
            Api.unblock(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure(e) { unblock(userId) }
            return@launch
        }
        blocked = blocked - userId
        blockedChanges++
        reloaded(changed = true)
    }
}

/** The block as part of a report: false when it did not go. */
private suspend fun CrewChatViewModel.tryBlock(userId: String): Boolean =
    try {
        Api.block(userId)
        blocked = blocked + userId
        blockedChanges++
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

/**
 * Asks the reader to agree to the chat rules ([CrewChatViewModel.rulesAsk] shows the dialog).
 * [then] goes ahead once they have; [declined] runs if they say not now. Asked twice before an
 * answer, both go ahead.
 */
fun CrewChatViewModel.askRules(
    then: () -> Unit,
    declined: () -> Unit = {},
) {
    val before = afterRules.takeIf { rulesAsk }
    val beforeNo = rulesDeclined.takeIf { rulesAsk }
    afterRules = {
        before?.invoke()
        then()
    }
    rulesDeclined = {
        beforeNo?.invoke()
        declined()
    }
    rulesAsk = true
}

/** "I agree": the server keeps it, then what was waiting goes ahead, then [then]. */
fun CrewChatViewModel.acceptRules(then: (() -> Unit)? = null) {
    rulesAsk = false
    viewModelScope.launch {
        try {
            val done = Api.acceptChatRules(maxOf(rulesVersion, 1))
            if (done.rulesVersion > 0) rulesVersion = done.rulesVersion
            rulesNeeded = done.rulesNeeded
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is ApiError) {
                Snacks.error(e.message)
                declineRules()
            } else {
                // The line that was waiting is marked not sent, so it never hangs as sending. The
                // snack's retry only asks the server again; once it agrees, the line goes (once).
                rulesDeclined?.invoke()
                Snacks.offline { acceptRules(then) }
            }
            return@launch
        }
        if (rulesNeeded) {
            // The rules moved on while they read: the same ask, once more.
            rulesAsk = true
            return@launch
        }
        val go = afterRules
        afterRules = null
        rulesDeclined = null
        go?.invoke()
        then?.invoke()
    }
}

/** "Not now": nothing is sent, and a send that was waiting is marked not sent. */
fun CrewChatViewModel.declineRules() {
    rulesAsk = false
    val no = rulesDeclined
    afterRules = null
    rulesDeclined = null
    no?.invoke()
}
