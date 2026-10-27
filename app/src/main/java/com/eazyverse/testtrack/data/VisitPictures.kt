package com.eazyverse.testtrack.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.PictureIn
import com.eazyverse.testtrack.net.visitPicture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Tonight's pictures on their way: kept on the phone until each is in its Drive and on its visit.
 *
 * A visit is recorded the moment it counts and its picture follows, so a slow upload never holds
 * up the next app. Until the server has the picture the visit does not count, which is why every
 * picture is written down here first: Android closing TestTrack, a lost connection or a Drive
 * grant that ran out all leave it waiting rather than lost, and [send] tries again on the next
 * return to the Crew page.
 *
 * The picture goes to the member's own Drive, shared by link, as a review's proof does; only its
 * file id reaches our server, which checks the file is theirs.
 */
object VisitPictures {
    const val DIR = "visit_pictures"
    private const val PREFS = "visit_pictures"
    private const val KEY = "waiting"

    @Serializable
    data class Waiting(
        val seatId: String,
        val label: String,
        val path: String,
        /** Picked from the phone after TestTrack's own picture failed. */
        val byHand: Boolean = false,
        val takenAt: Long = System.currentTimeMillis(),
        /** Its Drive file, reserved once, so a try that dies half way never makes a second copy. */
        val fileId: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val queue = MutableStateFlow<List<Waiting>>(emptyList())
    private var loaded = false

    /** The pictures not yet on their visits, for the page to say "sending" rather than "missing". */
    val waiting: StateFlow<List<Waiting>> = queue

    private val failure = MutableStateFlow<String?>(null)

    /** Why the last try to send stopped, for the page to say under "Sending"; null once they went. */
    val stuck: StateFlow<String?> = failure

    /** Google wants the member to agree to Drive again, which only the Crew page can ask. */
    fun needsDrive() {
        failure.value = "Connect Google Drive again to send tonight's pictures."
    }

    /** Drive access for this round, asked for while TestTrack was in front, since asking needs it. */
    @Volatile var token: String? = null

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val text = prefs(context).getString(KEY, null) ?: return
        queue.value = runCatching { json.decodeFromString<List<Waiting>>(text) }.getOrDefault(emptyList())
    }

    /** A picture TestTrack took, waiting for its upload. A newer one of the same app replaces it. */
    fun add(
        context: Context,
        item: Waiting,
    ) {
        load(context)
        val old = queue.value.filter { it.seatId == item.seatId }
        old.filter { it.path != item.path }.forEach { File(it.path).delete() }
        save(context, queue.value.filter { it.seatId != item.seatId } + item)
        // Sent from here as soon as there is a connection, with TestTrack open or not.
        VisitPicturesWork.enqueue(context)
    }

    /** A picture picked from the phone for an app whose own picture failed. */
    suspend fun addPicked(
        context: Context,
        seatId: String,
        label: String,
        uri: Uri,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val bytes = runCatching { ProofImages.bytes(context, uri) }.getOrNull() ?: return@withContext false
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, "picked_${seatId.take(8)}_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
            withContext(Dispatchers.Main) { add(context, Waiting(seatId, label, file.path, byHand = true)) }
            true
        }

    /**
     * Send every waiting picture: to Drive, then onto its visit. Stops at the first Drive or
     * connection failure and says why, keeping the rest; a visit the server no longer has drops
     * its picture. Safe to repeat: each picture's Drive file is reserved once and written over.
     *
     * @return null when everything went, or the reason the rest is still waiting.
     */
    suspend fun send(
        context: Context,
        token: String? = this.token,
    ): String? =
        lock.withLock {
            load(context)
            if (queue.value.isEmpty()) return null.also { failure.value = null }
            val grant = token ?: return "Connect Google Drive to send tonight's pictures.".also { failure.value = it }
            val why = sendAll(context, grant)
            failure.value = why
            why
        }

    private suspend fun sendAll(
        context: Context,
        grant: String,
    ): String? {
        for (waiting in queue.value.toList()) {
            val file = File(waiting.path)
            if (!file.exists()) {
                drop(context, waiting)
                continue
            }
            val item =
                waiting.fileId?.let { waiting }
                    ?: runCatching { DriveRepo.reserveId(grant) }
                        .getOrNull()
                        ?.let { id -> waiting.copy(fileId = id).also { keep(context, waiting, it) } }
                    ?: return "Drive didn't answer. Tonight's pictures will go by themselves when it does."
            val name = "crew_${item.seatId.take(8)}_${item.takenAt}.jpg"
            val upload = DriveRepo.upload(grant, name, "image/jpeg", fileId = item.fileId) { file.readBytes() }
            if (upload is UploadResult.Failed) return upload.reason
            try {
                Api.visitPicture(item.seatId, PictureIn("drive:${item.fileId}", grant, item.byHand))
                drop(context, item)
            } catch (e: ApiError) {
                // Gone on the server: the visit was never kept, or the app left the run.
                if (e.status == 404 || e.status == 409) drop(context, item) else return e.message
            } catch (_: Exception) {
                return "We could not reach TestTrack. Tonight's pictures will go by themselves when you are back online."
            }
        }
        return null
    }

    /** The same picture with its reserved Drive file, in its place in the queue. */
    private fun keep(
        context: Context,
        old: Waiting,
        new: Waiting,
    ) = save(context, queue.value.map { if (it.path == old.path) new else it })

    private fun drop(
        context: Context,
        item: Waiting,
    ) {
        File(item.path).delete()
        save(context, queue.value.filter { it !== item && it.path != item.path })
    }

    private fun save(
        context: Context,
        items: List<Waiting>,
    ) {
        queue.value = items
        prefs(context).edit { putString(KEY, json.encodeToString(items)) }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
