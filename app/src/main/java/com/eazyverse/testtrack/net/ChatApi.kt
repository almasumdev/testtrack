package com.eazyverse.testtrack.net

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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

/** Tells the team about one message. The same member reporting it again is answered `200` with the first report. */
suspend fun Api.reportChat(
    crewId: String,
    messageId: String,
    report: ChatReportIn,
): ApiChatReport = post("/crews/$crewId/chat/messages/$messageId/report", report)

/** Tells the team about a person in this crew's chat. [userId] is a `user_id` from the chat's people. */
suspend fun Api.reportPerson(
    crewId: String,
    userId: String,
    report: ChatReportIn,
): ApiChatReport = post("/crews/$crewId/chat/people/$userId/report", report)

/** The reader agrees to the chat rules at [version]. */
suspend fun Api.acceptChatRules(version: Int): ApiChatRules = post("/me/chat-rules", ChatRulesIn(version))

/** Stops showing this member's messages, in every crew chat. Silent for them; blocking twice is fine. */
suspend fun Api.block(userId: String) = sendEmpty(Request.Builder().url(url("/me/blocks/$userId")).put("".toRequestBody(JSON)))

/** Shows their messages again. Unblocking someone not blocked is fine. */
suspend fun Api.unblock(userId: String) = sendEmpty(Request.Builder().url(url("/me/blocks/$userId")).delete())

/** The members the reader blocked, newest first. */
suspend fun Api.blocks(): List<ApiBlocked> = get("/me/blocks")
