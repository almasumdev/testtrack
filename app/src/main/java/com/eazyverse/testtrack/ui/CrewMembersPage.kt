package com.eazyverse.testtrack.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.HandHeart
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.domain.instantOf
import com.eazyverse.testtrack.domain.shortDate
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AppNights
import com.eazyverse.testtrack.viewmodel.CrewPicturesViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A crew's testers, from the button in its bar: everybody testing the reader's app, on a running
 * crew and a finished one alike. A line on how tonight (or the run) went, then the grid of every
 * tester's nights, then a tile for each tester with their last three pictures, those out of the
 * run last. A face anywhere opens who it is. What the squares mean is behind the grid's (i).
 *
 * Two answers make it: the crew, for who each member is, and the crew's pictures, for each night.
 * A finished crew's details are cleared a few days after it ends, and the page then says so.
 */
@Composable
fun CrewMembersPage(
    vm: CrewViewModel,
    id: String,
    onBack: () -> Unit,
    pictures: CrewPicturesViewModel = viewModel(),
) {
    val crew = vm.crews.firstOrNull { it.id == id }
    val pruned = crew?.pruned == true
    LaunchedEffect(id, pruned) { if (!pruned) pictures.load(id) }
    CrewMembersBody(
        crew = crew,
        nights = pictures.pictures,
        failed = pictures.failed,
        gone = pruned || pictures.gone,
        onBack = onBack,
        onRetry = { pictures.load(id) },
    )
}

/** The page drawn from plain values, in every state it has. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewMembersBody(
    crew: Crew?,
    nights: AppNights?,
    failed: String?,
    gone: Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    now: Instant = Instant.now(),
) {
    val hour24 = DateFormat.is24HourFormat(LocalContext.current)
    var profile by remember { mutableStateOf<CrewTester?>(null) }
    var open by remember { mutableStateOf<NightPicture?>(null) }
    var key by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { PageBar(crew?.let { "${it.name} testers" } ?: "Testers", onBack) },
    ) { padding ->
        val page = Modifier.fillMaxSize().padding(padding)
        when {
            gone ->
                Box(page) {
                    RobotState(
                        Prop.Calendar,
                        instantOf(crew?.detailsUntil)?.let { shortDate(it) }?.let { "Details cleared on $it" } ?: "Details cleared",
                        "Testers and pictures are cleared 3 days after a crew ends. The result stays on the crew page.",
                        action = "Back to ${crew?.name ?: "the crew"}",
                        onAction = onBack,
                        failed = true,
                    )
                }
            nights == null && failed != null -> Box(page) { RobotOffline("Testers didn't load", onRetry = onRetry) }
            crew == null || nights == null -> Box(page) { TestersLoading() }
            else -> {
                val finished = crew.phase == CrewPhase.RECORD
                val testers = remember(crew, nights, now, hour24) { testersOf(nights, crew, now, hour24) }
                val tiles = remember(testers, finished) { tileOrder(testers, finished) }
                val today = if (!finished && crew.day in 1..nights.runDays) crew.day else 0
                LazyColumn(page, contentPadding = PaddingValues(top = 8.dp, bottom = Gutter + dockRoom())) {
                    item {
                        Text(
                            summaryOf(testers, nights, crew, finished),
                            Modifier.padding(horizontal = Gutter),
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = Tt.colors.text,
                            maxLines = 2,
                        )
                    }
                    item {
                        Box(Modifier.padding(start = Gutter, end = Gutter, top = 12.dp)) {
                            TrackingGrid(
                                nights,
                                testers,
                                finished = finished,
                                today = today,
                                onFace = { profile = it },
                                onKey = { key = true },
                            )
                        }
                    }
                    // Volunteers under their own heading once there are any; otherwise one list as before.
                    val volunteers = tiles.filter { it.volunteer }
                    val sections =
                        if (volunteers.isEmpty()) {
                            listOf("Testers" to tiles)
                        } else {
                            listOf("Members" to tiles.filter { !it.volunteer }, "Volunteers" to volunteers)
                        }
                    sections.forEach { (title, list) ->
                        item { CrewHeading(title, "${list.size}") }
                        itemsIndexed(list, key = { _, tester -> tester.id }) { index, tester ->
                            Box(Modifier.padding(horizontal = Gutter).padding(bottom = if (index < list.size - 1) 8.dp else 0.dp)) {
                                TesterTile(tester, onFace = { profile = tester }, onPicture = { open = it })
                            }
                        }
                    }
                }
                if (key) SquaresKey(finished, notesOf(testers, crew, finished)) { key = false }
            }
        }
    }
    profile?.let { CrewMemberProfile(it) { profile = null } }
    open?.let {
        PictureViewer(PictureSource.Proof(it.ref), title = nightTitle(it), footnote = nightProves(it, hour24)) { open = null }
    }
}

/** The line over the grid: how many opened the app tonight, or every night of a finished run. */
private fun summaryOf(
    testers: List<CrewTester>,
    nights: AppNights,
    crew: Crew,
    finished: Boolean,
): String {
    val app = nights.appName.ifBlank { crew.mine?.appName.orEmpty() }.ifBlank { "your app" }
    val stillIn = testers.filter { !it.out }
    return if (finished) {
        val every = stillIn.count { tester -> tester.squares.count { it.counted } >= tester.squares.count { it.owed } }
        "$every of ${stillIn.size} opened $app every night"
    } else {
        "${stillIn.count { it.openedTonight }} of ${stillIn.size} opened $app tonight"
    }
}

/** A night they owed the app: everything but one not owed or before their first. */
private val Square.owed: Boolean get() = this != Square.NOT_OWED && this != Square.BEFORE

/** The notes under the key: who is still in and what Google needs, and how long pictures are kept. */
private fun notesOf(
    testers: List<CrewTester>,
    crew: Crew,
    finished: Boolean,
): List<Pair<ImageVector, String>> {
    if (finished) {
        val until = instantOf(crew.detailsUntil)?.let { weekdayDate(it) }
        return listOf(
            Lucide.Clock to
                if (until != null) {
                    "Pictures of the last 3 nights are kept until $until. Tap one to see it whole."
                } else {
                    "Pictures of the last 3 nights. Tap one to see it whole."
                },
        )
    }
    val stillIn = testers.count { !it.out }
    val out = testers.size - stillIn
    return listOfNotNull(
        Lucide.Users to "$stillIn still in${if (out > 0) ", $out left" else ""}. ${CrewRules.FLOOR} need to keep opening it every day.",
        Lucide.Image to "Pictures of the last 3 nights. Tap one to see it whole.",
        if (testers.any { it.volunteer }) {
            Lucide.HandHeart to "A hand marks a volunteer. They have no app here and count for Google like members."
        } else {
            null
        },
    )
}

/** A day with its weekday on this phone, such as "Fri, Nov 20": where the page says when pictures go. */
private fun weekdayDate(at: Instant): String =
    at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
