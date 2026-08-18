package com.eazyverse.testtrack.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.data.*
import com.eazyverse.testtrack.findActivity
import kotlinx.coroutines.launch

/**
 * What this account is, and what it has done.
 *
 * The console has a screen like this about other people and there was no equivalent here about
 * yourself, which left a tester with no way to see their own record and no way to change how they
 * appear in twelve other people's grids. Both halves are the same screen because they are the same
 * question: an account is its name, its face and what it has been part of.
 *
 * Everything shown is already readable by this account. Nothing here is a new permission.
 */
class ProfileViewModel : ViewModel() {

    var me by mutableStateOf<Tester?>(null)
        private set
    var apps by mutableStateOf<List<TestApp>>(emptyList())
        private set
    var groups by mutableStateOf<List<TestGroup>>(emptyList())
        private set

    /** Days this account has reported anything, in any cohort. The count nobody else keeps. */
    var daysReported by mutableStateOf(0)
        private set

    var ready by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    fun load() {
        val uid = AuthRepo.uid ?: run { ready = true; return }
        viewModelScope.launch {
            runCatching {
                val mine = Repo.myApps(uid)
                val cohorts = Repo.myGroups(uid)
                // One query per cohort, and there are never many. Summed rather than listed,
                // because the useful number is "how many evenings has this taken" and the
                // per-group breakdown is already on each group's own screen.
                val days = cohorts.sumOf { Repo.myReportedDays(uid, it.id).size }

                me = Repo.me(uid) ?: Tester(uid = uid, email = Session.email.orEmpty())
                apps = mine.sortedWith(compareBy({ it.closed }, { -it.submittedAt }))
                groups = cohorts.sortedByDescending { it.startDate }
                daysReported = days
                message = null
            }.onFailure {
                message = it.friendly("We couldn't load your profile. Check your connection.")
            }
            ready = true
        }
    }

    /**
     * The same unwind the other two screens do, in the same order.
     *
     * [releaseSession] has to finish before the caller drops the account, because clearing the push
     * token is a Firestore write made with the credentials being abandoned.
     */
    fun signOut(context: android.content.Context, then: () -> Unit) {
        viewModelScope.launch {
            releaseSession(context)
            then()
        }
    }

