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
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.viewmodel.AppsViewModel
import com.eazyverse.testtrack.viewmodel.HudViewModel
import com.eazyverse.testtrack.viewmodel.ReviewMoment
import kotlinx.coroutines.delay

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
    PROFILE("You", Lucide.User),
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
    /** An optional update for the header's button; null when Play has none. */
    update: UpdateOffer? = null,
    onReview: (String) -> Unit,
    onSignOut: () -> Unit,
    onCheck: (String) -> Unit = {},
    onOpenAbout: () -> Unit = {},
    /** A crew's own page. */
    onOpenCrew: (String) -> Unit = {},
    /** The page that puts this app from My apps into a crew. */
    onCrewForApp: (String) -> Unit = {},
    /** Ask for feedback on this app from My apps. */
    onAskForApp: (String) -> Unit = {},
    /** An app's own page. */
    onOpenApp: (String) -> Unit = {},
    /** Adding an app to My apps. */
    onAddApp: () -> Unit = {},
    /** Set by a tapped crew notification; cleared once the Crew tab is showing. */
    openCrew: MutableState<Boolean> = remember { mutableStateOf(false) },
    hudVm: HudViewModel = viewModel(),
) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(MainTab.CIRCLE) }

    // Putting an app into a crew: on the testers group first, wherever it is asked from.
    val membership = rememberMembership()

    // My apps, shared by its tab and the pickers Crew and Circle open: one list, everywhere.
    val apps: AppsViewModel = viewModel()

    /** Which app, for Crew's Take a seat or Circle's Ask for feedback; null when not asking. */
    var picking by remember { mutableStateOf<PickFor?>(null) }

    /** Where Back from the profile goes, so it lands where you actually came from. */
    var cameFrom by rememberSaveable { mutableStateOf(MainTab.CIRCLE) }

    /**
     * Whether the sign-in sheet is up.
     *
     * Held here rather than by a screen, because it is asked for from the header, which is above
     * all five of them, and from two of the tabs themselves. One sheet, one place it is opened.
     */
    var asking by remember { mutableStateOf<SignInReason?>(null) }

    /** What to carry on with once the sheet has signed somebody in, when they were on their way somewhere. */
    var afterSignIn by remember { mutableStateOf<(() -> Unit)?>(null) }

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

    // Whether notifications are allowed can change in Settings while the app is away.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) Session.refreshNotifications(context)
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Back from a tab returns to the first one rather than closing the app, which is what a bottom
    // bar has taught everybody to expect. Off on the first tab, so Back there still leaves.
    BackHandler(enabled = tab != MainTab.CIRCLE) { show(MainTab.CIRCLE) }

    // Read again on every arrival at the shell, and on every return to it. Tix move while somebody
    // is inside another app being tested, so coming back to a stale balance would be the screen
    // telling them their last ten seconds did not count.
    LaunchedEffect(tab, hudVm.signedOut) { hudVm.load(context) }
    var ratingAsk by remember { mutableStateOf(DebugLooks.showRatingAsk) }
    if (ratingAsk) RatingAsk(onClose = { ratingAsk = false })

    // A value moment earned an ask: a beat for the screen to settle,
    // then Play's own card, and only if Play has none to show, the app's own dialog.
    // Collected rather than keyed on: asking clears the flag, and a key change would cancel the very
    // ask that cleared it.
    val activity = context.findActivity()
    LaunchedEffect(activity) {
        if (activity == null) return@LaunchedEffect
        ReviewMoment.askDue.collect { due ->
            if (!due) return@collect
            delay(1_500)
            if (!ReviewMoment.askWithCard(activity)) ratingAsk = true
        }
    }
    // And while the app is in front, whenever the server says the balance or the exp moved.
    WhileShown(onShow = { hudVm.startLive(context) }, onHide = hudVm::stopLive)
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
    // The update's offer for the header and the You page, and the restart dialog behind both.
    ProvideUpdateOffer(update) {
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
                        update = LocalUpdateOffer.current,
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
                                        // Which app, from My apps. Signed out, sign in first and then
                                        // the same: a request is always for an app on My apps, which
                                        // always has its icon.
                                        onAsk = {
                                            val pick = {
                                                apps.load(context)
                                                picking = PickFor.FEEDBACK
                                            }
                                            if (Session.signedIn) {
                                                pick()
                                            } else {
                                                afterSignIn = pick
                                                asking =
                                                    SignInReason(
                                                        title = "Sign in to ask for feedback",
                                                        why =
                                                            "Feedback is asked for one of your apps, so it needs your " +
                                                                "account and My apps.",
                                                    )
                                            }
                                        },
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

                                MainTab.GROUPS -> {
                                    // Get ready asks whether an app is set for a crew, so My apps is read here too.
                                    LaunchedEffect(Unit) { apps.load(context) }
                                    CrewScreen(
                                        onOpenCrew = onOpenCrew,
                                        myApps = apps.apps.takeIf { it.isNotEmpty() || !apps.loading },
                                        onAddApp = onAddApp,
                                        onOpenApp = onOpenApp,
                                        // Signed in and on the testers group already: which app goes in.
                                        onAddToCrew = {
                                            apps.load(context)
                                            picking = PickFor.CREW
                                        },
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
                                }
                                MainTab.RANKS -> RanksScreen()

                                MainTab.APPS ->
                                    AppsScreen(
                                        onOpenApp = onOpenApp,
                                        onAddApp = onAddApp,
                                        vm = apps,
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
                                        notifications = Session.notificationsGranted,
                                        onAbout = onOpenAbout,
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
                    .onSizeChanged { dockHeight = with(density) { it.height.toDp() } - DockTop },
            )
        }
    }

    picking?.let { purpose ->
        AppPickerSheet(
            purpose = purpose,
            apps = apps.apps,
            onPick = { app ->
                if (purpose == PickFor.CREW) membership.require { onCrewForApp(app.id) } else onAskForApp(app.id)
            },
            onAdd = onAddApp,
            onDismiss = { picking = null },
        )
    }

    asking?.let { reason ->
        SignInSheet(
            reason = reason,
            onDismiss = {
                asking = null
                afterSignIn = null
            },
            onSignedIn = {
                asking = null
                afterSignIn?.invoke()
                afterSignIn = null
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
