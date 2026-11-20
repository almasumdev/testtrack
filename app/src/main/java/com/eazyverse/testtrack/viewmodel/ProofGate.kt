package com.eazyverse.testtrack.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.eazyverse.testtrack.data.ProofStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The kept screenshot's one door. Writing a new version of it (one with details hidden, say) and
 * sending it take turns here, so a send reads the file the last finished write left, never the one
 * a write is about to replace, and an upload's id is never written down for a picture that was
 * replaced while it went.
 */
internal class ProofGate {
    private val lock = Mutex()
    private var writing by mutableIntStateOf(0)

    /** A new version is on its way. Send waits until it is kept or has failed. */
    val replacing: Boolean get() = writing > 0

    /**
     * Runs [write] alone, then [show] in the same turn, before any other write can start. [show]
     * reads what is kept from the store and puts it on the page, so the page never shows a picture
     * the store has already replaced. It runs when [write] throws too: the store then still holds
     * what was kept before, and the page shows that. A throw goes to the caller.
     */
    suspend fun <T> replace(
        show: () -> Unit = {},
        write: suspend () -> T,
    ): T {
        writing++
        try {
            return lock.withLock {
                try {
                    write()
                } finally {
                    show()
                }
            }
        } finally {
            writing--
        }
    }

    /**
     * Reads what is kept at this moment with [read] and sends it with [send], with no write in
     * between. Null when nothing is kept.
     */
    suspend fun <F : Any, T> send(
        read: () -> F?,
        send: suspend (F) -> T,
    ): T? = lock.withLock { read()?.let { send(it) } }
}

/**
 * [uri] (from the editor: picked, or with details hidden) kept for [reviewId] in place of the
 * screenshot kept before, which [ProofStore] deletes once the new one is whole. [show] puts what is
 * kept on the page inside the same turn (see [ProofGate.replace]). Throws when it could not be
 * kept, or when [stillOpen] says the review was sent meanwhile; the one kept before then stays as
 * it was. The editor's own copy in the cache goes either way.
 */
internal suspend fun ProofGate.keepEdited(
    context: Context,
    reviewId: String,
    uri: Uri,
    stillOpen: () -> Boolean,
    show: () -> Unit,
): File =
    try {
        replace(show) {
            check(stillOpen()) { "Review already sent" }
            withContext(Dispatchers.IO) { ProofStore.keep(context, reviewId, uri) } ?: error("Unreadable")
        }
    } finally {
        withContext(NonCancellable + Dispatchers.IO) { ProofEditing.discard(context, uri) }
    }
