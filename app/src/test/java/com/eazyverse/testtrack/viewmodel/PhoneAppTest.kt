package com.eazyverse.testtrack.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneAppTest {
    @Test
    fun aBuildFromAndroidStudioIsToldApartFromThePlayOne() {
        assertTrue(PhoneApp("com.eazyverse.listkin.debug", "ListKin").testBuild)
        assertTrue(PhoneApp("com.example.notes.dev", "Notes").testBuild)
        assertFalse(PhoneApp("com.eazyverse.listkin", "ListKin").testBuild)
        // A word that only ends like one is still the real app.
        assertFalse(PhoneApp("com.example.mydebugger", "Debugger").testBuild)
    }
}
