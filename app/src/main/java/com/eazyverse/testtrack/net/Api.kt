package com.eazyverse.testtrack.net

import com.eazyverse.testtrack.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Our own API, over the wire.
 *
 * The one place that knows about HTTP. Everything above works in the types from Wire.kt and in
 * [ApiError], and never sees a status code.
 *
 * An expired access token is refreshed once and the call retried, inside [send]. Doing that here
 * rather than in every caller is the whole reason this object exists: a fifteen minute token means
 * refreshing is ordinary rather than exceptional, and a screen that has to think about it is a
 * screen that will forget to.
 */
object Api {
    @PublishedApi internal val json = Json { ignoreUnknownKeys = true }

    @PublishedApi internal val JSON = "application/json; charset=utf-8".toMediaType()

    private val http =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @PublishedApi internal fun url(path: String) = BuildConfig.API_BASE + path

    // ---------------------------------------------------------------- auth

    /** Trade a Google sign-in for our own pair, and remember it. */
    suspend fun signIn(
        idToken: String,
        deviceFp: String,
    ): Tokens {
        val tokens =
            post<SessionIn, Tokens>(
                "/auth/session",
                SessionIn(idToken = idToken, deviceFp = deviceFp, timezone = TimeZone.getDefault().id),
                authed = false,
            )
        ApiSession.remember(tokens)
        return tokens
    }

    fun signOut() = ApiSession.clear()

    // ---------------------------------------------------------------- me

    suspend fun me(): Me = get("/me")

    // ---------------------------------------------------------------- feed

    suspend fun posts(before: String? = null): PostPage = get("/feed/posts" + if (before == null) "" else "?before=$before")

    suspend fun createPost(body: PostIn): ApiPost = post("/feed/posts", body)

    // ---------------------------------------------------------------- the guided review

    suspend fun claim(
        postId: String,
        deviceFp: String,
    ): ApiReview = post("/feed/posts/$postId/claim", ClaimIn(deviceFp))

    suspend fun installed(reviewId: String): ApiReview = post("/reviews/$reviewId/installed", Empty())

    suspend fun used(
        reviewId: String,
        usedMs: Long,
    ): ApiReview = post("/reviews/$reviewId/used", UsedIn(usedMs))

    suspend fun submit(
        reviewId: String,
        proofRef: String = "",
        written: String = "",
    ): ApiReview = post("/reviews/$reviewId/submit", SubmitIn(proofRef, written))

    suspend fun abandon(reviewId: String): ApiReview = post("/reviews/$reviewId/abandon", Empty())

    suspend fun myReviews(): List<ApiReview> = get("/reviews/mine")

    // ---------------------------------------------------------------- the owner's side

    suspend fun waiting(): List<ApiReview> = get("/reviews/waiting")

    suspend fun approve(reviewId: String): ApiReview = post("/reviews/$reviewId/approve", Empty())

    suspend fun reject(
        reviewId: String,
        reason: String,
    ): ApiDispute = post("/reviews/$reviewId/reject", RejectIn(reason))

    // ---------------------------------------------------------------- disputes

    suspend fun disputes(): List<ApiDispute> = get("/disputes")

    suspend fun vote(
        disputeId: String,
        forReviewer: Boolean,
    ): ApiDispute = post("/disputes/$disputeId/vote", VoteIn(forReviewer))

    // ---------------------------------------------------------------- crews

    suspend fun crews(): CrewList = get("/crews")

    /** One crew and everybody in it. Members only; the API refuses anybody else. */
    suspend fun crew(crewId: String): ApiCrew = get("/crews/$crewId")

    /**
     * Put an app into whichever crew is nearest to starting.
     *
     * Which crew that is stays the server's decision. A client that names its own crew is a
     * client picking which fourteen people test its app.
     */
    suspend fun takeSeat(body: SeatIn): ApiSeat = post("/crews/seats", body)

    suspend fun verdict(
        seatId: String,
        ok: Boolean,
        reason: String = "",
    ): ApiSeat = post("/crews/seats/$seatId/verdict", VerdictIn(ok, reason))

    /**
     * Note that this member opened somebody's app tonight.
     *
     * The one row the fortnight is actually made of. Sent when the other app comes to the
     * foreground rather than when a button is pressed: a button records an intention, and what
     * is owed is the opening.
     */
    suspend fun visit(seatId: String): ApiSeat = post("/crews/seats/$seatId/visit", Empty())

    suspend fun withdraw(seatId: String): ApiSeat = delete("/crews/seats/$seatId")

    // ---------------------------------------------------------------- plumbing

    @PublishedApi
    internal suspend inline fun <reified T> get(path: String): T = send(Request.Builder().url(url(path)).get())

    @PublishedApi
    internal suspend inline fun <reified T> delete(path: String): T = send(Request.Builder().url(url(path)).delete())

    @PublishedApi
    internal suspend inline fun <reified In, reified Out> post(
        path: String,
        body: In,
        authed: Boolean = true,
    ): Out =
        send(
            Request.Builder().url(url(path)).post(json.encodeToString(body).toRequestBody(JSON)),
            authed = authed,
        )

    /**
     * One request, with the token on it, refreshed once if it has expired.
     *
     * One retry, and only on a 401. A second failure means the refresh token is gone as well, and
     * retrying past that turns a signed-out person into a loop.
     */
    @PublishedApi
    internal suspend inline fun <reified T> send(
        builder: Request.Builder,
        authed: Boolean = true,
    ): T =
        withContext(Dispatchers.IO) {
            var attempt = call(builder, authed)
            if (attempt.first == 401 && authed && refreshQuietly()) {
                attempt = call(builder, true)
            }
            val (status, text) = attempt
            if (status !in 200..299) throw failure(status, text)
            json.decodeFromString<T>(text)
        }

    @PublishedApi
    internal fun call(
        builder: Request.Builder,
        authed: Boolean,
    ): Pair<Int, String> {
        if (authed) {
            ApiSession.access?.let { builder.header("Authorization", "Bearer $it") }
        }
        http.newCall(builder.build()).execute().use { response ->
            return response.code to (response.body?.string().orEmpty())
        }
    }

    /** Try the refresh token once. False means the session is really gone. */
    @PublishedApi
    internal fun refreshQuietly(): Boolean {
        val token = ApiSession.refresh ?: return false
        return try {
            val (status, text) =
                call(
                    Request
                        .Builder()
                        .url(url("/auth/refresh"))
                        .post(json.encodeToString(RefreshIn(token)).toRequestBody(JSON)),
                    authed = false,
                )
            if (status !in 200..299) {
                false
            } else {
                ApiSession.remember(json.decodeFromString<Tokens>(text))
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Turn the API's error body into something the app can match on. */
    @PublishedApi
    internal fun failure(
        status: Int,
        text: String,
    ): ApiError =
        try {
            val body = json.decodeFromString<ApiErrorOut>(text).error
            ApiError(status, body.code, body.message)
        } catch (_: Exception) {
            ApiError(status, "unknown", "Something went wrong. Try again in a moment.")
        }
}
