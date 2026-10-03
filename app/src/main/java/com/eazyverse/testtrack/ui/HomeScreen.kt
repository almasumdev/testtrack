package com.eazyverse.testtrack.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.CircleArrowDown
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.data.*
import com.eazyverse.testtrack.ui.theme.Tt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenGroup: (String, String) -> Unit,
    onSubmitApp: () -> Unit,
    onOpenProfile: () -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current

    /*
     * Loading, push registration, catch-up, notices and the version sync all used to live here.
     * They are in [MainShell] now — see the note there. None of them were ever about the group
     * list; they were about the app being open, and this screen stopped being the whole of that
     * the moment it became one tab of five.
     */

    /**
     * Below Android 13 there is no runtime permission to ask for, so the system dialog never
     * appears and the prompt would be a button that does nothing. Sending those to the app's
     * notification settings is the only way to turn them back on there.
     */
    val notificationLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { vm.showNotices(context) }

    // Play hands back an IntentSender rather than an Intent, so the update flow needs its own
    // contract. The result is ignored on purpose: cancelling is an answer, and the icon stays in
    // the app bar for whenever they want it.
    val updateLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) {}

    /*
     * An optional update never interrupts. It is the icon in the app bar and nothing else until
     * somebody taps it; the Restart banner below follows only that tap, so a download left on disk
     * from an earlier session waits behind the same icon rather than greeting them at launch.
     */
    val updateTier by AppUpdateService.tier.collectAsState()
    val updateDownloaded by AppUpdateService.isDownloaded.collectAsState()
    val userStartedUpdate by AppUpdateService.userStartedUpdate.collectAsState()
    val showUpdateIcon = updateTier == UpdateTier.NUDGE || updateDownloaded
    val updateBanner =
        if (userStartedUpdate && updateDownloaded) HomeUpdateBanner.DOWNLOADED else HomeUpdateBanner.NONE
    val onUpdateIconClick: () -> Unit = {
        // An offline replay, or a Play that will not download in the background, has nothing to
        // fetch in-app, so the store listing is the way to the update.
        if (!AppUpdateService.startUserUpdate(updateLauncher)) {
            runCatching {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    val askNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }

    /**
     * The app whose notes are being written, or null.
     *
     * Editable before a group exists on purpose. A developer submits, then spends days waiting for
     * a cohort, and that gap is exactly when they realise the test account they gave has expired.
     */
    var editingNotes by remember { mutableStateOf<TestApp?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // The same mark the notification shade already shows for this app, at UI
                        // scale instead of status-bar scale — one glyph standing for TestTrack
                        // rather than two different ones depending on where you see it.
                        Icon(
                            painterResource(R.drawable.ic_stat_name),
                            null,
                            modifier = Modifier.size(22.dp),
                            tint = Tt.colors.accentInk,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("TestTrack", fontWeight = FontWeight.Bold)
                        // Content already on screen quietly catching up with itself — coming back
                        // from a group, or the app returning to the foreground. Small and off to
                        // the side on purpose: the numbers below are the thing worth looking at,
                        // not this. Never alongside a pull, which gets the full skeleton below
                        // instead — asking outright earns a stronger answer than this.
                        if (vm.refreshing && !vm.pulling) {
                            Spacer(Modifier.width(8.dp))
                            CircularProgressIndicator(
                                Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                // Both actions shown rather than folded behind an overflow. Two is under the
                // threshold where a menu earns its extra tap, and neither is findable by guessing
                // that a three-dot icon contains it.
                //
                // Sign out used to be the second of them and now lives on the profile, which is
                // where an account action belongs and what the face on the right leads to. It is
                // available on the profile when somebody needs to change accounts after they
                // have signed in as the wrong person.
                actions = {
                    // The whole of the optional update: no banner, no sheet, just this until tapped.
                    if (showUpdateIcon) {
                        IconButton(onClick = onUpdateIconClick) {
                            Icon(Lucide.CircleArrowDown, "Update available")
                        }
                    }
                    IconButton(onClick = onOpenProfile) {
                        Avatar(vm.photo, vm.initial, size = 28.dp, loading = vm.me == null)
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
            )
        },
        // Its own slot rather than the last thing in the scrolling column, so the one button
        // every visit here ends with is never a scroll away — the skeleton and the loading gate
        // it used to share a condition with stay exactly the same, only where it sits changed.
        bottomBar = {
            if (vm.ready && !vm.pulling) {
                Column {
                    // Solid, not the dashed rule rows use between each other — this is the edge
                    // of a fixed piece of chrome, not a separator between two like things.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Primary(
                        "Submit an app",
                        Modifier
                            .padding(horizontal = Gutter)
                            .padding(top = 12.dp, bottom = 12.dp),
                        onClick = onSubmitApp,
                    )
                }
            }
        },
    ) { padding ->
        RefreshBox(
            // Bound to the pull, not the general refresh — otherwise the gesture's own arc plays
            // for a resume or a return from a group too, which nobody pulled for.
            isRefreshing = vm.pulling,
            onRefresh = { vm.load(fromPull = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                // A pull gets the same full skeleton a first load does — asking outright earns a
                // stronger answer than the small app-bar mark a quiet resume settles for.
                if (!vm.ready || vm.pulling) {
                    HomeSkeleton()
                } else {
                    if (vm.groups.isNotEmpty()) {
                        Row(
                            Modifier
                                .padding(start = Gutter, end = Gutter, top = 4.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                                SegmentedRing(
                                    done = vm.doneToday,
                                    total = vm.dueToday,
                                    modifier = Modifier.fillMaxSize(),
                                    strokeWidth = 9.dp,
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${vm.doneToday}",
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "of ${vm.dueToday}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.width(20.dp))

                            val remaining = vm.dueToday - vm.doneToday
                            val groupsPhrase =
                                "${vm.groups.size} " +
                                    if (vm.groups.size == 1) "group" else "groups"
                            // Real, not invented: the same VISIT_MS every visit is actually held open
                            // for, times what is actually left, rounded to the nearest minute and
                            // never down to zero while there is still at least one app to go.
                            val minutesLeft =
                                ((remaining * CaptureService.VISIT_MS + 30_000L) / 60_000L)
                                    .coerceAtLeast(1L)

                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Today".uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.2.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    when {
                                        vm.dueToday == 0 -> "Nothing due today"
                                        remaining <= 0 -> "You're clear for tonight"
                                        remaining == 1 -> "One app left before you're clear for tonight"
                                        else -> "$remaining apps left before you're clear for tonight"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (vm.dueToday > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Tested across $groupsPhrase so far" +
                                            if (remaining > 0) {
                                                " · about " +
                                                    (if (minutesLeft == 1L) "a minute" else "$minutesLeft minutes") +
                                                    " to finish up"
                                            } else {
                                                ""
                                            },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    // Above everything, because a tester one day from losing their place should not
                    // have to scroll to a group row to find that out.
                    vm.standings.forEach { standing ->
                        MissedDayNotice(standing) { vm.dismissStanding(context, standing) }
                    }

                    // Things that already happened, kept until acknowledged. A notification is not a
                    // record: it can be swiped off a lock screen unread.
                    vm.notices.forEach { notice ->
                        EventNotice(notice) { vm.dismissNotice(context, notice.id) }
                    }

                    if (vm.askForNotifications) NotificationPrompt(onGrant = askNotifications)

                /*
                 * The Restart half of an update somebody asked for from the app bar. The hard half
                 * of the gate sits over the whole app.
                 *
                 * Here rather than anywhere else because home is the screen somebody arrives on
                 * with nothing to do yet. An offer to restart mid-round would be asking them to
                 * restart the app in the middle of the one thing the app is for.
                 */
                    if (updateBanner != HomeUpdateBanner.NONE) {
                        UpdateBannerCard(
                            state = updateBanner,
                            onRestart = { AppUpdateService.completeUpdate() },
                            modifier = Modifier.padding(horizontal = Gutter).padding(top = 18.dp),
                        )
                    }

                    // Nothing at all: one empty state rather than two headed sections standing over
                    // nothing.
                    if (vm.groups.isEmpty() && vm.pending.isEmpty()) {
                        EmptyState(
                            icon = Lucide.Users,
                            title = "No crews yet",
                            subtitle =
                                "Submit an app you've put on a closed track. An admin places " +
                                    "it into a crew, and your 14 days start once that crew is " +
                                    "full.",
                        )
                    } else {
                        SectionLabel("Your crews")
                        Rows {
                            if (vm.groups.isEmpty()) {
                                Blank("None yet.")
                            } else {
                                vm.groups.forEachIndexed { index, progress ->
                                    if (index > 0) RowDivider()
                                    GroupRow(progress) { onOpenGroup(progress.group.id, progress.group.name) }
                                }
                            }
                        }

                        if (vm.pending.isNotEmpty()) {
                            SectionLabel("Waiting for a crew")
                            Rows {
                                vm.pending.forEachIndexed { index, app ->
                                    if (index > 0) RowDivider()
                                    PendingRow(
                                        app = app,
                                        onEditNotes = { editingNotes = app },
                                        onWithdraw = { vm.withdraw(app) },
                                    )
                                }
                            }
                        }
                    }

                    vm.message?.let { Failure(it) }
                }
                Spacer(Modifier.height(Gutter))
            }
        }
    }

    editingNotes?.let { app ->
        HomeNotesDialog(
            app = app,
            onSave = {
                editingNotes = null
                vm.updateNotes(app, it)
            },
            onDismiss = { editingNotes = null },
        )
    }
}
