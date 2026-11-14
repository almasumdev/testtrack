package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.CrewAddViewModel

/*
 * The seat page's details: the testers group once it is done, the crew, how they get past a sign-in,
 * what the seat costs, and the notes that can hold the button. They scroll with the page; the one filled button that takes
 * the seat is [CrewSeatSubmit], the page's last part.
 */

/** What the details and the button read from the seat screen, so the screen owns every launcher and callback. */
internal class SeatDetailsState(
    val app: AppEntry,
    /** The crew a seat goes into; null when none is filling and this seat starts one. */
    val crewName: String?,
    val busy: Boolean,
    /** Play shows the app to the testers group: the seat can't be submitted before. */
    val groupReady: Boolean,
    /** Play was asked and said no, or a seat was refused for it: the hint says so instead of asking for a check. */
    val groupFailed: Boolean = false,
    /** The seat was sent and Play is being asked the way the crew will see it. */
    val seating: Boolean = false,
    /** Both Play Console boxes are ticked: the seat can't be submitted before. */
    val boxesReady: Boolean,
    val needsDrive: Boolean,
    val driveMessage: String?,
    /** An appeal is waiting on the team, so no new seat anywhere: the app and crew it is about. */
    val held: Pair<String, String>?,
    /** The server's refusal, for the ones that are not about the group or the boxes. */
    val refusal: String?,
    /** The server said tix are short, even if the balance on this phone did not. */
    val shortTix: Boolean = false,
    /** Another app of theirs already waits in a crew that has not started. */
    val waiting: SeatWaiting? = null,
    val onOpenCrew: (String) -> Unit = {},
)

/** The crew an app already waits in, and its id when the server gave one, for the link to it. */
internal class SeatWaiting(
    val crewName: String,
    val crewId: String?,
)

@Composable
internal fun CrewSeatDetails(
    state: SeatDetailsState,
    crews: CrewViewModel,
    vm: CrewAddViewModel,
    onOpenCircle: () -> Unit,
    /** Play shows the app to the testers group: the group's part is gone and its tile row leads the details. */
    groupDone: Boolean,
) {
    val short = vm.short || state.shortTix
    SeatHeading("Seat details", if (short) SeatStatus.warn(shortHeadline(vm.tix)) else null)
    if (groupDone) {
        SeatGroupDoneRow()
        Spacer(Modifier.height(16.dp))
    }
    CrewOpenHeader(crews.open)
    Spacer(Modifier.height(16.dp))
    SignInFields(
        needed = vm.needsSignIn,
        onNeeded = vm::chooseSignIn,
        username = vm.username,
        onUsername = vm::typeUsername,
        password = vm.password,
        onPassword = vm::typePassword,
        also = vm.also,
        onAlso = vm::typeAlso,
        seatSpacing = true,
    )
    Spacer(Modifier.height(16.dp))
    SeatPrice(vm.tix, short, crews.running, onOpenCircle)
    // Every notice goes under the rules, 16dp apart.
    // Each icon is the first glyph of its line, so a wrap runs back to the left edge.
    state.refusal?.let {
        Spacer(Modifier.height(16.dp))
        val tone = Tt.colors.bad
        Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
            IconLine(Lucide.CircleAlert, tone, AnnotatedString("Couldn't take the seat"), TitleLine, tone, iconSize = 16.dp)
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = tone)
        }
    }
    state.waiting?.let { waiting ->
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth()) {
            IconLine(
                Lucide.TriangleAlert,
                Tt.colors.warn,
                AnnotatedString("Your app waits in ${waiting.crewName.ifBlank { "a crew" }}"),
                TitleLine,
                Tt.colors.text,
                iconSize = 16.dp,
            )
            Text(
                "Take the next seat once that crew is full.",
                style = MaterialTheme.typography.bodySmall,
                color = Tt.colors.text2,
            )
            waiting.crewId?.let { id ->
                Text(
                    "Open the crew",
                    Modifier.clickable { state.onOpenCrew(id) }.padding(top = 6.dp, end = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Tt.colors.accentInk,
                )
            }
        }
    }
    // While an appeal waits, no new seat anywhere: the answer decides which way they go.
    state.held?.let { (appName, crewName) ->
        Spacer(Modifier.height(16.dp))
        IconLine(
            Lucide.TriangleAlert,
            Status.short,
            AnnotatedString("You asked to get $appName back into $crewName. You can take a new seat once the team answers."),
            MaterialTheme.typography.bodySmall,
            MaterialTheme.colorScheme.onSurface,
            iconSize = 16.dp,
        )
    }
}

private val GroupDoneLine = tileStyle(12.5f, 14f, FontWeight.Medium)

