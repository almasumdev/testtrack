package com.eazyverse.testtrack.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiSession
import com.eazyverse.testtrack.net.Listing
import com.eazyverse.testtrack.net.PostIn
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

    /** Why the last try did not post, said under the bar; cleared when it closes or on the next try. */
    var pageMessage by mutableStateOf<String?>(null)
        private set

    fun clearMessage() {
        pageMessage = null
    }

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

    /** What each reviewer gives. Written or detailed raises the least a review may pay. */
    var feedback by mutableStateOf(FeedRules.Feedback.STARS)
        private set

    /** A detailed request's ready-written reviews, as typed; blank ones are not sent. */
    val readyTexts = mutableStateListOf<String>()

    fun changeReward(value: Int) {
        reward = value.coerceIn(feedback.least, FeedRules.MAX_REWARD)
    }

    fun changeReviewCount(value: Int) {
        reviewCount = value.coerceIn(FeedRules.MIN_REVIEWS, FeedRules.MAX_REVIEWS)
        // Fewer reviews than ready ones: the empty ones go; written ones stay for the owner to choose.
        while (readyTexts.size > reviewCount && readyTexts.last().isBlank()) readyTexts.removeAt(readyTexts.lastIndex)
    }

    /** A level lifts the pay to its least; leaving detailed takes the ready reviews with it. */
    fun chooseFeedback(level: FeedRules.Feedback) {
        feedback = level
        if (reward < level.least) reward = level.least
        if (level != FeedRules.Feedback.DETAILED) readyTexts.clear()
    }

    fun addReady() {
        if (readyTexts.size < reviewCount) readyTexts.add("")
    }

    fun editReady(
        index: Int,
        text: String,
    ) {
        if (index in readyTexts.indices) readyTexts[index] = text
    }

    fun removeReady(index: Int) {
        if (index in readyTexts.indices) readyTexts.removeAt(index)
    }

    private val ready: List<String> get() = readyTexts.map { it.trim() }.filter { it.isNotEmpty() }

    /** What is wrong with each ready review, by its place in [readyTexts]; null where nothing is. */
    val readyErrors: List<String?> get() = readyErrors(readyTexts)

    /** Each filled one long enough, all different, and no more of them than reviews asked for. */
    private val readyOk: Boolean get() = ready.size <= reviewCount && readyErrors.all { it == null }

    /**
     * Read when the page opens and again whenever the balance changes, so the cost line can say
     * what you have. Null until the first read has answered.
     */
    var tix by mutableStateOf<Int?>(null)
        private set

    /** What stops the request going up, for the hint over the button; null while the balance is unknown. */
    val blockReason: AskBlock? get() = askBlock(tix, cost, readyOk)

    /** How many tix the request is short by, for the hint. */
    val shortBy: Int get() = tix?.let { (cost - it).coerceAtLeast(0) } ?: 0

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
            pageMessage = "Sign-in couldn't finish. Please try again. Your request is still here."
            return
        }
        refreshBalance()
    }

    val cost: Int get() = FeedRules.postCost(reward, reviewCount)

    private val inRange: Boolean get() = reward in FeedRules.MIN_REWARD..FeedRules.MAX_REWARD

    val canPost: Boolean
        get() = found != null && tix != null && blockReason == null && inRange && reward >= feedback.least && !posting

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
        pageMessage = null
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
                feedback = feedback.wire,
                readyTexts = if (feedback == FeedRules.Feedback.DETAILED) ready else emptyList(),
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
        pageMessage = null
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
                // An older server would take the request as stars and say nothing.
                check(request.feedback == FeedRules.Feedback.STARS.wire || (options?.version ?: 0) >= FeedRules.FEEDBACK_OPTIONS) {
                    "Written and detailed feedback aren't available yet. Untick them to ask for star ratings."
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
                    val why = if (it is IllegalStateException) it.message.orEmpty() else whyNot(it)
                    pageMessage = "Couldn't send your request. $why"
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

/** Why the button waits, for the hint over it. */
enum class AskBlock {
    /** A ready review is too short or the same as another, or there are more than reviews. */
    READY,

    /** The request costs more than the balance. */
    SHORT,
}

/**
 * What stops a request going up, the first one on the page first: the ready reviews sit above the
 * price. Nothing while the balance is still unknown, when the button simply waits.
 */
internal fun askBlock(
    tix: Int?,
    cost: Int,
    readyOk: Boolean,
): AskBlock? =
    when {
        tix == null -> null
        !readyOk -> AskBlock.READY
        cost > tix -> AskBlock.SHORT
        else -> null
    }

/**
 * Each ready review's problem, in place: blank is fine (it is not sent), short says how far it got,
 * and one the same as an earlier one (ignoring case and the ends) names that one.
 */
internal fun readyErrors(texts: List<String>): List<String?> {
    val least = FeedRules.Feedback.DETAILED.chars
    val seen = mutableMapOf<String, Int>()
    return texts.mapIndexed { i, text ->
        val key = text.trim().lowercase()
        if (key.isEmpty()) return@mapIndexed null
        val first = seen.getOrPut(key) { i }
        val length = plainLength(text)
        when {
            length < least -> "$length of at least $least characters"
            first != i -> "Same as ready review ${first + 1}"
            else -> null
        }
    }
}
