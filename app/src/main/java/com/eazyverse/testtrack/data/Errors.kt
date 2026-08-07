package com.eazyverse.testtrack.data

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
    this is AppTakenException -> message ?: fallback

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

    else -> fallback
}
