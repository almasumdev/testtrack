package com.eazyverse.testtrack.domain

/** The day's allowance and the server's limits, as the last answer said them. */
data class ChatLimits(
    val messagesPerDay: Int = 10,
    val picturesPerDay: Int = 3,
    val pictureHours: Int = 24,
    val maxLength: Int = 500,
    val maxMentions: Int = 5,
    val pollSeconds: Int = 5,
    val pollMaxSeconds: Int = 30,
)

/** Why the message as written cannot go, said under the field. */
enum class DraftProblem { NONE, LINK, TOO_LONG, TOO_MANY_MENTIONS }

/** Counting what is left today, and whether the message as written can be sent. */
object ChatAllowance {
    /** What is wrong with [draft], first things first; [DraftProblem.NONE] when it may go. */
    fun problem(
        draft: ChatDraft,
        limits: ChatLimits,
        allow: Set<String>,
    ): DraftProblem =
        when {
            ChatLinks.blocked(draft.forLinkCheck(), allow) -> DraftProblem.LINK
            draft.visibleLength() > limits.maxLength -> DraftProblem.TOO_LONG
            draft.spans.size > limits.maxMentions -> DraftProblem.TOO_MANY_MENTIONS
            else -> DraftProblem.NONE
        }

    /** Send is on: something written, nothing wrong with it, and a message left today. */
    fun canSend(
        draft: ChatDraft,
        problem: DraftProblem,
        messagesLeft: Int,
        canPost: Boolean,
    ): Boolean = canPost && !draft.empty && problem == DraftProblem.NONE && messagesLeft > 0

    /** At this many messages left or fewer, the field says how many. */
    const val FIELD_COUNT = 3

    /** The count at the end of the field: shown from [FIELD_COUNT] down to one, and not at none, where the field says so. */
    fun fieldCount(messagesLeft: Int): Int? = if (messagesLeft in 1..FIELD_COUNT) messagesLeft else null

    /**
     * The count on the picture button, or null for none: when the button is greyed (see
     * [canAttach]), and when the server allows as many pictures as messages, so pictures are never
     * what runs out first.
     */
    fun pictureBadge(
        messagesLeft: Int,
        picturesLeft: Int,
        limits: ChatLimits,
        canPost: Boolean,
    ): String? =
        when {
            !canAttach(messagesLeft, picturesLeft, canPost) -> null
            limits.picturesPerDay >= limits.messagesPerDay -> null
            picturesLeft > 9 -> "9+"
            else -> picturesLeft.toString()
        }

    /** The picture button opens the picker: a message and a picture left. What is written is checked in the editor. */
    fun canAttach(
        messagesLeft: Int,
        picturesLeft: Int,
        canPost: Boolean,
    ): Boolean = canPost && messagesLeft > 0 && picturesLeft > 0

    /** A picture can be picked: a message and a picture left, and nothing wrong with the words with it. */
    fun canPickPicture(
        problem: DraftProblem,
        messagesLeft: Int,
        picturesLeft: Int,
        canPost: Boolean,
    ): Boolean = canPost && problem == DraftProblem.NONE && messagesLeft > 0 && picturesLeft > 0
}

/** How long to wait before the next poll, slowing down while nothing changes or the network fails. */
object ChatPolling {
    /** Failures in a row before the chat says it is offline. */
    const val OFFLINE_AFTER = 2

    /**
     * [baseS] after a poll that brought something, then half as long again for each empty or
     * failed poll in a row, never past [maxS].
     */
    fun nextDelayMs(
        baseS: Int,
        maxS: Int,
        emptyStreak: Int,
        failures: Int,
    ): Long {
        val base = baseS.coerceAtLeast(1) * 1000.0
        val cap = maxOf(maxS.coerceAtLeast(1) * 1000.0, base)
        var delay = base
        repeat((emptyStreak + failures).coerceIn(0, 20)) { delay *= 1.5 }
        return delay.coerceAtMost(cap).toLong()
    }
}
