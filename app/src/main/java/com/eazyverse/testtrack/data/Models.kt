package com.eazyverse.testtrack.data

/**
 * How long Play requires testers to stay opted in, and the length every run starts at.
 *
 * A starting point rather than a fixed rule: an admin can extend a cohort past it, so anything
 * reading a run's length must ask the group ([TestGroup.runDays]) instead of this constant. Kept
 * for the default and for the width of a grid nobody has extended.
 */
const val RUN_DAYS = 14

/**
 * The longest a set of [TestApp.notes] can be.
 *
 * A cap exists because the notes are read on a phone, in a list row alongside twelve other apps.
 * Left unbounded, one developer pasting their whole onboarding guide turns the daily list every
 * other member opens into a wall of scrolling, and the twelve rows they actually came for go
 * below the fold. Five hundred characters is enough for an account, a code and a sentence of
 * explanation, which is all this field is for.
 */
const val NOTES_MAX = 500

/**
 * How long an owner must wait between broadcasts to their own cohort.
 *
 * Enforced by firestore.rules against [TestApp.lastBroadcastAt], not only by the UI disabling a
 * button — a value here that drifted from the rules' own copy would either let a client through
 * that the server refuses, or hold a button disabled the server would have allowed.
 */
const val BROADCAST_COOLDOWN_MS = 2 * 24 * 60 * 60 * 1000L

/**
 * How long an owner must wait between asking their own cohort for feedback.
 *
 * Same duration as [BROADCAST_COOLDOWN_MS] and enforced the same way, against
 * [TestApp.lastFeedbackAskAt] — its own field and its own rule, so one cooldown running out
 * does not quietly reset the other.
 */
const val FEEDBACK_ASK_COOLDOWN_MS = 2 * 24 * 60 * 60 * 1000L

/**
 * When a member has stopped holding up their end, and the arithmetic that decides it.
 *
 * Miss one completed day and you are warned. Miss two in a row and your app leaves the group.
 *
 * The check runs on testers' phones, and needs neither a server nor an admin. Every developer can
 * already read every proof about their own app and nothing else, which happens to be exactly the
 * evidence required: whether a given member opened *their* app on a given day. Thirteen apps in a
 * group means thirteen devices each watching their own, and between them the whole cohort is
 * covered.
 *
 * The database checks the same sums before it accepts a removal, so these are not the only place
 * they are enforced — see the `groups` and `apps` blocks in firestore.rules. Change one and the
 * other has to move with it, or evictions start coming back PERMISSION_DENIED.
 */
object Compliance {
    /** Consecutive completed days missed before a member's app leaves the group. */
    const val MISSES_TO_REMOVE = 2

    private const val DAY_MS = 86_400_000L

    /**
     * The most recent day whose result is final, or null if there isn't one yet.
     *
     * Today is never judged: there are hours left to open the app, so a gap in it is not a miss.
     * Which means nobody can be removed before day three, the first day on which two completed
     * days exist to be missed.
     */
    fun lastCompletedDay(dayIndex: Int): Int? = (dayIndex - 1).takeIf { it >= 0 }

    /**
     * The first day this member can be held to.
     *
     * An app placed into a running group on day nine has no proofs for days one to eight. Read
     * literally that is eight missed days, and the first device to open the app would evict its
     * owner on the spot. So the count starts the day *after* they arrived: being added at 23:00 is
     * not a day's notice either.
     *
     * Founding members were placed before the clock started and are held to all of it.
     */
    fun firstJudgedDay(
        placedAt: Long,
        startDate: Long,
    ): Int =
        if (placedAt <= 0L || startDate <= 0L || placedAt <= startDate) {
            0
        } else {
            ((placedAt - startDate) / DAY_MS).toInt() + 1
        }

