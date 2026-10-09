package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.net.ApiMyApp

/**
 * Everything this account has put up, in one list.
 *
 * A crew seat and a Circle post are different rows in the database doing the same job: an app of
 * yours that other people are doing something to. Somebody looking for their own app does not
 * know or care which half of the product it went into, so they are not asked to.
 *
 * The one thing every row has to answer is "what is happening to it now", which is why the state
 * is a pill on the right rather than something to work out from a date.
 */
@Composable
fun AppsScreen(
    onSignIn: () -> Unit = {},
    /** A crew app: its crew, on the Crew tab. */
    onOpenCrew: (String) -> Unit = {},
    /** A Circle request: its page, with the reviews it has had. */
    onOpenRequest: (String) -> Unit = {},
    vm: AppsViewModel = viewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load(context) }
    // A crew filling or a review arriving shows up without leaving the tab.
    WhileShown(onShow = { vm.startLive(context) }, onHide = vm::stopLive)

    AppsBody(
        apps = vm.apps,
        loading = vm.loading,
        offline = vm.offline,
        signedOut = vm.signedOut,
        onSignIn = onSignIn,
        onRetry = { vm.load(context) },
        onOpen = { app ->
            if (app.kind == "crew") app.crewId?.let(onOpenCrew) else onOpenRequest(app.id)
        },
    )
}

/** The list, drawn. Handed its values so every state it has can be put in front of a test. */
@Composable
fun AppsBody(
    apps: List<ApiMyApp>,
    loading: Boolean = false,
    offline: Boolean = false,
    signedOut: Boolean = false,
    onSignIn: () -> Unit = {},
    onRetry: () -> Unit = {},
    onOpen: (ApiMyApp) -> Unit = {},
) {
    // Asked for here rather than at the door. Somebody can read the feed, the crews and the board
    // without an account; this is the first screen that cannot answer without knowing who they
    // are, so this is where the question belongs, with the way out attached to it.
    if (signedOut) {
        Gated(
            heading = "Nothing up yet",
            body = "Your apps, and how each one is getting on, belong to your account.",
            onSignIn = onSignIn,
            mark = { AppsMark() },
        )
        return
    }
    if (offline && apps.isEmpty()) {
        RobotOffline("Your apps did not load", onRetry = onRetry)
        return
    }
    if (DebugLooks.holdLoading || (loading && apps.isEmpty())) {
        AppsLoading()
        return
    }
    if (apps.isEmpty()) {
        // The robot, like every other tab with nothing to show.
        RobotState(
            Prop.Phone,
            "Nothing up yet",
            "Put an app into a crew to get a fortnight of testing, or ask Circle for a review. " +
                "Whatever you send lands here.",
        )
        return
    }

    val running = apps.filter { it.state in LIVE_APP_STATES }
    val over = apps - running.toSet()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = Gutter + dockRoom()),
    ) {
        // Cards for what is running, rows for what is over. A running app has a stage and a
        // count worth drawing, and the owner opens this tab to see how far it has got; a finished
        // one has a single sentence left to say, and a card around it would be a box of nothing.
        if (running.isNotEmpty()) {
            item { CrewHeading("Going on", trailing = "${running.size}") }
            items(running, key = { it.id }) { app ->
                MyAppCard(app, Modifier.padding(horizontal = Gutter, vertical = 5.dp), onOpen = { onOpen(app) })
            }
        }
        if (over.isNotEmpty()) {
            item { CrewHeading("Finished", trailing = "${over.size}") }
            itemsIndexed(over, key = { _, app -> app.id }) { index, app ->
                ListTile(index, over.size) { MyAppRow(app, onOpen = { onOpen(app) }) }
            }
        }
    }
}

/**
 * A running app, with how far it has got drawn rather than written.
 *
 * The row said "day 11 of 14" in the same small grey as everything else on it, which is the one
 * figure the owner came here for set in the type used for footnotes. Here the stage is a line of
 * its own with the count beside it, and the meter under them is the same one the crew screens use,
 * so fourteen days reads as fourteen boxes wherever it appears.
 */
