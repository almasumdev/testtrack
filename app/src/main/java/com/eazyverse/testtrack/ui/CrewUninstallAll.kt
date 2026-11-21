package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Trash2
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * Uninstall all on a finished crew's Apps list: one card over the rows, each row with its own
 * Uninstall. Stateless: it draws the values it is given and says what was tapped. The run, the
 * dialogs and the waiting are viewmodel/UninstallRun.
 */

/** What the card and its rows can do. */
class UninstallActions(
    /** Uninstall all, or the rest: the packages still on this phone, in order. */
    val onAll: (List<String>) -> Unit = {},
    val onOne: (String) -> Unit = {},
    val onStop: () -> Unit = {},
    val onNext: () -> Unit = {},
)

/** The card's title is 15.5 on 19; a row's name is 15 on 19 with 12.5 on 16 under both. */
private val CardTitle = tileStyle(15.5f, 19f, FontWeight.SemiBold)
private val RowName = tileStyle(15f, 19f, FontWeight.SemiBold)
private val RowLine = tileStyle(12.5f, 16f, FontWeight.Normal)

/**
 * The card, then the rows. [opens] is "Opens Nov 24, in 2 days" while it is locked and null once
 * it is open; [run] is this crew's run, if one has started.
 */
@Composable
internal fun UninstallAllSection(
    apps: List<RecordApp>,
    crewName: String,
    opens: String?,
    installed: (String) -> Boolean,
    run: UninstallRunState?,
    actions: UninstallActions,
) {
    val rows = uninstallRows(apps, opens == null, installed, run)
    val left = rows.live.count { it.state != UninstallRowState.REMOVED }
    val card = uninstallCard(opens == null, run, left)
    val names = apps.associate { it.packageName to it.name }
    Column {
        UninstallCardBox(
            card = card,
            left = left,
            run = run,
            opens = opens,
            crewName = crewName,
            names = names,
            onAll = { actions.onAll(rows.live.filter { it.state != UninstallRowState.REMOVED }.map { it.app.packageName }) },
            actions = actions,
        )
        UninstallList(rows, actions.onOne)
    }
}

@Composable
private fun UninstallCardBox(
    card: UninstallCard,
    left: Int,
    run: UninstallRunState?,
    opens: String?,
    crewName: String,
    names: Map<String, String>,
    onAll: () -> Unit,
    actions: UninstallActions,
) {
    val t = Tt.colors
    val trash = rememberVectorPainter(Lucide.Trash2)
    val next = rememberVectorPainter(Lucide.ArrowRight)
    val done = run?.removed?.size ?: 0
    val total = run?.total ?: left
    CrewCard(padding = PaddingValues(14.dp)) {
        when (card) {
            UninstallCard.LOCKED -> {
                CardTop(Lucide.Lock, "$left ${appsWord(left)} on this phone", opens.orEmpty(), info = LOCKED_INFO)
                Spacer(Modifier.height(12.dp))
                Primary("Uninstall all ($left)", enabled = false, mark = rememberVectorPainter(Lucide.Lock)) {}
            }
            UninstallCard.READY -> {
                CardTop(Lucide.Trash2, "$left ${appsWord(left)} on this phone", "Android asks before each one")
                Spacer(Modifier.height(12.dp))
                Primary("Uninstall all ($left)", mark = trash, onClick = onAll)
            }
            UninstallCard.RUNNING -> {
                CardTop(Lucide.Trash2, "Removing apps", "$done of $total removed", sub = t.accentInk)
                run?.let { Meter(it) }
                Spacer(Modifier.height(12.dp))
                Secondary("Stop", Modifier.fillMaxWidth(), onClick = actions.onStop)
            }
            UninstallCard.KEPT_ONE -> {
                CardTop(Lucide.Trash2, "You kept ${names[run?.current].orEmpty().ifBlank { "this app" }}", "$done of $total removed")
                run?.let { Meter(it) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Secondary("Stop", Modifier.weight(1f), onClick = actions.onStop)
                    Primary("Next app", Modifier.weight(1f), mark = next, onClick = actions.onNext)
                }
            }
            UninstallCard.PARTIAL -> {
                CardTop(Lucide.Trash2, "$done removed, $left still here", "You kept some, or stopped")
                run?.let { Meter(it) }
                Spacer(Modifier.height(12.dp))
                Primary("Uninstall the rest ($left)", mark = trash, onClick = onAll)
            }
            UninstallCard.ALL_REMOVED ->
                CardDone(
                    Prop.Trophy,
                    "All $done ${appsWord(done)} removed",
                    "Nothing from ${crewName.ifBlank { "this crew" }} is left on this phone.",
                )
            UninstallCard.NONE_LEFT ->
                CardDone(Prop.Phone, "None left on this phone", "Every app from this crew is already gone.")
        }
    }
}

private const val LOCKED_INFO =
    "Opens ${CrewRules.UNINSTALL_AFTER_DAYS} days after the whole crew has finished. Until then, keep the apps on this phone."

private fun appsWord(n: Int) = if (n == 1) "app" else "apps"

/** The icon tile, two lines, and an optional (i) at the end. */
@Composable
private fun CardTop(
    icon: ImageVector,
    title: String,
    line: String,
    sub: Color = Color.Unspecified,
    info: String? = null,
) {
    val t = Tt.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, size = 40.dp, glyph = 20.dp, corner = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = CardTitle, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(
                line,
                style = if (sub == Color.Unspecified) RowLine else RowLine.copy(fontWeight = FontWeight.SemiBold),
                color = if (sub == Color.Unspecified) t.text3 else sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (info != null) {
            InfoDot("Why it is locked", t.text4, glyph = 18.dp, slot = 32.dp) { CalloutLine(AnnotatedString(info)) }
        }
    }
}

