package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.HandHeart
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Undo2
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.domain.pluralOf
import com.eazyverse.testtrack.ui.theme.Tt
import java.time.Instant

/** The space under the bar, the same 8dp as every other page with a back bar. */
private val ListTop = 8.dp

private fun LazyListScope.block(content: @Composable () -> Unit) = item { Box(Modifier.padding(horizontal = Gutter)) { content() } }

private fun <T> LazyListScope.rows(
    key: String,
    list: List<T>,
    id: (T) -> String,
    row: @Composable (T) -> Unit,
) = itemsIndexed(list, key = { _, it -> "$key-${id(it)}" }) { index, it ->
    Box(Modifier.animateItem().padding(horizontal = Gutter).padding(bottom = if (index < list.size - 1) 8.dp else 0.dp)) {
        row(it)
    }
}

/** A crew's page, laid out the same way in every phase: hero, your app, then its lists. */
@Composable
private fun CrewList(
    crew: Crew,
    actions: CrewActions,
    content: LazyListScope.(onSheet: (RowSheet) -> Unit) -> Unit,
) {
    var sheet by remember { mutableStateOf<RowSheet?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = ListTop, bottom = Gutter + dockRoom())) {
        content { sheet = it }
    }
    sheet?.let { CrewRowSheet(it, crew.name, actions, volunteer = crew.volunteer != null) { sheet = null } }
}

/**
 * Your app is in and the crew has not started: still filling, or full and waiting for midnight.
 * Or it was sent back, which is the whole page until it is put in again.
 */
@Composable
fun CrewLobbyPhase(
    crew: Crew,
    actions: CrewActions,
) {
    val mine = crew.mine
    // A volunteer joins a run already going: only the apps still in it are theirs to install.
    val others =
        crew.seats.filter {
            !it.mine && it.state != SeatState.REJECTED && (crew.volunteer == null || it.state == SeatState.CONFIRMED)
        }
    val asked = if (crew.mayVote) others.filter { it.state == SeatState.CHECKING } else emptyList()
    val install = others - asked.toSet()

    CrewList(crew, actions) { onSheet ->
        // Sent back by members, or taken out by the team before the run: either way it is not
        // in this crew any more, and its owner is told why rather than shown a lobby.
        if (mine != null && (mine.state == SeatState.REJECTED || mine.state == SeatState.REMOVED)) {
            block { SentBack(crew, mine, actions) }
            item { CrewHeading("Your app") }
            block { YourAppCard(mine, "Sent back", Tone.BAD, "Not in ${crew.name} any more.") }
            return@CrewList
        }
        block { LobbyHero(crew) }
        // Not in the first drawing: the way out before the first night, so a spot taken by mistake can be given back.
        if (crew.volunteer != null) {
            item { CrewHeading("Your part") }
            block { VolunteerCard(crew, actions) }
        }
        if (mine != null) {
            item { CrewHeading("Your app") }
            block { LobbyYourApp(crew, mine, actions) }
        }
        if (asked.isNotEmpty()) {
            item { CrewHeading("Does it install for you?", "${asked.size}") }
            item { AskedSteps() }
            rows("ask", asked, { it.id }) { seat -> AskedRow(seat, actions, onSheet) }
        }
        if (install.isNotEmpty()) {
            item { CrewHeading("Install these", if (crew.volunteer != null) "${install.size}" else null) }
            rows("in", install, { it.id }) { seat -> InstallRow(seat, actions, onSheet) }
        }
    }
}

/** How many answers settle an app Play could not check, said the same way wherever it is said. */
private val DECIDES =
    "${CrewRules.VOTES_TO_DECIDE} Yes answers or ${CrewRules.VOTES_TO_DECIDE} No answers decide it."

/** What to do with an app Play could not check, as steps, over the rows that ask. */
@Composable
private fun AskedSteps() {
    Column(Modifier.padding(start = Gutter, end = Gutter, bottom = 10.dp)) {
        listOf(
            "Play couldn't check these, so members decide.",
            "1. Install the app from its row.",
            "2. Once it's on your phone, the row asks if it works. Answer Yes or No.",
            "3. Won't install? Tap the row and say so.",
            DECIDES,
        ).forEach { line ->
            Text(line, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = Tt.colors.text4)
        }
    }
}

