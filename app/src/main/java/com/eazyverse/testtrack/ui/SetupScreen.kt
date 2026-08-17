package com.eazyverse.testtrack.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.data.*
import com.eazyverse.testtrack.findActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

class SetupViewModel : ViewModel() {
    var checkingGroup by mutableStateOf(false)
        private set
    var connectingDrive by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    /**
     * Which step [message] is an answer to, so it can be shown beside the button that asked.
     *
     * [STEP_NONE] covers anything raised without a step to blame, which still has to appear
     * somewhere rather than be swallowed.
     */
    var messageStep by mutableStateOf(STEP_NONE)

    fun say(step: Int, text: String?) {
        message = text
        messageStep = step
    }

    companion object {
        const val STEP_NONE = -1
        const val STEP_GROUP = 1
        const val STEP_DRIVE = 2
    }

    /**
     * Abandons the account and hands back to navigate.
     *
     * Setup is where a tester lands on the wrong Gmail — the checklist is keyed to the account,
     * and every step below the first fails for an address that is not in the group. Without this
     * the only way back to the picker was to uninstall.
     */
    fun signOut(context: Context, then: () -> Unit) {
        viewModelScope.launch {
            releaseSession(context)
            then()
        }
    }

    /** Re-runs the server-side membership check for the account we already hold a token for. */
    fun verifyGroup(activity: Activity) {
        checkingGroup = true
        say(STEP_GROUP, null)
        viewModelScope.launch {
            try {
                val (_, gate) = AuthRepo.recheckGroup(activity)
                say(STEP_GROUP, when {
                    // Checked, but not about us. The old code adopted whatever address came back,
                    // which left the session showing one Gmail while every write still belonged to
                    // the other, and stamped that account's membership onto this one. Say so and
                    // change nothing; switching accounts is what Sign out is for.
                    gate.isAboutSomeoneElse(Session.email) ->
                        "Google answered for ${gate.address()}, not ${Session.email}. Sign out at " +
                            "the top of this page and sign back in with the Gmail you want to use."

                    gate is GateResult.Failed -> gate.reason

                    else -> {
                        Session.updateMember(gate is GateResult.Member)
                        (gate as? GateResult.NotMember)?.let { "${it.email} isn't in the group yet." }
                    }
                })
            } catch (e: CancellationException) {
                throw e   // leaving the screen cancels this; it is not a failed check
            } catch (e: Exception) {
                say(STEP_GROUP, e.friendly("We couldn't check your group membership just now. Try again in a moment."))
            }
            checkingGroup = false
        }
    }

