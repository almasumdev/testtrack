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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.BuildConfig
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.net.ApiProfile
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.TestingDriveViewModel
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
    onOpenRanks: () -> Unit = {},
    vm: MeViewModel = viewModel(),
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.load(context) }

    // Whether review and test proof can go to Drive, asked of Google each time the page opens.
    val activity = context.findActivity()
    val drive: TestingDriveViewModel = viewModel()
    LaunchedEffect(vm.signedOut) { if (!vm.signedOut) drive.check(activity) }
    val membership = rememberMembership()
    val driveSheet =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            drive.consentResult(activity, it.data) {}
        }
    val links = LocalUriHandler.current

    MeBody(
        profile = vm.profile,
        loading = vm.loading,
        offline = vm.offline,
        signedOut = vm.signedOut,
        // Not before the profile arrives, or an empty name could be saved over the real one.
        onEdit = { if (vm.profile != null) editing = true },
        onSignOut = onSignOut,
        onSignIn = onSignIn,
        onOpenRanks = onOpenRanks,
        onRetry = { vm.load(context) },
        drive = if (drive.busy) null else drive.connected,
        group = membership.member,
        // Joined, it opens the group itself; otherwise the check, and the way to join.
        onGroup = { if (membership.member) links.openUri(Config.GROUP_URL) else membership.show() },
        // Connected, it opens the account's own page, where the access can be seen and removed.
        onDrive = {
            if (drive.connected == true) {
                links.openUri(GOOGLE_CONNECTIONS)
            } else {
                drive.connect(activity, driveSheet::launch) {}
            }
        },
    )

    if (editing) {
        MeEditDialog(
            name = vm.profile?.displayName.orEmpty(),
            photo = vm.profile?.photoUrl.orEmpty(),
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
    onOpenRanks: () -> Unit = {},
    drive: Boolean? = null,
    onDrive: () -> Unit = {},
    group: Boolean = false,
    onGroup: () -> Unit = {},
    onRetry: () -> Unit = {},
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
    if (offline && profile == null) {
        // The robot, like every other tab that could not load.
        RobotOffline("Your record did not load", onRetry = onRetry)
        return
    }
    // Until it arrives the account rows are already there; only the person and figures shimmer.
    val shown = profile.takeUnless { DebugLooks.holdLoading }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 12.dp, bottom = 28.dp + dockRoom()),
    ) {
        item { if (shown == null) WhoLoading() else Who(shown, onEdit) }
        item { SectionTitle("Your record") }
        item {
            if (shown == null) {
                RecordGridLoading()
            } else {
                RecordGrid(
                    points = shown.points,
                    place = shown.place,
                    opens = shown.opens,
                    reviews = shown.reviews,
                    crewsRunning = shown.crewsRunning,
                    crewsFilled = shown.crewsFilled,
                    onOpenRanks = onOpenRanks,
                )
            }
        }
        item { SectionTitle("Account") }
        item { Account(onEdit = onEdit, onSignOut = onSignOut, drive = drive, onDrive = onDrive, group = group, onGroup = onGroup) }
        item { Spacer(Modifier.height(24.dp)) }
        item {
            Text(
                "v${BuildConfig.VERSION_NAME}",
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
 * The person: the face on the left, the name and address beside it, the small facts under both.
 *
 * Straight on the page rather than in a box, so the points under it are the one boxed thing at the
 * top and the eye goes there next. The face is the part that does something, and the pencil on it
 * says so. The balance is not here: the header over every tab already shows it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Who(
    profile: ApiProfile,
    onEdit: () -> Unit,
) {
    val t = Tt.colors
    Column(Modifier.padding(horizontal = 2.dp)) {
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
            }
        }
        // The facts on a row of their own under the name, full width and small, so the name and
        // the address keep the column beside the face to themselves.
        Spacer(Modifier.height(14.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            memberSince(profile.joinedAt)?.let { ProfileChip(Lucide.CalendarDays, it) }
            localTime(profile.timezone)?.let { ProfileChip(Lucide.Clock, it) }
        }
    }
}

internal val FaceSize = 64.dp

/** "Since Sep 2026", from the server's timestamp, or null if it cannot be read. */
private fun memberSince(joinedAt: String): String? =
    runCatching {
        "Since " + OffsetDateTime.parse(joinedAt).format(DateTimeFormatter.ofPattern("MMM yyyy"))
    }.getOrNull()

/** "Dhaka time", from the account's zone id, which is when its testing nights fall. */
private fun localTime(zone: String): String? =
    zone
        .substringAfterLast('/')
        .replace('_', ' ')
        .takeIf { it.isNotBlank() && zone.contains('/') }
        ?.let { "$it time" }

/** Where the policy and the deletion request live. The site owns both pages. */
private const val SITE = "https://eazyverse.com/apps/testtrack"

/** Google's list of apps with access to the account, where Drive access can be removed. */
private const val GOOGLE_CONNECTIONS = "https://myaccount.google.com/connections"

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
    drive: Boolean?,
    onDrive: () -> Unit,
    group: Boolean,
    onGroup: () -> Unit,
) {
    val links = LocalUriHandler.current
    val out: @Composable () -> Unit = {
        Icon(Lucide.ExternalLink, null, Modifier.size(17.dp), tint = Tt.colors.text4)
    }
    TileGroup(count = 6) { index, place ->
        when (index) {
            0 -> TileRow(Lucide.Pencil, "Edit profile", place, subtitle = "Your name and picture", onClick = onEdit)
            1 ->
                TileRow(
                    Lucide.Users,
                    "Testers group",
                    place,
                    subtitle = "Lets owners add you to closed tests",
                    value = if (group) "Joined" else "Join",
                    onClick = onGroup,
                )
            2 ->
                TileRow(
                    Lucide.HardDrive,
                    "Google Drive",
                    place,
                    subtitle = "Keeps your proof screenshots",
                    value =
                        when (drive) {
                            true -> "Connected"
                            false -> "Connect"
                            null -> "Checking"
                        },
                    onClick = if (drive == null) null else onDrive,
                )
            3 ->
                TileRow(Lucide.ShieldCheck, "Privacy policy", place, trailing = out) {
                    links.openUri("$SITE/privacy")
                }
            4 ->
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
    /** The picture there is now, as the profile carries it; blank for none. */
    photo: String = "",
    busy: Boolean = false,
    onSave: (String, Uri?) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by remember { mutableStateOf(name) }
    var picked by remember { mutableStateOf<Uri?>(null) }
    val t = Tt.colors

    val pick =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) picked = uri
        }

    val choose = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    Ask(
        title = "Edit profile",
        body = "Your name and picture are what people in your crews see.",
        confirm = if (busy) "Saving" else "Save",
        confirmEnabled = typed.trim().isNotBlank() && !busy,
        onConfirm = { onSave(typed, picked) },
        onDismiss = onDismiss,
    ) {
        // The picture in the middle with its camera badge, and the one thing to do with it under it.
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).clickable(enabled = !busy, onClick = choose)) {
                val chosen = picked
                if (chosen != null) {
                    AsyncImage(chosen, null, Modifier.size(72.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Avatar(
                        photo,
                        typed
                            .trim()
                            .take(1)
                            .uppercase()
                            .ifBlank { "?" },
                        size = 72.dp,
                        letterStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(t.glass)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(t.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Camera, null, Modifier.size(12.dp), tint = t.onAccent)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Change photo",
                Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = !busy, onClick = choose).padding(4.dp),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = t.accent,
            )
        }
        Spacer(Modifier.height(10.dp))
        DialogField(
            value = typed,
            onValueChange = { typed = it.take(NAME_MAX) },
            label = "Name",
            placeholder = "Rafiul",
            error = if (typed.isNotEmpty() && typed.isBlank()) "That is only spaces." else null,
            supporting = "It doesn't have to be your real name.",
            maxLength = NAME_MAX,
        )
    }
}

private const val NAME_MAX = 40
