package com.eazyverse.testtrack.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiAuth
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.PostIn
import kotlinx.coroutines.launch

/**
 * Circle, against the API.
 *
 * There is no sample data behind this any more. Everything on the screen is a row on the server,
 * which is what makes the tix real: a balance the phone could compute is a balance the phone could
 * be talked into computing differently.
 */
class FeedViewModel : ViewModel() {
    var tab by mutableStateOf(FeedTab.FEEDBACK)
        private set

    var posts by mutableStateOf<List<ApiPost>>(emptyList())
        private set

    var tix by mutableStateOf(0)
        private set

    var loading by mutableStateOf(true)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    /** Set once the API has refused us for good, so the screen can say so rather than spin. */
    var offline by mutableStateOf(false)
        private set

    val events = SampleFeed.events()

    fun show(next: FeedTab) {
        tab = next
    }

    fun clearMessage() {
        message = null
    }

    /** Everything the tab shows, fetched together. Safe to call on every resume. */
    fun load(context: Context) {
        viewModelScope.launch {
            try {
                if (!ApiAuth.ensure(context)) {
                    offline = true
                    loading = false
                    return@launch
                }
                posts = Api.posts().posts
                tix = Api.me().tix
                offline = false
            } catch (e: ApiError) {
                message = e.message
                offline = e.isAuth
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
            }
        }
    }

    /**
     * Put an app up. The price is the server's to decide, so nothing here works it out.
     */
    fun post(
        context: Context,
        appName: String,
        packageName: String,
        ask: String,
        reward: Int,
        isLive: Boolean,
    ) {
        viewModelScope.launch {
            try {
                Api.createPost(PostIn(appName, packageName, ask, reward, isLive))
                message = "Up. Reviewers can take it now."
                load(context)
            } catch (e: ApiError) {
                message = e.message
            } catch (_: Exception) {
                message = "We could not reach TestTrack. Check your connection."
            }
        }
    }
}
