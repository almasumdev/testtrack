package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AskFailure
import com.eazyverse.testtrack.viewmodel.CrewAddViewModel

/**
 * Putting an app into a crew, on a page as Ask for feedback is: the link, the app Play has for it
 * shown back to confirm, then what the crew needs to get in, and the seat.
 *
 * The crew itself is not chosen. The server puts the app into whichever crew is nearest to
 * starting; the crew's own model takes the seat, so the Crew tab shows it on the way back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewAddScreen(
    crews: CrewViewModel,
    /** The app from My apps going in; null while My apps has not loaded it, or if it is gone. */
    app: AppEntry?,
    onDone: () -> Unit,
    onBack: () -> Unit,
    /** My apps is still arriving, so a missing app is not gone yet. */
    loading: Boolean = false,
    /** My apps could not be read, so a missing app may not be gone at all. */
    offline: Boolean = false,
    onRetry: () -> Unit = {},
    vm: CrewAddViewModel = viewModel(),
) {
    val context = LocalContext.current
    val dismissKeyboard = rememberDismissKeyboard()
    val open = crews.open?.name
    // A refusal from an earlier try belongs to that try.
    LaunchedEffect(Unit) { crews.clearSeatError() }
    LaunchedEffect(app?.id) { app?.let(vm::start) }
    Scaffold(
        containerColor = Color.Transparent,
        // The navigation route already applies safeDrawingPadding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { PageBar(if (open != null) "Take a seat in $open" else "Start a new crew", onBack) },
    ) { padding ->
        val scroll = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .dismissKeyboardOnTap(dismissKeyboard)
                .imePadding()
                // Only with the app: the not-found state fills the page, which a scroll cannot hold.
                .then(if (app != null) Modifier.verticalScroll(scroll) else Modifier)
                .padding(horizontal = Gutter),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Your app goes in with ${CrewRules.SEATS - 1} others. You open theirs every day for " +
                    "${CrewRules.RUN_DAYS} days, and they open yours. That puts you on " +
                    "${CrewRules.nightly(crews.running + 1)} apps a day.",
                style = MaterialTheme.typography.bodyMedium,
                color = Tt.colors.text2,
            )
            Spacer(Modifier.height(20.dp))
            if (app == null) {
                if (loading) FoundAppLoading() else AppNotThere(offline, onRetry, onBack)
            } else {
                // Found and confirmed when it was added; "Change" goes back to choose another.
                FoundApp(app.pkg, app.name, app.icon, app.developer, unchecked = !app.verified, onChange = onBack)
                if (!app.verified) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Members will try installing it. Two who get it verify it for good.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Tt.colors.text3,
                    )
                }
                Spacer(Modifier.height(18.dp))
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
                crews.seatError?.let {
                    Spacer(Modifier.height(14.dp))
                    AskMessage(AskFailure("Couldn't take the seat", it))
                }
                Spacer(Modifier.height(18.dp))
                SeatPrice(vm.tix)
                Spacer(Modifier.height(14.dp))
                if (crews.seating) {
                    DialogNote("Checking it on Play the way the crew will see it. This can take half a minute.")
                    Spacer(Modifier.height(12.dp))
                }
                Primary(
                    "Take a seat",
                    busy = crews.seating,
                    enabled = vm.name.isNotBlank() && vm.signInReady && !vm.short,
                    tall = true,
                ) {
                    crews.takeSeat(context, app.pkg, vm.name.trim(), vm.notes.trim(), appId = app.id, onDone = onDone)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * What the seat costs and what there is to pay it from, said before the button rather than as a
 * refusal after it. Short of it, the way to more: reviews are what earn tix.
 */
@Composable
private fun SeatPrice(tix: Int?) {
    val short = tix != null && tix < CrewRules.SEAT_COST
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("A seat costs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Tt.colors.text)
            Spacer(Modifier.height(2.dp))
            Text(
                if (tix ==
                    null
                ) {
                    "Back in full if your app leaves before the run."
                } else {
                    "You have $tix tix. Back in full if your app leaves before the run."
                },
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
    if (short) {
        Spacer(Modifier.height(10.dp))
        AskMessage(AskFailure("Not enough tix yet", "Review somebody's app in Circle to earn the rest."))
    }
}
