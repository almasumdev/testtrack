package com.eazyverse.testtrack.ui

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
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
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
    vm: AppsViewModel = viewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load(context) }

    TabScaffold("My apps") {
        AppsBody(
            apps = vm.apps,
            loading = vm.loading,
            offline = vm.offline,
            signedOut = vm.signedOut,
            onSignIn = onSignIn,
        )
    }
}

/** The list, drawn. Handed its values so every state it has can be put in front of a test. */
@Composable
fun AppsBody(
    apps: List<ApiMyApp>,
    loading: Boolean = false,
    offline: Boolean = false,
    signedOut: Boolean = false,
    onSignIn: () -> Unit = {},
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
    if (offline) {
        EmptyState(
            icon = Lucide.LayoutGrid,
            title = "Your apps did not load",
            subtitle = "Check your connection and try again.",
        )
        return
    }
    if (loading && apps.isEmpty()) {
        Blank("Looking.")
        return
    }
    if (apps.isEmpty()) {
        EmptyState(
            icon = Lucide.LayoutGrid,
            title = "Nothing up yet",
            subtitle =
                "Put an app into a crew to get a fortnight of testing, or ask Circle for a " +
                    "review. Whatever you send lands here.",
        )
        return
    }

    val running = apps.filter { it.state in LIVE_APP_STATES }
    val over = apps - running.toSet()

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = Gutter),
    ) {
        if (running.isNotEmpty()) {
            item { CrewHeading("Going on", trailing = "${running.size}") }
            items(running, key = { it.id }) { app ->
                if (app != running.first()) RowDivider()
                MyAppRow(app)
            }
        }
        if (over.isNotEmpty()) {
            item { CrewHeading("Finished", trailing = "${over.size}") }
            items(over, key = { it.id }) { app ->
                if (app != over.first()) RowDivider()
                MyAppRow(app)
            }
        }
    }
}

/** States where something is still happening, which is what splits the list in two. */
private val LIVE_APP_STATES = setOf("waiting", "checking", "testing", "collecting")

/**
 * One app of yours.
 *
 * The line under the name is the only part that varies, and it says the one thing worth knowing
 * at that stage: how far through the fortnight, how many of the reviews bought have arrived, or
 * what has to be fixed.
 */
@Composable
fun MyAppRow(app: ApiMyApp) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp),
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
        "checking" -> "Checking"
        "testing" -> "Testing"
        "finished" -> "Done"
        "rejected" -> "Fix it"
        "withdrawn" -> "Pulled"
        "collecting" -> "Open"
        "done" -> "Done"
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
        "checking" -> "${app.where} · ${app.greens} of ${CrewRules.VERDICTS} have installed it"
        "testing" -> "${app.where} · day ${app.day} of ${app.runDays}, ${app.testers} testing"
        "finished" -> "${app.where} · ${app.testers} tested it for ${app.runDays} days"
        "rejected" -> "${app.where} · nobody could install it. Fix the listing and send it again."
        "withdrawn" -> "${app.where} · you took it back"
        "collecting" -> "Circle · ${app.testers} of ${app.reviewsBought} reviews in"
        "done" -> "Circle · all ${app.reviewsBought} reviews in"
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
        "testing" -> Status.posted to Status.postedSoft
        else -> MaterialTheme.colorScheme.onSurfaceVariant to Status.neutralSoft
    }
