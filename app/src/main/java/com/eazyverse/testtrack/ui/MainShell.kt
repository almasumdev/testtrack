package com.eazyverse.testtrack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.composables.icons.lucide.LayoutGrid
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Trophy
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.data.Session

/**
 * The five places the app has.
 *
 * Order is deliberate and not alphabetical: the two you open the app for are on the left, the two
 * about you are on the right, and the one in the middle is what the other four add up to.
 *
 * Circle is a place; the composables behind it are still named for the feed they draw, because a
 * `CircleCard` sitting in a Compose file next to `CircleShape` would read as a round one. The
 * split is on purpose: the tab names the room, the types name the data in it.
 *
 * Its icon carries weight it would not normally have to. Circle and Groups are near neighbours in
 * meaning, so the speech bubble against the pair of people is doing most of the work of saying
 * which is the whole community and which is your own fourteen.
 */
enum class MainTab(
    val label: String,
    val icon: ImageVector,
) {
    CIRCLE("Circle", Lucide.MessageSquare),
    GROUPS("Crew", Lucide.Users),
    RANKS("Ranks", Lucide.Trophy),
    APPS("My apps", Lucide.LayoutGrid),
    PROFILE("Profile", Lucide.User),
}

/**
 * One screen with five faces, under [com.eazyverse.testtrack.Routes.HOME].
 *
 * Tabs rather than nav destinations on purpose. Everything here is a top level place with no
 * meaningful back stack between them, and putting five of those on the controller means five
 * entries piling up behind whoever taps around before doing anything. The pushed screens still go
 * through the controller, which is what it is good at: a group, an app's dashboard, the submit
 * form, setup.
 *
 * Two of the tabs are stubs today, and the existing screens have been slotted in unchanged rather
 * than rebuilt to match Circle. That is the intended state of this step: Circle is the thing being
 * designed, and rebuilding the others against a layout still moving would mean doing it twice.
 */
@Composable
fun MainShell(
    onOpenGroup: (String, String) -> Unit,
    onSubmitApp: () -> Unit,
    onOpenSetup: () -> Unit,
    onSignOut: () -> Unit,
    /**
     * The way to a name, from whichever tab needed one.
     *
     * The app opens here now rather than at sign-in, so a reader can arrive on any of these five
     * tabs without an account. Three of them answer perfectly well that way; the two that cannot
     * ask, and this is what they call.
     */
    onSignIn: () -> Unit = {},
    homeVm: HomeViewModel = viewModel(),
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(MainTab.CIRCLE) }

    /** Where Back from the profile goes, so it lands where you actually came from. */
    var cameFrom by rememberSaveable { mutableStateOf(MainTab.CIRCLE) }

    val show: (MainTab) -> Unit = { next ->
        if (next != tab) cameFrom = tab
        tab = next
    }

    /*
     * Home's own work, lifted up here.
     *
     * All of it used to sit inside HomeScreen, which was the whole of this route and so was always
     * composed. It is not any more: a tester reading the feed or their profile would otherwise not
     * be registered for push, would not catch up a round the service left half finished, and would
     * not see a notice about being dropped from a cohort until they happened to tap Groups. None
     * of these are about the group list; they are about the app being open.
     */
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    homeVm.load()
                    Session.refreshNotifications(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keyed on the group list, so placement into a new cohort subscribes to it without waiting for
    // the next cold start.
    LaunchedEffect(homeVm.ready, homeVm.groups) { if (homeVm.ready) homeVm.registerForPush(context) }

    LaunchedEffect(Unit) { homeVm.catchUp(context) }

    // Anything a background sweep parked while the app was shut.
    LaunchedEffect(homeVm.ready) { if (homeVm.ready) homeVm.showNotices(context) }

    // Whatever this account's own apps look like installed here, if any are — see
    // HomeViewModel.syncMyVersions for why only a Play-sourced install counts.
    LaunchedEffect(homeVm.ready) { if (homeVm.ready) homeVm.syncMyVersions(context) }

    // Back from a tab returns to the first one rather than closing the app, which is what a bottom
    // bar has taught everybody to expect. Off on the first tab, so Back there still leaves.
    BackHandler(enabled = tab != MainTab.CIRCLE) { show(MainTab.CIRCLE) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { MainBar(tab, show) },
    ) { padding ->
        // Only the bottom inset is taken. Each tab brings its own app bar and handles its own top,
        // and passing the whole thing down would push every one of them below a gap nothing fills.
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                MainTab.CIRCLE -> FeedScreen()

                // The new crew screens, running on in-memory state. HomeScreen and the group
                // screens it pushes are still on disk and still work; nothing points at them from
                // the bar while this is being built. See docs/crew.md.
                MainTab.GROUPS -> CrewScreen()

                MainTab.RANKS -> RanksScreen()

                MainTab.APPS -> AppsScreen(onSignIn = onSignIn)

                MainTab.PROFILE -> MeScreen(onSignOut = onSignOut, onSignIn = onSignIn)
            }
        }
    }
}

/**
 * The bar itself.
 *
 * Painted in the page background with a hairline over it, rather than Material's own tonal
 * surface. The app has exactly one raised colour and it is the accent; a bar in a grey nothing
 * else here is painted in reads as a piece of another product bolted to the bottom, which is the
 * same reason [Ask] takes over the dialog container.
 *
 * Built by hand rather than from [NavigationBar], because the mark for the selected tab is a rail
 * along the top edge of the item and Material's own is a pill behind the icon. There is no way to
 * move that pill, so using the component would have meant hiding its indicator and drawing over
 * it, which is more code than the row below and leaves a component fighting its own defaults.
 *
 * What the pill was doing wrong is worth keeping written down. It was filled in `primaryContainer`
 * on a `background` bar, two colours three percent apart in lightness, so the selected tab was
 * marked by a smudge nobody could see; and its icon took `onPrimaryContainer` while its label took
 * `primary`, so one tab wore two different accents. A rail has no fill to get wrong, and both
 * halves of the selected item are now the one colour.
 */
@Composable
private fun MainBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .height(BarHeight),
        ) {
            MainTab.entries.forEach { tab ->
                MainBarItem(tab, tab == selected) { onSelect(tab) }
            }
        }
    }
}

/** Material's own bar height, kept so the rest of the layout is unchanged by the rewrite. */
private val BarHeight = 80.dp

@Composable
private fun RowScope.MainBarItem(
    tab: MainTab,
    current: Boolean,
    onSelect: () -> Unit,
) {
    val tint =
        if (current) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            // `selectable` rather than `clickable`: it carries the selected state into the
            // accessibility tree, which a coloured icon on its own does not, and Tab is the role
            // a screen reader announces for one of a set of five.
            .selectable(
                selected = current,
                onClick = onSelect,
                role = Role.Tab,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (current) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .size(width = 30.dp, height = 3.dp)
                    .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(tab.icon, null, Modifier.size(22.dp), tint = tint)
            Spacer(Modifier.height(5.dp))
            Text(
                tab.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                color = tint,
                maxLines = 1,
            )
        }
    }
}
