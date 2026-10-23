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
import com.eazyverse.testtrack.ui.AskFailure
import com.eazyverse.testtrack.ui.FeedRules
import com.eazyverse.testtrack.ui.MyAppsChanged
import com.eazyverse.testtrack.ui.lookupFailure
import com.eazyverse.testtrack.ui.packageFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The two ways an app gets onto My apps, both of which bring its real icon and name. */
enum class AddWay { PHONE, PLAY }

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
 * Nothing is spent and nothing is sent anywhere. The app is only remembered, and its crews and
 * feedback start from its own page. Either way it arrives with an icon and the name it really has:
 * Play's, or the phone's. Typing the details by hand is gone, because that is how apps ended up on
 * everybody's screens as a blank square. Picked from the phone, an app starts unverified and becomes
 * verified once Play, a crew or a review shows it is real, which also covers a closed test Play is
 * not showing yet.
 */
class AppAddViewModel : ViewModel() {
    /** Null on the first page, where the way is chosen. */
    var way by mutableStateOf<AddWay?>(null)
        private set

    /** The way selected on the first page, before Continue. */
    var choice by mutableStateOf(AddWay.PHONE)
        private set

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

    /** The icon it goes in with is a picture chosen for it, not the one on this phone. */
    var fromPicture by mutableStateOf(false)
        private set

    var pickedName by mutableStateOf("")
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

    fun select(next: AddWay) {
        choice = next
    }

    /** Continue from the first page: to the link, or the sheet of this phone's apps over it. */
    fun proceed(context: Context) {
        if (choice == AddWay.PHONE) {
            openPicker(context)
        } else {
            way = AddWay.PLAY
        }
    }

    /** The sheet of this phone's apps, to pick one or another one. */
    fun openPicker(context: Context) {
        loadPhoneApps(context)
        picking = true
    }

    /** The sheet closed. Picking something has already moved the page on. */
    fun closePicker() {
        picking = false
    }

    /** A step back inside the flow. False on the first page, where Back leaves the screen. */
    fun back(): Boolean {
        when {
            picked != null -> {
                picked = null
                pickedIcon = null
                fromPicture = false
                iconError = null
                addError = null
                way = null
            }
            found != null -> startOver()
            way != null -> {
                clearLookup()
                way = null
            }
            else -> return false
        }
        return true
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

    fun startOver() {
        clearLookup()
        found = null
    }

    fun look() {
        if (!canLook) return
        failure = null
        looking = true
        lookupJob =
            viewModelScope.launch {
                try {
                    found = Api.lookupApp(query.trim(), closed = !isLive)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failure = lookupFailure(error, closed = !isLive, fromPhone = true)
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
        choice = AddWay.PHONE
        loadPhoneApps(context) { apps ->
            val installed = apps.firstOrNull { it.pkg == typed }
            if (installed != null) pick(context, installed) else picking = true
        }
    }

    // ------------------------------------------------------------------ from this phone

    fun searchFor(next: String) {
        search = next.take(FeedRules.APP_NAME_MAX)
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
        way = AddWay.PHONE
        picking = false
        picked = app
        pickedName = app.label.take(FeedRules.APP_NAME_MAX)
        pickedIcon = null
        fromPicture = false
        addError = null
        readIcon(picture = false) { AppIconData.ofInstalled(context.applicationContext, app.pkg) }
    }

    fun renamePicked(next: String) {
        pickedName = next.take(FeedRules.APP_NAME_MAX)
    }

    /** Back to the icon the app has on this phone, after a picture was tried. */
    fun usePhoneIcon(context: Context) {
        val app = picked ?: return
        readIcon(picture = false) { AppIconData.ofInstalled(context.applicationContext, app.pkg) }
    }

    fun usePicture(
        context: Context,
        uri: Uri,
    ) {
        readIcon(picture = true) { AppIconData.ofPicture(context.applicationContext, uri) }
    }

    private fun readIcon(
        picture: Boolean,
        read: () -> AppIconData.Encoded?,
    ) {
        reading = true
        iconError = null
        viewModelScope.launch {
            val icon = withContext(Dispatchers.Default) { runCatching(read).getOrNull() }
            if (icon == null) {
                iconError = "That picture could not be used. Try another one."
            } else {
                pickedIcon = icon
                fromPicture = picture
            }
            reading = false
        }
    }

    // ------------------------------------------------------------------ adding

    /** Add what was found or picked. `onAdded` gets the app's id, and whether it was on My apps already. */
    fun add(onAdded: (id: String, existing: Boolean) -> Unit) {
        val body =
            when (way) {
                AddWay.PHONE -> {
                    val app = picked ?: return
                    val icon = pickedIcon ?: return
                    if (pickedName.isBlank()) return
                    AppIn(query = app.pkg, live = isLive, name = pickedName.trim(), fromPhone = true, iconData = icon.base64)
                }
                AddWay.PLAY -> AppIn(query = found?.`package` ?: return, live = isLive)
                null -> return
            }
        if (adding) return
        adding = true
        addError = null
        viewModelScope.launch {
            try {
                val added = Api.addApp(body)
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
    }

    private companion object {
        const val QUERY_MAX = 2048
    }
}
