package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Play
import com.eazyverse.testtrack.domain.pluralOf
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.PrivateState
import com.eazyverse.testtrack.viewmodel.PrivateTest
import com.eazyverse.testtrack.viewmodel.PrivateTestsViewModel

/*
 * One app the reader tests for its owner, opened from its tile: how it is going, the way to open
 * it, and the way to stop. Opening goes through the crew's round, timed the same, with no picture.
 */

/** The page's wiring: the ask from the private testers' model, the round from the Crew tab's. */
@Composable
fun PrivateTestScreen(
    crews: CrewViewModel,
    privates: PrivateTestsViewModel,
    id: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val membership = rememberMembership()
    val dialogs = rememberCrewDialogs()
    // Back from the app, the round records the open and the crews reload: read the asks again then.
    CrewLifecycle(crews)
    LaunchedEffect(crews.reloading) { if (!crews.reloading) privates.loadMine() }
    val test = privates.mine?.firstOrNull { it.id == id }
    var stopping by remember { mutableStateOf(false) }
    PrivateTestBody(
        test = test,
        loading = privates.mine == null,
        installed = test != null && crews.isInstalled(context, test.packageName),
        busy = privates.busy == id,
        onBack = onBack,
        onOpen = { membership.require { crews.openApp(context, it.asCrewApp()) } },
        onInstall = { membership.require { openOnPlay(context, it.packageName) } },
        onStop = { stopping = true },
    )
    CrewDialogsHost(crews, dialogs)
    if (stopping && test != null) {
        Ask(
            title = "Stop testing ${test.appName}?",
            body = "${test.ownerName.substringBefore(' ')} keeps the nights you opened. Only a new invite starts it again.",
            confirm = "Stop testing",
            dismiss = "Keep testing",
            destructive = true,
            onConfirm = {
                stopping = false
                privates.leave(test, onBack)
            },
            onDismiss = { stopping = false },
        )
    }
}

/** The page drawn from plain values, in every state it has. */
@Composable
fun PrivateTestBody(
    test: PrivateTest?,
    loading: Boolean,
    installed: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onOpen: (PrivateTest) -> Unit,
    onInstall: (PrivateTest) -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PageBar(
            test?.appName ?: "Private testing",
            onBack,
            subtitle = test?.let { "Private testing for ${it.ownerName}" },
            loading = test == null && loading,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                test == null && loading -> CrewCardLoading()
                test == null ->
                    RobotState(
                        Prop.Calendar,
                        "Not testing this now",
                        "The invite was answered or the test is over.",
                        action = "Back",
                        onAction = onBack,
                    )
                else -> PrivateTestDetails(test)
            }
            FootFade(Modifier.align(Alignment.BottomCenter))
        }
        if (test != null && test.state == PrivateState.ACCEPTED) {
            Column(Modifier.fillMaxWidth().snackFloor().padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 6.dp + dockRoom())) {
                if (installed) {
                    Primary("Open ${test.appName}", Modifier.fillMaxWidth(), mark = rememberVectorPainter(Lucide.Play)) { onOpen(test) }
                } else {
                    Primary("Install ${test.appName}", Modifier.fillMaxWidth(), mark = rememberVectorPainter(Lucide.Download)) {
                        onInstall(test)
                    }
                }
                InlineAction(
                    "Stop testing",
                    Modifier.padding(top = 6.dp),
                    busy = busy,
                    color = Tt.colors.bad,
                    underline = true,
                    fullWidth = true,
                    onClick = onStop,
                )
            }
        }
    }
}

@Composable
private fun PrivateTestDetails(test: PrivateTest) {
    val t = Tt.colors
    val ended = test.state == PrivateState.ENDED
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = Gutter, end = Gutter, top = 4.dp, bottom = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppMark(test.packageName, test.appName, test.icon, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(test.appName, style = AppTileName, color = t.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (test.packageName.isNotBlank()) {
                    Text(test.packageName, style = tileStyle(12f, 14f, FontWeight.Normal), color = t.text3, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        CrewCard(padding = PaddingValues(16.dp)) {
            when {
                ended -> StatusChip("Stopped", Tone.QUIET, if (test.runOver) Lucide.Calendar else Lucide.LogOut)
                test.openedToday -> StatusChip("Opened tonight", Tone.OK, Lucide.CircleCheck)
                else -> StatusChip("Not opened tonight", Tone.QUIET, Lucide.Moon)
            }
            Text(
                if (ended) "${test.stoppedLine()}." else test.openedLine(),
                Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = t.text2,
            )
            if (test.day > 0) {
                Spacer(Modifier.height(14.dp))
                DayMeter(
                    minOf(test.day, test.runDays),
                    done = test.openedToday,
                    from = test.firstNight.coerceAtLeast(1),
                    total = test.runDays,
                )
                MeterLabels(
                    "Night ${minOf(test.day, test.runDays)} of ${test.runDays}",
                    if (test.daysLeft > 0) "Ends in ${test.daysLeft} ${pluralOf(test.daysLeft, "day")}" else "Ends after tonight",
                )
            }
        }
        IconLine(
            Lucide.Info,
            t.text4,
            AnnotatedString("Opening counts after ${CrewRules.VISIT_SECONDS} seconds in front, as in a crew. No picture is taken."),
            tileStyle(12.5f, 17f, FontWeight.Normal),
            t.text4,
            Modifier.padding(top = 12.dp),
        )
    }
}
