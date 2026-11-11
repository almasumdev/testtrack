package com.eazyverse.testtrack.viewmodel

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.eazyverse.testtrack.data.FirebaseSignInException
import com.eazyverse.testtrack.data.StalledException
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.ui.UNREACHABLE_FAILURE
import com.google.firebase.FirebaseNetworkException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException

/**
 * Everything the sign-in sheet can be showing, as one value.
 *
 * It was a busy flag and a message string, which can say "working" and "something failed" and
 * nothing in between: cancelling, having no internet and having no Google account all looked
 * like either nothing or the same red line. Each of those is its own state now, with its own words.
 */
sealed interface SignInUi {
    data object Idle : SignInUi

    data object Working : SignInUi

    /** Somebody shut Google's account chooser. An answer, not a fault. */
    data object Cancelled : SignInUi

    data object Offline : SignInUi

    data object NoAccount : SignInUi

    /** [body] is a sentence worth showing as it is, or null for the sheet's own default text. */
    data class Failed(
        val body: String?,
    ) : SignInUi

    data class SignedIn(
        val email: String,
        val name: String?,
        val photo: String?,
        /** This phone gave its starter tix to another account; said once, so the sheet waits for OK. */
        val starterUsed: Boolean,
    ) : SignInUi
}

/**
 * Which state a failed sign-in belongs in.
 *
 * Order matters: a cancelled chooser and a missing account are both GetCredentialExceptions, so
 * they are picked out before the general case. [isOffline] is the phone's own word on its
 * connection, and is what tells "no internet" from "something else failed" for network errors and
 * for Credential Manager errors that carry no reason of their own.
 *
 * Firebase's refusal arrives wrapped in a [FirebaseSignInException] and is sorted by what is
 * inside it.
 */
internal fun sortSignInFailure(
    e: Throwable,
    isOffline: () -> Boolean,
): SignInUi =
    when {
        e is FirebaseSignInException -> sortSignInFailure(e.cause ?: e, isOffline)

        e is GetCredentialCancellationException -> SignInUi.Cancelled

        e is NoCredentialException -> SignInUi.NoAccount

        // Only "no internet" when the phone itself says so. Online, the same errors mean our server
        // (or Firebase) could not be reached, which is not the person's connection.
        e is IOException || e is FirebaseNetworkException || e is TimeoutCancellationException ->
            if (isOffline()) SignInUi.Offline else SignInUi.Failed(UNREACHABLE_FAILURE)

        e is GetCredentialException -> if (isOffline()) SignInUi.Offline else SignInUi.Failed(null)

        // Our own sentences, written for a person.
        e is StalledException -> SignInUi.Failed(e.message)

        e is ApiError && e.status in 400..499 && e.message.isNotBlank() -> SignInUi.Failed(e.message)

        else -> SignInUi.Failed(null)
    }
