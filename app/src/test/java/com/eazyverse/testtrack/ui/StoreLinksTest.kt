package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreLinksTest {
    @Test
    fun playIsAskedForByNameAndOnlyAPhoneWithoutItGetsTheWebPage() {
        assertEquals(
            listOf(
                "market://details?id=a" to "com.android.vending",
                "https://play.google.com/store/apps/details?id=a" to "com.android.vending",
                "https://play.google.com/store/apps/details?id=a" to null,
            ),
            storeAttempts("market://details?id=a", "https://play.google.com/store/apps/details?id=a"),
        )
    }
}
