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
import com.eazyverse.testtrack.net.ApiMyApp
import com.eazyverse.testtrack.net.ApiProfile
import com.eazyverse.testtrack.net.BoardRow
import kotlinx.coroutines.launch

/*
 * The three read-only tabs.
 *
 * Each does one thing: fetch, hold what came back, and say whether it is still waiting or has
 * given up. None of them computes anything, which is why there is no arithmetic here to test —
 * the figures are the server's and the screens draw them.
 *
 * The loading and offline pair is repeated rather than shared, because a base class that only
 * removes six lines each is a class somebody has to read before they can read the six.
 */

class RanksViewModel : ViewModel() {
    var rows by mutableStateOf<List<BoardRow>>(emptyList())
        private set

    var mine by mutableStateOf<BoardRow?>(null)
        private set

    /** What each kind of work is worth, as the server weighs it. */
    var openPoints by mutableStateOf(1)
        private set

    var reviewPoints by mutableStateOf(5)
        private set

    var loading by mutableStateOf(true)
        private set

    var offline by mutableStateOf(false)
        private set

    fun load(context: Context) {
        viewModelScope.launch {
            try {
                if (!ApiAuth.ensure(context)) {
                    offline = true
                    return@launch
                }
                val answer = Api.board()
                rows = answer.rows
                mine = answer.mine
                openPoints = answer.openPoints
                reviewPoints = answer.reviewPoints
                offline = false
            } catch (e: ApiError) {
                offline = e.isAuth
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
            }
        }
    }
}

class AppsViewModel : ViewModel() {
    var apps by mutableStateOf<List<ApiMyApp>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    var offline by mutableStateOf(false)
        private set

    fun load(context: Context) {
        viewModelScope.launch {
            try {
                if (!ApiAuth.ensure(context)) {
                    offline = true
                    return@launch
                }
                apps = Api.myApps().apps
                offline = false
            } catch (e: ApiError) {
                offline = e.isAuth
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
            }
        }
    }
}

class MeViewModel : ViewModel() {
    var profile by mutableStateOf<ApiProfile?>(null)
        private set

    var loading by mutableStateOf(true)
        private set

    var offline by mutableStateOf(false)
        private set

    fun load(context: Context) {
        viewModelScope.launch {
            try {
                if (!ApiAuth.ensure(context)) {
                    offline = true
                    return@launch
                }
                profile = Api.profile()
                offline = false
            } catch (e: ApiError) {
                offline = e.isAuth
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
            }
        }
    }
}
