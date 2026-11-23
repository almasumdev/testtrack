package com.eazyverse.testtrack.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import com.eazyverse.testtrack.data.ProofImages
import com.eazyverse.testtrack.data.ServerUpload
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.FeedbackIn
import com.eazyverse.testtrack.net.sendFeedback
import com.eazyverse.testtrack.ui.Snacks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a private feedback picture is on its way to the server. */
enum class UploadState { SENDING, FAILED, SENT }

/** A private feedback picture on its way, shown on its app's tile until the crews say it landed. */
data class FeedbackUpload(
    val uri: Uri,
    val label: String,
    val state: UploadState,
    /** The picture's id once the server holds it, so another try sends only the feedback, not the picture again. */
    val uploadedId: String? = null,
) {
    val failed get() = state == UploadState.FAILED
    val sent get() = state == UploadState.SENT
}

/**
 * The private feedback pictures being sent, kept for the life of the process so leaving a crew's page
 * never stops one, and the Run tab and a crew's page both see the same ones. Only the crews' own answer
 * clears a sent one ([clearSent]): until then the tile can't offer Write for feedback that has gone.
 */
object CrewFeedbackUploads {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** By seat. */
    val uploads = mutableStateMapOf<String, FeedbackUpload>()

    /** Starts sending [uri] for [seatId]; turned away while that seat already has one going, failed or sent. */
    fun send(
        context: Context,
        seatId: String,
        label: String,
        uri: Uri,
        reload: () -> Unit,
    ): Boolean {
        if (uploads.containsKey(seatId)) return false
        uploads[seatId] = FeedbackUpload(uri, label, UploadState.SENDING)
        run(context, seatId, reload)
        return true
    }

    /** Another try at a picture that did not go, from the copy kept on the phone. */
    fun retry(
        context: Context,
        seatId: String,
        reload: () -> Unit,
    ) {
        val up = uploads[seatId]?.takeIf { it.failed } ?: return
        uploads[seatId] = up.copy(state = UploadState.SENDING)
        run(context, seatId, reload)
    }

    /** Takes away the sent ones whose seat [owes] says no longer owes feedback, as the crews now read. */
    fun clearSent(owes: (String) -> Boolean) {
        uploads.filterValues { it.sent }.keys.forEach { if (!owes(it)) uploads.remove(it) }
    }

    private fun run(
        context: Context,
        seatId: String,
        reload: () -> Unit,
    ) {
        val app = context.applicationContext
        scope.launch {
            try {
                val up = uploads[seatId] ?: return@launch
                val id =
                    up.uploadedId
                        ?: ServerUpload
                            .send(ServerUpload.newId(), ServerUpload.CREW_FEEDBACK, "image/jpeg", seatId = seatId) {
                                ProofImages.bytes(app, up.uri, ProofImages.CREW_MAX_BYTES)
                            }.also { uploads[seatId] = up.copy(uploadedId = it) }
                Api.sendFeedback(seatId, FeedbackIn(id))
                uploads[seatId] = up.copy(state = UploadState.SENT, uploadedId = id)
                withContext(Dispatchers.IO) { ProofEditing.discard(app, up.uri) }
                Snacks.done("Feedback for ${up.label} sent. Thank you.")
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                uploads[seatId]?.let { uploads[seatId] = it.copy(state = UploadState.FAILED) }
                Snacks.error("Feedback picture not sent.")
            }
        }
    }
}