@Composable
private fun LobbyHero(crew: Crew) {
    val checking = (crew.taken - crew.filled).coerceAtLeast(0)
    if (crew.filling) {
        CrewHero(
            chip = "Filling, ${crew.filled} of ${CrewRules.SEATS}",
            tone = Tone.ACCENT,
            icon = Lucide.Users,
            headline = "${CrewRules.SEATS - crew.filled} more and it starts",
            line = "It starts at midnight, your time, the day it's full. Install the others now, so day 1 is only opening them.",
            meter = {
                SeatMeter(crew.filled, checking)
                MeterLabels("${crew.filled} in" + if (checking > 0) ", $checking being checked" else "", "${CrewRules.SEATS} seats")
            },
        )
    } else if (crew.volunteer != null) {
        // A volunteer, waiting for their first night: the same late start, said in nights.
        val apps = crew.seats.count { it.state == SeatState.CONFIRMED }
        CrewHero(
            chip = "Volunteer from night ${crew.firstNight}",
            tone = Tone.ACCENT,
            icon = Lucide.HandHeart,
            headline = "Starts in ${untilStart(crew.startsIn)}",
            line = "Install the $apps apps before midnight, your time. Nothing counts until then.",
            meter = {
                DayMeter(crew.firstNight - 1, done = true, from = crew.firstNight, total = crew.runDays)
                MeterLabels("You join on night ${crew.firstNight}", "${crew.ownNights} ${pluralOf(crew.ownNights, "night")} for you")
            },
        )
    } else if (crew.firstNight > 1) {
        // Put in by the team part-way through: this account's own run starts on a later day.
        CrewHero(
            chip = "Joining on day ${crew.firstNight}",
            tone = Tone.OK,
            icon = Lucide.Clock,
            headline = "Starts in ${untilStart(crew.startsIn)}",
            line =
                "This crew is already running. Your first day is day ${crew.firstNight}, from midnight, your time. " +
                    "Install the others before then and no day counts against you.",
            meter = {
                DayMeter(crew.firstNight - 1, done = true, from = crew.firstNight, total = crew.runDays)
                MeterLabels("You join on day ${crew.firstNight}", "${crew.ownNights} ${pluralOf(crew.ownNights, "day")} for you")
            },
        )
    } else {
        CrewHero(
            chip = "Full, ${CrewRules.SEATS} of ${CrewRules.SEATS}",
            tone = Tone.OK,
            icon = Lucide.Clock,
            headline = "Starts in ${untilStart(crew.startsIn)}",
            line = "Day 1 begins at midnight, your time. Install the other ${CrewRules.NIGHTLY} before then and no day counts against you.",
            meter = {
                SeatMeter(crew.filled, checking)
                MeterLabels("Everyone's in", "Day 1 at midnight, your time")
            },
        )
    }
}

@Composable
private fun LobbyYourApp(
    crew: Crew,
    mine: CrewSeat,
    actions: CrewActions,
) {
    val (chip, tone) =
        when {
            mine.state == SeatState.CHECKING -> "Checking" to Tone.WARN
            mine.reports > 0 -> "${mine.reports}" to Tone.WARN
            else -> "In" to Tone.OK
        }
    val reported = mine.state != SeatState.CHECKING && mine.reports > 0
    val status =
        when {
            mine.state == SeatState.CHECKING -> "Play couldn't check it, so members are trying it."
            mine.reports > 0 -> "Play shows it to the testers group, but a member had trouble."
            !crew.filling -> "Play shows it to the testers group. The crew is full, so it stays in now."
            else -> "Play shows it to the testers group."
        }
    YourAppCard(
        mine,
        chip,
        tone,
        status,
        extra = {
            when {
                mine.state == SeatState.CHECKING ->
                    Quotes(emptyList(), "${mine.yes} Yes and ${mine.no} No so far. $DECIDES Make sure the sign-in works.")
                mine.reportList.isNotEmpty() ->
                    SeatReports(mine, "One more report sends it back. Check the sign-in still works.")
            }
        },
        action =
            if (crew.filling) {
                {
                    Spacer(Modifier.height(12.dp))
                    val leaving = actions.busySeat == mine.id
                    Secondary(
                        if (leaving) "Taking it out" else "Take it back out",
                        Modifier.fillMaxWidth(),
                        destructive = true,
                        busy = leaving,
                    ) { actions.onLeave(mine) }
                    QuietNote("Your ${CrewRules.SEAT_COST} tix come back in full.", Modifier.padding(top = 10.dp))
                }
            } else {
                null
            },
        chipIcon = if (reported) Lucide.Flag else null,
        chipDescription = if (reported) "${mine.reports} ${pluralOf(mine.reports, "report")}" else null,
    )
}

