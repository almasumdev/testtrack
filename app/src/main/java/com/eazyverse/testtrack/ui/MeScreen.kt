package com.eazyverse.testtrack.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.UserRound
import com.eazyverse.testtrack.net.ApiProfile

/**
 * What this account is, and what it has done.
 *
 * The old version of this screen read Firestore and talked about cohorts an admin filled. It has
 * been replaced rather than ported, because the thing it described no longer exists: crews form
 * themselves now, and what an account is worth is counted from work rather than from membership.
 *
 * Three cards, in the order somebody asks the questions: what have I got, what have I done, and
 * who am I signed in as.
 */
@Composable
fun MeScreen(
    onSignOut: () -> Unit,
    vm: MeViewModel = viewModel(),
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.load(context) }

    TabScaffold("Profile") {
        MeBody(
            profile = vm.profile,
            loading = vm.loading,
            offline = vm.offline,
            onEdit = { editing = true },
            onSignOut = onSignOut,
        )
    }

    if (editing) {
        MeEditDialog(
            name = vm.profile?.displayName.orEmpty(),
            busy = vm.busy,
            onSave = { name, photo -> vm.save(context, name, photo) { editing = false } },
            onDismiss = { editing = false },
        )
    }
}

/** The profile, drawn. Handed its values, so a signed-out or empty account is testable too. */
@Composable
fun MeBody(
    profile: ApiProfile?,
    loading: Boolean = false,
    offline: Boolean = false,
    onEdit: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    if (offline) {
        EmptyState(
            icon = Lucide.UserRound,
            title = "Signed out",
            subtitle = "Sign in and your record, your tix and your apps come back with you.",
        )
        return
    }
    if (profile == null) {
        Blank(if (loading) "Getting your record." else "Nothing to show yet.")
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = Gutter),
    ) {
        item { Who(profile, onEdit) }
        item { Spacer(Modifier.height(12.dp)) }
        item { Record(profile) }
        item { Spacer(Modifier.height(12.dp)) }
        item { Crews(profile) }
        item { Spacer(Modifier.height(20.dp)) }
        item {
            Box(Modifier.padding(horizontal = Gutter)) {
                Secondary("Sign out", Modifier.fillMaxWidth(), destructive = true) { onSignOut() }
            }
        }
    }
}

/** Name, face and balance. The three things somebody checks without reading. */
@Composable
private fun Who(
    profile: ApiProfile,
    onEdit: () -> Unit,
) {
    Box(Modifier.padding(horizontal = Gutter)) {
        CrewCard(padding = PaddingValues(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Initial(
                    profile.displayName
                        .take(1)
                        .uppercase()
                        .ifBlank { "?" },
                    size = 48.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile.displayName.ifBlank { profile.email.substringBefore('@') },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        profile.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Pill(
                    "${profile.tix} tix",
                    MaterialTheme.colorScheme.onPrimaryContainer,
                    MaterialTheme.colorScheme.primaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Secondary("Edit", Modifier.fillMaxWidth(), onClick = onEdit)
        }
    }
}

/**
 * What this account has done, and where that puts it.
 *
 * The place is shown next to the work rather than on its own, because a rank without the figures
 * behind it is a number nobody can act on. An account that has done nothing is told so plainly
 * instead of being given a place it has not earned.
 */
@Composable
private fun Record(profile: ApiProfile) {
    Box(Modifier.padding(horizontal = Gutter)) {
        CrewCard(padding = PaddingValues(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Your record",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (profile.points > 0) {
                    Pill(
                        "${ordinal(profile.place)} on the board",
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.primaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Tally(Modifier.weight(1f), "${profile.opens}", "apps opened")
                Tally(Modifier.weight(1f), "${profile.reviews}", "reviews given")
                Tally(Modifier.weight(1f), "${profile.points}", "points")
            }
            if (profile.points == 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Open somebody's app on a testing night and this starts counting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** How many fortnights this account is in the middle of, and how many it has seen through. */
@Composable
private fun Crews(profile: ApiProfile) {
    Box(Modifier.padding(horizontal = Gutter)) {
        CrewCard(padding = PaddingValues(16.dp)) {
            Text(
                "Crews",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Tally(Modifier.weight(1f), "${profile.crewsRunning}", "going on")
                Tally(Modifier.weight(1f), "${profile.crewsFinished}", "finished")
                Tally(
                    Modifier.weight(1f),
                    "${CrewRules.nightly(profile.crewsRunning)}",
                    "apps a night",
                )
            }
        }
    }
}

/**
 * "1st", "2nd", "3rd".
 *
 * Eleventh through thirteenth are the exception every naive version gets wrong, and they are
 * exactly the places a board this size is full of.
 */
fun ordinal(place: Int): String {
    if (place <= 0) return "unranked"
    val suffix =
        when {
            place % 100 in 11..13 -> "th"
            place % 10 == 1 -> "st"
            place % 10 == 2 -> "nd"
            place % 10 == 3 -> "rd"
            else -> "th"
        }
    return "$place$suffix"
}

/**
 * Changing how you appear to everybody else.
 *
 * The old Firestore profile could do this and the version that replaced it could not, so an
 * account was stuck with whatever Google last called it. Two fields, because two is all an
 * account is allowed to change: the email came from Google and the balance comes from the ledger.
 *
 * The picker is permissionless — it hands back one item the person chose and never gives the app
 * the library — which is why there is no permission prompt anywhere in this flow.
 */
@Composable
fun MeEditDialog(
    name: String,
    busy: Boolean = false,
    onSave: (String, Uri?) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf(name) }
    var photo by remember { mutableStateOf<Uri?>(null) }

    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) photo = uri
        }

    Ask(
        title = "How you appear",
        body = "Your name and picture are what the other fourteen people in a crew see.",
        confirm = if (busy) "Saving" else "Save",
        confirmEnabled = typed.trim().isNotBlank() && !busy,
        onConfirm = { onSave(typed, photo) },
        onDismiss = onDismiss,
        content = {
            DialogField(
                value = typed,
                onValueChange = { typed = it.take(120) },
                label = "Name",
                placeholder = "Rafiul",
                error = if (typed.isNotEmpty() && typed.isBlank()) "That is only spaces." else null,
                supporting = "However you want to be known. It does not have to be your real one.",
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Picture",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Secondary(
                if (photo == null) "Choose a picture" else "Chosen. Pick another",
                Modifier.fillMaxWidth(),
                enabled = !busy,
            ) {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Optional. Without one your initial is used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
