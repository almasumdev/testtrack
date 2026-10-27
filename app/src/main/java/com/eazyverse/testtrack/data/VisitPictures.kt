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
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val queue = MutableStateFlow<List<Waiting>>(emptyList())
    private var loaded = false

    /** The pictures not yet on their visits, for the page to say "sending" rather than "missing". */
    val waiting: StateFlow<List<Waiting>> = queue

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
     * connection failure and says why; a visit the server no longer has drops its picture.
     *
     * @return null when everything went, or the reason the rest is still waiting.
     */
    suspend fun send(
        context: Context,
        token: String? = this.token,
    ): String? =
        lock.withLock {
            load(context)
            if (queue.value.isEmpty()) return null
            val grant = token ?: return "Connect Google Drive to send tonight's pictures."
            for (item in queue.value.toList()) {
                val file = File(item.path)
                if (!file.exists()) {
                    drop(context, item)
                    continue
                }
                val name = "crew_${item.seatId.take(8)}_${item.takenAt}.jpg"
                val upload = DriveRepo.upload(grant, file, name)
                if (upload is UploadResult.Failed) return upload.reason
                val id = (upload as UploadResult.Ok).file.id
                try {
                    Api.visitPicture(item.seatId, PictureIn("drive:$id", grant, item.byHand))
                    drop(context, item)
                } catch (e: ApiError) {
                    // Gone on the server: the visit was never kept, or the app left the run.
                    if (e.status == 404 || e.status == 409) drop(context, item) else return e.message
                } catch (_: Exception) {
                    return "We could not reach TestTrack. Tonight's pictures will go when you are back online."
                }
            }
            null
        }

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
