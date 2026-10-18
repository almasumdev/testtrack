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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AppAddViewModel

/**
 * Adding an app to My apps: where people find it, its link, Play's answer to confirm, and add.
 *
 * Nothing is spent here. The app is remembered, and its page is where it goes into a crew or up
 * for feedback, so it is found and confirmed once rather than at every step after.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppAddScreen(
    /** Added: its id, and whether it was on My apps already. */
    onAdded: (String) -> Unit,
    onBack: () -> Unit,
    vm: AppAddViewModel = viewModel(),
) {
    val dismissKeyboard = rememberDismissKeyboard()
    Scaffold(
        containerColor = Color.Transparent,
        // The navigation route already applies safeDrawingPadding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { PageBar("Add an app", onBack) },
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
                "Add it once. Then, from its page, put it in a crew for Play's closed test or ask " +
                    "other developers for feedback.",
                style = MaterialTheme.typography.bodyMedium,
                color = Tt.colors.text2,
            )
            Spacer(Modifier.height(20.dp))
            AskAvailability(vm.isLive, vm::choose)
            Spacer(Modifier.height(16.dp))
            val app = vm.found
            if (app == null) {
                Finding(vm)
            } else {
                FoundApp(app.`package`, app.name, app.icon, app.developer, unchecked = vm.unchecked, onChange = vm::startOver)
                vm.addError?.let {
                    Spacer(Modifier.height(14.dp))
                    AskMessage(AskFailure("Couldn't add it", it))
                }
                Spacer(Modifier.height(20.dp))
                Primary("Add to My apps", busy = vm.adding, tall = true) {
                    vm.add { id, _ -> onAdded(id) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Before an app is found: the link and the button, and the way past a checker that is down. */
@Composable
private fun Finding(vm: AppAddViewModel) {
    DialogField(
        value = vm.query,
        onValueChange = vm::type,
        label = "Your Google Play link",
        placeholder = "com.example.myapp",
        isError = vm.failure?.invalidLink == true,
        supporting =
            if (vm.isLive) {
                "The Google Play link or your app's package name."
            } else {
                "Add the testers group to your closed track before looking up your app."
            },
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
    // Our check is down, or Play is slow to show a closed track: the way on that does not need it.
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
