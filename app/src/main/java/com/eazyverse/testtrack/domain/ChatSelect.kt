package com.eazyverse.testtrack.domain

/**
 * What the chat's selection bar offers for the messages held down, from the lines themselves.
 * [reply] is only for one message; [copy] and [delete] work on many. [removeOnly] is when every
 * picked message never sent, so "Delete" reads "Remove". [ask] is when a delete reaches the
 * server and so wants a confirm first. [report] is for one message of someone else's.
 */
data class ChatPicks(
    val ids: Set<String> = emptySet(),
    val reply: Boolean = false,
    val copy: Boolean = false,
    val delete: Boolean = false,
    val removeOnly: Boolean = false,
    val ask: Boolean = false,
    val report: Boolean = false,
) {
    val count: Int get() = ids.size
    val active: Boolean get() = ids.isNotEmpty()

    fun has(id: String): Boolean = id in ids
}

/** The rules for picking messages in the chat the WhatsApp way: hold one, then tap others. */
object ChatSelect {
    /** A message can be answered: the chat is open for the reader, it landed, and it is not gone. */
    fun canReply(
        line: ChatLine,
        writable: Boolean,
    ): Boolean = writable && !line.deleted && !line.blocked && !line.held && line.send == SendState.SENT

    /** A stub has no words for the reader: one from a blocked member, or someone else's held for the team. */
    fun isStub(line: ChatLine): Boolean = line.blocked || (line.held && !line.mine)

    fun canCopy(line: ChatLine): Boolean = !line.deleted && !isStub(line) && line.text.isNotBlank()

    /**
     * Someone else's message that reached the server: not a bot or team line, not deleted, not one
     * from a member the reader blocked. Reading is enough, so a closed chat can report too.
     */
    fun canReport(line: ChatLine): Boolean =
        !line.mine && line.kind == ChatKind.USER && !line.deleted && !isStub(line) && line.send == SendState.SENT

    /** Your own, not still on its way: a message that is sending can't be called back yet. */
    fun canDelete(line: ChatLine): Boolean = line.mine && !line.deleted && line.send != SendState.SENDING

    /** A message that has something to offer. A deleted one or one still sending has nothing. */
    fun canPick(
        line: ChatLine,
        writable: Boolean,
    ): Boolean =
        !line.deleted &&
            line.send != SendState.SENDING &&
            (canReply(line, writable) || canCopy(line) || canDelete(line) || canReport(line))

    /** [id] added to the picked, or taken out when it already is. Keeps the order picked. */
    fun toggle(
        ids: Set<String>,
        id: String,
    ): Set<String> = if (id in ids) ids - id else ids + id

    /** The picked lines in the order of the thread. A message that is gone, or can no longer be picked, drops out. */
    fun lines(
        ids: Set<String>,
        thread: List<ChatLine>,
        writable: Boolean,
    ): List<ChatLine> = if (ids.isEmpty()) emptyList() else thread.filter { it.id in ids && canPick(it, writable) }

    /** What the bar shows for [ids] over [thread]. */
    fun picks(
        ids: Set<String>,
        thread: List<ChatLine>,
        writable: Boolean,
    ): ChatPicks {
        val picked = lines(ids, thread, writable)
        if (picked.isEmpty()) return ChatPicks()
        val delete = picked.all { canDelete(it) }
        return ChatPicks(
            ids = picked.mapTo(LinkedHashSet()) { it.id },
            reply = picked.size == 1 && canReply(picked[0], writable),
            copy = picked.any { canCopy(it) },
            delete = delete,
            removeOnly = delete && picked.all { it.send == SendState.FAILED },
            ask = delete && picked.any { it.send == SendState.SENT },
            report = picked.size == 1 && canReport(picked[0]),
        )
    }

    /** The words of the picked messages that have any, in thread order, one per line. */
    fun copyText(
        picked: List<ChatLine>,
        plain: (String) -> String,
    ): String = picked.filter { canCopy(it) }.joinToString("\n") { plain(it.text) }
}
