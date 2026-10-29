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

/** What this phone holds of an app, as the server judges an open of it. */
data class HereCopy(
    val installed: Boolean,
    /** Google Play put it here: a copy from Android Studio or a file does not count. */
    val fromPlay: Boolean = true,
    val versionCode: Long? = null,
)

/** The copy of [pkg] on this phone, as the rows see it. */
internal fun CrewActions.copyOf(pkg: String) = HereCopy(isInstalled(pkg), fromPlay(pkg), localVersion(pkg))

/**
 * An open of [app] from [copy] the server will refuse: a copy Google Play did not install, or one
 * older than the owner's newest once its 24 hours are over. Its row offers the way out instead.
 */
fun refusedHere(
    app: CrewApp,
    copy: HereCopy,
): Boolean {
    if (!copy.installed) return false
    if (!copy.fromPlay) return true
    val newer = app.versionCode ?: return false
    return app.updateRequired && (copy.versionCode ?: newer) < newer
}

/**
 * What Open all opens: owed tonight, on this phone, and nothing the server is known to refuse.
 * Left out too: an app whose feedback is late, and one whose tonight's picture is only waiting to
 * send. What is left out stays on its row, with its own button.
 */
fun openAllOf(
    apps: List<CrewApp>,
    copy: (String) -> HereCopy,
    sendingTonight: (CrewApp) -> Boolean = { false },
): List<CrewApp> =
    apps.filter { app ->
        val here = copy(app.packageName)
        !app.done && !app.reported && !app.feedbackLate && here.installed && !refusedHere(app, here) && !sendingTonight(app)
    }
