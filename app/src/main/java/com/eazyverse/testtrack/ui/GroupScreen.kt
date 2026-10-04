package com.eazyverse.testtrack.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.data.*
import com.eazyverse.testtrack.findActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    groupId: String,
    groupName: String,
    feedbackForAppId: String = "",
    onOpenDashboard: (String, String) -> Unit,
    onBack: () -> Unit,
    vm: GroupViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(Unit) { CaptureService.restorePending(context) }

    LaunchedEffect(groupId) { vm.load(groupId) }

    var canReturn by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    var reporting by remember { mutableStateOf<TestApp?>(null) }

    var givingFeedback by remember { mutableStateOf<TestApp?>(null) }

    var feedbackAutoOpened by remember { mutableStateOf(false) }
    LaunchedEffect(vm.toTest) {
        if (!feedbackAutoOpened && feedbackForAppId.isNotBlank()) {
            vm.toTest.firstOrNull { it.id == feedbackForAppId }?.let {
                givingFeedback = it
                feedbackAutoOpened = true
            }
        }
    }

    var editingNotes by remember { mutableStateOf<TestApp?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    canReturn = Settings.canDrawOverlays(context)
                    Session.refreshUsageAccess(context)
                    InstalledApps.refresh(context)
                    vm.load(groupId)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)

        val installs =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context?,
                    i: Intent?,
                ) = InstalledApps.refresh(context)
            }
        ContextCompat.registerReceiver(
            context,
            installs,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { context.unregisterReceiver(installs) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!activity.isChangingConfigurations && !CaptureService.roundActive) {
                CaptureService.endSession(context)
            }
        }
    }

    LaunchedEffect(vm.doneToday, vm.toTest, CaptureService.roundActive) {
        if (vm.toTest.isNotEmpty() &&
            vm.doneToday.size == vm.toTest.size &&
            !CaptureService.roundActive
        ) {
            CaptureService.endSession(context)
        }
    }

    var needsDrive by rememberSaveable { mutableStateOf(false) }
    var pendingDrivePackages by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(CaptureService.results.size, vm.toTest, Session.driveConnected) {
        if (CaptureService.results.isNotEmpty() && !Session.driveConnected) {
            needsDrive = true
            return@LaunchedEffect
        }
        CaptureService.results.forEach { (pkg, capture) ->
            vm.toTest.firstOrNull { it.packageName == pkg }?.let { vm.publish(activity, it, capture) }
        }
    }

    // A visit that came back without a picture is the one failure a tester cannot see. The app
    // opened, the ring emptied, TestTrack came back, and the row simply did not move. Said by name
    // here, because "nothing happened" is not something a list can express. The usage still
    // counted, though, so the row now also offers a way to supply a picture by hand — see
    // CaptureService.missed and TestRow's canPickScreenshot.
    LaunchedEffect(CaptureService.newlyMissed.size, vm.toTest) {
        val names =
            CaptureService.newlyMissed.map { visit ->
                vm.toTest.firstOrNull { it.packageName == visit.pkg }?.label ?: visit.pkg
            }
        if (names.isNotEmpty()) {
            val rest = names.size - 1
            vm.message =
                if (rest == 0) {
                    "We couldn't get a picture of ${names[0]}. Your time there still " +
                        "counted. Pick a screenshot for it below, or open it again."
                } else {
                    "We couldn't get a picture of ${names[0]} and $rest more. Your time in them " +
                        "still counted. Pick a screenshot for each below, or open them again."
                }
            CaptureService.forgetNewlyMissed()
        }
    }

    // Each queued app's sign-in note, for the overlay's copy button — see VisitOverlay.show and
    // TestApp.notes. Looked up fresh at the moment a round starts rather than carried on the
    // package list itself, since the queue is just names all the way down to the service.
    fun notesFor(packages: List<String>): Map<String, String> =
        vm.toTest
            .filter { it.packageName in packages && it.notes.isNotBlank() }
            .associate { it.packageName to it.notes }

    // Consent is one sheet for the whole round; the queue is held until it comes back approved.
    var queued by remember { mutableStateOf<List<String>>(emptyList()) }
    val consentLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                CaptureService.startSession(context, result.resultCode, data)
                if (queued.isNotEmpty()) CaptureService.startRound(context, queued, notesFor(queued))
            }
            queued = emptyList()
        }

    fun run(packages: List<String>) {
        if (packages.isEmpty()) return
        if (!Session.driveConnected) {
            pendingDrivePackages = packages
            needsDrive = true
            return
        }
        if (CaptureService.sessionActive) {
            CaptureService.startRound(context, packages, notesFor(packages))
            return
        }
        queued = packages
        val mpm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // Android 14 defaults the sheet to "Share one app", which would capture TestTrack rather
        // than the app under test. Asking for the default display removes the dropdown entirely.
        val intent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            } else {
                mpm.createScreenCaptureIntent()
            }
        consentLauncher.launch(intent)
    }

    // Which app a picked image belongs to — the launcher's own callback carries only the Uri,
    // not which row asked for it.
    var pickingScreenshotFor by remember { mutableStateOf<String?>(null) }
    val pickScreenshotLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            val pkg = pickingScreenshotFor
            pickingScreenshotFor = null
            if (uri == null || pkg == null) return@rememberLauncherForActivityResult
            if (!CaptureService.supplyManualCapture(context, pkg, uri)) {
                vm.message = "That visit isn't waiting for a picture any more. Open the app again."
            }
        }

    fun pickScreenshot(pkg: String) {
        pickingScreenshotFor = pkg
        pickScreenshotLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    if (needsDrive) {
        TestingDrivePrompt(
            onConnected = {
                needsDrive = false
                val pending = pendingDrivePackages
                pendingDrivePackages = emptyList()
                run(pending)
            },
            onDismiss = {
                needsDrive = false
                pendingDrivePackages = emptyList()
            },
        )
    }

    val group = vm.group

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // A confirmation like "Report cleared" and a failure both open under the bar, where they
            // are seen the moment they happen, rather than as red text at the foot of a long list.
            Column {
                TopAppBar(
                    title = {
                        // The name the caller already had, shown immediately — see Routes.GROUP —
                        // and swapped for the loaded group's own once that answers, which is almost
                        // always the same word appearing twice rather than a visible pop. Blank only
                        // for the rare entry with neither: a placeholder here would be telling the
                        // tester something they didn't ask to be told twice, or that isn't true yet.
                        val label = group?.name?.ifBlank { null } ?: groupName.ifBlank { null }
                        if (label != null) Text(label, fontWeight = FontWeight.SemiBold)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Lucide.ArrowLeft, "Back")
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                        ),
                )
                PageMessage(if (group != null) vm.message else null, { vm.message = null })
            }
        },
    ) { padding ->
        RefreshBox(
            // Bound to the pull, not the general refresh — see HomeScreen's own note on this.
            isRefreshing = vm.pulling,
            onRefresh = { vm.load(groupId, fromPull = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    // A pull gets the same full skeleton a first load does — see HomeScreen's own
                    // note on this.
                    !vm.ready || vm.pulling -> SkeletonPage { SkeletonGroupAppList() }

                    group == null -> Blank(vm.message ?: "This crew doesn't exist any more.")

                    else -> {
                        val outstanding = vm.outstanding(context)

                        Header(group, vm.doneToday.size, vm.toTest.size)

                        // Above the day's list, and above the button that starts it, because this is
                        // the more urgent of the two and it is the one nothing else will remind them
                        // about. An app left installed after its developer has been taken out of
                        // testing goes on counting as an install for somebody nobody is testing.
                        if (vm.toUninstall.isNotEmpty()) {
                            Skipped(vm.toUninstall) { app ->
                                context.startActivity(
                                    Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")),
                                )
                            }
                        }

                        Action(
                            group = group,
                            outstanding = outstanding.size,
                            remaining = vm.toTest.size - vm.doneToday.size,
                            uploading = vm.uploading,
                            usageGranted = Session.usageAccessGranted,
                            canReturn = canReturn,
                            onStart = { run(outstanding.map { it.packageName }) },
                            onStop = { CaptureService.abortRound(context) },
                            onEndSession = { CaptureService.endSession(context) },
                            onGrantUsage = { UsageRepo.openSettings(activity) },
                            onGrantOverlay = {
                                activity.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            },
                        )

                        vm.mine?.let { app ->
                            SectionLabel("Your app")
                            Rows {
                                MineRow(
                                    app = app,
                                    reporters = vm.reportersForMine,
                                    group = group,
                                    broadcastCooldown = vm.broadcastCooldownRemaining(),
                                    broadcasting = vm.broadcasting,
                                    feedbackAskCooldown = vm.feedbackAskCooldownRemaining(),
                                    askingForFeedback = vm.askingForFeedback,
                                    onEditNotes = { editingNotes = app },
                                    onBroadcast = { vm.broadcast() },
                                    onAskForFeedback = { vm.askForFeedback() },
                                    onClick = { onOpenDashboard(app.id, app.label) },
                                )
                            }
                        }

                        SectionLabel(
                            "Apps to test",
                            if (vm.day != null) "${vm.doneToday.size} of ${vm.toTest.size}" else null,
                        )

                        if (vm.toTest.isEmpty()) {
                            EmptyState(
                                icon = Lucide.Hourglass,
                                title = "Nobody else here yet",
                                subtitle =
                                    "An admin is still placing apps into this crew. Once " +
                                        "there are ${TestGroup.THRESHOLD}, the run starts.",
                            )
                        } else {
                            Rows {
                                vm.toTest.forEachIndexed { index, app ->
                                    if (index > 0) RowDivider()
                                    TestRow(
                                        app = app,
                                        done = app.id in vm.doneToday,
                                        manual = app.id in vm.manualToday,
                                        live = vm.day != null,
                                        ended = group.running,
                                        busy = CaptureService.capturing == app.packageName,
                                        sending = app.id in vm.sending,
                                        owner = vm.owners[app.ownerUid],
                                        skipped = app.id in vm.skipped,
                                        onOpen = { run(listOf(app.packageName)) },
                                        onInstall = { openInPlay(context, app) { vm.message = it } },
                                        onOpenStore = { openInPlay(context, app) { vm.message = it } },
                                        onReportProblem = { reporting = app },
                                        onClearReport = { vm.clearMyReport(app) },
                                        feedbackGiven = app.id in vm.feedbackToday,
                                        onGiveFeedback = { givingFeedback = app },
                                        onFeedbackAlreadyGiven = {
                                            vm.message = "Feedback already submitted for today."
                                        },
                                        onToggleSkip = { vm.toggleSkip(app) },
                                        canPickScreenshot =
                                            CaptureService.missed
                                                .any { it.pkg == app.packageName },
                                        onPickScreenshot = { pickScreenshot(app.packageName) },
                                    )
                                }
                            }
                        }

                        if (vm.myHistory.isNotEmpty()) {
                            Spacer(Modifier.height(Gutter))
                            SectionLabel("Your history here")
                            Rows {
                                vm.myHistory.forEachIndexed { index, entry ->
                                    if (index > 0) RowDivider()
                                    MyHistoryRow(entry)
                                }
                            }
                        }

                        Spacer(Modifier.height(Gutter))
                    }
                }
            }
        }
    }

    reporting?.let { app ->
        var reason by remember(app.packageName) { mutableStateOf("") }

        Ask(
            title = "Report a problem with ${app.label}?",
            confirm = "Report it",
            content = {
                DialogField(
                    value = reason,
                    onValueChange = { reason = it.take(NOTES_MAX) },
                    label = "What's wrong",
                    placeholder = "Crashes right after opening",
                    supporting = "Excuses you from today until an admin clears it.",
                    singleLine = false,
                    minHeight = 120.dp,
                    maxLength = NOTES_MAX,
                )
            },
            onConfirm = {
                reporting = null
                vm.reportProblem(app, reason.trim())
            },
            onDismiss = { reporting = null },
        )
    }

    givingFeedback?.let { app ->
        var note by remember(app.packageName) { mutableStateOf("") }

        Ask(
            title = "Feedback for ${app.label}",
            confirm = "Send",
            confirmEnabled = note.isNotBlank(),
            content = {
                DialogField(
                    value = note,
                    onValueChange = { note = it.take(Feedback.TEXT_MAX) },
                    label = "What did you find?",
                    placeholder = "The settings icon is hard to find",
                    supporting = "Sent to the developer. One note a day.",
                    singleLine = false,
                    minHeight = 120.dp,
                    maxLength = Feedback.TEXT_MAX,
                )
            },
            onConfirm = {
                givingFeedback = null
                vm.submitFeedback(app, note.trim())
            },
            onDismiss = { givingFeedback = null },
        )
    }

    editingNotes?.let { app ->
        NotesDialog(
            app = app,
            onSave = {
                editingNotes = null
                vm.updateNotes(app, it)
            },
            onDismiss = { editingNotes = null },
        )
    }
}