    /**
     * The completed days one member owed one app, most recent first.
     *
     * Both arrivals bound it, and the later of the two wins. A tester cannot be marked down for
     * the days before they joined, and equally cannot be marked down for days before the app
     * itself was placed — there was nothing there to open.
     *
     * Shorter than [count] means there is not yet enough finished run to judge, which is the
     * answer that keeps a new member safe.
     */
    fun judgedDays(
        dayIndex: Int,
        startDate: Long,
        testerPlacedAt: Long,
        appPlacedAt: Long,
        count: Int,
        graceDays: Int = 0,
        /**
         * When an admin last put this tester's app back into testing, or zero if it never left.
         *
         * The fourth floor: a member reinstated after being flagged has to be given the same
         * grace a genuine late joiner gets, or the days that got them flagged are still sitting
         * there the moment the next device sweeps, and the admin's own "put back in testing" is
         * undone within the hour by somebody else's phone.
         */
        reinstatedAt: Long = 0L,
    ): List<Int> {
        val last = lastCompletedDay(dayIndex) ?: return emptyList()
        // Four floors, and the latest of them wins: when the tester arrived, when the app
        // arrived, how many opening days an admin excused for everybody, and when this tester
        // was last reinstated.
        val first =
            maxOf(
                maxOf(firstJudgedDay(testerPlacedAt, startDate), firstJudgedDay(appPlacedAt, startDate)),
                maxOf(firstJudgedDay(reinstatedAt, startDate), graceDays.coerceAtLeast(0)),
            )
        return (0 until count).map { last - it }.filter { it >= first }
    }
}

/**
 * A swap cohort.
 *
 * Fourteen members, one app each. Google requires twelve testers opted in and does not count an
 * app's own developer, so a cohort of twelve leaves every app one short — hence [THRESHOLD] of
 * thirteen before the clock starts, and [CAPACITY] of fourteen for margin.
 *
 * The run belongs here rather than to the app. Every app in a group is on the same day, which is
 * what makes a group readable as a cohort at all.
 */
data class TestGroup(
    val id: String = "",
    val name: String = "",
    val memberUids: List<String> = emptyList(),
    val appIds: List<String> = emptyList(),
    /** Epoch millis. Zero until the group first reached [THRESHOLD]; set once, by an admin. */
    val startDate: Long = 0L,
    /**
     * Days at the start of the run that nobody is held to.
     *
     * A run begins the moment its thirteenth app is placed, which can be eight in the evening, and
     * the people it begins for are not watching for it. They find out when they next open the app,
     * and two of those days in a row is a removal for somebody who never knew the clock had
     * started.
     *
     * The days still exist and still show in the grid, empty. This only decides whether anybody
     * can be removed for them, which is the part that cannot be undone.
     *
     * Zero on every group written before this, which reads as "hold everyone to everything" and
     * is what those groups have been doing all along.
     */
    val graceDays: Int = 0,
    /**
     * How many days this run lasts.
     *
     * [RUN_DAYS] unless an admin has extended it. Nothing happens on its own when a run reaches
     * the end: the days simply stop being owed, and testing carries on for anyone who wants to.
     * Extending is the only way obligation continues.
     */
    val runDays: Int = RUN_DAYS,
    val status: String = STATUS_FORMING,
) {
    val size: Int get() = memberUids.size
    val running: Boolean get() = startDate > 0L

    /**
     * Which day of the run today is, or null before it starts and after it ends.
     *
     * Whole days since [startDate] rather than calendar days: a calendar boundary would let a
     * tester post at 23:59 and again at 00:01 and call it two days.
     */
    fun dayIndex(now: Long = System.currentTimeMillis()): Int? {
        if (startDate <= 0L) return null
        val day = ((now - startDate) / 86_400_000L).toInt()
        return day.takeIf { it in 0 until runDays }
    }

    /**
     * When today stops counting and the next day begins, or null when nothing is running.
     *
     * Worth showing, because it is not midnight and nobody would guess it. The clock starts at the
     * hour the group did, so a cohort that began at half past eight in the evening rolls over at
     * half past eight every evening after — and a tester who does their round after dinner can
     * find that half of it landed on one day and half on the next, with no way to tell from the
     * screen why.
     */
    fun dayEndsAt(now: Long = System.currentTimeMillis()): Long? {
        val day = dayIndex(now) ?: return null
        return startDate + (day + 1) * 86_400_000L
    }

    /**
     * Deliberately not here any more.
     *
     * There was an `atRisk` on this model, and two screens drew a red panel from it saying the
     * group was short and to go and ask an admin. It has gone, along with the panels, because a
     * group can now be started short on purpose: an admin can begin a run with eight people
     * rather than leave eight waiting for a ninth. Under that, "you are five members short" is
     * describing the plan, not a fault, and it is not something the person reading it could do
     * anything about in either case.
     *
     * The admin console keeps its version of the warning, in full, because the person who can
     * place another app is the one looking at that screen.
     */

    companion object {
        const val CAPACITY = 14
        const val THRESHOLD = 13

        const val STATUS_FORMING = "forming"
        const val STATUS_RUNNING = "running"
        const val STATUS_FINISHED = "finished"
    }
}

