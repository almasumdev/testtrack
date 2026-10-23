package com.eazyverse.testtrack.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiSession
import com.eazyverse.testtrack.net.Listing
import com.eazyverse.testtrack.net.PostIn
import com.eazyverse.testtrack.viewmodel.AskFailure
import com.eazyverse.testtrack.viewmodel.LiveReload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Asking for feedback on one of your apps: what to look at, how many reviews and what each pays.
 *
 * Always for an app on My apps, picked from Circle or opened from the app's own page, so its name,
 * icon and track are known before this opens. Somebody signed out is asked to sign in before they
 * get here, which is what keeps every request tied to an app that has its icon.
 */
class AskViewModel : ViewModel() {
    var found by mutableStateOf<Listing?>(null)
        private set

    /** Live on Play, rather than in closed testing: from the app, never asked again here. */
    var isLive by mutableStateOf(true)
        private set

    var posting by mutableStateOf(false)
        private set

    var failure by mutableStateOf<AskFailure?>(null)
        private set

    /** The app is not verified yet. Said on the page so nobody forgets. */
    var unchecked by mutableStateOf(false)
        private set

    /** The session ran out while posting: sign in again, then post again. */
    var needsSignIn by mutableStateOf(false)
        private set
    var authPromptId by mutableStateOf(0)
        private set

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

    private fun requestSignIn() {
        authPromptId++
        needsSignIn = true
    }

    fun dismissSignIn() {
        needsSignIn = false
    }

    fun signedIn() {
        needsSignIn = false
        if (!ApiSession.signedIn) {
            failure = AskFailure("Sign-in couldn't finish", "Please try again. Your request is still here.")
            return
        }
        refreshBalance()
    }

    val cost: Int get() = FeedRules.postCost(reward, reviewCount)

    private val inRange: Boolean get() = reward in FeedRules.MIN_REWARD..FeedRules.MAX_REWARD
    private val affordable: Boolean get() = inRange && cost <= tix

    val canPost: Boolean get() = found != null && affordable && !posting

    /** The app on My apps this request is for. */
    var appId by mutableStateOf<String?>(null)
        private set

    /** The app on My apps this is for: its track known, nothing to look up. */
    fun useApp(app: AppEntry) {
        if (appId == app.id) return
        appId = app.id
        isLive = app.live
        found = Listing(`package` = app.pkg, name = app.name, icon = app.icon, developer = app.developer)
        unchecked = !app.verified
        failure = null
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
                appId = appId,
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
                        "Custom review counts aren't available yet. Choose ${FeedRules.REVIEWS_BOUGHT} reviews for now."
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
                MyAppsChanged.tell()
                onDone()
            }.onFailure {
                if (it is CancellationException) throw it
                if (it is ApiError && it.isAuth) {
                    requestSignIn()
                } else {
                    failure =
                        AskFailure(
                            "Couldn't send your request",
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
     * What to say about a failure. The app is on My apps already, so nothing here is looked up on
     * Play; the server's own sentence is shown as it is.
     */
    private fun whyNot(error: Throwable): String =
        if (error is ApiError) error.message else "Couldn't reach TestTrack. Check your connection and try again."
}
