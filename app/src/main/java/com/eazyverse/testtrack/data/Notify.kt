package com.eazyverse.testtrack.data

import com.eazyverse.testtrack.Config
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Asks the membership service to send a push on this device's behalf.
 *
 * The phone cannot send one itself. FCM's v1 API authorises with a token minted from a service
 * account key, and a service account key inside an APK is a project credential in the hands of
 * anyone who unzips the file. The key lives in the Apps Script instead, which checks who is
 * asking before it sends anything.
 *
 * Everything here is best effort and silent. A submission is recorded by the Firestore write, not
 * by the notification, and it is waiting in the admin queue either way; a tester who could not
 * reach the notifier has lost somebody else a little speed, not their own submission, and an
 * error banner about it would be noise about a thing they cannot act on.
 */
object Notify {

    /**
     * A Firebase ID token, not the Google one the membership check sends.
     *
     * The service keys everything it does off Firebase uids, which the Google token does not
     * carry. This one also comes from the session already in hand, where a fresh Google token can
     * mean an account chooser appearing over whatever the tester was doing.
     */
    private suspend fun firebaseToken(): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return suspendCancellableCoroutine { cont ->
            user.getIdToken(false)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it.token) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        }
    }

    private suspend fun post(body: JSONObject) = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(Config.GATE_URL)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            // Reuses the gate's client, so this inherits its timeouts and its connection pool
            // rather than standing up a second one for two calls a week.
            GroupGate.http.newCall(request).execute().close()
        }
    }

    /** Tells whoever is watching the admin queue that something has arrived in it. */
    suspend fun submission(appName: String) {
        val token = firebaseToken() ?: return
        post(
            JSONObject()
                .put("idToken", token)
                .put("action", "notifySubmission")
                .put("name", appName)
        )
    }
}
