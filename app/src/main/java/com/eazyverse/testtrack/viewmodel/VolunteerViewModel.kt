package com.eazyverse.testtrack.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.Device
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiVolunteerSpot
import com.eazyverse.testtrack.net.VolunteerIn
import com.eazyverse.testtrack.net.leaveVolunteer
import com.eazyverse.testtrack.net.volunteer
import com.eazyverse.testtrack.net.volunteerSpots
import com.eazyverse.testtrack.ui.Crew
import com.eazyverse.testtrack.ui.CrewRules
import com.eazyverse.testtrack.ui.CrewViewModel
import com.eazyverse.testtrack.ui.Snacks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.TimeZone

/** Where the reader stands with one running crew on the volunteer list. */
enum class SpotState {
    /** Spots free and nothing in the way: Volunteer. */
    OPEN,

    /** No spot free now. */
    FULL,

    /** The reader volunteers here already. */
    VOLUNTEERING,

    /** The reader's own app is in it. */
    MEMBER,

    /** Taken out of it before, after a missed night. Never again in this crew. */
    OUT,

    /** Left it before. Never again in this crew. */
    LEFT,

    /** Their spot closed when an app came back. Never again in this crew. */
    CLOSED,

    /** The crew is on or past its last night: no night left to volunteer in. */
    TOO_LATE,

    /** A state this build does not know yet. Never joinable: the server may have a reason we can't read. */
    UNAVAILABLE,
    ;

    companion object {
        fun of(wire: String) =
            when (wire) {
                "full" -> FULL
                "volunteering" -> VOLUNTEERING
                "member" -> MEMBER
                "out", "removed" -> OUT
                "left" -> LEFT
                "released", "done" -> CLOSED
                "too_late" -> TOO_LATE
                "open" -> OPEN
                else -> UNAVAILABLE
            }
    }
}

/** The refusal of a second volunteer crew, from a server that allows only one at a time. */
internal val ApiError.isAlreadyVolunteering: Boolean
    get() = status == 409 && (code == "already_volunteering" || details["reason"] == "already_volunteering")

/** The order the list shows: spots to take first, then the reader's own, then the rest. */
private val SPOT_ORDER = listOf(SpotState.OPEN, SpotState.VOLUNTEERING, SpotState.FULL, SpotState.MEMBER)

/** Open spots first, so the ones the reader can take stay on top however many they are in already. */
internal fun List<VolunteerSpot>.inListOrder(): List<VolunteerSpot> =
    sortedBy { spot -> SPOT_ORDER.indexOf(spot.state).let { if (it < 0) SPOT_ORDER.size else it } }

/** A running crew with volunteer spots, as the Open tab lists it: icons only, never names of apps. */
data class VolunteerSpot(
    val crewId: String,
    val name: String,
    val day: Int,
    val runDays: Int,
    val free: Int,
    val icons: List<String>,
    /** The reader's first night here if they joined now. */
    val firstNight: Int,
    /** The most tix the reader could earn here, one whole night at a time. */
    val tix: Int,
    val state: SpotState,
) {
    /** "Night 6 of 14", never past the run's last night. */
    val nightLine: String get() = "Night ${if (runDays > 0) minOf(day, runDays) else day} of $runDays"
}

internal fun ApiVolunteerSpot.toSpot(): VolunteerSpot {
    val first = if (firstNight > 0) firstNight else day + 1
    val nights = (runDays - first + 1).coerceAtLeast(0)
    return VolunteerSpot(
        crewId = crewId,
        name = name.ifBlank { "Crew $number" },
        day = day,
        runDays = runDays,
        free = free,
        icons = icons,
        firstNight = first,
        tix = if (tix > 0) tix else nights * CrewRules.VOLUNTEER_NIGHT_TIX,
        state = SpotState.of(state),
    )
}

/**
 * The Open tab's volunteer list and the sheet that takes a spot. Leaving is on the crew's own page
 * ([leaveVolunteer]), where the spot is. Every rule is the server's: which crews have spots, and
 * whether this account may take one; refusals come back in its own words.
 */
class VolunteerViewModel : ViewModel() {
    /** The list, or null before the first answer. */
    var spots by mutableStateOf<List<VolunteerSpot>?>(null)
        private set
    var loading by mutableStateOf(true)
        private set

    /** The list could not be read: the section stays away rather than showing a fault of its own. */
    var failed by mutableStateOf(false)
        private set

    /** Whether this account may take a spot at all, and the server's reason when not. */
    var blocked by mutableStateOf<String?>(null)
        private set

    /** The crew whose join sheet is open, or null. */
    var picked by mutableStateOf<VolunteerSpot?>(null)
        private set

    var joining by mutableStateOf(false)
        private set

    fun load() {
        viewModelScope.launch {
            try {
                val got = Api.volunteerSpots()
                spots = got.spots.map { it.toSpot() }.inListOrder()
                blocked = if (got.canJoin) null else got.blocked.orEmpty().ifBlank { "You can't volunteer right now." }
                failed = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
            } finally {
                loading = false
            }
        }
    }

    fun pick(spot: VolunteerSpot?) {
        picked = spot
    }

    /** Take a spot in [spot]'s crew from this phone; [onDone] gets the crew's id once it is the reader's. */
    fun join(
        context: Context,
        spot: VolunteerSpot,
        onDone: (String) -> Unit,
    ) {
        if (joining) return
        // The retry outlives this call, so it must not hold an Activity.
        val app = context.applicationContext
        val fingerprint = Device.fingerprint(app)
        if (fingerprint == null) {
            Snacks.error("This phone won't identify itself, so it can't volunteer.")
            return
        }
        joining = true
        viewModelScope.launch {
            try {
                val crew = Api.volunteer(VolunteerIn(spot.crewId, fingerprint, TimeZone.getDefault().id))
                onDone(crew.id)
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                // A server from before several crews at once refuses a second one. Not a fault.
                if (e.isAlreadyVolunteering) {
                    Snacks.info("You can volunteer in one crew at a time for now.")
                } else {
                    Snacks.error(e.message)
                }
                load()
            } catch (_: Exception) {
                Snacks.offline { join(app, spot, onDone) }
            } finally {
                joining = false
            }
        }
    }
}

/**
 * Leave a volunteer spot for good, and go straight on: the crew leaves the list at once, so its
 * page closes with no "left" screen in between, nothing is said, and the spot is in the
 * Record tab's volunteering list. The crews are read again quietly behind it.
 */
fun CrewViewModel.leaveVolunteer(crew: Crew) {
    val spot = crew.volunteer ?: return
    call(
        seat = spot.id,
        onDone = { _: Unit ->
            forget(crew.id)
            records.load(signedOut = false)
        },
    ) { Api.leaveVolunteer(spot.id) }
}
