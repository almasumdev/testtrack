package com.eazyverse.testtrack.data

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eazyverse.testtrack.Config
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * One "Continue with Google" produces three different things, because Android and Firebase
 * separate them:
 *
 *  - **Identity** — Credential Manager returns an ID token. Only Google can mint it, so the
 *    membership verdict derived from it server-side cannot be forged by the device.
 *  - **Firebase session** — the same ID token is exchanged for a Firebase user, which is what
 *    Firestore security rules key on.
 *  - **Authorization** — [AuthorizationClient] returns a Drive access token.
 *
 * The user sees two sheets the first time. Afterwards [authorizeDrive] resolves silently, so it
 * is a single tap from then on.
 */
object AuthRepo {

    private val driveRequest: AuthorizationRequest
        get() = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(Config.DRIVE_SCOPE)))
            .build()

    val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    /**
     * Set when the ID token could not be exchanged for a Firebase session.
     *
     * Almost always means Google sign-in is not enabled under Firebase Authentication for this
     * project. Worth naming precisely, because the raw exception says nothing useful and the
     * symptom — an empty Today list — looks like missing data rather than missing auth.
     */
    var firebaseError by mutableStateOf<String?>(null)
        private set

    /**
     * Signs in, exchanges the token for a Firebase session, and asks whether the account is in
     * the group.
     *
     * A Firebase failure is recorded rather than thrown: the membership gate is independent of
     * it, so the tester can still be told where they stand instead of seeing sign-in collapse.
     */
    suspend fun signInAndVerify(activity: Activity): Pair<GoogleAccount, GateResult> {
        val account = GroupGate.signIn(activity)
        firebaseError = try {
            signInToFirebase(account.idToken)
            null
        } catch (e: Exception) {
            "Signed in, but Firebase rejected the token — enable Google under Firebase " +
                "Authentication → Sign-in method. (${e.message})"
        }
        return account to GroupGate.check(account.idToken)
    }

    private suspend fun signInToFirebase(idToken: String) = suspendCancellableCoroutine { cont ->
        FirebaseAuth.getInstance()
            .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
            .addOnSuccessListener { if (cont.isActive) cont.resume(Unit) }
            .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
    }

    /**
     * Requests the Drive access token.
     *
     * When Google needs to show a consent screen the result carries a pending intent instead of a
     * token; the caller must launch it through an
     * `ActivityResultContracts.StartIntentSenderForResult` launcher.
     */
    suspend fun authorizeDrive(activity: Activity): AuthorizationResult =
        suspendCancellableCoroutine { cont ->
            Identity.getAuthorizationClient(activity)
                .authorize(driveRequest)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

    /** The access token, if one can be had without showing anything. Null means consent needed. */
    suspend fun driveTokenOrNull(activity: Activity): String? =
        runCatching { authorizeDrive(activity).accessToken }.getOrNull()

    /** Pulls the access token out of the consent screen's result. */
    fun tokenFromConsent(activity: Activity, data: Intent?): String? =
        runCatching {
            Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
                .accessToken
        }.getOrNull()

    fun signOut() = FirebaseAuth.getInstance().signOut()
}
