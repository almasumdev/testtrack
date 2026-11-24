package com.eazyverse.testtrack.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import com.eazyverse.testtrack.data.ProofImages
import com.eazyverse.testtrack.data.ServerUpload
import com.eazyverse.testtrack.data.Session
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.FeedbackIn
import com.eazyverse.testtrack.net.sendFeedback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a private feedback picture is on its way to the server. */
enum class UploadState { SENDING, FAILED, SENT }

/** A private feedback picture on its way, shown on its app's tile until the crews say it landed. */
data class FeedbackUpload(
    val uri: Uri,
    val label: String,
    val state: UploadState,
    /** The signed-in account it belongs to: nobody else sees it or sends it again. */
    val account: String?,
    /** The picture's id, made before the first try so a lost answer is tried again under the same one. */
    val pictureId: String,
    /** The server holds the picture, so another try sends only the feedback. */
    val uploaded: Boolean = false,
    /** When it was sent, so a sent one never outlives its night. */
    val sentAt: Long = 0L,
) {
    val failed get() = state == UploadState.FAILED
    val sent get() = state == UploadState.SENT
}

/**
 * The private feedback pictures being sent, kept for the life of the process so leaving a crew's page
 * never stops one, and the Run tab and a crew's page both see the same ones. A sent one stays until the
 * crews read that the seat has its feedback ([clearSent]), or a day passes. Each belongs to the account
 * that sent it and goes with it ([clear] at sign out).
 */
object CrewFeedbackUploads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = HashMap<String, Job>()
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Ticks each time a picture's feedback has landed, so a screen reads the crews again. */
    val landed: SharedFlow<Unit> = ticks.asSharedFlow()

    private const val SENT_KEPT_MS = 24 * 60 * 60 * 1000L

    private val uploads = mutableStateMapOf<String, FeedbackUpload>()

    /** The upload for [seatId] that belongs to the signed-in account, or null. */
    fun of(seatId: String): FeedbackUpload? = uploads[seatId]?.takeIf { it.account == Session.email }

    /** The seats with a sent picture, so a screen can read the crews against them. */
    fun sentSeats(): Set<String> = uploads.filterValues { it.sent && it.account == Session.email }.keys

    /** Starts sending [uri] for [seatId]; turned away while that seat already has one going, failed or sent. */
    fun send(
        context: Context,
        seatId: String,
        label: String,
        uri: Uri,
    ): Boolean {
        if (of(seatId) != null) return false
        uploads[seatId] = FeedbackUpload(uri, label, UploadState.SENDING, Session.email, ServerUpload.newId())
        run(context, seatId)
        return true
    }

    /** Another try at a picture that did not go, from the copy kept on the phone. */
    fun retry(
        context: Context,
        seatId: String,
    ) {
        val up = of(seatId)?.takeIf { it.failed } ?: return
        uploads[seatId] = up.copy(state = UploadState.SENDING)
        run(context, seatId)
    }

    /** Takes away the sent ones the crews now show with their feedback ([landed]), or a day old. */
    fun clearSent(landed: Set<String>) {
        val now = System.currentTimeMillis()
        uploads.filterValues { it.sent }.forEach { (seat, up) ->
            if (seat in landed || now - up.sentAt > SENT_KEPT_MS) uploads.remove(seat)
        }
    }

    /** Signed out: every picture still on its way is stopped and forgotten. */
    fun clear() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        uploads.clear()
    }

    /** Changes the entry only while it is still there, so a stopped upload never brings one back. */
    private fun change(
        seatId: String,
        edit: (FeedbackUpload) -> FeedbackUpload,
    ): FeedbackUpload? = uploads[seatId]?.let(edit)?.also { uploads[seatId] = it }

    private fun run(
        context: Context,
        seatId: String,
    ) {
        val app = context.applicationContext
        jobs[seatId]?.cancel()
        jobs[seatId] =
            scope.launch {
                try {
                    var up = uploads[seatId] ?: return@launch
                    if (!up.uploaded) {
                        val id =
                            ServerUpload.send(
                                up.pictureId,
                                ServerUpload.CREW_FEEDBACK,
                                "image/jpeg",
                                seatId = seatId,
                                remint = { fresh -> change(seatId) { it.copy(pictureId = fresh) } },
                            ) { ProofImages.bytes(app, up.uri, ProofImages.CREW_MAX_BYTES) }
                        up = change(seatId) { it.copy(pictureId = id, uploaded = true) } ?: return@launch
                    }
                    Api.sendFeedback(seatId, FeedbackIn(up.pictureId))
                    change(seatId) { it.copy(state = UploadState.SENT, sentAt = System.currentTimeMillis()) } ?: return@launch
                    withContext(Dispatchers.IO) { ProofEditing.discard(app, up.uri) }
                    ticks.tryEmit(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    change(seatId) { it.copy(state = UploadState.FAILED) }
                }
            }
    }
}
