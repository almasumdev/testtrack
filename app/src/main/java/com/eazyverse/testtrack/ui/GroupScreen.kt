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
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.data.*
import com.eazyverse.testtrack.findActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date

class GroupViewModel : ViewModel() {

    var group by mutableStateOf<TestGroup?>(null)
        private set
    var mine by mutableStateOf<TestApp?>(null)
        private set
    var toTest by mutableStateOf<List<TestApp>>(emptyList())

    /**
     * Apps in this group that an admin has taken out of testing.
     *
     * Held separately from [toTest] rather than filtered out of it and forgotten, because there
     * is something to do about these and it is more urgent than the day's list: they have to come
     * off the phone. An app left installed goes on counting as an install against a developer who
     * is no longer being tested.
     */
    var toUninstall by mutableStateOf<List<TestApp>>(emptyList())
        private set
    var doneToday by mutableStateOf<Set<String>>(emptySet())
        private set
    var reportersForMine by mutableStateOf(0)
        private set
    /**
     * The group, its apps and today's tally are all in hand.
     *
     * One flag for the page — see [SkeletonPage]. This screen is where a partial render shows
     * worst: the header arrives first, an empty worklist means nothing is outstanding, and the
     * action reads "everything's done for today" in green before the apps it hasn't seen yet turn
     * it into "start testing · 5 left". Sticky once set, so the reload on every resume does not
     * throw the screen back to shimmer.
     */
    var ready by mutableStateOf(false)
        private set

    var uploading by mutableStateOf(0)
        private set

    /**
     * Which apps have a proof in the air, rather than how many.
     *
     * [uploading] counts, and a count can only be spent on one line at the top of the screen. The
     * rows underneath it went on offering Open for an app whose proof was already on its way, so
     * the obvious thing to do about a row that looked untouched was to do it again.
     */
    var sending by mutableStateOf<Set<String>>(emptySet())
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
    /**
     * Held for as long as this screen is on the stack, and started once.
     *
     * The apps in a cohort are the one thing here that another person changes: an admin takes one
     * out of testing and everybody else is meant to uninstall it. Everything else on the screen is
     * about the reader, so it stays on [load].
     */
    private var watching: Job? = null

    private fun watch(groupId: String) {
        if (watching != null) return
        watching = viewModelScope.launch {
            Repo.watchAppsInGroup(groupId)
                .catch { /* The reload on resume is still there and still corrects this. */ }
                .collect { apps ->
                    val uid = AuthRepo.uid ?: return@collect
                    toTest = apps.filter { it.ownerUid != uid && it.active }
                    toUninstall = apps.filter { it.ownerUid != uid && it.removed }
                    mine = apps.firstOrNull { it.ownerUid == uid }
                    Cache.put(Cache.appsIn(groupId), apps)
                }
        }
    }

    fun load(groupId: String) {
        val uid = AuthRepo.uid ?: return

        watch(groupId)
        showCached(uid, groupId)

        viewModelScope.launch {
            runCatching {
                val found = Repo.group(groupId) ?: return@runCatching
                val apps = Repo.appsInGroup(groupId)
                val own = apps.firstOrNull { it.ownerUid == uid }
                val d = found.dayIndex()

                val banked =
                    if (d == null) emptySet() else Repo.myProofsForDay(uid, groupId, d, found.startDate)
                val reporters =
                    if (d == null || own == null) 0
                    else Repo.proofsForApp(own.id, own.ownerUid).count { it.day == d && it.meetsBar }

                // Published in one go, after every query has answered — see [ready].
                group = found
                mine = own
                toTest = apps.filter { it.ownerUid != uid && it.active }
                toUninstall = apps.filter { it.ownerUid != uid && it.removed }
                doneToday = banked
                reportersForMine = reporters

                Cache.put(Cache.group(groupId), found)
                Cache.put(Cache.appsIn(groupId), apps)
                if (d != null) {
                    Cache.put(Cache.doneToday(uid, groupId, d), banked)
                    own?.let { Cache.put(Cache.reporters(it.id, d), reporters) }
                }
                message = null
            }.onFailure {
                message = it.friendly(
                    "We couldn't load this group. Check your connection and try again.",
                    denied = "You're not in this group any more. It may have been dissolved, or " +
                        "your app may have been taken out of it."
                )
            }
            ready = true
        }
    }

