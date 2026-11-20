package com.eazyverse.testtrack.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The proof step's work in progress, kept per review until it is sent.
 *
 * The screenshot, the note, and the fact that Play was opened all lived in memory, so backing
 * out of the review page and opening it again asked for every one of them again. They are kept
 * here instead, in the app's own files rather than the cache, so nothing but sending (or
 * uninstalling) throws them away. A picture from the gallery is copied in, because the picker's
 * grant to the original ends with the screen that asked for it.
 */
object ProofStore {
    private const val PREFS = "review_proof"

    /** The kept screenshot for [reviewId], if there is one. */
    fun proof(
        context: Context,
        reviewId: String,
    ): File? =
        prefs(context)
            .getString("file_$reviewId", null)
            ?.let { File(dir(context), it) }
            ?.takeIf { it.isFile && it.length() > 0 }

    /** The kept screenshot's type, for the upload; the file alone does not say. */
    fun mime(
        context: Context,
        reviewId: String,
    ): String = prefs(context).getString("mime_$reviewId", null) ?: "image/jpeg"

    /** Copy what the picker chose. Blocking: call it off the main thread. */
    fun keep(
        context: Context,
        reviewId: String,
        source: Uri,
    ): File? =
        runCatching {
            val bytes = ProofImages.bytes(context, source)
            write(context, reviewId, "image/jpeg") { out -> out.write(bytes) }
        }.getOrNull()

    /** Take over a screenshot the camera button saved in the cache, which goes either way. Blocking. */
    fun adopt(
        context: Context,
        reviewId: String,
        captured: File,
    ): File? =
        try {
            runCatching {
                val bytes = ProofImages.bytes(context, Uri.fromFile(captured))
                write(context, reviewId, "image/jpeg") { out -> out.write(bytes) }
            }.getOrNull()
        } finally {
            captured.delete()
            release(captured)
        }

    /**
     * The kept screenshot's id on our server, once it is there. Sending can fail after the upload,
     * offline for instance, and trying again then reuses this rather than sending it twice. A new
     * screenshot clears it. A ref kept by an older build is not one of ours and reads as none, so
     * the screenshot goes again from the phone.
     */
    fun uploaded(
        context: Context,
        reviewId: String,
    ): String? = prefs(context).getString("ref_$reviewId", null)?.takeIf(ServerUpload::isId)

    fun markUploaded(
        context: Context,
        reviewId: String,
        ref: String,
    ) = prefs(context).edit().putString("ref_$reviewId", ref).apply()

    /** The id the kept screenshot is sent under, written down before the first try. */
    fun reserved(
        context: Context,
        reviewId: String,
    ): String? = prefs(context).getString("upload_id_$reviewId", null)?.takeIf(ServerUpload::isId)

    fun reserve(
        context: Context,
        reviewId: String,
        fileId: String,
    ) {
        check(prefs(context).edit().putString("upload_id_$reviewId", fileId).commit())
    }

    fun note(
        context: Context,
        reviewId: String,
    ): String = prefs(context).getString("note_$reviewId", "") ?: ""

    fun saveNote(
        context: Context,
        reviewId: String,
        note: String,
    ) = prefs(context).edit().putString("note_$reviewId", note).apply()

    /** Whether the reviewer has been sent to Play for this review, which is what opens step four. */
    fun left(
        context: Context,
        reviewId: String,
    ): Boolean = prefs(context).getBoolean("left_$reviewId", false)

    fun markLeft(
        context: Context,
        reviewId: String,
    ) = prefs(context).edit().putBoolean("left_$reviewId", true).apply()

    /** The half-reward choice must survive leaving TestTrack to capture its Play proof. */
    fun noBox(
        context: Context,
        reviewId: String,
    ): Boolean = prefs(context).getBoolean("no_box_$reviewId", false)

    fun markNoBox(
        context: Context,
        reviewId: String,
    ) {
        prefs(context)
            .edit()
            .putBoolean("no_box_$reviewId", true)
            .putBoolean("left_$reviewId", true)
            .apply()
    }

