package com.eazyverse.testtrack.viewmodel

import android.content.Context
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.RoundStore
import com.eazyverse.testtrack.data.UsageRepo
import com.eazyverse.testtrack.data.VisitPictures
import com.eazyverse.testtrack.domain.AppNight
import com.eazyverse.testtrack.domain.RoundRules
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.PrivateVisitIn
import com.eazyverse.testtrack.net.privateVisit
import com.eazyverse.testtrack.ui.Snacks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/*
 * Recording one app of the round with the server, how that went, and queuing its picture. Beside
 * [NightRound], which decides what to do with each answer.
 */

internal sealed interface Outcome {
    data object Counted : Outcome

    data class Short(
        val ms: Long,
    ) : Outcome

    data class Failed(
        val message: String,
    ) : Outcome

    /** The server will not count this app's open as it is: skipped, and the round goes on. */
    data class Refused(
        val message: String,
    ) : Outcome
}

/** [now] with what the recording changed on the live round: the missing-picture count and the held mark. */
internal fun NightRound.fresh(now: RoundStore.Round): RoundStore.Round =
    round?.let { now.copy(noPicture = it.noPicture, recordedIndex = it.recordedIndex) } ?: now

private val recordingNow = MutableStateFlow<AppNight?>(null)

/** The app being recorded right now, with its picture on the way: its row says "Uploading screenshot" meanwhile. */
internal val NightRound.recording: StateFlow<AppNight?> get() = recordingNow

internal suspend fun NightRound.record(
    context: Context,
    stop: RoundStore.Stop,
    since: Long,
    taken: File? = null,
): Outcome {
    // The visit is dated by the night it is recorded on, and the stop's picture by the night
    // the round began: across midnight the two differ, so nothing is recorded.
    if (newNightFor(stop, round?.startedAt ?: 0L)) return Outcome.Failed(NEW_NIGHT_LINE)
    // A private test takes no picture, so its row never says one is uploading.
    val night = AppNight(stop.seatId, stop.day).takeIf { stop.privateTestId.isBlank() }
    if (night != null) recordingNow.value = night
    try {
        return recordVisit(context, stop, since, taken)
    } finally {
        if (night != null) recordingNow.compareAndSet(night, null)
    }
}

private suspend fun NightRound.recordVisit(
    context: Context,
    stop: RoundStore.Stop,
    since: Long,
    taken: File?,
): Outcome {
    val used = UsageRepo.measure(context, stop.packageName, since, needMs = COUNT_MS, source = "crew")
    if (used < COUNT_MS) return Outcome.Short(used)
    return try {
        // Which copy was opened: a copy Play did not put here, or one older than the owner's
        // newest past its 24 hours, is refused with what to do.
        val copy = InstalledApps.info(context, stop.packageName)
        // A private test counts on its own ask, from the seconds and the copy, and nothing else.
        if (stop.privateTestId.isNotBlank()) {
            Api.privateVisit(stop.privateTestId, PrivateVisitIn(used, copy.installer.orEmpty()))
            return Outcome.Counted
        }
        Api.visit(stop.seatId, used, versionCode = copy.versionCode.takeIf { copy.installed }, installer = copy.installer.orEmpty())
        // The visit first, then its picture, in the background: the next app does not wait.
        if (taken != null) {
            queuePicture(context, stop, taken)
        } else if (pictures) {
            // Counted without a picture: said plainly in the round's last line. A picture that
            // still comes while the app is held ([queueLate]) takes it off again.
            changeMissing(context, 1)
        }
        Outcome.Counted
    } catch (e: ApiError) {
        if (RoundRules.refusedOpen(e.status, e.isAuth)) Outcome.Refused(e.message) else Outcome.Failed(e.message)
    } catch (_: Exception) {
        Outcome.Failed(Snacks.OFFLINE_LINE)
    }
}

/** Puts a picture on the send queue for [stop]; one crew visit, one picture. */
internal fun NightRound.queuePicture(
    context: Context,
    stop: RoundStore.Stop,
    file: File,
) {
    if (pictureQueued) return
    pictureQueued = true
    VisitPictures.add(context, VisitPictures.Waiting(stop.seatId, stop.label, file.path, day = stop.day))
    scope.launch { VisitPictures.send(context) }
}

/**
 * A counted app whose picture came after its recording. Counted apps with no picture were
 * noted as missing when they were recorded, so this one is taken off that count.
 */
internal fun NightRound.queueLate(
    context: Context,
    stop: RoundStore.Stop,
) {
    val file = shot ?: return
    if (pictureQueued || recorded != Outcome.Counted || stop.privateTestId.isNotBlank()) return
    queuePicture(context, stop, file)
    changeMissing(context, -1)
}

internal fun NightRound.changeMissing(
    context: Context,
    by: Int,
) {
    val now = round ?: return
    round = now.copy(noPicture = (now.noPicture + by).coerceAtLeast(0))
    RoundStore.save(context, round)
}
