package com.eazyverse.testtrack.ui

import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.eazyverse.testtrack.BuildConfig
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.net.ApiProfile
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.TestingDriveViewModel
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * Current account identity, activity figures and settings, backed by the API.
 */
@Composable
fun MeScreen(
    onSignOut: () -> Unit,
    onSignIn: () -> Unit = {},
    onOpenRanks: () -> Unit = {},
    /** Whether notifications are allowed, read live by the shell; shown on the Notifications row. */
    notifications: Boolean = false,
    onAbout: () -> Unit = {},
    vm: MeViewModel = viewModel(),
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.load(context) }
    WhileShown(onShow = { vm.startLive(context) }, onHide = vm::stopLive)

    // Whether feedback proof/profile images can go to Drive, checked when the page opens.
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
        onEdit = {
            if (vm.profile != null) {
                vm.clearMessage()
                editing = true
            }
        },
        // Asked first: one tap on the last row of the page should not end the session.
        onSignOut = { signingOut = true },
        onSignIn = onSignIn,
        onOpenRanks = onOpenRanks,
        notifications = notifications,
        onAbout = onAbout,
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
            error = vm.message,
            onSave = { name, photo -> vm.save(context, name, photo) { editing = false } },
            onDismiss = { editing = false },
        )
    }

    if (signingOut) {
        Ask(
            title = "Sign out?",
            body = "Your crews, reviews and tix stay on your account. Sign in again any time to pick up where you left off.",
            confirm = "Sign out",
            destructive = true,
            onConfirm = {
                signingOut = false
                onSignOut()
            },
            onDismiss = { signingOut = false },
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
    notifications: Boolean = false,
    onAbout: () -> Unit = {},
    drive: Boolean? = null,
    onDrive: () -> Unit = {},
    group: Boolean = false,
    onGroup: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    if (signedOut) {
        // The sign-in ask, then what needs no account: About, rating and feedback, and the version.
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = FootRoom + dockRoom()),
        ) {
            item {
                // At its own height rather than a share of the screen, which cut the button in
                // half on a short phone; the settings follow straight after.
                RobotState(
                    Prop.Lock,
                    "Sign in to see your profile",
                    "Your points, your place on the board and every app you have opened, kept for you.",
                    action = "Sign in",
                    onAction = onSignIn,
                    fill = false,
                )
            }
            item { AppearanceGroup() }
            item { SupportGroup(onAbout) }
            item { VersionLine() }
        }
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
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 12.dp, bottom = FootRoom + dockRoom()),
    ) {
        item { LoadSwap(shown == null) { if (it) WhoLoading() else shown?.let { p -> Who(p, onEdit) } } }
        item { SectionTitle("Your record") }
        item {
            LoadSwap(shown == null) { waiting ->
                if (waiting || shown == null) return@LoadSwap RecordGridLoading()
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
        item {
            MeSettings(
                onEdit = onEdit,
                onSignOut = onSignOut,
                drive = drive,
                onDrive = onDrive,
                group = group,
                onGroup = onGroup,
                notifications = notifications,
                onAbout = onAbout,
            )
        }
        item { VersionLine() }
    }
}

/** A heading inside the page's own gutter, the list's padding already having taken it. */
@Composable
internal fun SectionTitle(text: String) {
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
                        .clickableRole("Edit profile", onEdit)
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

/** Under the version at the foot of the page: twice the gutter, so it does not sit on the dock. */
private val FootRoom = Gutter * 2

/** The running version, quietly, at the foot: the short form only. */
@Composable
private fun VersionLine() {
    Text(
        "v${BuildConfig.VERSION_NAME}",
        Modifier.fillMaxWidth().padding(top = 12.dp),
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
        fontWeight = FontWeight.Medium,
        color = Tt.colors.text4,
        textAlign = TextAlign.Center,
    )
}
