package com.eazyverse.testtrack.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SameAccountTest {
    @Test
    fun theSameAddressIsTheSameAccount() {
        assertTrue(sameAccount("a@x.com", "a@x.com"))
    }

    @Test
    fun anotherAccountIsNot() {
        assertFalse(sameAccount("a@x.com", "b@x.com"))
    }

    @Test
    fun signedOutIsNeverTheSameAccount() {
        assertFalse(sameAccount("a@x.com", null))
        assertFalse(sameAccount(null, null))
    }
}
