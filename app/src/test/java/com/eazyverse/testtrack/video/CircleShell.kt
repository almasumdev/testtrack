package com.eazyverse.testtrack.video

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiCache
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.ApiSession
import com.eazyverse.testtrack.net.PostPage
import com.eazyverse.testtrack.ui.Backdrop
import com.eazyverse.testtrack.ui.DockTop
import com.eazyverse.testtrack.ui.FeedScreen
import com.eazyverse.testtrack.ui.FeedViewModel
import com.eazyverse.testtrack.ui.LocalDockRoom
import com.eazyverse.testtrack.ui.MainBar
import com.eazyverse.testtrack.ui.MainTab
import com.eazyverse.testtrack.ui.Scene
import com.eazyverse.testtrack.ui.TopHud
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals

/**
 * The Circle tab as [com.eazyverse.testtrack.ui.MainShell] lays it out: the header over the page,
 * the dock floating at the bottom, and the dock's height handed down so the list makes room.
 *
 * MainShell itself cannot be drawn here. Its header and My apps models start Firebase and the
 * network the moment they exist, so the same real parts are put together the same way, with the
 * header's numbers passed in as MainShell would pass them from its model.
 */
@Composable
internal fun CircleShell(feed: FeedViewModel) = TabShell(MainTab.CIRCLE, Scene.CIRCLE) { FeedScreen(vm = feed) }

/** Any of the main tabs, laid out as [CircleShell] lays out Circle. */
@Composable
internal fun TabShell(
    tab: MainTab,
    scene: Scene,
    page: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var dock by remember { mutableStateOf(0.dp) }
    Backdrop(scene = scene) {
        Scaffold(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            containerColor = Color.Transparent,
            topBar = {
                TopHud(
                    signedIn = true,
                    level = CircleSamples.LEVEL,
                    intoLevel = CircleSamples.INTO_LEVEL,
                    forNext = CircleSamples.FOR_NEXT,
                    tix = CircleSamples.BALANCE,
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                CompositionLocalProvider(LocalDockRoom provides dock) { page() }
            }
        }
        MainBar(
            tab,
            {},
            Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { dock = with(density) { it.height.toDp() } - DockTop },
        )
    }
}

/**
 * Circle's model, opened on the three posts.
 *
 * Through the app's own route: the posts are kept on the phone the way a finished load keeps
 * them, and the model opens on what is kept, as it does on every launch. Its reload then fails
 * offline (see [NoNetwork]) and leaves them standing, which is what an offline phone shows too.
 */
internal fun feedWith(
    context: Context,
    posts: List<ApiPost>,
): FeedViewModel {
    ApiSession.load(context)
    ApiCache.load(context)
    ApiCache.write("/feed/posts", Json.encodeToString(PostPage(posts = posts)), ApiSession.generation)
    assertEquals("kept posts", posts, Api.lastPosts()?.posts)
    return FeedViewModel()
}

/**
 * [com.eazyverse.testtrack.ui.ProofImage], whole, for a picture on disk.
 *
 * The real one fetches through its own image loader on the API's client, which has nowhere to go
 * here. This is the same image with the same width, corners and scaling, so the page around it is
 * laid out exactly as it is on a phone.
 */
@Composable
internal fun LocalProof(file: android.net.Uri) {
    coil.compose.AsyncImage(
        model = file,
        contentDescription = "The screenshot sent as proof",
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)),
        contentScale = ContentScale.FillWidth,
    )
}

/**
 * Sets a model's state that only the server's answer sets, for a figure the frame needs.
 *
 * Compose state behind a private setter is a `MutableState` in a `<name>$delegate` field.
 */
internal fun <T> Any.setState(
    name: String,
    value: T,
) {
    val field = javaClass.getDeclaredField("$name\$delegate").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    (field.get(this) as MutableState<T>).value = value
}
