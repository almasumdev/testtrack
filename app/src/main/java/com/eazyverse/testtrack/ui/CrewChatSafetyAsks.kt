package com.eazyverse.testtrack.ui

import androidx.compose.runtime.Composable
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.viewmodel.CrewChatViewModel
import com.eazyverse.testtrack.viewmodel.acceptRules
import com.eazyverse.testtrack.viewmodel.block
import com.eazyverse.testtrack.viewmodel.declineRules
import com.eazyverse.testtrack.viewmodel.report
import com.eazyverse.testtrack.viewmodel.reportPerson

/** What the chat's safety dialogs are asking about: reporting or blocking [name] (their account is [userId]). */
internal data class ChatSafetyAsk(
    val kind: Kind,
    val userId: String,
    val name: String,
    /** The message being reported; null when it is the person. */
    val line: ChatLine? = null,
) {
    enum class Kind { REPORT, BLOCK }
}

/** The dialogs of the chat's safety side: the rules, when they are needed, and [ask] while one is open. */
@Composable
internal fun ChatSafetyAsks(
    vm: CrewChatViewModel,
    ask: ChatSafetyAsk?,
    onClose: () -> Unit,
) {
    if (vm.rulesAsk) ChatRulesDialog(onAgree = { vm.acceptRules() }, onNotNow = vm::declineRules)
    ask ?: return
    when (ask.kind) {
        ChatSafetyAsk.Kind.REPORT ->
            ChatReportDialog(
                name = ask.name,
                person = ask.line == null,
                offerBlock = ask.line != null,
                onSend = { reason, note, alsoBlock ->
                    onClose()
                    if (ask.line != null) vm.report(ask.line, reason, note, alsoBlock) else vm.reportPerson(ask.userId, reason, note)
                },
                onDismiss = onClose,
            )
        ChatSafetyAsk.Kind.BLOCK ->
            Ask(
                title = "Block ${ask.name}?",
                body =
                    "You won't see their messages in any crew chat, and their mentions won't reach you. " +
                        "They aren't told. You can unblock them in Settings.",
                confirm = "Block",
                onConfirm = {
                    onClose()
                    vm.block(ask.userId)
                },
                onDismiss = onClose,
            )
    }
}
