package com.eazyverse.testtrack.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.UserRound
import com.eazyverse.testtrack.BuildConfig
import com.eazyverse.testtrack.net.ApiProfile
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular
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
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 12.dp, bottom = 28.dp + dockRoom()),
    ) {
        item { Who(profile, onEdit) }
        item { Spacer(Modifier.height(12.dp)) }
        item { Record(profile) }
        item { Spacer(Modifier.height(6.dp)) }
        item { SectionTitle("Account") }
        item { Account(onEdit = onEdit, onSignOut = onSignOut) }
        item { Spacer(Modifier.height(24.dp)) }
        item {
            Text(
                "TestTrack ${BuildConfig.VERSION_NAME}",
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = Tt.colors.text4,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A heading inside the page's own gutter, the list's padding already having taken it. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(top = 16.dp, bottom = 10.dp),
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
        fontWeight = FontWeight.Bold,
    )
}

/**
 * The person, as a panel: Value Guard's profile card.
 *
 * Dashed because it is something you read rather than press; the face is the one part that does
 * something, and the pencil on it says so. The balance is not here: the header over every tab
 * already shows it.
 */
@Composable
private fun Who(
    profile: ApiProfile,
    onEdit: () -> Unit,
) {
    val t = Tt.colors
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The badge sits outside the clip, or the circle cuts the pencil in half.
            Box {
                Box(
                    Modifier
                        .size(FaceSize)
                        .clip(CircleShape)
                        .clickable(onClick = onEdit)
                        .background(t.glass)
                        .border(1.dp, t.glassBorder, CircleShape)
                        .padding(3.dp),
                ) {
                    Avatar(
                        profile.photoUrl,
                        profile.displayName
                            .take(1)
                            .uppercase()
                            .ifBlank { "?" },
                        size = FaceSize - 6.dp,
                        letterStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 23.sp),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onEdit)
                        .background(t.glass)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(t.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Pencil, "Edit profile", Modifier.size(11.dp), tint = t.onAccent)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    profile.displayName.ifBlank { profile.email.substringBefore('@') },
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    profile.email,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = t.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val since = memberSince(profile.joinedAt)
                if (since != null || profile.points > 0) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (profile.points > 0) {
                            ProfileChip("${ordinal(profile.place)} on the board", lit = true)
                        }
                        if (since != null) ProfileChip(since)
                    }
                }
            }
        }
    }
}

/** A small fact under the name. The board place is the one lit in the accent. */
@Composable
private fun ProfileChip(
    text: String,
    lit: Boolean = false,
) {
    val t = Tt.colors
    Text(
        text,
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (lit) t.tile else t.glass2)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
        fontWeight = FontWeight.SemiBold,
        color = if (lit) t.tileInk else t.text2,
        maxLines = 1,
    )
}

private val FaceSize = 64.dp

/** "Since September 2026", from the server's timestamp, or null if it cannot be read. */
private fun memberSince(joinedAt: String): String? =
    runCatching {
        "Since " + OffsetDateTime.parse(joinedAt).format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    }.getOrNull()

/**
 * What this account has done, as one panel of six figures.
 *
 * Value Guard's detail grid: the figures in mono, labels under them, hairlines between the cells.
 * The work is the top row and the crews are the bottom one. An account that has done nothing is
 * told so plainly instead of being given a place it has not earned.
 */
@Composable
private fun Record(profile: ApiProfile) {
    Panel(padding = PaddingValues(vertical = 16.dp)) {
        Text(
            "Your record",
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Figures(
            Triple("${profile.opens}", "apps opened", true),
            Triple("${profile.reviews}", "reviews given", true),
            Triple("${profile.points}", "points", true),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .height(1.dp)
                .background(Tt.colors.hair),
        )
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
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                color = Tt.colors.text3,
            )
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
    val t = Tt.colors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        figures.forEachIndexed { index, (value, label, accent) ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(t.hair),
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleLarge.tabular(),
                    fontWeight = FontWeight.Bold,
                    color = if (accent) t.accentInk else t.text,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
                    color = t.text3,
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
 * Everything you can do to the account, as one group of tiles.
 *
 * Value Guard's settings group: each row its own tile with an icon tile at the front, three points
 * apart, only the outer corners round. Sign out is the red one at the end, the policy and the way to
 * delete the account sit beside it where Play and the person reading both expect to find them.
 */
@Composable
private fun Account(
    onEdit: () -> Unit,
    onSignOut: () -> Unit,
) {
    val links = LocalUriHandler.current
    val out: @Composable () -> Unit = {
        Icon(Lucide.ExternalLink, null, Modifier.size(17.dp), tint = Tt.colors.text4)
    }
    TileGroup(count = 4) { index, place ->
        when (index) {
            0 -> TileRow(Lucide.Pencil, "Edit profile", place, subtitle = "Your name and picture", onClick = onEdit)
            1 ->
                TileRow(Lucide.ShieldCheck, "Privacy policy", place, trailing = out) {
                    links.openUri("$SITE/privacy")
                }
            2 ->
                TileRow(Lucide.Trash2, "Delete account", place, subtitle = "And everything in it", trailing = out) {
                    links.openUri("$SITE/delete-account")
                }
            else -> TileRow(Lucide.LogOut, "Sign out", place, danger = true, onClick = onSignOut)
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
