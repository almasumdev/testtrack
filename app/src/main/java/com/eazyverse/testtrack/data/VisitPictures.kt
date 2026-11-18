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
 * The nights' pictures on their way: kept on the phone until each is on TestTrack and on its visit.
 *
 * A visit is recorded the moment it counts and its picture follows, so a slow upload never holds
 * up the next app. Until the server has the picture the visit does not count, which is why every
 * picture is written down here first: Android closing TestTrack or a lost connection leaves it
 * waiting rather than lost, and [send] tries again on the next return to the Crew page.
 *
 * One picture per app per night ([PictureQueue]): night 5's picture still waiting is kept when the
 * same app is opened on night 6, and each is sent with its own night, so a late one lands there.
 *
 * The picture goes to TestTrack's own server ([ServerUpload]), shrunk on the phone first, and its
 * id is then put on the visit. A picture taken by an older build is still a `.jpg` and goes as one.
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
        /** Its id on our server, made once, so a try that dies half way never makes a second copy. */
        val fileId: String? = null,
        /** The member's own night it was taken on; 0 from a build before nights were kept. */
        val day: Int = 0,
    ) {
        /** Waited longer than [LATE_MS]: its row and the Crew page say so in red until it goes. */
        fun late(now: Long = System.currentTimeMillis()) = now - takenAt > LATE_MS
    }

    /** How long a picture may wait before the page shows it in red. */
    const val LATE_MS = 3 * 60 * 60 * 1000L

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val queue = MutableStateFlow<List<Waiting>>(emptyList())
    private var loaded = false

    /** The pictures not yet on their visits, for the page to say "sending" rather than "missing". */
    val waiting: StateFlow<List<Waiting>> = queue

    private val failure = MutableStateFlow<String?>(null)

    /** Why the last try to send stopped, for the page to say under "Sending"; null once they went. */
    val stuck: StateFlow<String?> = failure

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val text = prefs(context).getString(KEY, null) ?: return
        queue.value = runCatching { json.decodeFromString<List<Waiting>>(text) }.getOrDefault(emptyList())
    }

    /** A picture TestTrack took, waiting for its upload. A newer one of the same app and night replaces it. */
    fun add(
        context: Context,
        item: Waiting,
    ) {
        load(context)
        val (kept, replaced) = PictureQueue.adding(queue.value, item)
        replaced.filter { it.path != item.path }.forEach { File(it.path).delete() }
        save(context, kept)
        // Sent from here as soon as there is a connection, with TestTrack open or not.
        VisitPicturesWork.enqueue(context)
    }

    /** A picture picked from the phone for an app whose own picture failed. */
    suspend fun addPicked(
        context: Context,
        seatId: String,
        label: String,
        uri: Uri,
        /** The member's own night the picture is for. */
        day: Int = 0,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val bytes = runCatching { ProofImages.visit(context, uri) }.getOrNull() ?: return@withContext false
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, "picked_${seatId.take(8)}_${System.currentTimeMillis()}.webp")
            file.writeBytes(bytes)
            withContext(Dispatchers.Main) { add(context, Waiting(seatId, label, file.path, byHand = true, day = day)) }
            true
        }

    /**
     * Send every waiting picture: to TestTrack, then onto its visit, each with its own night. Stops
     * at the first connection or server failure and says why, keeping the rest. A picture the
     * server refuses for good (the app left the run, the visit is gone) is dropped and the rest
     * carry on, so one crew's refusal never holds up another's. Safe to repeat: each picture's id
     * is made once and the server answers a repeat as it did the first time.
     *
     * @return null when everything went, or the reason the rest is still waiting.
     */
    suspend fun send(context: Context): String? =
        lock.withLock {
            load(context)
            if (queue.value.isEmpty()) return null.also { failure.value = null }
            val why = sendAll(context)
            failure.value = why
            why
        }

    private suspend fun sendAll(context: Context): String? =
        PictureQueue.pass(queue.value.toList()) { waiting ->
            val file = File(waiting.path)
            if (!file.exists()) {
                drop(context, waiting)
                return@pass PictureQueue.Step.Gone
            }
            // An id kept from before pictures came to TestTrack is not one of ours: a new one is made.
            var item = if (ServerUpload.isId(waiting.fileId)) waiting else withId(context, waiting, ServerUpload.newId())
            try {
                val id =
                    ServerUpload.send(
                        id = item.fileId.orEmpty(),
                        kind = ServerUpload.VISIT,
                        mime = mime(file),
                        seatId = item.seatId,
                        remint = { fresh -> item = withId(context, item, fresh) },
                    ) { file.readBytes() }
                val night = item.day.takeIf { it > 0 }
                Api.visitPicture(item.seatId, PictureIn(id, item.byHand, day = night))
                drop(context, item)
                PictureQueue.Step.Gone
            } catch (e: ApiError) {
                // Refused for good (the visit was never kept, the app left the run): dropped, and on
                // to the next. Anything else stops the pass and is tried again later.
                if (!PictureQueue.refusedForGood(e.status)) return@pass PictureQueue.Step.Stop(ServerUpload.queuedLine(e))
                drop(context, item)
                PictureQueue.Step.Gone
            } catch (_: Exception) {
                PictureQueue.Step.Stop("We could not reach TestTrack. The pictures will go by themselves when you are back online.")
            }
        }

    /** What the file is sent as: WebP for every picture taken now, JPEG for one an older build left waiting. */
    internal fun mime(file: File): String = if (file.extension.equals("webp", ignoreCase = true)) "image/webp" else "image/jpeg"

    /** [item] under [id], written down in its place in the queue before anything is sent. */
    private fun withId(
        context: Context,
        item: Waiting,
        id: String,
    ): Waiting = item.copy(fileId = id).also { save(context, queue.value.map { old -> if (old.path == item.path) it else old }) }

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
