package com.eazyverse.testtrack.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.crewPictures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One night of one member's, with its picture's ref, or empty. */
data class MemberNight(
    val day: Int,
    val at: String,
    val usedMs: Long,
    val proof: String,
    val byHand: Boolean,
    val counts: Boolean,
) {
    /** Opened, and its picture still on the member's phone hours later: drawn red until it lands. */
    fun waitingLong(now: java.time.Instant = java.time.Instant.now()): Boolean {
        if (counts || proof.isNotBlank()) return false
        val opened =
            runCatching {
                java.time.OffsetDateTime
                    .parse(at)
                    .toInstant()
            }.getOrNull() ?: return false
        return opened.plusSeconds(WAIT_LONG_HOURS * 3600L) < now
    }

    companion object {
        /** A picture still on the phone this long after its visit shows red, on every grid. */
        const val WAIT_LONG_HOURS = 3
    }
}

/** One member's nights with the reader's own app. */
data class MemberNights(
    val seatId: String,
    val name: String,
    val day: Int,
    val out: Boolean,
    val nights: List<MemberNight>,
    /** Reported the app as not installing, so owes it nothing. */
    val excused: Boolean = false,
    /** Their first night with this app, and nights the team let them off. */
    val firstNight: Int = 1,
    val freeDays: Set<Int> = emptySet(),
    /** Nights they did not owe the app, a quiet skip covering it. Drawn like any night not owed. */
    val skippedDays: Set<Int> = emptySet(),
    /** Nights a report they sent during the run excused the app, that night alone. */
    val reportedDays: Set<Int> = emptySet(),
    /** Their face, as an address, and their email; empty when the server does not send them. */
    val photo: String = "",
    val email: String = "",
    /** The night their app was taken out of the run, as the server says it; null while in or unsaid. */
    val outDay: Int? = null,
    /** A volunteer, with no app here: [seatId] is their volunteer id. */
    val volunteer: Boolean = false,
) {
    /** They did not owe the app that night: reported, let off, or covered by a skip. */
    fun notOwed(
        day: Int,
        offDays: Set<Int>,
    ) = excused || day in freeDays || day in offDays || day in skippedDays || day in reportedDays
}

/** The reader's app in one crew, as every other member opened it. */
data class AppNights(
    val appName: String,
    val runDays: Int,
    val testers: List<MemberNights>,
    /** Nights the app was let off for everybody. */
    val offDays: Set<Int> = emptySet(),
)

/** One crew's pictures of your own app, every member and every night: the owner's grid. */
class CrewPicturesViewModel : ViewModel() {
    var pictures by mutableStateOf<AppNights?>(null)
        private set
    var failed by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(true)
        private set

    /** The crew's details were cleared after it ended (410 details_gone): nothing left to show. */
    var gone by mutableStateOf(false)
        private set

    fun load(crewId: String) {
        loading = true
        viewModelScope.launch {
            try {
                val got = Api.crewPictures(crewId)
                pictures =
                    AppNights(
                        got.appName,
                        got.runDays,
                        (got.testers + got.volunteers).map { t ->
                            val volunteer = t.volunteerId.isNotBlank()
                            MemberNights(
                                if (volunteer) t.volunteerId else t.seatId,
                                t.name,
                                t.day,
                                t.out,
                                t.nights.map { MemberNight(it.day, it.at, it.usedMs, it.proof, it.byHand, it.counts) },
                                excused = t.excused,
                                firstNight = t.firstNight,
                                freeDays = t.freeDays.toSet(),
                                skippedDays = t.skippedDays.toSet(),
                                reportedDays = t.reportedDays.toSet(),
                                photo = t.photo,
                                email = t.email,
                                // A volunteer's is their last whole night: the nights after it are not theirs.
                                outDay = if (volunteer) t.outDay?.plus(1) else t.outDay,
                                volunteer = volunteer,
                            )
                        },
                        offDays = got.offDays.toSet(),
                    )
                failed = null
                gone = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                gone = e.status == 410 || e.code == DETAILS_GONE
                failed = e.message
            } catch (_: Exception) {
                failed = "We could not reach TestTrack. Check your connection."
            } finally {
                loading = false
            }
        }
    }

    private companion object {
        const val DETAILS_GONE = "details_gone"
    }
}
