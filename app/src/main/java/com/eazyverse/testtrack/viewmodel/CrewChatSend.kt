package com.eazyverse.testtrack.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.ProofImages
import com.eazyverse.testtrack.data.ServerUpload
import com.eazyverse.testtrack.domain.ChatAllowance
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatPicture
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.domain.SendState
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiChatLeft
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ChatPostIn
import com.eazyverse.testtrack.net.deleteChat
import com.eazyverse.testtrack.net.sendChat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** What a line still unsent needs to go again: the same id, so a repeated try is the same message. */
internal data class ChatPending(
    val text: String,
    val replyTo: String?,
    /** The picture's id, and where it is on the phone until the server has it. */
    val pictureId: String? = null,
    val pictureUri: String? = null,
    val pictureSent: Boolean = false,
)

/** The words written, sent: shown at once as sending, then as the server keeps them. */
fun CrewChatViewModel.send() {
    if (!ChatAllowance.canSend(draft, problem, messagesLeft, canPost)) return
    val id = ServerUpload.newId()
    val text = draft.toWire()
    val line = local(id, text)
    pending[id] = ChatPending(text, replyTo?.id)
    lines = lines + line
    clearDraft()
    post(id)
}

/**
 * A picture from the phone, with whatever is written as its caption. It goes through the same
 * picture pipeline as the night's pictures, under an id written down first, then as a message.
 */
fun CrewChatViewModel.sendPicture(
    context: Context,
    uri: Uri,
) {
    if (!ChatAllowance.canPickPicture(problem, messagesLeft, picturesLeft, canPost)) {
        message = if (picturesLeft <= 0) "No pictures left today." else "That can't be sent right now."
        return
    }
    val id = ServerUpload.newId()
    val pictureId = ServerUpload.newId()
    val text = draft.toWire()
    val line = local(id, text, ChatPicture(pictureId, local = uri.toString()))
    pending[id] = ChatPending(text, replyTo?.id, pictureId, uri.toString())
    lines = lines + line
    clearDraft()
    post(id, context.applicationContext)
}

/** A failed line, tried again under its own id. */
fun CrewChatViewModel.retry(
    line: ChatLine,
    context: Context,
) {
    if (line.send != SendState.FAILED || pending[line.id] == null) return
    lines = lines.map { if (it.id == line.id) it.copy(send = SendState.SENDING, error = "") else it }
    post(line.id, context.applicationContext)
}

/** One of your own messages, gone for everyone. Its row stays as a deleted line. */
fun CrewChatViewModel.delete(line: ChatLine) {
    if (!line.mine || line.deleted) return
    if (line.send != SendState.SENT) {
        // Never reached the server: it only has to go from here.
        pending.remove(line.id)
        lines = lines.filterNot { it.id == line.id }
        return
    }
    viewModelScope.launch {
        try {
            val gone = Api.deleteChat(crewId, line.id)
            lines = ChatThread.merge(lines, listOf(gone.toLine(limits.pictureHours)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = (e as? ApiError)?.message ?: "Couldn't delete it. Try again."
        }
    }
}

private fun CrewChatViewModel.post(
    id: String,
    context: Context? = null,
) {
    val job = pending[id] ?: return
    viewModelScope.launch {
        try {
            var pictureId = job.pictureId
            if (pictureId != null && !job.pictureSent) {
                val uri = Uri.parse(requireNotNull(job.pictureUri))
                val app = requireNotNull(context)
                pictureId =
                    ServerUpload.send(
                        pictureId,
                        ServerUpload.CHAT,
                        "image/jpeg",
                        seatId = mySeat,
                        remint = { fresh -> pending[id] = job.copy(pictureId = fresh) },
                    ) { ProofImages.bytes(app, uri) }
                pending[id] = (pending[id] ?: job).copy(pictureId = pictureId, pictureSent = true)
            }
            val sent = Api.sendChat(crewId, ChatPostIn(id, job.text, job.replyTo, pictureId))
            pending.remove(id)
            left(sent.left)
            lines = ChatThread.merge(lines, listOf(sent.message.toLine(limits.pictureHours)))
            pollSoon()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(id, e)
        }
    }
}

/** The line stays, marked not sent, with what the server said when it is worth saying. */
private fun CrewChatViewModel.failed(
    id: String,
    e: Exception,
) {
    val error = e as? ApiError
    val why =
        when (error?.code) {
            "chat_limit" -> {
                val kind = error.details["kind"]
                val left = ApiChatLeft(messages = messagesLeft, pictures = picturesLeft)
                left(if (kind == "pictures") left.copy(pictures = 0) else left.copy(messages = 0))
                if (kind == "pictures") "No pictures left today." else "No messages left today."
            }
            "link_not_allowed" -> "Links can't be sent here."
            "chat_closed", "chat_gone", "chat_paused" -> {
                pollSoon()
                error.message
            }
            null -> ""
            else -> error.message
        }
    lines = lines.map { if (it.id == id) it.copy(send = SendState.FAILED, error = why) else it }
}
