package com.eazyverse.testtrack.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.eazyverse.testtrack.Config
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

/**
 * Raised when a step of signing in never came back at all.
 *
 * Not a failure anybody reported to us: it is what we say when nothing was reported, because the
 * call we were waiting on has no limit of its own and would otherwise leave the button spinning
 * until the tester gave up. Carries text meant for a person, so it keeps its own wording.
 */
class StalledException(message: String) : Exception(message)

/** Result of asking the membership service whether this account is in the Google Group. */
sealed interface GateResult {
    data class Member(val email: String) : GateResult
    data class NotMember(val email: String) : GateResult
    data class Failed(val reason: String) : GateResult
}

/** The address the service resolved from the token, or empty when it did not name one. */
fun GateResult.address(): String = when (this) {
    is GateResult.Member -> email
    is GateResult.NotMember -> email
    is GateResult.Failed -> ""
}

/**
 * The one form of an address that two systems can agree on.
 *
 * Gmail treats dots and `+suffixes` in the local part as decoration: `md.billamaruf@gmail.com`
 * and `mdbillamaruf@gmail.com` are one account, and Google will hand back either spelling
 * depending on who is asked. The membership service already folds them before it looks anybody
 * up, so it answers about the dotless form while the phone holds whatever the account sheet
 * reported, and a comparison between the two strings says "different person" about one person.
 *
 * Deliberately identical to `norm()` in gate.gs. The two run in different languages on different
 * machines and have to agree, so if one changes the other has to change with it.
 */
fun normalizeAddress(email: String): String {
    val lower = email.trim().lowercase()
    val at = lower.lastIndexOf('@')
    if (at <= 0 || at == lower.length - 1) return lower

    val domain = lower.substring(at + 1)
    if (domain != "gmail.com" && domain != "googlemail.com") return lower

    // Only Gmail. Every other provider is entitled to treat a dot as part of the name, and
    // stripping one there would merge two genuinely different people.
    val user = lower.substring(0, at).substringBefore('+').replace(".", "")
    return "$user@gmail.com"
}

/**
 * Whether this verdict is about somebody other than the account we believe we are signed in as.
 *
 * A phone can hold several Google accounts, and the token for a re-check comes from Credential
 * Manager, not from the session. When more than one is authorised it picks — sometimes by auto
 * select, sometimes by showing a sheet — and it can hand back the other one. The verdict is
 * then perfectly correct about an address the tester is not using, which is how the group step
 * could say "you are in" to somebody who is not and "you are not" to somebody who is, differently
 * on different launches.
 *
 * Compared after [normalizeAddress], because the first version of this compared raw strings and
 * accused every tester with a dot in their Gmail of being a different person. A third of the
 * group writes their address that way. A guard against the wrong account is worth nothing if it
 * cannot tell one account from two.
 *
 * Deliberately only ever rejects a verdict that names a *different* address. A blank one means the
 * service did not say, and treating silence as a mismatch would make the whole gate inert.
 */
fun GateResult.isAboutSomeoneElse(signedInAs: String?): Boolean {
    val said = address()
    if (said.isBlank() || signedInAs.isNullOrBlank()) return false
    return normalizeAddress(said) != normalizeAddress(signedInAs)
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
     * Shows the Google account chooser and returns the ID token plus the chosen address.
     * The token's `aud` is [Config.WEB_CLIENT_ID], which is what the service checks.
     *
     * [GetSignInWithGoogleOption] rather than [GetGoogleIdOption], which is what this used to send
     * and is a different flow wearing the same name. GetGoogleIdOption is the one-tap bottom
     * sheet, a suggestion Google offers, and Android throttles suggestions: dismiss it a couple of
     * times and it is withheld for the rest of the day. What comes back then is
     * NoCredentialException, so a tester with three Google accounts on their phone was told there
     * were none on it, and the only cure was to wait. This one is the flow behind a "Sign in with
     * Google" button. It shows the full chooser every time it is asked, because somebody pressed
     * something to ask.
     *
     * @throws NotGmailException if the chosen account is not @gmail.com.
     */
    suspend fun signIn(context: Context): GoogleAccount {
        val option = GetSignInWithGoogleOption.Builder(Config.WEB_CLIENT_ID).build()
        return try {
            credential(context, option)
        } catch (e: NoCredentialException) {
            // Asked again because the first refusal is not believable.
            //
            // Nothing in this app differs between one press of the button and the next: the token
            // is only written on success, and no flag is touched by a failure. So when the first
            // press says there is no account and the second offers a chooser, the account was
            // there the whole time and Play services had not finished looking. It resolves its
            // provider and reads its account list lazily, and a cold call can return before that
            // is done.
            //
            // Only NoCredentialException. Somebody who closed the chooser gets
            // GetCredentialCancellationException instead, and reopening it under them because
            // they shut it would be its own kind of rude.
            delay(RETRY_PAUSE)
            credential(context, option)
        }
    }

    /** Long enough for Play services to finish waking, short enough to feel like one tap. */
    private const val RETRY_PAUSE = 600L

    /**
     * A replacement token for an account that has already signed in once, or nothing.
     *
     * ID tokens expire after an hour, so re-checking membership later needs a new one. Filtering
     * to already-authorised accounts lets Google hand one back without showing anything.
     *
     * It used to fall back to the full chooser when that failed, and the chooser is the problem.
     * Somebody who pressed a button that says check whether I have joined was shown a list of
     * every Google account on the phone and asked to pick one, which is a question they had no
     * reason to expect and could answer wrongly: the verdict would then be about an account they
     * are not signed in as. That case was caught afterwards and explained, but being saved by an
     * error message is not the same as not being asked.
     *
     * So it throws instead, and the caller says the sign-in has expired. Nothing offers an
     * account that is not the one already signed in.
     */
    suspend fun refresh(context: Context): GoogleAccount =
        credential(
            context,
            GetGoogleIdOption.Builder()
                .setServerClientId(Config.WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .build()
        )

    /**
     * One [CredentialManager], not a new one per attempt.
     *
     * Building it resolves and binds the credential provider, and doing that again on every press
     * of the button means every press races the same cold start. Held against the application
     * context so it outlives the activity; the activity is still what gets passed to
     * `getCredential`, because that is the thing the chooser has to be shown over.
     */
    @Volatile private var cached: CredentialManager? = null

    private fun manager(context: Context): CredentialManager =
        cached ?: CredentialManager.create(context.applicationContext).also { cached = it }

    private suspend fun credential(context: Context, option: CredentialOption): GoogleAccount {
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = manager(context).getCredential(context, request)
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

                // A missing `isMember` is not a "no".
                //
                // optBoolean defaults to false, so every reply that did not explicitly say true
                // came back as "isn't in the group yet": a truncated body, a shape the script
                // changed, an error it phrased in a field this does not read. Telling somebody
                // who joined an hour ago that they are not a member is the answer most likely to
                // make them give up and least likely to be questioned, because it sounds like a
                // fact rather than a failure. An answer we cannot read now says so.
                if (!json.has("isMember")) {
                    return@withContext GateResult.Failed(
                        "The membership check gave an answer we couldn't read. " +
                            "Try again in a moment. (${raw.take(100)})"
                    )
                }

                val email = json.optString("email")
                if (json.getBoolean("isMember")) GateResult.Member(email)
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
