package com.eazyverse.testtrack.net

import android.content.Context
import com.eazyverse.testtrack.data.Connectivity
import java.io.File

/**
 * The last answer to each of the screens' main questions, kept on the phone.
 *
 * It is the offline backup. While the phone has a connection a screen shimmers
 * until the server answers, so it never shows an old list as if it were current. Without a
 * connection, or when the request fails for want of one, the screen falls back to what it showed
 * last time. See [online]. The profile is the one exception, shown online too: see [ProfileCache].
 *
 * Kept as the server's own JSON, one file per path, so there is no second format to keep in step:
 * an answer this build cannot read any more is simply not used. Written only by requests that
 * finished inside the same session they started in, and wiped with the session, so one account's
 * record never opens under another.
 */
object ApiCache {
    @Volatile private var dir: File? = null

    @Volatile private var app: Context? = null

    /** A test's say-so about the connection, so a frame does not depend on the machine's. */
    @Volatile internal var onlineForTest: Boolean? = null

    /**
     * Whether the phone has a working connection, as Android sees it. Not known yet counts as
     * offline, so the kept copy is what shows.
     */
    fun online(): Boolean = onlineForTest ?: (app?.let(Connectivity::isOnline) == true)

    fun load(context: Context) {
        app = context.applicationContext
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
        runCatching { file(path)?.apply { parentFile?.mkdirs() }?.writeText(text) }
    }

    /** Forget every answer, for a sign-out. */
    fun clear() {
        dir?.listFiles()?.forEach { it.delete() }
    }
}
