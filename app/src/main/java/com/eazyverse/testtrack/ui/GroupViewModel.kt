package com.eazyverse.testtrack.ui

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class GroupViewModel : ViewModel() {
    /**
     * Serializes [publish]'s uploads.
     *
     * A round finishes with every visit landing at once, and that used to mean up to fourteen
     * concurrent Drive uploads and auth fetches — exactly the burst that trips transient failures
     * (rate limits, token contention). One at a time gives Drive far less to contend with, and as
     * a side effect stops [DriveRepo.folderId]'s lookup-or-create from racing itself into
     * duplicate folders when the very first upload of a session is what creates one.
     */
    private val uploadMutex = Mutex()
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

    /** This group's own record of what happened to me — see AdminRepo.logHistory in the admin app. */
    var myHistory by mutableStateOf<List<History>>(emptyList())
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
                }.onFailure {
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
                }.onFailure {
                    message = it.friendly("We couldn't send that. Check your connection and try again.")
                }
            askingForFeedback = false
        }
    }

    /** Installed, in the run, and not yet banked — exactly what a round should walk. */
    fun outstanding(context: Context): List<TestApp> =
        if (day == null) {
            emptyList()
        } else {
            toTest.filter {
                it.id !in doneToday && InstalledApps.cachedInfo(context, it.packageName).installed
            }
        }

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
        watching =
            viewModelScope.launch {
                Repo
                    .watchAppsInGroup(groupId)
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

    fun load(
        groupId: String,
        fromPull: Boolean = false,
    ) {
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
                val owed =
                    apps
                        .asSequence()
                        .filter { it.ownerUid != uid && it.active }
                        .mapTo(HashSet()) { it.id }
                val details =
                    if (d == null) {
                        emptyMap()
                    } else {
                        Repo
                            .myProofDetailsForDay(uid, groupId, d, found.startDate)
                            .filterKeys { it in owed }
                    }
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
                    if (d == null || own == null) {
                        0
                    } else {
                        Repo
                            .proofsForApp(own.id, own.ownerUid)
                            .filter { it.day == d && it.meetsBar && it.runStartedAt == found.startDate }
                            .distinctBy { it.testerUid }
                            .size
                    }
                val people = runCatching { Repo.testers(found.memberUids) }.getOrDefault(emptyList())
                // Same reasoning as [people]: a checkbox that failed to load is not worth taking
                // the rest of the list down for.
                val mySkips = runCatching { Repo.mySkips(uid, groupId) }.getOrDefault(emptySet())
                val myFeedback =
                    if (d == null) {
                        emptySet()
                    } else {
                        runCatching {
                            Repo.myFeedbackToday(uid, groupId, d, found.startDate)
                        }.getOrDefault(emptySet())
                    }
                val history = runCatching { Repo.myHistory(groupId, uid) }.getOrDefault(emptyList())

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
                myHistory = history
                // Failure here is not failure of the screen. A row with no name against it is a
                // row that still opens, so the roster is fetched with its own runCatching rather
                // than taking the day's list down with it.
                if (people.isNotEmpty()) owners = people.associateBy { it.uid }

                Cache.put(Cache.group(groupId), found)
                Cache.put(Cache.testers(groupId), people)
                Cache.put(Cache.appsIn(groupId), apps)
                Cache.put(Cache.skips(uid, groupId), mySkips)
                Cache.put(Cache.myHistory(uid, groupId), history)
                if (d != null) {
                    Cache.put(Cache.doneToday(uid, groupId, d), banked)
                    Cache.put(Cache.manualToday(uid, groupId, d), manual)
                    Cache.put(Cache.feedbackToday(uid, groupId, d), myFeedback)
                    own?.let { Cache.put(Cache.reporters(it.id, d), reporters) }
                }
                message = null
            }.onFailure {
                message =
                    it.friendly(
                        "We couldn't load this crew. Check your connection and try again.",
                        denied =
                            "You're not in this crew any more. It may have been dissolved, or " +
                                "your app may have been taken out of it.",
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
    private fun showCached(
        uid: String,
        groupId: String,
    ) {
        val cached = Cache.get<TestGroup>(Cache.group(groupId)) ?: return
        val apps = Cache.get<List<TestApp>>(Cache.appsIn(groupId)) ?: return
        val own = apps.firstOrNull { it.ownerUid == uid }
        val d = cached.dayIndex()

        val banked =
            if (d == null) {
                emptySet()
            } else {
                Cache.get<Set<String>>(Cache.doneToday(uid, groupId, d)) ?: return
            }
        val reporters =
            if (d == null || own == null) {
                0
            } else {
                Cache.get<Int>(Cache.reporters(own.id, d)) ?: return
            }

        // Not a `?: return` like the others. A name is a nicety on a row that works without one,
        // so its absence should not hold back a screen the cache can otherwise draw in full. The
        // manual/automated split is the same: worth showing when it's in hand, not worth blocking
        // the rest of the screen over.
        Cache
            .get<List<Tester>>(Cache.testers(groupId))
            ?.let { owners = it.associateBy { person -> person.uid } }
        Cache.get<Set<String>>(Cache.skips(uid, groupId))?.let { skipped = it }
        Cache.get<List<History>>(Cache.myHistory(uid, groupId))?.let { myHistory = it }
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
     * Says an app would not install, crashed, or otherwise could not be tested — and stops it
     * counting against the day, from right now, without waiting on an admin to notice.
     *
     * Two things happen, and only the first is answered on screen straight away: the Firestore
     * write is what actually excuses the day (see [Compliance] and the rules' `isReported`), and
     * the best-effort push is what gets an admin looking at it sooner rather than at their next
     * visit to the console. The write's own result is what [watch] picks up and turns into the
     * row's "Reported" state, so nothing here needs to re-read the group by hand.
     */
    fun reportProblem(
        app: TestApp,
        reason: String,
    ) {
        val uid = AuthRepo.uid ?: return
        message = "Thanks. ${app.label} won't count against your day until an admin looks into it."
        viewModelScope.launch {
            runCatching { Repo.reportProblem(app.id, uid, reason) }
                .onFailure {
                    message =
                        it.friendly(
                            "We couldn't send that report. Check your connection and try again.",
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
    fun submitFeedback(
        app: TestApp,
        text: String,
    ) {
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
                message =
                    it.friendly(
                        "We couldn't save that. Check your connection and try again.",
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
    fun updateNotes(
        app: TestApp,
        notes: String,
    ) {
        val groupId = group?.id ?: return
        message = null
        viewModelScope.launch {
            runCatching { Repo.updateNotes(app.packageName, notes) }
                .onSuccess { load(groupId) }
                .onFailure {
                    message =
                        it.friendly(
                            "We couldn't save your notes. Check your connection and try again.",
                        )
                }
        }
    }

    /**
     * Uploads a finished visit and records it.
     *
     * Drive first, Firestore second: the proof document points at a Drive file, so writing the
     * document before the upload lands would leave a grid cell referencing nothing.
     */
    fun publish(
        activity: Activity,
        app: TestApp,
        capture: Capture,
    ) {
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
            // Only a visit that genuinely finished — uploaded and recorded — is consumed below.
            // A transient failure used to be indistinguishable from success as far as the queue
            // was concerned: either way it vanished from CaptureService.results. Now it stays
            // there, so the next time this same drain effect runs (the next visit that lands, or
            // simply leaving and returning to this screen) picks it back up and tries again,
            // instead of the tester's already-earned visit being gone for good.
            val recorded =
                uploadMutex.withLock {
                    val token = AuthRepo.driveTokenOrNull(activity)
                    if (token == null) {
                        // Trigger the Drive prompt on this testing screen.
                        // Keep the capture until access is restored and upload succeeds.
                        // Other app features do not require this permission.
                        Session.updateDriveConnected(false)
                        message = "Drive access is needed to upload this test proof. " +
                            "Connect it here to continue; your screenshot has been kept."
                        false
                    } else {
                        val file = File(capture.path)
                        val name = "${app.packageName}_day${d}_${uid.take(6)}.jpg"
                        when (val result = DriveRepo.upload(token, file, name)) {
                            is UploadResult.Failed -> {
                                message = result.reason
                                false
                            }
                            is UploadResult.Ok -> {
                                val proof =
                                    Proof(
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
                                        manual = capture.manual,
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
                                    }.onFailure {
                                        message =
                                            it.friendly(
                                                "We couldn't save that proof. Check your connection and try again.",
                                                denied =
                                                    "You're not in this crew any more, so today's test " +
                                                        "can't be recorded against it.",
                                            )
                                    }.isSuccess
                            }
                        }
                    }
                }
            if (recorded) CaptureService.consume(app.packageName)
            uploading -= 1
            sending = sending - app.id
        }
    }
}
