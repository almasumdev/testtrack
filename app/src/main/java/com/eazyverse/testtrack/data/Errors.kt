package com.eazyverse.testtrack.data

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.api.ApiException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import java.io.IOException

/**
 * Turns a thrown thing into a sentence worth showing someone.
 *
 * This exists because `it.message ?: "something friendly"` does not work, and reads as though it
 * does. The fallback only applies when the message is *null*, and a Firestore exception always has
 * one — so every screen written that way showed
 * `PERMISSION_DENIED: Missing or insufficient permissions.` and the carefully written sentence
 * beside it was never once displayed.
 *
 * A raw code is worse than useless to a tester. It names a database they have never heard of,
 * implies they did something wrong, and gives them nothing to do about it.
 *
 * Every line below is one sentence of cause and, where there is one, one short thing to do. They
 * are read on a phone, in a hurry, by somebody who wants to get back to what they were doing, and
 * a paragraph gets skimmed to its first full stop anyway. Where the cause is genuinely unknown the
 * exception's own name and text are appended rather than guessed at.
 *
 * Our own exceptions keep their text. They were written for this.
 */
fun Throwable.friendly(
    fallback: String,
    denied: String = "You're not in this group any more, or it was dissolved."
): String = when {
    this is AppTakenException || this is BlockedException || this is StalledException ||
        this is AppClosedException -> message ?: fallback

    this is FirebaseFirestoreException -> when (code) {
        // Also what a deleted document looks like: the read rule tests fields on a document that
        // is not there, which is a refusal rather than an empty answer.
        Code.PERMISSION_DENIED -> denied

        Code.UNAUTHENTICATED -> "Your sign-in expired. Open Setup and sign in again."

        Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED, Code.ABORTED, Code.CANCELLED ->
            "Can't reach TestTrack. Check your connection."

        Code.RESOURCE_EXHAUSTED -> "TestTrack is busy. Try again in a minute."

        else -> fallback
    }

    this is IOException -> "Can't reach TestTrack. Check your connection."

    // Signing in fails in Credential Manager or Play services far more often than it fails in
    // Firestore, and none of those are an IOException, so every one of them used to fall through
    // to the generic fallback. A phone with no Google account on it and a build whose signing key
    // is not registered were both told, wrongly, to check their connection.
    // Nothing went wrong here, and saying so keeps it out of the same register as the rest.
    this is GetCredentialCancellationException ->
        "You closed the sign-in. Press the button to try again."

    // Not "there is no Google account on this phone", which is a claim the exception never
    // makes, and which sent people to Settings to add a Gmail that was already sitting there.
    // What it means is that Google had nothing to offer *this request*, and in practice that is
    // Play services still reading its account list on the first press after a cold start. So the
    // instruction is the one that actually works: press it again.
    this is NoCredentialException ->
        "Google had no account ready yet. Press the button again."

    this is GetCredentialException -> "Google couldn't finish the sign-in. ${detail()}"

    this is ApiException -> "Google turned the sign-in down. ${detail()}"

    // Anything unrecognised says what it actually was.
    //
    // The sentence on its own is a guess dressed as an explanation: it names the network because
    // the network is the usual suspect, and when it is wrong it sends somebody off to fix the one
    // thing that was never broken. A tester who can read the real cause out to you, or screenshot
    // it, turns an unreproducible report into a fixable one.
    else -> "$fallback ${detail()}"
}

/** The real cause, in the smallest form still worth reading out over a chat. */
private fun Throwable.detail(): String {
    val name = this::class.simpleName ?: "Error"
    val text = message?.trim()?.takeIf { it.isNotEmpty() }?.take(140)
    return if (text == null) "($name)" else "($name: $text)"
}