    fun connectDrive(activity: Activity, launchConsent: (IntentSenderRequest) -> Unit) {
        connectingDrive = true
        say(STEP_DRIVE, null)
        viewModelScope.launch {
            try {
                // Bounded, because the task underneath this never fails on its own when Play
                // services cannot service the call. It retries quietly and the button spins for
                // as long as somebody is willing to watch it.
                val result = withTimeoutOrNull(AuthRepo.AUTH_TIMEOUT_MS) {
                    AuthRepo.authorizeDrive(activity)
                }
                val pending = result?.pendingIntent
                when {
                    result == null ->
                        say(STEP_DRIVE, "Google Play services didn't answer. Try again.")

                    result.hasResolution() && pending != null ->
                        launchConsent(IntentSenderRequest.Builder(pending.intentSender).build())

                    else -> Session.updateDriveConnected(result.accessToken != null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(STEP_DRIVE, e.friendly("Google didn't complete the Drive connection. Try it again."))
            }
            connectingDrive = false
        }
    }
}

/** The answer to one step's button, shown under that button rather than at the foot of the page. */
@Composable
private fun StepMessage(vm: SetupViewModel, step: Int) {
    if (vm.messageStep != step) return
    vm.message?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun SetupScreen(
    onFinished: () -> Unit,
    onSignOut: () -> Unit,
    vm: SetupViewModel = viewModel()
) {
    val activity = LocalContext.current.findActivity()
    var confirmSignOut by remember { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
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
        val observer = LifecycleEventObserver { _, event ->
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
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
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
    val granted = listOf(
        Session.signedIn,
        Session.isGroupMember,
        Session.driveConnected,
        Session.usageAccessGranted,
        Session.remindersSettled
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

        /** Tapping the open one closes it, which hands the choice back to the app. */
        fun toggle(index: Int) { reopened = if (live(index)) null else index }

        Column(Modifier.padding(start = Gutter, end = Gutter, top = 36.dp, bottom = 24.dp)) {
            Text(
                "Set up TestTrack",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Five things to do once. Take them in order.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Five steps with no sense of how many remain is how somebody abandons this halfway.
            // Counted rather than given as a percentage: five is a number people can hold.
            Spacer(Modifier.height(20.dp))
            Meter(done = done.count { it }, total = done.size)
            Spacer(Modifier.height(8.dp))
            Text(
                "${done.count { it }} of ${done.size} done",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                onToggle = { toggle(0) },
                actionOutlivesTheStep = true,
            ) {
                Text(
                    "You're signed in as ${Session.email ?: "this account"}. If that's the wrong " +
                        "Gmail, sign out and pick another one. Your place in the group and your " +
                        "Drive both belong to the account, so the steps below will start again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                TextButton(
                    onClick = { confirmSignOut = true },
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
            }

            Step(
                number = 2,
                title = "Join the testers group",
                result = "You're in the group",
                detail = "App owners can only add you to their closed test if you're in this " +
                    "group. Open it, join with the same Gmail you signed in with, then come back " +
                    "and verify.",
                done = done[1],
                live = live(1),
                onToggle = { toggle(1) },
                answers = listOf(
                    // First, and open by default, because it happens before anybody gets as far
                    // as verifying. The page refuses in large friendly letters and the way in is
                    // a button above the refusal, so people read the refusal and stop.
                    QA(
                        "The group page says I don't have permission",
                        "If you see a Join group button at the top, tap it. When it " +
                            "disappears, you are in.\n\n" +
                            "If there is no button at all, the browser is on a different Google " +
                            "account. Switch it to the Gmail on step one and open the link again.",
                        lead = "Ignore that message. Joining is the only thing to do on that page.",
                        art = { GroupPageShots() }
                    ),
                    QA(
                        "I joined, but Verify says I'm not in it",
                        "Google takes a minute or two to publish a new member, and the check " +
                            "reads Google rather than this phone. Wait a moment and press Verify " +
                            "again. If it still refuses, the usual cause is joining with a " +
                            "different Gmail than the one on step one."
                    ),
                    QA(
                        "What does joining let you do?",
                        "Nothing on its own. It is a list Google keeps, and being on it is what " +
                            "makes you eligible to be added to a closed test. It gives nobody " +
                            "access to your account."
                    ),
                    QA(
                        "Can I leave later?",
                        "Yes, from Google Groups. Leaving mid run means the apps you were " +
                            "testing drop out of your list and your group is a person short, so " +
                            "finish the fortnight first if you can."
                    )
                )
            ) {
                // Open, then verify, which is the order they happen in. Verify was the filled
                // button and Open a text link under it, so the loud control was the one that
                // cannot succeed until the quiet one has been used. Somebody arriving at this
                // step has not joined yet; that is what the step is for.
                Primary("Open the group") {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.GROUP_URL)))
                }
                Spacer(Modifier.height(8.dp))
                Secondary(
                    if (vm.checkingGroup) "Checking\u2026" else "I've joined, check now",
                    Modifier.fillMaxWidth()
                ) {
                    if (!vm.checkingGroup) vm.verifyGroup(activity)
                }

                StepMessage(vm, SetupViewModel.STEP_GROUP)
            }

            Step(
                number = 3,
                title = "Connect Google Drive",
                result = "Proof will be saved to your Drive",
                detail = "Your daily screenshots go to your own Google Drive, so the proof stays " +
                    "yours. We can only see the files we put there and nothing else in it.",
                done = done[2],
                live = live(2),
                onToggle = { toggle(2) },
                answers = listOf(
                    // First, and open by default, because it is the one people arrive with. The
                    // grant is the account's, and a phone carrying two Google accounts is the
                    // ordinary case rather than the odd one.
                    QA(
                        "It says connect, but I already did",
                        "The grant belongs to a Google account, not to the phone. If more than " +
                            "one account is signed in here, check the address on step one is the " +
                            "one you connected. Connecting again on the right account costs " +
                            "nothing."
                    ),
                    QA(
                        "Can you read the rest of my Drive?",
                        "No. The permission asked for is the one that limits an app to the files " +
                            "it created itself. Everything else in your Drive is invisible to " +
                            "TestTrack, including files you put in the same folder by hand."
                    ),
                    QA(
                        "What exactly gets uploaded?",
                        "One screenshot per app per day, taken while that app is open, plus the " +
                            "time you spent in it. Nothing from any other app and nothing while " +
                            "TestTrack is closed."
                    ),
                    QA(
                        "How much space will it use?",
                        "A fortnight of testing thirteen apps is about a hundred and eighty " +
                            "screenshots, which is somewhere near fifty megabytes. It is yours " +
                            "to delete whenever the run is over."
                    )
                )
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
                detail = "Your daily report shows how long you actually spent in each app, not " +
                    "just that you opened it. Android only shares that if you switch on usage " +
                    "access, and there's no pop-up to ask for it, so you'll need to find " +
                    "TestTrack in the list and turn it on yourself.",
                done = done[3],
                live = live(3),
                onToggle = { toggle(3) },
                answers = listOf(
                    // The most common way this step fails, and the one a tester cannot solve by
                    // trying harder: the switch is there, they tap it, and Android refuses
                    // without explaining that the cause is how the app was installed rather than
                    // anything they did. It sits first and opens by default, because the menu
                    // that unblocks it stays hidden until the refusal has been seen once, so
                    // nobody finds it by looking.
                    QA(
                        "The switch won't turn on",
                        "Some phones, Samsung especially, block this for apps installed from a " +
                            "browser instead of the Play Store. You'll see a message saying the " +
                            "setting is restricted. Nothing is wrong with your phone or with " +
                            "TestTrack, and you can turn it on yourself:\n\n" +
                            "1. Tap the switch once and close the message that appears\n" +
                            "2. Open Settings, then Apps, then TestTrack\n" +
                            "3. Tap the three dot menu in the top right corner\n" +
                            "4. Choose Allow restricted settings\n" +
                            "5. Confirm with your PIN, pattern or fingerprint\n" +
                            "6. Come back here and the switch will work\n\n" +
                            "The menu in step 3 only appears after you've tapped the switch " +
                            "once, so don't skip the first step."
                    ),
                    QA(
                        "Can you see every app I use?",
                        "The permission is broad, but TestTrack only ever asks Android about the " +
                            "apps on your testing list. Nothing else is read, and nothing about " +
                            "your phone leaves it except the seconds you spent in those apps."
                    ),
                    QA(
                        "Will it drain my battery?",
                        "No. Nothing runs in the background for this. The figure is read once, " +
                            "from a total Android already keeps, at the moment a visit ends."
                    ),
                    QA(
                        "Can I skip it?",
                        "Not this one. Without it a day can only prove the app opened, and " +
                            "opening is not testing. It is the step that makes your report worth " +
                            "anything to the developer."
                    )
                )
            ) {
                Primary("Open usage access settings") { UsageRepo.openSettings(activity) }
            }

            Step(
                number = 5,
                title = "Turn on reminders",
                result = if (Session.notificationsGranted) "You'll be nudged if a day is slipping"
                         else "Off, so you'll need to remember on your own",
                detail = "A run is fourteen days with no gaps, and one missed day resets the " +
                    "clock for everyone in your group, not just for you. We'll only nudge you " +
                    "when apps are still waiting, never to say well done.",
                done = done[4],
                live = live(4),
                last = true,
                onToggle = { toggle(4) },
                // Only while they are actually off.
                //
                // This step counts as done when it has been settled, and declining settles it, so
                // a tick here means "you have decided" rather than "they are on". Somebody who
                // said Not now and came back needs the button; somebody whose reminders are
                // already arriving was being shown a tick and an offer to turn on the thing that
                // is on, which reads as the screen not knowing its own state.
                actionOutlivesTheStep = !Session.notificationsGranted,
                answers = listOf(
                    QA(
                        "How many will I get?",
                        "At most one a day, in the evening, and only if something is still " +
                            "outstanding. Finish early and you hear nothing at all."
                    ),
                    QA(
                        "Can I say no and still use TestTrack?",
                        "Yes. This is the one step you can decline. Not now marks it settled and " +
                            "you keep track of the days yourself."
                    ),
                    QA(
                        "I said no and changed my mind",
                        "Come back to this screen and the step is here. After the first refusal " +
                            "Android will not ask again, so it opens your notification settings " +
                            "instead."
                    )
                )
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
                                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                        )
                        Session.updateRemindersAsked()
                    }
                }
                TextButton(
                    onClick = { Session.updateRemindersAsked() },
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) { Text("Not now") }
            }

            // Only what no step claimed. Everything else is shown beside its own button.
            if (vm.messageStep == SetupViewModel.STEP_NONE) vm.message?.let { Failure(it) }
            Spacer(Modifier.height(24.dp))
        }

        Primary(
            "Start testing",
            Modifier.padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 24.dp),
            enabled = Session.setupComplete,
            tall = true,
            onClick = onFinished
        )
    }

