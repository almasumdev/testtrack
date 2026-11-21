package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.HandHeart
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Ticket
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.VolunteerSpot
import com.eazyverse.testtrack.viewmodel.VolunteerViewModel

/*
 * Taking a volunteer spot: what it asks every night, what it pays, and the one hard rule, said
 * before the button rather than discovered after a missed night.
 */

private val LineStyle = tileStyle(13.5f, 19f, FontWeight.Normal)

/** The sheet for one crew. [onJoin] is handed a way to close the sheet once the spot is the reader's. */
@Composable
internal fun VolunteerJoinSheet(
    spot: VolunteerSpot,
    busy: Boolean,
    onJoin: (close: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = Tt.colors
    val apps = spot.icons.size
    PickerSheet(
        title = "Volunteer in ${spot.name}",
        onDismiss = onDismiss,
        subtitle = "Night ${spot.day} of ${spot.runDays}, $apps apps",
        skipHalf = true,
        leading = { IconTile(Lucide.HandHeart, 36.dp, corner = 12.dp) },
    ) { close ->
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            if (apps > 0) {
                SpotIcons(spot.icons)
                Spacer(Modifier.height(14.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SheetLine(Lucide.Moon, "Open all $apps apps every night", ", the same round as members, pictures too.")
                SheetLine(
                    Lucide.Ticket,
                    "${CrewRules.VOLUNTEER_NIGHT_TIX} tix for each night you finish.",
                    " Up to ${spot.tix} tix in this crew.",
                )
                SheetLine(Lucide.TriangleAlert, "Miss one night and you're out.", " One app left unopened is a miss.", t.warn)
                SheetLine(Lucide.MessageCircle, "", "Chat with the crew. Reports and votes stay with members.")
                SheetLine(Lucide.LogOut, "", "Leave any time. The tix you earned stay yours.")
            }
            TintBox(Modifier.padding(top = 12.dp)) {
                IconLine(
                    Lucide.Clock,
                    t.accentInk,
                    buildAnnotatedString {
                        append("Your first night is ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = t.text)) { append("night ${spot.firstNight}") }
                        append(", from midnight your time. Install the apps before then.")
                    },
                    tileStyle(13f, 18f, FontWeight.Normal),
                    t.text2,
                    Modifier.padding(vertical = 2.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Primary("Volunteer", busy = busy, mark = rememberVectorPainter(Lucide.HandHeart)) {
                onJoin { close(onDismiss) }
            }
        }
    }
}

/** One line of the deal: its icon inline, a bold start (in [ink] for the warning) and the rest. */
@Composable
private fun SheetLine(
    icon: ImageVector,
    bold: String,
    rest: String,
    ink: Color? = null,
) {
    val t = Tt.colors
    IconLine(
        icon,
        ink ?: t.accentInk,
        buildAnnotatedString {
            if (bold.isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = ink ?: t.text)) { append(bold) }
            append(rest)
        },
        LineStyle,
        t.text2,
        iconSize = 15.dp,
    )
}

/**
 * The join sheet, wherever the volunteer list is: open for the crew picked on it. A spot taken
 * reloads the crews, then opens the new one's page, so it is never shown before it is there.
 */
@Composable
internal fun VolunteerJoinHost(
    crews: CrewViewModel,
    volunteers: VolunteerViewModel,
    onOpenCrew: (String) -> Unit,
) {
    val context = LocalContext.current
    val spot = volunteers.picked ?: return
    VolunteerJoinSheet(
        spot,
        busy = volunteers.joining,
        onJoin = { close ->
            volunteers.join(context, spot) { crewId ->
                close()
                crews.load(context) { onOpenCrew(crewId) }
            }
        },
        onDismiss = { volunteers.pick(null) },
    )
}
