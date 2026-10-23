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
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
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
                // Our token is for our server only. A proof kept in Drive loads through this
                // client too, and Google has no business being handed it.
                if (!chain
                        .request()
                        .url
                        .toString()
                        .startsWith(BuildConfig.API_BASE)
                ) {
                    return@addInterceptor chain.proceed(chain.request())
                }

                fun withToken(token: String?) =
                    chain
                        .request()
                        .newBuilder()
                        .apply { token?.let { header("Authorization", "Bearer $it") } }
                        .build()
                val used = ApiSession.access
                val first = chain.proceed(withToken(used))
                if (first.code != 401 || !refreshQuietly(used)) return@addInterceptor first
                first.close()
                chain.proceed(withToken(ApiSession.access))
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

    fun signOut() {
        ApiSession.queueRevocation()
        ApiSession.clear()
        Live.reset()
    }

    internal suspend fun revoke(refresh: String) {
        sendText(
            Request
                .Builder()
                .url(url("/auth/logout"))
                .post(json.encodeToString(RefreshIn(refresh)).toRequestBody(JSON)),
            authed = false,
        )
    }

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

    /** The first page is kept on the phone (see [lastPosts]); later pages are not. */
    suspend fun posts(before: String? = null): PostPage = if (before == null) kept(POSTS) else get("$POSTS?before=$before")

    suspend fun feedbackOptions(): FeedbackOptions = get("/feed/options")

    suspend fun createPost(body: PostIn): ApiPost = post("/feed/posts", body)

    /** What happened in the community over the last month, newest first. Readable signed out. */
    suspend fun activity(): ApiActivity = kept(ACTIVITY)

    /** The reader's own requests, open or closed, each with every review it has had. */
    suspend fun myPosts(): List<ApiMyPost> = kept(MY_POSTS)

    /** Stop taking reviews; the tix held for slots nobody took come back. */
    suspend fun closePost(postId: String): ClosedPost = post("/feed/posts/$postId/close", Empty())

    // ---------------------------------------------------------------- the guided review

    suspend fun claim(
        postId: String,
        deviceFp: String,
    ): ApiReview = post("/feed/posts/$postId/claim", ClaimIn(deviceFp))

    suspend fun installed(reviewId: String): ApiReview = post("/reviews/$reviewId/installed", Empty())

    suspend fun used(
        reviewId: String,
        usedMs: Long,
        days: Int = 0,
    ): ApiReview = post("/reviews/$reviewId/used", UsedIn(usedMs, days))

    suspend fun submit(
        reviewId: String,
        proofRef: String = "",
        written: String = "",
        driveToken: String = "",
    ): ApiReview = post("/reviews/$reviewId/submit", SubmitIn(proofRef, written, driveToken))

    suspend fun abandon(reviewId: String): ApiReview = post("/reviews/$reviewId/abandon", Empty())

    /** Whether a live app is still installed a few days after its review was sent. */
    suspend fun kept(
        reviewId: String,
        installed: Boolean,
    ): ApiReview = post("/reviews/$reviewId/kept", KeptIn(installed))

    suspend fun myReviews(): List<ApiReview> = kept(MY_REVIEWS)

    // ---------------------------------------------------------------- the owner's side

    suspend fun waiting(): List<ApiReview> = kept(WAITING)

    suspend fun approve(reviewId: String): ApiReview = post("/reviews/$reviewId/approve", Empty())

    suspend fun reject(
        reviewId: String,
        reason: String,
    ): ApiDispute = post("/reviews/$reviewId/reject", RejectIn(reason))

    // ---------------------------------------------------------------- disputes

    suspend fun disputes(): List<ApiDispute> = kept(DISPUTES)

    suspend fun vote(
        disputeId: String,
        forReviewer: Boolean,
    ): ApiDispute = post("/disputes/$disputeId/vote", VoteIn(forReviewer))

    // ---------------------------------------------------------------- crews

    suspend fun crews(): CrewList = kept(CREWS)

    /** One crew and everybody in it. Members only; the API refuses anybody else. */
    suspend fun crew(crewId: String): ApiCrew = get("/crews/$crewId")

    /**
     * Check the app on Play for the testers group and put it into the crew nearest to starting.
     *
     * Slow on purpose: the server opens the listing in a signed-in browser before it answers.
     * Which crew it lands in stays the server's decision. A client that names its own crew is a
     * client picking which fourteen people test its app.
     */
    suspend fun takeSeat(body: SeatIn): ApiSeat = post("/crews/seats", body)

    /** Say whether an app the Play check could not look at installed for you. */
    suspend fun vote(
        seatId: String,
        installed: Boolean,
        reason: String = "",
    ): ApiSeat = post("/crews/seats/$seatId/vote", SeatVoteIn(installed, reason))

    /** Tell an app's owner it will not install, and why. */
    suspend fun report(
        seatId: String,
        reason: String,
    ): ApiSeat = post("/crews/seats/$seatId/report", ReportIn(reason))

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

    // ---------------------------------------------------------------- push

    /** This phone can be reached at this token, by whoever is signed in on it now. */
    suspend fun keepPushToken(token: String) =
        sendEmpty(
            Request
                .Builder()
                .url(url("/me/push-token"))
                .put(json.encodeToString(PushTokenIn(token)).toRequestBody(JSON)),
        )

    /** Stop sending to this phone. Called on sign-out, before the session goes. */
    suspend fun dropPushToken(token: String) =
        sendEmpty(
            Request
                .Builder()
                .url(url("/me/push-token?token=" + java.net.URLEncoder.encode(token, "UTF-8")))
                .delete(),
        )

    // ------------------------------------------- standing, apps and the profile

    suspend fun board(): ApiBoard = kept(BOARD)

    suspend fun profile(): ApiProfile = kept(PROFILE)

    /** Change this account's name or face. Nothing else about it is ours to set. */
    suspend fun updateMe(body: MeIn): Me = patch("/me", body)

    /** Every app added to My apps, and where each is with crews and feedback. */
    suspend fun apps(): AppsOut = kept(APPS)

    suspend fun addApp(body: AppIn): AppAdded = post("/apps", body)

    suspend fun goLive(appId: String): ApiApp = post("/apps/$appId/live", Empty())

    suspend fun verifyApp(appId: String): ApiApp = post("/apps/$appId/verify", Empty())

    /** Give an app the icon on this phone, or a picture its owner chose. */
    suspend fun setAppIcon(
        appId: String,
        body: IconIn,
    ): ApiApp = put("/apps/$appId/icon", body)

    suspend fun deleteApp(appId: String) = sendEmpty(Request.Builder().url(url("/apps/$appId")).delete())

    /** Where a piece of proof lives. Needs the token, so it is fetched, never linked. */
    fun proofUrl(ref: String): String = url("/uploads/$ref")

    // ---------------------------------------------------------------- kept on the phone

    // What each screen's main question returned last time, read back from the phone, or null when
    // nothing is kept. A screen shows this while the fresh answer is on its way. See [ApiCache].
    // Plain reads, not suspending: a view model takes them as it is made, so a screen's very first
    // frame is already the kept answer rather than a placeholder for the moment a read takes. The
    // files are a few kilobytes of JSON.
    fun lastPosts(): PostPage? = last(POSTS)

    fun lastActivity(): ApiActivity? = last(ACTIVITY)

    fun lastMyPosts(): List<ApiMyPost>? = last(MY_POSTS)

    fun lastMyReviews(): List<ApiReview>? = last(MY_REVIEWS)

    fun lastWaiting(): List<ApiReview>? = last(WAITING)

    fun lastDisputes(): List<ApiDispute>? = last(DISPUTES)

    fun lastCrews(): CrewList? = last(CREWS)

    fun lastBoard(): ApiBoard? = last(BOARD)

    fun lastProfile(): ApiProfile? = last(PROFILE)

    fun lastApps(): AppsOut? = last(APPS)

    private const val POSTS = "/feed/posts"
    private const val ACTIVITY = "/feed/activity"
    private const val MY_POSTS = "/feed/posts/mine"
    private const val MY_REVIEWS = "/reviews/mine"
    private const val WAITING = "/reviews/waiting"
    private const val DISPUTES = "/disputes"
    private const val CREWS = "/crews"
    private const val BOARD = "/board"
    private const val PROFILE = "/me/profile"
    private const val APPS = "/apps"

    /** A GET whose answer is also kept on the phone, for [last] to hand back next time. */
    @PublishedApi
    internal suspend inline fun <reified T> kept(path: String): T {
        val generation = ApiSession.generation
        val text = sendText(Request.Builder().url(url(path)).get())
        withContext(Dispatchers.IO) { ApiCache.write(path, text, generation) }
        return json.decodeFromString<T>(text)
    }

    /** What [kept] last stored for [path], or null if there is none or it no longer reads. */
    @PublishedApi
    internal inline fun <reified T> last(path: String): T? =
        ApiCache.read(path)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    // ---------------------------------------------------------------- plumbing

    @PublishedApi
    internal suspend inline fun <reified T> get(path: String): T = send(Request.Builder().url(url(path)).get())

    @PublishedApi
    internal suspend inline fun <reified In, reified Out> patch(
        path: String,
        body: In,
    ): Out = send(Request.Builder().url(url(path)).patch(json.encodeToString(body).toRequestBody(JSON)))

    @PublishedApi
    internal suspend inline fun <reified In, reified Out> put(
        path: String,
        body: In,
    ): Out = send(Request.Builder().url(url(path)).put(json.encodeToString(body).toRequestBody(JSON)))

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
    ): T = json.decodeFromString<T>(sendText(builder, authed))

    /** [send], up to the body: the answer's text, for a caller that keeps it as well as reads it. */
    @PublishedApi
    internal suspend fun sendText(
        builder: Request.Builder,
        authed: Boolean = true,
    ): String =
        withContext(Dispatchers.IO) {
            val used = if (authed) ApiSession.access else null
            var attempt = call(builder, used)
            if (attempt.first == 401 && authed && refreshQuietly(used)) {
                attempt = call(builder, ApiSession.access)
            }
            val (status, text) = attempt
            if (status !in 200..299) throw failure(status, text)
            text
        }

    /** A request answered with 204 and no body. Refreshed once on a 401, like [send]. */
    suspend fun sendEmpty(builder: Request.Builder) =
        withContext(Dispatchers.IO) {
            val used = ApiSession.access
            var attempt = call(builder, used)
            if (attempt.first == 401 && refreshQuietly(used)) attempt = call(builder, ApiSession.access)
            val (status, text) = attempt
            if (status !in 200..299) throw failure(status, text)
        }

    @PublishedApi
    internal fun call(
        builder: Request.Builder,
        token: String?,
    ): Pair<Int, String> {
        if (token != null) builder.header("Authorization", "Bearer $token") else builder.removeHeader("Authorization")
        http.newCall(builder.build()).execute().use { response ->
            return response.code to (response.body?.string().orEmpty())
        }
    }

    private val refreshing = Any()

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
    @PublishedApi
    internal fun refreshQuietly(refused: String?): Boolean =
        synchronized(refreshing) {
            val current = ApiSession.access
            if (current != null && current != refused) return true
            refreshNow()
        }

    private fun refreshNow(): Boolean {
        val generation = ApiSession.generation
        val token = ApiSession.refresh ?: return false
        return try {
            val (status, text) =
                call(
                    Request
                        .Builder()
                        .url(url("/auth/refresh"))
                        .post(json.encodeToString(RefreshIn(token)).toRequestBody(JSON)),
                    null,
                )
            if (status !in 200..299) {
                if (status == 401) ApiSession.dropTokens(generation) // Refused, not unreachable: the pair is dead.
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
