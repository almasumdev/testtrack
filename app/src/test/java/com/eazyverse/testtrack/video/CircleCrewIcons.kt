package com.eazyverse.testtrack.video

import com.eazyverse.testtrack.ui.Crew

/** Actual icons from the dev Circle feed, saved locally so the native previews stay offline. */
private val circleApps =
    listOf(
        "Bluefy: BT Keyboard & Mouse" to "com.eazyverse.bluefy",
        "Auto Silent: Volume Scheduler" to "com.eazyverse.soundscheduler",
        "Astra Match" to "com.shahadat.astramatch",
        "Baka Run" to "com.markuljdev.bakarun",
        "Cherish – Remember People" to "app.cherish.people",
        "EditMe: Photo Editor & Presets" to "com.arisoftware.editme",
        "Gaan Music Player" to "com.arisoftware.gaan",
    )

private fun circleImage(pkg: String) = "https://icons.testtrack.invalid/circle_actual_${pkg.replace('.', '_')}.png"

/** Alternate actual feed images with letter fallbacks, keeping the original nightly state. */
internal fun Crew.withCircleIcons(): Crew =
    copy(
        seats =
            seats.mapIndexed { index, seat ->
                when {
                    seat.mine ->
                        seat.copy(
                            appName = circleApps[0].first,
                            packageName = circleApps[0].second,
                            icon = circleImage(circleApps[0].second),
                        )
                    index % 2 == 0 -> {
                        val (name, pkg) = circleApps[index / 2 % circleApps.size]
                        seat.copy(appName = name, packageName = pkg, icon = circleImage(pkg))
                    }
                    else -> seat.copy(icon = "")
                }
            },
        apps =
            apps.mapIndexed { index, app ->
                if (index % 2 == 0) {
                    val (name, pkg) = circleApps[index / 2 % circleApps.size]
                    app.copy(label = name, packageName = pkg)
                } else {
                    app
                }
            },
    )
