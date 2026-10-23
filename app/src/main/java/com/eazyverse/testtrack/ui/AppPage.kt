package com.eazyverse.testtrack.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowUpRight
import com.composables.icons.lucide.BadgeCheck
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AppAction
import com.eazyverse.testtrack.viewmodel.AppsViewModel

/**
 * One app on its own page: the app itself up top, then what is being done with it, then managing it.
 *
 * Crews and feedback start here and nowhere else, so the app is never typed twice. An app nothing
 * has verified says so first, with a way to ask Play again now. Going live and deleting are asked
 * once each: one ends crews for the app for good, the other removes it, and an app tested in a
 * crew or reviewed cannot be removed at all.
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
    var changingIcon by remember { mutableStateOf(false) }
    val picture =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val target = app
            if (uri != null && target != null) vm.iconFromPicture(context, target.id, uri)
        }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // The name is the page's own heading, so the bar is only the way back.
        topBar = { PageBar("", onBack) },
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
                    val busy = vm.acting == app.id
                    val running = if (busy) vm.actingOn else null
                    Spacer(Modifier.height(8.dp))
                    Hero(app, iconBusy = running == AppAction.ICON) { if (!busy) changingIcon = true }
                    Spacer(Modifier.height(20.dp))
                    // Nothing for everybody else to see it by yet: the phone's own icon is not theirs.
                    if (app.icon.isBlank()) {
                        IconNudge(app.name) { changingIcon = true }
                        Spacer(Modifier.height(14.dp))
                    }
                    if (!app.verified) {
                        VerifyCallout(running == AppAction.VERIFY) { if (!busy) vm.verify(context, app.id) }
                        Spacer(Modifier.height(4.dp))
                    }
                    vm.actionError?.let {
                        Spacer(Modifier.height(10.dp))
                        AskMessage(AskFailure("That didn't work", it))
                    }
                    Testing(app, onOpenCrew, onOpenRequest, onCrew, onFeedback)
                    Manage(app, busy, running, onLive = { askLive = true }, onDelete = { askDelete = true })
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }

    if (changingIcon && app != null) {
        AppIconSheet(
            name = app.name,
            installedHere = installedLabel(app.pkg) != null,
            onPhone = { vm.iconFromPhone(context, app.id, app.pkg) },
            onPicture = { picture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onPlay = { vm.iconFromPlay(context, app.id) },
            onDismiss = { changingIcon = false },
        )
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

/** The app, centred: its icon, name and package, and chips for where it is found and whether it's real. */
@Composable
private fun Hero(
    app: AppEntry,
    /** A new icon is being read and sent: shown on the icon itself, which is what is changing. */
    iconBusy: Boolean,
    onChangeIcon: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clickable(role = Role.Button, onClickLabel = "Change icon", onClick = onChangeIcon)) {
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

/** An app nothing has verified: why, the way to ask Play again now, and the other ways it gets verified. */
@Composable
private fun VerifyCallout(
    busy: Boolean,
    onVerify: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Status.shortSoft)
            .border(1.dp, Status.short.copy(alpha = 0.28f), shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.CircleAlert, null, Modifier.size(18.dp), tint = Status.short)
            Spacer(Modifier.width(8.dp))
            Text("Not verified yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Status.short)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Play couldn't show it to us when you added it. Add the testers group to your closed " +
                "track, then check again.",
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.text2,
        )
        Spacer(Modifier.height(12.dp))
        Primary(if (busy) "Asking Play..." else "Verify now", busy = busy, onClick = onVerify)
        Spacer(Modifier.height(8.dp))
        Text(
            "It's also verified when two crew members install it, or you accept a review.",
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.text4,
        )
    }
}

/** Crew test and feedback as two rows: where each stands, and tapping goes on to the next step. */
@Composable
private fun Testing(
    app: AppEntry,
    onOpenCrew: (String) -> Unit,
    onOpenRequest: (String) -> Unit,
    onCrew: (String) -> Unit,
    onFeedback: (String) -> Unit,
) {
    val crewRow = !app.live || app.crew != null
    SectionTitle("Testing")
    TileGroup(count = if (crewRow) 2 else 1) { index, place ->
        if (crewRow && index == 0) {
            val crew = app.crew
            TileRow(
                Lucide.Users,
                "Crew test",
                place,
                subtitle = crewStatus(app),
                trailing = if (!app.crewOpen && app.canCrew) ({ Cta("Put in · ${CrewRules.SEAT_COST} tix") }) else null,
                onClick =
                    when {
                        crew != null && app.crewOpen -> ({ onOpenCrew(crew.crewId) })
                        app.canCrew -> ({ onCrew(app.id) })
                        else -> null
                    },
            )
        } else {
            val feedback = app.feedback
            TileRow(
                Lucide.MessageSquare,
                "Feedback",
                place,
                subtitle = feedbackStatus(app),
                trailing = if (app.canFeedback) ({ Cta(if (feedback == null) "Ask" else "Ask again") }) else null,
                onClick =
                    when {
                        feedback != null && app.collecting -> ({ onOpenRequest(feedback.postId) })
                        app.canFeedback -> ({ onFeedback(app.id) })
                        feedback != null -> ({ onOpenRequest(feedback.postId) })
                        else -> null
                    },
            )
        }
    }
}

/** What a row starts, in the accent, before its chevron. */
@Composable
private fun Cta(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Bold,
            color = Tt.colors.accent,
        )
        Spacer(Modifier.width(2.dp))
        Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = Tt.colors.accent)
    }
}

/** Going live and deleting, each behind its own question; an app that can't go says why. */
@Composable
private fun Manage(
    app: AppEntry,
    busy: Boolean,
    running: AppAction?,
    onLive: () -> Unit,
    onDelete: () -> Unit,
) {
    val rows = listOfNotNull("live".takeIf { !app.live }, "delete".takeIf { app.canDelete })
    if (rows.isEmpty() && app.whyNotDelete.isBlank()) return
    SectionTitle("Manage")
    if (rows.isNotEmpty()) {
        TileGroup(count = rows.size) { index, place ->
            if (rows[index] == "live") {
                val checking = running == AppAction.LIVE
                TileRow(
                    Lucide.ArrowUpRight,
                    "It's live on Google Play now",
                    place,
                    subtitle = if (checking) "Checking its public Play page..." else "Moves it to feedback only",
                    trailing = if (checking) ({ RowBusy() }) else null,
                    onClick = if (busy) null else onLive,
                )
            } else {
                TileRow(
                    Lucide.Trash2,
                    "Delete app",
                    place,
                    danger = true,
                    subtitle = if (running == AppAction.DELETE) "Deleting..." else null,
                    trailing = if (running == AppAction.DELETE) ({ RowBusy() }) else null,
                    onClick = if (busy) null else onDelete,
                )
            }
        }
    }
    if (!app.canDelete && app.whyNotDelete.isNotBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Can't be deleted. ${app.whyNotDelete}",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.text4,
        )
    }
}

/** A row's action under way: a small spinner where its chevron was. */
@Composable
private fun RowBusy() {
    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Tt.colors.accentInk)
}
