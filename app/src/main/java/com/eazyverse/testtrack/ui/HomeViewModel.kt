package com.eazyverse.testtrack.ui

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** One group as home needs it: the cohort, plus how much of today this tester still owes it. */
data class GroupProgress(
    val group: TestGroup,
    val toTest: Int,
    val done: Int,
)

class HomeViewModel : ViewModel() {
    var groups by mutableStateOf<List<GroupProgress>>(emptyList())
        private set
    var pending by mutableStateOf<List<TestApp>>(emptyList())
        private set

    /**
     * A background refetch is in flight while cached numbers are still on screen.
     *
     * [ready] is sticky-true once first set, on purpose — see its own doc — which means it
     * cannot also mark "this refresh is still running." Without a second flag, numbers left over
     * from before a group was edited elsewhere sit on screen looking current for however long the
     * refetch takes, with nothing to say they might be about to change.
     */
    var refreshing by mutableStateOf(false)
        private set

    /**
     * The reader pulled for this themselves, rather than the screen doing it quietly on its own.
     *
     * A pull is asking outright, which earns the same full skeleton a first load gets — not the
     * small mark [refreshing] leaves in the app bar for a refresh nobody asked for, on resuming
     * or coming back from a group.
     */
    var pulling by mutableStateOf(false)
        private set

