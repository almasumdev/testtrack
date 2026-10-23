package com.eazyverse.testtrack.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.MembershipViewModel

/** The one membership check for the whole app, whichever screen asks. */
@Composable
fun rememberMembership(): MembershipViewModel = viewModel(LocalContext.current.findActivity() as ComponentActivity)

/**
 * Checking the testers group, and what to do when this account is not on it.
 *
 * The first check is a short list of what is being checked, ticked as it goes. A no turns into
 * the way to join: which account, three steps, and a way to check again, and coming back from
 * the group page checks again by itself. A yes says so and waits for Continue. A check that failed
 * is said as that, never as "you are not a member": telling somebody who joined an hour ago they
 * are not in the group is the answer most likely to make them give up. Cancel, at any point,
 * leaves the account unverified.
 */
@Composable
fun JoinGroupDialog(vm: MembershipViewModel = rememberMembership()) {
    if (!vm.asking) return
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watcher = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.returned() }
        owner.lifecycle.addObserver(watcher)
        onDispose { owner.lifecycle.removeObserver(watcher) }
    }
    JoinGroupAsk(
        email = vm.email,
        checking = vm.checking,
        outside = vm.outside,
        failure = vm.failure,
        confirmed = vm.confirmed,
        stillOut = vm.stillOut,
        onCheck = vm::check,
        onOpenGroup = {
            vm.openedGroup()
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.GROUP_URL)))
        },
        onClose = vm::close,
        onContinue = vm::proceed,
    )
}

/** The dialog, drawn from plain values so each of its faces can be put in front of a test. */
@Composable
fun JoinGroupAsk(
    email: String,
    checking: Boolean,
    outside: Boolean,
    failure: String?,
    onCheck: () -> Unit,
    onOpenGroup: () -> Unit,
    onClose: () -> Unit,
    confirmed: Boolean = false,
    stillOut: Boolean = false,
    onContinue: () -> Unit = {},
) {
    val account = email.ifBlank { "This Google account" }
    when {
        confirmed ->
            Ask(
                title = "You're all set",
                body = "Reviews and crew testing are open to you.",
                confirm = "Continue",
                onConfirm = onContinue,
                onDismiss = onClose,
                dismiss = null,
            ) {
                CheckList {
                    CheckRow(CheckState.DONE, "Signed in", trailing = account)
                    CheckRow(CheckState.DONE, "In the testers group")
                    CheckRow(CheckState.DONE, "Reviews and testing unlocked")
                }
            }
        failure != null ->
            Ask(
                title = "Couldn't check the group",
                body = "$failure Reviews and crew testing need this check first.",
                confirm = "Try again",
                confirmEnabled = !checking,
                onConfirm = onCheck,
                onDismiss = onClose,
                dismiss = "Not now",
                looking = checking,
            )
        outside -> Outside(account, checking, stillOut, onCheck, onOpenGroup, onClose)
        else ->
            Ask(
                title = "Checking your tester access",
                body = "Owners can only add you to closed tests through the testers group.",
                confirm = null,
                onConfirm = {},
                onDismiss = onClose,
                dismiss = "Cancel",
                looking = true,
            ) {
                CheckList {
                    CheckRow(CheckState.DONE, "Signed in", trailing = account)
                    CheckRow(CheckState.NOW, "Looking you up in the group")
                    CheckRow(CheckState.WAIT, "Unlock reviews and testing")
                }
            }
    }
}

/** Not in the group: which account, how to join, and a way to look again. */
@Composable
private fun Outside(
    account: String,
    checking: Boolean,
    stillOut: Boolean,
    onCheck: () -> Unit,
    onOpenGroup: () -> Unit,
    onClose: () -> Unit,
) {
    val t = Tt.colors
    Ask(
        title = "Join the testers group",
        body = "Owners can only add you to their closed tests once this account is in the group.",
        confirm = "Open group",
        confirmIcon = Lucide.ExternalLink,
        confirmEnabled = !checking,
        onConfirm = onOpenGroup,
        onDismiss = onClose,
        dismiss = if (checking) "Cancel" else "Not now",
        looking = checking,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(t.field)
                .border(1.dp, t.hair, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                account,
                Modifier.weight(1f),
                style = DialogBody,
                fontWeight = FontWeight.SemiBold,
                color = t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            if (checking) {
                Tag("Checking", t.accentInk, t.tile, busy = true)
            } else {
                Tag("Not joined", t.warn, t.warnBg)
            }
        }
        Spacer(Modifier.height(10.dp))
        DialogSteps(
            listOf(
                AnnotatedString("Open the group with this account"),
                buildAnnotatedString {
                    append("Tap ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append("Join group") }
                },
                AnnotatedString("Come back, we check again"),
            ),
            circled = true,
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(start = 28.dp)) { DialogNote("If the page says you don't have permission, tap Join anyway.") }
        Spacer(Modifier.height(12.dp))
        when {
            checking -> DialogNote("Looking you up in the group")
            stillOut ->
                Text(
                    buildAnnotatedString {
                        append("Still not in the group. Google can take a minute or two after you join. ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)) {
                            append("Check again")
                        }
                    },
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(t.warnBg)
                        .clickable(onClick = onCheck)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    style = DialogBody.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                    color = t.warn,
                )
            else ->
                Text(
                    buildAnnotatedString {
                        append("Already joined? ")
                        withStyle(
                            SpanStyle(color = t.accentInk, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
                        ) {
                            append("Check now")
                        }
                    },
                    // A line of text, touched as a 48dp target.
                    Modifier.minimumInteractiveComponentSize().clickable(onClick = onCheck),
                    style = DialogBody.copy(fontSize = 13.sp),
                    color = t.text4,
                )
        }
    }
}

/** A small rounded word beside the account: where it stands. */
@Composable
private fun Tag(
    text: String,
    ink: Color,
    ground: Color,
    busy: Boolean = false,
) {
    Row(
        Modifier.clip(RoundedCornerShape(percent = 50)).background(ground).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(10.dp), color = ink, strokeWidth = 1.8.dp, trackColor = Tt.colors.hair)
            Spacer(Modifier.width(5.dp))
        }
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
    }
}

private enum class CheckState { DONE, NOW, WAIT }

/** What is being checked, one row each, on a tinted ground. */
@Composable
private fun CheckList(rows: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Tt.colors.glass2)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) { rows() }
}

/** One row: ticked, in progress, or still to come. */
@Composable
private fun CheckRow(
    state: CheckState,
    text: String,
    trailing: String? = null,
) {
    val t = Tt.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            when (state) {
                CheckState.DONE ->
                    Box(Modifier.size(18.dp).clip(CircleShape).background(t.accent), contentAlignment = Alignment.Center) {
                        Icon(Lucide.Check, null, Modifier.size(11.dp), tint = t.onAccent)
                    }
                CheckState.NOW ->
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        color = t.accent,
                        strokeWidth = 2.2.dp,
                        trackColor = t.tile,
                    )
                CheckState.WAIT -> Box(Modifier.size(16.dp).border(1.6.dp, t.hair, CircleShape))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            Modifier.weight(1f),
            style = DialogBody,
            fontWeight = if (state == CheckState.NOW) FontWeight.SemiBold else FontWeight.Normal,
            color =
                when (state) {
                    CheckState.NOW -> t.text
                    CheckState.DONE -> t.text2
                    CheckState.WAIT -> t.text4
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                it,
                Modifier.weight(1f, fill = false),
                fontSize = 12.sp,
                color = t.text4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
