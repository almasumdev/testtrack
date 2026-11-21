package com.eazyverse.testtrack.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.ProofImages
import com.eazyverse.testtrack.data.ServerUpload
import com.eazyverse.testtrack.domain.ChatAllowance
import com.eazyverse.testtrack.domain.ChatDraft
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
import com.eazyverse.testtrack.ui.Snacks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
 * An edited picture and its caption, sent as one message. The JPEG is kept in the cache under the
 * picture's id, so the line shows it at once and a failed send can go again. It goes through the
 * same picture pipeline as the night's pictures, then as a message. False when it cannot go now.
 */
suspend fun CrewChatViewModel.sendPicture(
    context: Context,
    jpeg: ByteArray,
    caption: ChatDraft,
): Boolean {
    val captionProblem = ChatAllowance.problem(caption, limits, packages)
    if (!ChatAllowance.canPickPicture(captionProblem, messagesLeft, picturesLeft, canPost)) {
        if (picturesLeft <= 0) Snacks.info("No pictures left today.") else Snacks.error("That can't be sent right now.")
        return false
    }
    val app = context.applicationContext
    val id = ServerUpload.newId()
    val pictureId = ServerUpload.newId()
    val file = withContext(Dispatchers.IO) { keepPicture(app, pictureId, jpeg) }
    val uri = Uri.fromFile(file).toString()
    // The caption becomes the draft for a moment, so the line carries its mentions.
    edit(caption)
    val text = draft.toWire()
    val line = local(id, text, ChatPicture(pictureId, local = uri))
    pending[id] = ChatPending(text, replyTo?.id, pictureId, uri)
    lines = lines + line
    clearDraft()
    post(id, app)
    return true
}

/** The picture as it goes, in the cache. Ones left from two days back are cleared on the way. */
private fun keepPicture(
    context: Context,
    pictureId: String,
    jpeg: ByteArray,
): File {
    val dir = File(context.cacheDir, "chat-send").apply { mkdirs() }
    val old = System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L
    dir.listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
    return File(dir, "$pictureId.jpg").apply { writeBytes(jpeg) }
}

/** The bytes of a picture waiting to go: an edited one as it was kept, anything else made ready again. */
private fun pictureBytes(
    context: Context,
    uri: Uri,
): ByteArray = if (uri.scheme == "file") File(requireNotNull(uri.path)).readBytes() else ProofImages.bytes(context, uri)

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
            limits = gone.limits.toLimits()
            left(gone.left)
            lines = ChatThread.merge(lines, listOf(gone.message.toLine(limits.pictureHours)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is ApiError) Snacks.error(e.message) else Snacks.error("Couldn't delete it.") { delete(line) }
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
                // A volunteer's "seat" in the chat is their volunteer id, which is no seat: the crew goes instead.
                val seat = people.values.firstOrNull { it.mine && !it.volunteer }?.seatId
                pictureId =
                    ServerUpload.send(
                        pictureId,
                        ServerUpload.CHAT,
                        "image/jpeg",
                        seatId = seat,
                        crewId = if (seat == null) crewId else null,
                        remint = { fresh -> pending[id] = job.copy(pictureId = fresh) },
                    ) { pictureBytes(app, uri) }
                pending[id] = (pending[id] ?: job).copy(pictureId = pictureId, pictureSent = true)
            }
            val sent = Api.sendChat(crewId, ChatPostIn(id, job.text, job.replyTo, pictureId))
            pending.remove(id)
            limits = sent.limits.toLimits()
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
            "chat_closed", "chat_gone", "chat_paused", "chat_off" -> {
                pollSoon()
                error.message
            }
            null -> ""
            else -> error.message
        }
    lines = lines.map { if (it.id == id) it.copy(send = SendState.FAILED, error = why) else it }
}