/**
 * An app someone has put on a closed testing track.
 *
 * Registered by its owner, placed into a group by an admin. Those are one action: there is no
 * approved-but-unassigned state, because approval *is* the placement.
 */
data class TestApp(
    val id: String = "",
    val ownerUid: String = "",
    val ownerEmail: String = "",
    val name: String = "",
    val packageName: String = "",
    /** Null until an admin places it. */
    val groupId: String? = null,
    val submittedAt: Long = 0L,
    /**
     * Epoch millis of the placement that put this app in its current group. Zero if it has never
     * been placed, or was placed before the console started recording it.
     *
     * [Compliance] will not act against an app without one. Membership of a running group says
     * nothing about *when* someone joined, and a member placed on day nine has no proofs for the
     * eight days before they arrived — which is indistinguishable from having skipped them.
     */
    val placedAt: Long = 0L,
    val status: String = STATUS_PENDING,
    /**
     * Taken out of testing by an admin, without leaving the group.
     *
     * Everybody uninstalls it and stops owing it a day. The owner keeps their own days on
     * everybody else's apps and is told to go and talk to an admin. The group's member count does
     * not move, which is deliberate: the slot is still theirs and this is reversible.
     *
     * Set by an admin only, and by the security rules rather than by convention, because an owner
     * writing `removed: false` over their own removal is otherwise a single field away.
     */
    val removed: Boolean = false,
    val removedAt: Long = 0L,
    /** Blank for an automatic removal, which is every one nobody typed a reason for. */
    val removedReason: String = "",
    /**
     * A cohort member saying this app's owner has missed two days running on theirs — set once,
     * automatically, by whoever's device noticed. Nothing is taken away when this is set: the
     * member stays in the group and the app stays in testing, so an admin who never opens the
     * console does not accidentally cost anyone their place. It exists to be seen and decided on,
     * not acted on by itself.
     *
     * Cleared by [reinstatedAt] moving forward, whether that happens because an admin cleared the
     * flag on its own or because they took the app out of testing and later put it back — either
     * way the member should meet the next check with a clean two days, not the ones that already
     * flagged them once.
     */
    val flaggedAt: Long = 0L,
    val flaggedByAppId: String = "",
    /**
     * The last time an admin put this app back into testing, or gave it a clean slate without
     * ever taking it out — zero if that has never happened.
     *
     * The fourth floor in [Compliance.judgedDays], for the same reason [placedAt] is one of the
     * first three: a member reinstated this morning cannot be judged on the two days that got
     * them flagged, because those days are the ones the reinstatement was about. Without this, the
     * very same evidence that justified the flag the first time re-flags them within the hour, on
     * whichever cohort-mate's phone happens to sweep next.
     */
    val reinstatedAt: Long = 0L,
    /**
     * A cohort member saying this app would not open, or broke, before an admin ever heard about
     * it any other way.
     *
     * Set once, by whoever hit the problem — not the owner, who has nothing to report about their
     * own app. From the day it is set onward, nobody's judged for missing this app: a day nobody
     * could get into is not a day anybody skipped. Cleared only by an admin, from the console,
     * once whatever was wrong is sorted out; a tester who filed one cannot un-file it, which is
     * what would happen if writing `reportedAt: 0` over it were merely another update.
     *
     * [removed] is the harsher, admin-only tool for the same shape of problem — this is what
     * exists for the gap before an admin has looked at it at all.
     */
    val reportedAt: Long = 0L,
    val reportedBy: String = "",
    /** Same cap as [notes] — read by whoever eventually resolves it, not typed for an audience. */
    val reportedReason: String = "",
    /**
     * The last time this app's owner told their cohort an update was out.
     *
     * Zero until the first broadcast. [BROADCAST_COOLDOWN_MS] after this is when the next one is
     * allowed — enforced by firestore.rules against this exact field, not merely by the UI
     * disabling a button, since a patched client could otherwise send one every time it liked.
     */
    val lastBroadcastAt: Long = 0L,
    /**
     * The last time this app's owner asked their cohort for feedback.
     *
     * Zero until the first ask. [FEEDBACK_ASK_COOLDOWN_MS] after this is when the next one is
     * allowed — its own field and its own rule, the same discipline as [lastBroadcastAt] right
     * above it, so the two asks stay on independent cooldowns rather than one resetting the
     * other.
     */
    val lastFeedbackAskAt: Long = 0L,
    /**
     * An admin's standing permission for this app to be skipped, member by member, rather than
     * for everybody at once.
     *
     * [removed] excuses the whole cohort in one admin action; this is the other shape of the same
     * problem, for when the app is fine for most people but a genuine blocker for a few — each
     * member decides for themselves whether to skip it, in the `skips` collection, and this field
     * is only the switch that makes that collection's writes admissible at all. Admin-only, pinned
     * in the owner-update rule the same as [removed]/[reportedAt] — an owner granting their own app
     * blanket immunity from being tested is exactly the attack that pinning closes.
     */
    val skippable: Boolean = false,
    /**
     * How a tester gets past the front door: a test account, a sign-in code, a sandbox card
     * number. Whatever an app asks for before it will show anything worth testing.
     *
     * Blank on every app submitted before this field existed, and blank is the normal answer for
     * an app that just opens.
     *
     * Read by the owner, by every member of the cohort testing the app, and by every admin. It is
     * stored in plain text in the app document, so it is not a secret and cannot be made into one:
     * anything written here is visible to at least thirteen people and lives in the database as
     * typed. That is why the field asks for a throwaway test account rather than a real password,
     * and why anyone extending this should keep it that way rather than adding a field that looks
     * private without being it.
     */
    val notes: String = "",
) {
    val placed: Boolean get() = status == STATUS_ASSIGNED && !groupId.isNullOrBlank()

    /**
     * In a group and being tested. Every daily obligation is built from these.
     *
     * The distinction that matters is between "in the group" and "owed a day". A removed app is
     * the first and not the second, and code that reaches for [placed] where it means this is how
     * somebody ends up being nagged to open an app they were told to uninstall.
     */
    val active: Boolean get() = placed && !removed

    val label: String get() = name.ifBlank { packageName }

    /**
     * Where a tester installs this app.
     *
     * Derived rather than stored: it is the package name with a fixed prefix, so nothing has to be
     * supplied at submission and no existing app document needs a new field.
     *
     * The store listing, not `play.google.com/apps/testing/{package}`. That opt-in page belongs to
     * a track whose testers were invited by email address, where accepting the invitation is a
     * step of its own. TestTrack's cohorts are not built that way: every member joins the testers
     * Google Group during setup, and being in the group *is* the opt-in. There is nothing left to
     * accept, so the opt-in page has nothing to show and answers with an error, which is the one
     * outcome guaranteed to read as a broken app.
     */
    val storeUrl: String get() = "https://play.google.com/store/apps/details?id=$packageName"

    /**
     * The same listing, addressed to the Play app rather than to whatever claims https links.
     *
     * Worth trying first. A closed-testing listing is only visible to an account on the tester
     * list, so opening it in a browser signed in as a different Google account shows "item not
     * found" — the same dead end by another route. The Play app is already on the right account.
     */
    val storeAppUri: String get() = "market://details?id=$packageName"

    /** Anything that is not waiting and not in a group. It keeps its document either way. */
    val closed: Boolean
        get() = status == STATUS_REJECTED || status == STATUS_WITHDRAWN || status == STATUS_DONE

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ASSIGNED = "assigned"

        /**
         * The three ways an app stops being in play without ceasing to exist.
         *
         * Turning a submission down used to delete the document and withdrawing one used to
         * delete it too, which lost the only record that the app had ever been submitted. Events
         * survived, so the developer's profile still showed something had happened, and the thing
         * it happened to was gone: no package name to place, no way to reverse a decision, and
         * the owner's only route back was to submit it again from scratch.
         *
         * So a package name is claimed by its first submission and never released. What changes
         * afterwards is the status, and every one of these can be put back on the queue by an
         * admin. Nobody else can: the owner-update rule pins `status`, so a developer cannot
         * revive their own app whatever they send.
         */
        const val STATUS_REJECTED = "rejected"
        const val STATUS_WITHDRAWN = "withdrawn"

        /** Finished a full run. A second fortnight is an admin's decision, not the owner's. */
        const val STATUS_DONE = "done"
    }
}

