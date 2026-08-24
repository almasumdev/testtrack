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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.text.format.DateFormat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay
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
    /** The subset of [doneToday] posted by hand rather than captured automatically. */
    var manualToday by mutableStateOf<Set<String>>(emptySet())
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

    /** A refresh in flight over content already on screen — see [HomeViewModel.refreshing]. */
    var refreshing by mutableStateOf(false)
        private set

    /** The reader pulled for this themselves — see [HomeViewModel.pulling]. */
    var pulling by mutableStateOf(false)
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

    /**
     * Who each app belongs to, by uid.
     *
     * One read of the roster for the whole list rather than a lookup per row. The email is already
     * on the app document, so this is only here for the name somebody chose — an account that has
     * not set one falls back to the address, which is what [Tester.shortName] does anyway.
     */
    var owners by mutableStateOf<Map<String, Tester>>(emptyMap())
        private set

    /** Apps in [toTest] the caller has personally opted out of — see [toggleSkip]. */
    var skipped by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Apps in [toTest] the caller has already left today's note on — see [submitFeedback]. */
    var feedbackToday by mutableStateOf<Set<String>>(emptySet())
        private set

    var message by mutableStateOf<String?>(null)

    var broadcasting by mutableStateOf(false)
        private set

    val day: Int? get() = group?.dayIndex()

    /** Millis until [mine]'s owner may broadcast again, or 0 if one could be sent right now. */
    fun broadcastCooldownRemaining(): Long {
        val last = mine?.lastBroadcastAt?.takeIf { it > 0L } ?: return 0L
        return (BROADCAST_COOLDOWN_MS - (System.currentTimeMillis() - last)).coerceAtLeast(0L)
    }

    /**
     * Tells everyone testing [mine] that an update is out — the same write DashboardViewModel's
     * own [broadcast] makes, offered here too so an owner doesn't have to leave the group list to
     * send it.
     */
    fun broadcast() {
        val a = mine ?: return
        val g = group ?: return
        broadcasting = true
        viewModelScope.launch {
            runCatching { Repo.broadcastUpdate(a, g) }
                .onSuccess {
                    mine = a.copy(lastBroadcastAt = System.currentTimeMillis())
                    message = "Told everyone testing ${a.label} that an update is out."
                }
                .onFailure {
                    message = it.friendly("We couldn't send that. Check your connection and try again.")
                }
            broadcasting = false
        }
    }

    var askingForFeedback by mutableStateOf(false)
        private set

    /** Millis until [mine]'s owner may ask for feedback again, or 0 if one could be sent now. */
    fun feedbackAskCooldownRemaining(): Long {
        val last = mine?.lastFeedbackAskAt?.takeIf { it > 0L } ?: return 0L
        return (FEEDBACK_ASK_COOLDOWN_MS - (System.currentTimeMillis() - last)).coerceAtLeast(0L)
    }

    /** [broadcast]'s reverse: asking the cohort to speak up, on its own independent cooldown. */
    fun askForFeedback() {
        val a = mine ?: return
        val g = group ?: return
        askingForFeedback = true
        viewModelScope.launch {
            runCatching { Repo.askForFeedback(a, g) }
                .onSuccess {
                    mine = a.copy(lastFeedbackAskAt = System.currentTimeMillis())
                    message = "Asked everyone testing ${a.label} for feedback."
                }
                .onFailure {
                    message = it.friendly("We couldn't send that. Check your connection and try again.")
                }
            askingForFeedback = false
        }
    }

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

    fun load(groupId: String, fromPull: Boolean = false) {
        val uid = AuthRepo.uid ?: return

        watch(groupId)
        showCached(uid, groupId)

        viewModelScope.launch {
            refreshing = ready // only worth flagging over content that's already on screen
            if (fromPull) pulling = true
            runCatching {
                val found = Repo.group(groupId) ?: return@runCatching
                val apps = Repo.appsInGroup(groupId)
                val own = apps.firstOrNull { it.ownerUid == uid }
                val d = found.dayIndex()

                // Same shape as HomeViewModel's own fix: a proof posted earlier today for an app
                // since taken out of testing is still a proof, but it is not one of today's owed
                // apps any more — left uncounted here, or the header can read more done than
                // there are apps to do, like "26 of 25".
                val owed = apps.asSequence().filter { it.ownerUid != uid && it.active }
                    .mapTo(HashSet()) { it.id }
                val details =
                    if (d == null) emptyMap()
                    else Repo.myProofDetailsForDay(uid, groupId, d, found.startDate)
                        .filterKeys { it in owed }
                val banked = details.keys.toSet()
                val manual = details.filterValues { it }.keys.toSet()
                /*
                 * People, not documents.
                 *
                 * This counted proofs and said "14 of 12 reported today" for a cohort that only
                 * holds twelve other testers. A proof is keyed by run as well as by day, so one
                 * tester can hold two for the same day whenever a run's start has been corrected
                 * underneath them, and counting rows counted that person twice.
                 *
                 * The run stamp is the same filter the tester's own side already applies in
                 * [Repo.myProofsForDay]. Without it this answers with days numbered from a start
                 * date that is no longer the start date.
                 */
                val reporters =
                    if (d == null || own == null) 0
                    else Repo.proofsForApp(own.id, own.ownerUid)
                        .filter { it.day == d && it.meetsBar && it.runStartedAt == found.startDate }
                        .distinctBy { it.testerUid }
                        .size
                val people = runCatching { Repo.testers(found.memberUids) }.getOrDefault(emptyList())
                // Same reasoning as [people]: a checkbox that failed to load is not worth taking
                // the rest of the list down for.
                val mySkips = runCatching { Repo.mySkips(uid, groupId) }.getOrDefault(emptySet())
                val myFeedback = if (d == null) emptySet() else runCatching {
                    Repo.myFeedbackToday(uid, groupId, d, found.startDate)
                }.getOrDefault(emptySet())

                // Published in one go, after every query has answered — see [ready].
                group = found
                mine = own
                toTest = apps.filter { it.ownerUid != uid && it.active }
                toUninstall = apps.filter { it.ownerUid != uid && it.removed }
                doneToday = banked
                manualToday = manual
                reportersForMine = reporters
                skipped = mySkips
                feedbackToday = myFeedback
                // Failure here is not failure of the screen. A row with no name against it is a
                // row that still opens, so the roster is fetched with its own runCatching rather
                // than taking the day's list down with it.
                if (people.isNotEmpty()) owners = people.associateBy { it.uid }

                Cache.put(Cache.group(groupId), found)
                Cache.put(Cache.testers(groupId), people)
                Cache.put(Cache.appsIn(groupId), apps)
                Cache.put(Cache.skips(uid, groupId), mySkips)
                if (d != null) {
                    Cache.put(Cache.doneToday(uid, groupId, d), banked)
                    Cache.put(Cache.manualToday(uid, groupId, d), manual)
                    Cache.put(Cache.feedbackToday(uid, groupId, d), myFeedback)
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
            refreshing = false
            pulling = false
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

        // Not a `?: return` like the others. A name is a nicety on a row that works without one,
        // so its absence should not hold back a screen the cache can otherwise draw in full. The
        // manual/automated split is the same: worth showing when it's in hand, not worth blocking
        // the rest of the screen over.
        Cache.get<List<Tester>>(Cache.testers(groupId))
            ?.let { owners = it.associateBy { person -> person.uid } }
        Cache.get<Set<String>>(Cache.skips(uid, groupId))?.let { skipped = it }
        if (d != null) {
            Cache.get<Set<String>>(Cache.manualToday(uid, groupId, d))?.let { manualToday = it }
            Cache.get<Set<String>>(Cache.feedbackToday(uid, groupId, d))?.let { feedbackToday = it }
        }

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
     * Says an app would not install, crashed, or otherwise could not be tested — and stops it
     * counting against the day, from right now, without waiting on an admin to notice.
     *
     * Two things happen, and only the first is answered on screen straight away: the Firestore
     * write is what actually excuses the day (see [Compliance] and the rules' `isReported`), and
     * the best-effort push is what gets an admin looking at it sooner rather than at their next
     * visit to the console. The write's own result is what [watch] picks up and turns into the
     * row's "Reported" state, so nothing here needs to re-read the group by hand.
     */
    fun reportProblem(app: TestApp, reason: String) {
        val uid = AuthRepo.uid ?: return
        message = "Thanks. ${app.label} won't count against your day until an admin looks into it."
        viewModelScope.launch {
            runCatching { Repo.reportProblem(app.id, uid, reason) }
                .onFailure {
                    message = it.friendly(
                        "We couldn't send that report. Check your connection and try again."
                    )
                }
            runCatching { Notify.cannotInstall(app.label, app.packageName) }
        }
    }

    /**
     * Takes a report back — only ever the tester who filed it, since that is all the rules admit;
     * see [Repo.clearMyReport]. [watch] picks up the cleared fields the same way it picks up a
     * fresh report, so there is nothing to update here beyond the message.
     */
    fun clearMyReport(app: TestApp) {
        message = "Report cleared. ${app.label} counts toward today again."
        viewModelScope.launch {
            runCatching { Repo.clearMyReport(app.id) }
                .onFailure {
                    message = it.friendly("Couldn't clear that report. Check your connection and try again.")
                }
        }
    }

    /**
     * Leaves a day-one note on an app that otherwise went fine — a rough edge, a typo, something
     * worth the owner hearing that isn't [reportProblem]'s "excuse me from this." At most one a
     * day per app; see [Repo.submitFeedback].
     *
     * Marked given locally rather than waiting on [watch], the same reasoning [toggleSkip] gives:
     * the write that just landed is the only thing that could have changed this today.
     */
    fun submitFeedback(app: TestApp, text: String) {
        val uid = AuthRepo.uid ?: return
        val groupId = group?.id ?: return
        val d = day ?: return
        val started = group?.startDate ?: return
        feedbackToday = feedbackToday + app.id
        message = "Sent to ${app.label}'s developer."
        viewModelScope.launch {
            runCatching { Repo.submitFeedback(app, uid, groupId, d, started, text) }
                .onFailure {
                    feedbackToday = feedbackToday - app.id
                    message = it.friendly("We couldn't send that. Check your connection and try again.")
                }
        }
    }

    /**
     * Opts the caller in or out of testing one skippable app, on their own — see [TestApp.skippable]
     * and the `skips` collection.
     *
     * Updated locally on success rather than waiting for [watch]'s next snapshot, the same reason
     * [broadcast] does in DashboardViewModel: the write that just landed is the only thing that
     * could have changed this, so there is nothing a reload would tell us that isn't already known.
     */
    fun toggleSkip(app: TestApp) {
        val uid = AuthRepo.uid ?: return
        val groupId = group?.id ?: return
        val nowSkipped = app.id in skipped
        // Applied before the write settles, the way a checkbox should feel — and rolled back in
        // onFailure rather than left wrong, since this is the one piece of state here nothing else
        // re-derives if the write silently didn't take.
        skipped = if (nowSkipped) skipped - app.id else skipped + app.id
        viewModelScope.launch {
            runCatching {
                if (nowSkipped) Repo.clearSkip(app.id, uid) else Repo.setSkip(app, uid, groupId)
            }.onFailure {
                skipped = if (nowSkipped) skipped + app.id else skipped - app.id
                message = it.friendly(
                    "We couldn't save that. Check your connection and try again."
                )
            }
        }
    }

    /**
     * Saves the way into this app, then re-reads the group so the row shows what was saved.
     *
     * Nothing is put on screen ahead of the write, unlike [reportProblem]. This is the text
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
                            usageMs = capture.usageMs,
                            manual = capture.manual
                        )
                        runCatching { Repo.recordProof(proof) }
                            .onSuccess {
                                Telemetry.proofPosted()
                                file.delete()
                                if (proof.meetsBar) {
                                    doneToday = doneToday + app.id
                                    if (capture.manual) manualToday = manualToday + app.id
                                    Cache.put(Cache.doneToday(uid, cohort.id, d), doneToday)
                                    Cache.put(Cache.manualToday(uid, cohort.id, d), manualToday)
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
    /** What the caller already knew this group as, shown until [vm]'s own load answers the same
     *  question — see Routes.GROUP. Blank for the rare entry with no row to read it from. */
    groupName: String,
    /** An app to open the feedback prompt for as soon as it loads — see Routes.GROUP. Blank the
     *  ordinary way in here, from every entry that isn't a tapped "ask for feedback" notification. */
    feedbackForAppId: String = "",
    onOpenDashboard: (String, String) -> Unit,
    onBack: () -> Unit,
    vm: GroupViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    // Once per process. A visit whose capture failed before this screen was last open is still
    // sitting in local storage, waiting on a picture — see CaptureService.missed — and this is
    // what brings it back into CaptureService.missed after the app was closed or updated in the
    // meantime.
    LaunchedEffect(Unit) { CaptureService.restorePending(context) }

    LaunchedEffect(groupId) { vm.load(groupId) }

    var canReturn by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    /** The app a tester is about to report as uninstallable, or null. */
    var reporting by remember { mutableStateOf<TestApp?>(null) }

    /** The app a tester is about to leave a day-one note on, or null. */
    var givingFeedback by remember { mutableStateOf<TestApp?>(null) }

    // Opened once, the moment the target of a tapped "ask for feedback" notification actually
    // shows up in the list — vm.toTest starts empty every time this screen is composed fresh, so
    // firing on the param alone would miss it. Guarded so a later reload (pull, resume) does not
    // reopen a dialog the tester already dealt with.
    var feedbackAutoOpened by remember { mutableStateOf(false) }
    LaunchedEffect(vm.toTest) {
        if (!feedbackAutoOpened && feedbackForAppId.isNotBlank()) {
            vm.toTest.firstOrNull { it.id == feedbackForAppId }?.let {
                givingFeedback = it
                feedbackAutoOpened = true
            }
        }
    }

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

    /*
     * Nothing left owed today, so nothing left for the recording to be open for — ended here
     * rather than the moment a round's own queue empties, which is CaptureService's call and
     * deliberately not this: a round only covers what was outstanding when it started, and a
     * tester opening rows one at a time instead of tapping Start reaches the same "done" a step
     * at a time. Ending the session after the first of those would ask for a fresh consent
     * before every single one after it — the exact cost the round-level version of this was
     * built to avoid. Checked against every app owed, not just this round's queue, so it fires
     * once regardless of which way the tester got there.
     *
     * Not gated on CaptureService.missed being empty: supplying a screenshot by hand for a visit
     * that already ran is a gallery pick, not a new capture, and needs no live recording either
     * way.
     */
    LaunchedEffect(vm.doneToday, vm.toTest, CaptureService.roundActive) {
        if (vm.toTest.isNotEmpty() &&
            vm.doneToday.size == vm.toTest.size &&
            !CaptureService.roundActive
        ) {
            CaptureService.endSession(context)
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
    // here, because "nothing happened" is not something a list can express. The usage still
    // counted, though, so the row now also offers a way to supply a picture by hand — see
    // CaptureService.missed and TestRow's canPickScreenshot.
    LaunchedEffect(CaptureService.newlyMissed.size, vm.toTest) {
        val names = CaptureService.newlyMissed.map { visit ->
            vm.toTest.firstOrNull { it.packageName == visit.pkg }?.label ?: visit.pkg
        }
        if (names.isNotEmpty()) {
            val rest = names.size - 1
            vm.message =
                if (rest == 0) "We couldn't get a picture of ${names[0]}. Your time there still " +
                    "counted. Pick a screenshot for it below, or open it again."
                else "We couldn't get a picture of ${names[0]} and $rest more. Your time in them " +
                    "still counted. Pick a screenshot for each below, or open them again."
            CaptureService.forgetNewlyMissed()
        }
    }

    // Each queued app's sign-in note, for the overlay's copy button — see VisitOverlay.show and
    // TestApp.notes. Looked up fresh at the moment a round starts rather than carried on the
    // package list itself, since the queue is just names all the way down to the service.
    fun notesFor(packages: List<String>): Map<String, String> =
        vm.toTest.filter { it.packageName in packages && it.notes.isNotBlank() }
            .associate { it.packageName to it.notes }

    // Consent is one sheet for the whole round; the queue is held until it comes back approved.
    var queued by remember { mutableStateOf<List<String>>(emptyList()) }
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
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
        if (CaptureService.sessionActive) {
            CaptureService.startRound(context, packages, notesFor(packages))
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

    // Which app a picked image belongs to — the launcher's own callback carries only the Uri,
    // not which row asked for it.
    var pickingScreenshotFor by remember { mutableStateOf<String?>(null) }
    val pickScreenshotLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val pkg = pickingScreenshotFor
        pickingScreenshotFor = null
        if (uri == null || pkg == null) return@rememberLauncherForActivityResult
        if (!CaptureService.supplyManualCapture(context, pkg, uri)) {
            vm.message = "That visit isn't waiting for a picture any more. Open the app again."
        }
    }

    /**
     * Only offered for a visit already in [CaptureService.missed] — a real, already-served
     * twenty seconds whose automated frame did not save, not a way around actually opening the
     * app. See CaptureService.supplyManualCapture for what happens to the picked image.
     */
    fun pickScreenshot(pkg: String) {
        pickingScreenshotFor = pkg
        pickScreenshotLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val group = vm.group

    // A confirmation like "Report cleared" is not the same fact as "we couldn't reach the
    // server" — the first is good news about something that just finished, the second is a
    // standing problem worth reading carefully. Routing both through the same red inline text at
    // the bottom of a thirteen-row list meant the good news looked like an error and needed a
    // scroll to ever be seen. A Snackbar surfaces the instant it happens and clears itself; the
    // other screens' inline [Failure] text is left as-is for now, only this one moves.
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        val text = vm.message
        if (text != null && group != null) {
            snackbarHostState.showSnackbar(text)
            if (vm.message == text) vm.message = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        RefreshBox(
            // Bound to the pull, not the general refresh — see HomeScreen's own note on this.
            isRefreshing = vm.pulling,
            onRefresh = { vm.load(groupId, fromPull = true) },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            when {
                // A pull gets the same full skeleton a first load does — see HomeScreen's own
                // note on this.
                !vm.ready || vm.pulling -> SkeletonPage { SkeletonGroupAppList() }

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
                                broadcastCooldown = vm.broadcastCooldownRemaining(),
                                broadcasting = vm.broadcasting,
                                feedbackAskCooldown = vm.feedbackAskCooldownRemaining(),
                                askingForFeedback = vm.askingForFeedback,
                                onEditNotes = { editingNotes = app },
                                onBroadcast = { vm.broadcast() },
                                onAskForFeedback = { vm.askForFeedback() },
                                onClick = { onOpenDashboard(app.id, app.label) }
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
                                    canPickScreenshot = CaptureService.missed
                                        .any { it.pkg == app.packageName },
                                    onPickScreenshot = { pickScreenshot(app.packageName) }
                                )
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
                    maxLength = NOTES_MAX
                )
            },
            onConfirm = {
                reporting = null
                vm.reportProblem(app, reason.trim())
            },
            onDismiss = { reporting = null }
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
                    maxLength = Feedback.TEXT_MAX
                )
            },
            onConfirm = {
                givingFeedback = null
                vm.submitFeedback(app, note.trim())
            },
            onDismiss = { givingFeedback = null }
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
                supporting = "Everyone in the group and the admins can read this. Use a " +
                    "throwaway account, never your real password.",
                singleLine = false,
                // The same floor the other input dialogs give it, so a field asking for a
                // sign-in note reads as wanting a couple of lines rather than one word.
                minHeight = 120.dp,
                maxLength = NOTES_MAX
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
            // The same segmented bar the home screen uses. One bar per app, so the day reads as
            // twelve things to do rather than a percentage, and the two screens agree about what
            // the same day looks like.
            Meter(done = done, total = total)

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
                Spacer(Modifier.height(12.dp))
                DayEndBlock(day = day, endsAt = ends)
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
 * When the day turns over, spelled out and counted down.
 *
 * The run's clock starts at the hour the group did, so this is almost never midnight and there is
 * no way to work it out from anything else on screen. Somebody testing after dinner can put half
 * a round on one day and half on the next and see only that their count went back to zero.
 *
 * The clock time is in it as well as the countdown. "3h left" on its own forces the reader to do
 * the arithmetic themselves if they want to know whether that is worth waiting up for; the two
 * together answer both "how soon" and "how late" in one glance.
 */
@Composable
private fun DayEndBlock(day: Int, endsAt: Long) {
    val context = LocalContext.current
    val clockTime = remember(endsAt) {
        val time = DateFormat.getTimeFormat(context).format(Date(endsAt))
        val today = DateFormat.getDateFormat(context).format(Date())
        val then = DateFormat.getDateFormat(context).format(Date(endsAt))
        if (today == then) "$time today" else "$time tomorrow"
    }

    // Recomputed on a timer rather than once: a countdown that never counts down is just the
    // clock time again, in more words.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val remaining = (endsAt - now).coerceAtLeast(0L)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Status.neutralSoft)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.HourglassEmpty,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                formatCountdown(remaining),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Day ${day + 1} ends $clockTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** "3h 42m left", or just the minutes once it's under an hour — nobody needs the seconds. */
private fun formatCountdown(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "${days}d ${hours}h left"
        hours > 0 -> "${hours}h ${minutes}m left"
        else -> "${minutes}m left"
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
                // The explanation used to sit under the button on every visit to this screen,
                // which is fine reading it once and furniture every time after. Behind the icon
                // it is there exactly when it's wanted, and takes nothing from anyone who
                // already knows how a round works.
                var showHowItWorks by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Primary(
                        "Start testing · $outstanding left",
                        tall = true,
                        modifier = Modifier.weight(1f),
                        onClick = onStart
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { showHowItWorks = true },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "How a round works",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (showHowItWorks) {
                    Ask(
                        title = "How a round works",
                        /*
                         * This used to end "so leave the phone alone until you're back here",
                         * which read as an instruction to sit still through the round. It was
                         * aimed at the wrong thing: what breaks a visit is switching away from
                         * the app, not touching it. Told to do nothing, a tester does nothing,
                         * and twelve people opening apps and staring at them is not testing and
                         * would not survive anybody looking at it.
                         */
                        body = "Each app opens for about twenty seconds, then moves to the " +
                            "next one on its own. Have a proper look while you're in there. " +
                            "The screenshot is taken at a moment you won't know in advance, " +
                            "so stay in the app until it hands over.",
                        confirm = "Got it",
                        onConfirm = { showHowItWorks = false },
                        onDismiss = { showHowItWorks = false },
                        dismiss = null
                    )
                }
                if (CaptureService.sessionActive) {
                    TextButton(
                        onClick = onEndSession,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { Text("Stop screen sharing") }
                } else {
                    Spacer(Modifier.height(8.dp))
                }
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
    broadcastCooldown: Long,
    broadcasting: Boolean,
    feedbackAskCooldown: Long,
    askingForFeedback: Boolean,
    onEditNotes: () -> Unit,
    onBroadcast: () -> Unit,
    onAskForFeedback: () -> Unit,
    onClick: () -> Unit
) {
    val expected = (group.size - 1).coerceAtLeast(0)

    // Until now this row had no way to say it at all — an owner found out their own app had
    // been reported broken only by opening the Dashboard, if they thought to. "Reported" would
    // collide with the stat line right below it, which already uses that word for "posted a
    // proof" — see DayState.POSTED's legend — so the count is worded as "tested" here instead,
    // just on this row, rather than renaming that vocabulary everywhere it's established.
    val problem = app.reportedAt > 0L

    // Brought in line with TestRow: same icon-matches-the-text-block sizing, actions on their
    // own line as chips rather than a text button, and the row itself — not a trailing arrow —
    // is what says "there's more here, tap it."
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Gutter, vertical = 10.dp)
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .heightIn(min = 44.dp, max = 58.dp)
                    .fillMaxHeight()
                    .aspectRatio(1f)
            ) {
                AppIcon(app.packageName, app.label, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        app.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (problem) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Status.shortSoft)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Reported",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Status.short
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        problem -> app.reportedReason.ifBlank {
                            "Marked as broken. Excused until you sort it out."
                        }
                        group.dayIndex() != null -> "$reporters of $expected tested today"
                        else -> "Waiting for the run to start"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem) Status.short else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Two labels, because the two states are not the same job. An owner who has never
            // written notes has no reason to expect the row hides a field, and "Notes" on its
            // own reads as a heading rather than something to press.
            ActionChip(
                if (app.notes.isBlank()) "Add notes" else "Edit notes",
                MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft,
                onEditNotes
            )

            // Only once there's a cohort to tell — nothing to broadcast to while the group is
            // still forming. Cooldown shown rather than merely enforced, the same reasoning as
            // Dashboard's own copy of this button: a chip that just refuses to do anything reads
            // as broken, not as "wait a while."
            if (group.dayIndex() != null && expected > 0) {
                ActionChip(
                    when {
                        broadcasting -> "Sending…"
                        broadcastCooldown > 0L -> "Notify again in ${formatWait(broadcastCooldown)}"
                        else -> "Notify testers"
                    },
                    if (broadcastCooldown > 0L || broadcasting) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.primary,
                    Status.neutralSoft
                ) {
                    if (broadcastCooldown <= 0L && !broadcasting) onBroadcast()
                }

                // [broadcast]'s reverse and its own cooldown — an owner curious how it's going
                // for their cohort has had no way to ask, short of writing to each of them one at
                // a time outside the app entirely.
                ActionChip(
                    when {
                        askingForFeedback -> "Sending…"
                        feedbackAskCooldown > 0L -> "Ask again in ${formatWait(feedbackAskCooldown)}"
                        else -> "Ask for feedback"
                    },
                    if (feedbackAskCooldown > 0L || askingForFeedback) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.primary,
                    Status.neutralSoft
                ) {
                    if (feedbackAskCooldown <= 0L && !askingForFeedback) onAskForFeedback()
                }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TestRow(
    app: TestApp,
    done: Boolean,
    /** Today's [done] came from a screenshot picked by hand — marked yellow, not green. */
    manual: Boolean,
    /** The run is under way — the only state in which a visit can be banked against a day. */
    live: Boolean,
    /** Distinguishes a run that is over from one that has not begun. Only read when not [live]. */
    ended: Boolean,
    /** This app is on screen right now, being visited. */
    busy: Boolean,
    /** This app's proof is uploading. Not done yet, and not something to start again either. */
    sending: Boolean,
    /** Whose app this is, if the roster has arrived. The row works without it. */
    owner: Tester?,
    /** Whether the caller has personally opted out of this app. Only ever true if [TestApp.skippable]. */
    skipped: Boolean,
    /** A visit already served, waiting only on a picture — see CaptureService.missed. */
    canPickScreenshot: Boolean,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onOpenStore: () -> Unit,
    onReportProblem: () -> Unit,
    onClearReport: () -> Unit,
    /** Whether the caller has already left today's note on this app — see [Repo.submitFeedback]. */
    feedbackGiven: Boolean,
    onGiveFeedback: () -> Unit,
    /** Tapped the chip again after already sending today's note. */
    onFeedbackAlreadyGiven: () -> Unit,
    onToggleSkip: () -> Unit,
    onPickScreenshot: () -> Unit
) {
    val context = LocalContext.current
    // Keyed on the revision as well as the package: without it this row holds the answer it was
    // first given, and an app installed since then keeps offering Install.
    val info = remember(app.packageName, InstalledApps.revision) {
        InstalledApps.cachedInfo(context, app.packageName)
    }

    var details by remember { mutableStateOf(false) }
    if (details) AppDetails(app) { details = false }

    var reportDetails by remember { mutableStateOf(false) }
    if (reportDetails) {
        // Only the person who filed it gets a way to take it back — everyone else reading this
        // dialog is just catching up on why the row says what it says.
        val mine = app.reportedBy.isNotBlank() && app.reportedBy == AuthRepo.uid
        Ask(
            title = "Reported",
            body = app.reportedReason.ifBlank { "No further detail was given." },
            confirm = if (mine) "Clear report" else "Close",
            onConfirm = {
                reportDetails = false
                if (mine) onClearReport()
            },
            onDismiss = { reportDetails = false },
            dismiss = if (mine) "Close" else null,
            destructive = mine
        )
    }

    val iconGap = 12.dp

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Gutter, end = 12.dp, top = 10.dp, bottom = 10.dp)
    ) {
        Row(
            // Icon squared off against the three lines beside it rather than a fixed number, so
            // it grows or shrinks with whatever the tester's own text size setting makes those
            // three lines add up to.
            Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /*
             * How long they have held on to it, on the icon it belongs to.
             *
             * This was a line of text under the package name, which is a whole row of type spent
             * on one small number, thirteen times down the screen. Play counts continuous days
             * installed and nothing else, so it is worth showing and not worth a line.
             *
             * Long press gives the sentence back. A corner badge reading "2d" is a reminder for
             * somebody who already knows what it counts, and a tooltip is where the answer goes
             * for somebody who does not.
             */
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(info.streakLabel) } },
                state = rememberTooltipState(),
                // Nothing to explain where there is no badge, and a tooltip that opens on an app
                // the tester has not installed yet would be answering a question nobody asked.
                enableUserInput = info.streakBadge != null
            ) {
                Box(
                    Modifier
                        .heightIn(min = 44.dp, max = 58.dp)
                        .fillMaxHeight()
                        .aspectRatio(1f)
                ) {
                    AppIcon(app.packageName, app.label, modifier = Modifier.fillMaxSize())
                    info.streakBadge?.let { badge ->
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 5.dp, y = 5.dp)
                                // Painted in the page ground and padded, which draws a ring
                                // cutting the badge out of the icon behind it. Without it the
                                // pill sits on whatever colour that corner of the icon happens
                                // to be.
                                .clip(RoundedCornerShape(9.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .padding(2.dp)
                        ) {
                            Text(
                                badge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Status.neutralSoft)
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(iconGap))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        /*
                         * What the developer left for you, when there is any.
                         *
                         * The row can only ever show the first two lines of it and none of it can
                         * be selected, which is the one thing a test login exists to be: copied
                         * into somebody else's sign-in screen. Shown only where notes exist, so
                         * the button is a promise that there is something behind it rather than
                         * furniture on every row.
                         */
                        if (app.notes.isNotBlank()) {
                            Spacer(Modifier.width(2.dp))
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .clickable { details = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = "How to get into ${app.label}",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    /*
                     * Today's answer, on the name it is about — a glyph, not a word. The row
                     * used to say it three different ways further down (a pill, a colour, an
                     * icon-only button in disguise); one mark up here, checked or not, replaces
                     * all of them.
                     */
                    Icon(
                        if (done) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                        contentDescription = if (done) "Done today" else "Not done today",
                        modifier = Modifier.size(18.dp),
                        tint = when {
                            !done -> MaterialTheme.colorScheme.outline
                            manual -> Status.short
                            else -> Status.posted
                        }
                    )
                }
                /*
                 * Whose app this is, on two lines of its own.
                 *
                 * A cohort is thirteen strangers holding each other to a fortnight, and the only
                 * other thing on the row is a package name. Somebody who cannot get past a login
                 * screen needs to know who to ask, and somebody deciding whether the notes are
                 * worth trusting needs to know who wrote them.
                 *
                 * Name above address, not joined by a separator: the two ran together as
                 * "abdudevs · abdudevs@gmail.com" often enough to look broken, because Google
                 * hands back a display name for every account and for most of these it is the
                 * address with the domain taken off. Stacked, the repetition reads as one person
                 * rather than a bug, and the address gets the whole width instead of being cut
                 * in half.
                 */
                val who = remember(owner, app.ownerEmail) {
                    owner?.displayName?.takeIf { it.isNotBlank() }
                        ?: app.ownerEmail.substringBefore('@')
                }
                // Both lines always reserved, blank or not — a row missing owner data used to be
                // two lines shorter than one that had it, which is the bulk of why rows in this
                // list did not line up. A blank line here reads as nothing to say, not as a
                // layout glitch.
                Spacer(Modifier.height(3.dp))
                Text(
                    who.ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    app.ownerEmail.ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        /*
         * Every action this row could take, flush with the row's own left edge rather than
         * indented under the icon, scrolling sideways rather than wrapping so the row's height
         * never depends on how many of them apply.
         */
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                busy -> Working("Opening")

                // Ahead of everything else, including Install/Open. An upload in flight is
                // about this exact row and outranks anything the row would otherwise be saying.
                sending -> Working("Uploading")

                // A visit already served, waiting only on a picture — see
                // CaptureService.missed. Ahead of Reported: reporting an app excuses it from
                // judgment, but it does not un-happen a visit that already earned its usage
                // figure.
                canPickScreenshot -> ActionChip("Pick a screenshot", Status.short, Status.shortSoft, onPickScreenshot)

                // Tappable, not a label — the whole point is to read back what was typed, see
                // [reportDetails]. Gone once [done]: this tester just proved the app works by
                // testing it, so there is nothing current left for the report to excuse.
                app.reportedAt > 0L && !done -> ActionChip("Reported", Status.short, Status.shortSoft) { reportDetails = true }

                !info.installed -> ActionChip("Install", Status.missed, Status.missedSoft, onInstall)

                // Today's visit is banked. Open used to stay regardless, on the idea that
                // finishing today is not a reason to take away the only way into the app — but
                // mid-round that read as the row asking to be opened again right after it just
                // finished, which is exactly backwards. Tomorrow this clears with [done] itself.
                //
                // Nothing shown here at all, Done pill included: the checkbox beside the app's
                // name already says done, top-right of the same row, and repeating it here said
                // the same word twice for every finished app in the list.
                live && done -> Unit

                // Off the clock. `live` alone was not enough for this once it stayed true after
                // a run ends — a day with no run left to file a visit under has nothing here to
                // offer either way.
                live -> ActionChip(
                    "Open",
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    MaterialTheme.colorScheme.primaryContainer,
                    onOpen
                )

                else -> Pill(
                    if (ended) "Ended" else "Waiting",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    Status.neutralSoft
                )
            }

            // The only route to a newer build once one is out — kept separate from Open/Install
            // above rather than folded into either, since checking the Store is not the same
            // errand as opening the app or installing it the first time.
            if (info.installed && !busy && !sending) {
                ActionChip("Update", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft, onOpenStore)
            }

            /*
             * Available whether or not it is installed — won't-install and crashes-on-open are
             * both things only the tester staring at the error can see. Gone once reported: a
             * second report would only be refused, see firestore.rules' set-once guard, and the
             * Reported chip above already reads the first one back.
             */
            if (app.reportedAt <= 0L) {
                ActionChip(
                    "Report",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    Status.neutralSoft,
                    onReportProblem
                )
            }

            /*
             * A note about the app, not a complaint about it — nothing here excuses the day, so
             * it stays offered whether or not [done] yet. Installed only: there's nothing to have
             * found in an app the tester hasn't opened. Muted once sent, but still tappable, so a
             * second tap gets an answer instead of silently doing nothing.
             */
            if (info.installed && app.reportedAt <= 0L) {
                if (feedbackGiven) {
                    ActionChip("Feedback", MaterialTheme.colorScheme.outline, Status.neutralSoft, onFeedbackAlreadyGiven)
                } else {
                    ActionChip("Feedback", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft, onGiveFeedback)
                }
            }

            /*
             * Only where an admin has opened this specific app up to it — most rows never show
             * this at all. Independent of Install/Open on purpose: skipping is about what counts
             * against the day, not about hiding the app, so a tester can still install and open
             * it later even after marking it skipped, and un-skipping is the same tap in reverse.
             */
            if (app.skippable) {
                if (skipped) {
                    ActionChip("Skipped", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft, onToggleSkip)
                } else {
                    ActionChip("Skip", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft, onToggleSkip)
                }
            }
        }
    }
}

/**
 * A [TextButton] enforces Material's ~40dp minimum touch height no matter what contentPadding
 * says, which is too tall for a row meant to hold five or six of these side by side. Built on a
 * plain clickable [Box] instead — the same shape the [reportDetails]-reading "Reported" chip
 * already used — so the padding below is the actual, final size.
 */
@Composable
private fun ActionChip(text: String, tone: Color, background: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = tone,
            maxLines = 1
        )
    }
}

/**
 * What the developer wrote down so you could get past their front door.
 *
 * Whose app it is stays on the row, where it is read; this is only the part that gets *used*. A
 * test login is typed into another app's sign-in screen, and it was on the row until now in the
 * worst possible form: cut off after two lines, and not selectable, which is the one thing a
 * password is for.
 *
 * Selectable, not editable. A text field would offer a keyboard and a cursor for text nobody here
 * is allowed to change, and a value that looks editable gets typed into anyway. Long press picks
 * it up, which is the gesture people already use on a message.
 */
@Composable
private fun AppDetails(app: TestApp, onDismiss: () -> Unit) {
    Ask(
        title = app.label,
        confirm = "Close",
        onConfirm = onDismiss,
        onDismiss = onDismiss,
        // Nothing is being decided here, so a Cancel next to Close would be two words for one door.
        dismiss = null,
        content = {
            // No label over it. The dialog is titled with the app and holds one thing, so a
            // heading would only be a word explaining the only word on screen.
            SelectionContainer {
                Text(
                    app.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Notes run to 500 characters and the dialog would otherwise grow until
                        // the buttons went off the bottom of the screen.
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(10.dp))
                        .background(Status.neutralSoft)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
    )
}