/** A square per app in the run, the way the night strip is drawn. */
@Composable
private fun Meter(run: UninstallRunState) {
    val t = Tt.colors
    val marks = uninstallMarks(run)
    Spacer(Modifier.height(12.dp))
    SegmentMeter(marks.size) { i ->
        when (marks[i]) {
            UninstallMark.REMOVED -> Status.posted
            UninstallMark.NOW -> t.accent.copy(alpha = 0.5f)
            UninstallMark.KEPT -> t.glassBorderStrong
            UninstallMark.TODO -> t.track
        }
    }
}

@Composable
private fun CardDone(
    prop: Prop,
    title: String,
    line: String,
) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RobotHolding(prop, size = 58.dp, description = title)
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = tileStyle(17f, 22f, FontWeight.Bold),
            color = Status.posted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Text(line, style = TextStyles.PageLine, color = Tt.colors.text3, textAlign = TextAlign.Center)
    }
}

@Composable
private fun UninstallList(
    rows: UninstallRows,
    onOne: (String) -> Unit,
) {
    val t = Tt.colors
    if (rows.live.isEmpty() && rows.gone.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    CrewCard(padding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)) {
        var before: UninstallRowState? = null
        rows.live.forEach { row ->
            if (before != null && before != UninstallRowState.ASKING && row.state != UninstallRowState.ASKING) Line()
            UninstallAppRow(row, onOne)
            before = row.state
        }
        if (rows.live.isNotEmpty() && rows.gone.isNotEmpty()) {
            Hairline(start = 8.dp)
            Text(
                "Already gone, not counted",
                Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp),
                style = tileStyle(12f, 16f, FontWeight.Bold),
                color = t.text4,
            )
            before = null
        }
        rows.gone.forEach { row ->
            if (before != null) Line()
            UninstallAppRow(row, onOne)
            before = row.state
        }
    }
}

/** The hairline between rows, starting under the words rather than under the icon. */
@Composable
private fun Line() {
    Hairline(start = 60.dp)
}

@Composable
private fun Hairline(start: Dp) {
    Box(
        Modifier
            .padding(start = start, end = 8.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(Tt.colors.cardLine),
    )
}

@Composable
private fun UninstallAppRow(
    row: UninstallRow,
    onOne: (String) -> Unit,
) {
    val t = Tt.colors
    val app = row.app
    val faded = row.state == UninstallRowState.REMOVED || row.state == UninstallRowState.EARLIER
    val asking = row.state == UninstallRowState.ASKING
    val (line, lineInk) =
        when (row.state) {
            UninstallRowState.ASKING -> "Android is asking" to t.accentInk
            UninstallRowState.REMOVED -> "Removed" to Status.posted
            UninstallRowState.EARLIER -> "Not on this phone" to t.text3
            UninstallRowState.KEPT -> "You kept it" to t.text3
            else -> (if (app.left) "Left the crew" else app.packageName) to t.text3
        }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (asking) Modifier.clip(RoundedCornerShape(10.dp)).background(t.leafAccentSoft) else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(Modifier.size(40.dp).alpha(if (faded) 0.45f else 1f)) {
                AppMark(app.packageName, app.name, app.icon, size = 40.dp)
            }
            if (row.state == UninstallRowState.REMOVED) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(4.dp, 4.dp)
                        .size(16.dp)
                        .background(t.bg, CircleShape)
                        .padding(2.dp)
                        .background(Status.posted, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Check, null, Modifier.size(10.dp), tint = t.inkOn(Status.posted))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.name,
                style = RowName,
                color = t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(if (faded) 0.45f else 1f),
            )
            Spacer(Modifier.height(1.dp))
            Text(
                line,
                style = if (lineInk == t.text3) RowLine else RowLine.copy(fontWeight = FontWeight.SemiBold),
                color = lineInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (row.state) {
            UninstallRowState.OPEN, UninstallRowState.KEPT ->
                RowPill("Uninstall", PillKind.SOFT, onClick = { onOne(app.packageName) })
            UninstallRowState.ASKING ->
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(16.dp)
                        .background(t.accent.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(8.dp).background(t.accent, CircleShape))
                }
            else -> Unit
        }
    }
}

/** The card and three rows as shapes, while the record is on its way. */
@Composable
internal fun UninstallAllLoading() {
    CrewCard(padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(width = 40.dp, height = 40.dp, corner = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Skeleton(width = 150.dp, height = 16.dp)
                Spacer(Modifier.height(6.dp))
                Skeleton(width = 190.dp, height = 12.dp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Skeleton(height = 52.dp, corner = 26.dp)
    }
    Spacer(Modifier.height(8.dp))
    CrewCard(padding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)) {
        repeat(3) {
            Row(Modifier.heightIn(min = 60.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Skeleton(width = 40.dp, height = 40.dp, corner = 10.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Skeleton(width = 110.dp, height = 15.dp)
                    Spacer(Modifier.height(6.dp))
                    Skeleton(width = 150.dp, height = 11.dp)
                }
                Skeleton(width = 86.dp, height = 34.dp, corner = 17.dp)
            }
        }
    }
}
