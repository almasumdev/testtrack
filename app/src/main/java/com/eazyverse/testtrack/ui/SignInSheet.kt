package com.eazyverse.testtrack.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserPlus
import com.composables.icons.lucide.UserX
import com.composables.icons.lucide.WifiOff
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AuthViewModel
import com.eazyverse.testtrack.viewmodel.SignInUi
import kotlinx.coroutines.delay

/**
 * Why the sheet came up, in the words of whatever brought it.
 *
 * Carried in rather than written on the sheet, because the honest answer differs: from the header
 * nothing was refused and somebody is simply starting, and from a tab a screen has just declined
 * to show them their own things. A sheet that says the same sentence either way is answering a
 * question it was not asked.
 */
data class SignInReason(
    val title: String,
    val why: String,
)

/** From the header, where nobody was turned away and nothing is being withheld. */
val StartReason =
    SignInReason(
        title = "Sign in to TestTrack",
        why =
            "Your crews, apps and tix are saved to your account, so they are there on any " +
                "phone you sign in on.",
    )

/**
 * Signing in, over whatever asked for it.
 *
 * There used to be a screen for this, and it was the first thing anybody saw: a logo, a three
 * point pitch and a button, shown before they had been shown a single thing the app does. It is a
 * sheet now, over the content that prompted it, from the header or from a tab that cannot answer
 * without a name. Nobody is sent anywhere, and dismissing puts them back exactly where they were.
 *
 * Drawn in the one [PickerSheet] frame. The sheet answers in its own title: what happened is the
 * first thing read after Google closes, so the title and its leading mark or tile follow the state
 * rather than staying on the reason. It ends one of two ways and never both: [onDismiss] when
 * somebody puts it away, [onSignedIn] when it closes itself after showing the account.
 *
 * While Google is being waited on it cannot be put away at all. Closing it then would leave a
 * sign-in running behind nothing, and the answer arriving into a sheet that is gone.
 */
@Composable
fun SignInSheet(
    reason: SignInReason,
    onDismiss: () -> Unit,
    onSignedIn: () -> Unit,
    vm: AuthViewModel = viewModel(),
) {
    DisposableEffect(vm) {
        onDispose { vm.resetResult() }
    }
    val context = LocalContext.current
    val activity = context.findActivity()
    // Set just before the sheet closes itself, so the frame's close ends in onSignedIn rather than
    // onDismiss, which would forget whatever was waiting on the sign in.
    var done by remember { mutableStateOf(false) }
    val ui = vm.ui

    PickerSheet(
        title = signInTitle(reason, ui),
        onDismiss = { if (done) onSignedIn() else onDismiss() },
        leading = { SignInLead(ui) },
        containerColor = Tt.colors.sheet,
        topLine = Tt.colors.sheetLine,
        handleGap = 16.dp,
        headerGap = 12.dp,
        titleLiveRegion = true,
        dismissible = ui !is SignInUi.Working,
    ) { close ->
        // Long enough to read which of three accounts was picked, short enough that nobody is
        // waiting on it. The sheet leaves on its own; there is nothing here to press once it has
        // worked. Hidden first, then removed, which is what close does: a sheet taken out while
        // still open vanishes on one frame.
        // Not when there is something to read first: then it waits for OK.
        LaunchedEffect(ui) {
            if (ui is SignInUi.SignedIn && !ui.starterUsed) {
                delay(1_100)
                done = true
                close {}
            }
        }
        SignInSheetBody(
            reason = reason,
            ui = ui,
            onPress = { vm.signIn(activity) },
            onAddAccount = { addGoogleAccount(context) },
            onClose = { close {} },
            onOk = {
                done = true
                close {}
            },
        )
    }
}

/**
 * Opens the phone's own add account screen, for Google accounts only.
 *
 * Nothing starts signing in when the person comes back: they tap Try again, which is the one thing
 * on the sheet that says so.
 */
private fun addGoogleAccount(context: Context) {
    val intent =
        Intent(Settings.ACTION_ADD_ACCOUNT)
            .putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // A phone with no add account screen has nothing to offer here.
    }
}

/** The sheet's title for a state. Only the first one is the caller's own words. */
internal fun signInTitle(
    reason: SignInReason,
    ui: SignInUi,
): String =
    when (ui) {
        SignInUi.Idle -> reason.title
        SignInUi.Working -> "Signing you in"
        SignInUi.Cancelled -> "Sign-in was cancelled"
        SignInUi.Offline -> "No internet connection"
        SignInUi.NoAccount -> "No Google account found"
        is SignInUi.Failed -> "Sign-in didn't work"
        is SignInUi.SignedIn -> "You're signed in"
    }

/**
 * What sits before the title. The TestTrack mark while nothing is wrong, a toned tile when
 * something is, so a tile only ever appears for a reason.
 */
