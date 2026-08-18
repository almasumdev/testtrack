package com.eazyverse.testtrack.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the pure [AppUpdateService.resolveTier] decision — the path that can lock a user out —
 * with the same cases Sound Scheduler's AppUpdateServiceTest exercises.
 */
class AppUpdateServiceTest {

    private fun tier(
        playUpdateAvailable: Boolean = true,
        availableBuild: Int = 10,
        currentBuild: Int = 6,
        currentMajor: Int = 1,
        latestBuild: Int = 10,
        latestVersionName: String = "1.0.6",
        minSupportedBuild: Int = 0,
        stalenessDays: Int = 5,
        nudgeAfterDays: Int = 3,
    ) = AppUpdateService.resolveTier(
        playUpdateAvailable, availableBuild, currentBuild, currentMajor,
        latestBuild, latestVersionName, minSupportedBuild, stalenessDays, nudgeAfterDays,
    )

    @Test
    fun playSaysNothing_isAlwaysNone_evenWithAForcingConfig() {
        assertEquals(
            UpdateTier.NONE,
            tier(playUpdateAvailable = false, minSupportedBuild = 999, latestVersionName = "2.0.0"),
        )
    }

    @Test
    fun unreadableLocalBuild_isNone() {
        assertEquals(UpdateTier.NONE, tier(currentBuild = 0))
    }

    @Test
    fun belowMinSupportedBuild_isBlocked() {
        assertEquals(UpdateTier.BLOCKED, tier(currentBuild = 6, minSupportedBuild = 7))
    }

    @Test
    fun minSupportedBuildZero_blocksNobody() {
        assertEquals(UpdateTier.NUDGE, tier(minSupportedBuild = 0))
    }

    @Test
    fun newMajorPlayCanReach_isBlocked() {
        assertEquals(
            UpdateTier.BLOCKED,
            tier(currentMajor = 1, latestVersionName = "2.0.0", availableBuild = 12, latestBuild = 12),
        )
    }

    @Test
    fun newMajorPlayCannotReach_isNotBlocked() {
        // A 2.0.0 sits on another track; Play can only hand this user an older build, so no block.
        assertEquals(
            UpdateTier.NUDGE,
            tier(currentMajor = 1, latestVersionName = "2.0.0", availableBuild = 8, latestBuild = 12),
        )
    }

    @Test
    fun unreadableCurrentMajor_neverForces() {
        assertEquals(
            UpdateTier.NUDGE,
            tier(currentMajor = 0, latestVersionName = "2.0.0", availableBuild = 12, latestBuild = 12),
        )
    }

    @Test
    fun majorComparedNumericallyNotLexically() {
        // "10" > "9" must force; a lexical compare would wrongly skip it.
        assertEquals(
            UpdateTier.BLOCKED,
            tier(currentMajor = 9, latestVersionName = "10.0.0", availableBuild = 12, latestBuild = 12),
        )
    }

    @Test
    fun stalenessBelowThreshold_isNone() {
        assertEquals(UpdateTier.NONE, tier(stalenessDays = 1, nudgeAfterDays = 3))
    }

    @Test
    fun stalenessAtOrAboveThreshold_isNudge() {
        assertEquals(UpdateTier.NUDGE, tier(stalenessDays = 3, nudgeAfterDays = 3))
    }
}
