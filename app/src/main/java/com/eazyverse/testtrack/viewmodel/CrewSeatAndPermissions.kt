package com.eazyverse.testtrack.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.Device
import com.eazyverse.testtrack.data.UsageRepo
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.SeatIn
import com.eazyverse.testtrack.ui.CrewPhase
import com.eazyverse.testtrack.ui.CrewViewModel
import com.eazyverse.testtrack.ui.MyAppsChanged
import com.eazyverse.testtrack.ui.SnackTone
import kotlinx.coroutines.launch
import java.util.TimeZone

/*
 * Taking a seat, and the two switches a round needs from Android. Kept beside CrewViewModel rather
 * than in it, which is long enough already.
 */

/**
 * Take a seat with an app of your own.
 *
 * The crew is not named. The server checks the app on Play for the testers group and puts it
 * into whichever crew is nearest to starting; a client that picks its own crew is a client
 * picking which fourteen people test its app. [onRefused] gets a refusal's code and details.
 */
fun CrewViewModel.takeSeat(
    context: Context,
    packageName: String,
    appName: String,
    notes: String,
    appId: String? = null,
    onRefused: (ApiError) -> Unit = {},
    onDone: () -> Unit,
) {
    val fingerprint = Device.fingerprint(context)
    if (fingerprint == null) {
        seatError = "This phone will not identify itself, so it cannot hold a seat."
        return
    }
    if (seating) return
    seating = true
    seatError = null
    viewModelScope.launch {
        try {
            val seat =
                Api.takeSeat(
                    SeatIn(
                        appName = appName,
                        packageName = packageName,
                        notes = notes,
                        deviceFp = fingerprint,
                        timezone = TimeZone.getDefault().id,
                        appId = appId,
                        confirmBoxes = true,
                    ),
                )
            if (seat.state == "confirmed") {
                say("${seat.appName} is in. Play shows it to the testers group.", SnackTone.DONE)
            } else {
                say("${seat.appName} is waiting. We couldn't reach Play just now and will check again shortly.")
            }
            showTab(CrewPhase.LOBBY)
            onDone()
            load(context)
            MyAppsChanged.tell()
        } catch (e: ApiError) {
            seatError = e.message.also { onRefused(e) }
        } catch (_: Exception) {
            seatError = "We could not reach TestTrack. Check your connection."
        } finally {
            seating = false
        }
    }
}

/** Both switches a round needs: usage access for the proof, drawing over apps for the timer. */
fun CrewViewModel.permissionsReady(context: Context) = UsageRepo.hasAccess(context) && NightRound.canFloat(context)

/** The guide's Allow: whichever of the two is still off, usage access first. */
fun CrewViewModel.allowPermissions(activity: Activity) {
    if (!UsageRepo.hasAccess(activity)) UsageRepo.openSettings(activity) else allowOverlay(activity)
}

/** "Allow": to Android's switch; the round starts when TestTrack is back in front. */
fun CrewViewModel.allowOverlay(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
