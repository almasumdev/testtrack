package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.viewmodel.AppsViewModel

/**
 * My apps: the one place an app lives.
 *
 * An app is added here once, and its crews and feedback start from its own page, so this is a
 * plain list of apps rather than a list of what was sent where. Each app says what is happening
 * to it in badges: a crew and a feedback request can both be running, and either can be done.
 */
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

    LoadSwap(DebugLooks.holdLoading || (vm.loading && vm.apps.isEmpty() && !vm.offline && !vm.signedOut)) { waiting ->
        if (waiting) {
            AppsLoading()
        } else {
            val canAdd = !vm.signedOut && !(vm.offline && vm.apps.isEmpty())
            Box(Modifier.fillMaxSize()) {
                AppsBody(
                    apps = vm.apps,
                    offline = vm.offline,
                    signedOut = vm.signedOut,
                    onSignIn = onSignIn,
                    onRetry = { vm.load(context) },
                    onOpen = { onOpenApp(it.id) },
                    onAdd = onAddApp,
                    addRoom = if (canAdd && vm.apps.isNotEmpty()) AddRoom else 0.dp,
                )
                // Over the list, never over the empty state, which has its own button in the middle.
                if (canAdd && vm.apps.isNotEmpty()) {
                    AddAppButton(
                        onClick = onAddApp,
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = Gutter, bottom = dockRoom() + 12.dp)
                                .width(IntrinsicSize.Max),
                    )
                }
            }
        }
    }
}

/** What the list keeps clear at its foot so its last app can scroll out from under the button. */
private val AddRoom = 64.dp

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
    /** Room kept at the foot for a button floating over the list. */
    addRoom: Dp = 0.dp,
) {
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
        contentPadding = PaddingValues(top = 4.dp, bottom = Gutter + dockRoom() + addRoom),
    ) {
        item { CrewHeading("Your apps", trailing = "${apps.size}") }
        items(apps, key = { it.id }) { app ->
            // Fades in, fades out and glides to its new place when the list changes.
            Box(Modifier.animateItem()) {
                AppCard(app, Modifier.padding(horizontal = Gutter, vertical = 5.dp), onOpen = { onOpen(app) })
            }
        }
    }
}