    if (confirmSignOut) {
        Ask(
            title = "Sign out?",
            body = "You'll come back to the account picker and can sign in with a different " +
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
            onDismiss = { confirmSignOut = false }
        )
    }
}

/** One question a step raises, and the answer to it. */
/**
 * One question a step raises, the answer to it, and sometimes a picture.
 *
 * [art] is for the one case where words are the wrong tool: a page whose layout is the problem.
 * Drawn rather than photographed, and deliberately not a lookalike. A screenshot of somebody
 * else's page goes stale the week they restyle it, and a convincing copy of a Google page is a
 * thing nobody should be shipping. A diagram in this app's own colours can only be read as a
 * diagram, which is what it is.
 */
data class QA(
    val q: String,
    val a: String,
    val art: (@Composable () -> Unit)? = null,
    /**
     * The one sentence that would do on its own, said louder than the rest.
     *
     * For the answers where somebody is already lost and reading fast. Everything under it is
     * still worth having, and none of it is worth having first.
     */
    val lead: String? = null
)

/**
 * One node on the rail.
 *
 * A finished step keeps only what it produced: the address, the outcome. The live step is the only
 * one showing instructions, controls or answers. Everything ahead is the title alone, dimmed: it
 * says what is coming without offering anything to press.
 *
 * The dot carries the state so the screen can be read without reading it. Done is a tick on the
 * same green a kept day is drawn in everywhere else in the app, live is the number on the iris the
 * buttons use, ahead is the number outlined and faint. The connector between two finished steps is
 * green as well, which is how far you got, said without a word.
 *
 * [IntrinsicSize.Min] is what lets the connector run the height of whatever the body turns out to
 * be, which for step four with its walkthrough open is most of a screen.
 */