/**
 * One tester's proof for one app on one day of one run.
 *
 * The document id carries the run as well as the day. Without it a restart was cosmetic: day
 * numbering begins again at zero, the previous run's proofs are still sitting under those numbers,
 * and a cohort restarted for having bled out would open on day one already showing everybody
 * present. The run is identified by the `startDate` it began on, which changes every time the
 * clock is set — so the old fortnight keeps its record and stops counting towards the new one.
 */
data class Proof(
    val appId: String = "",
    val groupId: String = "",
    /**
     * The owner of [appId], copied in at write time.
     *
     * Carried on the proof so reading one needs no lookup: the two people entitled to see it — the
     * tester who posted it and the owner of the app it is about — are both named in the document.
     * A rule that had to fetch the group instead did not survive a 45-document query.
     */
    val ownerUid: String = "",
    val testerUid: String = "",
    val testerEmail: String = "",
    val day: Int = 0,
    val fileId: String = "",
    val imageUrl: String = "",
    val capturedAt: Long = 0L,
    /** Foreground time for that app that day, from UsageStatsManager. */
    val usageMs: Long = 0L,
    /**
     * The `startDate` of the run this belongs to.
     *
     * Part of the identity, not decoration: every query for a day has to name it, or a restarted
     * cohort reads its predecessor's attendance as its own.
     */
    val runStartedAt: Long = 0L,
    /**
     * The screenshot was supplied by hand — see CaptureService.supplyManualCapture — because that
     * day's automated capture genuinely failed to save a frame. [usageMs] is unaffected either
     * way: it always comes from the real visit, never from the picture.
     *
     * Read by the row that posted it, to mark that day yellow rather than green — a real day, just
     * not one the random-timed capture itself produced a picture for.
     */
    val manual: Boolean = false,
) {
    /** A screenshot proves the app opened. The ten seconds of use is what proves it was used. */
    val meetsBar: Boolean get() = usageMs >= MIN_USAGE_MS

    companion object {
        /** The tester is asked to stay for ten seconds, so that is what a day has to show. */
        const val MIN_USAGE_MS = 10_000L

        /**
         * Same tester, same app, same day of the same run always writes the same document, so
         * posting twice overwrites rather than duplicating.
         */
        fun id(
            appId: String,
            testerUid: String,
            day: Int,
            runStartedAt: Long,
        ) = "${appId}__${testerUid}__${runStartedAt}__$day"
    }
}

