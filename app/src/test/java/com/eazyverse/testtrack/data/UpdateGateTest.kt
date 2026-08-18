package com.eazyverse.testtrack.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one decision in this app that can stop somebody using it.
 *
 * [UpdateGate.resolve] is kept free of Play and Firestore so it can be checked here, without a
 * device, a network, or a signed-in account. The cases that matter are not the ones where it
 * blocks — they are the ones where it must not, because a tester walled out of a cohort they are
 * being held to has no way round it and loses their place while they work out what happened.
 */
class UpdateGateTest {

    private fun resolve(
        available: Boolean = true,
        availableBuild: Int = 60,
        runningBuild: Int = 50,
        runningMajor: Int = 1,
        latestBuild: Int = 60,
        latestVersionName: String = "1.6.0",
        minSupportedBuild: Int = 0,
        stalenessDays: Int = 5,
        nudgeAfterDays: Int = 3
    ) = UpdateGate.resolve(
        available = available,
        availableBuild = availableBuild,
        runningBuild = runningBuild,
        runningMajor = runningMajor,
        latestBuild = latestBuild,
        latestVersionName = latestVersionName,
        minSupportedBuild = minSupportedBuild,
        stalenessDays = stalenessDays,
        nudgeAfterDays = nudgeAfterDays
    )

    // ---- nothing overrules Play ---------------------------------------------------------------

    @Test
    fun `no update to be had means no gate, whatever the config says`() {
        // The floor is above the running build and a new major is out. Both would block. Play says
        // there is nothing to install, so blocking would leave a wall with no door in it.
        assertEquals(
            UpdateTier.NONE,
            resolve(available = false, minSupportedBuild = 99, latestVersionName = "2.0.0")
        )
    }

    @Test
    fun `an unreadable build number gates nobody`() {
        assertEquals(UpdateTier.NONE, resolve(runningBuild = 0, minSupportedBuild = 99))
    }

    // ---- the manual switch --------------------------------------------------------------------

    @Test
    fun `zero is the resting position and blocks nobody`() {
        assertEquals(UpdateTier.NUDGE, resolve(minSupportedBuild = 0))
    }

    @Test
    fun `below the floor is blocked`() {
        assertEquals(UpdateTier.BLOCKED, resolve(runningBuild = 50, minSupportedBuild = 51))
    }

    @Test
    fun `exactly the floor is not below it`() {
        assertEquals(UpdateTier.NUDGE, resolve(runningBuild = 51, minSupportedBuild = 51))
    }

    // ---- a new major ---------------------------------------------------------------------------

    @Test
    fun `a new major blocks when Play can actually serve it`() {
        assertEquals(
            UpdateTier.BLOCKED,
            resolve(runningMajor = 1, latestVersionName = "2.0.0", availableBuild = 60, latestBuild = 60)
        )
    }

    @Test
    fun `a new major on a track this install cannot reach does not block it`() {
        // 2.0.0 is build 70 on internal testing; production can only serve 60. Blocking here walls
        // somebody off with no update available to them, which is the failure this guard exists
        // for.
        assertEquals(
            UpdateTier.NUDGE,
            resolve(runningMajor = 1, latestVersionName = "2.0.0", availableBuild = 60, latestBuild = 70)
        )
    }

    @Test
    fun `an unreadable version name does not block`() {
        assertEquals(UpdateTier.NUDGE, resolve(runningMajor = 1, latestVersionName = ""))
    }

    @Test
    fun `the same major does not block`() {
        assertEquals(UpdateTier.NUDGE, resolve(runningMajor = 1, latestVersionName = "1.9.9"))
    }

    @Test
    fun `majors are compared as numbers, not as text`() {
        // The one that would pass a lexical comparison and be wrong: "10" sorts below "9" as text,
        // so a tenth major would quietly stop forcing exactly when it mattered most.
        assertEquals(
            UpdateTier.BLOCKED,
            resolve(runningMajor = 9, latestVersionName = "10.0.0")
        )
    }

    // ---- the nudge ------------------------------------------------------------------------------

    @Test
    fun `a fresh release waits for the config to say how long`() {
        assertEquals(UpdateTier.NONE, resolve(stalenessDays = 2, nudgeAfterDays = 3))
        assertEquals(UpdateTier.NUDGE, resolve(stalenessDays = 3, nudgeAfterDays = 3))
    }

    @Test
    fun `no config at all still offers the update`() {
        // Everything at zero is what a missing config/app document reads as. Play has an update,
        // nobody has said to wait, so it is offered and nobody is blocked.
        assertEquals(
            UpdateTier.NUDGE,
            resolve(
                latestBuild = 0,
                latestVersionName = "",
                minSupportedBuild = 0,
                stalenessDays = 0,
                nudgeAfterDays = 0
            )
        )
    }
}
