package com.eazyverse.testtrack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.BadgeCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Smartphone
import com.eazyverse.testtrack.viewmodel.AppAddViewModel

/**
 * Adding an app to My apps, on two pages.
 *
 * The start page asks where people try it, gives a closed test the testers group to add, and takes
 * the app either way: picked from this phone, or a Play link or package to find. The second page is
 * the app found or picked, with what it is about, and Add to My apps. Back from it keeps what was
 * typed. The button follows the page while it is short and stays pinned once the page runs longer.
 *
 * Nothing is spent here. The app is remembered, and its page is where it goes into a crew or up
 * for feedback. Both ways bring the app's real icon and name; typing them is gone, since that is
 * how blank squares got in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppAddScreen(
    /** Added: its id, and whether it was on My apps already. */
    onAdded: (AppEntry) -> Unit,
    onBack: () -> Unit,
    vm: AppAddViewModel = viewModel(),
) {
    val context = LocalContext.current
    val dismissKeyboard = rememberDismissKeyboard()
    // Straight to the gallery: the phone's own icon is already the one shown, so a sheet offering
    // it again only closed. Backing out leaves the icon as it was.
    val chooseIcon = rememberImagePicker { uri -> uri?.let { vm.usePicture(context, it) } }
    // Back from the app goes to the start page before it leaves this screen.
    BackHandler(enabled = !vm.starting) { vm.back() }

    Scaffold(
        containerColor = Color.Transparent,
        // The navigation route already applies safeDrawingPadding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                PageBar("Add an app", onBack = { if (!vm.back()) onBack() })
                PageMessage(
                    vm.addError ?: vm.iconError,
                    onDone = { if (vm.addError != null) vm.clearAddError() else vm.clearIconError() },
                    error = true,
                )
            }
        },
    ) { padding ->
        // Each page starts at its top.
        val scroll = key(vm.starting) { rememberScrollState() }
        // Taller than its room: the button stays put over the page's faded foot.
        val pinned = scroll.maxValue in 1 until Int.MAX_VALUE
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .dismissKeyboardOnTap(dismissKeyboard)
                .imePadding(),
        ) {
            Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scroll)
                        .padding(horizontal = Gutter)
                        .then(if (vm.adding) Modifier.alpha(DISABLED_ALPHA) else Modifier),
                ) {
                    Spacer(Modifier.height(8.dp))
                    if (vm.starting) AddStart(vm) else AddApp(vm, chooseIcon)
                    // Scrolled to the end, the last block stops 12dp over the 18dp foot fade, which
                    // sits on the button. Always there, so pinning never flickers and the end of a
                    // growing field has real room to scroll into.
                    Spacer(Modifier.height(30.dp))
                }
                if (pinned) FootFade(Modifier.align(Alignment.BottomCenter))
            }
            val padded = Modifier.padding(horizontal = Gutter)
            if (vm.starting) {
                Primary(
                    when {
                        vm.looking -> "Finding your app"
                        vm.failure != null -> "Try again"
                        else -> "Find my app"
                    },
                    padded,
                    busy = vm.looking,
                    enabled = vm.canLook,
                    onClick = vm::look,
                )
            } else {
                Primary(
                    "Add to My apps",
                    padded,
                    busy = vm.adding,
                    enabled = vm.picked == null || vm.canAddPicked,
                ) { vm.add { app, _ -> onAdded(app) } }
            }
            Spacer(Modifier.height(6.dp))
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

/**
 * The start page: where people try it, the testers group for a closed test, then the app, picked
 * from this phone or found by its Play link, and why Play could not find it.
 */
@Composable
private fun AddStart(vm: AppAddViewModel) {
    val context = LocalContext.current
    var how by rememberSaveable { mutableStateOf(false) }
    AskAvailability(vm.isLive, vm::choose)
    if (!vm.isLive) {
        SectionHeading("First, add our testers group", top = 24.dp)
        // The copy button's 40dp reaches into the card's edge, as the address row is drawn.
        CrewCard(padding = PaddingValues(start = 16.dp, top = 12.dp, end = 6.dp, bottom = 6.dp)) {
            SeatAddressRow()
            InlineAction("How to add it", Modifier.offset(x = (-8).dp)) { how = true }
        }
    }
    SectionHeading("Your app", top = 24.dp)
    AppStartRow(
        Lucide.Smartphone,
        "Pick from this phone",
        "Works before Play is ready",
        onClick = { vm.pickFromPhone(context) },
        pill = "Pick",
    )
    QuietNote("or", Modifier.padding(top = 10.dp, bottom = 12.dp))
    OutlinedField(
        value = vm.query,
        onValueChange = vm::type,
        label = "Play link or package name",
        placeholder = "com.example.myapp",
        isError = vm.failure?.invalidLink == true,
        keyboardType = KeyboardType.Uri,
        imeAction = ImeAction.Search,
        onImeAction = vm::look,
        enabled = !vm.looking,
    )
    vm.failure?.let {
        Spacer(Modifier.height(12.dp))
        FailureLine(it)
    }
    if (how) GroupHowToDialog(onDismiss = { how = false })
}

/** The app found on Play or picked from this phone, and what it is about. */
@Composable
private fun AddApp(
    vm: AppAddViewModel,
    onChangeIcon: () -> Unit,
) {
    val context = LocalContext.current
    val listing = vm.found
    val picked = vm.picked
    if (listing != null) {
        AppHero(
            name = listing.name,
            pkg = listing.`package`,
            live = vm.isLive,
            // Play's icon is the one it has; nothing here changes it.
            onChangeIcon = null,
            icon = { AppMark(listing.`package`, listing.name, listing.icon, size = AppHeroIcon, ownIcon = true) },
            chips = { StatusChip("Found on Play", Tone.OK, Lucide.BadgeCheck, size = ChipSize.FACT) },
            below = { ChooseAnother { vm.back() } },
        )
        PlayAbout(listing.shortDescription, listing.fullDescription)
    } else if (picked != null) {
        PickedApp(
            pkg = picked.pkg,
            name = vm.pickedName,
            preview = vm.pickedPreview,
            live = vm.isLive,
            testBuild = picked.testBuild,
            onPickAnother = { vm.openPicker(context) },
            onChangeIcon = onChangeIcon,
            onRename = vm::renamePicked,
        )
        PickedNotice(testBuild = picked.testBuild, live = vm.isLive)
        // No Play page yet, so the owner can write them.
        SectionHeading("About the app", tag = "Optional", top = 24.dp, tagBeside = true)
        Spacer(Modifier.height(4.dp))
        AboutFields(vm.shortDesc, vm::writeShort, vm.fullDesc, vm::writeFull, enabled = !vm.adding)
    }
}
