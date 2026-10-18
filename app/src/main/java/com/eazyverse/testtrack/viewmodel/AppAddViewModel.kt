package com.eazyverse.testtrack.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.AppIn
import com.eazyverse.testtrack.net.Listing
import com.eazyverse.testtrack.ui.AskFailure
import com.eazyverse.testtrack.ui.FeedRules
import com.eazyverse.testtrack.ui.HandEntry
import com.eazyverse.testtrack.ui.MyAppsChanged
import com.eazyverse.testtrack.ui.lookupFailure
import com.eazyverse.testtrack.ui.packageFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Adding an app to My apps: where people find it, its link, Play's answer to confirm, then add.
 *
 * Nothing is spent and nothing is sent anywhere. The app is only remembered, and its crews and
 * feedback start from its own page. When Play cannot be read for us, or a closed track is not
 * showing yet, the details can be typed instead; the app is then added unverified, and becomes
 * verified once Play, a crew or a review shows it is real.
 */
class AppAddViewModel :
    ViewModel(),
    HandEntry {
    var query by mutableStateOf("")
        private set

    var isLive by mutableStateOf(false)
        private set

    var found by mutableStateOf<Listing?>(null)
        private set

    /** What was found was typed rather than read off Play. */
    var unchecked by mutableStateOf(false)
        private set

    var looking by mutableStateOf(false)
        private set

    var failure by mutableStateOf<AskFailure?>(null)
        private set

    var byHand by mutableStateOf(false)
        private set

    override var handName by mutableStateOf("")
        private set

    var adding by mutableStateOf(false)
        private set

    var addError by mutableStateOf<String?>(null)
        private set

    private var lookupJob: Job? = null

    val canLook: Boolean get() = query.trim().length >= 3 && !looking

    override val typedPackage: String? get() = packageFrom(query)

    override val canUseByHand: Boolean get() = typedPackage != null && handName.isNotBlank()

    fun type(next: String) {
        clearLookup()
        query = next.take(QUERY_MAX)
    }

    /** Changing where it is throws away anything found under the other answer. */
    fun choose(live: Boolean) {
        if (live == isLive) return
        clearLookup()
        isLive = live
        found = null
        unchecked = false
    }

    fun startOver() {
        clearLookup()
        found = null
        unchecked = false
    }

    fun enterByHand() {
        failure = null
        byHand = true
    }

    override fun typeName(next: String) {
        handName = next.take(FeedRules.APP_NAME_MAX)
    }

    override fun useByHand() {
        val pkg = typedPackage ?: return
        if (handName.isBlank()) return
        found = Listing(`package` = pkg, name = handName.trim())
        unchecked = true
        byHand = false
    }

    fun look() {
        if (!canLook) return
        failure = null
        looking = true
        lookupJob =
            viewModelScope.launch {
                try {
                    found = Api.lookupApp(query.trim(), closed = !isLive)
                    unchecked = false
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failure = lookupFailure(error, closed = !isLive)
                } finally {
                    if (isActive) looking = false
                }
            }
    }

    /** Add what was found. `onAdded` gets the app's id, and whether it was on My apps already. */
    fun add(onAdded: (id: String, existing: Boolean) -> Unit) {
        val app = found ?: return
        if (adding) return
        adding = true
        addError = null
        viewModelScope.launch {
            try {
                val added =
                    Api.addApp(
                        AppIn(
                            query = app.`package`,
                            live = isLive,
                            byHand = unchecked,
                            name = if (unchecked) app.name else "",
                        ),
                    )
                MyAppsChanged.tell()
                onAdded(added.app.id, added.existing)
            } catch (e: ApiError) {
                addError = e.message
            } catch (_: Exception) {
                addError = "We could not reach TestTrack. Check your connection."
            } finally {
                adding = false
            }
        }
    }

    private fun clearLookup() {
        lookupJob?.cancel()
        looking = false
        failure = null
        byHand = false
    }

    private companion object {
        const val QUERY_MAX = 2048
    }
}
