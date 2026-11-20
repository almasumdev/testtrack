package com.eazyverse.testtrack.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.AppIconData
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.AppIn
import com.eazyverse.testtrack.net.Listing
import com.eazyverse.testtrack.ui.AppEntry
import com.eazyverse.testtrack.ui.FeedRules
import com.eazyverse.testtrack.ui.MyAppsChanged
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An app on this phone that can be picked, as the list draws it. */
data class PhoneApp(
    val pkg: String,
    val label: String,
) {
    /** A build from Android Studio rather than the one on Play: a different package to the listing. */
    val testBuild: Boolean get() = TEST_SUFFIXES.any { pkg.endsWith(it) }

    private companion object {
        val TEST_SUFFIXES = listOf(".debug", ".dev", ".staging")
    }
}

/**
 * Adding an app to My apps: picked from this phone, or found on Play by its link.
 *
 * Two pages. The start page asks where people try it and takes either way in; the second shows the
 * app found or picked, to add. Back from the second keeps what was typed and the answer given.
 *
 * Nothing is spent and nothing is sent anywhere. The app is only remembered, and its crews and
 * feedback start from its own page. Either way it arrives with an icon and the name it really has:
 * Play's, or the phone's. Typing the details by hand is gone, because that is how apps ended up on
 * everybody's screens as a blank square. Picked from the phone, an app starts unverified and becomes
 * verified once Play, a crew or a review shows it is real, which also covers a closed test Play is
 * not showing yet. Its descriptions are the owner's to write, since Play has none to give yet.
 */
class AppAddViewModel : ViewModel() {
    var isLive by mutableStateOf(false)
        private set

    // ------------------------------------------------------------------ from Play

    var query by mutableStateOf("")
        private set

    var found by mutableStateOf<Listing?>(null)
        private set

    var looking by mutableStateOf(false)
        private set

    var failure by mutableStateOf<AskFailure?>(null)
        private set

    // ------------------------------------------------------------------ from this phone

    var phoneApps by mutableStateOf<List<PhoneApp>?>(null)
        private set

    var search by mutableStateOf("")
        private set

    /** The sheet of this phone's apps is open. */
    var picking by mutableStateOf(false)
        private set

    /** The app picked from this phone. */
    var picked by mutableStateOf<PhoneApp?>(null)
        private set

    var pickedName by mutableStateOf("")
        private set

    /** The two descriptions written for an app picked from the phone. Optional. */
    var shortDesc by mutableStateOf("")
        private set

    var fullDesc by mutableStateOf("")
        private set

    private var pickedIcon by mutableStateOf<AppIconData.Encoded?>(null)

    /** The icon it will go in with, to show before it does. */
    val pickedPreview: ImageBitmap? get() = pickedIcon?.preview

    /** Reading an icon, the phone's or a picture's. */
    var reading by mutableStateOf(false)
        private set

    var iconError by mutableStateOf<String?>(null)
        private set

    // ------------------------------------------------------------------ adding

    var adding by mutableStateOf(false)
        private set

    var addError by mutableStateOf<String?>(null)
        private set

    private var lookupJob: Job? = null

    val canLook: Boolean get() = query.trim().length >= 3 && !looking

    val canAddPicked: Boolean get() = picked != null && pickedIcon != null && pickedName.isNotBlank() && !reading

    /** The phone's apps that match the search, by name or package. */
    val shownPhoneApps: List<PhoneApp>
        get() {
            val all = phoneApps.orEmpty()
            val needle = search.trim()
            if (needle.isEmpty()) return all
            return all.filter { it.label.contains(needle, ignoreCase = true) || it.pkg.contains(needle, ignoreCase = true) }
        }

    /** On the start page: neither found nor picked. */
    val starting: Boolean get() = found == null && picked == null

    /** The sheet of this phone's apps, to pick one or another one. */
    fun openPicker(context: Context) {
        loadPhoneApps(context)
        picking = true
    }

    /** The sheet closed. Picking something has already moved the page on. */
    fun closePicker() {
        picking = false
    }

    /**
     * Back to the start page, keeping what was typed and where it is tried. False on the start
     * page, where Back leaves the screen. Nothing happens while the app is being added.
     */
    fun back(): Boolean {
        if (starting) return false
        if (adding) return true
        iconJob?.cancel()
        reading = false
        picked = null
        pickedIcon = null
        iconError = null
        addError = null
        found = null
        return true
    }

    /**
     * The phone's row on the start page. When Play could not show the app typed, straight to that
     * app if it is installed here; otherwise the sheet of this phone's apps.
     */
    fun pickFromPhone(context: Context) {
        if (failure?.pickInstead == true) pickInstead(context) else openPicker(context)
    }

    /** Changing where it is throws away anything found under the other answer. */
    fun choose(live: Boolean) {
        if (live == isLive) return
        clearLookup()
        isLive = live
        found = null
    }

