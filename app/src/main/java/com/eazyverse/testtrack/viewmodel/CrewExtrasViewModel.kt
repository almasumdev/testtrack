package com.eazyverse.testtrack.viewmodel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.AuthRepo
import com.eazyverse.testtrack.data.DriveRepo
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.ProofImages
import com.eazyverse.testtrack.data.ReviewCaptureService
import com.eazyverse.testtrack.data.UploadResult
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.FeedbackIn
import com.eazyverse.testtrack.net.VersionIn
import com.eazyverse.testtrack.net.markDown
import com.eazyverse.testtrack.net.sendFeedback
import com.eazyverse.testtrack.net.tellUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The crew's own acts beyond opening apps, as docs/crew.md decides them.
 *
 * The owner telling the crew about an update, read off the copy Play put on their own phone;
 * the owner marking their app down for a night; and a member's private feedback on Play for an
 * app, with its picture taken by the review camera over Play, or picked from the phone. Each
 * says what happened in [message] and has the Crew page read the crews again.
 */
class CrewExtrasViewModel : ViewModel() {
    var message by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    /** Why the owner's Update cannot be tapped yet, said when they tap it anyway. */
    var explain by mutableStateOf<String?>(null)

    /** The seat waiting on its owner's yes to being marked down tonight. */
    var confirmDown by mutableStateOf<String?>(null)

    /** The app a member is sending Play feedback for, from its first ask to its picture landing. */
    var feedbackFor by mutableStateOf<FeedbackJob?>(null)
        private set

    data class FeedbackJob(
        val seatId: String,
        val label: String,
        val packageName: String,
        /** Instructions asked and agreed; the camera is up over Play now. */
        val capturing: Boolean = false,
        /** Shown how it goes first; false when they chose to pick a picture from the phone. */
        val asking: Boolean = true,
    )

    /** The copy on this phone, as the server will judge it. */
    data class Copy(
        val installed: Boolean,
        val fromPlay: Boolean,
        val versionCode: Long,
        val versionName: String,
    )

    fun copy(
        context: Context,
        packageName: String,
    ): Copy {
        val info = InstalledApps.info(context, packageName)
        return Copy(info.installed, info.installer == PLAY, info.versionCode, info.versionName.orEmpty())
    }

    /**
     * Why the owner's Update cannot be tapped yet, or null when it can: their own copy must have
     * come from Play, and be newer than the version the crew already has.
     */
    fun updateBlocked(
        context: Context,
        packageName: String,
        crewHas: Long?,
    ): String? {
        val mine = copy(context, packageName)
        return when {
            !mine.installed ->
                "Install your app from Google Play on this phone first. You're in its closed test, so Play has it for you."
            !mine.fromPlay ->
                "Install the new version from Google Play on this phone first. A copy from Android Studio or a file doesn't count."
            crewHas != null && mine.versionCode <= crewHas ->
                "The crew already has this version. Publish a newer one on Play, install it from Play on this phone, then tap Update."
            else -> null
        }
    }

    fun tellUpdate(
        activity: Activity,
        seatId: String,
        packageName: String,
        reload: () -> Unit,
    ) = act(reload) {
        val mine = copy(activity, packageName)
        Api.tellUpdate(seatId, VersionIn(mine.versionCode, mine.versionName, if (mine.fromPlay) PLAY else ""))
        "The crew was told. They have 24 hours to update."
    }

    fun markDown(
        seatId: String,
        reload: () -> Unit,
    ) = act(reload) {
        Api.markDown(seatId)
        "Marked down tonight. Nobody owes it tonight, and nobody is marked down for it."
    }

    /** Ask about the feedback, then the camera and Play once they have read how it goes. */
    fun askFeedback(
        seatId: String,
        label: String,
        packageName: String,
        asking: Boolean = true,
    ) {
        feedbackFor = FeedbackJob(seatId, label, packageName, asking = asking)
    }

    fun cancelFeedback() {
        feedbackFor = null
    }

    /** Screen sharing said yes: the camera button over Play, and the app's Play page under it. */
    fun startCapture(
        activity: Activity,
        code: Int,
        data: Intent,
    ) {
        val job = feedbackFor ?: return
        feedbackFor = job.copy(capturing = true)
        ReviewCaptureService.start(activity, code, data, crew = true)
        runCatching {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${job.packageName}"))
                    .setPackage(PLAY),
            )
        }
    }

    /** Back in TestTrack: the camera's picture, if it took one, goes to Drive and onto the feedback. */
    fun takeCaptured(activity: Activity) {
        val job = feedbackFor ?: return
        val file = ReviewCaptureService.crewProof.value ?: return
        ReviewCaptureService.takeCrewProof()
        send(activity, job) { file.readBytes() }
    }

    /** A picture of the feedback picked from the phone instead. */
    fun pick(
        activity: Activity,
        uri: Uri,
    ) {
        val job = feedbackFor ?: return
        send(activity, job) { ProofImages.bytes(activity, uri) }
    }

    private fun send(
        activity: Activity,
        job: FeedbackJob,
        bytes: () -> ByteArray,
    ) = act({}) {
        val token =
            AuthRepo.driveTokenOrNull(activity)
                ?: throw ApiError(0, "drive", "Connect Google Drive to send it. The picture is saved there for the team.")
        val data = withContext(Dispatchers.IO) { bytes() }
        val id = DriveRepo.reserveId(token)
        val upload = DriveRepo.upload(token, "feedback_${job.seatId.take(8)}.jpg", "image/jpeg", fileId = id) { data }
        if (upload is UploadResult.Failed) throw ApiError(0, "drive", upload.reason)
        Api.sendFeedback(job.seatId, FeedbackIn("drive:$id", token))
        feedbackFor = null
        "Feedback for ${job.label} sent. Thank you."
    }

    fun clearMessage() {
        message = null
    }

    private fun act(
        reload: () -> Unit,
        block: suspend () -> String,
    ) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                message = block()
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                message = e.message
            } catch (_: Exception) {
                message = "We could not reach TestTrack. Check your connection."
            } finally {
                busy = false
            }
        }
    }

    private companion object {
        const val PLAY = "com.android.vending"
    }
}
