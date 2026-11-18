package com.eazyverse.testtrack.data

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Google Drive access older builds asked for, handed back.
 *
 * Pictures used to be kept in each account's own Drive, under a grant for the files this app made
 * there. They go to TestTrack's own server now and Google is asked for nothing but who is signing
 * in, so the grant is given up once, the first time this build starts with an account signed in.
 *
 * Quietly: nothing is shown and nothing waits on it. Google is first asked, without a screen,
 * whether the account ever gave the access; an account that never did has nothing to hand back
 * and is left alone, since revoking also makes Google ask for consent again at the next sign-in.
 * A try that fails (offline, Play services not answering) is made again on the next start.
 */
object OldDriveGrant {
    private const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val PREFS = "old_drive_grant"
    private const val KEY_DONE = "handed_back"

    /** Play services has no timeout of its own here: a task it cannot serve never completes. */
    private const val LIMIT_MS = 20_000L

    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Once per install, and at most one try per start. Returns at once; safe to call on every launch. */
    fun revokeOnce(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        scope.launch {
            try {
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                if (prefs.getBoolean(KEY_DONE, false)) return@launch
                // Nobody signed in: no account to ask about yet, so this waits for a start that has one.
                val address = FirebaseAuth.getInstance().currentUser?.email ?: return@launch
                val account = Account(address, "com.google")
                val scopes = listOf(Scope(SCOPE))
                val client = Identity.getAuthorizationClient(app)
                val held =
                    withTimeout(LIMIT_MS) {
                        client
                            .authorize(
                                AuthorizationRequest
                                    .builder()
                                    .setRequestedScopes(scopes)
                                    .setAccount(account)
                                    .build(),
                            ).await()
                    }
                if (held.accessToken != null) {
                    withTimeout(LIMIT_MS) {
                        client
                            .revokeAccess(
                                RevokeAccessRequest
                                    .builder()
                                    .setAccount(account)
                                    .setScopes(scopes)
                                    .build(),
                            ).await()
                    }
                }
                prefs.edit().putBoolean(KEY_DONE, true).apply()
            } catch (e: Exception) {
                // Offline, timed out, or Play services said no: nothing is marked, so the next start tries again.
                Log.w("OldDriveGrant", "Not handed back this time", e)
            }
        }
    }
}
