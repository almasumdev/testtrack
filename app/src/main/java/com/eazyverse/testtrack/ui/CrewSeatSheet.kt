package com.eazyverse.testtrack.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AppAction
import com.eazyverse.testtrack.viewmodel.AppsViewModel
import com.eazyverse.testtrack.viewmodel.CrewAddViewModel
import com.eazyverse.testtrack.viewmodel.TestingDriveViewModel

/** Where the seat sheet is: the list, one app's steps, or the seat details. START waits for My apps. */
private enum class SeatStage { START, LIST, STEPS, DETAILS }

/**
 * Taking a seat, in one sheet: which app goes in, what that app still needs, then the seat's
 * details and the button. Opened by every Take a seat, after the testers group is checked.
 *
 * [startAppId] opens it on that app (from its page, or to put an app back in): its steps if it is
 * not ready, its details if it is. The crew itself is not chosen; the server puts the app into
 * whichever is nearest to starting, and the crew's own model takes the seat, so the Crew tab shows
 * it on the way back.
 */
@Composable
internal fun CrewSeatSheet(
    crews: CrewViewModel,
    apps: AppsViewModel,
    startAppId: String? = null,
    onAddApp: () -> Unit,
    onOpenCircle: () -> Unit,
    onDismiss: () -> Unit,
    /** A crew's own page, for the crew an app already waits in. */
    onOpenCrew: (String) -> Unit = {},
    vm: CrewAddViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val open = crews.open?.name
    val loading = apps.loading || apps.reading

    var stage by remember { mutableStateOf(if (startAppId == null) SeatStage.LIST else SeatStage.START) }
    var pickedId by remember { mutableStateOf(startAppId) }
    // The server's own words when Play could not see the app at the seat, and for unticked boxes.
    var notVisible by remember { mutableStateOf<String?>(null) }
    var boxesMessage by remember { mutableStateOf<String?>(null) }
    // The other refusals that have a place in the details: short of tix, and another app already waiting.
    var shortTix by remember { mutableStateOf(false) }
    var waitingIn by remember { mutableStateOf<SeatWaiting?>(null) }
    val app = pickedId?.let(apps::byId)

    LaunchedEffect(Unit) {
        // A refusal or a failure from an earlier try belongs to that try.
        crews.clearSeatError()
        apps.clearActionError()
        apps.attach(context)
    }
    DisposableEffect(Unit) {
        onDispose {
            vm.reset()
            crews.clearSeatError()
        }
    }
    // Opened on an app: decided once My apps has it. Ready goes straight to its details.
    LaunchedEffect(app?.id, loading) {
        if (stage != SeatStage.START) return@LaunchedEffect
        if (app != null) {
            stage = if (seatNeed(app, apps.boxes(app)) == SeatNeed.NONE) SeatStage.DETAILS else SeatStage.STEPS
        } else if (!loading) {
            stage = SeatStage.LIST
        }
    }
    // An app deleted while the sheet is open leaves nothing to show a step for.
    LaunchedEffect(app == null, loading) {
        if (app == null && !loading && (stage == SeatStage.STEPS || stage == SeatStage.DETAILS)) stage = SeatStage.LIST
    }

    // Every way to a seat ends here, so this is where Drive is made sure of: each night's
    // pictures go there, and without them no night counts. Asked of Google quietly first.
    val drive: TestingDriveViewModel = viewModel()
    LaunchedEffect(Unit) { drive.check(activity) }
    var afterDrive by remember { mutableStateOf<(() -> Unit)?>(null) }
    val driveSheet =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            drive.consentResult(activity, it.data) { afterDrive?.invoke() }
        }

    val inApp = (stage == SeatStage.STEPS || stage == SeatStage.DETAILS) && app != null
    val boxes = app?.let(apps::boxes) ?: SeatBoxes()
    PickerSheet(
        title =
            when {
                inApp && stage == SeatStage.STEPS -> app?.name.orEmpty()
                inApp -> open?.let { "Take a seat in $it" } ?: "Take a seat"
                else -> "Which app goes in?"
            },
        subtitle =
            when {
                inApp && stage == SeatStage.STEPS -> app?.let { stepsLeftLine(stepsLeft(it.verified, boxes)) }
                inApp -> "Your app goes in with ${CrewRules.SEATS - 1} others"
                loading && apps.apps.isEmpty() -> "Getting your apps."
                apps.offline && apps.apps.isEmpty() -> "Couldn't load your apps."
                apps.apps.isEmpty() -> "You haven't added an app yet."
                else -> open?.let { "Take a seat in $it" } ?: "Start a new crew"
            },
        onDismiss = onDismiss,
        tall = false,
        skipHalf = true,
        fitMax = 0.9f,
        leading = if (inApp) ({ BackMark { stage = SeatStage.LIST } }) else null,
        titleLiveRegion = true,
    ) { close ->
        BackHandler(enabled = inApp) { stage = SeatStage.LIST }
        // The sheet is as tall as its content, so a change of view grows or shrinks it smoothly.
        Column(Modifier.weight(1f, fill = false).animateContentSize()) {
            when {
                inApp && stage == SeatStage.STEPS && app != null ->
                    key(SeatStage.STEPS) {
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            val facts = apps.facts[app.id]
                            val failedAt = apps.failedChecks[app.id]
                            // The newer of what the server kept and what this run was told.
                            val newestFailed = failedAt != null && (facts?.checkedAt == null || failedAt >= facts.checkedAt)
                            val checkedAt = if (newestFailed) failedAt else facts?.checkedAt
                            val checkedOk = if (newestFailed) false else facts?.checkedOk
                            val checking = apps.acting == app.id && apps.actingOn == AppAction.VERIFY
                            CrewSeatSteps(
                                SeatStepsState(
                                    app = app,
                                    boxes = boxes,
                                    groupOpen = !app.verified || notVisible != null,
                                    checkLine = lastCheckLine(checkedAt, checkedOk, System.currentTimeMillis(), notVisible),
                                    checkFailed = notVisible != null || checkedOk == false,
                                    checking = checking,
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
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                inApp && app != null ->
                    key(SeatStage.DETAILS) {
                        LaunchedEffect(app.id) { vm.start(app) }
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            val take = {
                                shortTix = false
                                waitingIn = null
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
                                            "already_waiting" ->
                                                waitingIn = SeatWaiting(error.details["crew_name"].orEmpty(), error.details["crew_id"])
                                        }
                                    },
                                    onDone = { close {} },
                                )
                            }
                            val needsDrive = drive.connected == false
                            CrewSeatDetails(
                                SeatDetailsState(
                                    app = app,
                                    crewName = open,
                                    busy = crews.seating || drive.busy,
                                    needsDrive = needsDrive,
                                    driveMessage = drive.message,
                                    held = appealWaitingIn(crews.crews),
                                    refusal =
                                        crews.seatError.takeIf {
                                            notVisible == null &&
                                                boxesMessage == null &&
                                                !shortTix &&
                                                waitingIn == null
                                        },
                                    shortTix = shortTix,
                                    waiting = waitingIn,
                                    onOpenCrew = { id -> close { onOpenCrew(id) } },
                                ),
                                crews = crews,
                                vm = vm,
                                onChange = { stage = SeatStage.LIST },
                                onOpenCircle = { close(onOpenCircle) },
                                onTake = {
                                    if (drive.connected == true) {
                                        take()
                                    } else {
                                        afterDrive = take
                                        drive.connect(activity, driveSheet::launch, take)
                                    }
                                },
                            )
                        }
                    }
                else ->
                    key(SeatStage.LIST) {
                        CrewSeatList(
                            crews = crews,
                            apps = apps,
                            waiting = stage == SeatStage.START || (loading && apps.apps.isEmpty()),
                            chosenId = pickedId,
                            onPick = { picked ->
                                pickedId = picked.id
                                notVisible = null
                                boxesMessage = null
                                shortTix = false
                                waitingIn = null
                                stage = if (seatNeed(picked, apps.boxes(picked)) == SeatNeed.NONE) SeatStage.DETAILS else SeatStage.STEPS
                            },
                            onAdd = { close(onAddApp) },
                            onRetry = { apps.load(context) },
                        )
                    }
            }
        }
    }
}

