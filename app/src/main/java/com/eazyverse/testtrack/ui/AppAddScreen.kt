package com.eazyverse.testtrack.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AddWay
import com.eazyverse.testtrack.viewmodel.AppAddViewModel

/**
 * Adding an app to My apps: picked from this phone, or found on Play by its link, then added.
 *
 * Nothing is spent here. The app is remembered, and its page is where it goes into a crew or up
 * for feedback, so it is found and confirmed once rather than at every step after. Both ways bring
 * the app's real icon and name; typing them is gone, since that is how blank squares got in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppAddScreen(
    /** Added: its id, and whether it was on My apps already. */
    onAdded: (String) -> Unit,
    onBack: () -> Unit,
    vm: AppAddViewModel = viewModel(),
) {
    val context = LocalContext.current
    val dismissKeyboard = rememberDismissKeyboard()
    val picture =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { vm.usePicture(context, it) }
        }
    // Back walks the steps of this page before it leaves it.
    BackHandler(enabled = vm.way != null) { vm.back() }

    Scaffold(
        containerColor = Color.Transparent,
        // The navigation route already applies safeDrawingPadding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            PageBar("Add an app", onBack = { if (!vm.back()) onBack() })
        },
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
            when (vm.way) {
                null -> {
                    AddWayChoice(vm.choice, vm::select)
                    Spacer(Modifier.height(22.dp))
                    Primary("Continue", tall = true) { vm.proceed(context) }
                }
                AddWay.PHONE -> {
                    // Only reached once an app is picked: the list itself is the sheet below.
                    val app = vm.picked
                    if (app != null) {
                        Spacer(Modifier.height(8.dp))
                        PickedApp(
                            pkg = app.pkg,
                            name = vm.pickedName,
                            preview = vm.pickedPreview,
                            live = vm.isLive,
                            testBuild = app.testBuild,
                            onPickAnother = { vm.openPicker(context) },
                            // Straight to the gallery: the phone's own icon is already the one shown, so a
                            // sheet offering it again only closed. Backing out leaves the icon as it was.
                            onChangeIcon = { picture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onRename = vm::renamePicked,
                        )
                        vm.iconError?.let {
                            Spacer(Modifier.height(12.dp))
                            AskMessage(AskFailure("Couldn't use that picture", it))
                        }
                        Spacer(Modifier.height(22.dp))
                        AskAvailability(vm.isLive, vm::choose)
                        Spacer(Modifier.height(12.dp))
                        PickedNotice(testBuild = app.testBuild)
                        AddButton(vm, enabled = vm.canAddPicked, onAdded)
                    }
                }
                AddWay.PLAY -> {
                    AskAvailability(vm.isLive, vm::choose)
                    Spacer(Modifier.height(16.dp))
                    val app = vm.found
                    if (app == null) {
                        Finding(vm) { vm.pickInstead(context) }
                    } else {
                        FoundApp(app.`package`, app.name, app.icon, app.developer, unchecked = false, onChange = vm::startOver)
                        AddButton(vm, enabled = true, onAdded)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (vm.picking) {
        PhoneAppSheet(
            apps = vm.shownPhoneApps.takeIf { vm.phoneApps != null },
            search = vm.search,
            onSearch = vm::searchFor,
            picked = vm.picked?.pkg,
            onPick = { vm.pick(context, it) },
            onDismiss = vm::closePicker,
        )
    }
}

@Composable
private fun AddButton(
    vm: AppAddViewModel,
    enabled: Boolean,
    onAdded: (String) -> Unit,
) {
    vm.addError?.let {
        Spacer(Modifier.height(14.dp))
        AskMessage(AskFailure("Couldn't add it", it))
    }
    Spacer(Modifier.height(20.dp))
    Primary("Add to My apps", busy = vm.adding, enabled = enabled, tall = true) {
        vm.add { id, _ -> onAdded(id) }
    }
}

/** Before an app is found on Play: the link and the button, and the phone when Play cannot help. */
@Composable
private fun Finding(
    vm: AppAddViewModel,
    onPickInstead: () -> Unit,
) {
    Text(
        "Paste your Google Play link or type the package name.",
        style = MaterialTheme.typography.bodyMedium,
        color = Tt.colors.text2,
    )
    Spacer(Modifier.height(12.dp))
    DialogField(
        value = vm.query,
        onValueChange = vm::type,
        label = "Play link or package name",
        placeholder = "com.example.myapp",
        isError = vm.failure?.invalidLink == true,
        supporting =
            if (vm.isLive) {
                null
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
    Spacer(Modifier.height(16.dp))
    // Our check is down, or Play is slow to show a closed track: the phone has the app anyway.
    if (vm.failure?.byHand == true) {
        Secondary("Pick it from this phone instead", Modifier.fillMaxWidth(), onClick = onPickInstead)
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
