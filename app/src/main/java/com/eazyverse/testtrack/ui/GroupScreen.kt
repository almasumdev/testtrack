package com.eazyverse.testtrack.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.data.*
import com.eazyverse.testtrack.findActivity
import kotlinx.coroutines.launch
import java.io.File

class GroupViewModel : ViewModel() {

    var group by mutableStateOf<TestGroup?>(null)
        private set
    var mine by mutableStateOf<TestApp?>(null)
        private set
    var toTest by mutableStateOf<List<TestApp>>(emptyList())
        private set
    var doneToday by mutableStateOf<Set<String>>(emptySet())
        private set
    var reportersForMine by mutableStateOf(0)
        private set
    var loading by mutableStateOf(true)
        private set
    var uploading by mutableStateOf(0)
        private set
    var message by mutableStateOf<String?>(null)

    val day: Int? get() = group?.dayIndex()

    /** Installed, in the run, and not yet banked — exactly what a round should walk. */
    fun outstanding(context: Context): List<TestApp> =
        if (day == null) emptyList()
        else toTest.filter {
            it.id !in doneToday && InstalledApps.cachedInfo(context, it.packageName).installed
        }

    /**
     * Shows what was known before fetching. A ViewModel is rebuilt on every navigation, so without
     * the cache, stepping into an app and back would spin over content already in hand.
     */
    fun load(groupId: String) {
        val uid = AuthRepo.uid ?: return

        Cache.get<TestGroup>(Cache.group(groupId))?.let { cached ->
            group = cached
            Cache.get<List<TestApp>>(Cache.appsIn(groupId))?.let { apps ->
                mine = apps.firstOrNull { it.ownerUid == uid }
                toTest = apps.filter { it.ownerUid != uid }
            }
            cached.dayIndex()?.let { d ->
                Cache.get<Set<String>>(Cache.doneToday(uid, groupId, d))?.let { doneToday = it }
            }
            loading = false
        }

        viewModelScope.launch {
            runCatching {
                val found = Repo.group(groupId) ?: return@runCatching
                group = found
                Cache.put(Cache.group(groupId), found)

                val apps = Repo.appsInGroup(groupId)
                Cache.put(Cache.appsIn(groupId), apps)
                mine = apps.firstOrNull { it.ownerUid == uid }
                toTest = apps.filter { it.ownerUid != uid }

                val d = found.dayIndex()
                if (d != null) {
                    doneToday = Repo.myProofsForDay(uid, groupId, d)
                    Cache.put(Cache.doneToday(uid, groupId, d), doneToday)
                    reportersForMine = mine?.let { app ->
                        Repo.proofsForApp(app.id).count { it.day == d && it.meetsBar }
                    } ?: 0
                }
                message = null
            }.onFailure { message = it.message ?: "Could not load this group" }
            loading = false
        }
    }

