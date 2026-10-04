package com.eazyverse.testtrack.viewmodel

import android.app.Activity
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.AuthRepo
import com.eazyverse.testtrack.data.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Drive belongs only to testing flows that upload screenshot proof. */
class TestingDriveViewModel : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** Whether proof can go to Drive. Null until Google has been asked. */
    var connected by mutableStateOf<Boolean?>(null)
        private set

    /** Ask Google without showing anything. The grant lives on the account, not on this phone. */
    fun check(activity: Activity) {
        viewModelScope.launch {
            val granted = AuthRepo.hasDriveAccess(activity)
            Session.updateDriveConnected(granted)
            connected = granted
        }
    }

    fun connect(
        activity: Activity,
        launch: (IntentSenderRequest) -> Unit,
        onConnected: () -> Unit,
    ) {
        if (busy) return
        busy = true
        message = null
        viewModelScope.launch {
            try {
                val result = withTimeoutOrNull(AuthRepo.AUTH_TIMEOUT_MS) { AuthRepo.authorizeDrive(activity) }
                val pending = result?.pendingIntent
                when {
                    result == null -> message = "Google didn't respond. Please try again."
                    result.hasResolution() && pending != null -> launch(IntentSenderRequest.Builder(pending.intentSender).build())
                    else -> finish(result.accessToken != null, onConnected)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                message = "Couldn't connect Drive. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun clearMessage() {
        message = null
    }

    fun consentResult(
        activity: Activity,
        data: Intent?,
        onConnected: () -> Unit,
    ) {
        finish(AuthRepo.tokenFromConsent(activity, data) != null, onConnected)
    }

    private fun finish(
        granted: Boolean,
        onConnected: () -> Unit,
    ) {
        Session.updateDriveConnected(granted)
        connected = granted
        if (granted) onConnected() else message = "Drive access wasn't granted. Your pending proof is kept so you can try again."
    }
}