/**
 * A tester's own note about an app they're testing — a rough edge, a typo, something that works
 * but shouldn't, anything worth the owner hearing that isn't the app being broken outright. That
 * case is [TestApp.reportedAt], which excuses the day; this is the opposite kind of message, sent
 * on a day that otherwise went fine.
 *
 * Same shape as [Proof] on purpose, including the id: one of these a day per (tester, app, run),
 * and posting a second one the same day overwrites rather than piling up — a tester refining one
 * thought during the day is not the same thing as a thread.
 */
data class Feedback(
    val appId: String = "",
    val groupId: String = "",
    /** The owner of [appId], copied in at write time — see [Proof.ownerUid] for why. */
    val ownerUid: String = "",
    val testerUid: String = "",
    val day: Int = 0,
    val runStartedAt: Long = 0L,
    val text: String = "",
    val createdAt: Long = 0L,
) {
    companion object {
        const val TEXT_MAX = 1000

        fun id(
            appId: String,
            testerUid: String,
            day: Int,
            runStartedAt: Long,
        ) = "${appId}__${testerUid}__${runStartedAt}__$day"
    }
}

/** A member of the group, as far as a grid is concerned. */
data class Tester(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    /** A tiny JPEG as base64, or blank. See [Photo] for why it lives on the document. */
    val photo: String = "",
) {
    /** `someone@gmail.com` -> `someone`, which is all a grid column has room for. */
    val shortName: String
        get() = displayName.takeIf { it.isNotBlank() } ?: email.substringBefore('@')

    val initial: String get() = shortName.firstOrNull()?.uppercase() ?: "?"
}

/** [History.type] values. Every one is written by an admin — see AdminRepo.logHistory. */
const val HISTORY_JOINED = "joined"
const val HISTORY_REMOVED = "removed"
const val HISTORY_RESTORED = "restored"
const val HISTORY_KICKED = "kicked"

/**
 * One durable line in a cohort's own record — see AdminRepo.logHistory in the admin app.
 *
 * Unlike an event, nothing ever deletes one of these: an app being erased or an account being
 * wiped takes their live documents with it, not the fact that they were once here.
 *
 * [groupName] and [appLabel] are copied in at write time rather than looked up when a row is
 * drawn, because both source documents can later be gone — the app erased, the group emptied and
 * deleted — and a durable record that goes blank the moment the thing it names is cleaned up was
 * never durable at all.
 */
data class History(
    val id: String = "",
    val groupId: String = "",
    val groupName: String = "",
    val uid: String = "",
    val type: String = "",
    val appId: String = "",
    val appLabel: String = "",
    val actorUid: String = "",
    val createdAt: Long = 0L,
)

/** Milliseconds as `4m 12s` / `45s`, for a stat line. */
fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}