/** The way back to the list, in the title row: Material's own arrow_back_ios_new, as every page bar has it. */
@Composable
private fun BackMark(onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = "Back to your apps", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_arrow_back_ios_new), "Back", Modifier.size(20.dp), tint = Tt.colors.text)
    }
}

/** One app in the list, with what decides its row: why it can't go in, or what it needs next. */
private class SeatRow(
    val app: AppEntry,
    val block: SeatBlock?,
    val need: SeatNeed,
) {
    /** Ready ones first, then those a step or two away, then those that can't go in. */
    val rank: Int get() =
        if (block != null) {
            2
        } else if (need == SeatNeed.NONE) {
            0
        } else {
            1
        }
}

/** "Which app goes in?": your apps, ready first, then those that can't go in, and a way to add another. */
@Composable
private fun ColumnScope.CrewSeatList(
    crews: CrewViewModel,
    apps: AppsViewModel,
    waiting: Boolean,
    chosenId: String?,
    onPick: (AppEntry) -> Unit,
    onAdd: () -> Unit,
    onRetry: () -> Unit,
) {
    val t = Tt.colors
    val scroll = rememberScrollState()
    val failed = !waiting && apps.apps.isEmpty() && apps.offline
    Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) {
        when {
            waiting ->
                // Three tiles, not SKELETON_ROWS: this sheet sizes itself to its content, and ten
                // would open it tall and then shrink it once the apps arrive.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeatTileSkeleton(120.dp, 160.dp)
                    SeatTileSkeleton(96.dp, 182.dp)
                    SeatTileSkeleton(134.dp, 146.dp)
                }
            failed ->
                Row(Modifier.padding(horizontal = 2.dp), verticalAlignment = Alignment.Top) {
                    Icon(Lucide.TriangleAlert, null, Modifier.padding(top = 1.dp).size(16.dp), tint = t.warn)
                    Spacer(Modifier.width(8.dp))
                    Text("Check your connection, then try again.", style = SeatNoteStyle, color = t.text2)
                }
            apps.apps.isEmpty() ->
                Text(
                    "Add the app you want tested. It needs a closed test on Play.",
                    Modifier.padding(horizontal = 2.dp),
                    style = SeatNoteStyle,
                    color = t.text2,
                )
            else -> {
                val rows =
                    apps.apps
                        .map {
                            SeatRow(
                                it,
                                seatBlock(it, apps.facts[it.id], appealCrewFor(crews.crews, it.pkg)),
                                seatNeed(it, apps.boxes(it)),
                            )
                        }.sortedBy { it.rank }
                val (blocked, ready) = rows.partition { it.block != null }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ready.forEach { row ->
                        SeatAppTile(
                            app = row.app,
                            line = row.need.line(),
                            mark = row.need.mark,
                            blocked = false,
                            chosen = row.app.id == chosenId,
                            onClick = { onPick(row.app) },
                        )
                    }
                }
                if (blocked.isNotEmpty()) {
                    Text(
                        "Can't go in now",
                        Modifier.padding(start = 2.dp, top = 16.dp, bottom = 8.dp),
                        style = SeatNoteStyle.copy(fontSize = 13.sp, lineHeight = 16.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = t.text3,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        blocked.forEach { row ->
                            val block = row.block ?: return@forEach
                            SeatAppTile(app = row.app, line = block.line, mark = block.mark, blocked = true)
                        }
                    }
                }
            }
        }
    }
    // A hairline over the bar only while there is more list than shows.
    if (scroll.canScrollForward || scroll.canScrollBackward) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.hair))
    }
    Spacer(Modifier.height(12.dp))
    if (failed) {
        SeatBar("Try again", Lucide.RefreshCw, onClick = onRetry)
    } else {
        SeatBar("Add a new app", Lucide.Plus, onClick = onAdd)
    }
    Spacer(Modifier.height(16.dp))
}

private val SeatNoteStyle = tileStyle(13f, 18f, FontWeight.Normal)
