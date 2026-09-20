package com.eazyverse.testtrack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.eazyverse.testtrack.viewmodel.HudViewModel

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
    homeVm: HomeViewModel = viewModel(),
    hudVm: HudViewModel = viewModel(),
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(MainTab.CIRCLE) }

    /** Where Back from the profile goes, so it lands where you actually came from. */
    var cameFrom by rememberSaveable { mutableStateOf(MainTab.CIRCLE) }

    /**
     * Whether the sign-in sheet is up.
     *
     * Held here rather than by a screen, because it is asked for from the header, which is above
     * all five of them, and from two of the tabs themselves. One sheet, one place it is opened.
     */
    var asking by rememberSaveable { mutableStateOf(false) }
    val onSignIn: () -> Unit = { asking = true }

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

    // Read again on every arrival at the shell, and on every return to it. Tix move while somebody
    // is inside another app being tested, so coming back to a stale balance would be the screen
    // telling them their last ten seconds did not count.
    LaunchedEffect(tab) { hudVm.load(context) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watcher =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) hudVm.load(context)
            }
        owner.lifecycle.addObserver(watcher)
        onDispose { owner.lifecycle.removeObserver(watcher) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Above all five tabs rather than inside any of them, because it belongs to the account
        // and not to a screen. The two tabbed screens keep their own controls and now sit under
        // this rather than beside it.
        topBar = {
            TopHud(
                level = hudVm.level,
                intoLevel = hudVm.intoLevel,
                forNext = hudVm.forNext,
                tix = hudVm.tix,
                openCrew = hudVm.openCrew,
                openTaken = hudVm.openTaken,
                openSeats = hudVm.openSeats,
                onSignIn = onSignIn,
            )
        },
        bottomBar = { MainBar(tab, show) },
    ) { padding ->
        // The top inset is taken now that the shell has a bar of its own; each tab still handles
        // whatever it puts under it.
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                ),
        ) {
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

    if (asking) {
        SignInSheet(
            onDismiss = { asking = false },
            onSignedIn = {
                asking = false
                // Everything on screen was drawn for somebody without a name. The header is the
                // one that would otherwise sit there telling them to sign in after they have.
                hudVm.load(context)
            },
        )
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
 * Built by hand rather than from Material's NavigationBar, because the mark for the selected tab
 * is a rail along the top edge and Material's own is a pill behind the icon. There is no way to
 * move that pill, so using the component would have meant hiding its indicator and drawing over
 * it, which is more code than this and leaves a component fighting its own defaults.
 *
 * What the pill was doing wrong is worth keeping written down. It was filled in `primaryContainer`
 * on a `background` bar, two colours three percent apart in lightness, so the selected tab was
 * marked by a smudge nobody could see; and its icon took `onPrimaryContainer` while its label took
 * `primary`, so one tab wore two different accents.
 *
 * The rail is drawn here rather than inside the selected item, which is the whole reason the bar
 * needs its own width. One rail that slides is a bar with five places in it; a rail per item that
 * appears and disappears is five separate things blinking, and it read as a glitch rather than as
 * a movement.
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
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .height(BarHeight),
        ) {
            val slot = maxWidth / MainTab.entries.size
            val index = MainTab.entries.indexOf(selected)
            val railX by animateDpAsState(
                targetValue = slot * index + (slot - RailWidth) / 2,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                label = "rail",
            )

            Row(Modifier.fillMaxSize()) {
                MainTab.entries.forEach { tab ->
                    MainBarItem(tab, tab == selected) { onSelect(tab) }
                }
            }

            Box(
                Modifier
                    .offset(x = railX)
                    .size(width = RailWidth, height = RailHeight)
                    .clip(RoundedCornerShape(bottomStart = RailHeight, bottomEnd = RailHeight))
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/*
 * Shorter than Material's eighty.
 *
 * That height is built for a bar with a filled pill behind the selected icon, and there is no pill
 * here: a rail along the top edge needs no room of its own. What the eighty was buying was space
 * around a shape that is gone, and on a phone the bar is the one piece of furniture that is on
 * screen the whole time, so what it does not need it should not take from the content above it.
 */
private val BarHeight = 64.dp
private val RailWidth = 30.dp
private val RailHeight = 3.dp

/** Wide enough to take a thumb, and the reason the touch target is not the ripple. */
private val TapSize = 36.dp

@Composable
private fun RowScope.MainBarItem(
    tab: MainTab,
    current: Boolean,
    onSelect: () -> Unit,
) {
    // Crossfaded rather than switched. The rail takes a quarter of a second to arrive and a colour
    // that changed instantly would beat it there, which is what made the old switch look broken:
    // two halves of one movement happening at different times.
    val tint by animateColorAsState(
        targetValue =
            if (current) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        animationSpec = tween(durationMillis = 220),
        label = "tint",
    )

    /*
     * The press and what it draws are deliberately on different elements.
     *
     * The whole item is the touch target, because a thumb is wider than an icon and a tab that
     * only answers a direct hit is a tab people miss. But letting the ripple fill that target
     * flashes a rectangle nearly the height of the bar, square-cornered and running into its
     * neighbours, which is the odd thing you see on a tap. Handing the same interaction source to
     * a small round patch behind the icon keeps the large target and draws the circle somebody
     * expects at the place they actually aimed.
     */
    val press = remember { MutableInteractionSource() }

    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            // `selectable` rather than `clickable`: it carries the selected state into the
            // accessibility tree, which a coloured icon on its own does not, and Tab is the role
            // a screen reader announces for one of a set of five.
            .selectable(
                selected = current,
                interactionSource = press,
                indication = null,
                role = Role.Tab,
                onClick = onSelect,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(TapSize)
                    .indication(press, ripple(bounded = false, radius = TapSize / 2)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(tab.icon, null, Modifier.size(21.dp), tint = tint)
            }
            Text(
                tab.label,
                style = MaterialTheme.typography.labelSmall,
                // One weight, for the same reason the tabs above have one: semi bold is
                // wider than medium, so a weight that moved with the selection re-measured the
                // word and nudged the icon above it off centre at the moment of the tap.
                fontWeight = FontWeight.Medium,
                color = tint,
                maxLines = 1,
            )
        }
    }
}
