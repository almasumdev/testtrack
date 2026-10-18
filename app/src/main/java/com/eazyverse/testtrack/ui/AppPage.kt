package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BadgeCheck
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AppsViewModel

/**
 * One app on its own page: what it is, how it is known to be real, and everything done with it.
 *
 * Crews and feedback start here and nowhere else, so the app is never typed twice. Going live and
 * deleting are here too, each asked once: one ends crews for the app for good, the other removes
 * it, and an app tested in a crew or reviewed cannot be removed at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPage(
    vm: AppsViewModel,
    id: String,
    onBack: () -> Unit,
    onOpenCrew: (String) -> Unit,
    onOpenRequest: (String) -> Unit,
    onCrew: (String) -> Unit,
    onFeedback: (String) -> Unit,
) {
    val context = LocalContext.current
    // Deleted here: still drawn while the page leaves, rather than flashing the not-found state.
    var leaving by remember { mutableStateOf<AppEntry?>(null) }
    val app = vm.byId(id) ?: leaving
    LaunchedEffect(id) {
        vm.clearActionError()
        if (app == null) vm.load(context)
    }
    var askLive by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { PageBar(app?.name ?: "App", onBack) },
    ) { padding ->
        val scroll = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Only the app scrolls: the loading and not-found states fill the page themselves,
                // and a filling state inside a scroll has no height to fill and crashes.
                .then(if (app != null) Modifier.verticalScroll(scroll) else Modifier)
                .padding(horizontal = Gutter),
        ) {
            when {
                app == null && vm.loading -> CrewLobbyLoading()
                app == null ->
                    RobotState(
                        Prop.Notes,
                        "This app isn't on My apps",
                        "It may have been deleted on another phone.",
                        action = "Back",
                        onAction = onBack,
                    )
                else -> {
                    Spacer(Modifier.height(8.dp))
                    Head(app)
                    Spacer(Modifier.height(20.dp))
                    Section("Crew test", crewLine(app)) {
                        app.crew?.let { crew ->
                            if (crew.state != "rejected" && crew.state != "removed") {
                                Secondary("Open ${crew.crewName}", Modifier.fillMaxWidth()) { onOpenCrew(crew.crewId) }
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                        if (app.canCrew) {
                            Primary("Put in a crew · ${CrewRules.SEAT_COST} tix", tall = true) { onCrew(app.id) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Section("Feedback", feedbackLine(app)) {
                        app.feedback?.let { feedback ->
                            Secondary("Open the request", Modifier.fillMaxWidth()) { onOpenRequest(feedback.postId) }
                            Spacer(Modifier.height(10.dp))
                        }
                        if (app.canFeedback) {
                            Primary("Ask for feedback", tall = true) { onFeedback(app.id) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    vm.actionError?.let {
                        AskMessage(AskFailure("That didn't work", it))
                        Spacer(Modifier.height(12.dp))
                    }
                    val busy = vm.acting == app.id
                    if (!app.live) {
                        Secondary("It's live on Google Play now", Modifier.fillMaxWidth(), enabled = !busy) { askLive = true }
                        Spacer(Modifier.height(10.dp))
                    }
                    if (app.canDelete) {
                        Secondary("Delete app", Modifier.fillMaxWidth(), destructive = true, enabled = !busy) { askDelete = true }
                    } else if (app.whyNotDelete.isNotBlank()) {
                        Text(
                            "Can't be deleted. ${app.whyNotDelete}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Tt.colors.text3,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (askLive && app != null) {
        Ask(
            title = "Mark ${app.name} live?",
            body =
                "We'll check its public Play page. Once it's live it takes feedback, but it can " +
                    "never go into a crew again.",
            confirm = "Mark it live",
            onConfirm = {
                askLive = false
                vm.goLive(context, app.id)
            },
            onDismiss = { askLive = false },
        )
    }
    if (askDelete && app != null) {
        Ask(
            title = "Delete ${app.name}?",
            body = "It comes off My apps. Nothing has happened to it yet, so there is nothing to keep.",
            confirm = "Delete",
            destructive = true,
            onConfirm = {
                askDelete = false
                vm.delete(context, app.id) {
                    leaving = app
                    onBack()
                }
            },
            onDismiss = { askDelete = false },
        )
    }
}

/** The app: its icon and name, where it is found, and how it is known to be real. */
@Composable
private fun Head(app: AppEntry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppMark(app.pkg, app.name, app.icon, size = 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                if (app.developer.isBlank()) app.pkg else "${app.developer} · ${app.pkg}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        SourceTag(app.live)
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.Top) {
        val tone = if (app.verified) Status.posted else Status.short
        Icon(if (app.verified) Lucide.BadgeCheck else Lucide.CircleAlert, null, Modifier.size(18.dp), tint = tone)
        Spacer(Modifier.width(8.dp))
        Text(verifiedLine(app), style = MaterialTheme.typography.bodySmall, color = Tt.colors.text2)
    }
}

/** A part of the page: its title, where things stand in a sentence, then what can be done. */
@Composable
private fun Section(
    title: String,
    line: String,
    actions: @Composable () -> Unit,
) {
    CrewCard {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Tt.colors.text)
        Spacer(Modifier.height(4.dp))
        Text(line, style = MaterialTheme.typography.bodySmall, color = Tt.colors.text2)
        Spacer(Modifier.height(14.dp))
        actions()
    }
}