    /**
     * Everything this screen shows is in hand.
     *
     * One flag for the page, not one per list: whichever half arrives first would otherwise be
     * drawn over the half that hasn't, and a total struck across sections is wrong until both are
     * counted. Sticky once set — a refresh over content already on screen must not blank it.
     */
    var ready by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)

    /** Days already missed, and how close that is to costing them the group. */
    var standings by mutableStateOf<List<Enforcement.Standing>>(emptyList())
        private set

    /** Removals and departures, held until acknowledged rather than only sent as notifications. */
    var notices by mutableStateOf<List<Enforcement.Notice>>(emptyList())
        private set

    /**
     * Notifications are switched off, and this account is in a running cohort.
     *
     * Worth a prompt now that a missed day costs something: the warning that says "open them today
     * or your app leaves the group" arrives as a notification, and a tester who declined the
     * permission gets no warning at all before the removal lands.
     */
    var askForNotifications by mutableStateOf(false)
        private set

    /** Across every cohort — the number the tester actually cares about first thing. */
    val doneToday: Int get() = groups.sumOf { it.done }
    val dueToday: Int get() = groups.sumOf { it.toTest }

    /**
     * This account's own face, for the button that leads to it.
     *
     * Its own state rather than part of [load]'s batch, because it is the one thing on this screen
     * that is not about today and must not hold the rest of it up. A failed read leaves the
     * initial showing, which is what most accounts see anyway.
     */
    var me by mutableStateOf<Tester?>(null)
        private set

    val photo: String get() = me?.photo.orEmpty()
    val initial: String
        get() = me?.initial ?: Session.email?.firstOrNull()?.uppercase() ?: "?"

    private fun loadMe() {
        val uid = AuthRepo.uid ?: return
        viewModelScope.launch { runCatching { me = Repo.me(uid) } }
    }

    /** [fromPull] is the reader asking outright — see [pulling]'s own doc for what that changes. */
    fun load(fromPull: Boolean = false) {
        val uid =
            AuthRepo.uid ?: run {
                ready = true
                return
            }

        loadMe()
        showCached(uid)

        viewModelScope.launch {
            refreshing = ready // only worth flagging over content that's already on screen
            if (fromPull) pulling = true
            runCatching {
                val waiting = Repo.pendingApps(uid)
                // Each group's own two reads run concurrently with every other group's, not
                // queued behind them — with N groups this was up to 2N sequential round trips
                // before anything on screen changed, which is the multi-second window where
                // Home kept showing yesterday's numbers after an edit elsewhere.
                val progress =
                    coroutineScope {
                        Repo
                            .myGroups(uid)
                            .map { group ->
                                async {
                                    // active, not placed: a removed app is still in the group and is
                                    // owed nothing, so counting it here would ask for a day on an app
                                    // this tester has been told to uninstall.
                                    val others =
                                        Repo
                                            .appsInGroup(group.id)
                                            .filter { it.ownerUid != uid && it.active }
                                    val day = group.dayIndex()
                                    // Meant against today's roster, not raw proof count: a proof posted
                                    // earlier today for an app that has since been taken out of testing
                                    // (or reported, or the owner's own) is still a proof, but it is no
                                    // longer one of the apps [others] is counting — without this filter
                                    // it kept counting toward "done" anyway, and the header could read
                                    // more done than there were apps to do, like "26 of 25".
                                    val done =
                                        if (day == null) {
                                            0
                                        } else {
                                            val owed = others.mapTo(HashSet()) { it.id }
                                            Repo
                                                .myProofsForDay(uid, group.id, day, group.startDate)
                                                .count { it in owed }
                                        }
                                    GroupProgress(group, others.size, done)
                                }
                            }.awaitAll()
                    }

                // Published together, at the end. Assigning as each query lands would repaint a
                // screen that already has content with a half-updated one.
                pending = waiting
                groups = progress
                Cache.put(Cache.pending(uid), waiting)
                Cache.put(Cache.groups(uid), progress)
                message = null
            }.onFailure {
                message =
                    it.friendly(
                        "We couldn't load your crews. Check your connection and try again.",
                    )
            }
            ready = true
            refreshing = false
            pulling = false
        }
    }

    /**
     * Makes this device reachable, and puts it on the right lists.
     *
     * Here rather than at sign-in because the cohort is only known once the groups are loaded, and
     * because membership changes without the app being reinstalled — an admin places an app, and
     * the next time home refreshes, the topic follows.
     */
    fun registerForPush(context: Context) {
        val uid = AuthRepo.uid ?: return
        viewModelScope.launch {
            PushRepo.register(context, uid)
            if (ready) PushRepo.syncTopics(context, groups.map { it.group.id })
        }
    }

    /**
     * Tells each of the caller's own apps what their own device can see of itself.
     *
     * Only a Play-sourced install counts — see [InstalledApps.Info.fromPlay] — because that is
     * the one fact on the device saying this exact build actually came through Play's pipeline
     * rather than a local Android Studio run, which is what most owners have on this same phone
     * most of the time. Advances [TestApp.latestVersionCode] only forward, so a stray older
     * install lying around never regresses what the cohort is told.
     */
    fun syncMyVersions(context: Context) {
        val uid = AuthRepo.uid ?: return
        viewModelScope.launch {
            runCatching {
                Repo.myApps(uid).forEach { app ->
                    val info = InstalledApps.info(context, app.packageName)
                    if (info.installed && info.fromPlay && info.versionCode > app.latestVersionCode) {
                        Repo.updateLatestVersion(app.packageName, info.versionCode, info.versionName.orEmpty())
                    }
                }
            }
        }
    }

    /**
     * The daily pass, plus everything waiting, in the order the two have to happen.
     *
     * Scheduled here rather than at sign-in because this is the screen a tester always reaches,
     * and re-scheduling is free: the work is unique and KEEP, so opening the app twenty times does
     * not push the evening reminder twenty times further away.
     *
     * Drain first and sweep second, in one coroutine rather than two. They overlap on exactly one
     * thing: a removal is both an event addressed to this device and a cohort that has vanished
     * from under it. The event is the better of the two messages because it knows the cause, so it
     * claims the group and the sweep stays quiet — which only works if the event has already been
     * read by the time the sweep looks.
     */
    fun catchUp(context: Context) {
        val uid = AuthRepo.uid ?: return
        ReminderWorker.schedule(context)
        viewModelScope.launch {
            runCatching { AdminEvents.drain(context, uid) }
            standings = Enforcement.sweep(context, uid).standings
            notices = Enforcement.notices(context)
            refreshNotificationPrompt(context)
        }
    }

    /** Anything parked by a background sweep, picked up when the screen opens. */
    fun showNotices(context: Context) {
        notices = Enforcement.notices(context)
        refreshNotificationPrompt(context)
    }

    fun dismissNotice(
        context: Context,
        id: String,
    ) {
        Enforcement.dismiss(context, id)
        notices = Enforcement.notices(context)
    }

    /** Closed by the tester. Stays gone until a fresh sweep has something new to say about it. */
    fun dismissStanding(
        context: Context,
        standing: Enforcement.Standing,
    ) {
        Enforcement.dismissStanding(context, standing)
        standings = standings - standing
    }

    private fun refreshNotificationPrompt(context: Context) {
        askForNotifications = !PushRepo.granted(context) && groups.any { it.group.running }
    }

    /** Both halves or neither — see [ready]. A partial hit is a head start, not a screen. */
    private fun showCached(uid: String) {
        val cachedGroups = Cache.get<List<GroupProgress>>(Cache.groups(uid)) ?: return
        val cachedPending = Cache.get<List<TestApp>>(Cache.pending(uid)) ?: return
        groups = cachedGroups
        pending = cachedPending
        ready = true
    }

    /**
     * Unhooks the account, then hands back to navigate.
     *
     * `viewModelScope` outlives the dialog and is only torn down by the navigation [then]
     * performs, so the await always completes. See [releaseSession] for why the order matters.
     */
    fun signOut(
        context: Context,
        then: () -> Unit,
    ) {
        viewModelScope.launch {
            releaseSession(context)
            then()
        }
    }

    fun withdraw(app: TestApp) {
        message = null
        viewModelScope.launch {
            runCatching { Repo.withdrawApp(app.packageName) }
                .onSuccess { load() }
                .onFailure { message = it.friendly("We couldn't withdraw ${app.label}. Try again in a moment.") }
        }
    }

    /**
     * Saves the way into an app that is still waiting for a group.
     *
     * Reloads rather than patching the row in place, for the same reason [withdraw] does: this
     * list is whatever Firestore last said, and a row edited locally would keep showing the new
     * login even on the attempt where the write never landed.
     */
    fun updateNotes(
        app: TestApp,
        notes: String,
    ) {
        message = null
        viewModelScope.launch {
            runCatching { Repo.updateNotes(app.packageName, notes) }
                .onSuccess { load() }
                .onFailure {
                    message =
                        it.friendly(
                            "We couldn't save your notes for ${app.label}. Try again in a moment.",
                        )
                }
        }
    }
}