    /**
     * Renders the cache only on a complete hit.
     *
     * Every `?: return` here is a piece the screen would otherwise have to invent a value for, and
     * the invented value is always the confident one: no apps means nothing to test, no tally
     * means nothing done. Half a group on screen is not a faster group, it is a wrong one.
     */
    private fun showCached(uid: String, groupId: String) {
        val cached = Cache.get<TestGroup>(Cache.group(groupId)) ?: return
        val apps = Cache.get<List<TestApp>>(Cache.appsIn(groupId)) ?: return
        val own = apps.firstOrNull { it.ownerUid == uid }
        val d = cached.dayIndex()

        val banked =
            if (d == null) emptySet()
            else Cache.get<Set<String>>(Cache.doneToday(uid, groupId, d)) ?: return
        val reporters =
            if (d == null || own == null) 0
            else Cache.get<Int>(Cache.reporters(own.id, d)) ?: return

        group = cached
        mine = own
        toTest = apps.filter { it.ownerUid != uid && it.active }
        toUninstall = apps.filter { it.ownerUid != uid && it.removed }
        doneToday = banked
        reportersForMine = reporters
        ready = true
    }

    /**
     * Uploads a finished visit and records it.
     *
     * Drive first, Firestore second: the proof document points at a Drive file, so writing the
     * document before the upload lands would leave a grid cell referencing nothing.
     */
    /**
     * Tells the admins this app cannot be installed.
     *
     * Answered on screen straight away rather than waiting for the send, because the tester's
     * part is finished the moment they press it and the round trip is not theirs to watch. If it
     * never arrives they have lost nothing they had.
     */
    fun reportCannotInstall(app: TestApp) {
        message = "Thanks. The admins have been told about ${app.label}."
        viewModelScope.launch {
            runCatching { Notify.cannotInstall(app.label, app.packageName) }
        }
    }

    /**
     * Saves the way into this app, then re-reads the group so the row shows what was saved.
     *
     * Nothing is put on screen ahead of the write, unlike [reportCannotInstall]. This is the text
     * twelve people will be typing in tonight, so a row that claims the new login is saved when
     * Firestore never took it would send all twelve to a sign-in screen that turns them away.
     */
    fun updateNotes(app: TestApp, notes: String) {
        val groupId = group?.id ?: return
        message = null
        viewModelScope.launch {
            runCatching { Repo.updateNotes(app.packageName, notes) }
                .onSuccess { load(groupId) }
                .onFailure {
                    message = it.friendly(
                        "We couldn't save your notes. Check your connection and try again."
                    )
                }
        }
    }

