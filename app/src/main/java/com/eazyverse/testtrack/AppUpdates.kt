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
 * The optional update the shell offers, following mkit's docs/in-app-update.md: nothing on launch
 * and no dialog of its own; an icon that downloads when tapped, a spinner while it does, then
 * Restart. A finished download shows as Restart whatever session it came from. A forced update is
 * the full screen instead, so it gets no offer. Here because only the shell may reach
 * [AppUpdateService].
 */
@Composable
internal fun updateOffer(
    launcher: ActivityResultLauncher<IntentSenderRequest>,
    openStore: () -> Unit,
): UpdateOffer? {
    val tier by AppUpdateService.tier.collectAsState()
    val downloaded by AppUpdateService.isDownloaded.collectAsState()
    val started by AppUpdateService.userStartedUpdate.collectAsState()
    val progress by AppUpdateService.downloadProgress.collectAsState()
    val version = AppUpdateService.waitingVersionName
    return when {
        tier == UpdateTier.BLOCKED -> null
        downloaded ->
            UpdateOffer(UpdateOffer.State.READY, version, autoAsk = started) { AppUpdateService.completeUpdate() }
        progress != null -> UpdateOffer(UpdateOffer.State.DOWNLOADING, version, progress ?: -1f)
        tier == UpdateTier.NUDGE ->
            UpdateOffer(UpdateOffer.State.AVAILABLE, version) {
                if (!AppUpdateService.startUserUpdate(launcher)) openStore()
            }
        else -> null
    }
}
