package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Tt
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A crew's members, from the button in its bar: everybody in it, and what each of them did with
 * the reader's own app. The proof is the phone's own record of each open, ten seconds or more in
 * the app, so there is nothing like a screenshot to check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewMembersPage(
    vm: CrewViewModel,
    id: String,
    onBack: () -> Unit,
) {
    val crew = vm.crews.firstOrNull { it.id == id }
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { PageBar(crew?.let { "${it.name} · Members" } ?: "Members", onBack) },
    ) { padding ->
        if (crew == null) {
            Box(Modifier.fillMaxSize().padding(padding)) { CrewLobbyLoading() }
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
                        chip = if (finished) "Finished · ${crew.finishedDays} days" else "Their day ${crew.day}",
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
                            "${testers.size} testers are still in. ${CrewRules.FLOOR} need to keep opening it every day " +
                                "for Google to count the run.",
                        meter = {
                            val lit = Status.posted
                            SegmentMeter(testers.size.coerceAtLeast(1)) { i -> if (i < opened) lit else Tt.colors.glass2 }
                            val notYet = testers.size - opened
                            MeterLabels(
                                if (finished) "$opened every day · $out left" else "$opened opened · $notYet not yet · $out out",
                                "${CrewRules.FLOOR} needed",
                            )
                        },
                    )
                }
            }
            item { CrewHeading("Testers", "${others.size}") }
            itemsIndexed(others, key = { _, m -> m.id }) { index, member ->
                Box(Modifier.padding(horizontal = Gutter).padding(bottom = if (index < others.size - 1) 3.dp else 0.dp)) {
                    MemberRow(member, crew, placeOf(index, others.size))
                }
            }
        }
    }
}

/** One person in the crew, and what they did with your app. */
@Composable
private fun MemberRow(
    member: CrewMember,
    crew: Crew,
    place: TilePlace,
) {
    val finished = crew.phase == CrewPhase.RECORD
    val days = member.openedMineDays
    val (line, chip, tone) =
        when {
            member.out -> Triple("Left the run · opened yours on $days ${plural(days, "day")}", "Out", Tone.QUIET)
            finished && days >= crew.finishedDays -> Triple("Opened yours on all ${crew.finishedDays} days", "Stayed", Tone.OK)
            finished -> Triple("Opened yours on $days of ${crew.finishedDays} days", "Stayed", Tone.OK)
            member.missedYesterday ->
                Triple(
                    "Missed yesterday · opened yours on $days ${plural(days, "day")}",
                    "${member.missed} missed",
                    Tone.WARN,
                )
            member.openedMineToday ->
                Triple(
                    "Opened yours${clock(member.openedMineAt)} · $days ${plural(days, "day")} so far",
                    "Opened",
                    Tone.OK,
                )
            else -> Triple("Not tonight yet · opened yours on $days ${plural(days, "day")}", "Not yet", Tone.QUIET)
        }
    val t = Tt.colors
    val shape = place.shape()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Initial(member.name.take(1).uppercase(), size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                member.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                line,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                color = t.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(chip, tone)
    }
}

private fun plural(
    n: Int,
    word: String,
) = if (n == 1) word else "${word}s"

/** " at 9:14 pm", in this phone's own time, or nothing if the time did not come. */
private fun clock(iso: String): String =
    runCatching {
        val local = OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
        " at " + local.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())).lowercase()
    }.getOrDefault("")