/** Your app came back before the run: why, in the members' own words, and the way back in. */
@Composable
private fun SentBack(
    crew: Crew,
    mine: CrewSeat,
    actions: CrewActions,
) {
    val byTeam = mine.state == SeatState.REMOVED
    androidx.compose.foundation.layout.Column {
        CrewHero(
            chip = if (byTeam) "Taken out" else "Sent back",
            tone = Tone.BAD,
            icon = Lucide.Undo2,
            headline = if (byTeam) "The TestTrack team took your app out" else "Your app came back",
            line = mine.outReason.ifBlank { if (byTeam) "It is not in ${crew.name} any more." else "It came back to you before the run." },
            extra = {
                SeatReports(mine, "What members wrote. Who wrote it isn't shown.", Tone.BAD)
            },
            action = if (byTeam) "Put it in a crew again" else "Fix it, then put it in again",
            onAction = actions.onTakeSeat,
        )
        QuietNote(
            seatRefundLine(mine, if (byTeam) "" else " Fix what's wrong, then put it in a crew again."),
            Modifier.padding(top = 10.dp),
        )
    }
}

/**
 * One crew's night. A row is a tap and the app opens; an app not on this phone offers Install
 * first. Above it, the night in one press, for the apps that are here.
 */
@Composable
fun CrewRunPhase(
    crew: Crew,
    actions: CrewActions,
) {
    val seatOf = { app: CrewApp -> crew.seats.firstOrNull { it.id == app.id } }
    val owed = crew.apps.filter { !it.done && !it.reported }
    // A row needs its seat; one the crew has no seat for (a list caught mid-change) is left out.
    val apps = crewRunAppEntries(crew, actions.isInstalled).filter { seatOf(it.app) != null }
    // What Open all opens: on this phone, and nothing the server is known to refuse.
    val here = openAllOf(owed, { actions.copyOf(it) }, actions.sendingPicture)
    val missing = owed.filter { !actions.isInstalled(it.packageName) }
    val refused = owed.filter { refusedHere(it, actions.copyOf(it.packageName)) }

    CrewList(crew, actions) { onSheet ->
        if (crew.volunteer?.ended == true) {
            block { VolunteerEndedHero(crew, actions) }
            return@CrewList
        }
        if (crew.out) {
            block { OwnerOutCard(crew, actions) }
            return@CrewList
        }
        // Ahead in time zone: this account's last night is over while the others finish theirs.
        if (crew.nightsDone) {
            block { NightsDoneHero(crew) }
            if (crew.volunteer != null) {
                item { CrewHeading("Your part") }
                block { VolunteerCard(crew, actions) }
            }
            crew.mine?.let { mine ->
                item { CrewHeading("Your app") }
                block { TestersCard(crew, mine, actions) }
                privateRow(mine, actions)
            }
            return@CrewList
        }
        block { RunHero(crew, owed, here, missing, refused, actions) }
        // Tonight's pictures missing or sending, and any older night's still on the phone.
        block { PicturesDueCard(crew.apps + crew.optional, actions) }
        crew.mine?.let { mine ->
            item { CrewHeading("Your app") }
            block { TestersCard(crew, mine, actions) }
            privateRow(mine, actions)
            block { OwnerToolsCard(mine, actions) }
        }
        if (crew.volunteer != null) {
            item { CrewHeading("Your part") }
            block { VolunteerCard(crew, actions) }
        }
        if (apps.isNotEmpty()) {
            item { CrewHeading("Apps", "${apps.size}") }
            rows("app", apps, { it.app.id }) { entry ->
                CrewRunAppTile(entry.app, seatOf(entry.app) ?: return@rows, actions, onSheet, entry.kind, volunteer = crew.volunteer != null)
            }
        }
    }
}

/** The way to the owner's private testers, under their testers card while the run goes. */
private fun LazyListScope.privateRow(
    mine: CrewSeat,
    actions: CrewActions,
) = actions.privateLine(mine)?.let { line -> block { PrivateTestersRow(line) { actions.onPrivateTesters(mine) } } }

