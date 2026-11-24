package com.eazyverse.testtrack.viewmodel

import com.eazyverse.testtrack.domain.ChatKind
import com.eazyverse.testtrack.domain.ChatLimits
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatMention
import com.eazyverse.testtrack.domain.ChatPerson
import com.eazyverse.testtrack.domain.ChatPicture
import com.eazyverse.testtrack.domain.ChatQuote
import com.eazyverse.testtrack.domain.instantOf
import com.eazyverse.testtrack.net.ApiChatLimits
import com.eazyverse.testtrack.net.ApiChatMessage
import com.eazyverse.testtrack.net.ApiChatPerson

/*
 * The chat's wire shapes, turned into the plain values the screen draws. Anything the server
 * leaves out becomes a blank, never a crash.
 */

internal fun ApiChatPerson.toPerson() =
    ChatPerson(
        seatId = seatId,
        userId = userId,
        name = ownerName,
        photo = ownerPhoto,
        email = ownerEmail,
        appName = appName,
        appIcon = icon,
        packageName = packageName,
        day = day,
        inCrew = inCrew,
        mine = mine,
        volunteer = volunteer,
    )

internal fun ApiChatLimits.toLimits() =
    ChatLimits(
        messagesPerDay = messagesPerDay,
        picturesPerDay = picturesPerDay,
        pictureHours = pictureHours,
        maxLength = maxLength,
        maxMentions = maxMentions,
        pollSeconds = pollSeconds,
        pollMaxSeconds = pollMaxSeconds,
    )

/** A bot line with no event is a post from the team, which the server sends under the bot's name. */
internal fun ApiChatMessage.toLine(pictureHours: Int): ChatLine {
    val at = instantOf(createdAt)
    return ChatLine(
        id = id,
        seq = seq,
        rev = rev,
        kind =
            when {
                kind == "user" -> ChatKind.USER
                event.isBlank() && !deleted -> ChatKind.TEAM
                else -> ChatKind.BOT
            },
        authorId = authorId,
        authorSeatId = authorSeatId,
        mine = mine,
        text = text,
        event = event,
        mentions = mentions.map { ChatMention(app = it.kind == "app", seatId = it.seatId, label = it.label) },
        quote =
            replyTo?.let {
                ChatQuote(it.id, it.authorSeatId, it.excerpt, picture = it.picture, deleted = it.deleted)
            },
        picture =
            picture?.let {
                ChatPicture(
                    id = it.id,
                    width = it.width,
                    height = it.height,
                    expired = it.state != "live",
                    expiresAt = instantOf(it.expiresAt) ?: at?.plusSeconds(pictureHours * 3600L),
                )
            },
        deleted = deleted,
        blocked = blocked,
        held = held,
        at = at,
    )
}