    fun publish(activity: Activity, app: TestApp, capture: Capture) {
        val cohort = group ?: return
        val uid = AuthRepo.uid ?: return
        val email = Session.email ?: return
        val d = cohort.dayIndex()

        if (d == null) {
            CaptureService.consume(app.packageName)
            return
        }

        // Already on its way. The effect that drains finished visits restarts whenever the map it
        // is reading changes size, which it does on every upload that lands, so without this an
        // app in flight is handed to a second upload of the same file.
        if (app.id in sending) return

        /*
         * The picture is gone before we have started.
         *
         * The guard above is what stopped this happening, because the second of two publishes of
         * one visit arrived after the first had uploaded the file and deleted it. Kept anyway, and
         * checked here rather than left to the upload, for two reasons: the upload's answer for a
         * missing file was the path and `ENOENT` printed in red on a tester's screen, and this is
         * the only place that knows which app the row belongs to.
         *
         * Consumed rather than left pending. There is nothing on disk to retry.
         */
        if (!File(capture.path).exists()) {
            message = "${app.label} didn't leave a screenshot behind, so it hasn't counted. " +
                "Open it again."
            CaptureService.consume(app.packageName)
            return
        }

        uploading += 1
        sending = sending + app.id
        viewModelScope.launch {
            val token = AuthRepo.driveTokenOrNull(activity)
            if (token == null) {
                // The cached flag is now known to be wrong, and it is the only thing standing
                // between the tester and the step that fixes this. Left true, Setup shows Drive as
                // done and offers no way to reconnect.
                Session.updateDriveConnected(false)
                message = "Your Drive access has expired. Open Setup from the top of the home " +
                    "screen and reconnect it, then try again."
            } else {
                val file = File(capture.path)
                val name = "${app.packageName}_day${d}_${uid.take(6)}.jpg"
                when (val result = DriveRepo.upload(token, file, name)) {
                    is UploadResult.Failed -> message = result.reason
                    is UploadResult.Ok -> {
                        val proof = Proof(
                            runStartedAt = cohort.startDate,
                            appId = app.id,
                            groupId = cohort.id,
                            ownerUid = app.ownerUid,
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
                                    message = "${app.label} only counted " +
                                        "${formatDuration(proof.usageMs)}. Open it again and " +
                                        "stay in it until we bring you back."
                                }
                            }
                            .onFailure {
                                message = it.friendly(
                                    "We couldn't save that proof. Check your connection and try again.",
                                    denied = "You're not in this group any more, so today's test " +
                                        "can't be recorded against it."
                                )
                            }
                    }
                }
            }
            CaptureService.consume(app.packageName)
            uploading -= 1
            sending = sending - app.id
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

    /** The app a tester is about to report as uninstallable, or null. */
    var reporting by remember { mutableStateOf<TestApp?>(null) }

    /** The owner's own app while its notes are being edited, or null. */
    var editingNotes by remember { mutableStateOf<TestApp?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canReturn = Settings.canDrawOverlays(context)
                Session.refreshUsageAccess(context)
                // Coming back from Play with the app now installed looks exactly like coming back
                // from anywhere else, so every resume re-asks. It is one call per row and it moves
                // nothing unless an answer changed.
                InstalledApps.refresh(context)
                vm.load(groupId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // Resume alone is not enough. Play frequently finishes the download after the tester has
        // already come back, so the row they are looking at would stay stale until they left and
        // returned a second time, for no reason they could see. The system announces the install
        // when it lands; a receiver that lives only as long as this screen is the cheap way to
        // hear it, and needs no permission because these are protected broadcasts.
        val installs = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) = InstalledApps.refresh(context)
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
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { context.unregisterReceiver(installs) }
        }
    }

    /*
     * Screen sharing belongs to this page and is handed back on the way off it.
     *
     * Both guards are load bearing, because leaving composition is not the same thing as leaving
     * the screen. A configuration change disposes and rebuilds this, and MainActivity declares no
     * configChanges, so a landscape app under test tearing the activity down looks identical to
     * somebody pressing back. And during a round the tester is inside somebody else's app rather
     * than here, which is the one time a dispose certainly does not mean they left.
     *
     * What is left over is a dispose that happens mid-round and really was a departure. It cannot
     * happen from this screen, and if it somehow does the session ends the next time the page is
     * left or the app is closed.
     */
    DisposableEffect(Unit) {
        onDispose {
            if (!activity.isChangingConfigurations && !CaptureService.roundActive) {
                CaptureService.endSession(context)
            }
        }
    }

    // A round finishes with TestTrack back in front and several captures banked at once, so every
    // pending result is drained here rather than one screen at a time.
    LaunchedEffect(CaptureService.results.size, vm.toTest) {
        CaptureService.results.forEach { (pkg, capture) ->
            vm.toTest.firstOrNull { it.packageName == pkg }?.let { vm.publish(activity, it, capture) }
        }
    }

