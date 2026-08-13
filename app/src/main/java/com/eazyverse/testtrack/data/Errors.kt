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
 * implies they did something wrong, and gives them nothing to do about it. What follows says what
 * happened in terms of their group, and where that is genuinely actionable, what to do.
 *
 * Our own exceptions keep their text. They were written for this.
 */
fun Throwable.friendly(
    fallback: String,
    denied: String = "You don't have access to this any more. You may have been removed from the " +
        "group, or it may have been dissolved."
): String = when {
    this is AppTakenException || this is BlockedException -> message ?: fallback

    this is FirebaseFirestoreException -> when (code) {
        // Also what a deleted document looks like: the read rule tests fields on a document that
        // is not there, which is a refusal rather than an empty answer.
        Code.PERMISSION_DENIED -> denied

        Code.UNAUTHENTICATED ->
            "Your sign-in has expired. Open Setup from the top of the home screen and sign in again."

        Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED, Code.ABORTED, Code.CANCELLED ->
            "We couldn't reach TestTrack. Check your connection and try again."

        Code.RESOURCE_EXHAUSTED ->
            "TestTrack is busy right now. Give it a minute and try again."

        else -> fallback
    }

    this is IOException ->
        "We couldn't reach TestTrack. Check your connection and try again."

    // Signing in fails in Credential Manager or Play services far more often than it fails in
    // Firestore, and none of those are an IOException, so every one of them used to fall through
    // to the generic fallback. A phone with no Google account on it and a build whose signing key
    // is not registered were both told, wrongly, to check their connection.
    this is GetCredentialCancellationException ->
        "Sign-in was cancelled. Tap Continue with Google when you're ready."

    this is NoCredentialException ->
        "There's no Google account on this phone yet. Add the Gmail you test with under " +
            "Settings, Accounts, then come back and try again."

    this is GetCredentialException -> "Google couldn't complete the sign-in. ${detail()}"

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
