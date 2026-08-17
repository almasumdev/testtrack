package com.eazyverse.testtrack.data

import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eazyverse.testtrack.Config
import android.accounts.Account
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
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

    /**
     * The Drive request, pinned to the account that is signed in.
     *
     * Without [AuthorizationRequest.Builder.setAccount] the authorization API is not told which
     * account the question is about, and on a phone carrying more than one Google account it
     * answers about whichever one it picks. That is wrong twice over: an existing grant on the
     * signed-in account goes unseen, so the setup screen offers Connect Drive for an
     * authorisation nobody lost, and a grant made from that screen can land on the other account,
     * which would put a tester's proof in a Drive that is not the one their record points at.
     *
     * Firebase is the authority on who is signed in, because it is the session Firestore rules
     * key on. [Session.email] is the same address remembered locally and only stands in while
     * Firebase is still restoring.
     */
    private val driveRequest: AuthorizationRequest
        get() = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(Config.DRIVE_SCOPE)))
            .apply {
                val address = FirebaseAuth.getInstance().currentUser?.email ?: Session.email
                address?.let { setAccount(Account(it, "com.google")) }
            }
            .build()

    /**
     * How long Play services gets to answer before the screen stops believing it will.
     *
     * There is no timeout inside the authorization API: the task simply never completes when
     * Play services cannot service the call, and it retries in the background about every ten
     * seconds while it does so. A coroutine awaiting that task waits for the rest of the day, and
     * the only thing the tester sees is a spinner on a button that never stops.
     */
    const val AUTH_TIMEOUT_MS = 20_000L

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

    /**
     * Google will not re-issue a token without being asked out loud.
     *
     * Not the same as being signed out, and worth keeping the two apart. The Firebase session
     * persists and refreshes itself, so the account, its uid and its Drive are all still here and
     * everything else in the app carries on working. What has aged out is the Google ID token the
     * membership service verifies, which is a thing only Google can mint and only for somebody
     * standing in front of the phone.
     *
     * So the answer is not to sign anybody out. It is to ask Google once, from a control that
     * says so.
     */
    const val NEEDS_GOOGLE = "needs-google"

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
     * Signs in and exchanges the token for a Firebase session. Nothing else.
     *
     * Deliberately only those two things. Setup exists to settle group membership, Drive, usage
     * access and reminders, and it already asks each of them for itself. Sign-in used to answer
     * two of those a second time while a spinner sat over the screen saying "Signing in...", which
     * made the slowest question in the app, the group roster, the thing standing between a tester
     * and the rest of it. Getting a session is quick; everything after it belongs to the screen
     * that shows its progress.
     *
     * A Firebase failure is recorded rather than thrown, so a tester with a working Google account
     * still lands somewhere they can act instead of watching sign-in collapse.
     */
    suspend fun signIn(activity: Activity): GoogleAccount {
        val account = withTimeoutOrNull(GOOGLE_LIMIT) { GroupGate.signIn(activity) }
            ?: throw StalledException(
                "Google never came back with an answer. Close TestTrack from your recent apps, " +
                    "open it again and tap Continue with Google. If it happens a second time, " +
                    "restart the phone. It is Play services that has stalled, not your account."
            )

        token = account.idToken
        tokenAt = System.currentTimeMillis()
        firebaseError = try {
            withTimeout(FIREBASE_LIMIT) { signInToFirebase(account.idToken) }
            null
        } catch (e: TimeoutCancellationException) {
            // Caught before CancellationException on purpose: a timeout is one, so the branch
            // below would rethrow it and the spinner this exists to end would go on turning.
            "Google signed you in, but TestTrack's own sign-in didn't answer. Check your " +
                "connection and tap Continue with Google again."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "You're signed in, but Firebase turned the token down. Google needs enabling under " +
                "Firebase Authentication, in Sign-in method. (${e.message})"
        }
        return account
    }

    /**
     * The verdict for the token we already hold, or null when we do not hold one.
     *
     * The quiet counterpart of [recheckGroup], for the background check the setup screen runs on
     * its own initiative. It asks Credential Manager for nothing, which is the whole point: the
     * interactive path can put an account sheet on screen, and a check nobody requested must never
     * do that. Null means there are no credentials to ask with, which is a reason to leave the
     * last known answer alone rather than guess at a new one.
     *
     * Using only the sign-in token also settles *whose* membership is being reported: it is the
     * account that signed in, and cannot be some other Gmail on the same phone.
     */
    suspend fun recheckGroupSilently(): GateResult? = liveToken?.let { GroupGate.check(it) }

    /**
     * Asks the membership service again — after the tester has gone off and joined the group.
     *
     * Reuses the sign-in token while it is still good. Without one it goes back to Google for a
     * fresh token, which can show the account sheet, so this belongs behind a button somebody
     * pressed. Returns the account only when one was actually chosen, which is the sole case where
     * the address the verdict is about can differ from the one we signed in as.
     */
    suspend fun recheckGroup(activity: Activity): Pair<GoogleAccount?, GateResult> {
        liveToken?.let { return null to GroupGate.check(it) }

        // Silently or not at all. Google re-issues without showing anything for an account that
        // has used this app before, which is every account that reaches this screen. When it
        // cannot, the honest answer is that the sign-in has expired, not a list of every Google
        // account on the phone attached to a button that asked about one of them.
        val account = runCatching { GroupGate.refresh(activity) }.getOrNull()
            ?: return null to GateResult.Failed(NEEDS_GOOGLE)

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
        withTimeoutOrNull(AUTH_TIMEOUT_MS) {
            runCatching { authorizeDrive(activity).accessToken }.getOrNull()
        }

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
