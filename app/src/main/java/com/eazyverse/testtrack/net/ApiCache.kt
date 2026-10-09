package com.eazyverse.testtrack.net

import android.content.Context
import java.io.File

/**
 * The last answer to each of the screens' main questions, kept on the phone.
 *
 * Without it every launch began from nothing: the profile, the board, your apps and the feed all
 * shimmered until the server had answered again, though nothing about them had changed since the
 * last time anybody looked. Now a screen opens on what it showed last time and the fresh answer
 * replaces it a moment later, the way the header already did with its own snapshot.
 *
 * Kept as the server's own JSON, one file per path, so there is no second format to keep in step:
 * an answer this build cannot read any more is simply not used. Written only by requests that
 * finished inside the same session they started in, and wiped with the session, so one account's
 * record never opens under another.
 */
object ApiCache {
    @Volatile private var dir: File? = null

    fun load(context: Context) {
        if (dir != null) return
        dir = File(context.applicationContext.filesDir, "api-last").apply { mkdirs() }
    }

    private fun file(path: String) = dir?.let { File(it, path.trim('/').replace(Regex("[^A-Za-z0-9]+"), "_") + ".json") }

    /** The text last kept for [path], or null. A disk read: call it off the main thread. */
    fun read(path: String): String? = runCatching { file(path)?.takeIf { it.exists() }?.readText() }.getOrNull()

    /** Keep [text] for [path], unless the session changed while it was being fetched. */
    fun write(
        path: String,
        text: String,
        generation: Long,
    ) {
        if (generation != ApiSession.generation) return
        runCatching { file(path)?.writeText(text) }
    }

    /** Forget every answer, for a sign-out. */
    fun clear() {
        dir?.listFiles()?.forEach { it.delete() }
    }
}
