package com.eazyverse.testtrack.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.composables.icons.lucide.WifiOff
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.GroupUi
import com.eazyverse.testtrack.viewmodel.MembershipViewModel
import com.eazyverse.testtrack.viewmodel.offline
import kotlinx.coroutines.delay
import java.net.URLEncoder

/** The one membership check for the whole app, whichever screen asks. */
@Composable
fun rememberMembership(): MembershipViewModel = viewModel(LocalContext.current.findActivity() as ComponentActivity)

/** How long the joined face stays, and how long its line takes to fill. */
internal const val GROUP_JOINED_MS = 1_200

/**
 * The testers group, in one sheet whose inside changes as the answer comes in.
 *
 * It used to be a window per state, so each change closed one window and opened the next. Now it
 * opens once in the sign-in sheet's frame, waits calmly behind the browser while the group page is
 * open and ends one of two ways: by itself a moment after a yes, or when somebody puts it away.
 *
 * Nothing locks it. Swipe, Back and a tap outside close it at any time, and closing forgets what
 * was waiting. A yes that arrives after that is kept on the phone and not shown. See
 * [MembershipViewModel].
 */
@Composable
fun GroupSheet(vm: MembershipViewModel = rememberMembership()) {
    val ui = vm.ui ?: return
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watcher = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.returned() }
        owner.lifecycle.addObserver(watcher)
        onDispose { owner.lifecycle.removeObserver(watcher) }
    }
    // Signed in as somebody else than the sheet was opened for: it is about the wrong account.
    LaunchedEffect(vm.email) { vm.accountChanged() }
    // Set just before the sheet closes itself after a yes, so that close ends in proceed rather
    // than in close, which would forget whatever was waiting.
    var done by remember { mutableStateOf(false) }
    GroupSheetFrame(ui, onDismiss = { if (done) vm.proceed() else vm.close() }) { close ->
        LaunchedEffect(ui is GroupUi.Joined) {
            if (ui is GroupUi.Joined) {
                delay(GROUP_JOINED_MS.toLong())
                done = true
                close {}
            }
        }
        GroupSheetBody(
            ui = ui,
            email = vm.email,
            onOpenGroup = {
                vm.openedGroup()
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(groupLink(Config.GROUP_URL, vm.email))))
            },
            onCheck = vm::check,
            onClose = { close {} },
        )
    }
}

/** The sheet's frame: the sign-in sheet's, with the state's own title and tile. */
@Composable
internal fun GroupSheetFrame(
    ui: GroupUi,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(close: (() -> Unit) -> Unit) -> Unit,
) {
    PickerSheet(
        title = groupTitle(ui),
        onDismiss = onDismiss,
        leading = { GroupLead(ui) },
        containerColor = Tt.colors.sheet,
        topLine = Tt.colors.sheetLine,
        handleGap = 16.dp,
        headerGap = 12.dp,
        titleLiveRegion = true,
        skipHalf = true,
        content = content,
    )
}

/**
 * The group's page, opened on the account TestTrack is signed in as. Without it Google opens the
 * page on whichever of the phone's accounts it likes, and joining with the wrong one is the
 * commonest reason a check keeps saying no.
 */
internal fun groupLink(
    base: String,
    email: String,
): String = if (email.isBlank()) base else base + (if ('?' in base) "&" else "?") + "authuser=" + URLEncoder.encode(email, "UTF-8")

/** The sheet's title for a state. */
internal fun groupTitle(ui: GroupUi): String =
    when (ui) {
        GroupUi.Checking -> "Checking the testers group"
        is GroupUi.Join, GroupUi.Rechecking -> "Join the testers group"
        GroupUi.Joined -> "You're in the testers group"
        GroupUi.StillOut -> "Not in the group yet"
        is GroupUi.Failed -> "Couldn't check the group"
    }

/** What sits before the title: a toned tile, neutral until there is something to say. */
@Composable
internal fun GroupLead(ui: GroupUi) {
    val t = Tt.colors
    when (ui) {
        GroupUi.Checking, is GroupUi.Join, GroupUi.Rechecking -> StatusTile(Lucide.Users, t.tileInk, t.tile)
        GroupUi.Joined -> StatusTile(Lucide.Check, t.ok, t.okBg)
        GroupUi.StillOut -> StatusTile(Lucide.Hourglass, t.warn, t.warnBg)
        // A dropped connection reads as one; anything else is a message the server sent.
        is GroupUi.Failed ->
            StatusTile(if (ui.offline) Lucide.WifiOff else Lucide.CircleAlert, t.bad, t.badBg)
    }
}