    // A visit that came back without a picture is the one failure a tester cannot see. The app
    // opened, the ring emptied, TestTrack came back, and the row simply did not move. Said by name
    // here, because "nothing happened" is not something a list can express.
    LaunchedEffect(CaptureService.missed.size, vm.toTest) {
        val names = CaptureService.missed.map { pkg ->
            vm.toTest.firstOrNull { it.packageName == pkg }?.label ?: pkg
        }
        if (names.isNotEmpty()) {
            val rest = names.size - 1
            vm.message =
                if (rest == 0) "We couldn't get a picture of ${names[0]}, so it hasn't counted " +
                    "yet. Open it again."
                else "We couldn't get a picture of ${names[0]} and $rest more, so they haven't " +
                    "counted yet. Run the rest of today's list again."
            CaptureService.forgetMissed()
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
                    // The bar is part of the page: a placeholder name would pop to the real one a
                    // second later, which is the same lie the body no longer tells.
                    if (!vm.ready) Skeleton(width = 116.dp, height = 19.dp, corner = 6.dp)
                    else Text(
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
                !vm.ready -> SkeletonPage { SkeletonAppList() }

                group == null -> Blank(vm.message ?: "This group doesn't exist any more.")

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
                                Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
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
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    )

                    vm.mine?.let { app ->
                        SectionLabel("Your app")
                        Rows {
                            MineRow(
                                app = app,
                                reporters = vm.reportersForMine,
                                group = group,
                                onEditNotes = { editingNotes = app },
                                onClick = { onOpenDashboard(app.id) }
                            )
                        }
                    }

                    SectionLabel(
                        "Apps to test",
                        if (vm.day != null) "${vm.doneToday.size} of ${vm.toTest.size}" else null
                    )

                    if (vm.toTest.isEmpty()) {
                        EmptyState(
                            icon = Icons.Outlined.HourglassEmpty,
                            title = "Nobody else here yet",
                            subtitle = "An admin is still placing apps into this group. Once " +
                                "there are ${TestGroup.THRESHOLD}, the run starts."
                        )
                    } else {
                        Rows {
                            vm.toTest.forEachIndexed { index, app ->
                                if (index > 0) RowDivider()
                                TestRow(
                                    app = app,
                                    done = app.id in vm.doneToday,
                                    live = vm.day != null,
                                    ended = group.running,
                                    busy = CaptureService.capturing == app.packageName,
                                    sending = app.id in vm.sending,
                                    onOpen = { run(listOf(app.packageName)) },
                                    onInstall = { openInPlay(context, app) { vm.message = it } },
                                    onCannotInstall = { reporting = app }
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

    reporting?.let { app ->
        Ask(
            title = "Can't install ${app.label}?",
            body = "If Play says the app is not available, its testing track is set up wrong " +
                "and nobody else can install it either. Telling the admins is the only way " +
                "anyone finds out, and they can skip it so it stops counting against your " +
                "day. Nothing happens to your own record either way.",
            confirm = "Tell the admins",
            onConfirm = {
                reporting = null
                vm.reportCannotInstall(app)
            },
            onDismiss = { reporting = null }
        )
    }

    editingNotes?.let { app ->
        NotesDialog(
            app = app,
            onSave = {
                editingNotes = null
                vm.updateNotes(app, it)
            },
            onDismiss = { editingNotes = null }
        )
    }
}

/**
 * The way into an app, written by the person who has it and read by everyone who needs it.
 *
 * Not destructive, and an empty field saved is a real answer rather than a slip to guard against:
 * an app that needed a test account last week may not need one now, and clearing the field is the
 * only way to take a stale login down once it stops working.
 */
@Composable
private fun NotesDialog(app: TestApp, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(app.packageName) { mutableStateOf(app.notes) }

    Ask(
        title = "Notes for your testers",
        confirm = "Save",
        onConfirm = { onSave(text.trim()) },
        onDismiss = onDismiss,
        content = {
            DialogField(
                value = text,
                // Held to the cap as they type rather than checked on save, because the limit is
                // the document's. Refusing it afterwards would throw away whatever they had
                // already written past the end.
                onValueChange = { text = it.take(NOTES_MAX) },
                label = "Notes",
                placeholder = "Test account: demo@example.com / pass1234",
                supporting = "Anything they need to get past the front door. Everyone in your " +
                    "group and the admins can read this, so use a throwaway account, never your " +
                    "own password.",
                singleLine = false,
                // The same floor the submit form gives it. A field asking for a sign-in note is
                // asking for a couple of lines, and one line high says it wants a word.
                minHeight = 96.dp
            )
        }
    )
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
                    "of ${group.runDays}",
                    Modifier.padding(bottom = 5.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            when {
                day != null ->
                    "$done of $total done · ${group.size} of ${TestGroup.CAPACITY} members"
                // A run that has ended is not one that has not started. `startDate > 0` is what
                // tells them apart, and the copy has to as well.
                group.running ->
                    "All ${group.runDays} days are behind you. Testing on is welcome but no longer " +
                        "asked for. Your dashboard has the full record."
                else ->
                    "${group.size} of ${TestGroup.THRESHOLD} members so far. The run starts once " +
                        "the ${TestGroup.THRESHOLD}th joins."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (day != null) {
            Spacer(Modifier.height(14.dp))
            Meter(if (total == 0) 0f else done.toFloat() / total)

            /*
             * When the day turns over, spelled out.
             *
             * The run's clock starts at the hour the group did, so this is almost never midnight
             * and there is no way to work it out from anything else on screen. Somebody testing
             * after dinner can put half a round on one day and half on the next and see only that
             * their count went back to zero.
             *
             * The date is in it as well as the time. "Ends at 8:33 pm" on an evening after 8:33 is
             * a sentence about tomorrow that reads as one about tonight.
             */
            group.dayEndsAt()?.let { ends ->
                Spacer(Modifier.height(10.dp))
                val context = LocalContext.current
                val clock = remember(ends) {
                    val time = DateFormat.getTimeFormat(context).format(Date(ends))
                    val today = DateFormat.getDateFormat(context).format(Date())
                    val then = DateFormat.getDateFormat(context).format(Date(ends))
                    if (today == then) "Day ${day + 1} ends at $time today"
                    else "Day ${day + 1} ends at $time tomorrow"
                }
                Text(
                    clock,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // A short-handed group used to be called out here in red. It is not shown any more, and
        // the reason is that being short is now something an admin can choose: a run can be
        // started deliberately with eight people, and telling those eight every day that they are
        // five short reads as a fault in their group rather than a decision about it.
        //
        // Nothing is hidden that a tester can act on. Filling an empty slot was never theirs to
        // do, the day count in front of them is the same either way, and the admin console still
        // shows the warning in full, which is where the person who can fix it is looking.
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
    remaining: Int,
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
                    "We record how long you spend in each app, and Android only shares that with " +
                        "usage access switched on. Turn it on for TestTrack and come back here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Primary("Open usage access", onClick = onGrantUsage)
            }

            !canReturn -> {
                Text(
                    "A round opens each app and brings you back on its own. Android only lets us " +
                        "do that with permission to display over other apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Primary("Allow TestTrack to switch apps", onClick = onGrantOverlay)
            }

            // Nothing to run, but for two very different reasons. A round only walks apps that are
            // actually on the phone, so a cohort none of which are installed also has nothing
            // outstanding — and calling that "done", in green, over a header reading "0 of 11
            // done" is the screen contradicting itself.
            outstanding == 0 && remaining > 0 -> Text(
                "$remaining app${if (remaining == 1) "" else "s"} still to test, and " +
                    "${if (remaining == 1) "it isn't" else "none of them are"} installed on this " +
                    "phone yet. Tap Install on ${if (remaining == 1) "it" else "each one"} to " +
                    "join the test and get it from Play. A round can only open what's already here.",
                style = MaterialTheme.typography.bodySmall,
                color = Status.missed
            )

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
                    "Each app opens for about twenty seconds, then moves to the next one on " +
                        "its own. We take the screenshot at a moment you won't know in advance, " +
                        "so leave the phone alone until you're back here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

}

/**
 * Apps nobody is being asked for, and what to do about them.
 *
 * Deliberately not called "uninstall these", which is what it said first and which assumes the
 * tester ever got it installed. The commonest reason an app ends up here is that it was never
 * installable: a closed testing track set up wrong, a listing that answers with an error, and
 * thirteen people who could not have opened it however willing they were. Telling those thirteen
 * to uninstall something they never had reads as an app that has lost track of itself.
 *
 * A removal is not a punishment being announced to the group either, so this says as little about
 * the developer as it can. The reason line is shown when an admin typed one, because "not on
 * closed testing yet" is worth knowing and stops people trying again.
 *
 * "Until it comes back" rather than a date, because there is no date. An admin decides, and
 * anything more definite would be invented.
 */
@Composable
private fun Skipped(apps: List<TestApp>, onUninstall: (TestApp) -> Unit) {
    // Read once, out here. Inside the remember lambda it is a composable call in a place that is
    // not composable, which the compiler catches and is right to.
    val context = LocalContext.current

    SectionLabel("Not being tested")
    Rows {
        apps.forEachIndexed { index, app ->
            if (index > 0) RowDivider()
            val info = remember(app.packageName, InstalledApps.revision) {
                InstalledApps.cachedInfo(context, app.packageName)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        app.removedReason.ifBlank {
                            "Skipped for now. It does not count towards your day."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Offered only to somebody who actually has it. The rest have nothing to do here.
                if (info.installed) {
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = { onUninstall(app) }) {
                        Text("Uninstall", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun MineRow(
    app: TestApp,
    reporters: Int,
    group: TestGroup,
    onEditNotes: () -> Unit,
    onClick: () -> Unit
) {
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
        // Two labels, because the two states are not the same job. An owner who has never written
        // notes has no reason to expect the row hides a field, and "Notes" on its own reads as a
        // heading rather than something to press.
        TextButton(onClick = onEditNotes) {
            Text(if (app.notes.isBlank()) "Add notes" else "Edit notes")
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Sends a tester to this app's listing, to install it.
 *
 * The Play app is asked directly first, by `market://`, and only then the https address. Both
 * reach the same listing, but a closed-testing listing is visible only to an account on the tester
 * list: opened in a browser signed in as somebody else, it reports the app as missing. Going to
 * the Play app first keeps that from happening on a phone where it would have worked.
 *
 * A device with neither is possible, a stripped ROM or Play disabled, and that is the one case
 * worth a message rather than a shrug, because the tester is stuck on a step they cannot complete
 * from here.
 */
private fun openInPlay(context: Context, app: TestApp, onFailure: (String) -> Unit) {
    fun open(uri: String) = runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    open(app.storeAppUri)
        .recoverCatching { open(app.storeUrl).getOrThrow() }
        .onFailure {
            onFailure(
                "We couldn't open Play on this phone. Install ${app.label} from " +
                    "${app.storeUrl}, using the account you joined the testers group with."
            )
        }
}

@Composable
private fun TestRow(
    app: TestApp,
    done: Boolean,
    /** The run is under way — the only state in which a visit can be banked against a day. */
    live: Boolean,
    /** Distinguishes a run that is over from one that has not begun. Only read when not [live]. */
    ended: Boolean,
    /** This app is on screen right now, being visited. */
    busy: Boolean,
    /** This app's proof is uploading. Not done yet, and not something to start again either. */
    sending: Boolean,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onCannotInstall: () -> Unit
) {
    val context = LocalContext.current
    // Keyed on the revision as well as the package: without it this row holds the answer it was
    // first given, and an app installed since then keeps offering Install.
    val info = remember(app.packageName, InstalledApps.revision) {
        InstalledApps.cachedInfo(context, app.packageName)
    }

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

            /*
             * What the developer said you need to get in, on the row you need it on.
             *
             * Two lines and no more. Thirteen rows each carrying a paragraph is a list nobody
             * reads, and the first line is nearly always the account and the password. Somebody
             * who wrote more can still see all of it from their own screen, and the tester who
             * needs the rest has the label right above it to ask about.
             */
            if (app.notes.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    app.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            /*
             * Only while it is not installed, because that is the only state in which somebody
             * would say it.
             *
             * This exists because the failure it reports is invisible from everywhere else. An
             * app whose closed testing track is set up wrong cannot be installed by anyone, and
             * from the outside that looks exactly like thirteen people who did not bother. The
             * tester staring at the error is the only one who knows, and without a button they
             * have nowhere to put it except a chat nobody reads.
             */
            if (!info.installed) {
                TextButton(
                    onClick = onCannotInstall,
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("Can't install this?", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.width(10.dp))

        when {
            busy -> Working("Opening")

            // Ahead of every other state on purpose, including Install. An upload in flight is
            // about this exact row and outranks anything the row would otherwise be saying.
            sending -> Working("Uploading")

            // A button, not a label. This sat where Open sits, in Open's shape, and did nothing
            // at all — the one control on the screen that looked tappable and was not.
            !info.installed -> TextButton(
                onClick = onInstall,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.textButtonColors(
                    containerColor = Status.missedSoft,
                    contentColor = Status.missed
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    "Install",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            done -> Pill("Done", Status.posted, Status.postedSoft)

            // Off the clock, in either direction. `running` alone was not enough: it stays true
            // once a run ends, so day 15 still offered Open — a button that spends twenty
            // seconds capturing a visit `publish` then drops, because there is no day to file it
            // under.
            !live -> Pill(
                if (ended) "Ended" else "Waiting",
                MaterialTheme.colorScheme.onSurfaceVariant,
                Status.neutralSoft
            )
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