    /**
     * Saves the name and the picture together.
     *
     * The screen shows what was saved rather than what was typed, so a failure leaves the old
     * values on screen instead of a hopeful edit that never landed.
     */
    fun save(displayName: String, photo: String, then: () -> Unit) {
        val uid = AuthRepo.uid ?: return
        saving = true
        viewModelScope.launch {
            runCatching { Repo.updateProfile(uid, displayName, photo) }
                .onSuccess {
                    me = me?.copy(displayName = displayName.trim(), photo = photo)
                    message = null
                    then()
                }
                .onFailure {
                    message = it.friendly("We couldn't save your profile. Check your connection.")
                }
            saving = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    vm: ProfileViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    LaunchedEffect(Unit) { vm.load() }

    var editing by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { editing = true }) {
                        Icon(Icons.Default.Edit, "Edit profile")
                    }
                    IconButton(onClick = { confirmSignOut = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, "Sign out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {
            if (!vm.ready) {
                Loading()
                return@Column
            }

            val me = vm.me
            Header(
                photo = me?.photo.orEmpty(),
                initial = me?.initial ?: "?",
                name = me?.shortName.orEmpty(),
                email = me?.email.orEmpty(),
                cohorts = vm.groups.size,
                submissions = vm.apps.size,
                days = vm.daysReported
            )

            if (vm.apps.isNotEmpty()) {
                SectionLabel("Your apps", "${vm.apps.size}")
                Rows {
                    vm.apps.forEachIndexed { index, app ->
                        if (index > 0) RowDivider()
                        SubmissionRow(app)
                    }
                }
            }

            if (vm.groups.isNotEmpty()) {
                SectionLabel("Cohorts", "${vm.groups.size}")
                Rows {
                    vm.groups.forEachIndexed { index, group ->
                        if (index > 0) RowDivider()
                        CohortRow(group)
                    }
                }
            }

            if (vm.apps.isEmpty() && vm.groups.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Inbox,
                    title = "Nothing here yet",
                    subtitle = "Submit an app and your record starts filling in."
                )
            }

            vm.message?.let {
                Spacer(Modifier.height(20.dp))
                Failure(it)
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (editing && vm.me != null) {
        EditProfile(
            me = vm.me!!,
            saving = vm.saving,
            onSave = { name, photo -> vm.save(name, photo) { editing = false } },
            onDismiss = { editing = false }
        )
    }

    if (confirmSignOut) {
        Ask(
            title = "Sign out?",
            body = "Your record stays where it is. Signing back in with the same account brings " +
                "it all back.",
            confirm = "Sign out",
            destructive = true,
            onConfirm = {
                confirmSignOut = false
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

/**
 * The name and the picture, edited together.
 *
 * The picker is [ActivityResultContracts.PickVisualMedia], which is the whole reason this screen
 * asks for no permission at all. It runs in the system's own process and hands back one image the
 * person chose, so the alternative — reading the photo library and asking for the right to — buys
 * nothing here and costs another prompt in an app that already asks for four.
 */
@Composable
private fun EditProfile(
    me: Tester,
    saving: Boolean,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(me.displayName) }
    var photo by remember { mutableStateOf(me.photo) }
    var tooBig by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = Photo.encode(context, uri)
            tooBig = encoded == null
            if (encoded != null) photo = encoded
        }
    }

    Ask(
        title = "Edit profile",
        confirm = if (saving) "Saving" else "Save",
        confirmEnabled = !saving,
        onConfirm = { onSave(name, photo) },
        onDismiss = onDismiss
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The badge is the only way in, and the whole avatar is tappable behind it. A text
            // button underneath saying the same thing was a second control for one action, which
            // is a thing to read and decide about rather than a thing to press.
            val pick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(Modifier.clip(CircleShape).clickable { pick() }) {
                    Avatar(photo, me.initial, size = 84.dp)
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { pick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.PhotoCamera,
                        "Change picture",
                        Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Only ever shown when there is something to remove, so the row is empty rather than
            // disabled for the accounts that never set one.
            if (photo.isNotBlank()) {
                TextButton(onClick = { photo = ""; tooBig = false }) {
                    Text("Remove picture", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Spacer(Modifier.height(14.dp))
            }

            Spacer(Modifier.height(6.dp))
            DialogField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = "Name",
                placeholder = me.email.substringBefore('@'),
                // What went wrong, in the reader's terms. This said the picture "wouldn't compress
                // small enough", which told somebody choosing a photograph about our JPEG budget
                // and left them nothing to do about it. Reading the file is the only way this
                // actually fails now, so that is what it says.
                error = if (tooBig) "We couldn't read that picture. Try another one." else null,
                supporting = "This is what the rest of your cohort sees beside your app.",
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            )
        }
    }
}

/** One app this account has submitted, and where it got to. */
@Composable
private fun SubmissionRow(app: TestApp) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(app.packageName, app.label)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        val (label, tone, background) = when {
            app.status == TestApp.STATUS_REJECTED ->
                Triple("Turned down", Status.missed, Status.missedSoft)
            app.status == TestApp.STATUS_WITHDRAWN ->
                Triple("Withdrawn", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft)
            app.status == TestApp.STATUS_DONE ->
                Triple("Finished", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft)
            app.removed ->
                Triple("Taken out", Status.short, Status.shortSoft)
            app.placed -> Triple("In a cohort", Status.posted, Status.postedSoft)
            else -> Triple("Waiting", MaterialTheme.colorScheme.onSurfaceVariant, Status.neutralSoft)
        }
        Pill(label, tone, background)
    }
}

/** One cohort this account has been part of. */
@Composable
private fun CohortRow(group: TestGroup) {
    val day = group.dayIndex()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                group.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when {
                    // Plus one, because dayIndex counts from zero and every other screen that
                    // shows a day to a person adds it. Getting this wrong put "Day 1" on a
                    // profile while the group screen behind it said "Day 2".
                    day != null -> "Day ${day + 1} of ${group.runDays}"
                    group.running -> "Finished its run"
                    else -> "Waiting to start"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Who this is, then what they have done, in the console's shape.
 *
 * Left aligned and not centred. A centred portrait is a social profile and this is a record: the
 * name is the subject of a page whose whole body is a list, and a column that starts in the middle
 * and then jumps to the left margin for every row underneath has no spine to it.
 *
 * The three numbers sit in one bordered box rather than floating on the background, which is what
 * makes them read as a set. `days reported` is the one worth having: every group screen knows its
 * own and nothing else adds them up.
 */
@Composable
private fun Header(
    photo: String,
    initial: String,
    name: String,
    email: String,
    cohorts: Int,
    submissions: Int,
    days: Int
) {
    Column(Modifier.padding(start = Gutter, end = Gutter, top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(photo, initial, size = 46.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name.ifBlank { "You" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (email.isNotBlank()) {
                    Text(
                        email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(13.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Figure(Modifier.weight(1f), "$cohorts", "cohorts")
            Upright()
            Figure(Modifier.weight(1f), "$submissions", "submissions")
            Upright()
            Figure(Modifier.weight(1f), "$days", "days reported")
        }
    }
}

/** The page's own shape while it loads, rather than the home screen's. */
@Composable
private fun Loading() {
    Column(Modifier.padding(start = Gutter, end = Gutter, top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(width = 46.dp, height = 46.dp, corner = 23.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Skeleton(width = 132.dp, height = 17.dp)
                Spacer(Modifier.height(6.dp))
                Skeleton(width = 186.dp, height = 12.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Skeleton(height = 68.dp, corner = 13.dp)
    }
    SectionLabel("Your apps")
    Rows { SkeletonAppList(2) }
}

@Composable
private fun Upright() {
    Box(
        Modifier
            .width(1.dp)
            .height(46.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun Figure(modifier: Modifier, value: String, caption: String) {
    Column(modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            caption,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
