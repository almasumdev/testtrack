package com.eazyverse.testtrack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    onAskForFeedback: () -> Unit,
    onReview: (String) -> Unit,
    onSignOut: () -> Unit,
    onCheck: (String) -> Unit = {},
    /** Set by a tapped crew notification; cleared once the Crew tab is showing. */
    openCrew: MutableState<Boolean> = remember { mutableStateOf(false) },
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
    var asking by remember { mutableStateOf<SignInReason?>(null) }

    val show: (MainTab) -> Unit = { next ->
        if (next != tab) cameFrom = tab
        tab = next
    }

    LaunchedEffect(openCrew.value) {
        if (openCrew.value) {
            show(MainTab.GROUPS)
            openCrew.value = false
        }
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
    LaunchedEffect(tab, hudVm.signedOut) { hudVm.load(context) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val watcher =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) hudVm.load(context)
            }
        owner.lifecycle.addObserver(watcher)
        onDispose { owner.lifecycle.removeObserver(watcher) }
    }

    val density = LocalDensity.current
    var dockHeight by remember { mutableStateOf(0.dp) }
    Backdrop(scene = tab.scene) {
        Scaffold(
            // The status bar and the sides only. The bottom is left to the dock, which lifts itself
            // over the navigation bar, so the page can run under both.
            modifier =
                Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                ),
            // Clear, so the exchange-pattern ground under the whole shell shows through; see [Backdrop].
            containerColor = Color.Transparent,
            // Above all five tabs rather than inside any of them, because it belongs to the account
            // and not to a screen. The two tabbed screens keep their own controls and now sit under
            // this rather than beside it.
            topBar = {
                TopHud(
                    signedIn = !hudVm.signedOut,
                    level = hudVm.level,
                    intoLevel = hudVm.intoLevel,
                    forNext = hudVm.forNext,
                    tix = hudVm.tix,
                    openCrew = hudVm.openCrew,
                    openTaken = hudVm.openTaken,
                    openSeats = hudVm.openSeats,
                    onSignIn = { asking = StartReason },
                )
            },
            // No bottom bar slot: the dock floats over the page below, and the page runs to the
            // bottom edge under it and under the system bar. Lists make room with [dockRoom].
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
            ) {
                CompositionLocalProvider(LocalDockRoom provides dockHeight) {
                    // Fade through rather than cut or cross-fade: the old page goes first, then the new
                    // one comes in, so two pages' tabs and headings never sit on top of each other.
                    AnimatedContent(
                        tab,
                        transitionSpec = { fadeIn(tween(160, delayMillis = 80)) togetherWith fadeOut(tween(80)) },
                        label = "page",
                    ) { page ->
                        when (page) {
                            MainTab.CIRCLE ->
                                FeedScreen(
                                    onReview = onReview,
                                    onCheck = onCheck,
                                    onAsk = onAskForFeedback,
                                    onSignIn = {
                                        asking =
                                            SignInReason(
                                                title = "Sign in to see what is waiting",
                                                why =
                                                    "Reviews of your apps and cases you are asked to decide " +
                                                        "are addressed to an account. The rest of Circle " +
                                                        "you can read without one.",
                                            )
                                    },
                                )

                            // The new crew screens, running on in-memory state. HomeScreen and the group
                            // screens it pushes are still on disk and still work; nothing points at them from
                            // the bar while this is being built. See docs/crew.md.
                            MainTab.GROUPS ->
                                CrewScreen(
                                    onSignIn = {
                                        asking =
                                            SignInReason(
                                                title = "Sign in to see your crews",
                                                why =
                                                    "Which seats are yours, what you owe tonight and what " +
                                                        "you have finished all hang off the account. Open " +
                                                        "reads without one.",
                                            )
                                    },
                                )

                            MainTab.RANKS -> RanksScreen()

                            MainTab.APPS ->
                                AppsScreen(
                                    onSignIn = {
                                        asking =
                                            SignInReason(
                                                title = "Sign in to see your apps",
                                                why =
                                                    "This is the list of what you have put up, so it needs " +
                                                        "to know who you are. Everything else in TestTrack " +
                                                        "you can read without an account.",
                                            )
                                    },
                                )

                            MainTab.PROFILE ->
                                MeScreen(
                                    onSignOut = onSignOut,
                                    onOpenRanks = { show(MainTab.RANKS) },
                                    onSignIn = {
                                        asking =
                                            SignInReason(
                                                title = "Sign in to see your record",
                                                why =
                                                    "Your level, your tix and what you have done belong to " +
                                                        "the account. Everything else in TestTrack you can " +
                                                        "read without one.",
                                            )
                                    },
                                )
                        }
                    }
                }
            }
        }

        MainBar(
            tab,
            show,
            Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { dockHeight = with(density) { it.height.toDp() } },
        )
    }

    asking?.let { reason ->
        SignInSheet(
            reason = reason,
            onDismiss = { asking = null },
            onSignedIn = {
                asking = null
                // Everything on screen was drawn for somebody without a name. The header is the
                // one that would otherwise sit there telling them to sign in after they have.
                hudVm.load(context)
            },
        )
    }
}

/** The ground each tab stands on. */
private val MainTab.scene: Scene
    get() =
        when (this) {
            MainTab.CIRCLE -> Scene.CIRCLE
            MainTab.GROUPS -> Scene.CREW
            MainTab.RANKS -> Scene.RANKS
            MainTab.APPS -> Scene.APPS
            MainTab.PROFILE -> Scene.PROFILE
        }
