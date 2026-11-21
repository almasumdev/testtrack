package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * A crew's fifteen places first, the reader's app last. Opening the card still opens its crew; the
 * chat mark opens its chat. Without [showApp] the app line is left out and the chat mark moves up
 * into the heading.
 */
@Composable
internal fun CrewLobbyRow(
    crew: Crew,
    onChat: () -> Unit = {},
    showApp: Boolean = true,
    onOpen: () -> Unit,
) {
    val mine = crew.mine
    CrewCard(padding = PaddingValues(16.dp), onClick = onOpen) {
        CrewTileHeading(
            crew.name,
            if (crew.out) "Sent back" else "${crew.filled} of ${CrewRules.SEATS} in",
            if (crew.out) Tone.BAD else null,
            trailing = headingChat(crew, showApp, onChat),
        )
        if (!crew.out) {
            Spacer(Modifier.height(14.dp))
            CrewLobbyAppIcons(crew)
        }
        Spacer(Modifier.height(if (crew.out) 14.dp else 9.dp))
        LobbyStanding(crew)
        if (showApp && crew.volunteer != null) {
            CrewTileVolunteer(crew) { CrewChatTileButton(crew, onChat) }
        } else if (showApp) {
            CrewTileApp(mine) { CrewChatTileButton(crew, onChat) }
        }
    }
}

@Composable
private fun LobbyStanding(crew: Crew) {
    val mine = crew.mine
    val checking = mine?.state == SeatState.CHECKING
    val reported = (mine?.reports ?: 0) > 0
    val headline =
        when {
            crew.out -> if (mine?.state == SeatState.REMOVED) "Taken out by the team" else "Your app needs a fix"
            checking -> "Members are checking your app"
            reported -> "${mine?.reports} of ${CrewRules.REPORTS_TO_RETURN} reports"
            !crew.filling -> if (crew.startsIn < 60_000L) "Starts any moment" else "Starts in ${untilStart(crew.startsIn)}"
            else -> "Your seat is held"
        }
    val detail =
        when {
            crew.out -> mine?.let(::lobbyRefund).orEmpty()
            checking -> "${mine?.yes} Yes, ${mine.no} No"
            reported -> "Check the report"
            !crew.filling -> if (crew.firstNight > 1) "Your day ${crew.firstNight}" else "At midnight"
            else -> "${(CrewRules.SEATS - crew.filled).coerceAtLeast(0)} more to start"
        }
    val tone =
        when {
            crew.out -> Tone.BAD
            checking || reported -> Tone.WARN
            else -> null
        }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(headline, Modifier.weight(1f), style = TextStyles.RowValue, color = tone?.ink() ?: Tt.colors.text)
        Spacer(Modifier.width(12.dp))
        Text(
            detail,
            style = TextStyles.SmallNote,
            color = Tt.colors.text3,
            textAlign = TextAlign.End,
        )
    }
}

/** Older servers omit ledger amounts; do not invent a refund while its result is unknown. */
private fun lobbyRefund(seat: CrewSeat): String =
    when {
        seat.refunded > 0 -> "${seat.refunded} tix returned"
        seat.charged == 0 -> "No tix charged"
        seat.refunded == 0 -> "No tix returned"
        else -> "See refund details"
    }

@Composable
internal fun CrewLobbyRowLoading() {
    CrewCard(padding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Skeleton(width = 78.dp, height = 23.dp)
            Skeleton(width = 62.dp, height = 17.dp)
        }
        Spacer(Modifier.height(14.dp))
        CrewAppIconStrip(CrewRules.SEATS) { side ->
            repeat(CrewRules.SEATS) { Skeleton(width = side, height = side, corner = side * 0.3f) }
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Skeleton(height = 20.dp, modifier = Modifier.weight(1f))
            Skeleton(height = 17.dp, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(width = 28.dp, height = 28.dp, corner = 8.dp)
            Spacer(Modifier.width(8.dp))
            Skeleton(width = 130.dp, height = 18.dp)
        }
    }
}
