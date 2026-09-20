package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.viewmodel.AuthViewModel
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
            "Your crews, your apps and everything you earn belong to the account, and follow " +
                "you onto any phone you sign in on.",
    )

/**
 * Signing in, over whatever asked for it.
 *
 * There used to be a screen for this, and it was the first thing anybody saw: a logo, a three
 * point pitch and a button, shown before they had been shown a single thing the app does. It is a
 * sheet now, over the content that prompted it, from the header or from a tab that cannot answer
 * without a name. Nobody is sent anywhere, and dismissing puts them back exactly where they were.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInSheet(
    reason: SignInReason,
    onDismiss: () -> Unit,
    onSignedIn: () -> Unit,
    vm: AuthViewModel = viewModel(),
) {
    val activity = LocalContext.current.findActivity()
    val state = rememberModalBottomSheetState()

    // Long enough to read which of three accounts was picked, short enough that nobody is waiting
    // on it. The sheet leaves on its own; there is nothing here to press once it has worked.
    LaunchedEffect(vm.signedInAs) {
        if (vm.signedInAs != null) {
            delay(1_100)
            // Hidden first, then removed. A ModalBottomSheet taken out of the composition while
            // still open has no chance to run its own exit: it vanishes on one frame, which is
            // what made the end of this flow feel broken however well the halves crossed over.
            state.hide()
            onSignedIn()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        SignInSheetBody(
            reason = reason,
            busy = vm.busy,
            message = vm.message,
            signedInAs = vm.signedInAs,
            onPress = { vm.signIn(activity) },
        )
    }
}

/**
 * The sheet's contents, given its state rather than holding it.
 *
 * The same shape the rest of the screens here take, and for the same reason: everything this draws
 * can be put in front of a test this way, including the half that only exists after a real Google
 * account has said yes, which is the half no emulator can reach.
 */
@Composable
fun SignInSheetBody(
    reason: SignInReason,
    busy: Boolean = false,
    message: String? = null,
    signedInAs: String? = null,
    onPress: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = Gutter, end = Gutter, bottom = 30.dp),
    ) {
            /*
             * Crossed over rather than swapped.
             *
             * The two halves of this sheet are different heights and different shapes, and it was
             * cutting from one to the other on the frame the answer arrived: the form vanished,
             * the panel appeared, and the sheet jumped to its new height under both of them. The
             * one moment in this flow worth feeling was the only one with nothing in it.
             *
             * The outgoing goes first and faster, so the two are never both solid on top of each
             * other, and the incoming lifts a little as it arrives. SizeTransform is what carries
             * the sheet between the two heights instead of snapping.
             */
        AnimatedContent(
            targetState = signedInAs,
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
        ) { account ->
            if (account == null) {
                AskToSignIn(reason, busy, message, onPress)
            } else {
                SignedIn(account)
            }
        }
    }
}

/** The half that asks. */
@Composable
private fun AskToSignIn(
    reason: SignInReason,
    busy: Boolean,
    message: String?,
    onPress: () -> Unit,
) {
    Column {
        Text(
            reason.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            reason.why,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        message?.let { Refusal(it) }

        Spacer(Modifier.height(20.dp))

        // Google asks for its own mark on a plain surface rather than a tinted button, which
        // also keeps it distinct from the app's own primary actions.
        Button(
            onClick = onPress,
            enabled = !busy,
            shape = RoundedCornerShape(15.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    disabledContainerColor = MaterialTheme.colorScheme.background,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                GoogleMark(Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (busy) "Waiting for Google" else "Continue with Google",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Play ties closed testing to Google accounts.",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * What went wrong, as a wash rather than a box.
 *
 * It was an outlined rectangle with plain white text in it, which put the loudest thing in the
 * notice on its edge and the quietest on its words: a validation box bolted to the sheet rather
 * than the sheet saying something. The colour is carried by the text now and the shape only holds
 * it together.
 */
@Composable
private fun Refusal(message: String) {
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            .padding(12.dp),
    ) {
        Icon(
            Lucide.CircleAlert,
            null,
            Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/**
 * It worked. Shown for a beat so the account is visible, then the sheet goes on its own.
 *
 * Its own Column, and that is not tidiness. AnimatedContent lays its content out in a Box, so
 * siblings emitted here stack on top of each other rather than under: the title, the address and
 * the line about loading were all drawn over one another, which is what "broken" looked like.
 */
@Composable
private fun SignedIn(account: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Status.posted.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.Check, null, Modifier.size(20.dp), tint = Status.posted)
            }
            Spacer(Modifier.width(13.dp))
            Column {
                Text(
                    "Signed in",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    account,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Your crews and everything you have earned are loading.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
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
