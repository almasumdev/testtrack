package com.eazyverse.testtrack.data

/** How long Play requires testers to stay opted in. The grid is this many columns wide. */
const val RUN_DAYS = 14

/** An app someone in the group has put on a closed testing track. */
data class TestApp(
    val id: String = "",
    val ownerUid: String = "",
    val ownerEmail: String = "",
    val name: String = "",
    val packageName: String = "",
    /** Epoch millis. Day 0 of the 14-day run. */
    val startDate: Long = 0L,
    val status: String = STATUS_PENDING
) {
    val approved: Boolean get() = status == STATUS_APPROVED

    /**
     * Which day of the run today is, or null when the run has not started or has finished.
     *
     * Whole days since [startDate] rather than calendar days: a calendar boundary would let a
     * tester post at 23:59 and again at 00:01 and call it two days.
     */
    fun dayIndex(now: Long = System.currentTimeMillis()): Int? {
        if (startDate <= 0L) return null
        val day = ((now - startDate) / 86_400_000L).toInt()
        return day.takeIf { it in 0 until RUN_DAYS }
    }

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_APPROVED = "approved"
    }
}

/**
 * One tester's proof for one app on one day.
 *
 * The document id is `{appId}__{testerUid}__{day}`, which makes posting twice an overwrite rather
 * than a duplicate and lets the whole grid come back in a single query.
 */
data class Proof(
    val appId: String = "",
    val testerUid: String = "",
    val testerEmail: String = "",
    val day: Int = 0,
    val fileId: String = "",
    val imageUrl: String = "",
    val capturedAt: Long = 0L
) {
    companion object {
        fun id(appId: String, testerUid: String, day: Int) = "${appId}__${testerUid}__$day"
    }
}

/** A member of the group, as far as the grid is concerned. */
data class Tester(
    val uid: String = "",
    val email: String = "",
    val displayName: String = ""
) {
    /** `someone@gmail.com` -> `someone`, which is all the grid column has room for. */
    val shortName: String
        get() = displayName.takeIf { it.isNotBlank() } ?: email.substringBefore('@')
}
