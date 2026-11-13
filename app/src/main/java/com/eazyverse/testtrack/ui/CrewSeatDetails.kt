package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AskFailure
import com.eazyverse.testtrack.viewmodel.CrewAddViewModel

/*
 * The seat sheet's last view: the details of the seat and the one filled button that takes it.
 * What the old Add to a crew page held, in the sheet: the name the crew sees, how they get past a
 * sign-in, what the seat costs, and the notes that can hold the button.
 */

/** What the details view reads from the sheet, so the sheet owns every launcher and callback. */
internal class SeatDetailsState(
    val app: AppEntry,
    /** The crew a seat goes into; null when none is filling and this seat starts one. */
    val crewName: String?,
    val busy: Boolean,
    val needsDrive: Boolean,
    val driveMessage: String?,
    /** An appeal is waiting on the team, so no new seat anywhere: the app and crew it is about. */
    val held: Pair<String, String>?,
    /** The server's refusal, for the ones that are not about a step. */
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
    onChange: () -> Unit,
    onOpenCircle: () -> Unit,
    onTake: () -> Unit,
) {
    val app = state.app
    Spacer(Modifier.height(4.dp))
    FoundApp(app.pkg, app.name, app.icon, app.developer, unchecked = false, onChange = onChange)
    Spacer(Modifier.height(14.dp))
    DialogField(
        value = vm.name,
        onValueChange = vm::rename,
        label = "Name the crew sees",
        placeholder = app.name,
        imeAction = ImeAction.Next,
        maxLength = FeedRules.APP_NAME_MAX,
    )
    Spacer(Modifier.height(20.dp))
    SignInFields(
        needed = vm.needsSignIn,
        onNeeded = vm::chooseSignIn,
        username = vm.username,
        onUsername = vm::typeUsername,
        password = vm.password,
        onPassword = vm::typePassword,
        also = vm.also,
        onAlso = vm::typeAlso,
    )
    state.refusal?.let {
        Spacer(Modifier.height(14.dp))
        AskMessage(AskFailure("Couldn't take the seat", it))
    }
    Spacer(Modifier.height(18.dp))
    SeatPrice(vm.tix, crews.running)
    if (vm.short || state.shortTix) {
        Spacer(Modifier.height(12.dp))
        ShortOfTix(onOpenCircle)
    }
    state.waiting?.let { waiting ->
        Spacer(Modifier.height(12.dp))
        Notice(Tt.colors.warn, Lucide.TriangleAlert) {
            Text(
                "Your app waits in ${waiting.crewName.ifBlank { "a crew" }}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Tt.colors.text,
            )
            Text(
                "Take the next seat once that crew is full.",
                style = MaterialTheme.typography.bodySmall,
                color = Tt.colors.text2,
            )
            waiting.crewId?.let { id ->
                Text(
                    "Open the crew",
                    Modifier.clickable { state.onOpenCrew(id) }.padding(top = 6.dp, bottom = 4.dp, end = 12.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Tt.colors.accentInk,
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    if (crews.seating) {
        DialogNote("Checking it on Play the way the crew will see it. This can take half a minute.")
        Spacer(Modifier.height(12.dp))
    }
    // While an appeal waits, no new seat anywhere: the answer decides which way they go.
    state.held?.let { (appName, crewName) ->
        Notice(Status.short, Lucide.TriangleAlert) {
            Text(
                "You asked to get $appName back into $crewName. You can take a new seat once the team answers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
    if (state.needsDrive) {
        DialogNote("Each night's pictures go to your own Google Drive, so it is connected before you take a seat.")
        Spacer(Modifier.height(12.dp))
    }
    val seat = state.crewName?.let { "Take a seat in $it" } ?: "Take a seat"
    Primary(
        if (state.needsDrive) "Connect Drive and take a seat" else seat,
        busy = state.busy,
        enabled = vm.name.isNotBlank() && vm.signInReady && !vm.short && !state.shortTix && state.held == null,
        onClick = onTake,
    )
    state.driveMessage?.let {
        Spacer(Modifier.height(10.dp))
        DialogNote(it)
    }
    Spacer(Modifier.height(24.dp))
}

/**
 * What the seat costs and what there is to pay it from, said before the button rather than as a
 * refusal after it, and what it asks of the nights: every crew somebody is in is fourteen apps a night.
 */
@Composable
private fun SeatPrice(
    tix: Int?,
    running: Int,
) {
    val short = tix != null && tix < CrewRules.SEAT_COST
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("A seat costs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Tt.colors.text)
            Spacer(Modifier.height(2.dp))
            val back = "Back in full if you take it out while the crew fills, or finish every day."
            Text(
                if (tix == null) back else "You have $tix tix. $back",
                style = MaterialTheme.typography.bodySmall,
                color = if (short) Tt.colors.bad else Tt.colors.text3,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "${CrewRules.SEAT_COST} tix",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Tt.colors.accentInk,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "You open theirs every day for ${CrewRules.RUN_DAYS} days, and they open yours. That puts you on " +
            "${CrewRules.nightly(running + 1)} apps a night.",
        style = MaterialTheme.typography.bodySmall,
        color = Tt.colors.text3,
    )
}

/** Tix spent elsewhere after "Take a seat" was tapped: said once, the button held, Circle one tap away. */
@Composable
private fun ShortOfTix(onOpenCircle: () -> Unit) {
    Notice(Tt.colors.warn, Lucide.TriangleAlert) {
        Text("Not enough tix yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Tt.colors.text)
        Text(
            "Review somebody's app in Circle to earn the rest.",
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.text2,
        )
        Text(
            "Open Circle",
            Modifier.clickable(onClick = onOpenCircle).padding(top = 6.dp, bottom = 4.dp, end = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Tt.colors.accentInk,
        )
    }
}
