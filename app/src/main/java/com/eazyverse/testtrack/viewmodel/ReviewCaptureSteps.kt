package com.eazyverse.testtrack.viewmodel

import android.content.Context
import android.content.Intent
import com.eazyverse.testtrack.data.ReviewCaptureService
import com.eazyverse.testtrack.data.UsageRepo
import com.eazyverse.testtrack.ui.ReviewViewModel
import com.eazyverse.testtrack.ui.openPlay

/*
 * The review page's steps that hand somebody to the phone: Usage access, and the camera button
 * over Google Play. Beside its view model, so the class itself stays the part that talks to the server.
 */

/** Open the Usage access list so the reviewer can switch TestTrack on. */
fun ReviewViewModel.openUsageSettings(context: Context) = UsageRepo.openSettings(context)

/**
 * Put the camera button up with the consent just given, then open the app's Play page, where
 * the review or the private feedback is left. Proof opens on the way out, so somebody who comes
 * back without pressing it can still pick a screenshot.
 */
fun ReviewViewModel.startCapture(
    context: Context,
    code: Int,
    data: Intent,
    pkg: String,
) {
    if (!requirePlayInstall(context)) return
    ReviewCaptureService.start(context, code, data)
    openPlay(context, pkg)
    markLeft()
}

/** Remove a waiting capture button when returning without a picture. */
fun ReviewViewModel.dropStrayButton(context: Context) {
    if (ReviewCaptureService.running.value == ReviewCaptureService.State.WAITING) {
        ReviewCaptureService.cancel(context)
    }
}
