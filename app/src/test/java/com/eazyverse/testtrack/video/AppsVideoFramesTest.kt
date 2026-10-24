package com.eazyverse.testtrack.video

import android.app.Application
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.eazyverse.testtrack.data.AppIconData
import com.eazyverse.testtrack.net.ApiSession
import com.eazyverse.testtrack.net.Listing
import com.eazyverse.testtrack.ui.AppAddScreen
import com.eazyverse.testtrack.ui.AppEntry
import com.eazyverse.testtrack.ui.AppPage
import com.eazyverse.testtrack.ui.AppsBody
import com.eazyverse.testtrack.ui.AppsScreen
import com.eazyverse.testtrack.ui.MainTab
import com.eazyverse.testtrack.ui.Scene
import com.eazyverse.testtrack.viewmodel.AddWay
import com.eazyverse.testtrack.viewmodel.AppAddViewModel
import com.eazyverse.testtrack.viewmodel.AppsViewModel
import com.eazyverse.testtrack.viewmodel.PhoneApp
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.imageio.ImageIO

/**
 * Adding an app to My apps, from the app's own composables: both ways in, the phone's app list,
 * the picked app, Play's answer, and the app on My apps and on its own page. Written to
 * media/public/frames/apps, opt-in like [CircleVideoFramesTest].
 */