    /** Taken back out before sending: the picture goes, the note and the step stay. */
    fun dropPicture(
        context: Context,
        reviewId: String,
    ) {
        proof(context, reviewId)?.delete()
        prefs(context)
            .edit()
            .remove("file_$reviewId")
            .remove("mime_$reviewId")
            .remove("ref_$reviewId")
            .remove("upload_id_$reviewId")
            .apply()
    }

    /** Sent: none of it is needed any more. */
    fun forget(
        context: Context,
        reviewId: String,
    ) {
        proof(context, reviewId)?.delete()
        prefs(context)
            .edit()
            .remove("file_$reviewId")
            .remove("mime_$reviewId")
            .remove("note_$reviewId")
            .remove("left_$reviewId")
            .remove("no_box_$reviewId")
            .remove("ref_$reviewId")
            .remove("upload_id_$reviewId")
            .apply()
    }

    /** The start of the picture editor's files in the cache, written before they are kept here. */
    const val EDIT_PREFIX = "proof_edit_"

    /** How old a file must be before [sweep] may take it: no write or edit runs this long. */
    const val SWEEP_AGE_MS = 10 * 60_000L

    /** The end of a file [write] is still writing, renamed to its real name once it is whole. */
    private const val PART = ".part"

    /** Files on their way somewhere ([hold]): the editor's copy, a capture not yet taken over. */
    private val held = ConcurrentHashMap.newKeySet<String>()

    /** [file] is in use and [sweep] leaves it, however old, until [release]. */
    fun hold(file: File) {
        held += file.absolutePath
    }

    fun release(file: File) {
        held -= file.absolutePath
    }

    /**
     * Deletes what no kept screenshot refers to: a file here left by a write cut short, or an old
     * one whose delete failed after a newer version (one with details hidden, say) replaced it;
     * the editor's files in the cache; and camera pictures that were never taken over. Run at
     * process start, but a capture or the editor may already be writing, so it only takes files
     * older than [SWEEP_AGE_MS] by [now], and never a kept one or one [hold] marks in use. It takes
     * turns with [write], so it never sees one half done. Blocking.
     */
    @Synchronized
    fun sweep(
        context: Context,
        now: Long = System.currentTimeMillis(),
    ) {
        val kept =
            prefs(context)
                .all
                .filterKeys { it.startsWith("file_") }
                .values
                .toSet()

        fun stale(file: File) = file.absolutePath !in held && now - file.lastModified() >= SWEEP_AGE_MS
        dir(context).listFiles()?.forEach { if (it.name !in kept && stale(it)) it.delete() }
        context.cacheDir.listFiles()?.forEach { if (it.name.startsWith(EDIT_PREFIX) && stale(it)) it.delete() }
        File(context.cacheDir, "review_proof").listFiles()?.forEach { if (stale(it)) it.delete() }
    }

    /**
     * Written to a part file beside the old one and renamed to a new name once whole; the old one
     * goes only once the record points at the new one. Cut short at any moment, the record names
     * one whole picture, never a mix, and a failed write leaves no part behind. A new name each
     * time, too: the same name gave the page the same address for a different picture, and it kept
     * showing the first one.
     */
    @Synchronized
    internal fun write(
        context: Context,
        reviewId: String,
        mime: String,
        copy: (java.io.OutputStream) -> Unit,
    ): File {
        val old = proof(context, reviewId)
        val target = File(dir(context), "${reviewId}_${UUID.randomUUID()}")
        val part = File(target.path + PART)
        try {
            part.outputStream().use(copy)
            check(part.length() > 0L) { "empty image" }
            check(part.renameTo(target)) { "not renamed" }
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
        val recorded =
            prefs(context)
                .edit()
                .putString("file_$reviewId", target.name)
                .putString("mime_$reviewId", mime)
                .remove("ref_$reviewId")
                .remove("upload_id_$reviewId")
                .commit()
        if (!recorded) {
            target.delete()
            error("not recorded")
        }
        old?.delete()
        return target
    }

    private fun dir(context: Context) = File(context.filesDir, "review_proof").apply { mkdirs() }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
