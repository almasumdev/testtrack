package com.eazyverse.testtrack.viewmodel

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.eazyverse.testtrack.data.FirebaseSignInException
import com.eazyverse.testtrack.data.StalledException
import com.eazyverse.testtrack.data.sizedGooglePhoto
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.ui.UNREACHABLE_FAILURE
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class SignInOutcomeTest {
    private val online = { false }
    private val offline = { true }

    private fun sort(
        e: Throwable,
        isOffline: () -> Boolean = online,
    ) = sortSignInFailure(e, isOffline)

    private fun timeoutException(): Throwable {
        val timeout =
            runCatching { runBlocking { withTimeout(1) { delay(1_000) } } }.exceptionOrNull()
        assertEquals(true, timeout is TimeoutCancellationException)
        return timeout!!
    }

    @Test
    fun closingTheChooserIsACancelNotAFailure() {
        assertEquals(SignInUi.Cancelled, sort(GetCredentialCancellationException("closed")))
    }

    @Test
    fun noAccountOnThePhone() {
        assertEquals(SignInUi.NoAccount, sort(NoCredentialException("none")))
    }

    @Test
    fun networkProblemsAreOfflineWhenThePhoneSaysSo() {
        assertEquals(SignInUi.Offline, sort(IOException("reset"), offline))
        assertEquals(SignInUi.Offline, sort(timeoutException(), offline))
    }

    @Test
    fun networkProblemsOnAnOnlinePhoneMeanTheServerCouldNotBeReached() {
        val unreachable = SignInUi.Failed(UNREACHABLE_FAILURE)
        assertEquals(unreachable, sort(IOException("reset"), online))
        assertEquals(unreachable, sort(timeoutException(), online))
    }

    @Test
    fun otherCredentialErrorsAreOfflineOnlyWhenThePhoneIs() {
        val e = GetCredentialUnknownException("boom")
        assertEquals(SignInUi.Offline, sort(e, offline))
        assertEquals(SignInUi.Failed(null), sort(e, online))
    }

    @Test
    fun aFirebaseRefusalIsSortedByItsCause() {
        assertEquals(SignInUi.Offline, sort(FirebaseSignInException(IOException("down")), offline))
        assertEquals(
            SignInUi.Failed(UNREACHABLE_FAILURE),
            sort(FirebaseSignInException(IOException("down")), online),
        )
        assertEquals(SignInUi.Failed(null), sort(FirebaseSignInException(IllegalStateException("provider off"))))
    }

    @Test
    fun ourOwnSentencesAreShownAsTheyAre() {
        assertEquals(SignInUi.Failed("Google didn't answer."), sort(StalledException("Google didn't answer.")))
        assertEquals(
            SignInUi.Failed("This account was deleted."),
            sort(ApiError(403, "account_gone", "This account was deleted.")),
        )
    }

    @Test
    fun aServerFaultGetsTheDefaultText() {
        assertEquals(SignInUi.Failed(null), sort(ApiError(503, "down", "upstream said something")))
        assertEquals(SignInUi.Failed(null), sort(RuntimeException("anything")))
    }

    @Test
    fun theProfilePictureIsAskedForAt144() {
        assertEquals("https://x.test/a/photo=s144-c", sizedGooglePhoto("https://x.test/a/photo=s96-c"))
        assertEquals("https://x.test/a/photo=s144-c", sizedGooglePhoto("https://x.test/a/photo=s64"))
        assertEquals("https://x.test/a/photo", sizedGooglePhoto("https://x.test/a/photo"))
        assertNull(sizedGooglePhoto(null))
        assertNull(sizedGooglePhoto("  "))
    }
}
