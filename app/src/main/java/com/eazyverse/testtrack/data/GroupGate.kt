package com.eazyverse.testtrack.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialOption
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * The account the picker handed back.
 *
 * [name] is what Google has on the account, which is what everybody else in a cohort should see
 * against your apps. The app used to throw it away and store the address with the domain cut off,
 * so a grid of thirteen developers read as a column of email fragments.
 */
data class GoogleAccount(
    val idToken: String,
    val email: String,
    val name: String,
)

/**
 * Raised when a step of signing in never came back at all.
 *
 * Not a failure anybody reported to us: it is what we say when nothing was reported, because the
 * call we were waiting on has no limit of its own and would otherwise leave the button spinning
 * until the tester gave up. Carries text meant for a person, so it keeps its own wording.
 */
class StalledException(
    message: String,
) : Exception(message)

/** Result of asking the membership service whether this account is in the Google Group. */
sealed interface GateResult {
    data class Member(
        val email: String,
    ) : GateResult

    data class NotMember(
        val email: String,
    ) : GateResult

    data class Failed(
        val reason: String,
    ) : GateResult
}

/** The address the service resolved from the token, or empty when it did not name one. */
fun GateResult.address(): String =
    when (this) {
        is GateResult.Member -> email
        is GateResult.NotMember -> email
        is GateResult.Failed -> ""
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
    internal val http: OkHttpClient =
        OkHttpClient
            .Builder()
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
            GetGoogleIdOption
                .Builder()
                .setServerClientId(Config.WEB_CLIENT_ID)
                .setFilterByAuthorizedAccounts(true)
                .setAutoSelectEnabled(true)
                .build(),
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

    private suspend fun credential(
        context: Context,
        option: CredentialOption,
    ): GoogleAccount {
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = manager(context).getCredential(context, request)
        val credential = response.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val google = GoogleIdTokenCredential.createFrom(credential.data)
            val email = google.id // GoogleIdTokenCredential.id is the account address

            // Google returns the profile name where the account has one, and both halves
            // separately where it does not. The address with its domain cut off is the last
            // resort, not the first: it is what this used to store for everybody.
            val name =
                google.displayName?.takeIf { it.isNotBlank() }
                    ?: listOfNotNull(
                        google.givenName?.takeIf { it.isNotBlank() },
                        google.familyName?.takeIf { it.isNotBlank() },
                    ).joinToString(" ").takeIf { it.isNotBlank() }
                    ?: email.substringBefore('@')

            return GoogleAccount(google.idToken, email, name)
        }
        error("Unexpected credential type: ${credential.type}")
    }

    /**
     * Asks our own server whether this account is on the group. Failures are values, never a "no".
     *
     * It used to post the Google ID token straight to the Apps Script, which meant the phone held
     * the script's address, the group's name and the retry policy, and the server held its own
     * copies of all three. The server now keeps a copy of the member list, so this is usually a
     * database read on its side, and there is one place where any of it can be wrong.
     *
     * The token is still taken, because the caller has one and dropping the parameter would
     * ripple through five call sites for no gain. It is no longer sent anywhere: the server knows
     * who is asking from our own session, which is a stronger claim than a token the phone chose.
     */
    suspend fun check(idToken: String): GateResult =
        withContext(Dispatchers.IO) {
            try {
                val me = Api.me()
                val answer = Api.groupCheck(me.email)
                if (answer.isMember) GateResult.Member(answer.email) else GateResult.NotMember(answer.email)
            } catch (e: ApiError) {
                // Never a negative. The server separates "could not ask" from "not a member" and
                // this must not put them back together: telling somebody who joined an hour ago
                // that they are not in the group sounds like a fact rather than an outage, and it
                // is the answer most likely to make them give up.
                GateResult.Failed(e.message)
            } catch (e: IOException) {
                // A dropped connection is not a verdict, and OkHttp's message for one names an IP
                // address, which reads like the deployment is broken rather than the network.
                GateResult.Failed("Couldn't reach the membership service. Check your connection and try again.")
            } catch (e: Exception) {
                GateResult.Failed(e.message ?: "Membership check failed")
            }
        }
}
