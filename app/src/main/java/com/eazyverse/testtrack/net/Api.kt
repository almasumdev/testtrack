package com.eazyverse.testtrack.net

import com.eazyverse.testtrack.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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

    /**
     * Shared, because an image loader needs the same client and the same token.
     *
     * A proof screenshot is served by this API and refused without a session, so it is fetched
     * through [images], which puts the session on it. See [ProofImage].
     */
    val http =
        OkHttpClient
            .Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    /**
     * The client for proof screenshots: [http] with the session's token added to every request,
     * and refreshed once on a 401, the same as [send] does for JSON.
     *
     * The image loader used to be handed [http] itself, which carries no token, so every proof
     * request was refused and no screenshot ever showed.
     */
    val images: OkHttpClient =
        http
            .newBuilder()
            .addInterceptor { chain ->
                fun withToken() =
                    chain
                        .request()
                        .newBuilder()
                        .apply { ApiSession.access?.let { header("Authorization", "Bearer $it") } }
                        .build()
                val first = chain.proceed(withToken())
                if (first.code != 401 || !refreshQuietly()) return@addInterceptor first
                first.close()
                chain.proceed(withToken())
            }.build()

    @PublishedApi internal fun url(path: String) = BuildConfig.API_BASE + path

    // ---------------------------------------------------------------- auth

    /** Trade a Google sign-in for our own pair, and remember it. */
    suspend fun signIn(
        idToken: String,
        deviceFp: String,
        generation: Long = ApiSession.generation,
    ): Tokens {
        val tokens =
            post<SessionIn, Tokens>(
                "/auth/session",
                SessionIn(idToken = idToken, deviceFp = deviceFp, timezone = TimeZone.getDefault().id),
                authed = false,
            )
        if (!ApiSession.remember(tokens, generation)) throw kotlinx.coroutines.CancellationException("Session changed")
        return tokens
    }

    fun signOut() = ApiSession.clear()

    // ---------------------------------------------------------------- me

    suspend fun me(): Me = get("/me")

    // ---------------------------------------------------------------- apps

    /**
     * What Play shows for a pasted package name or link.
     *
     * The server does the looking, through a browser signed in as an account that is on the
     * tester group, because a closed testing listing is invisible to everybody else. Whatever
     * the owner pasted goes up whole: picking the package out of a Play Console URL is work a
     * regular expression does perfectly and a person should not be asked to do.
     */
    suspend fun lookupApp(
        query: String,
        closed: Boolean,
    ): Listing = post("/apps/lookup", LookupIn(query = query, closed = closed))

    // ---------------------------------------------------------------- group

    /**
     * Whether this account is on the Google Group Play gates closed testing behind.
     *
     * Asked of our own server rather than of the Apps Script behind it. The server keeps a copy
     * of the member list, so this is usually a database read; when it is not, the server refreshes
     * the whole list and answers from that. The phone knowing the script's address at all was the
     * older arrangement, and it put the group's name, the failure wording and the retry policy in
     * two places.
     *
     * Only your own address is accepted, which is the server's rule, not a convenience here.
     */
    suspend fun groupCheck(email: String): GroupOut = post("/group/check", GroupIn(email = email))

    // ---------------------------------------------------------------- feed

    suspend fun posts(before: String? = null): PostPage = get("/feed/posts" + if (before == null) "" else "?before=$before")

    suspend fun feedbackOptions(): FeedbackOptions = get("/feed/options")

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
    suspend fun visit(
        seatId: String,
        usedMs: Long,
    ): ApiSeat = post("/crews/seats/$seatId/visit", VisitIn(usedMs))

    suspend fun withdraw(seatId: String): ApiSeat = delete("/crews/seats/$seatId")

    // ------------------------------------------- standing, apps and the profile

    suspend fun board(): ApiBoard = get("/board")

    suspend fun profile(): ApiProfile = get("/me/profile")

    /** Change this account's name or face. Nothing else about it is ours to set. */
    suspend fun updateMe(body: MeIn): Me = patch("/me", body)

    suspend fun myApps(): MyApps = get("/me/apps")

    /** Where a piece of proof lives. Needs the token, so it is fetched, never linked. */
    fun proofUrl(ref: String): String = url("/uploads/$ref")

    // ---------------------------------------------------------------- files

    /**
     * Send a screenshot, and get back the reference a review is submitted with.
     *
     * Multipart rather than JSON: the bytes are the payload, and base64 in a body would be a
     * third larger on the wire for nothing. The filename is deliberately fixed — the server names
     * the file by its own id, and whatever the picker called it is not ours to pass on.
     */
    suspend fun upload(
        bytes: ByteArray,
        mediaType: String,
    ): ApiUpload {
        val body =
            MultipartBody
                .Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    "proof",
                    bytes.toRequestBody(mediaType.toMediaType(), 0, bytes.size),
                ).build()
        return send(Request.Builder().url(url("/uploads")).post(body))
    }

    // ---------------------------------------------------------------- plumbing

    @PublishedApi
    internal suspend inline fun <reified T> get(path: String): T = send(Request.Builder().url(url(path)).get())

    @PublishedApi
    internal suspend inline fun <reified In, reified Out> patch(
        path: String,
        body: In,
    ): Out = send(Request.Builder().url(url(path)).patch(json.encodeToString(body).toRequestBody(JSON)))

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
        val generation = ApiSession.generation
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
                ApiSession.remember(json.decodeFromString<Tokens>(text), generation)
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
