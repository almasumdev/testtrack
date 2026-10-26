package com.eazyverse.testtrack.data

import android.util.Log
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.api.ApiException
import java.io.IOException

/**
 * Turns a thrown thing into a sentence worth showing someone.
 *
 * This exists because `it.message ?: "something friendly"` does not work, and reads as though it
 * does. The fallback only applies when the message is *null*, and a library exception always has
 * one, so a screen written that way shows the library's text and never the sentence beside it.
 *
 * A raw code is worse than useless to a tester. It names a database they have never heard of,
 * implies they did something wrong, and gives them nothing to do about it.
 *
 * Every line below is one sentence of cause and, where there is one, one short thing to do. They
 * are read on a phone, in a hurry, by somebody who wants to get back to what they were doing, and
 * a paragraph gets skimmed to its first full stop anyway. The exception's own name and text are
 * never shown: they go to the log, where whoever is fixing it can read them.
 *
 * Our own exceptions keep their text. They were written for this.
 */
fun Throwable.friendly(fallback: String): String =
    when {
        // The testers-group check's own refusal, already written for a person.
        this is StalledException -> message ?: fallback

        this is IOException -> "Can't reach TestTrack. Check your connection."

        // Signing in fails in Credential Manager or Play services far more often than it fails in
        // Firestore, and none of those are an IOException, so every one of them used to fall through
        // to the generic fallback. A phone with no Google account on it and a build whose signing key
        // is not registered were both told, wrongly, to check their connection.
        // Nothing went wrong here, and saying so keeps it out of the same register as the rest.
        // Better still is not to ask: the sign-in path catches this itself and says nothing at
        // all, because narrating back to somebody what they have just chosen to do reads as the
        // app being confused about who decided it.
        this is GetCredentialCancellationException -> "Sign-in was not completed."

        // Not "there is no Google account on this phone", which is a claim the exception never
        // makes, and which sent people to Settings to add a Gmail that was already sitting there.
        // What it means is that Google had nothing to offer *this request*, and in practice that is
        // Play services still reading its account list on the first press after a cold start. So the
        // instruction is the one that actually works: press it again.
        this is NoCredentialException ->
            "Google had no account ready. Try once more."

        this is GetCredentialException -> logged("Google couldn't finish the sign-in. Try once more.")

        this is ApiException -> logged("Google turned the sign-in down. Try once more in a moment.")

        // TestTrack's own refusals are written for a person, so they are shown as they came:
        // "This account was deleted", "That app is already in a crew". Only a server failure
        // (5xx) gets the caller's sentence instead.
        this is com.eazyverse.testtrack.net.ApiError && status in 400..499 && message.isNotBlank() -> message

        // Anything unrecognised: the caller's own sentence, with the real cause in the log.
        else -> logged(fallback)
    }

/** The sentence for the person; the exception itself, class and text, for the log. */
private fun Throwable.logged(sentence: String): String {
    Log.w("TestTrack", sentence, this)
    return sentence
}
