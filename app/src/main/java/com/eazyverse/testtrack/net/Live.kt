package com.eazyverse.testtrack.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The server's live events, for whichever screen is showing.
 *
 * One socket, shared. Each screen that is showing says which channels it wants with [watch], under
 * its own name, and lets go with [stop]; the socket carries all of them together and closes when
 * nobody is left. Everything that arrives comes out of [events] to every screen, and each decides
 * what concerns it, which today is always "fetch again". The event carries the change, but a
 * refetch is the one answer that cannot drift from what the server holds.
 *
 * The server keeps a connection while it hears from it, so this sends a ping frame every half
 * minute, and comes back after a drop with a growing pause rather than hammering. Your own channel
 * and the feed's are added by the server, so a screen that only needs those watches no channels.
 */
object Live {
    /** One event: which channel, what happened on it, and the id of what it happened to, if any. */
    data class Event(
        val channel: String,
        val type: String,
        val ref: String? = null,
        val seq: Long? = null,
        val reconcile: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flow = MutableSharedFlow<Event>(extraBufferCapacity = 32)

    val events: SharedFlow<Event> = flow.asSharedFlow()

    private var socket: WebSocket? = null
    private val wants = mutableMapOf<String, Set<String>>()
    private var wanted: Set<String> = emptySet()
    private var running: Job? = null
    private var pinging: Job? = null
    private val sequences = mutableMapOf<String, Long>()

    @Synchronized
    fun reset() {
        running?.cancel()
        running = null
        pinging?.cancel()
        socket?.close(1000, null)
        socket = null
        sequences.clear()
        flow.tryEmit(Event("", "live.reset", reconcile = true))
    }

    /** [who] listens to these channels until [stop]. Asking again with the same set changes nothing. */
    @Synchronized
    fun watch(
        who: String,
        channels: Set<String> = emptySet(),
    ) {
        wants[who] = channels
        connect()
    }

    /** [who] is done; the socket closes once nobody is watching. */
    @Synchronized
    fun stop(who: String) {
        if (wants.remove(who) == null) return
        if (wants.isNotEmpty()) {
            connect()
            return
        }
        wanted = emptySet()
        running?.cancel()
        running = null
        pinging?.cancel()
        socket?.close(1000, null)
        socket = null
    }

    /** Open the socket for everybody's channels together; reopened only when that set changes. */
    private fun connect() {
        val all = wants.values.flatten().toSet()
        if (all == wanted && running?.isActive == true) return
        wanted = all
        running?.cancel()
        socket?.close(1000, null)
        running = scope.launch { stay() }
    }

    /** Connect, and connect again after a drop, for as long as somebody is watching. */
    private suspend fun stay() {
        var pause = 2_000L
        // Ends by cancellation: [stop] and [watch] cancel this job, and every wait below is one.
        while (true) {
            val token = freshToken() ?: return
            val closed = Job()
            // The token in a header, not the address: an address is written into every access log.
            val request =
                Request
                    .Builder()
                    .url(Api.url("/ws").replaceFirst("http", "ws"))
                    .header("Authorization", "Bearer $token")
                    .build()
            val listener =
                object : WebSocketListener() {
                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response,
                    ) {
                        val hello =
                            buildJsonObject {
                                putJsonArray("channels") { wanted.forEach { add(JsonPrimitive(it)) } }
                            }
                        webSocket.send(hello.toString())
                        pause = 2_000L
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String,
                    ) {
                        val event = runCatching { Api.json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                        val type = event.text("type") ?: return
                        if (type == "subscribed") {
                            synchronized(this@Live) { sequences.clear() }
                            flow.tryEmit(Event("", "live.reconnected", reconcile = true))
                            return
                        }
                        val channel = event.text("ch").orEmpty()
                        val seq = event.text("seq")?.toLongOrNull()
                        val gap =
                            synchronized(this@Live) {
                                val previous = sequences[channel]
                                if (seq != null) sequences[channel] = seq
                                seq != null && previous != null && seq != previous + 1
                            }
                        val ref = (event["data"] as? JsonObject)?.text("id")
                        flow.tryEmit(Event(channel, type, ref, seq, reconcile = gap))
                    }

                    // The server closes when the token it was opened with runs out. Answering at
                    // once ends it now, so the next socket opens with a fresh token, rather than
                    // after the server gives up waiting and events in between are missed.
                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        webSocket.close(1000, null)
                        closed.complete()
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String,
                    ) {
                        closed.complete()
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?,
                    ) {
                        closed.complete()
                    }
                }
            val opened = Api.http.newWebSocket(request, listener)
            socket = opened
            pinging?.cancel()
            pinging =
                scope.launch {
                    while (isActive) {
                        delay(30_000)
                        opened.send("ping")
                    }
                }
            closed.join()
            pinging?.cancel()
            delay(pause)
            pause = (pause * 2).coerceAtMost(60_000L)
        }
    }

    /** The current access token, refreshed first if it has lapsed. Null when nobody is signed in. */
    private suspend fun freshToken(): String? =
        withContext(Dispatchers.IO) {
            // A cheap authenticated call refreshes the pair if the access token has expired; the
            // socket itself cannot, since it only learns that from a refusal.
            runCatching { Api.me() }
            ApiSession.access
        }

    private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull
}
