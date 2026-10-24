package com.eazyverse.testtrack.video

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.performScrollTo
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import coil.Coil
import coil.ImageLoader
import coil.decode.DataSource
import coil.intercept.Interceptor
import coil.request.ImageResult
import coil.request.SuccessResult
import com.eazyverse.testtrack.Inset
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import com.eazyverse.testtrack.ui.theme.exchangeBackground
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertTrue
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * The phone the frames are drawn for.
 *
 * 1080 x 2340 at 420 dpi is 411.4 x 891.4 dp, and Robolectric only takes whole dp, so the page is
 * laid out at 412 x 892 dp (1081 x 2341 px) and the extra pixel column and row are cut off the right
 * and bottom edges.
 *
 * The bars are measured off the emulator the store captures came from (store/art/shots): its
 * status bar inset is 137 px, of which the top 96 hold the clock and icons, and its gesture bar
 * 63 px. Both are reserved as real window insets, so the app lays itself out under them exactly as
 * on that phone, and left as page ground for the video to draw a status bar over.
 */
internal object Phone {
    const val WIDTH = 1080
    const val HEIGHT = 2340
    const val DENSITY = 2.625f
    const val STATUS_BAR = 137
    const val NAV_BAR = 63
    const val QUALIFIERS = "w412dp-h892dp-port-420dpi"
    const val SDK = 34
}

/** Everything a viewer is told to tap on one frame, in PNG pixels. */
internal class Targets {
    val rects = linkedMapOf("screen" to intArrayOf(0, 0, Phone.WIDTH, Phone.HEIGHT))

    fun put(
        name: String,
        bounds: Rect,
    ) {
        val x = bounds.left.roundToInt()
        val y = bounds.top.roundToInt()
        rects[name] = intArrayOf(x, y, bounds.right.roundToInt() - x, bounds.bottom.roundToInt() - y)
    }

    fun json(): String =
        rects.entries.joinToString(
            separator = ",\n",
            prefix = "{\n  \"width\": ${Phone.WIDTH},\n  \"height\": ${Phone.HEIGHT},\n  \"targets\": {\n",
            postfix = "\n  }\n}\n",
        ) { (name, r) -> "    \"$name\": { \"x\": ${r[0]}, \"y\": ${r[1]}, \"w\": ${r[2]}, \"h\": ${r[3]} }" }
}

/** Every window's root, the activity's first and then any dialog or sheet over it. */
internal fun windowRoots(): List<View> {
    val global = Class.forName("android.view.WindowManagerGlobal")
    val instance = global.getMethod("getInstance").invoke(null)
    val views = global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance)
    @Suppress("UNCHECKED_CAST")
    return (views as List<View>).toList()
}

/** Where a window is drawn in the PNG: placed by its gravity over the activity, as Roborazzi does. */
private fun View.placed(): android.graphics.Rect {
    val screen = windowRoots().first()
    val params = layoutParams as? WindowManager.LayoutParams ?: return android.graphics.Rect(0, 0, width, height)
    val out = android.graphics.Rect()
    Gravity.apply(params.gravity, width, height, android.graphics.Rect(0, 0, screen.width, screen.height), params.x, params.y, out)
    return out
}

private fun View.composeViews(): List<View> =
    if (javaClass.name.endsWith("AndroidComposeView")) {
        listOf(this)
    } else if (this is ViewGroup) {
        (0 until childCount).flatMap { getChildAt(it).composeViews() }
    } else {
        emptyList()
    }

/**
 * A node's bounds in the PNG.
 *
 * Its place in its own window, plus where that window was drawn. The window is the one whose
 * Compose view is the size of the node's semantics root; a dialog's is never the page's size.
 */
internal fun SemanticsNodeInteraction.screenBounds(): Rect {
    val node: SemanticsNode = fetchSemanticsNode()
    var root = node
    while (true) root = root.parent ?: break
    val window =
        windowRoots().lastOrNull { view ->
            view.composeViews().any { it.width == root.size.width && it.height == root.size.height }
        }
    val offset = window?.placed() ?: android.graphics.Rect()
    val at = node.positionInWindow
    val x = offset.left + at.x
    val y = offset.top + at.y
    return Rect(x, y, x + node.size.width, y + node.size.height)
}

/** The page ground every screen stands on: MainActivity's own theme, surface and background. */
@Composable
internal fun Ground(content: @Composable () -> Unit) {
    TestTrackTheme(darkTheme = false) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize().exchangeBackground()) { content() }
        }
    }
}

/** A pushed page, as the nav host shows one: on the ground, inside the safe drawing inset. */
@Composable
internal fun Pushed(content: @Composable () -> Unit) = Ground { Inset(content) }