    // ------------------------------------------------------------------ from Play

    fun type(next: String) {
        clearLookup()
        query = next.take(QUERY_MAX)
    }

    fun look() {
        if (!canLook) return
        failure = null
        looking = true
        lookupJob =
            viewModelScope.launch {
                try {
                    found = Api.lookupApp(query.trim(), closed = !isLive)
                    addError = null
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failure = lookupFailure(error, closed = !isLive)
                } finally {
                    if (isActive) looking = false
                }
            }
    }

    /**
     * Play could not show it: go to the phone instead. Where the package typed is installed here,
     * straight to that app, so nobody has to find it twice.
     */
    fun pickInstead(context: Context) {
        val typed = packageFrom(query)
        clearLookup()
        loadPhoneApps(context) { apps ->
            val installed = apps.firstOrNull { it.pkg == typed }
            if (installed != null) pick(context, installed) else picking = true
        }
    }

    // ------------------------------------------------------------------ from this phone

    fun searchFor(next: String) {
        search = next.take(SEARCH_MAX)
    }

    private fun loadPhoneApps(
        context: Context,
        then: (List<PhoneApp>) -> Unit = {},
    ) {
        val app = context.applicationContext
        if (phoneApps != null) {
            then(phoneApps.orEmpty())
            return
        }
        viewModelScope.launch {
            val apps =
                withContext(Dispatchers.IO) {
                    runCatching { AppIconData.launchable(app).map { PhoneApp(it.pkg, it.label) } }.getOrDefault(emptyList())
                }
            phoneApps = apps
            then(apps)
        }
    }

    fun pick(
        context: Context,
        app: PhoneApp,
    ) {
        picking = false
        if (picked?.pkg != app.pkg) {
            // Another app: what was written was about the one before it.
            shortDesc = ""
            fullDesc = ""
        }
        picked = app
        found = null
        pickedName = app.label.take(FeedRules.APP_NAME_MAX)
        pickedIcon = null
        addError = null
        readIcon { AppIconData.ofInstalled(context.applicationContext, app.pkg) }
    }

    fun renamePicked(next: String) {
        pickedName = next.take(FeedRules.APP_NAME_MAX)
    }

    /** One line, as Play's short description is. */
    fun writeShort(next: String) {
        shortDesc = next.replace('\n', ' ').take(FeedRules.SHORT_DESC_MAX)
    }

    fun writeFull(next: String) {
        fullDesc = next.take(FeedRules.FULL_DESC_MAX)
    }

    fun clearIconError() {
        iconError = null
    }

    fun clearAddError() {
        addError = null
    }

    fun usePicture(
        context: Context,
        uri: Uri,
    ) {
        readIcon { AppIconData.ofPicture(context.applicationContext, uri) }
    }

    /** The read under way. A newer one replaces it, so a slow first read never lands over the last. */
    private var iconJob: Job? = null

    private fun readIcon(read: () -> AppIconData.Encoded?) {
        reading = true
        iconError = null
        iconJob?.cancel()
        iconJob =
            viewModelScope.launch {
                val icon = withContext(Dispatchers.Default) { runCatching(read).getOrNull() }
                if (icon == null) {
                    iconError = "That picture could not be used. Try another one."
                } else {
                    pickedIcon = icon
                }
                reading = false
            }
    }

    // ------------------------------------------------------------------ adding

    /** Add what was found or picked. `onAdded` gets the app, and whether it was on My apps already. */
    fun add(onAdded: (app: AppEntry, existing: Boolean) -> Unit) {
        val app = picked
        val listing = found
        val body =
            when {
                app != null -> {
                    val icon = pickedIcon ?: return
                    if (pickedName.isBlank() || reading) return
                    AppIn(
                        query = app.pkg,
                        live = isLive,
                        name = pickedName.trim(),
                        fromPhone = true,
                        iconData = icon.base64,
                        shortDescription = shortDesc.trim(),
                        fullDescription = fullDesc.trim(),
                    )
                }
                listing != null ->
                    AppIn(
                        query = listing.`package`,
                        live = isLive,
                        shortDescription = listing.shortDescription,
                        fullDescription = listing.fullDescription,
                    )
                else -> return
            }
        if (adding) return
        adding = true
        addError = null
        viewModelScope.launch {
            try {
                val added = Api.addApp(body)
                MyAppsChanged.tell()
                onAdded(added.app.toEntry(), added.existing)
            } catch (e: ApiError) {
                addError = e.message
            } catch (_: Exception) {
                addError = "Couldn't add it. Check your connection."
            } finally {
                adding = false
            }
        }
    }

    private fun clearLookup() {
        lookupJob?.cancel()
        looking = false
        failure = null
    }

    private companion object {
        const val QUERY_MAX = 2048

        /** The phone app search also matches package names, which run longer than a name. */
        const val SEARCH_MAX = 100
    }
}
