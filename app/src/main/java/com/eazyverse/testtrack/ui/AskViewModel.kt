package com.eazyverse.testtrack.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.BuildConfig
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiSession
import com.eazyverse.testtrack.net.Listing
import com.eazyverse.testtrack.net.PostIn
import com.eazyverse.testtrack.viewmodel.LiveReload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Two steps: find the app, then say what you want looked at.
 *
 * The lookup goes through our server, which drives a browser signed in as an account that is on
 * the tester group. That is the only way to see a closed testing listing at all, and it is why
 * the first step can fail for a reason worth explaining rather than just "not found".
 */
class AskViewModel : ViewModel() {
    var query by mutableStateOf(if (BuildConfig.DEBUG) "com.eazyverse." else "")
        private set

    var found by mutableStateOf<Listing?>(null)
        private set

    /**
     * Where the app is, asked before anything else.
     *
     * Not guessed at, because the two are looked up differently and fail differently. A live app
     * has a public page the server reads in under a second. A closed one is invisible to anybody
     * who is not a tester, so it needs the signed-in browser, and "we cannot see it" then almost
     * always means the testers group is not on the track. Telling somebody the wrong one of those
     * sends them to fix the wrong thing.
     */
    var isLive by mutableStateOf(true)
        private set

    var looking by mutableStateOf(false)
        private set

    var posting by mutableStateOf(false)
        private set

    var failure by mutableStateOf<AskFailure?>(null)
        private set
    var needsSignIn by mutableStateOf(false)
        private set
    var authPromptId by mutableStateOf(0)
        private set
    private var resumeLookup = false
    private var lookupJob: Job? = null

    var ask by mutableStateOf("")
    var reward by mutableStateOf(FeedRules.MIN_REWARD)
        private set
    var reviewCount by mutableStateOf(FeedRules.REVIEWS_BOUGHT)
        private set

    fun changeReward(value: Int) {
        reward = value.coerceIn(FeedRules.MIN_REWARD, FeedRules.MAX_REWARD)
    }

    fun changeReviewCount(value: Int) {
        reviewCount = value.coerceIn(FeedRules.MIN_REVIEWS, FeedRules.MAX_REVIEWS)
    }

    /**
     * Read once when the page opens, so the cost line can say what you have.
     *
     * Not refreshed after posting: this page is leaving at that point, and the tab underneath
     * reloads on its own.
     */
    var tix by mutableStateOf(0)
        private set

    init {
        refreshBalance()
    }

    // The balance is the server's: a payout or a refund while this page is open shows at once.
    private val live =
        LiveReload("ask", viewModelScope) { it.channel.startsWith("user:") && it.type == "tix.changed" }

    fun startLive() {
        if (ApiSession.signedIn) live.start { refreshBalance() }
    }

    fun stopLive() = live.stop()

    private fun refreshBalance() {
        if (ApiSession.signedIn) viewModelScope.launch { runCatching { tix = Api.me().tix } }
    }

    private fun requestSignIn(lookup: Boolean) {
        resumeLookup = lookup
        authPromptId++
        needsSignIn = true
    }

    fun dismissSignIn() {
        needsSignIn = false
        resumeLookup = false
    }

    fun signedIn() {
        needsSignIn = false
        if (!ApiSession.signedIn) {
            resumeLookup = false
            failure = AskFailure("Sign-in couldn't finish", "Please try again. Your app link has been kept.")
            return
        }
        refreshBalance()
        val shouldLook = resumeLookup
        resumeLookup = false
        if (shouldLook) look()
    }

    private fun clearLookup() {
        lookupJob?.cancel()
        looking = false
        failure = null
    }

    val cost: Int get() = FeedRules.postCost(reward, reviewCount)

    private val inRange: Boolean get() = reward in FeedRules.MIN_REWARD..FeedRules.MAX_REWARD
    private val affordable: Boolean get() = inRange && cost <= tix

    val rewardError: String?
        get() =
            when {
                !inRange -> "Between ${FeedRules.MIN_REWARD} and ${FeedRules.MAX_REWARD} tix."
                !affordable -> "That would cost $cost tix and you have $tix."
                else -> null
            }

    val canLook: Boolean get() = query.trim().length >= 3 && !looking
    val canPost: Boolean get() = found != null && affordable && !posting

    fun type(next: String) {
        clearLookup()
        query = next.take(QUERY_MAX)
    }

    /** Changing where it is throws away anything found under the other answer. */
    fun choose(next: Boolean) {
        if (next == isLive) return
        clearLookup()
        isLive = next
        found = null
    }

    /** Back to the first step, keeping what was typed so a wrong paste can be corrected. */
    fun startOver() {
        clearLookup()
        found = null
    }

