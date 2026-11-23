package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.HandHeart
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserX
import com.eazyverse.testtrack.domain.instantOf
import com.eazyverse.testtrack.domain.pluralOf
import com.eazyverse.testtrack.domain.shortDate
import com.eazyverse.testtrack.ui.theme.Tt
import java.time.ZoneId

/*
 * The reader's volunteer spots that are over, on the Record tab under their finished crews. Every
 * one is kept, a join and a leave on the same evening too, with what it paid.
 */

/** One volunteer spot that is over, as GET /me/crew-records lists it under `volunteering`. */
data class VolunteerStint(
    val id: String,
    val crewId: String,
    val crewName: String,
    val crewNumber: Int = 0,
    /** left, out, released, removed or done. */
    val state: String = "left",
    /** ISO times. */
    val joinedAt: String? = null,
    val endedAt: String? = null,
    val firstNight: Int = 1,
    /** The last whole night that counted; 0 or null when it ended before the first night. */
    val lastNight: Int? = null,
    val nightsPaid: Int = 0,
    val tixEarned: Int = 0,
    val endReason: String = "",
)

/** What came of a spot, in one line, and the tone it is said in. */
internal fun stintHeadline(s: VolunteerStint): Pair<String, Tone> {
    val last = s.lastNight ?: 0
    return when (s.state) {
        "out" -> "You were out" to Tone.BAD
        "removed" -> "The TestTrack team took you out" to Tone.BAD
        "released" -> "Your spot closed when an app came back" to Tone.QUIET
        "done" -> "You volunteered to the end" to Tone.OK
        else -> (if (last < s.firstNight) "You left before your first night" else "You left after night $last") to Tone.QUIET
    }
}

/** The days the spot ran, in this phone's calendar: "Nov 21", or "Nov 21 to 24". */
internal fun stintDates(
    s: VolunteerStint,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val from = instantOf(s.joinedAt)
    val to = instantOf(s.endedAt)
    if (from == null) return to?.let { shortDate(it, zone) }.orEmpty()
    if (to == null) return shortDate(from, zone)
    val first = from.atZone(zone).toLocalDate()
    val last = to.atZone(zone).toLocalDate()
    return when {
        first == last -> shortDate(from, zone)
        first.month == last.month -> "${shortDate(from, zone)} to ${last.dayOfMonth}"
        else -> "${shortDate(from, zone)} to ${shortDate(to, zone)}"
    }
}

/** One spot: the crew and its days, how it ended, and the nights and tix it came to. */
@Composable
internal fun VolunteerStintTile(stint: VolunteerStint) {
    val (headline, tone) = stintHeadline(stint)
    CrewCard(padding = PaddingValues(16.dp)) {
        CrewTileHeading(stint.crewName.ifBlank { crewNameOf(stint.crewNumber) }, stintDates(stint))
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(
                when (stint.state) {
                    "left" -> Lucide.LogOut
                    "out", "removed" -> Lucide.UserX
                    else -> Lucide.HandHeart
                },
                28.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(headline, style = TextStyles.RowValue, color = tone.ink())
        }
        if (stint.endReason.isNotBlank() && stint.state != "left") {
            Text(stint.endReason, Modifier.padding(top = 6.dp), style = TextStyles.NoteLine, color = Tt.colors.text3)
        }
        VolunteerStats(
            listOf(
                "${stint.nightsPaid}" to "${pluralOf(stint.nightsPaid, "night")} done",
                "${stint.tixEarned} tix" to "earned",
            ),
            Modifier.padding(top = 12.dp),
        )
    }
}
