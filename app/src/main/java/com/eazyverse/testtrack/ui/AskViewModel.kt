package com.eazyverse.testtrack.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.Listing
import com.eazyverse.testtrack.net.PostIn
import kotlinx.coroutines.launch

/**
 * Two steps: find the app, then say what you want looked at.
 *
 * The lookup goes through our server, which drives a browser signed in as an account that is on
 * the tester group. That is the only way to see a closed testing listing at all, and it is why
 * the first step can fail for a reason worth explaining rather than just "not found".
 */
class AskViewModel : ViewModel() {
    var query by mutableStateOf("")
        private set

    var found by mutableStateOf<Listing?>(null)
        private set

    var looking by mutableStateOf(false)
        private set

    var posting by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)

    var ask by mutableStateOf("")
    var offer by mutableStateOf(FeedRules.MIN_REWARD.toString())

    /**
     * Read once when the page opens, so the cost line can say what you have.
     *
     * Not refreshed after posting: this page is leaving at that point, and the tab underneath
     * reloads on its own.
     */
    var tix by mutableStateOf(0)
        private set

    init {
        viewModelScope.launch { runCatching { tix = Api.me().tix } }
    }

    private val reward: Int get() = offer.toIntOrNull() ?: 0
    val cost: Int get() = FeedRules.postCost(reward)

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
    val canPost: Boolean get() = found != null && ask.isNotBlank() && affordable && !posting

    fun type(next: String) {
        query = next.take(QUERY_MAX)
        message = null
    }

    /** Back to the first step, keeping what was typed so a wrong paste can be corrected. */
    fun startOver() {
        found = null
        message = null
    }

    fun look() {
        if (!canLook) return
        looking = true
        message = null
        viewModelScope.launch {
            runCatching { Api.lookupApp(query.trim()) }
                .onSuccess { found = it }
                .onFailure { message = whyNot(it) }
            looking = false
        }
    }

    fun post(onDone: () -> Unit) {
        val app = found
        if (app == null || !canPost) return
        posting = true
        message = null
        viewModelScope.launch {
            runCatching {
                Api.createPost(
                    PostIn(
                        appName = app.name,
                        packageName = app.`package`,
                        ask = ask.trim(),
                        reward = reward,
                        // Read off Play rather than asked. Somebody who has just pasted their own
                        // closed track link should not then be asked which track it is on.
                        isLive = false,
                    ),
                )
            }.onSuccess { onDone() }
                .onFailure { message = whyNot(it) }
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
                "Play won't show us that app. Check the closed testing track has the testers " +
                    "group added, then try again."
            error.code == "package_unreadable" ->
                "That doesn't look like a Play link or a package name. Paste the link from Play " +
                    "Console, or the package name like com.example.myapp."
            error.code == "listing_unavailable" ->
                "We couldn't look that up just now. Try again in a moment."
            else -> error.message
        }

    private companion object {
        /** A Play URL with every parameter Console attaches is nowhere near this long. */
        const val QUERY_MAX = 2048
    }
}
