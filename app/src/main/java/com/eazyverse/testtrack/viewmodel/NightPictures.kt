package com.eazyverse.testtrack.viewmodel

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eazyverse.testtrack.data.AuthRepo
import com.eazyverse.testtrack.data.PictureQueue
import com.eazyverse.testtrack.data.RoundStore
import com.eazyverse.testtrack.data.Session
import com.eazyverse.testtrack.data.VisitCameraService
import com.eazyverse.testtrack.data.VisitPictures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What a night's round needs before it starts, now that every visit counts with its picture.
 *
 * Google Drive, where the pictures go, and tonight's screen-share yes, which Android gives for one
 * round at a time. A round waits here for both; Not now opens nothing, and tonight is still owed.
 * Afterwards it sends whatever pictures are still on the phone, each time TestTrack is in front.
 *
 * Kept apart from the Crew page's view model, which [say] and [reload] lead back to.
 */
class NightPictures(
    private val scope: CoroutineScope,
    private val say: (String) -> Unit,
    private val reload: (Activity) -> Unit,
) {
    /** A round waiting on tonight's screen-share yes. */
    var waitingFor by mutableStateOf<List<RoundStore.Stop>?>(null)
        private set
    private var float = true

    /** Drive is not connected, so tonight's pictures have nowhere to go: asked before the round. */
    var needsDrive by mutableStateOf(false)
        private set

    /** The pictures still on the phone, so a row can say "sending" rather than "missing". */
    val waiting get() = VisitPictures.waiting

    /** Why the last try to send them stopped; null while they are going, or once they went. */
    val stuck get() = VisitPictures.stuck

    /**
     * Tonight's picture of this app is only waiting to send: it has been opened tonight and needs
     * no second visit. A picture from an earlier night waiting is not tonight's, so it is opened.
     */
    fun sending(
        seatId: String,
        day: Int,
    ) = PictureQueue.waitingTonight(VisitPictures.waiting.value, seatId, day)

    fun ask(
        stops: List<RoundStore.Stop>,
        float: Boolean,
    ) {
        if (!Session.driveConnected) {
            needsDrive = true
            return
        }
        this.float = float
        waitingFor = stops
    }

    /** Not now: nothing is opened. */
    fun skip() {
        waitingFor = null
    }

    fun clearDriveAsk() {
        needsDrive = false
    }

    /** A line for the Crew page, from the asks this drives. */
    fun tell(text: String) = say(text)

    /**
     * Android said yes: the camera goes on and the round starts. Drive's grant is asked for here,
     * while TestTrack is in front, since asking needs it; the round's pictures go with it.
     */
    fun start(
        activity: Activity,
        code: Int,
        data: Intent,
    ) {
        val stops = waitingFor ?: return
        waitingFor = null
        scope.launch {
            val token = AuthRepo.driveTokenOrNull(activity)
            if (token == null) {
                Session.updateDriveConnected(false)
                needsDrive = true
                return@launch
            }
            VisitPictures.token = token
            VisitCameraService.start(activity, code, data)
            NightRound.start(activity, stops, float = float && NightRound.canFloat(activity), pictures = true)
        }
    }

    /** A picture picked from the phone for an app whose own picture failed. */
    fun pick(
        activity: Activity,
        seatId: String,
        label: String,
        uri: Uri,
        day: Int = 0,
    ) {
        scope.launch {
            if (!VisitPictures.addPicked(activity, seatId, label, uri, day)) {
                say("That picture could not be read. Choose another.")
                return@launch
            }
            send(activity)
        }
    }

    /**
     * Pictures still on the phone, onto their visits. A fresh Drive grant every time, since the one
     * a round started with lasts an hour; none to be had quietly means Google wants the member to
     * agree again, which is asked. What still could not go stays, and says why on the page.
     */
    fun send(activity: Activity) {
        scope.launch {
            VisitPictures.load(activity)
            if (VisitPictures.waiting.value.isEmpty()) return@launch
            val token = AuthRepo.driveTokenOrNull(activity)
            if (token == null) {
                VisitPictures.needsDrive()
                needsDrive = true
                return@launch
            }
            VisitPictures.token = token
            VisitPictures.send(activity, token)
            reload(activity)
        }
    }
}
