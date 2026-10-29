package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.CrewPicturesViewModel
import com.eazyverse.testtrack.viewmodel.MemberNights

/**
 * Every night, from the crew's members page: each member down the side, each night across, and
 * in every cell the picture they took of your app that night, as the August app's owner grid had.
 *
 * Shapes as well as colours say what a cell is: a picture, a picture with a yellow edge for one
 * picked by hand, a cross for a finished night they missed, a clock for tonight not done yet or a
 * picture on its way, grey for a night they did not owe, and a dashed outline for nights still to
 * come. A tap on a picture opens it whole.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrewPicturesPage(
    vm: CrewPicturesViewModel,
    crewId: String,
    crewName: String,
    onBack: () -> Unit,
) {
    LaunchedEffect(crewId) { vm.load(crewId) }
    var open by remember { mutableStateOf<NightPicture?>(null) }
    val pictures = vm.pictures
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { PageBar("Every night", onBack) },
    ) { padding ->
        when {
            pictures == null && vm.failed != null ->
                Box(Modifier.fillMaxSize().padding(padding)) { RobotOffline("The pictures didn't load", onRetry = { vm.load(crewId) }) }
            pictures == null -> Box(Modifier.fillMaxSize().padding(padding)) { CrewLobbyLoading() }
            else ->
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = Gutter + dockRoom()),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    item {
                        Text(
                            "${pictures.appName} in $crewName. Each member's picture of it, night by night. Swipe a row for every night.",
                            Modifier.padding(bottom = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Tt.colors.text3,
                        )
                    }
                    item { TrackingGrid(pictures) }
                    item {
                        Text(
                            "Each member's pictures",
                            Modifier.padding(top = 12.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(pictures.testers, key = { it.seatId }) { tester ->
                        TesterNights(tester, pictures.runDays, pictures.offDays) { open = it.copy(app = pictures.appName) }
                    }
                    item { Legend() }
                }
        }
    }
    open?.let { PictureViewer(it) { open = null } }
}

/** One member: their name, and their nights as a row of cells that scrolls on its own. */
@Composable
private fun TesterNights(
    tester: MemberNights,
    runDays: Int,
    offDays: Set<Int>,
    onOpen: (NightPicture) -> Unit,
) {
    val t = Tt.colors
    val nights = tester.nights.associateBy { it.day }
    CrewCard(padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tester.name,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val counted = tester.nights.count { it.counts }
            StatusChip(
                when {
                    tester.out -> "Out"
                    tester.excused -> "Reported it"
                    else -> "$counted of ${tester.day.coerceIn(0, runDays)} nights"
                },
                if (tester.out || tester.excused) Tone.QUIET else Tone.OK,
            )
        }
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (day in 1..runDays) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val night = nights[day]
                    when {
                        night != null && night.proof.isNotBlank() ->
                            PictureThumb(night.proof, night.byHand, CELL) {
                                onOpen(NightPicture(night.proof, tester.name, day, night.at, night.usedMs, night.byHand, ""))
                            }
                        night != null && night.counts -> Mark(t.okBg, null, "Opened")
                        day < tester.firstNight -> Mark(Color.Transparent, null, "Not joined yet", dashed = true)
                        // Opened, its picture still on its way: not a miss.
                        // Hours later and still on their phone: red, until it lands.
                        night != null && night.waitingLong() ->
                            Mark(
                                t.badBg,
                                Lucide.Clock,
                                "Picture waiting for hours",
                                border = t.badBorder,
                            )
                        night != null -> Mark(t.accent.copy(alpha = 0.35f), Lucide.Clock, "Picture on its way", border = t.accent)
                        tester.notOwed(day, offDays) -> Mark(t.glass2, null, "Not owed", border = t.glassBorder)
                        day == tester.day -> Mark(Color.Transparent, Lucide.Clock, "Tonight", border = t.accent)
                        day < tester.day -> Mark(t.badBg, Lucide.X, "Missed", border = t.badBorder)
                        else -> Mark(Color.Transparent, null, "To come", dashed = true)
                    }
                    Text("$day", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = t.text4)
                }
            }
        }
    }
}

private val CELL = 40.dp

/** A cell without a picture, in the shape of one. */
@Composable
private fun Mark(
    fill: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    said: String,
    border: Color = Tt.colors.glassBorder,
    dashed: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .size(CELL, CELL * 4 / 3)
            .clip(shape)
            .background(fill)
            .border(if (dashed) 1.dp else 1.5.dp, if (dashed) Tt.colors.glassBorder.copy(alpha = 0.6f) else border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, said, Modifier.size(14.dp), tint = border)
    }
}

@Composable
private fun Legend() {
    val t = Tt.colors
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf("Yellow edge: picked by hand", "Cross: missed", "Dashes: to come").forEachIndexed { i, text ->
            if (i > 0) Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = t.text4)
        }
    }
}