@RunWith(VideoFramesRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Phone.SDK], qualifiers = Phone.QUALIFIERS, application = Application::class)
class AppsVideoFramesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val frame = Frame(compose, "apps")

    @Before
    fun onlyWhenAsked() = assumeTrue("set -PvideoFrames to draw the video frames", videoFramesOn)

    private val focus = CircleSamples.focus

    /** What a developer's phone has on it, their own app among the rest. */
    private val phone =
        listOf(
            PhoneApp("com.example.calendar", "Calendar"),
            PhoneApp("com.example.camera", "Camera"),
            PhoneApp("com.example.clock", "Clock"),
            PhoneApp(focus.pkg, focus.name),
            PhoneApp("com.example.notes", "Notes"),
            PhoneApp("com.example.photos", "Photos"),
            PhoneApp("com.example.weather", "Weather"),
        )

    /** Focus Flow's own icon, from the sample art, as the phone would hand it over. */
    private fun art(): Bitmap {
        val picture = ImageIO.read(FrameImages.art.resolve("icon_focus.png"))
        val bitmap = Bitmap.createBitmap(picture.width, picture.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(picture.getRGB(0, 0, picture.width, picture.height, null, 0, picture.width), 0, picture.width, 0, 0, picture.width, picture.height)
        return bitmap
    }

    /** The phone's apps installed, Focus Flow with its icon. */
    private fun installPhone() {
        val packages = shadowOf(compose.activity.packageManager)
        phone.forEach { app ->
            packages.installPackage(
                android.content.pm.PackageInfo().apply {
                    packageName = app.pkg
                    applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = app.pkg }
                },
            )
        }
        packages.setApplicationIcon(focus.pkg, BitmapDrawable(compose.activity.resources, art()))
    }

    private fun adding(configure: AppAddViewModel.() -> Unit = {}): AppAddViewModel {
        ApiSession.load(compose.activity)
        installPhone()
        return AppAddViewModel().apply(configure)
    }

    /** Focus Flow as My apps has it once added from the phone: a closed test, not verified yet. */
    private val added =
        AppEntry(id = "app-focus", pkg = focus.pkg, name = focus.name, icon = focus.icon, developer = "You", live = false, canCrew = true, canFeedback = true, canDelete = true)

    private fun apps(list: List<AppEntry>): AppsViewModel {
        ApiSession.load(compose.activity)
        return AppsViewModel().apply {
            setState("apps", list)
            setState("loading", false)
        }
    }

    private fun tap(label: String) = compose.onNode(hasText(label, substring = true) and hasClickAction())

    @Test
    fun a01_empty() {
        // The list's own empty state, as a signed-in account with nothing added sees it.
        ApiSession.load(compose.activity)
        frame.show { Ground { TabShell(MainTab.APPS, Scene.APPS) { AppsBody(apps = emptyList(), onAdd = {}) } } }
        runCatching { frame.target("addEmpty", tap("Add an app")) }
        frame.write("empty")
    }

    @Test
    fun a02_ways() {
        val vm = adding()
        frame.show { Pushed { AppAddScreen(onAdded = {}, onBack = {}, vm = vm) } }
        runCatching { frame.target("wayPhone", tap("Pick from this phone")) }
        runCatching { frame.target("wayPlay", tap("Play link or package name")) }
        runCatching { frame.target("continue", tap("Continue")) }
        frame.write("ways")
    }

    @Test
    fun a03_waysPlay() {
        val vm = adding { select(AddWay.PLAY) }
        frame.show { Pushed { AppAddScreen(onAdded = {}, onBack = {}, vm = vm) } }
        runCatching { frame.target("wayPlay", tap("Play link or package name")) }
        runCatching { frame.target("continue", tap("Continue")) }
        frame.write("waysPlay")
    }

    @Test
    fun a04_sheet() {
        val vm =
            adding {
                setState("way", AddWay.PHONE)
                setState("phoneApps", phone)
                setState("picking", true)
            }
        frame.show { Pushed { AppAddScreen(onAdded = {}, onBack = {}, vm = vm) } }
        runCatching { frame.target("search", compose.onNode(hasSetTextAction())) }
        runCatching { frame.target("pickFocus", tap(focus.name)) }
        frame.write("sheet")
    }

    @Test
    fun a05_picked() {
        val vm =
            adding {
                setState("way", AddWay.PHONE)
                setState("phoneApps", phone)
                setState("picked", phone.first { it.pkg == focus.pkg })
                setState("pickedName", focus.name)
                setState("pickedIcon", AppIconData.Encoded("", art().asImageBitmap()))
            }
        frame.show { Pushed { AppAddScreen(onAdded = {}, onBack = {}, vm = vm) } }
        runCatching { frame.target("icon", compose.onNode(hasContentDescription("App icon"), useUnmergedTree = true)) }
        runCatching { frame.target("closed", tap("Closed testing")) }
        runCatching { frame.target("live", tap("Live on Play")) }
        // The note is a line of text after its 15 dp info icon and an 8 dp gap: the mark takes both.
        runCatching {
            val words = compose.onNodeWithText("We check it on Play", substring = true).screenBounds()
            frame.target("notice", androidx.compose.ui.geometry.Rect(words.left - frame.px(23f), words.top, words.right, words.bottom))
        }
        runCatching { frame.target("add", tap("Add to My apps")) }
        frame.write("picked")
    }

    @Test
    fun a06_play() {
        val vm =
            adding {
                setState("way", AddWay.PLAY)
                setState("choice", AddWay.PLAY)
                setState("query", "https://play.google.com/store/apps/details?id=${focus.pkg}")
            }
        frame.show { Pushed { AppAddScreen(onAdded = {}, onBack = {}, vm = vm) } }
        runCatching { frame.target("field", compose.onNode(hasSetTextAction())) }
        runCatching { frame.target("find", tap("Find my app")) }
        runCatching { frame.target("closed", tap("Closed testing")) }
        frame.write("play")
    }

    @Test
    fun a07_found() {
        val vm =
            adding {
                setState("way", AddWay.PLAY)
                setState("choice", AddWay.PLAY)
                setState("found", Listing(`package` = focus.pkg, name = focus.name, icon = focus.icon, developer = "You"))
            }
        frame.show { Pushed { AppAddScreen(onAdded = {}, onBack = {}, vm = vm) } }
        runCatching { frame.target("change", tap("Change")) }
        runCatching { frame.target("add", tap("Add to My apps")) }
        frame.write("found")
    }

    @Test
    fun a08_list() {
        val vm = apps(listOf(added))
        frame.show { Ground { TabShell(MainTab.APPS, Scene.APPS) { AppsScreen(vm = vm) } } }
        runCatching { frame.target("appRow", tap(focus.name)) }
        runCatching { frame.target("addApp", tap("Add app")) }
        frame.write("list")
    }

    @Test
    fun a09_page() {
        val vm = apps(listOf(added))
        frame.show { Pushed { AppPage(vm = vm, id = added.id, onBack = {}, onOpenCrew = {}, onOpenRequest = {}, onCrew = {}, onFeedback = {}) } }
        runCatching { frame.target("crewRow", tap("Crew test")) }
        runCatching { frame.target("feedbackRow", compose.onNode(hasText("Feedback") and hasClickAction())) }
        runCatching { frame.target("verify", compose.onNodeWithText("Not verified yet")) }
        frame.write("page")
    }
}
