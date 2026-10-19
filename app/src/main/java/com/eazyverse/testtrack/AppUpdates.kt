package com.eazyverse.testtrack

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.eazyverse.testtrack.data.AppUpdateService
import com.eazyverse.testtrack.data.UpdateTier
import com.eazyverse.testtrack.ui.UpdateOffer

/**
 * The header's update button, when Play has an optional update for this install.
 *
 * Tapping it starts the background download; once that is on the phone and was started from here,
 * the button becomes Restart. A download left from an earlier session stays behind the same button
 * rather than greeting anybody at launch. A forced update is the full screen instead, so it has no
 * button. Here rather than in a screen because only the shell may reach [AppUpdateService].
 */
@Composable
internal fun updateOffer(
    launcher: ActivityResultLauncher<IntentSenderRequest>,
    openStore: () -> Unit,
): UpdateOffer? {
    val tier by AppUpdateService.tier.collectAsState()
    val downloaded by AppUpdateService.isDownloaded.collectAsState()
    val started by AppUpdateService.userStartedUpdate.collectAsState()
    return when {
        tier == UpdateTier.BLOCKED -> null
        downloaded && started -> UpdateOffer(UpdateOffer.State.READY) { AppUpdateService.completeUpdate() }
        tier == UpdateTier.NUDGE || downloaded ->
            UpdateOffer(UpdateOffer.State.AVAILABLE) {
                if (!AppUpdateService.startUserUpdate(launcher)) openStore()
            }
        else -> null
    }
}
