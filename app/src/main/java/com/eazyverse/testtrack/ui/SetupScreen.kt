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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.data.*
import com.eazyverse.testtrack.findActivity

/** The answer to one step's button, shown under that button rather than at the foot of the page. */
@Composable
private fun StepMessage(
    vm: SetupViewModel,
    step: Int,
) {
    if (vm.messageStep != step) return
    vm.message?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
fun SetupScreen(
    onFinished: () -> Unit,
    onSignOut: () -> Unit,
    vm: SetupViewModel = viewModel(),
) {
    val activity = LocalContext.current.findActivity()
    var confirmSignOut by remember { mutableStateOf(false) }

    val consentLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            val token = AuthRepo.tokenFromConsent(activity, result.data)
            Session.updateDriveConnected(token != null)
            if (token == null) vm.say(SetupViewModel.STEP_DRIVE, "Drive access was not granted")
        }

    // Usage access and notifications both live in system settings and can be switched off behind
    // our back, so they are re-read every time this screen comes forward rather than trusted from
    // a stored flag.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    Session.refreshUsageAccess(activity)
                    Session.refreshNotifications(activity)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The channel exists before the prompt, so the name the tester sees in system settings is the
    // one they will be looking for if they ever go turning it off.
    LaunchedEffect(Unit) { PushRepo.ensureChannel(activity) }

    /**
     * Android answers the notification prompt exactly once per install.
     *
     * A second request after a refusal returns denied without showing anything, so the button has
     * to change into one that opens system settings instead — otherwise it becomes a control that
     * visibly does nothing.
     */
    var promptSpent by rememberSaveable { mutableStateOf(false) }
    val notificationLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) {
            promptSpent = true
            Session.refreshNotifications(activity)
            Session.updateRemindersAsked()
        }

    // Same for Drive: the grant is the account's, so ask Google rather than trusting a local flag
    // that sign-out cleared. Silent when it is already there.
    LaunchedEffect(Unit) {
        if (!Session.driveConnected) {
            runCatching { Session.updateDriveConnected(AuthRepo.hasDriveAccess(activity)) }
        }
    }

    /**
     * And the group, which used to be answered during sign-in and had no business being there.
     *
     * This is the slowest question the app asks: the service verifies the token with Google and
     * then reads the whole testers roster, which grows with every person who joins. Asked here it
     * costs nobody anything, because the tester is already on the screen and reading the step it
     * belongs to.
     *
     * Silent, but not one-way. It used to run only while the flag was false and only ever write
     * true, and the flag is kept on disk, so the first true was permanent: somebody who left the
     * group, was removed from it, or never belonged and got one wrong answer went on being told
     * "You're in the group" on every launch after. A membership check that cannot take a
     * membership away is not a check.
     *
     * So a definite verdict is written either way. Only [GateResult.Failed] leaves the flag
     * alone, because that is the service not answering, and a check that could not run is no
     * reason to throw away what was last known.
     *
     * Silent for real, which the first attempt at this was not. That one went back to Google for a
     * token whenever the held one had expired, and going back to Google can put an account sheet
     * on screen; opening setup from home is not a request to pick an account. So it asks with the
     * token from sign-in or it does not ask, and no token leaves the flag alone for the same
     * reason a failure does. The cost is that a membership lost between one launch and the next
     * goes unnoticed until the tester presses Verify. That is the right way round: a check that
     * interrupts you to run is worse than one that waits to be asked.
     *
     * And the verdict has to be about the account on this screen. Credential Manager answers for
     * whichever Google account it picks, not necessarily the one that signed in.
     *
     * Nothing is said out loud here. "Not in the group yet" belongs to the Verify button, when a
     * tester asks for it, rather than greeting a newcomer who has not had a chance to join.
     */
    LaunchedEffect(Unit) {
        runCatching {
            val gate = AuthRepo.recheckGroupSilently()
            if (gate != null && !gate.isAboutSomeoneElse(Session.email)) {
                when (gate) {
                    is GateResult.Member -> Session.updateMember(true)
                    is GateResult.NotMember -> Session.updateMember(false)
                    is GateResult.Failed -> Unit
                }
            }
        }
    }

    /**
     * Only the first outstanding step is live; the rest are inert until it clears. Each depends on
     * the one before, so a control that cannot succeed yet is worse than no control.
     *
     * A finished step then stays shut. Only sign-in reopens, and only because the wrong Gmail is
     * the one failure the phone cannot detect for itself. Everything else is re-read when this
     * screen comes forward, so a revoked grant or a switch turned off in Settings drops its step
     * back to outstanding and opens it without being asked.
     */
    val granted =
        listOf(
            Session.signedIn,
            Session.isGroupMember,
            Session.driveConnected,
            Session.usageAccessGranted,
            Session.remindersSettled,
        )

    /**
     * Finished, and finished in order.
     *
     * A step reads as done only when everything above it is. The five are asked in sequence but
     * the underlying grants are independent, so a Drive connected on a previous install shows up
     * the moment this screen opens, and step three sat there ticked while step two was still
     * outstanding.
     *
     * Which is true and useless. The tick is the one thing on this screen nobody reads twice, and
     * a tick further down than you have got says you are further along than you are. The grant is
     * not lost: the moment step two clears, step three is already green without anybody pressing
     * anything.
     */
    val done = granted.runningFold(true) { above, step -> above && step }.drop(1)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val outstanding = done.indexOfFirst { !it }
        var reopened by rememberSaveable { mutableStateOf<Int?>(null) }

        /**
         * Which step is showing its instructions.
         *
         * The first outstanding one by default, which is almost always the one somebody came for.
         * [reopened] overrides it, and now any step can set it: tap a finished one to see how it
         * was done, tap one further down to read what is coming.
         *
         * Still one at a time. Five steps expanded at once is a page of instructions with no
         * indication of where you are in it, which is the screen this replaced.
         */
        fun live(index: Int) = if (reopened != null) reopened == index else index == outstanding

        /**
         * Tapping the open one closes it, which hands the choice back to the app.
         *
         * Nothing below the outstanding step opens at all. Reading ahead sounds harmless and is
         * not: every step's instructions assume the ones above it are done, and its control
         * cannot succeed yet. Somebody who opens step four before joining the group gets a button
         * that fails and an explanation of a thing they cannot do.
         */
        fun toggle(index: Int) {
            if (index > outstanding) return
            reopened = if (live(index)) null else index
        }

        /** Whether the row responds to a tap at all, which is the same question. */
        fun opens(index: Int) = index <= outstanding

        Column(Modifier.padding(start = Gutter, end = Gutter, top = 36.dp, bottom = 24.dp)) {
            // The same control the home screen carries, in the same corner, for the same reason.
            // Setup is where somebody discovers they are on the wrong Gmail, and until now the way
            // out was a text link buried inside step one, which is closed unless you open it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Set up TestTrack",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = { confirmSignOut = true },
                    modifier = Modifier.offset(x = 12.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, "Sign out")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Five things to do once. Take them in order.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Five steps with no sense of how many remain is how somebody abandons this halfway.
            // Counted rather than given as a percentage: five is a number people can hold.
            Spacer(Modifier.height(20.dp))
            Meter(done = done.count { it }, total = done.size)
            Spacer(Modifier.height(8.dp))
            Text(
                "${done.count { it }} of ${done.size} done",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // The only step that reopens. The rest repair themselves: a revoked Drive grant or a
            // usage switch turned off in Settings is re-read when this screen comes forward, and
            // the step goes back to outstanding and opens on its own. Signing in is the one thing
            // the phone cannot notice going wrong, because the wrong Gmail is not a fault, and
            // every step below it fails without saying so.
            Step(
                number = 1,
                title = "Sign in",
                result = Session.email ?: "",
                done = done[0],
                live = live(0),
                onToggle = { toggle(0) }.takeIf { opens(0) },
            ) {
                Text(
                    "You're signed in as ${Session.email ?: "this account"}. If that's the wrong " +
                        "Gmail, sign out and pick another one. Your place in the group and your " +
                        "Drive both belong to the account, so the steps below will start again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Step(
                number = 2,
                title = "Join the testers group",
                result = "You're in the group",
                detail =
                    "App owners can only add you to their closed test if you're in this " +
                        "group. Open it, join with the same Gmail you signed in with, then come back " +
                        "and verify.",
                done = done[1],
                live = live(1),
                onToggle = { toggle(1) }.takeIf { opens(1) },
                answers =
                    listOf(
                        // First, and open by default, because it happens before anybody gets as far
                        // as verifying. The page refuses in large friendly letters and the way in is
                        // a button above the refusal, so people read the refusal and stop.
                        QA(
                            "The group page says I don't have permission",
                            "If you see a Join group button at the top, tap it. When it " +
                                "disappears, you are in.",
                            lead = "Ignore that message. Joining is the only thing to do on that page.",
                            art = { GroupPageShots() },
                        ),
                        QA(
                            "I joined, but Verify says I'm not in it",
                            "Google takes a minute or two to publish a new member, and the check " +
                                "reads Google rather than this phone. Wait a moment and press Verify " +
                                "again. If it still refuses, the usual cause is joining with a " +
                                "different Gmail than the one on step one.",
                        ),
                        QA(
                            "What does joining let you do?",
                            "Nothing on its own. It is a list Google keeps, and being on it is what " +
                                "makes you eligible to be added to a closed test. It gives nobody " +
                                "access to your account.",
                        ),
                        QA(
                            "Can I leave later?",
                            "Yes, from Google Groups. Leaving mid run means the apps you were " +
                                "testing drop out of your list and your group is a person short, so " +
                                "finish the fortnight first if you can.",
                        ),
                    ),
            ) {
                // Open, then verify, which is the order they happen in. Verify was the filled
                // button and Open a text link under it, so the loud control was the one that
                // cannot succeed until the quiet one has been used. Somebody arriving at this
                // step has not joined yet; that is what the step is for.
                Primary("Open the group", enabled = !vm.checkingGroup) {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.GROUP_URL)))
                }
                Spacer(Modifier.height(8.dp))
                Secondary(
                    when {
                        vm.checkingGroup -> "Checking\u2026"
                        vm.needsGoogle -> "Confirm with Google"
                        else -> "I've joined, check now"
                    },
                    Modifier.fillMaxWidth(),
                    enabled = !vm.checkingGroup,
                ) {
                    if (vm.needsGoogle) vm.signInAgain(activity) else vm.verifyGroup(activity)
                }

                StepMessage(vm, SetupViewModel.STEP_GROUP)
            }

            Step(
                number = 3,
                title = "Connect Google Drive",
                result = "Proof will be saved to your Drive",
                detail =
                    "Your daily screenshots go to your own Google Drive, so the proof stays " +
                        "yours. We can only see the files we put there and nothing else in it.",
                done = done[2],
                live = live(2),
                onToggle = { toggle(2) }.takeIf { opens(2) },
                answers =
                    listOf(
                        // First, and open by default, because it is the one people arrive with. The
                        // grant is the account's, and a phone carrying two Google accounts is the
                        // ordinary case rather than the odd one.
                        QA(
                            "It says connect, but I already did",
                            "The grant belongs to a Google account, not to the phone. If more than " +
                                "one account is signed in here, check the address on step one is the " +
                                "one you connected. Connecting again on the right account costs " +
                                "nothing.",
                        ),
                        QA(
                            "Can you read the rest of my Drive?",
                            "No. The permission asked for is the one that limits an app to the files " +
                                "it created itself. Everything else in your Drive is invisible to " +
                                "TestTrack, including files you put in the same folder by hand.",
                        ),
                        QA(
                            "What exactly gets uploaded?",
                            "One screenshot per app per day, taken while that app is open, plus the " +
                                "time you spent in it. Nothing from any other app and nothing while " +
                                "TestTrack is closed.",
                        ),
                        QA(
                            "How much space will it use?",
                            "A fortnight of testing thirteen apps is about a hundred and eighty " +
                                "screenshots, which is somewhere near fifty megabytes. It is yours " +
                                "to delete whenever the run is over.",
                        ),
                    ),
            ) {
                Primary("Connect Drive", busy = vm.connectingDrive) {
                    vm.connectDrive(activity) { consentLauncher.launch(it) }
                }

                StepMessage(vm, SetupViewModel.STEP_DRIVE)
            }

            Step(
                number = 4,
                title = "Allow usage access",
                result = "Time in each app is recorded",
                detail =
                    "Your daily report shows how long you actually spent in each app, not " +
                        "just that you opened it. Android only shares that if you switch on usage " +
                        "access, and there's no pop-up to ask for it, so you'll need to find " +
                        "TestTrack in the list and turn it on yourself.",
                done = done[3],
                live = live(3),
                onToggle = { toggle(3) }.takeIf { opens(3) },
                answers =
                    listOf(
                        // The most common way this step fails, and the one a tester cannot solve by
                        // trying harder: the switch is there, they tap it, and Android refuses
                        // without explaining that the cause is how the app was installed rather than
                        // anything they did. It sits first and opens by default, because the menu
                        // that unblocks it stays hidden until the refusal has been seen once, so
                        // nobody finds it by looking.
                        QA(
                            "The switch won't turn on",
                            "Tap the switch once, close the message, then open Settings, Apps, " +
                                "TestTrack and use the three dot menu. Confirm with your PIN, come " +
                                "back, and the switch will work.",
                            lead =
                                "Nothing is wrong with your phone. Some phones block this for " +
                                    "apps installed from a browser instead of the Play Store.",
                            art = { RestrictedSettingShots() },
                        ),
                        QA(
                            "Can you see every app I use?",
                            "The permission is broad, but TestTrack only ever asks Android about the " +
                                "apps on your testing list. Nothing else is read, and nothing about " +
                                "your phone leaves it except the seconds you spent in those apps.",
                        ),
                        QA(
                            "Will it drain my battery?",
                            "No. Nothing runs in the background for this. The figure is read once, " +
                                "from a total Android already keeps, at the moment a visit ends.",
                        ),
                        QA(
                            "Can I skip it?",
                            "Not this one. Without it a day can only prove the app opened, and " +
                                "opening is not testing. It is the step that makes your report worth " +
                                "anything to the developer.",
                        ),
                    ),
            ) {
                Primary("Open usage access settings") { UsageRepo.openSettings(activity) }
            }

            Step(
                number = 5,
                title = "Turn on reminders",
                result =
                    if (Session.notificationsGranted) {
                        "You'll be nudged if a day is slipping"
                    } else {
                        "Off, so you'll need to remember on your own"
                    },
                detail =
                    "A run is fourteen days with no gaps, and one missed day resets the " +
                        "clock for everyone in your group, not just for you. We'll only nudge you " +
                        "when apps are still waiting, never to say well done.",
                done = done[4],
                live = live(4),
                last = true,
                onToggle = { toggle(4) }.takeIf { opens(4) },
                // Only while they are actually off.
                //
                // This step counts as done when it has been settled, and declining settles it, so
                // a tick here means "you have decided" rather than "they are on". Somebody who
                // said Not now and came back needs the button; somebody whose reminders are
                // already arriving was being shown a tick and an offer to turn on the thing that
                // is on, which reads as the screen not knowing its own state.
                actionOutlivesTheStep = !Session.notificationsGranted,
                answers =
                    listOf(
                        QA(
                            "How many will I get?",
                            "At most one a day, in the evening, and only if something is still " +
                                "outstanding. Finish early and you hear nothing at all.",
                        ),
                        QA(
                            "Can I say no and still use TestTrack?",
                            "Yes. This is the one step you can decline. Not now marks it settled and " +
                                "you keep track of the days yourself.",
                        ),
                        QA(
                            "I said no and changed my mind",
                            "Come back to this screen and the step is here. After the first refusal " +
                                "Android will not ask again, so it opens your notification settings " +
                                "instead.",
                        ),
                    ),
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !promptSpent) {
                    Primary("Turn on reminders") {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    // Either the prompt is spent, or this is Android 12 and below where there was
                    // never a prompt to spend — both lead to the same place.
                    Primary("Open notification settings") {
                        activity.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName),
                        )
                        Session.updateRemindersAsked()
                    }
                }
                TextButton(
                    onClick = { Session.updateRemindersAsked() },
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) { Text("Not now") }
            }

            // Only what no step claimed. Everything else is shown beside its own button.
            if (vm.messageStep == SetupViewModel.STEP_NONE) vm.message?.let { Failure(it) }
            Spacer(Modifier.height(Gutter))
        }

        Primary(
            "Start testing",
            Modifier.padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 24.dp),
            enabled = Session.setupComplete,
            tall = true,
            onClick = onFinished,
        )
    }

    if (confirmSignOut) {
        Ask(
            title = "Sign out?",
            body =
                "You'll come back to the account picker and can sign in with a different " +
                    "Gmail. Nothing you've already reported is lost.",
            confirm = "Sign out",
            onConfirm = {
                confirmSignOut = false
                // Setup is reachable from home, where a round may still be running. A no-op
                // when there is nothing to end.
                CaptureService.endSession(activity)
                vm.signOut(activity) {
                    Cache.clear()
                    onSignOut()
                }
            },
            onDismiss = { confirmSignOut = false },
        )
    }
}
