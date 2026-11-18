package com.eazyverse.testtrack.viewmodel

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eazyverse.testtrack.data.PictureQueue
import com.eazyverse.testtrack.data.RoundStore
import com.eazyverse.testtrack.data.VisitCameraService
import com.eazyverse.testtrack.data.VisitPictures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What a night's round needs before it starts, now that every visit counts with its picture.
 *
 * Tonight's screen-share yes, which Android gives for one round at a time. A round waits here for
 * it; Not now opens nothing, and tonight is still owed. Afterwards it sends whatever pictures are
 * still on the phone, each time TestTrack is in front.
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
        this.float = float
        waitingFor = stops
    }

    /** Not now: nothing is opened. */
    fun skip() {
        waitingFor = null
    }

    /** A line for the Crew page, from the asks this runs. */
    fun tell(text: String) = say(text)

    /** Android said yes: the camera goes on and the round starts. */
    fun start(
        activity: Activity,
        code: Int,
        data: Intent,
    ) {
        val stops = waitingFor ?: return
        waitingFor = null
        VisitCameraService.start(activity, code, data)
        NightRound.start(activity, stops, float = float && NightRound.canFloat(activity), pictures = true)
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

    /** Pictures still on the phone, onto their visits. What still could not go stays, and says why on the page. */
    fun send(activity: Activity) {
        scope.launch {
            VisitPictures.load(activity)
            if (VisitPictures.waiting.value.isEmpty()) return@launch
            VisitPictures.send(activity)
            reload(activity)
        }
    }
}
