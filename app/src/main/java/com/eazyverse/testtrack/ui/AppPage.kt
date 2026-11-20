package com.eazyverse.testtrack.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowUpRight
import com.composables.icons.lucide.BadgeCheck
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AppAction
import com.eazyverse.testtrack.viewmodel.AppsViewModel
import com.eazyverse.testtrack.viewmodel.AskFailure

/**
 * One app on its own page: the app itself up top, then what is being done with it.
 *
 * Crews and feedback start here and nowhere else, so the app is never typed twice. An app nothing
 * has verified says so first, with a way to ask Play again now. Going live is a soft pill under the
 * chips and deleting is the bin in the bar, each asked once: one ends crews for the app for good,
 * the other removes it. An app tested in a crew or reviewed cannot be removed, and the bin says why.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPage(
    vm: AppsViewModel,
    id: String,
    onBack: () -> Unit,
    onOpenCrew: (String) -> Unit,
    onOpenRequest: (String) -> Unit,
    onFeedback: (String) -> Unit,
    /** The Check on a request's card: the first review waiting on the owner, or the request without one. */
    onCheck: (Request) -> Unit = { onOpenRequest(it.id) },
    /** The Crew tab's model, for this app's crew tile. */
    crews: CrewViewModel,
    /** This app's own feedback requests, from Circle's model, and whether they are still on their way. */
    requests: List<Request>,
    requestsLoading: Boolean,
    /** The seat screen for this app. */
    onSeat: (String) -> Unit = {},
    onOpenChat: (String) -> Unit = {},
    /** Left by the page this one came back from, such as a request that has just gone up. */
    message: String? = null,
    onMessageTaken: () -> Unit = {},
) {
    val context = LocalContext.current
    // Putting it in a crew: on the testers group first, then the seat screen on this app.
    val membership = rememberMembership()
    // Deleted here: still drawn while the page leaves, rather than flashing the not-found state.
    var leaving by remember { mutableStateOf<AppEntry?>(null) }
    val app = vm.byId(id) ?: leaving
    LaunchedEffect(id) {
        vm.clearActionError()
        if (app == null) vm.load(context)
    }
    var askLive by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    // Said under the bar, such as why the bin can't delete this app.
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            note = message
            onMessageTaken()
        }
    }
    val picture =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val target = app
            if (uri != null && target != null) vm.iconFromPicture(context, target.id, uri)
        }
    // The pencil goes straight to the gallery. Play's icon is filled in by the server once Play
    // shows the app, so a sheet of choices added a step and nothing else.
    val choosePicture = { picture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // The name is the page's own heading, so the bar is the way back and the bin.
        topBar = {
            Column {
                PageBar("", onBack, actions = {
                    if (app != null) {
                        val deleting = vm.acting == app.id && vm.actingOn == AppAction.DELETE
                        DeleteAction(app.canDelete, deleting) {
                            when {
                                vm.acting == app.id -> Unit
                                app.canDelete -> askDelete = true
                                else -> note = "Can't delete. ${app.whyNotDelete}".trim()
                            }
                        }
                    }
                })
                PageMessage(note, onDone = { note = null })
            }
        },
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
                app == null && (vm.loading || vm.reading) -> AppHeroLoading()
                app == null -> AppNotThere(vm.offline, onRetry = { vm.load(context) }, onBack = onBack)
                else -> {
                    val busy = vm.acting == app.id
                    val running = if (busy) vm.actingOn else null
                    Spacer(Modifier.height(8.dp))
                    Hero(
                        app,
                        iconBusy = running == AppAction.ICON,
                        liveBusy = running == AppAction.LIVE,
                        onChangeIcon = { if (!busy) choosePicture() },
                        onLive = { if (!busy) askLive = true },
                    )
                    if (!app.verified) {
                        Spacer(Modifier.height(16.dp))
                        AppVerifyCard(app.live, running == AppAction.VERIFY) { if (!busy) vm.verify(context, app.id) }
                    }
                    vm.actionError?.let {
                        Spacer(Modifier.height(10.dp))
                        AskMessage(AskFailure("That didn't work", it))
                    }
                    // A live app can never go into a crew, so it has no crew block unless it was in one.
                    if (!app.live || app.crew != null) {
                        BlockHeading("Crew test", CREW_WHEN)
                        AppCrewBlock(app, crews, onOpenCrew, onOpenChat, onPutIn = { membership.require { onSeat(app.id) } })
                    }
                    BlockHeading("Feedback", FEEDBACK_WHEN)
                    FeedbackRequests(
                        requests = requests,
                        loading = requestsLoading,
                        onOpen = { onOpenRequest(it.id) },
                        onAsk = { onFeedback(app.id) },
                        onCheck = onCheck,
                        onFix = { onOpenRequest(it.id) },
                        canAsk = app.canFeedback,
                    )
                    Spacer(Modifier.height(16.dp))
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

/**
 * The bin in the bar. Quiet ink when the app can go; faded when it can't, and a tap then says why
 * under the bar instead of asking. A loader takes its place while the app is being deleted.
 */
@Composable
private fun DeleteAction(
    canDelete: Boolean,
    deleting: Boolean,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    val tint = if (canDelete) t.text2 else t.text4.copy(alpha = 0.55f)
    PageBarIcon(Lucide.Trash2, "Delete app", onClick, tint = tint, enabled = !deleting) {
        if (deleting) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = t.text2)
        } else {
            Icon(Lucide.Trash2, "Delete app", Modifier.size(22.dp), tint = tint)
        }
    }
}

/**
 * The app, centred: its icon, name and package, chips for where it is found and whether it's real,
 * and for a closed test the pill that says it is live now.
 */
@Composable
private fun Hero(
    app: AppEntry,
    /** A new icon is being read and sent: shown on the icon itself, which is what is changing. */
    iconBusy: Boolean,
    /** Play is being asked whether it is live: the pill says so and takes no taps. */
    liveBusy: Boolean,
    onChangeIcon: () -> Unit,
    onLive: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).iconTap(onChangeIcon)) {
            AppMark(app.pkg, app.name, app.icon, size = 88.dp, ownIcon = true)
            if (iconBusy) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(88.dp * 0.3f))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp, color = Tt.colors.accentInk) }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, Tt.colors.glassBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Lucide.Pencil, null, Modifier.size(15.dp), tint = Tt.colors.accentInk) }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            app.name,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 26.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            app.pkg,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppChip(
                if (app.live) Lucide.Globe else Lucide.FlaskConical,
                if (app.live) "Live" else "Closed test",
                MaterialTheme.colorScheme.onSurfaceVariant,
                Status.neutralSoft,
            )
            if (app.verified) {
                AppChip(Lucide.BadgeCheck, verifiedChip(app), Status.posted, Status.postedSoft)
            } else {
                AppChip(Lucide.CircleAlert, verifiedChip(app), Status.short, Status.shortSoft)
            }
        }
        // A closed test can say it's live now; a live app has only its Live chip.
        if (!app.live) {
            Spacer(Modifier.height(10.dp))
            SoftPill(
                if (liveBusy) "Checking Play" else "It's live on Play",
                icon = Lucide.ArrowUpRight,
                onClick = onLive,
                busy = liveBusy,
            )
        }
    }
}

/** A small fact about an app, as its page shows them: where it is found, whether it is real. */
@Composable
internal fun AppChip(
    icon: ImageVector,
    text: String,
    tone: Color,
    background: Color,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = tone)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), fontWeight = FontWeight.SemiBold, color = tone)
    }
}
