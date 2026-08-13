package com.eazyverse.testtrack.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.eazyverse.testtrack.Config
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The signed-in Google account. */
data class GoogleAccount(val idToken: String, val email: String)

/** Raised when a non-Gmail account is chosen — sign-in is Gmail-only by product decision. */
class NotGmailException(val email: String) : Exception("$email is not a Gmail account")

/** Result of asking the membership service whether this account is in the Google Group. */
sealed interface GateResult {
    data class Member(val email: String) : GateResult
    data class NotMember(val email: String) : GateResult
    data class Failed(val reason: String) : GateResult
}

/**
 * Membership verification.
 *
 * Google publishes no API for reading a consumer `@googlegroups.com` roster — the Admin SDK and
 * Cloud Identity are Workspace-only, and Play Console accepts only consumer groups. Apps Script's
 * `GroupsApp` is the one thing that works, because it authorises on *permission* (group owner)
 * rather than account edition.
 *
 * The verdict is computed on Google's servers from a token this device cannot forge, and the
 * endpoint returns only `{email, isMember}` about the caller — so nothing here leaks the roster
 * and the phone never asserts its own membership.
 */
object GroupGate {

    /**
     * Short enough that a person is still waiting when it gives up.
     *
     * Sixty seconds is a sensible ceiling for a background job and a terrible one for a button.
     * The membership check sits on the sign-in spinner, so the old limits meant a stalled Apps
     * Script could hold "Signing in..." for the better part of two minutes before saying anything,
     * which every tester read as the app being broken. A warm call answers in under two seconds.
     */
    internal val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        // Apps Script /exec answers 302 -> script.googleusercontent.com and the JSON lives at
        // the redirect target. OkHttp follows it and downgrades POST to GET, which is exactly
        // what that endpoint expects. Leave followRedirects on.
        .followRedirects(true)
        .build()

    /**
     * Shows the Google account picker and returns the ID token plus the chosen address.
     * The token's `aud` is [Config.WEB_CLIENT_ID], which is what the service checks.
     *
     * @throws NotGmailException if the chosen account is not @gmail.com.
     */
    suspend fun signIn(context: Context): GoogleAccount = credential(context, silent = false)

    /**
     * A replacement token for an account that has already signed in once.
     *
     * ID tokens expire after an hour, so re-checking membership later needs a new one. Filtering
     * to already-authorised accounts lets Google return it without showing anything; the picker
     * only appears if that fails, which it does when the grant has been revoked.
     */
    suspend fun refresh(context: Context): GoogleAccount =
        try {
            credential(context, silent = true)
        } catch (e: GetCredentialException) {
            credential(context, silent = false)
        }

    private suspend fun credential(context: Context, silent: Boolean): GoogleAccount {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(Config.WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(silent)
            .setAutoSelectEnabled(silent)
            .build()

        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = CredentialManager.create(context).getCredential(context, request)
        val credential = response.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val email = google.id            // GoogleIdTokenCredential.id is the account address

            // The picker cannot be filtered by domain, so reject after selection with a clear
            // message rather than letting a Workspace account fail confusingly later.
            val domain = email.substringAfterLast('@', "").lowercase()
            if (domain !in Config.ALLOWED_DOMAINS) throw NotGmailException(email)

            return GoogleAccount(google.idToken, email)
        }
        error("Unexpected credential type: ${credential.type}")
    }

    /** Asks the membership service. Network and parse failures are values, not exceptions. */
    suspend fun check(idToken: String): GateResult = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().put("idToken", idToken).toString()
            val request = Request.Builder()
                .url(Config.GATE_URL)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext GateResult.Failed("HTTP ${response.code}")

                val json = JSONObject(raw)
                json.optString("error").takeIf { it.isNotEmpty() }?.let {
                    return@withContext GateResult.Failed(it)
                }

                val email = json.optString("email")
                if (json.optBoolean("isMember", false)) GateResult.Member(email)
                else GateResult.NotMember(email)
            }
        } catch (e: IOException) {
            // A dropped connection is not a verdict, and OkHttp's message for one names a Google
            // IP address, which reads like the deployment is broken rather than the network.
            GateResult.Failed("Couldn't reach the membership service. Check your connection and try again.")
        } catch (e: Exception) {
            GateResult.Failed(e.message ?: "Membership check failed")
        }
    }
}