@Composable
fun MyAppCard(
    app: ApiMyApp,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    CrewCard(modifier, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppTile(app.appName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    app.where,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            val (tone, background) = appTone(app.state)
            Pill(appLabel(app.state), tone, background)
        }

        val stage = stageOf(app)
        if (stage == null) {
            // A state this build does not know. The sentence still says something sensible.
            Spacer(Modifier.height(10.dp))
            Text(
                appStanding(app),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@CrewCard
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stage.title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stage.count,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        // Counted boxes while there are few enough to count, a plain bar past that. Fifty reviews
        // as fifty boxes is a stripe, not a meter.
        if (stage.total <= COUNTABLE) {
            Meter(done = stage.done, total = stage.total, height = 8.dp)
        } else {
            Meter(fraction = stage.done.toFloat() / stage.total, height = 8.dp)
        }
        if (stage.note != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                stage.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** More boxes than this and a meter stops being something you can count. */
private const val COUNTABLE = 20

/** What a running card draws under the name: the stage, its count, and how full the meter is. */
data class AppStage(
    val title: String,
    val count: String,
    val done: Int,
    val total: Int,
    val note: String? = null,
)

/**
 * The stage a running app is at, or null for one with nothing to count.
 *
 * Every figure is the server's. Nothing is worked out here except how many days are left, which is
 * the subtraction the owner would otherwise do in their head.
 */
fun stageOf(app: ApiMyApp): AppStage? =
    when (app.state) {
        "waiting" ->
            if (app.kind == "crew") {
                // The others in it so far: the count does not include this app.
                AppStage(
                    title = "Filling the crew",
                    count = "${app.testers + 1} of ${CrewRules.SEATS} seats",
                    done = app.testers + 1,
                    total = CrewRules.SEATS,
                )
            } else {
                null
            }
        "starting" ->
            AppStage(
                title = "Crew full",
                count = "Day 1 at midnight",
                done = CrewRules.SEATS,
                total = CrewRules.SEATS,
            )
        "checking" ->
            // Play could not look at it, so members decide: two answers either way.
            AppStage(
                title = "Members are trying it",
                count = "${app.greens} Yes, ${app.reds} No",
                done = app.greens,
                total = CrewRules.VOTES_TO_DECIDE,
            )
        "testing" -> {
            val left = (app.runDays - app.day).coerceAtLeast(0)
            AppStage(
                title = "Day ${app.day} of ${app.runDays}",
                count =
                    if (left == 0) {
                        "Last day"
                    } else if (left == 1) {
                        "1 day left"
                    } else {
                        "$left days left"
                    },
                done = app.day,
                total = app.runDays,
                note = if (app.testers == 1) "1 person testing it" else "${app.testers} people testing it",
            )
        }
        "collecting" ->
            AppStage(
                title = "Reviews in",
                count = "${app.testers} of ${app.reviewsBought}",
                done = app.testers,
                total = app.reviewsBought,
            )
        else -> null
    }.takeIf { it == null || it.total > 0 }

/** States where something is still happening, which is what splits the list in two. */
private val LIVE_APP_STATES = setOf("waiting", "starting", "checking", "testing", "collecting")

/**
 * One app of yours.
 *
 * The line under the name is the only part that varies, and it says the one thing worth knowing
 * at that stage: how far through the fortnight, how many of the reviews bought have arrived, or
 * what has to be fixed.
 */
@Composable
fun MyAppRow(
    app: ApiMyApp,
    onOpen: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = Gutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTile(app.appName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.appName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                appStanding(app),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        val (tone, background) = appTone(app.state)
        Pill(appLabel(app.state), tone, background)
    }
}

/** The pill's word. Short, because it sits next to a name that may already be long. */
fun appLabel(state: String): String =
    when (state) {
        "waiting" -> "Waiting"
        "starting" -> "Starting"
        "checking" -> "Checking"
        "testing" -> "Testing"
        "finished" -> "Done"
        "rejected" -> "Fix it"
        "withdrawn" -> "Pulled"
        "removed" -> "Out"
        "collecting" -> "Open"
        "done" -> "Done"
        "closed" -> "Closed"
        else -> "Up"
    }

/**
 * What the line under the name says.
 *
 * Written per state rather than as one template with blanks, because the interesting number is a
 * different number at every stage and a sentence that fits all of them says nothing at any.
 */
fun appStanding(app: ApiMyApp): String =
    when (app.state) {
        "waiting" ->
            if (app.kind == "crew") {
                "${app.where} · waiting for it to fill"
            } else {
                app.where
            }
        "starting" -> "${app.where} · full, day 1 starts at midnight"
        "checking" -> "${app.where} · members are trying it: ${app.greens} Yes, ${app.reds} No"
        "testing" -> "${app.where} · day ${app.day} of ${app.runDays}, ${app.testers} testing"
        "finished" -> "${app.where} · ${app.testers} tested it for ${app.runDays} days"
        "rejected" -> "${app.where} · it came back to you. Fix it and put it in again."
        "removed" -> "${app.where} · out of the run after missed days"
        "withdrawn" -> "${app.where} · you took it back"
        "collecting" -> "Circle · ${app.testers} of ${app.reviewsBought} reviews in"
        "done" -> "Circle · all ${app.reviewsBought} reviews in"
        "closed" -> "Circle · closed with ${app.testers} of ${app.reviewsBought} reviews"
        else -> app.where
    }

/**
 * The pill's colours.
 *
 * Only two states are coloured at all: the one that needs the owner to do something, and the one
 * that is actually running. Everything else is the quiet neutral, because a list where every row
 * shouts is a list nobody scans.
 */
@Composable
fun appTone(state: String): Pair<Color, Color> =
    when (state) {
        "rejected" -> Status.short to Status.shortSoft
        "removed" -> Status.missed to Status.missedSoft
        "testing" -> Status.posted to Status.postedSoft
        else -> MaterialTheme.colorScheme.onSurfaceVariant to Status.neutralSoft
    }
