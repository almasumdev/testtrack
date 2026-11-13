package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Barlow
import com.eazyverse.testtrack.ui.theme.Tt

/** Tags the header's text column, so a test can hold it to the 40dp of the icon beside it. */
internal const val CREW_HEAD_TAG = "crew-open-head"

/**
 * The crew taking seats, and what taking one means.
 *
 * One card: the crew nearest to starting and when it likely starts, how full it is, and then either
 * what the account still needs before a seat (CrewJoinChecklist) or what a seat means with one way
 * in. When none is filling the card says a seat starts one, because that is what happens, and a tab
 * with nothing on it would read as there being nowhere to go.
 *
 * No roster here. Who is already in a crew you have not joined is a list of strangers, and the
 * server does not send it to anybody outside the crew anyway. What takes its place is the deal
 * itself, which is what somebody standing here is trying to work out.
 */
@Composable
fun CrewOpenTab(
    vm: CrewViewModel,
    actions: CrewActions,
    guide: CrewGuide = CrewGuide(),
) {
    val notificationsOff = rememberNotificationsOff()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 12.dp, bottom = Gutter + dockRoom()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The notification tile lives here, in the list, and only while it draws, so no empty item adds a gap.
        if (vm.running > 0 && notificationsOff) {
            item(key = "notifications") { TestingNotifications(Modifier.padding(horizontal = Gutter)) }
        }
        // A reminder, not a step: nothing stops a seat, but the first night needs both switches.
        if (!vm.firstLoad && !guide.join.signedOut && !guide.join.permissions) {
            item(key = "allow") {
                AskRow(
                    "Before your first night",
                    "Allow usage access and the timer",
                    guide.onAllow,
                    Modifier.padding(horizontal = Gutter),
                    icon = Lucide.Moon,
                    trailing = { LookOnlyPill("Allow", PillKind.SOFT, guide.onAllow) },
                )
            }
        }
        // The guide below is the app's own words and needs nothing from the server; only the box waits.
        item {
            LoadSwap(vm.firstLoad) { waiting ->
                if (waiting) {
                    CrewOpenCardLoading()
                    return@LoadSwap
                }
                Box(Modifier.padding(horizontal = Gutter)) { OpenCrewCard(vm, actions, guide) }
            }
        }

        crewGuide()
    }
}

/** The card: the crew and when it likely starts, its seats, then what is still missing, or the terms and the way in. */
@Composable
private fun OpenCrewCard(
    vm: CrewViewModel,
    actions: CrewActions,
    guide: CrewGuide,
) {
    val crew = vm.open
    CrewCard(padding = PaddingValues(16.dp)) {
        CrewOpenHeader(crew)
        Spacer(Modifier.height(14.dp))
        SeatCountTiles(filled = crew?.filled ?: 0, taken = crew?.taken ?: 0, icons = crew?.icons.orEmpty())
        Spacer(Modifier.height(14.dp))
        val steps = guide.join.missing
        when {
            vm.inOpen -> OpenStatusRow(ok = true, "You are in this crew", "See it in your Lobby", onClick = { vm.showTab(CrewPhase.LOBBY) })
            vm.blockedCrew != null ->
                OpenStatusRow(ok = false, "Your app waits in ${vm.blockedCrew}", "Take another seat once it is full")
            // An older server sends only its sentence, which can run long, so it gets three lines.
            vm.blocked != null -> OpenStatusRow(ok = false, "Your app is waiting", vm.blocked.orEmpty(), noteLines = 3)
            steps.isNotEmpty() ->
                CrewJoinChecklist(steps, guide.join.signedOut, guide.join.tix) { step ->
                    when (step) {
                        JoinStep.SIGN_IN -> guide.onSignIn()
                        JoinStep.GROUP -> guide.onJoin()
                        JoinStep.APP -> guide.onAddApp()
                        JoinStep.DRIVE -> guide.onDrive()
                        JoinStep.TIX -> guide.onCircle()
                    }
                }
            else -> {
                CrewSeatTerms()
                Spacer(Modifier.height(14.dp))
                FloatingActionBar(
                    label = if (crew != null) "Take a seat" else "Take a seat and start one",
                    onClick = actions.onTakeSeat,
                    icon = rememberVectorPainter(Lucide.Plus),
                    gutter = 0.dp,
                )
            }
        }
    }
}

private val EstimateStyle = tileStyle(12.5f, 14f, FontWeight.Medium)
private val QuietStyle = tileStyle(12.5f, 14f, FontWeight.Normal)