/**
 * No network. Every client is pointed at a closed local port, so a screen that reaches for the
 * server (a live socket, a reload) fails at once, as it would offline, and nothing leaves the machine.
 */
internal object NoNetwork {
    val attempts = AtomicInteger()

    fun install() {
        ProxySelector.setDefault(
            object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> {
                    attempts.incrementAndGet()
                    return listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 9)))
                }

                override fun connectFailed(
                    uri: URI?,
                    sa: SocketAddress?,
                    ioe: IOException?,
                ) = Unit
            },
        )
    }
}

/**
 * Coil answered from disk. An app's icon address names a file in store/art, and a picked
 * screenshot is a file already, so AppMark and the proof thumbnail draw the real art.
 */
internal object FrameImages {
    val art = File(requireNotNull(System.getProperty("videoFrames.art")) { "videoFrames.art is not set" })

    fun file(data: Any): File? =
        when (data) {
            is File -> data
            is Uri -> data.path?.let(::File)?.takeIf { data.scheme == "file" } ?: art.resolve("${data.lastPathSegment}")
            is String -> art.resolve(data.substringAfterLast('/'))
            else -> null
        }?.takeIf { it.exists() }

    fun install(context: Context) {
        val loader =
            ImageLoader
                .Builder(context)
                .components {
                    add(
                        Interceptor { chain ->
                            val file = file(chain.request.data) ?: error("No local image for ${chain.request.data}")
                            val bitmap = BitmapFactory.decodeFile(file.path)
                            SuccessResult(BitmapDrawable(context.resources, bitmap), chain.request, DataSource.DISK) as ImageResult
                        },
                    )
                }.build()
        Coil.setImageLoader(loader)
    }
}

/** One frame being made: a page on screen, the taps recorded, then the PNG and its JSON. */
internal class Frame(
    private val compose: AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>,
) {
    private val targets = Targets()

    init {
        // Before anything builds an HTTP client, which takes the proxy it will always use.
        NoNetwork.install()
    }

    private val out = File(requireNotNull(System.getProperty("videoFrames.out")) { "videoFrames.out is not set" })

    /** Edge to edge, as MainActivity is, with the page set and given time to settle. */
    fun show(content: @Composable () -> Unit) {
        val activity = compose.activity
        FrameImages.install(activity)
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        compose.mainClock.autoAdvance = false
        compose.setContent(content)
        settle()
    }

    /** Time passes, insets land on every window, and the frame clock catches up. */
    fun settle(millis: Long = 2_500) {
        repeat(4) {
            applyInsets()
            compose.mainClock.advanceTimeBy(millis / 4)
            ShadowLooper.idleMainLooper()
        }
        compose.waitForIdle()
    }

    /** Scrolls the page until [node] is in view, then lets it settle. */
    fun scrollTo(node: SemanticsNodeInteraction) {
        compose.mainClock.autoAdvance = true
        node.performScrollTo()
        compose.mainClock.autoAdvance = false
        settle(600)
    }

    fun target(
        name: String,
        node: SemanticsNodeInteraction,
    ) = targets.put(name, node.screenBounds())

    fun target(
        name: String,
        bounds: Rect,
    ) = targets.put(name, bounds)

    /** dp to PNG pixels. */
    fun px(dp: Float) = dp * Phone.DENSITY

    fun write(id: String) {
        settle(400)
        out.mkdirs()
        val raw = File(out, "$id.raw.png")
        captureScreenRoboImage(raw, RoborazziOptions(taskType = RoborazziTaskType.Record))
        val image = ImageIO.read(raw)
        assertTrue("$id is ${image.width}x${image.height}", image.width >= Phone.WIDTH && image.height >= Phone.HEIGHT)
        ImageIO.write(image.getSubimage(0, 0, Phone.WIDTH, Phone.HEIGHT), "png", File(out, "$id.png"))
        raw.delete()
        File(out, "$id.json").writeText(targets.json())
    }

    private fun applyInsets() {
        val bars = Insets.of(0, Phone.STATUS_BAR, 0, Phone.NAV_BAR)
        val insets =
            WindowInsetsCompat
                .Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, Phone.STATUS_BAR, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, Phone.NAV_BAR))
                .setInsets(WindowInsetsCompat.Type.tappableElement(), bars)
                .setInsets(WindowInsetsCompat.Type.systemGestures(), bars)
                .setVisible(WindowInsetsCompat.Type.statusBars(), true)
                .setVisible(WindowInsetsCompat.Type.navigationBars(), true)
                .build()
        windowRoots().forEach { ViewCompat.dispatchApplyWindowInsets(it, insets) }
    }
}
