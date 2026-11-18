package com.eazyverse.testtrack.ui

private val demoMemberPackage = Regex("dev\\.seedapps\\.member[0-9]+")

/** Display-only install state for seeded crew members; real packages retain their device state. */
internal fun demoCrewCopy(
    pkg: String,
    enabled: Boolean,
): HereCopy? {
    if (!enabled || !demoMemberPackage.matches(pkg)) return null
    // The isolated tile-review crew needs an older copy to show both update states.
    val reviewTile = pkg.removePrefix("dev.seedapps.member").toIntOrNull() in 100..113
    return HereCopy(true, fromPlay = true, versionCode = if (reviewTile) 1L else Long.MAX_VALUE)
}
