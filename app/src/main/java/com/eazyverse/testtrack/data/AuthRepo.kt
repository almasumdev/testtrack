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
import kotlinx.coroutines.withTimeoutOrNull
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
     * The ID token from the last sign-in, held only in memory.
     *
     * Without it every membership re-check would put the Google account sheet in front of a
     * tester who signed in a minute ago, purely to mint a token identical to the one we already
     * had. Google's expire after an hour; this is retired early so a check never races the edge.
     */
    private var token: String? = null
    private var tokenAt = 0L
    private const val TOKEN_LIFETIME = 45 * 60 * 1000L

    private val liveToken: String?
        get() = token?.takeIf { System.currentTimeMillis() - tokenAt < TOKEN_LIFETIME }

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
        token = account.idToken
        tokenAt = System.currentTimeMillis()
        firebaseError = try {
            signInToFirebase(account.idToken)
            null
        } catch (e: Exception) {
            "You're signed in, but Firebase turned the token down. Google needs enabling under " +
                "Firebase Authentication, in Sign-in method. (${e.message})"
        }

        // Bounded, and a slow answer is treated as "not yet" rather than as a failure.
        //
        // This call is the slow half of signing in, and it is not one request: the service checks
        // the token with Google and then reads the whole testers roster, which gets longer every
        // time somebody joins. It sits on the sign-in spinner, so as the group grew it turned a
        // few seconds into a wait people quite reasonably read as the app being broken.
        //
        // Little is lost by giving up early. The next screen's first step is "Join the testers
        // group" with a Verify button that runs this very check, so a member who times out here
        // spends one tap instead of a minute, and is shown no error on the way.
        val gate = withTimeoutOrNull(GATE_BUDGET) { GroupGate.check(account.idToken) }
        return account to (gate ?: GateResult.NotMember(account.email))
    }

    /** Long enough for a warm service on a poor connection, short enough to keep a person. */
    private const val GATE_BUDGET = 9_000L

    /**
     * Asks the membership service again — after the tester has gone off and joined the group.
     *
     * Reuses the sign-in token while it is still good, so this is usually a bare network call
     * with no Google UI at all. Returns the account only when one was actually chosen, which is
     * the sole case where the signed-in address can have changed.
     */
    suspend fun recheckGroup(activity: Activity): Pair<GoogleAccount?, GateResult> {
        liveToken?.let { return null to GroupGate.check(it) }

        val account = GroupGate.refresh(activity)
        token = account.idToken
        tokenAt = System.currentTimeMillis()
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

    /**
     * Has this account already granted Drive access?
     *
     * Asked rather than remembered. The grant lives on Google's servers against the account and
     * survives sign-out, reinstall and a new phone — whereas the local flag did not, so signing
     * out and back in used to present "Connect Drive" again for an authorisation that had never
     * been lost. This resolves silently when the grant exists; it shows nothing either way.
     */
    suspend fun hasDriveAccess(activity: Activity): Boolean =
        driveTokenOrNull(activity) != null

    /** Pulls the access token out of the consent screen's result. */
    fun tokenFromConsent(activity: Activity, data: Intent?): String? =
        runCatching {
            Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(data)
                .accessToken
        }.getOrNull()

    fun signOut() {
        token = null
        tokenAt = 0L
        FirebaseAuth.getInstance().signOut()
    }
}
