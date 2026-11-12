package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * A crew's members, from the button in its bar: everybody in it, and what each of them did with
 * the reader's own app. The proof is two things: the phone's own record of each open, ten seconds
 * or more in the app, and the picture of the app taken then, which each row shows for tonight and
 * Every night shows for the whole run.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewMembersPage(
    vm: CrewViewModel,
    id: String,
    onBack: () -> Unit,
    onEveryNight: () -> Unit = {},
) {
    val crew = vm.crews.firstOrNull { it.id == id }
    var open by remember { mutableStateOf<NightPicture?>(null) }
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { PageBar(crew?.let { "${it.name} members" } ?: "Members", onBack) },
    ) { padding ->
        if (crew == null) {
            Box(Modifier.fillMaxSize().padding(padding)) { CrewMembersLoading() }
            return@Scaffold
        }
        val finished = crew.phase == CrewPhase.RECORD
        val testers = crew.testers
        val opened = if (finished) crew.testedEveryDay else crew.openedMineTonight
        val out = crew.members.count { !it.mine && it.out }
        val others = crew.members.filter { !it.mine }.sortedWith(compareBy({ it.out }, { !it.openedMineToday }))
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 8.dp, bottom = Gutter + dockRoom()),
        ) {
            item {
                Box(Modifier.padding(horizontal = Gutter)) {
                    CrewHero(
                        chip = if (finished) "Finished after ${crew.finishedDays} days" else "Their day ${crew.day}",
                        tone =
                            if (finished && opened < CrewRules.FLOOR) {
                                Tone.WARN
                            } else if (finished) {
                                Tone.OK
                            } else {
                                Tone.ACCENT
                            },
                        icon = if (finished) Lucide.CircleCheck else Lucide.Users,
                        headline = "$opened of ${testers.size} " + if (finished) "tested it every day" else "opened your app tonight",
                        line =
                            "${testers.size} ${if (testers.size == 1) "tester is" else "testers are"} still in. " +
                                "${CrewRules.FLOOR} need to keep opening it every day " +
                                "for Google to count the run.",
                        meter = {
                            val lit = Status.posted
                            SegmentMeter(testers.size.coerceAtLeast(1)) { i -> if (i < opened) lit else Tt.colors.track }
                            val notYet = testers.size - opened
                            MeterLabels(
                                if (finished) "$opened every day, $out left" else "$opened opened, $notYet not yet, $out out",
                                "${CrewRules.FLOOR} needed",
                            )
                        },
                    )
                }
            }
            item {
                Box(Modifier.padding(horizontal = Gutter).padding(top = 8.dp)) {
                    Secondary("See every night", Modifier.fillMaxWidth(), onClick = onEveryNight)
                }
            }
            item { CrewHeading("Testers") }
            itemsIndexed(others, key = { _, m -> m.id }) { index, member ->
                Box(Modifier.padding(horizontal = Gutter).padding(bottom = if (index < others.size - 1) 8.dp else 0.dp)) {
                    MemberRow(member, crew) { picture -> open = picture }
                }
            }
        }
    }
    open?.let { PictureViewer(it) { open = null } }
}

/** One person in the crew, and what they did with your app. */
@Composable
private fun MemberRow(
    member: CrewMember,
    crew: Crew,
    onPicture: (NightPicture) -> Unit,
) {
    val finished = crew.phase == CrewPhase.RECORD
    val days = member.openedMineDays
    val (line, chip, tone) =
        when {
            member.out -> Triple("Left the run, opened yours on $days ${plural(days, "day")}", "Out", Tone.QUIET)
            finished && days >= crew.finishedDays -> Triple("Opened yours on all ${crew.finishedDays} days", "Stayed", Tone.OK)
            finished -> Triple("Opened yours on $days of ${crew.finishedDays} days", "Some days", Tone.QUIET)
            member.missedYesterday ->
                Triple(
                    "Missed yesterday, opened yours on $days ${plural(days, "day")}",
                    "${member.missed} missed",
                    Tone.WARN,
                )
            member.openedMineToday ->
                Triple(
                    if (member.openedMineByHand) {
                        "Opened yours${clockAt(member.openedMineAt)}, picked by hand"
                    } else {
                        "Opened yours${clockAt(member.openedMineAt)}, $days ${plural(days, "day")} so far"
                    },
                    "Opened",
                    Tone.OK,
                )
            else -> Triple("Not tonight yet, opened yours on $days ${plural(days, "day")}", "Not yet", Tone.QUIET)
        }
    // Tonight's picture takes the place of the Opened chip: it is the proof, not a word for it.
    val picture = member.openedMinePicture
    HairlineRow(
        leading = { Initial(member.name.take(1).uppercase(), size = 52.dp, letterStyle = MaterialTheme.typography.titleMedium) },
        title = member.name,
        note = AnnotatedString(line),
        trailing = {
            if (member.openedMineToday && picture.isNotBlank() && !finished) {
                PictureThumb(picture, member.openedMineByHand, 40.dp) {
                    onPicture(
                        NightPicture(
                            picture,
                            member.name,
                            member.day,
                            member.openedMineAt,
                            0,
                            member.openedMineByHand,
                            crew.mine?.appName.orEmpty(),
                        ),
                    )
                }
            } else {
                StatusChip(chip, tone)
            }
        },
    )
}

private fun plural(
    n: Int,
    word: String,
) = if (n == 1) word else "${word}s"
