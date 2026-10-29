package com.eazyverse.testtrack.ui

/*
 * The copy of an app on this phone, against what the crew has: an update out, and when the old
 * copy stops counting.
 */

/**
 * When the old copy stops counting, said short for a row: "6:40 PM" today, "tomorrow 6:40 PM",
 * or null when the owner's time is unknown or already past.
 */
internal fun updateBy(
    app: CrewApp,
    now: java.time.Instant = java.time.Instant.now(),
): String? {
    val told =
        runCatching {
            java.time.OffsetDateTime
                .parse(app.versionAt)
                .toInstant()
        }.getOrNull() ?: return null
    val by = told.plusSeconds(CrewRules.UPDATE_GRACE_HOURS * 3600L)
    if (!by.isAfter(now)) return null
    return opensWhen(by, now).replace("today at ", "").replace("tomorrow at ", "tomorrow ")
}

/** The sheet's line about an update out: whose version, and until when the old copy counts. */
internal fun updateLine(
    app: CrewApp,
    owner: String,
): String {
    val version = if (app.versionName.isNotBlank()) "version ${app.versionName}" else "a new version"
    val by = updateBy(app)
    return when {
        app.updateRequired || by == null ->
            "$owner put out $version. Opening the old copy no longer counts, so update it before you open it."
        else -> "$owner put out $version. Your copy still counts until $by. After that, only the new one does."
    }
}

/** The copy on this phone is older than the version the owner told the crew about. */
internal fun olderHere(
    app: CrewApp,
    actions: CrewActions,
): Boolean {
    val newer = app.versionCode ?: return false
    return actions.isInstalled(app.packageName) && (actions.localVersion(app.packageName) ?: newer) < newer
}