    /**
     * Uploads a finished visit and records it.
     *
     * Drive first, Firestore second: the proof document points at a Drive file, so writing the
     * document before the upload lands would leave a grid cell referencing nothing.
     */
    fun publish(activity: Activity, app: TestApp, capture: Capture) {
        val cohort = group ?: return
        val uid = AuthRepo.uid ?: return
        val email = Session.email ?: return
        val d = cohort.dayIndex()

        if (d == null) {
            CaptureService.consume(app.packageName)
            return
        }

        uploading += 1
        viewModelScope.launch {
            val token = AuthRepo.driveTokenOrNull(activity)
            if (token == null) {
                message = "Drive access expired — reconnect it in setup."
            } else {
                val file = File(capture.path)
                val name = "${app.packageName}_day${d}_${uid.take(6)}.jpg"
                when (val result = DriveRepo.upload(token, file, name)) {
                    is UploadResult.Failed -> message = result.reason
                    is UploadResult.Ok -> {
                        val proof = Proof(
                            appId = app.id,
                            groupId = cohort.id,
                            testerUid = uid,
                            testerEmail = email,
                            day = d,
                            fileId = result.file.id,
                            imageUrl = result.file.thumbUrl,
                            capturedAt = System.currentTimeMillis(),
                            usageMs = capture.usageMs
                        )
                        runCatching { Repo.recordProof(proof) }
                            .onSuccess {
                                file.delete()
                                if (proof.meetsBar) {
                                    doneToday = doneToday + app.id
                                    Cache.put(Cache.doneToday(uid, cohort.id, d), doneToday)
                                } else {
                                    message = "${app.label} logged only " +
                                        "${formatDuration(proof.usageMs)} — open it again and stay put."
                                }
                            }
                            .onFailure { message = it.message ?: "Could not record proof" }
                    }
                }
            }
            CaptureService.consume(app.packageName)
            uploading -= 1
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(
    groupId: String,
    onOpenDashboard: (String) -> Unit,
    onBack: () -> Unit,
    vm: GroupViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(groupId) { vm.load(groupId) }

    var canReturn by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canReturn = Settings.canDrawOverlays(context)
                Session.refreshUsageAccess(context)
                vm.load(groupId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A round finishes with TestTrack back in front and several captures banked at once, so every
    // pending result is drained here rather than one screen at a time.
    LaunchedEffect(CaptureService.results.size, vm.toTest) {
        CaptureService.results.forEach { (pkg, capture) ->
            vm.toTest.firstOrNull { it.packageName == pkg }?.let { vm.publish(activity, it, capture) }
        }
    }

    // Consent is one sheet for the whole round; the queue is held until it comes back approved.
    var queued by remember { mutableStateOf<List<String>>(emptyList()) }
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            CaptureService.startSession(context, result.resultCode, data)
            if (queued.isNotEmpty()) CaptureService.startRound(context, queued)
        }
        queued = emptyList()
    }

    fun run(packages: List<String>) {
        if (packages.isEmpty()) return
        if (CaptureService.sessionActive) {
            CaptureService.startRound(context, packages)
            return
        }
        queued = packages
        val mpm = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // Android 14 defaults the sheet to "Share one app", which would capture TestTrack rather
        // than the app under test. Asking for the default display removes the dropdown entirely.
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        else
            mpm.createScreenCaptureIntent()
        consentLauncher.launch(intent)
    }

    val group = vm.group

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        group?.name?.ifBlank { null } ?: "Group",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            when {
                group == null && vm.loading -> {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.padding(horizontal = Gutter)) {
                        Skeleton(width = 150.dp, height = 30.dp, corner = 10.dp)
                        Spacer(Modifier.height(10.dp))
                        Skeleton(width = 200.dp, height = 12.dp)
                    }
                    Spacer(Modifier.height(24.dp))
                    SkeletonRows(4)
                }

                group == null -> Blank(vm.message ?: "That group no longer exists.")

                else -> {
                    val outstanding = vm.outstanding(context)

                    Header(group, vm.doneToday.size, vm.toTest.size)

                    Action(
                        group = group,
                        outstanding = outstanding.size,
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
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    )

                    vm.mine?.let { app ->
                        SectionLabel("Your app")
                        Panel {
                            MineRow(app, vm.reportersForMine, group) { onOpenDashboard(app.id) }
                        }
                    }

                    SectionLabel(
                        "Apps to test",
                        if (vm.day != null) "${vm.doneToday.size} of ${vm.toTest.size}" else null
                    )

                    when {
                        vm.toTest.isEmpty() && vm.loading -> Panel { SkeletonRows(4) }

                        vm.toTest.isEmpty() -> EmptyState(
                            icon = Icons.Outlined.HourglassEmpty,
                            title = "Nobody else here yet",
                            subtitle = "An admin is still placing apps into this group. Once " +
                                "there are ${TestGroup.THRESHOLD}, the run starts."
                        )

                        else -> Panel {
                            vm.toTest.forEach { app ->
                                TestRow(
                                    app = app,
                                    done = app.id in vm.doneToday,
                                    running = group.running,
                                    busy = CaptureService.capturing == app.packageName,
                                    onOpen = { run(listOf(app.packageName)) }
                                )
                            }
                        }
                    }

                    vm.message?.let { Failure(it) }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
private fun Header(group: TestGroup, done: Int, total: Int) {
    val day = group.dayIndex()

    Column(Modifier.padding(start = Gutter, end = Gutter, top = 4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (day != null) "Day ${day + 1}" else if (group.running) "Finished" else "Forming",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            if (day != null) {
                Spacer(Modifier.width(7.dp))
                Text(
                    "of $RUN_DAYS",
                    Modifier.padding(bottom = 5.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            if (day != null)
                "$done of $total done · ${group.size} of ${TestGroup.CAPACITY} members"
            else
                "${group.size} of ${TestGroup.THRESHOLD} members — the run starts when the " +
                    "${TestGroup.THRESHOLD}th joins",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (day != null) {
            Spacer(Modifier.height(14.dp))
            Meter(if (total == 0) 0f else done.toFloat() / total)
        }
        if (group.atRisk) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Short ${group.stillNeeded} member${if (group.stillNeeded == 1) "" else "s"}. The " +
                    "count above keeps going, but Play may have reset its own — ask an admin to " +
                    "fill the slot.",
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Status.missedSoft)
                    .padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Status.missed
            )
        }
    }
}

/**
 * One control, whichever one applies.
 *
 * Obstacles are offered in the order they have to clear: a "Start testing" button next to an
 * ungranted permission is a button that fails.
 */
@Composable
private fun Action(
    group: TestGroup,
    outstanding: Int,
    uploading: Int,
    usageGranted: Boolean,
    canReturn: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onEndSession: () -> Unit,
    onGrantUsage: () -> Unit,
    onGrantOverlay: () -> Unit
) {
    Column(Modifier.padding(start = Gutter, end = Gutter, top = 18.dp)) {
        when {
            group.dayIndex() == null -> Unit

            CaptureService.roundActive -> {
                Text(
                    CaptureService.status
                        ?: "Testing ${CaptureService.roundIndex} of ${CaptureService.roundTotal}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onStop,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) { Text("Stop") }
            }

            uploading > 0 -> Row(
                Modifier.fillMaxWidth().height(46.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Uploading $uploading proof${if (uploading == 1) "" else "s"}…",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            !usageGranted -> {
                Text(
                    "Your report records how long you spend in each app. Turn on usage access for " +
                        "TestTrack and come back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Primary("Open usage access", onClick = onGrantUsage)
            }

            !canReturn -> {
                Text(
                    "A round moves between apps on its own, which Android only allows with the " +
                        "display-over-other-apps permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Primary("Allow TestTrack to switch apps", onClick = onGrantOverlay)
            }

            outstanding == 0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Everything's done for today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Status.posted
                )
                if (CaptureService.sessionActive) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onEndSession) { Text("Stop sharing") }
                }
            }

            else -> {
                Primary("Start testing · $outstanding left", tall = true, onClick = onStart)
                if (CaptureService.sessionActive) {
                    TextButton(
                        onClick = onEndSession,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { Text("Stop screen sharing") }
                } else {
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Each app opens for just over half a minute and moves to the next on its own. " +
                        "The screenshot lands at a moment you won't know in advance, so leave the " +
                        "phone alone until you're back here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MineRow(app: TestApp, reporters: Int, group: TestGroup, onClick: () -> Unit) {
    val expected = (group.size - 1).coerceAtLeast(0)

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.packageName, app.label)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (group.dayIndex() != null) "$reporters of $expected reported today"
                else "Waiting for the run to start",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TestRow(
    app: TestApp,
    done: Boolean,
    running: Boolean,
    busy: Boolean,
    onOpen: () -> Unit
) {
    val context = LocalContext.current
    val info = remember(app.packageName) { InstalledApps.cachedInfo(context, app.packageName) }

    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.packageName, app.label)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (info.installed) info.streakLabel else app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))

        when {
            busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            !info.installed -> Pill("Install", Status.missed, Status.missedSoft)
            done -> Pill("Done", Status.posted, Status.postedSoft)
            !running -> Pill("Waiting", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft)
            else -> TextButton(
                onClick = onOpen,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    "Open",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
