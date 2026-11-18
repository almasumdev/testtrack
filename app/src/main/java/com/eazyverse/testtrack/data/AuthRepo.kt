package com.eazyverse.testtrack.data

import android.app.Activity
import android.content.Context
import android.util.Log
import coil.imageLoader
import coil.request.ImageRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * One "Continue with Google" produces two different things, because Android and Firebase
 * separate them:
 *
 *  - **Identity**: Credential Manager returns an ID token. Only Google can mint it, so the
 *    membership verdict derived from it server-side cannot be forged by the device.
 *  - **Firebase session**: the same ID token is exchanged for a Firebase user, which is what
 *    Firestore security rules key on.
 *
 * Signing in shows only Google's account chooser, and nothing else of Google's is asked for.
 */
object AuthRepo {
    val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    /**
     * How long each half of signing in may take before the app stops waiting.
     *
     * Neither half has a limit of its own, which is the whole reason for these. Credential Manager
     * is a binder call into Play services and Firebase answers with a Task, and when the thing at
     * the far end is wedged neither returns, throws, or times out. The button then spins for as
     * long as the tester is willing to watch it and the report we get back is "it just loads",
     * which contains nothing to act on. A wait that ends says which half ended it.
     *
     * The Google half is generous because there is a person inside it choosing an account. The
     * Firebase half is not: nobody is looking at it and it is one round trip.
     */
    private const val GOOGLE_LIMIT = 120_000L
    private const val FIREBASE_LIMIT = 20_000L

    /**
     * Signs in and exchanges the token for a Firebase session. Nothing else.
     *
     * Deliberately only those two things. Each testing feature requests group membership, usage
     * access and reminders when needed. Sign-in used to answer
     * two of those a second time while a spinner sat over the screen saying "Signing in...", which
     * made the slowest question in the app, the group roster, the thing standing between a tester
     * and the rest of it. Getting a session is quick; everything after it belongs to the screen
     * that shows its progress.
     *
     * A Firebase failure is thrown as [FirebaseSignInException] with the real cause inside. Almost
     * always that means Google sign-in is not enabled under Firebase Authentication for this
     * project, or the connection dropped; the caller sorts which, because the person needs a
     * different sentence for each. It used to be left in a field the screen never read, so every
     * one of them showed as the same generic line.
     */
    suspend fun signIn(activity: Activity): GoogleAccount {
        var account =
            withTimeoutOrNull(GOOGLE_LIMIT) { GroupGate.signIn(activity) }
                ?: throw StalledException(
                    "Google didn't answer. Close TestTrack from your recent apps, open it again and " +
                        "tap Continue with Google. If it happens again, restart your phone. Your " +
                        "account is fine.",
                )
        // Started now, while the Firebase and server round trips run, so the face is already
        // there when the sheet shows the account.
        preloadPhoto(activity, account.photo)

        try {
            firebaseSession(account.idToken)
        } catch (e: TimeoutCancellationException) {
            // Caught before CancellationException on purpose: a timeout is one, so the branch
            // below would rethrow it and the spinner this exists to end would go on turning.
            throw FirebaseSignInException(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The cause is ours to read, not the person's.
            Log.w("AuthRepo", "Firebase sign-in refused", e)
            throw FirebaseSignInException(e)
        }

        if (account.photo == null) {
            val user = FirebaseAuth.getInstance().currentUser
            val fallback = sizedGooglePhoto(user?.photoUrl?.toString())
            if (fallback != null) {
                account = account.copy(photo = fallback)
                preloadPhoto(activity, fallback)
            }
        }
        return account
    }

    /** Fetches the picture into Coil's cache, at the size the sheet's avatar asks for. */
    private fun preloadPhoto(
        context: Context,
        photo: String?,
    ) {
        if (photo == null) return
        val px = (AVATAR_DP * context.resources.displayMetrics.density).roundToInt()
        val request =
            ImageRequest
                .Builder(context)
                .data(photo)
                .size(px)
                .build()
        context.imageLoader.enqueue(request)
    }

    private const val AVATAR_DP = 36

    /** Counts Firebase sign-in attempts, so a late answer can tell whether it is still the current one. */
    private val firebaseAttempts = AtomicInteger()

    /**
     * Exchanges the token for a Firebase user, waiting at most [FIREBASE_LIMIT].
     *
     * Giving up on the wait does not stop the Firebase task. If it succeeds afterwards, Firebase is
     * signed in while our own session says signed out, so a wait that ends early leaves a one-shot
     * listener behind to put that right. See [shouldUndoLateSignIn].
     */
    private suspend fun firebaseSession(idToken: String) {
        val attempt = firebaseAttempts.incrementAndGet()
        val task =
            FirebaseAuth
                .getInstance()
                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
        try {
            withTimeout(FIREBASE_LIMIT) {
                suspendCancellableCoroutine<Unit> { cont ->
                    task
                        .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
                        .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
                }
            }
        } catch (e: CancellationException) {
            // Covers the timeout too, which is one. Cancelled or timed out, so nobody is waiting
            // for this task any more; undo it if it still lands.
            task.addOnSuccessListener {
                if (shouldUndoLateSignIn(attempt, firebaseAttempts.get(), com.eazyverse.testtrack.net.ApiSession.signedIn)) {
                    undoHalfSignIn()
                }
            }
            throw e
        }
    }

    fun signOut() {
        com.eazyverse.testtrack.net.ApiAuth
            .signOut()
        FirebaseAuth.getInstance().signOut()
    }

    /**
     * Google said yes but our server never did (it was unreachable, or the sheet was closed while
     * it was asked). Left signed in to Google alone, the header showed an account that nothing
     * else in the app had, so the Google half is undone and the next try starts clean.
     */
    fun undoHalfSignIn() {
        if (!com.eazyverse.testtrack.net.ApiSession.signedIn) FirebaseAuth.getInstance().signOut()
    }
}

/**
 * Whether a Firebase sign-in that finished after its wait was abandoned should be undone.
 *
 * Only the latest attempt may be: a newer attempt owns the Firebase session now, and undoing on
 * behalf of an older one would sign it out. And never when our own session is signed in, because
 * then the sign-in did complete and Firebase is meant to be signed in.
 */
internal fun shouldUndoLateSignIn(
    attempt: Int,
    latestAttempt: Int,
    apiSignedIn: Boolean,
): Boolean = attempt == latestAttempt && !apiSignedIn
