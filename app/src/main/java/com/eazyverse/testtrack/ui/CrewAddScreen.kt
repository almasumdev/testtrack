package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.ui.theme.Tt
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
    onDone: () -> Unit,
    onBack: () -> Unit,
    vm: CrewAddViewModel = viewModel(),
) {
    val context = LocalContext.current
    val dismissKeyboard = rememberDismissKeyboard()
    val open = crews.open?.name
    // A refusal from an earlier try belongs to that try.
    LaunchedEffect(Unit) { crews.clearSeatError() }
    Scaffold(
        containerColor = Color.Transparent,
        // The navigation route already applies safeDrawingPadding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { PageBar(if (open != null) "Take a seat in $open" else "Start a new crew", onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .dismissKeyboardOnTap(dismissKeyboard)
                .imePadding()
                .verticalScroll(rememberScrollState())
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
            val app = vm.found
            if (app == null) {
                Finding(vm)
            } else {
                FoundApp(app.pkg, app.name, app.icon, app.developer, unchecked = app.unchecked, onChange = vm::startOver)
                Spacer(Modifier.height(18.dp))
                DialogField(
                    value = vm.name,
                    onValueChange = vm::rename,
                    label = "Name the crew sees",
                    placeholder = app.name,
                    imeAction = ImeAction.Next,
                    maxLength = FeedRules.APP_NAME_MAX,
                )
                Spacer(Modifier.height(14.dp))
                DialogField(
                    value = vm.notes,
                    onValueChange = vm::typeNotes,
                    label = "Getting in (optional)",
                    placeholder = "Test account: demo@example.com / demo",
                    supporting =
                        "A login or code if yours needs one. Fourteen strangers read this, so make " +
                            "it a throwaway.",
                    singleLine = false,
                    minHeight = 72.dp,
                    maxLength = 280,
                )
                crews.seatError?.let {
                    Spacer(Modifier.height(14.dp))
                    AskMessage(AskFailure("Couldn't take the seat", it))
                }
                Spacer(Modifier.height(18.dp))
                if (crews.seating) {
                    DialogNote("Checking it on Play the way the crew will see it. This can take half a minute.")
                    Spacer(Modifier.height(12.dp))
                }
                Primary(
                    "Take a seat",
                    busy = crews.seating,
                    enabled = vm.name.isNotBlank(),
                    tall = true,
                ) {
                    crews.takeSeat(context, app.pkg, vm.name.trim(), vm.notes.trim(), onDone)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Before an app is found: the link and the button, and the way past a checker that is down. */
@Composable
private fun Finding(vm: CrewAddViewModel) {
    DialogField(
        value = vm.query,
        onValueChange = vm::type,
        label = "Your closed testing link",
        placeholder = "com.example.myapp",
        isError = vm.failure?.invalidLink == true,
        supporting = "The Play link or package name. Your closed track needs the testers group added.",
        singleLine = true,
        keyboardType = KeyboardType.Uri,
        imeAction = ImeAction.Search,
        onImeAction = vm::look,
    )
    vm.failure?.let {
        Spacer(Modifier.height(14.dp))
        AskMessage(it)
    }
    if (vm.byHand) {
        Spacer(Modifier.height(18.dp))
        ByHandForm(vm)
        return
    }
    Spacer(Modifier.height(16.dp))
    if (vm.failure?.byHand == true) {
        Secondary("Enter details yourself", Modifier.fillMaxWidth(), onClick = vm::enterByHand)
        Spacer(Modifier.height(10.dp))
    }
    Primary(
        when {
            vm.looking -> "Finding your app..."
            vm.failure != null -> "Try again"
            else -> "Find my app"
        },
        busy = vm.looking,
        enabled = vm.canLook,
        tall = true,
        onClick = vm::look,
    )
}
