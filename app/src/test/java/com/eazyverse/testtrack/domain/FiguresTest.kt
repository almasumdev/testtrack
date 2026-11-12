package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FiguresTest {
    @Test
    fun smallNumbersHaveNoSeparator() {
        assertEquals("0", grouped(0, Locale.US))
        assertEquals("999", grouped(999, Locale.US))
    }

    @Test
    fun separatorsFollowTheLocale() {
        assertEquals("12,450", grouped(12450, Locale.US))
        assertEquals("1.240", grouped(1240, Locale.GERMANY))
    }
}
