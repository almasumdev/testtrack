package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.viewmodel.AppsViewModel
import kotlinx.coroutines.flow.filter

/**
 * My apps: the one place an app lives.
 *
 * An app is added here once, and its crews and feedback start from its own page, so this is a
 * plain list of apps rather than a list of what was sent where. Each tile says what is happening
 * to it in one line, with a mark on the right when something needs the owner.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onSignIn: () -> Unit = {},
    /** An app's own page. */
    onOpenApp: (String) -> Unit = {},
    /** Adding an app. */
    onAddApp: () -> Unit = {},
    vm: AppsViewModel = viewModel(),
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.load(context) }
    // A crew filling or a review arriving shows up without leaving the tab.
    WhileShown(onShow = { vm.startLive(context) }, onHide = vm::stopLive)

    val listState = rememberLazyListState()
    val waiting = DebugLooks.holdLoading || (vm.loading && vm.apps.isEmpty() && !vm.offline && !vm.signedOut)
    val canAdd = !vm.signedOut && !(vm.offline && vm.apps.isEmpty())
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LoadSwap(waiting) { hold ->
                if (hold) {
                    AppsLoading()
                } else {
                    AppsBody(
                        apps = vm.apps,
                        offline = vm.offline,
                        signedOut = vm.signedOut,
                        onSignIn = onSignIn,
                        onRetry = { vm.load(context) },
                        onOpen = { onOpenApp(it.id) },
                        onAdd = if (canAdd) onAddApp else null,
                        listState = listState,
                    )
                }
            }
        }
    }
}

/** The list, drawn. Handed its values so every state it has can be put in front of a test. */
@Composable
fun AppsBody(
    apps: List<AppEntry>,
    loading: Boolean = false,
    offline: Boolean = false,
    signedOut: Boolean = false,
    onSignIn: () -> Unit = {},
    onRetry: () -> Unit = {},
    onOpen: (AppEntry) -> Unit = {},
    /** Adding an app; the empty state's button. Not offered where there is none. */
    onAdd: (() -> Unit)? = null,
    /** The list's scroll, also used to close an app's problem callout. */
    listState: LazyListState = rememberLazyListState(),
) {
    // Which app's problem callout is open: one at a time, and a scroll closes it.
    var openProblemFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { openProblemFor = null }
    }
    // Asked for here rather than at the door. Somebody can read the feed, the crews and the board
    // without an account; this is the first screen that cannot answer without knowing who they
    // are, so this is where the question belongs, with the way out attached to it.
    if (signedOut) {
        RobotState(
            Prop.Lock,
            "Sign in to see your apps",
            "Your apps, and how each one is getting on, belong to your account.",
            action = "Sign in",
            onAction = onSignIn,
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
        RobotState(
            Prop.Phone,
            "Nothing added yet",
            "Add your app once. Then put it in a crew for Play's closed test, or ask other " +
                "developers for feedback, from its page.",
            action = if (onAdd != null) "Add an app" else "",
            onAction = { onAdd?.invoke() },
            footer = { HowToLinks(HowTo.APPS) },
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(top = 12.dp, bottom = Gutter + dockRoom()),
    ) {
        items(apps, key = { it.id }) { app ->
            // Fades in, fades out and glides to its new place when the list changes.
            Box(Modifier.animateItem()) {
                AppCard(
                    app,
                    Modifier.padding(horizontal = Gutter, vertical = 4.dp),
                    problemOpen = openProblemFor == app.id,
                    onProblemOpenChange = { open ->
                        if (open) {
                            openProblemFor = app.id
                        } else if (openProblemFor == app.id) {
                            openProblemFor = null
                        }
                    },
                    onOpen = { onOpen(app) },
                )
            }
        }
    }
}
