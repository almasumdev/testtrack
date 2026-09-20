package com.eazyverse.testtrack.net

import android.content.Context
import com.eazyverse.testtrack.data.Device
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * The bridge between the sign-in the app already has and a session of our own.
 *
 * Google stays because Drive and the testers-group check both need it. What changes is that the
 * API trusts its own token afterwards, so no ordinary request depends on Firebase being reachable
 * and leaving Firebase later is this one file.
 */
object ApiAuth {
    /**
     * Make sure there is a usable session, exchanging a Firebase token for one if not.
     *
     * Called wherever the app is about to talk to the API and might not have a session yet:
     * launch, and after a sign-in. Cheap when there already is one, which is nearly always.
     *
     * False is no longer a reason not to make the call. The crew list, the feed and the board
     * answer without a token, and a stranger reading them is the whole point rather than a
     * failure: the one question somebody arriving has is whether real people are in here, and
     * the answer used to sit behind the screen asking them to sign in.
     *
     * So the answer is only worth checking on the screens that show one person's own things,
     * and there it means "sign in", never "you are offline". [Api] sends no Authorization header
     * when there is no token, which is exactly what the server reads as a stranger.
     */
    suspend fun ensure(context: Context): Boolean {
        ApiSession.load(context)
        if (ApiSession.signedIn) return true
        return refresh(context)
    }

    /** Trade the current Firebase user's ID token for ours. False if nobody is signed in. */
    suspend fun refresh(context: Context): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        return runCatching {
            val idToken = user.getIdToken(false).await().token ?: return false
            Api.signIn(idToken, Device.fingerprint(context).orEmpty())
            true
        }.getOrDefault(false)
    }

    fun signOut() = Api.signOut()
}