/** Icon tile, then the crew's name and seats on one line and when it likely starts under it: 19 + 2 + 14 = 35dp. */
@Composable
internal fun CrewOpenHeader(crew: Crew?) {
    val t = Tt.colors
    val estimate = crew?.let { startEstimate(it.likelyFullAt, it.likelyBasis) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(t.tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.Users, null, Modifier.size(20.dp), tint = t.tileInk)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).testTag(CREW_HEAD_TAG)) {
            // Only the name gives way to an ellipsis; the seats left are the answer and stay whole.
            Row {
                Text(
                    crew?.name ?: "No crew is filling",
                    Modifier.weight(1f, fill = false),
                    style = AppTileName,
                    color = t.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (crew != null) {
                    Text(
                        ", ${seatsLeftText(CrewRules.SEATS - crew.taken)}",
                        style = AppTileName,
                        color = t.text,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
            if (crew == null || estimate != null) Spacer(Modifier.height(2.dp))
            when {
                crew == null -> QuietLine("Your seat starts a new one")
                estimate == null -> Unit
                !estimate.known -> QuietLine(estimate.text)
                else ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.Clock, null, Modifier.size(11.dp), tint = t.accentInk)
                        Spacer(Modifier.width(4.dp))
                        Text(estimate.text, style = EstimateStyle, color = t.accentInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
            }
        }
    }
}

@Composable
private fun QuietLine(text: String) =
    Text(text, style = QuietStyle, color = Tt.colors.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)

private val TermsStyle = TextStyle(fontFamily = Barlow, fontSize = 13.sp, lineHeight = 18.sp)
private const val INFO_MARK = "info"

/**
 * What a seat costs and what comes back, in short lines. The info icon sits inline at the start of
 * the first one, so a wrapped line runs back to the left edge instead of hanging under the icon.
 */
@Composable
internal fun CrewSeatTerms(modifier: Modifier = Modifier) {
    val t = Tt.colors
    val bold = SpanStyle(fontWeight = FontWeight.SemiBold, color = t.text)
    // 16dp of icon and 6dp of gap, as one inline slot at the start of the text.
    val icon =
        mapOf(
            INFO_MARK to
                InlineTextContent(Placeholder(22.sp, 16.sp, PlaceholderVerticalAlign.TextCenter)) {
                    Box(Modifier.fillMaxSize()) {
                        Icon(Lucide.Info, null, Modifier.size(16.dp).align(Alignment.CenterStart), tint = t.accentInk)
                    }
                },
        )
    val cost = CrewRules.SEAT_COST
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            buildAnnotatedString {
                appendInlineContent(INFO_MARK, "Info")
                append("A seat costs ")
                withStyle(bold) { append("$cost tix") }
                append(". You get all $cost back in either case:")
            },
            style = TermsStyle,
            color = t.text2,
            inlineContent = icon,
        )
        TermsBullet(bold, "Leave early:", " take your app out before the crew is full.")
        TermsBullet(bold, "Finish the run:", " open every app, every night, until the run ends.")
        Text(
            buildAnnotatedString {
                withStyle(bold) { append("Start:") }
                append(" the run begins at midnight, your local time, after the last seat is taken.")
            },
            style = TermsStyle,
            color = t.text2,
        )
    }
}

/** A small indented row: a 4dp dot in the quiet ink, then the line. Lines up under the text, not the icon. */
@Composable
private fun TermsBullet(
    bold: SpanStyle,
    lead: String,
    rest: String,
) {
    val t = Tt.colors
    Row(Modifier.padding(start = 22.dp)) {
        // Centred on the first 18sp line: (18 - 4) / 2 from its top.
        Box(Modifier.padding(top = 7.dp).size(4.dp).background(t.text3, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(bold) { append(lead) }
                append(rest)
            },
            style = TermsStyle,
            color = t.text2,
        )
    }
}

/**
 * A hairline row in the card: a tick when you are in this crew (a tap goes to the Lobby), an
 * hourglass when your app waits elsewhere (nothing to tap). Two lines beside a 36dp tile.
 */
@Composable
private fun OpenStatusRow(
    ok: Boolean,
    title: String,
    note: String,
    onClick: (() -> Unit)? = null,
    noteLines: Int = 1,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(TileRadius)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, t.cardLine, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (ok) t.okBg else t.tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (ok) Lucide.Check else Lucide.Hourglass, null, Modifier.size(18.dp), tint = if (ok) t.ok else t.tileInk)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = tileStyle(14f, 19f, FontWeight.SemiBold), color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                note,
                style = tileStyle(12.5f, 15f, FontWeight.Normal),
                color = t.text3,
                maxLines = noteLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Spacer(Modifier.width(8.dp))
            Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = t.text4)
        }
    }
}

/** What the Open tab needs from outside the crew: what the account has, and what each step does. */
class CrewGuide(
    val join: CrewJoinState = CrewJoinState(),
    val onSignIn: () -> Unit = {},
    /** The testers group: its dialog. */
    val onJoin: () -> Unit = {},
    val onAddApp: () -> Unit = {},
    val onDrive: () -> Unit = {},
    /** Circle, where reviews earn tix. */
    val onCircle: () -> Unit = {},
    val onAllow: () -> Unit = {},
)
