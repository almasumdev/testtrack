package com.eazyverse.testtrack.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AppAction
import com.eazyverse.testtrack.viewmodel.AppsViewModel
import com.eazyverse.testtrack.viewmodel.CrewAddViewModel
import com.eazyverse.testtrack.viewmodel.TestingDriveViewModel

/** Where the seat screen is: waiting for My apps, the app's steps, or the seat's details and button. */
private enum class SeatStage { START, STEPS, DETAILS }

/**
 * Taking a seat, on a page of its own: the app picked in [CrewSeatSheet], what it still needs, then
 * the seat's details with the one filled button pinned under them.
 *
 * Everything the screen holds across a rotation or a trip to Play Console or the browser is saved:
 * the stage and the server's refusals here, the two boxes in My apps' model (which also keeps them
 * on the phone), the name and sign-in in this page's own model. The crew itself is not chosen; the
 * server puts the app into whichever is nearest to starting, and the crew's own model takes the seat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CrewSeatScreen(
    crews: CrewViewModel,
    apps: AppsViewModel,
    appId: String,
    onBack: () -> Unit,
    /** The seat is taken: leave for the Crew tab. */
    onDone: () -> Unit,
    onOpenCircle: () -> Unit,
    /** A crew's own page, for the crew an app already waits in. */
    onOpenCrew: (String) -> Unit,
    vm: CrewAddViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val loading = apps.loading || apps.reading
    val app = apps.byId(appId)

    var stage by rememberSaveable { mutableStateOf(SeatStage.START) }
    // The server's own words when Play could not see the app at the seat, and for unticked boxes.
    var notVisible by rememberSaveable { mutableStateOf<String?>(null) }
    var boxesMessage by rememberSaveable { mutableStateOf<String?>(null) }
    // The other refusals that have a place in the details: short of tix, and another app already waiting.
    var shortTix by rememberSaveable { mutableStateOf(false) }
    var waitingName by rememberSaveable { mutableStateOf<String?>(null) }
    var waitingId by rememberSaveable { mutableStateOf<String?>(null) }
    var fresh by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // A refusal or a failure from an earlier try belongs to that try, but not to a rotation.
        if (fresh) {
            fresh = false
            crews.clearSeatError()
            apps.clearActionError()
        }
        apps.attach(context)
    }
    DisposableEffect(Unit) {
        onDispose { if (!activity.isChangingConfigurations) crews.clearSeatError() }
    }
    // Decided once My apps has the app: ready goes straight to its details.
    LaunchedEffect(app?.id, loading) {
        if (stage != SeatStage.START) return@LaunchedEffect
        if (app != null) {
            stage = if (seatNeed(app, apps.boxes(app)) == SeatNeed.NONE) SeatStage.DETAILS else SeatStage.STEPS
        }
    }
    // After the process was recreated My apps starts empty: read it, rather than call the app gone.
    LaunchedEffect(appId) { if (apps.byId(appId) == null) apps.load(context) }
    // An app deleted while the screen is open leaves nothing to take a seat with.
    LaunchedEffect(app == null, loading, apps.offline) {
        if (app == null && !loading && !apps.offline) onBack()
    }
    LaunchedEffect(app?.id) { app?.let(vm::start) }

    // Every way to a seat ends here, so this is where Drive is made sure of: each night's
    // pictures go there, and without them no night counts. Asked of Google quietly first.
    val drive: TestingDriveViewModel = viewModel()
    LaunchedEffect(Unit) { drive.check(activity) }
    var afterDrive by remember { mutableStateOf<(() -> Unit)?>(null) }
    val driveSheet =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            drive.consentResult(activity, it.data) { afterDrive?.invoke() }
        }

    val boxes = app?.let(apps::boxes) ?: SeatBoxes()
    val waiting = waitingName?.let { SeatWaiting(it, waitingId) }
    val details =
        app?.let {
            SeatDetailsState(
                app = it,
                crewName = crews.open?.name,
                busy = crews.seating || drive.busy,
                needsDrive = drive.connected == false,
                driveMessage = drive.message,
                held = appealWaitingIn(crews.crews),
                refusal =
                    crews.seatError.takeIf {
                        notVisible == null && boxesMessage == null && !shortTix && waiting == null
                    },
                shortTix = shortTix,
                waiting = waiting,
                onOpenCrew = onOpenCrew,
            )
        }

    Scaffold(
        containerColor = Color.Transparent,
        // The navigation route already applies safeDrawingPadding: the bar and the keyboard are cleared.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { PageBar("Take a seat", onBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Gutter),
            ) {
                Spacer(Modifier.height(8.dp))
                if (app == null && !loading && apps.offline) {
                    Text("Check your connection, then try again.", style = MaterialTheme.typography.bodySmall, color = Tt.colors.text2)
                    Spacer(Modifier.height(12.dp))
                    SeatBar("Try again", Lucide.RefreshCw, onClick = { apps.load(context) })
                } else if (app == null || stage == SeatStage.START) {
                    SeatTileSkeleton(120.dp, 160.dp)
                } else {
                    FoundApp(app.pkg, app.name, app.icon, app.developer, unchecked = false, onChange = null)
                    Text(
                        if (stage == SeatStage.STEPS) {
                            stepsLeftLine(stepsLeft(app.verified, boxes))
                        } else {
                            "Your app goes in with ${CrewRules.SEATS - 1} others"
                        },
                        Modifier.padding(top = 2.dp, bottom = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Tt.colors.text2,
                    )
                    if (stage == SeatStage.STEPS) {
                        val facts = apps.facts[app.id]
                        val failedAt = apps.failedChecks[app.id]
                        // The newer of what the server kept and what this run was told.
                        val newestFailed = failedAt != null && (facts?.checkedAt == null || failedAt >= facts.checkedAt)
                        val checkedAt = if (newestFailed) failedAt else facts?.checkedAt
                        val checkedOk = if (newestFailed) false else facts?.checkedOk
                        CrewSeatSteps(
                            SeatStepsState(
                                app = app,
                                boxes = boxes,
                                groupOpen = !app.verified || notVisible != null,
                                checkLine = lastCheckLine(checkedAt, checkedOk, System.currentTimeMillis(), notVisible),
                                checkFailed = notVisible != null || checkedOk == false,
                                checking = apps.acting == app.id && apps.actingOn == AppAction.VERIFY,
                                error = apps.actionError.takeIf { failedAt == null },
                                boxesMessage = boxesMessage,
                            ),
                            onVerify = {
                                if (apps.acting == null) {
                                    apps.verify(context, app.id) { notVisible = null }
                                }
                            },
                            onBoxes = {
                                boxesMessage = null
                                apps.setBoxes(context, app, it)
                            },
                            onContinue = { stage = SeatStage.DETAILS },
                        )
                    } else if (details != null) {
                        CrewSeatDetails(details, crews = crews, vm = vm, onOpenCircle = onOpenCircle)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
            if (app != null && details != null && stage == SeatStage.DETAILS) {
                val take = {
                    shortTix = false
                    waitingName = null
                    waitingId = null
                    crews.takeSeat(
                        context,
                        app.pkg,
                        vm.name.trim(),
                        vm.notes.trim(),
                        appId = app.id,
                        // A refused seat goes to what it is about, in the server's own words.
                        onRefused = { error ->
                            when (error.code) {
                                "not_visible" -> {
                                    notVisible = error.message
                                    stage = SeatStage.STEPS
                                }
                                "boxes_unticked" -> {
                                    boxesMessage = error.message
                                    apps.clearBoxes(context, app)
                                    stage = SeatStage.STEPS
                                }
                                "short_tix" -> shortTix = true
                                "already_waiting" -> {
                                    waitingName = error.details["crew_name"].orEmpty()
                                    waitingId = error.details["crew_id"]
                                }
                            }
                        },
                        onDone = onDone,
                    )
                }
                // Pinned under the details, above the bar and keyboard the route already clears.
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(Tt.colors.hair))
                Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 12.dp)) {
                    CrewSeatSubmit(details, vm) {
                        if (drive.connected == true) {
                            take()
                        } else {
                            afterDrive = take
                            drive.connect(activity, driveSheet::launch, take)
                        }
                    }
                }
            }
        }
    }
}