@Composable
internal fun SignInLead(ui: SignInUi) {
    when (ui) {
        SignInUi.Idle, SignInUi.Working, SignInUi.Cancelled -> {
            // As tall as the title's line, and scales with the font size.
            val markSize = with(LocalDensity.current) { PickerTitleLineHeight.toDp() }
            Image(painterResource(R.drawable.testtrack_mark), null, Modifier.size(markSize))
        }

        SignInUi.Offline -> IconTile(Lucide.WifiOff, tone = Tone.WARN)

        SignInUi.NoAccount -> IconTile(Lucide.UserX, tone = Tone.WARN)

        is SignInUi.Failed -> IconTile(Lucide.CircleAlert, danger = true)

        is SignInUi.SignedIn -> IconTile(Lucide.Check, tone = Tone.OK)
    }
}

/**
 * The sheet's contents, given its state rather than holding it. The title is the frame's, from
 * [signInTitle].
 *
 * The same shape the rest of the screens here take, and for the same reason: everything this draws
 * can be put in front of a test this way, including the half that only exists after a real Google
 * account has said yes, which is the half no emulator can reach.
 */
@Composable
fun SignInSheetBody(
    reason: SignInReason,
    ui: SignInUi = SignInUi.Idle,
    onPress: () -> Unit = {},
    onAddAccount: () -> Unit = {},
    onClose: () -> Unit = {},
    onOk: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
    ) {
        /*
         * Crossed over rather than swapped.
         *
         * The states of this sheet are different heights and different shapes, and it was cutting
         * from one to the next on the frame the answer arrived: the form vanished, the panel
         * appeared, and the sheet jumped to its new height under both of them. The one moment in
         * this flow worth feeling was the only one with nothing in it.
         *
         * The outgoing goes first and faster, so the two are never both solid on top of each
         * other, and the incoming lifts a little as it arrives. SizeTransform is what carries
         * the sheet between heights instead of snapping.
         *
         * Keyed on the kind of state, not the state: two failures with different sentences are
         * the same panel and should not play the transition over again.
         */
        AnimatedContent(
            targetState = ui,
            contentKey = { it::class },
            transitionSpec = {
                (
                    fadeIn(tween(durationMillis = 240, delayMillis = 70)) +
                        slideInVertically(
                            tween(durationMillis = 300, delayMillis = 110),
                        ) { it / 5 }
                ).togetherWith(fadeOut(tween(durationMillis = 150)))
                    .using(SizeTransform(clip = false))
            },
            label = "signin",
        ) { state ->
            // Its own Column: AnimatedContent lays its content out in a Box, so siblings emitted
            // directly would be drawn over one another rather than under.
            Column {
                when (state) {
                    SignInUi.Idle -> {
                        Why(reason.why)
                        Actions { GooglePill(onPress) }
                        QuietNote("Google Play links testers by their Google account.", Modifier.padding(top = 12.dp))
                    }

                    SignInUi.Working -> {
                        Why("This takes a few seconds.")
                        Actions { AccountSkeleton() }
                    }

                    SignInUi.Cancelled -> {
                        Why("You can try again or keep looking around.")
                        Actions { GooglePill(onPress) }
                        InlineAction("Keep looking around", Modifier.padding(top = 6.dp), fullWidth = true, onClick = onClose)
                    }

                    SignInUi.Offline -> {
                        Why("Sign-in needs the internet. Check your Wi-Fi or mobile data, then try again.")
                        Actions { GooglePill(onPress) }
                    }

                    SignInUi.NoAccount -> {
                        Why("Add a Google account to this phone, then come back and try again.")
                        Actions {
                            Primary(
                                "Add a Google account",
                                mark = rememberVectorPainter(Lucide.UserPlus),
                                onClick = onAddAccount,
                            )
                        }
                        InlineAction("Try again", Modifier.padding(top = 6.dp), fullWidth = true, onClick = onPress)
                    }

                    is SignInUi.Failed -> {
                        Why(state.body ?: DEFAULT_FAILURE)
                        Actions { GooglePill(onPress) }
                    }

                    is SignInUi.SignedIn -> SignedIn(state, onOk)
                }
            }
        }
    }
}

/**
 * Google's own mark, from Google's own outline.
 *
 * It was four arcs and a crossbar drawn here by hand, which was close enough to recognise and not
 * close enough to be right: the proportions were ours rather than theirs. A drawable is also the
 * one place it can be fixed for every button that uses it.
 *
 * Untinted on purpose. The four colours are the mark; painting it in one of ours would make it a
 * different logo.
 */
@Composable
fun GoogleMark(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_google),
        contentDescription = null,
        modifier = modifier,
    )
}
