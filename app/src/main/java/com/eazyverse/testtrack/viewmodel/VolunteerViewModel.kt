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
import com.eazyverse.testtrack.ui.SnackTone
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
    ;

    companion object {
        fun of(wire: String) =
            when (wire) {
                "full" -> FULL
                "volunteering" -> VOLUNTEERING
                "member" -> MEMBER
                "out", "removed" -> OUT
                "left", "released" -> LEFT
                else -> OPEN
            }
    }
}

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
)

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
                spots = got.spots.map { it.toSpot() }
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
                Snacks.done("You're in ${crew.name}. Your first night starts at midnight.")
                onDone(crew.id)
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                Snacks.error(e.message)
                load()
            } catch (_: Exception) {
                Snacks.error("Couldn't reach TestTrack. Check your connection.") { join(app, spot, onDone) }
            } finally {
                joining = false
            }
        }
    }
}

/** Leave a volunteer spot for good. The crew reloads, and its page says the spot is over. */
fun CrewViewModel.leaveVolunteer(
    context: Context,
    crew: Crew,
) {
    val spot = crew.volunteer ?: return
    call(
        context = context,
        seat = spot.id,
        onDone = { _: Unit -> say("You left ${crew.name}. The tix you earned stay yours.", SnackTone.DONE) },
    ) { Api.leaveVolunteer(spot.id) }
}
