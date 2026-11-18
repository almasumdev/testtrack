package com.eazyverse.testtrack.net

/*
 * A crew's chat. Read by polling while the chat is on screen, never over the live socket: the
 * server keeps no connection open for it.
 */

/**
 * The chat from a cursor: [since] 0 for the newest page, or the last `rev` seen for what changed
 * after it. [before] asks for an older page, by `seq`.
 */
suspend fun Api.chat(
    crewId: String,
    since: Long = 0,
    before: Long? = null,
    limit: Int? = null,
): ApiChatPage {
    val query =
        buildList {
            add("since=$since")
            before?.let { add("before=$it") }
            limit?.let { add("limit=$it") }
        }.joinToString("&")
    return get("/crews/$crewId/chat?$query")
}

/** A message, under the id the phone made for it, so a try repeated after a failure is the same message. */
suspend fun Api.sendChat(
    crewId: String,
    message: ChatPostIn,
): ApiChatSent = post("/crews/$crewId/chat/messages", message)

/** Takes one of your own messages away for everyone. Answers with its deleted stub. */
suspend fun Api.deleteChat(
    crewId: String,
    messageId: String,
): ApiChatSent = delete("/crews/$crewId/chat/messages/$messageId")

/** No pushes for this crew's chat, except when someone mentions you. */
suspend fun Api.muteChat(
    crewId: String,
    muted: Boolean,
): ApiChatMuted = put("/crews/$crewId/chat/mute", ChatMuteIn(muted))

/** Everything up to [rev] is read. The server never moves it back. */
suspend fun Api.readChat(
    crewId: String,
    rev: Long,
): ApiChatRead = post("/crews/$crewId/chat/read", ChatReadIn(rev))
