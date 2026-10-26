package com.eazyverse.testtrack.net

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Swapping a refused access token for a new pair, for [Api] and its image client.
 *
 * Its own object because the rule is subtle and has bitten before: see [quietly].
 */
internal object ApiRefresh {
    /**
     * Try the refresh token once. False means the session is really gone.
     *
     * One at a time, and only for the token that was refused. The server retires a refresh token
     * the moment it is used, so when a launch after the access token has lapsed sends five
     * requests at once and all five come back 401, five refreshes with the same token meant one
     * won and four were told to sign in again: the screen said the session had ended, and the next
     * launch, holding the winner's pair, worked. Now the first refreshes, and the rest find the
     * token they were refused with has already been replaced and simply retry with the new one.
     */
    fun quietly(refused: String?): Boolean =
        synchronized(this) {
            val current = ApiSession.access
            if (current != null && current != refused) return true
            now()
        }

    private fun now(): Boolean {
        val generation = ApiSession.generation
        val token = ApiSession.refresh ?: return false
        return try {
            val (status, text) =
                Api.call(
                    Request
                        .Builder()
                        .url(Api.url("/auth/refresh"))
                        .post(Api.json.encodeToString(RefreshIn(token)).toRequestBody(Api.JSON)),
                    null,
                )
            if (status !in 200..299) {
                if (status == 401) ApiSession.dropTokens(generation) // Refused, not unreachable: the pair is dead.
                if (status == 403) Api.failure(status, text) // A deleted account ends here too.
                false
            } else {
                ApiSession.remember(Api.json.decodeFromString<Tokens>(text), generation)
            }
        } catch (_: Exception) {
            false
        }
    }
}
