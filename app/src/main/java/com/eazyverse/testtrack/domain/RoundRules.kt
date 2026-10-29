package com.eazyverse.testtrack.domain

/**
 * How a night's round treats an app the server will not count, apart from the phone.
 *
 * Open all leaves out what it knows will be refused, but a refusal can still come (an update the
 * owner announced a moment ago). That app is skipped and the round finishes the rest; it stays on
 * its row with its own button, and the round's last line says how many were left.
 */
object RoundRules {
    /**
     * A refusal of this one app's open, which the round skips: anything the server says no to on
     * purpose. A lost session, too many tries or the server down stop the round instead.
     */
    fun refusedOpen(
        status: Int,
        auth: Boolean = false,
    ) = status in 400..499 && !auth && status != 401 && status != 429

    /** The round's last line: tonight done, or how many were left out and why the first was. */
    fun lastWord(skipped: List<String>): String =
        if (skipped.isEmpty()) "That is tonight done." else "${skipped.size} left. ${skipped.first()}"
}