/** The testers group once Play shows the app to it: one quiet tile row at the top of the details. */
@Composable
private fun SeatGroupDoneRow() {
    val t = Tt.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(t.okBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.CircleCheck, null, Modifier.size(20.dp), tint = t.ok)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Testers group added", style = AppTileName, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                "Play shows your app to the group",
                style = GroupDoneLine,
                color = t.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The details heading's status when tix are short: how many, once the balance is known. */
private val TitleLine
    @Composable get() = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)

private fun shortHeadline(tix: Int?): String =
    if (tix != null && tix < CrewRules.SEAT_COST) "${CrewRules.SEAT_COST - tix} tix short" else "Not enough tix"

/** The button that takes the seat, with the Drive note and the hint above it and its message under it. The page's last part. */
@Composable
internal fun CrewSeatSubmit(
    state: SeatDetailsState,
    vm: CrewAddViewModel,
    onSpot: (SeatSpot) -> Unit,
    onTake: () -> Unit,
) {
    if (state.needsDrive) {
        DialogNote("Each night's pictures go to your own Google Drive, so it is connected before you take a seat.")
        Spacer(Modifier.height(8.dp))
    }
    // What is still missing from the page, the first thing first. Tapping it goes there.
    val hint =
        seatHint(
            groupReady = state.groupReady,
            groupFailed = state.groupFailed,
            name = vm.name,
            signInNeeded = vm.needsSignIn,
            user = vm.username,
            pass = vm.password,
            also = vm.also,
            tix = vm.tix,
            shortServer = state.shortTix,
            held = state.held != null,
            boxesReady = state.boxesReady,
            busy = state.seating,
            cost = CrewRules.SEAT_COST,
        )
    SeatHintRow(hint, onSpot)
    Spacer(Modifier.height(8.dp))
    val seat = state.crewName?.let { "Take a seat in $it" } ?: "Take a seat"
    Primary(
        if (state.needsDrive) "Connect Drive and take a seat" else seat,
        busy = state.busy,
        enabled =
            state.groupReady &&
                state.boxesReady &&
                vm.name.isNotBlank() &&
                vm.signInReady &&
                !vm.short &&
                !state.shortTix &&
                state.held == null,
        onClick = onTake,
    )
    state.driveMessage?.let {
        Spacer(Modifier.height(10.dp))
        DialogNote(it)
    }
}

private val PriceLine = tileStyle(12.5f, 14f, FontWeight.Medium)
private val PriceLineBold = tileStyle(12.5f, 14f, FontWeight.SemiBold)

/**
 * What the seat costs and what there is to pay it from, said before the button rather than as a
 * refusal after it, then the rules in three short lines, ending on what it asks of the nights: every
 * crew somebody is in is fourteen apps a night. Short of tix puts Circle one tap away at the row's end.
 */
@Composable
private fun SeatPrice(
    tix: Int?,
    short: Boolean,
    running: Int,
    onOpenCircle: () -> Unit,
) {
    val t = Tt.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(t.tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Coins, null, Modifier.size(20.dp), tint = t.tileInk)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "A seat is ${CrewRules.SEAT_COST} tix",
                style = AppTileName,
                color = t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val line =
                when {
                    tix != null && tix < CrewRules.SEAT_COST -> "You have $tix tix, ${CrewRules.SEAT_COST - tix} short"
                    short -> "Not enough tix for a seat"
                    tix != null -> "You have $tix tix"
                    else -> null
                }
            if (line != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    line,
                    style = if (short) PriceLineBold else PriceLine,
                    color = if (short) t.warn else t.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (short) {
            Spacer(Modifier.width(8.dp))
            RowPill("Open Circle", PillKind.SOFT, onClick = onOpenCircle)
        }
    }
    Spacer(Modifier.height(16.dp))
    val bold = SpanStyle(fontWeight = FontWeight.SemiBold, color = t.text)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RuleLine(
            buildAnnotatedString {
                append("All ")
                withStyle(bold) { append("${CrewRules.SEAT_COST} tix") }
                append(" come back if you take it out while the crew fills, or open every app every night to the end.")
            },
        )
        RuleLine(
            buildAnnotatedString {
                append("You open the other ")
                withStyle(bold) { append("${CrewRules.SEATS - 1}") }
                append(" apps every day for ")
                withStyle(bold) { append("${CrewRules.RUN_DAYS}") }
                append(" days, and they open yours.")
            },
        )
        RuleLine(
            buildAnnotatedString {
                append("That puts you on ")
                withStyle(bold) { append("${CrewRules.nightly(running + 1)}") }
                append(" apps a night.")
            },
        )
    }
}
