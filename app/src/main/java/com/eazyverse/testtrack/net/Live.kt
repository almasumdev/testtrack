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
 * One socket at a time. A screen says which channels it wants with [watch] and stops with [stop];
 * everything that arrives on them comes out of [events] as the event's type, and the screen
 * decides what to do, which today is always "fetch again". The event carries the change, but a
 * refetch is the one answer that cannot drift from what the server holds.
 *
 * The server keeps a connection while it hears from it, so this sends a ping frame every half
 * minute, and comes back after a drop with a growing pause rather than hammering. Your own channel
 * and the feed's are added by the server; only crew channels are asked for here.
 */
object Live {
    /** One event: which channel, and what happened on it. */
    data class Event(
        val channel: String,
        val type: String,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val flow = MutableSharedFlow<Event>(extraBufferCapacity = 32)

    val events: SharedFlow<Event> = flow.asSharedFlow()

    private var socket: WebSocket? = null
    private var wanted: Set<String> = emptySet()
    private var running: Job? = null
    private var pinging: Job? = null

    /** Listen to these channels until [stop]. Asking again with the same set changes nothing. */
    @Synchronized
    fun watch(channels: Set<String>) {
        if (channels == wanted && running?.isActive == true) return
        wanted = channels
        running?.cancel()
        socket?.close(1000, null)
        running = scope.launch { stay() }
    }

    @Synchronized
    fun stop() {
        wanted = emptySet()
        running?.cancel()
        running = null
        pinging?.cancel()
        socket?.close(1000, null)
        socket = null
    }

    /** Connect, and connect again after a drop, for as long as somebody is watching. */
    private suspend fun stay() {
        var pause = 2_000L
        // Ends by cancellation: [stop] and [watch] cancel this job, and every wait below is one.
        while (true) {
            val token = freshToken() ?: return
            val closed = Job()
            val url = Api.url("/ws").replaceFirst("http", "ws") + "?token=" + token
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
                        if (type == "subscribed") return
                        flow.tryEmit(Event(event.text("ch").orEmpty(), type))
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
            val opened = Api.http.newWebSocket(Request.Builder().url(url).build(), listener)
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
