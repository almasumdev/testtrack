package com.eazyverse.testtrack.data

import android.content.Context
import com.eazyverse.testtrack.net.ApiReview
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The last answer the server gave about each review, kept on the phone.
 *
 * So the review page opens straight onto where it was, offline too, instead of a loader or an
 * error. Only ever what the server said: nothing here is moved on by the phone, so what is shown
 * offline is behind at worst, never ahead of the truth. Everything that changes a review still
 * goes to the server first and is saved here only from its answer.
 *
 * Keyed by account and post, so a second account on the phone never opens on the first one's.
 */
object ReviewKeep {
    private const val PREFS = "review_keep"
    private val json = Json { ignoreUnknownKeys = true }

    fun get(
        context: Context,
        postId: String,
    ): ApiReview? {
        val raw = prefs(context).getString(key(postId), null) ?: return null
        return runCatching { json.decodeFromString<ApiReview>(raw) }.getOrNull()
    }

    fun put(
        context: Context,
        postId: String,
        review: ApiReview,
    ) = prefs(context).edit().putString(key(postId), json.encodeToString(review)).apply()

    /** Signing out: the next account starts clean. */
    fun clear(context: Context) = prefs(context).edit().clear().apply()

    private fun key(postId: String) = "${AuthRepo.uid.orEmpty()}:$postId"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