/**
 * The sheet's contents, given its state rather than holding it, so every face can be put in
 * front of a test. The title and tile are the frame's, from [groupTitle] and [GroupLead].
 */
@Composable
fun GroupSheetBody(
    ui: GroupUi,
    email: String,
    onOpenGroup: () -> Unit = {},
    onCheck: () -> Unit = {},
    onClose: () -> Unit = {},
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        // Crossed over rather than swapped, as in the sign-in sheet: the outgoing face goes first
        // and faster, the incoming one lifts a little, and the height follows. Keyed on the kind
        // of state so a change inside one kind does not play it again.
        AnimatedContent(
            targetState = ui,
            contentKey = { it::class },
            transitionSpec = {
                (
                    fadeIn(tween(durationMillis = 240, delayMillis = 70)) +
                        slideInVertically(tween(durationMillis = 300, delayMillis = 110)) { it / 5 }
                ).togetherWith(fadeOut(tween(durationMillis = 150)))
                    .using(SizeTransform(clip = false))
            },
            label = "group",
        ) { state ->
            // Its own Column: AnimatedContent lays its content out in a Box.
            Column { Face(state, email, onOpenGroup, onCheck, onClose) }
        }
    }
}

@Composable
private fun Face(
    state: GroupUi,
    email: String,
    onOpenGroup: () -> Unit,
    onCheck: () -> Unit,
    onClose: () -> Unit,
) {
    val t = Tt.colors
    val link = rememberVectorPainter(Lucide.ExternalLink)
    when (state) {
        GroupUi.Checking -> {
            Why("Reviews and crew testing need it. This takes a few seconds.")
            Account(email, "Checking now", t.text3, running = true)
            Actions {}
            TextAction("Not now", onClose)
        }

        is GroupUi.Join, GroupUi.Rechecking -> {
            val checking = state == GroupUi.Rechecking
            Why(JOIN_WHY)
            Account(email, if (checking) "Checking now" else "Not in the group yet", t.text3, running = checking)
            Spacer(Modifier.height(12.dp))
            GroupSteps(opened = checking || (state as? GroupUi.Join)?.opened == true)
            Actions {
                if (checking) {
                    GroupPrimary(
                        "Checking",
                        onClick = {},
                        busy = true,
                    )
                } else {
                    GroupPrimary("Open the group", onOpenGroup, mark = link)
                }
            }
            TextAction("I already joined", onCheck, enabled = !checking)
        }

        GroupUi.Joined -> {
            Why("Reviews and crew testing are open to you.")
            Account(email, "In the testers group", t.ok, icon = Lucide.Check)
            Spacer(Modifier.height(14.dp))
            JoinedLine()
        }

        GroupUi.StillOut -> {
            Why("Google can take a minute or two after you tap Join group. If it still says no, check these two things.")
            Account(email, "Checked just now", t.warn)
            Spacer(Modifier.height(14.dp))
            GroupHints()
            Actions { GroupPrimary("Check again", onCheck) }
            TextAction("Open the group again", onOpenGroup)
        }

        is GroupUi.Failed -> {
            Why("${state.reason} If you already joined, you're still in.")
            if (state.retrying) {
                Account(email, "Checking now", t.text3, running = true)
            } else {
                Account(email, "Couldn't check", t.bad, icon = Lucide.CircleAlert)
            }
            Actions {
                if (state.retrying) GroupPrimary("Checking", onClick = {}, busy = true) else GroupPrimary("Try again", onCheck)
            }
            TextAction("Not now", onClose)
        }
    }
}

private const val JOIN_WHY =
    "App owners share their closed tests with this group. Join it once and you can install them from Play."

/** The account row, 16 under the line above it. */
@Composable
private fun Account(
    email: String,
    status: String,
    tone: Color,
    icon: ImageVector? = null,
    running: Boolean = false,
) {
    Spacer(Modifier.height(16.dp))
    GroupAccountRow(email, status, tone, icon, running)
}

/** A thin determinate line that fills while the joined face is up, the time the sheet then waits. */
@Composable
private fun JoinedLine() {
    val t = Tt.colors
    val fill = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fill.animateTo(1f, tween(GROUP_JOINED_MS, easing = LinearEasing)) }
    LinearProgressIndicator(
        progress = { fill.value },
        modifier = Modifier.fillMaxWidth().height(3.dp),
        color = t.primary,
        trackColor = t.track,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
