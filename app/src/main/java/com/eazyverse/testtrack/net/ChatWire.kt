package com.eazyverse.testtrack.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * A crew's chat, as the server sends it. See the backend's app/schemas/chat.py.
 *
 * Every field has a default, so a field the server renames or drops leaves a blank rather than a
 * page that will not decode.
 */

/** One page of a crew's chat: what changed since a cursor, or the newest messages. */
@Serializable
data class ApiChatPage(
    /** open, read_only or gone (the crew was pruned and the list is empty). */
    val state: String = "open",
    @SerialName("can_post") val canPost: Boolean = false,
    /** Why not: blank, ended, out or paused. */
    @SerialName("why_not") val whyNot: String = "",
    val rev: Long = 0,
    /** The phone's cursor was ahead of the server: start again from this page. */
    val reset: Boolean = false,
    @SerialName("more_before") val moreBefore: Boolean = false,
    @SerialName("more_after") val moreAfter: Boolean = false,
    val muted: Boolean = false,
    @SerialName("read_rev") val readRev: Long = 0,
    val left: ApiChatLeft = ApiChatLeft(),
    val limits: ApiChatLimits = ApiChatLimits(),
    val bot: ApiChatBot = ApiChatBot(),
    /** Every seat in the crew, sent with messages; empty on a poll that brought nothing. */
    val people: List<ApiChatPerson> = emptyList(),
    val messages: List<ApiChatMessage> = emptyList(),
)

/** What is left of today's allowance; -1 where the server does not say. */
@Serializable
data class ApiChatLeft(
    val messages: Int = -1,
    val pictures: Int = -1,
    @SerialName("resets_at") val resetsAt: String? = null,
)

/** The server's limits, read from every answer rather than built into the app. */
@Serializable
data class ApiChatLimits(
    @SerialName("messages_per_day") val messagesPerDay: Int = 10,
    @SerialName("pictures_per_day") val picturesPerDay: Int = 3,
    @SerialName("picture_hours") val pictureHours: Int = 24,
    @SerialName("max_length") val maxLength: Int = 500,
    @SerialName("max_mentions") val maxMentions: Int = 5,
    @SerialName("poll_seconds") val pollSeconds: Int = 5,
    @SerialName("poll_max_seconds") val pollMaxSeconds: Int = 30,
)

/** Who speaks for the team and the crew's own news. */
@Serializable
data class ApiChatBot(
    val name: String = "Crew bot",
    /** Read but not shown: the app draws its own robot as the bot's face. */
    @SerialName("avatar_url") val avatarUrl: String = "",
)

/** A seat in the crew: who sits in it and with which app. The names are the crew's own (SeatOut). */
@Serializable
data class ApiChatPerson(
    @SerialName("seat_id") val seatId: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("owner_name") val ownerName: String = "",
    @SerialName("owner_photo") val ownerPhoto: String = "",
    /** Empty unless the server shows members' emails. */
    @SerialName("owner_email") val ownerEmail: String = "",
    @SerialName("app_name") val appName: String = "",
    val icon: String = "",
    @SerialName("package_name") val packageName: String = "",
    /** Their own night. */
    val day: Int = 0,
    @SerialName("in_crew") val inCrew: Boolean = true,
    val mine: Boolean = false,
    /** A volunteer, with no app here: [seatId] is their volunteer id and the app fields are blank. */
    val volunteer: Boolean = false,
)

/** One line of the chat. */
@Serializable
data class ApiChatMessage(
    val id: String = "",
    val seq: Long = 0,
    val rev: Long = 0,
    /** user or bot; a team post is a bot line with no [event]. */
    val kind: String = "user",
    /** Who wrote it and the seat they spoke as; null for the bot and the team. */
    @SerialName("author_id") val authorId: String? = null,
    @SerialName("author_seat_id") val authorSeatId: String? = null,
    val mine: Boolean = false,
    /** Mention tokens kept inline: `@[seat:<id>]` and `@[app:<id>]`. */
    val text: String = "",
    /** A bot line's key; blank for a team post. */
    val event: String = "",
    val mentions: List<ApiChatMention> = emptyList(),
    @SerialName("reply_to") val replyTo: ApiChatReply? = null,
    val picture: ApiChatPicture? = null,
    val deleted: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class ApiChatMention(
    /** member or app. */
    val kind: String = "member",
    @SerialName("seat_id") val seatId: String = "",
    val label: String = "",
)

@Serializable
data class ApiChatReply(
    val id: String = "",
    val seq: Long = 0,
    @SerialName("author_seat_id") val authorSeatId: String? = null,
    val excerpt: String = "",
    val picture: Boolean = false,
    val deleted: Boolean = false,
)

@Serializable
data class ApiChatPicture(
    val id: String = "",
    val width: Int = 0,
    val height: Int = 0,
    /** live or expired. */
    val state: String = "live",
    /** When it goes, an ISO time. The server does not send it yet: then it is sent time plus picture_hours. */
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class ChatPostIn(
    val id: String,
    val text: String,
    @SerialName("reply_to") val replyTo: String?,
    @SerialName("picture_id") val pictureId: String?,
)

/** The answer to a send, and to a delete, where [message] is the deleted stub. */
@Serializable
data class ApiChatSent(
    val message: ApiChatMessage = ApiChatMessage(),
    val left: ApiChatLeft = ApiChatLeft(),
    val limits: ApiChatLimits = ApiChatLimits(),
    val rev: Long = 0,
)

@Serializable
data class ChatMuteIn(
    val muted: Boolean,
)

@Serializable
data class ApiChatMuted(
    val muted: Boolean = false,
    val limits: ApiChatLimits = ApiChatLimits(),
)

@Serializable
data class ChatReadIn(
    val rev: Long,
)

@Serializable
data class ApiChatRead(
    @SerialName("read_rev") val readRev: Long = 0,
    val limits: ApiChatLimits = ApiChatLimits(),
)
