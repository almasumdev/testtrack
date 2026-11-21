package com.eazyverse.testtrack.ui

/*
 * Uninstall all on a finished crew, as plain values: which apps are still here, where a run has
 * got to, what each row and the card say. Nothing here knows about Android, so each rule is
 * checked on a desk. The run itself (the dialogs, the waiting) is viewmodel/UninstallRun.
 */

/** Where a run is. A run that never started is no state at all (null). */
enum class UninstallStep {
    /** Android's own confirm is up for [UninstallRunState.current], or was a moment ago. */
    ASKING,

    /** [UninstallRunState.current] is gone: a short tick, then the next app. */
    TICK,

    /** The next app is due but TestTrack is not in front, so it waits to be back. */
    WAIT,

    /** The person said no to [UninstallRunState.current]: paused on it. */
    KEPT,

    /** Finished or stopped. What is still here is read from the phone. */
    ENDED,
}

/**
 * One run over one crew's apps. [order] is every package in it, those already removed first, so a
 * second run ("the rest") carries on from the first one's count. [kept] are the ones said no to in
 * this pass; [removed] the ones gone.
 */
data class UninstallRunState(
    val crewId: String,
    val order: List<String>,
    val removed: Set<String> = emptySet(),
    val kept: Set<String> = emptySet(),
    val current: String? = null,
    val step: UninstallStep = UninstallStep.ASKING,
) {
    val total: Int get() = order.size
    val active: Boolean get() = step != UninstallStep.ENDED
}

/**
 * The first run, or the rest after a stop. [left] are the packages on the phone now; [earlier] the
 * ones this crew's last run already removed. Null when there is nothing to remove.
 */
fun beginUninstall(
    crewId: String,
    left: List<String>,
    earlier: Set<String> = emptySet(),
): UninstallRunState? {
    val todo = left.distinct().filterNot { it in earlier }
    if (todo.isEmpty()) return null
    return UninstallRunState(
        crewId = crewId,
        order = earlier.toList() + todo,
        removed = earlier,
        current = todo.first(),
        step = UninstallStep.ASKING,
    )
}

/**
 * What the phone says once TestTrack is back in front: [gone] is a fresh read, never a cached one.
 * Gone ticks the app off; still here means the person said no. A late answer after a stop still
 * counts a removed app, but never wakes the run up again.
 */
fun UninstallRunState.settled(
    pkg: String,
    gone: Boolean,
): UninstallRunState {
    val asking = step == UninstallStep.ASKING && current == pkg
    return when {
        gone -> copy(removed = removed + pkg, step = if (asking) UninstallStep.TICK else step)
        asking -> copy(kept = kept + pkg, step = UninstallStep.KEPT)
        else -> this
    }
}

/**
 * The next app to ask about, after a tick or a "Next app". An app already gone from the phone by
 * some other way is ticked off without asking; the run ends when none is left.
 */
fun UninstallRunState.advanced(installed: (String) -> Boolean): UninstallRunState {
    var gone = removed
    val next =
        order.firstOrNull { pkg ->
            when {
                pkg in gone || pkg in kept -> false
                !installed(pkg) -> {
                    gone = gone + pkg
                    false
                }
                else -> true
            }
        }
    return copy(
        removed = gone,
        current = next,
        step = if (next == null) UninstallStep.ENDED else UninstallStep.ASKING,
    )
}

/** Stopped: nothing more is asked. */
fun UninstallRunState.stopped() = copy(current = null, step = UninstallStep.ENDED)

/** The apps still on this phone, once each, in the crew's order. */
fun appsLeft(
    apps: List<RecordApp>,
    installed: (String) -> Boolean,
): List<RecordApp> = apps.filter { it.packageName.isNotBlank() && installed(it.packageName) }.distinctBy { it.packageName }

/** What the card is showing. */
enum class UninstallCard { LOCKED, READY, RUNNING, KEPT_ONE, ALL_REMOVED, PARTIAL, NONE_LEFT }

/** Which card, from whether it is open, the run if there is one, and how many apps are still here. */
fun uninstallCard(
    open: Boolean,
    run: UninstallRunState?,
    left: Int,
): UninstallCard =
    when {
        run == null ->
            when {
                left == 0 -> UninstallCard.NONE_LEFT
                open -> UninstallCard.READY
                else -> UninstallCard.LOCKED
            }
        run.step == UninstallStep.KEPT -> UninstallCard.KEPT_ONE
        run.active -> UninstallCard.RUNNING
        left == 0 -> UninstallCard.ALL_REMOVED
        else -> UninstallCard.PARTIAL
    }

/** One square of the card's meter. */
enum class UninstallMark { REMOVED, NOW, KEPT, TODO }

/** A square per app in the run: removed, the one being asked about, said no to, still to do. */
fun uninstallMarks(run: UninstallRunState): List<UninstallMark> =
    run.order.map { pkg ->
        when {
            pkg in run.removed -> UninstallMark.REMOVED
            pkg == run.current && run.step == UninstallStep.ASKING -> UninstallMark.NOW
            pkg in run.kept -> UninstallMark.KEPT
            else -> UninstallMark.TODO
        }
    }

/** How one row stands. */
enum class UninstallRowState { LOCKED, OPEN, ASKING, WAITING, REMOVED, KEPT, EARLIER }

/** One crew app and how its row stands. */
data class UninstallRow(
    val app: RecordApp,
    val state: UninstallRowState,
)

/**
 * The rows: [live] are on this phone or were removed in this run, [gone] were not here to begin
 * with ("Already gone, not counted"). While a run is going the list keeps the crew's order; once it
 * stops, what is still here comes first and what was removed settles below it.
 */
data class UninstallRows(
    val live: List<UninstallRow>,
    val gone: List<UninstallRow>,
)

fun uninstallRowState(
    pkg: String,
    open: Boolean,
    installed: Boolean,
    run: UninstallRunState?,
): UninstallRowState =
    when {
        run != null && pkg in run.removed -> UninstallRowState.REMOVED
        !installed -> UninstallRowState.EARLIER
        run != null && pkg == run.current && run.step == UninstallStep.ASKING -> UninstallRowState.ASKING
        run != null && pkg == run.current && run.step == UninstallStep.KEPT -> UninstallRowState.KEPT
        run != null && pkg in run.kept -> UninstallRowState.KEPT
        run != null && run.active && pkg in run.order -> UninstallRowState.WAITING
        else -> if (open) UninstallRowState.OPEN else UninstallRowState.LOCKED
    }

fun uninstallRows(
    apps: List<RecordApp>,
    open: Boolean,
    installed: (String) -> Boolean,
    run: UninstallRunState?,
): UninstallRows {
    val rows =
        apps
            .filter { it.packageName.isNotBlank() }
            .distinctBy { it.packageName }
            .map { UninstallRow(it, uninstallRowState(it.packageName, open, installed(it.packageName), run)) }
    val (gone, live) = rows.partition { it.state == UninstallRowState.EARLIER }
    val settled = run == null || !run.active
    val ordered = if (settled) live.sortedBy { it.state == UninstallRowState.REMOVED } else live
    return UninstallRows(ordered, gone)
}
