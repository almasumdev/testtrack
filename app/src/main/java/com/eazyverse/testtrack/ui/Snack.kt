package com.eazyverse.testtrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * One snackbar for the whole app. See docs/design/snackbar/snackbar.html.
 *
 * Screens and view models only post here, through [Snacks]. One [SnackHost] at the app's root
 * draws whatever is showing, over every page, and keeps it above whatever a page has pinned to
 * its foot: each such bar, button foot, the tab bar and the chat box carries [snackFloor].
 */

/** How a snack is marked: its icon and that icon's colour. */
enum class SnackTone { INFO, DONE, ERROR, OFFLINE }

/** The one button a snack may carry. Tapping it acts and closes the snack. */
data class SnackAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * One line said to the reader. [sticky] stays until it is dismissed or replaced by its [key]; a
 * new snack with the same [key] replaces the old one. [icon] stands in for the tone's own mark and
 * [millis] for the tone's own time.
 */
data class Snack(
    val text: String,
    val tone: SnackTone = SnackTone.INFO,
    val action: SnackAction? = null,
    val sticky: Boolean = false,
    val key: String? = null,
    val icon: ImageVector? = null,
    val millis: Long? = null,
)

/** A snack as posted, with an id, so the same words said again restart the time. */
internal class Shown(
    val id: Long,
    val snack: Snack,
) {
    /** Time still to show, kept here so it runs on across page changes and a rotation; -1 until it starts. */
    var left: Long = -1L

    /** Equal for the same words in the same look, so saying them again does not animate. */
    val look: String get() = "${snack.tone}|${snack.text}|${snack.action?.label}|${snack.icon?.name}"
}

/** Where every snack is posted. */
object Snacks {
    private var next = 0L

    /** The passing one, if any, which shows over [standing] until it goes. */
    internal var current by mutableStateOf<Shown?>(null)
        private set

    /** The one that stays, such as offline. It steps aside for a passing one, then comes back. */
    internal var standing by mutableStateOf<Shown?>(null)
        private set

    internal val shown: Shown? get() = current ?: standing

    /** What is on screen now, or null. */
    val showing: Snack? get() = shown?.snack

    fun show(s: Snack) {
        val item = Shown(++next, s)
        if (s.key != null) {
            if (standing?.snack?.key == s.key) standing = null
            if (current?.snack?.key == s.key) current = null
        }
        if (s.sticky) standing = item else current = item
    }

    fun info(t: String) = show(Snack(t, SnackTone.INFO))

    fun done(
        t: String,
        action: SnackAction? = null,
    ) = show(Snack(t, SnackTone.DONE, action))

    /** An error, with Try again when [retry] is given. */
    fun error(
        t: String,
        retry: (() -> Unit)? = null,
    ) = show(Snack(t, SnackTone.ERROR, retry?.let { SnackAction(TRY_AGAIN, it) }))

    /** The one line for a call that never reached the server, with Try again when [retry] is given. */
    fun offline(retry: (() -> Unit)? = null) = error(OFFLINE_LINE, retry)

    /** Takes away whatever was posted under [key], showing or standing. */
    fun dismiss(key: String) {
        if (current?.snack?.key == key) current = null
        if (standing?.snack?.key == key) standing = null
    }

    /** The one with [id] is done: its time ran out. */
    internal fun gone(id: Long) {
        if (current?.id == id) current = null
        if (standing?.id == id) standing = null
    }

    /**
     * [item] was swiped away or its button tapped. The same words said again since are the same bar
     * on screen, so they go with it.
     */
    internal fun gone(item: Shown) {
        if (current?.id == item.id || current?.look == item.look) current = null
        if (standing?.id == item.id || standing?.look == item.look) standing = null
    }

    const val TRY_AGAIN = "Try again"
    const val OFFLINE_LINE = "Couldn't reach TestTrack. Check your connection."
    const val PICTURE_UNREADABLE = "Couldn't open that picture. Try another."
}

/**
 * How long a snack shows before accessibility settings have their say, as the design page times
 * them: with a button 8s, an error 6s, two lines 5s, one line 3s. Offline is sticky and has none.
 */
internal val Snack.baseMillis: Long
    get() =
        millis ?: when {
            action != null -> 8_000L
            tone == SnackTone.ERROR -> 6_000L
            text.length > TWO_LINES_FROM -> 5_000L
            else -> 3_000L
        }

/** About where a snack's words wrap to a second line on a phone. */
private const val TWO_LINES_FROM = 60

/** What the host knows of the page under it: the tops of what is pinned, and the snack's own height. */
internal object SnackPlace {
    /** Each pinned thing's top, in root pixels, by its node. */
    val floors = mutableStateMapOf<Any, Float>()

    /** The showing snack's height in pixels, for [snackRoom]. */
    var height by mutableFloatStateOf(0f)

    /** How many sheets are open. Each is a window over the root host, so it draws the snack itself. */
    var overlays by mutableIntStateOf(0)
}

/**
 * Marks something pinned to the bottom of a page: a button foot, the tab bar, the chat box. The
 * snackbar sits 8 above the highest one on screen, or above the keyboard when that is higher.
 */
fun Modifier.snackFloor(): Modifier = this then SnackFloorElement

private object SnackFloorElement : ModifierNodeElement<SnackFloorNode>() {
    override fun create() = SnackFloorNode()

    override fun update(node: SnackFloorNode) = Unit

    override fun hashCode() = "snackFloor".hashCode()

    override fun equals(other: Any?) = other === this
}

private class SnackFloorNode :
    Modifier.Node(),
    GlobalPositionAwareModifierNode {
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) SnackPlace.floors[this] = coordinates.boundsInRoot().top
    }

    override fun onDetach() {
        SnackPlace.floors.remove(this)
    }
}

/**
 * Room for a snack that stays, such as offline, so the end of a list is never under it. Nothing
 * while a passing snack or none is showing.
 */
@Composable
fun snackRoom(): Dp {
    val s = Snacks.shown ?: return 0.dp
    if (!s.snack.sticky || SnackPlace.height <= 0f) return 0.dp
    return with(LocalDensity.current) { SnackPlace.height.toDp() } + SnackRoomGap
}

/** The snack's 8 over the floor and a little air under the last row above it. */
private val SnackRoomGap = 14.dp
