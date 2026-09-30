package com.eazyverse.testtrack.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.UserRound
import com.eazyverse.testtrack.BuildConfig
import com.eazyverse.testtrack.net.ApiProfile
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * What this account is, and what it has done.
 *
 * The old version of this screen read Firestore and talked about cohorts an admin filled. It has
 * been replaced rather than ported, because the thing it described no longer exists: crews form
 * themselves now, and what an account is worth is counted from work rather than from membership.
 *
 * Who you are, what you have done, and the account itself, in the order somebody asks. It was three
 * cards of the same size, which gave the name, the record and the crews equal weight and put the
 * balance on screen twice, once here and once in the header above it. Now the person is the top of
 * the page, the figures share one card, and everything you can do to the account is one list.
 */
@Composable
fun MeScreen(
    onSignOut: () -> Unit,
    onSignIn: () -> Unit = {},
    vm: MeViewModel = viewModel(),
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.load(context) }

    MeBody(
        profile = vm.profile,
        loading = vm.loading,
        offline = vm.offline,
        signedOut = vm.signedOut,
        onEdit = { editing = true },
        onSignOut = onSignOut,
        onSignIn = onSignIn,
    )

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
    signedOut: Boolean = false,
    onEdit: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onSignIn: () -> Unit = {},
) {
    if (signedOut) {
        Gated(
            heading = "Level one, waiting",
            body =
                "Every night you open somebody's app moves this ring. Sign in and it starts " +
                    "counting.",
            onSignIn = onSignIn,
            mark = { LevelMark() },
        )
        return
    }
    if (offline) {
        EmptyState(
            icon = Lucide.UserRound,
            title = "Your record did not load",
            subtitle = "Check your connection and try again.",
        )
        return
    }
    if (profile == null) {
        Blank(if (loading) "Getting your record." else "Nothing to show yet.")
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
    ) {
        item { Who(profile, onEdit) }
        item { Spacer(Modifier.height(20.dp)) }
        item { Record(profile) }
        item { CrewHeading("Account") }
        item { Account(onEdit = onEdit, onSignOut = onSignOut) }
        item { Spacer(Modifier.height(24.dp)) }
        item {
            Text(
                "TestTrack ${BuildConfig.VERSION_NAME}",
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The person, as the top of the page rather than as one card among three.
 *
 * The face is large and tappable, with the pencil on it saying so. The balance is not here: the
 * header over every tab already shows it, and a second copy two centimetres below the first was the
 * page repeating itself.
 */
@Composable
private fun Who(
    profile: ApiProfile,
    onEdit: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The badge sits outside the clip. Clipping the whole stack to the circle cut the pencil
        // in half, which is the one part of it that says the face can be pressed.
        Box {
            Box(
                Modifier
                    .size(FaceSize + 8.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onEdit)
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), CircleShape)
                    .padding(4.dp),
            ) {
                Avatar(
                    profile.photoUrl,
                    profile.displayName
                        .take(1)
                        .uppercase()
                        .ifBlank { "?" },
                    size = FaceSize,
                    letterStyle = MaterialTheme.typography.headlineMedium,
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onEdit)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Lucide.Pencil, "Edit profile", Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            profile.displayName.ifBlank { profile.email.substringBefore('@') },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            profile.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val since = memberSince(profile.joinedAt)
        if (since != null || profile.points > 0) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (profile.points > 0) {
                    Pill(
                        "${ordinal(profile.place)} on the board",
                        MaterialTheme.colorScheme.onPrimaryContainer,
                        MaterialTheme.colorScheme.primaryContainer,
                    )
                }
                if (since != null && profile.points > 0) Spacer(Modifier.width(8.dp))
                if (since != null) {
                    Pill(since, MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft)
                }
            }
        }
    }
}

private val FaceSize = 84.dp

/** "Since September 2026", from the server's timestamp, or null if it cannot be read. */
private fun memberSince(joinedAt: String): String? =
    runCatching {
        "Since " + OffsetDateTime.parse(joinedAt).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    }.getOrNull()

/**
 * What this account has done, as one card of six figures.
 *
 * Two cards of three were two boxes of the same shape saying different kinds of thing, and nothing
 * in them said which was which but a heading. One card with the work on the top row and the crews
 * underneath reads as one record, and the rule between the rows is what separates them. An account
 * that has done nothing is told so plainly instead of being given a place it has not earned.
 */
@Composable
private fun Record(profile: ApiProfile) {
    Box(Modifier.padding(horizontal = Gutter)) {
        CrewCard(padding = PaddingValues(vertical = 16.dp)) {
            Text(
                "Your record",
                Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))
            Figures(
                Triple("${profile.opens}", "apps opened", true),
                Triple("${profile.reviews}", "reviews given", true),
                Triple("${profile.points}", "points", true),
            )
            Spacer(Modifier.height(14.dp))
            CardRule()
            Spacer(Modifier.height(14.dp))
            Figures(
                Triple("${profile.crewsRunning}", "crews going on", false),
                Triple("${profile.crewsFinished}", "crews finished", false),
                Triple("${CrewRules.nightly(profile.crewsRunning)}", "apps a night", false),
            )
            if (profile.points == 0) {
                Spacer(Modifier.height(14.dp))
                Text(
                    "Open somebody's app on a testing night and this starts counting.",
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Three figures in a row, with a hairline between each.
 *
 * The work is in the accent and the crews are in plain ink. They are both counts, but only the
 * first row is the score, and colouring all six the same said they were six equal numbers.
 */
@Composable
private fun Figures(vararg figures: Triple<String, String, Boolean>) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        figures.forEachIndexed { index, (value, label, accent) ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge.tabular(),
                    fontWeight = FontWeight.Bold,
                    color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Where the policy and the deletion request live. The site owns both pages. */
private const val SITE = "https://eazyverse.com/apps/testtrack"

/**
 * Everything you can do to the account, as one list.
 *
 * Sign out was a full width outlined button in red on its own under the cards, the loudest thing
 * on the page for the action nobody comes here to take. In a list it is one row among the others,
 * still red, and the policy and the way to delete the account sit beside it where Play and the
 * person reading both expect to find them.
 */
@Composable
private fun Account(
    onEdit: () -> Unit,
    onSignOut: () -> Unit,
) {
    val links = LocalUriHandler.current
    Box(Modifier.padding(horizontal = Gutter)) {
        CrewCard(padding = PaddingValues(0.dp)) {
            AccountRow(Lucide.Pencil, "Edit profile", onClick = onEdit)
            CardRule()
            AccountRow(Lucide.ShieldCheck, "Privacy policy", external = true) {
                links.openUri("$SITE/privacy")
            }
            CardRule()
            AccountRow(Lucide.Trash2, "Delete account", external = true) {
                links.openUri("$SITE/delete-account")
            }
            CardRule()
            AccountRow(Lucide.LogOut, "Sign out", tone = MaterialTheme.colorScheme.error, onClick = onSignOut)
        }
    }
}

/** One line of [Account]: an icon, what it does, and where it goes. */
@Composable
private fun AccountRow(
    icon: ImageVector,
    label: String,
    tone: Color? = null,
    external: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = tone ?: MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = tone ?: MaterialTheme.colorScheme.onSurface,
        )
        if (tone == null) {
            Icon(
                if (external) Lucide.ExternalLink else Lucide.ChevronRight,
                null,
                Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
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