/** Your app during a run, or after one: how many of the others opened it, and who. */
@Composable
private fun TestersCard(
    crew: Crew,
    mine: CrewSeat,
    actions: CrewActions,
) {
    val testers = crew.testers.size
    val finished = crew.phase == CrewPhase.RECORD
    val opened = if (finished) crew.testedEveryDay else crew.openedMineTonight
    val enough = opened >= CrewRules.FLOOR
    val status =
        if (finished) {
            "Tested by $opened people on all ${crew.finishedDays} days. " +
                if (enough) "Enough for Google." else "Google asks for ${CrewRules.FLOOR}."
        } else {
            "Opened by $opened of $testers testers so far tonight."
        }
    YourAppCard(
        mine.copy(notes = ""),
        "$opened of $testers",
        if (!finished) {
            Tone.ACCENT
        } else if (enough) {
            Tone.OK
        } else {
            Tone.WARN
        },
        status,
        extra = {
            Spacer(Modifier.height(14.dp))
            val fill =
                if (!finished) {
                    Tt.colors.accent
                } else if (enough) {
                    Status.posted
                } else {
                    Status.short
                }
            SegmentMeter(testers.coerceAtLeast(1)) { i -> if (i < opened) fill else Tt.colors.track }
            MeterLabels(if (finished) "${crew.finishedDays} days done" else "Their day ${crew.day}", "${CrewRules.FLOOR} keep it counting")
            SeatReports(mine, "What members wrote, newest first. Who wrote it isn't shown.", title = true)
        },
        action = {
            Spacer(Modifier.height(12.dp))
            Secondary("See who tested it", Modifier.fillMaxWidth(), onClick = actions.onMembers)
        },
    )
}

/**
 * It is over, and this is what it was: what came of the run and what it still asks, the reader's
 * app, their nights and the other apps. Once the details are cleared, the result and the apps stay.
 */
@Composable
fun CrewRecordPhase(
    crew: Crew,
    record: CrewRecord?,
    failed: Boolean,
    busy: RecordBusy?,
    onRetry: () -> Unit,
    onAnswer: (Boolean) -> Unit,
    onClaim: () -> Unit,
    onMembers: () -> Unit,
    /** Opens another crew's page, for an app moved there; null hides the way there. */
    onOpenCrew: ((String) -> Unit)? = null,
    /** Whether an app is on this phone, read fresh enough to redraw when it changes. */
    installed: (String) -> Boolean = { false },
    /** This crew's Uninstall all run, if one has started. */
    run: UninstallRunState? = null,
    uninstall: UninstallActions = UninstallActions(),
    now: Instant = Instant.now(),
) {
    if (record == null) {
        if (failed) RobotOffline("This crew didn't load", onRetry = onRetry) else RecordPageLoading()
        return
    }
    val pruned = record.pruned || crew.pruned
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = ListTop, bottom = Gutter + dockRoom())) {
        block { RecordHero(record, crew.detailsUntil, pruned, busy, onAnswer, onClaim, onOpenCrew, now) }
        if (!record.ran()) return@LazyColumn
        item { CrewHeading("Your app") }
        block { YourAppRow(record, if (pruned) null else onMembers) }
        if (record.nights.isNotEmpty() || nightCountsOf(record).isNotEmpty()) {
            item { CrewHeading("Your nights") }
            block { NightsCard(record, showStrip = !pruned) }
        }
        // The apps are kept with the record when the details are cleared, so this stays after it.
        if (record.crewApps.isNotEmpty()) {
            item { CrewHeading("Apps", "${record.crewApps.size}") }
            block {
                UninstallAllSection(
                    apps = record.crewApps,
                    crewName = record.crewName,
                    opens = uninstallOpensLine(record, now),
                    installed = installed,
                    run = run?.takeIf { it.crewId == crew.id },
                    actions = uninstall,
                )
            }
        }
        if (pruned) block { ClearedNote(clearedOn(crew.detailsUntil), keptDays(record.endedAt, crew.detailsUntil)) }
    }
}

/** What came back for a seat that left before its run, as the server says it was paid. */
internal fun seatRefundLine(
    seat: CrewSeat,
    after: String = "",
): String =
    when {
        seat.refunded > 0 -> "Your ${seat.refunded} tix are back.$after"
        seat.charged == 0 -> "It was placed free by the TestTrack team, so there was nothing to refund.$after"
        seat.charged > 0 -> "Your ${seat.charged} tix are on their way back.$after"
        // An older server that does not say: the usual price, as before.
        else -> "Your ${CrewRules.SEAT_COST} tix are back.$after"
    }

/** What a seat out of a running crew came to: refunded only before its run began. */
internal fun seatOutLine(seat: CrewSeat): String =
    when {
        seat.refunded > 0 -> "Its ${seat.refunded} tix were refunded."
        seat.charged == 0 -> "It was placed free by the TestTrack team, so there was nothing to refund."
        else -> "Seats are kept once a run starts, so this one's tix aren't refunded."
    }
