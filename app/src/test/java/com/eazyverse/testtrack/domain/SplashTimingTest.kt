package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SplashTimingTest {
    @Test
    fun `already played time is never added again`() {
        assertEquals(600L, remainingSplashAnimationMillis(1_000, 900, 1_300))
        assertEquals(0L, remainingSplashAnimationMillis(1_000, 900, 2_500))
    }

    @Test
    fun `static or missing animation exits immediately`() {
        assertEquals(0L, remainingSplashAnimationMillis(0, 900, 1_000))
        assertEquals(0L, remainingSplashAnimationMillis(1_000, 0, 1_000))
        assertEquals(0L, remainingSplashAnimationMillis(1_000, -1, 1_000))
    }

    @Test
    fun `unexpected metadata and backwards clock cannot extend the sequence`() {
        assertEquals(900L, remainingSplashAnimationMillis(1_000, 20_000, 900))
        assertEquals(300L, remainingSplashAnimationMillis(1_000, 300, 900))
    }
}
