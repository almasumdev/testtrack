package com.eazyverse.testtrack.domain

/** Finish only the unplayed part of the native icon, with a hard bound on stale platform metadata. */
fun remainingSplashAnimationMillis(
    startMillis: Long,
    durationMillis: Long,
    nowMillis: Long,
): Long {
    if (startMillis <= 0 || durationMillis <= 0) return 0
    val duration = durationMillis.coerceAtMost(900)
    val elapsed = (nowMillis - startMillis).coerceAtLeast(0)
    return (duration - elapsed).coerceAtLeast(0)
}
