package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.PrivateState
import com.eazyverse.testtrack.viewmodel.PrivateTest
import com.eazyverse.testtrack.viewmodel.PrivateTestsViewModel

/*
 * The reader's private tests on the Run tab, under their runs: an app tile each, with one way to
 * open it. Never part of a crew's night: not in tonight's count, Open all or the tab's badge.
 */

/** What the Run tab needs to draw the reader's private tests. */
class PrivateRunSection(
    val tests: List<PrivateTest> = emptyList(),
    val isInstalled: (String) -> Boolean = { true },
    val onOpen: (PrivateTest) -> Unit = {},
    val onInstall: (PrivateTest) -> Unit = {},
    /** The test's own page. */
    val onPage: (PrivateTest) -> Unit = {},
)

/** A private test opened through the round, as a crew's app is: no picture, its own ask. */
internal fun PrivateTest.asCrewApp() =
    CrewApp(seatId, appName, packageName, ownerName, crewName, day = day, privateTestId = id, notes = notes)

/** The section for the Crew tab, wired to the round and the testers group. */
@Composable
internal fun rememberPrivateRun(
    vm: CrewViewModel,
    privates: PrivateTestsViewModel,
    onPage: (String) -> Unit,
): PrivateRunSection {
    val context = LocalContext.current
    val membership = rememberMembership()
    return PrivateRunSection(
        tests = privates.mine.orEmpty().filter { it.state != PrivateState.INVITED },
        isInstalled = { vm.isInstalled(context, it) },
        onOpen = { test -> membership.require { vm.openApp(context, test.asCrewApp()) } },
        onInstall = { test -> membership.require { openOnPlay(context, test.packageName) } },
        onPage = { onPage(it.id) },
    )
}

/** The heading, a tile per test, and what testing here means. Nothing when there are none. */
internal fun LazyListScope.privateTesting(section: PrivateRunSection) {
    if (section.tests.isEmpty()) return
    item("private-heading") { CrewHeading("Private testing", "${section.tests.size}") }
    itemsIndexed(section.tests, key = { _, test -> "private-${test.id}" }) { index, test ->
        Box(Modifier.animateItem().padding(start = Gutter, end = Gutter, top = if (index > 0) 8.dp else 0.dp)) {
            PrivateTestTile(test, section)
        }
    }
    item("private-note") {
        IconLine(
            Lucide.Lock,
            Tt.colors.text4,
            AnnotatedString("No tix and no rules here. Only each app's owner sees when you open it."),
            TextStyles.DialogNote,
            Tt.colors.text4,
            Modifier.padding(start = Gutter, end = Gutter, top = 10.dp),
        )
    }
}

/** Where a private test stands tonight, said on its tile's band, with the one thing to do. */
internal data class PrivateBand(
    val icon: ImageVector,
    val text: String,
    val tone: Tone,
    val action: String? = null,
)

internal fun privateBandOf(
    test: PrivateTest,
    installed: Boolean,
): PrivateBand =
    when {
        test.state == PrivateState.ENDED ->
            PrivateBand(if (test.runOver) Lucide.Calendar else Lucide.LogOut, test.stoppedLine(), Tone.QUIET)
        test.day < 1 || test.day < test.firstNight -> PrivateBand(Lucide.Clock, "Starts at midnight, your time", Tone.QUIET)
        !installed -> PrivateBand(Lucide.Download, "Not installed", Tone.WARN, "Install")
        test.openedToday -> PrivateBand(Lucide.CircleCheck, "Opened tonight", Tone.OK)
        else -> PrivateBand(Lucide.Moon, "Not opened tonight", Tone.QUIET, "Open")
    }

/** One private test: the app, whose it is, and tonight on the band. The tile opens its page. */
@Composable
internal fun PrivateTestTile(
    test: PrivateTest,
    section: PrivateRunSection,
) {
    val band = privateBandOf(test, section.isInstalled(test.packageName))
    CrewCard(padding = PaddingValues(14.dp), onClick = { section.onPage(test) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppMark(test.packageName, test.appName, test.icon, size = 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(test.appName, style = AppTileName, color = Tt.colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "For ${test.ownerName}",
                    style = tileStyle(12f, 14f, FontWeight.Normal),
                    color = Tt.colors.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        StatusLine(
            band.icon,
            band.text,
            tone = band.tone,
            band = true,
            actions =
                band.action?.let { label ->
                    {
                        InlineAction(label) { if (label == "Install") section.onInstall(test) else section.onOpen(test) }
                    }
                },
        )
    }
}