    fun look() {
        if (!canLook) return
        failure = null
        if (!ApiSession.signedIn) {
            requestSignIn(lookup = true)
            return
        }
        looking = true
        lookupJob =
            viewModelScope.launch {
                try {
                    found = Api.lookupApp(query.trim(), closed = !isLive)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (error is ApiError && error.isAuth) {
                        requestSignIn(lookup = true)
                    } else {
                        failure = lookupFailure(error)
                    }
                } finally {
                    if (isActive) looking = false
                }
            }
    }

    private var pendingRequest: PostIn? = null

    fun post(onDone: () -> Unit) {
        val app = found
        if (app == null || !canPost) return
        val request =
            PostIn(
                appName = app.name,
                packageName = app.`package`,
                ask = ask.trim(),
                reward = reward,
                reviewsBought = reviewCount,
                isLive = isLive,
            )
        val operation =
            pendingRequest?.takeIf { it.copy(requestId = null) == request }
                ?: request
                    .copy(
                        requestId =
                            java.util.UUID
                                .randomUUID()
                                .toString(),
                    ).also { pendingRequest = it }
        posting = true
        failure = null
        viewModelScope.launch {
            runCatching {
                // Older servers silently ignore unknown fields; never charge an unintended review count.
                val options =
                    try {
                        Api.feedbackOptions()
                    } catch (error: ApiError) {
                        if (error.status != 404) throw error
                        null
                    }
                if (options == null) {
                    check(request.reviewsBought == FeedRules.REVIEWS_BOUGHT) {
                        "Custom review counts aren't available yet. Choose 3 reviews to post now."
                    }
                    // The previous API requires a question, even for general feedback.
                    Api.createPost(operation.copy(ask = request.ask.ifBlank { "General feedback on the app experience." }))
                } else {
                    check(options.version >= 2 && request.reviewsBought in options.minReviews..options.maxReviews) {
                        "This review count isn't supported yet. Choose between ${options.minReviews} and ${options.maxReviews} reviews."
                    }
                    Api.createPost(operation)
                }
            }.onSuccess {
                pendingRequest = null
                onDone()
            }.onFailure {
                if (it is CancellationException) throw it
                if (it is ApiError && it.isAuth) {
                    requestSignIn(lookup = false)
                } else {
                    failure =
                        AskFailure(
                            "Couldn't post your request",
                            if (it is IllegalStateException) {
                                it.message.orEmpty()
                            } else {
                                whyNot(it)
                            },
                        )
                }
            }
            posting = false
        }
    }

    /**
     * What to say about a failure, in the reader's terms rather than the server's.
     *
     * The one worth spelling out is a listing we cannot see. By far its most common real cause is
     * an owner who set up a closed test and did not add the testers group to it, and telling them
     * now beats fifteen people failing to install it later and nobody knowing why.
     */
    private fun whyNot(error: Throwable): String =
        when {
            error !is ApiError -> "Couldn't reach the server. Check your connection and try again."
            error.code == "listing_not_visible" ->
                "Play won't show us that app. The usual cause is a closed testing track without " +
                    "the testers group on it. Add the group, then try again."
            error.code == "listing_not_found" ->
                "Play has no public app with that package name. Check it, or pick Closed " +
                    "testing if it is not public yet."
            error.code == "package_unreadable" ->
                "That doesn't look like a Play link or a package name. Paste the link from Play " +
                    "Console, or the package name like com.example.myapp."
            // Not reworded. The server knows why it could not look, and the reasons differ:
            // Play refusing us, Play unreachable, the closed testing lookup not being set up at
            // all. Replacing all three with one cheerful line was how a real outage spent an
            // evening looking like a network blip.
            else -> error.message
        }

    private companion object {
        /** A Play URL with every parameter Console attaches is nowhere near this long. */
        const val QUERY_MAX = 2048
    }
}

/** Display information only; colours and actions stay in the screen. */
data class AskFailure(
    val title: String,
    val detail: String,
    val invalidLink: Boolean = false,
)

internal fun lookupFailure(error: Throwable): AskFailure =
    when {
        error !is ApiError -> AskFailure("Couldn't connect", "Check your internet connection, then try again. Your app link is still here.")
        error.code == "package_unreadable" ->
            AskFailure(
                "Check the app link",
                "Enter a full Play link or package name, such as com.eazyverse.timelapse.",
                invalidLink = true,
            )
        error.code == "listing_not_found" ->
            AskFailure(
                "We couldn't find this app",
                "Check the package name. If your app isn't public yet, choose Closed testing.",
                invalidLink = true,
            )
        error.code == "listing_not_visible" ->
            AskFailure(
                "We can't access this listing",
                "Check that the testers group is added to your closed track and the release is available.",
            )
        error.code == "listing_unavailable" -> AskFailure("App lookup is unavailable", error.message)
        else -> AskFailure("Couldn't find your app", error.message)
    }