@Composable
private fun Step(
    number: Int,
    title: String,
    result: String,
    done: Boolean,
    live: Boolean,
    onToggle: () -> Unit,
    /**
     * Whether [action] still means anything once the step is finished.
     *
     * False for the ones that perform the step. Opening a finished step to read how it was done
     * and being handed Connect Drive is the screen asking for something it already has, and the
     * button is worse than useless: pressing it sends somebody back through a Google consent
     * sheet to arrive exactly where they started.
     *
     * True for the two where the control is not "do this step" but "change what it produced".
     * Signing out is the only fix for the wrong Gmail. Reminders count as settled when somebody
     * declines them, so a finished reminders step is often a switched-off one, and hiding the way
     * to turn it on would strand anybody who changed their mind.
     *
     * Nothing is lost by hiding the rest. Every one of them is re-read when this screen comes
     * forward, so a Drive grant revoked or a usage switch turned off drops its step back to
     * outstanding on its own, action and all.
     */
    actionOutlivesTheStep: Boolean = false,
    detail: String? = null,
    last: Boolean = false,
    answers: List<QA> = emptyList(),
    action: @Composable (ColumnScope.() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Always, and in both directions. It used to be tappable only when it was closed
            // and only on the one step that could reopen, so a step you had opened out of
            // curiosity could not be put away again.
            .clickable(onClick = onToggle)
            .padding(horizontal = Gutter)
            .height(IntrinsicSize.Min)
    ) {
        Column(
            Modifier.width(24.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Node(number, done, live)
            if (!last) {
                Box(
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(vertical = 4.dp)
                        .background(
                            if (done) Status.posted else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f).padding(bottom = if (last) 8.dp else 22.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal,
                lineHeight = 24.sp,
                color = when {
                    live || done -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }
            )

            if (done && result.isNotBlank()) {
                Text(
                    result,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (live) {
                detail?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (action != null && (!done || actionOutlivesTheStep)) {
                    Spacer(Modifier.height(16.dp))
                    action()
                }
                Answers(answers)
            }
        }
    }
}

/** Done, doing, or waiting its turn. */
@Composable
private fun Node(number: Int, done: Boolean, live: Boolean) {
    val fill = when {
        done -> Status.posted
        live -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(fill)
            .then(
                if (done || live) Modifier
                else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (done) {
            // The page ground, which is dark behind the light green of the dark theme and light
            // behind the deep green of the light one. One value, legible on both.
            Icon(
                Icons.Default.Check, null,
                Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.background
            )
        } else {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (live) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

/**
 * What people ask when they get stuck on this step.
 *
 * The first one open, the rest closed, one at a time. These are not documentation moved closer:
 * without them a tester wondering what lands in their Drive has nowhere to look, so they guess,
 * and the ones who guess wrong stop setting up and never say why.
 *
 * Which question somebody opened is deliberately not remembered across a launch. It is a glance,
 * not a place in a document.
 */
@Composable
private fun Answers(items: List<QA>) {
    if (items.isEmpty()) return
    var open by remember { mutableStateOf(0) }

    Column(Modifier.padding(top = 18.dp)) {
        Dashes()
        items.forEachIndexed { index, item ->
            val shown = open == index
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = if (shown) -1 else index }
                    .padding(vertical = 11.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        item.q,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (shown) "\u2013" else "+",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (shown) {
                    item.lead?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.4f
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.a,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.5f
                    )
                    item.art?.let {
                        Spacer(Modifier.height(12.dp))
                        it()
                    }
                }
            }
            Dashes()
        }
    }
}

/**
 * The page itself, twice, with the difference between the two marked.
 *
 * Drawn from the real thing rather than approximated, because the whole problem is where a
 * particular button sits relative to a particular message, and a tidied-up illustration would put
 * them wherever it was convenient. The ring and the dashed box are the only things added.
 *
 * Above: the way in, which is small, at the top, and easy to miss under a refusal set three times
 * its size. Below: the same page on the wrong Google account, where the button is simply absent,
 * which is a different problem with a different fix and looks almost identical until you know
 * where to look.
 */
@Composable
private fun GroupPageShots() {
    Column(Modifier.fillMaxWidth()) {
        Shot(
            R.drawable.group_page_join,
            286.dp,
            "Tap the button. When it disappears, you have joined."
        )
        Spacer(Modifier.height(14.dp))
        Shot(
            R.drawable.group_page_no_join,
            270.dp,
            "No button here? Your browser is signed in with a different Gmail. Switch it to " +
                "the one on step one, then open the link again."
        )
    }
}

/**
 * [height] is stated rather than derived, which looks like the lazy way round and is not.
 *
 * The rail this sits inside is a Row measured at IntrinsicSize.Min, so the connector can run the
 * height of whatever the step turns out to be. Intrinsic measurement asks a child how tall it
 * wants to be without telling it how wide it will end up, and an image sized from its own aspect
 * ratio cannot answer that: it reports something far too short, gets laid out at that height, and
 * loses its top. Both screenshots came out missing the title row, which is where the button and
 * the ring are, so the picture lost the only thing it was there to show.
 *
 * A stated height answers the intrinsic query honestly. Fit then scales the picture inside it, so
 * a narrower screen makes it smaller rather than cutting a piece off.
 */
@Composable
private fun Shot(id: Int, height: Dp, caption: String) {
    Image(
        painter = painterResource(id),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
    )
    Spacer(Modifier.height(6.dp))
    Text(
        caption,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = MaterialTheme.typography.labelSmall.fontSize * 1.4f
    )
}

/** The console's dashed rule, which is what separates rows in a list over there. */
@Composable
private fun Dashes() {
    val ink = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(1.dp)) {
        drawLine(
            color = ink,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.5.dp.toPx(), 3.dp.toPx()))
        )
    }
}
