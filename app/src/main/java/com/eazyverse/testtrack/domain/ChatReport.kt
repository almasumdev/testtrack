package com.eazyverse.testtrack.domain

/**
 * Why a message or a person is reported. The codes are the server's and fixed; the words may be
 * reworded here. [OTHER] needs a note.
 */
enum class ChatReportReason(
    val code: String,
    val label: String,
) {
    SPAM("spam", "Spam or ads"),
    ABUSE("abuse", "Rude, bullying or hateful"),
    SEXUAL("sexual", "Sexual or violent content"),
    PRIVATE("private", "Shares someone's private details"),
    SCAM("scam", "A scam or a trick to get tix"),
    OTHER("other", "Something else"),
}

/** The rules for the note that goes with a report. */
object ChatReport {
    const val NOTE_MIN = 10
    const val NOTE_MAX = 280

    /** The note as it goes: trimmed. */
    fun note(raw: String): String = raw.trim()

    /** Whether [reason] with [note] can be sent: "Something else" needs 10 to 280 characters, the rest only a note no longer than 280. */
    fun ready(
        reason: ChatReportReason?,
        note: String,
    ): Boolean {
        if (reason == null) return false
        val length = note(note).length
        return length <= NOTE_MAX && (reason != ChatReportReason.OTHER || length >= NOTE_MIN)
    }
}
