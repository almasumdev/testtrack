package com.eazyverse.testtrack.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.GraphicsLayerScope
import com.eazyverse.testtrack.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * What the robot holds on an empty, loading or finished page: a cartoon thing for that case,
 * in place of an icon on a disc.
 *
 * Each is one or more drawables (`robot_prop_*`, drawn in the robot layers' own 880 by 980 space,
 * so they sit in its hands as they are) and each layer moves its own way: the tape's reels turn,
 * the gears turn, the bell rings, the plug's two ends almost meet. Some scenes give the robot its
 * own face and something on its head too, like the headphones it bops along in.
 */
enum class Prop(
    vararg layers: PropLayer,
    /** Its own eyes and mouth, drawn over the face in place of the blinking eyes. */
    @DrawableRes internal val face: Int? = null,
    /** Worn on the head, so it moves with it. */
    internal val head: List<PropLayer> = emptyList(),
    /** How the whole head moves, about the neck. */
    internal val nod: Motion? = null,
) {
    /** Nobody is asking for feedback: headphones on, grooving to a cassette while it waits. */
    Music(
        PropLayer(R.drawable.robot_prop_music_tape),
        PropLayer(R.drawable.robot_prop_music_reel_left, Spin(3f), px = 562f, py = 953f),
        PropLayer(R.drawable.robot_prop_music_reel_right, Spin(3f), px = 692f, py = 953f),
        PropLayer(R.drawable.robot_prop_music_note1, Rise(dx = -20f, dy = -90f, delay = 0f), px = 250f, py = 340f),
        PropLayer(R.drawable.robot_prop_music_note2, Rise(dx = 20f, dy = -90f, delay = 0.25f), px = 1000f, py = 290f),
        PropLayer(R.drawable.robot_prop_music_note3, Rise(dx = -16f, dy = -70f, delay = 0.5f), px = 270f, py = 200f),
        PropLayer(R.drawable.robot_prop_music_note4, Rise(dx = 16f, dy = -70f, delay = 0.75f), px = 975f, py = 165f),
        face = R.drawable.robot_prop_music_face,
        head = listOf(PropLayer(R.drawable.robot_prop_music_headphones)),
        nod = Rock(deg = 5f, period = 960f),
    ),

    /** Nobody on the board yet. */
    Trophy(
        PropLayer(R.drawable.robot_prop_trophy),
        PropLayer(R.drawable.robot_prop_trophy_spark1, Pulse(0f, 1600f)),
        PropLayer(R.drawable.robot_prop_trophy_spark2, Pulse(0.5f, 1600f)),
    ),

    /** No apps sent yet: a phone with empty slots and one to fill. */
    Phone(
        PropLayer(R.drawable.robot_prop_phone),
        PropLayer(R.drawable.robot_prop_phone_plus, Rock(dy = 7f, period = 1600f)),
    ),

    /** Sign in first. */
    Key(
        PropLayer(R.drawable.robot_prop_key, Rock(deg = 3f), px = 627f, py = 905f),
        PropLayer(R.drawable.robot_prop_key_spark, Pulse(0f, 1600f)),
    ),

    /** No reviews taken on yet: glasses on, notepad out, the pencil tapping, ready to write one. */
    Notes(
        PropLayer(R.drawable.robot_prop_notes_pad),
        PropLayer(R.drawable.robot_prop_notes_pencil, Rock(deg = 6f, period = 800f), px = 640f, py = 1000f),
        head = listOf(PropLayer(R.drawable.robot_prop_notes_glasses)),
    ),

    /** Nothing has happened yet: settled in with popcorn, waiting for the show; kernels hop out of the bucket. */
    Popcorn(
        PropLayer(R.drawable.robot_prop_popcorn_puffs),
        PropLayer(R.drawable.robot_prop_popcorn_pop1, Rise(dx = -70f, dy = -70f, delay = 0f, period = 1600f), px = 570f, py = 710f),
        PropLayer(R.drawable.robot_prop_popcorn_pop2, Rise(dx = 70f, dy = -80f, delay = 0.33f, period = 1600f), px = 700f, py = 706f),
        PropLayer(R.drawable.robot_prop_popcorn_pop3, Rise(dx = 20f, dy = -60f, delay = 0.66f, period = 1600f), px = 636f, py = 694f),
        PropLayer(R.drawable.robot_prop_popcorn_bucket),
        face = R.drawable.robot_prop_popcorn_face,
        nod = Rock(deg = 3f),
    ),

    /** Nothing waiting: licking a double scoop with a cherry on top; a drip slides down. */
    IceCream(
        PropLayer(R.drawable.robot_prop_icecream_cone),
        PropLayer(R.drawable.robot_prop_icecream_scoops, Rock(deg = 3f), px = 627f, py = 900f),
        PropLayer(R.drawable.robot_prop_icecream_drip, Rise(dx = 0f, dy = 60f, delay = 0f), px = 716f, py = 915f),
        face = R.drawable.robot_prop_icecream_face,
        nod = Rock(deg = 2f, period = 1200f),
    ),

    /** Did not load: the two ends of a cable, not quite meeting. */
    Plug(
        PropLayer(R.drawable.robot_prop_plug_left, Rock(dx = 10f, period = 1200f)),
        PropLayer(R.drawable.robot_prop_plug_right, Rock(dx = -10f, period = 1200f)),
        PropLayer(R.drawable.robot_prop_plug_spark, Pulse(0.25f, 1200f)),
    ),

    /** Loading. */
    Gears(
        PropLayer(R.drawable.robot_prop_gear_big, Spin(2f), px = 545f, py = 902f),
        PropLayer(R.drawable.robot_prop_gear_small, Spin(-3f), px = 715f, py = 797f),
    ),

    /** Waiting on someone: turned over now and then. */
    Hourglass(
        PropLayer(R.drawable.robot_prop_hourglass, Turn, px = 627f, py = 872f),
    ),

    /** Approved. */
    Seal(
        PropLayer(R.drawable.robot_prop_seal, Rock(deg = 4f), px = 627f, py = 870f),
        PropLayer(R.drawable.robot_prop_seal_spark1, Pulse(0f, 1600f)),
        PropLayer(R.drawable.robot_prop_seal_spark2, Pulse(0.5f, 1600f)),
    ),

    /** Being decided. */
    Scales(
        PropLayer(R.drawable.robot_prop_scales_stand),
        PropLayer(R.drawable.robot_prop_scales_beam, Rock(deg = 7f), px = 627f, py = 786f),
        // The beam's ends rise and fall by its half-length times sin 7°.
        PropLayer(R.drawable.robot_prop_scales_pan_left, Rock(dy = -23f)),
        PropLayer(R.drawable.robot_prop_scales_pan_right, Rock(dy = 23f)),
    ),

    /** Not paid: an empty wallet with a moth flying out. */
    Wallet(
        PropLayer(R.drawable.robot_prop_wallet),
        PropLayer(R.drawable.robot_prop_wallet_moth, Rock(deg = 10f, dy = -16f, period = 1200f), px = 627f, py = 768f),
    ),

    /** Given up: a white flag. */
    Flag(
        PropLayer(R.drawable.robot_prop_flag_pole),
        PropLayer(R.drawable.robot_prop_flag_cloth, Rock(deg = 4f, period = 1600f), px = 492f, py = 782f),
    ),

    /** Ran out of time. */
    Clock(
        PropLayer(R.drawable.robot_prop_clock, Shake(8f), px = 627f, py = 1040f),
        PropLayer(R.drawable.robot_prop_clock_lines, Pulse(0.05f, 2400f)),
    ),
    ;

    internal val layers = layers.toList()
}

/** One drawable of a prop, how it moves, and the point it moves about, in the robot's space. */
class PropLayer(
    @DrawableRes internal val res: Int,
    internal val motion: Motion? = null,
    internal val px: Float = 627f,
    internal val py: Float = 900f,
)

/** Every motion repeats within this, so the one clock driving them never jumps. */
internal const val PROP_CYCLE = 4800f

/** How a layer moves at [ms] into the cycle; [unit] is pixels per unit of the robot's space. */
sealed interface Motion {
    fun GraphicsLayerScope.at(
        ms: Float,
        unit: Float,
    )
}

/** Fades down and back, brightest at [delay] of each [period]. */
private class Pulse(
    private val delay: Float,
    private val period: Float,
) : Motion {
    override fun GraphicsLayerScope.at(
        ms: Float,
        unit: Float,
    ) {
        alpha = 0.25f + 0.75f * (0.5f + 0.5f * cos(2 * PI * (ms / period - delay)).toFloat())
    }
}

/** Sways about its point and drifts, out and back once each [period]. */
private class Rock(
    private val deg: Float = 0f,
    private val dx: Float = 0f,
    private val dy: Float = 0f,
    private val period: Float = 2400f,
) : Motion {
    override fun GraphicsLayerScope.at(
        ms: Float,
        unit: Float,
    ) {
        val s = sin(2 * PI * ms / period).toFloat()
        rotationZ = deg * s
        translationX = dx * s * unit
        translationY = dy * s * unit
    }
}

/**
 * Floats up from its place by ([dx], [dy]) while growing in and fading out, once each [period];
 * [delay] is the fraction of a period it runs ahead, so several of them take turns.
 */
private class Rise(
    private val dx: Float,
    private val dy: Float,
    private val delay: Float,
    private val period: Float = 2400f,
) : Motion {
    override fun GraphicsLayerScope.at(
        ms: Float,
        unit: Float,
    ) {
        val p = (ms / period + delay) % 1f
        val out = 1f - (1f - p) * (1f - p)
        alpha = if (p < 0.25f) p / 0.25f else (1f - p) / 0.75f
        scaleX = 0.6f + 0.45f * out
        scaleY = scaleX
        translationX = dx * out * unit
        translationY = (30f + (dy - 30f) * out) * unit
    }
}

/** Whole turns per cycle; negative turns the other way. */
private class Spin(
    private val turns: Float,
) : Motion {
    override fun GraphicsLayerScope.at(
        ms: Float,
        unit: Float,
    ) {
        rotationZ = turns * 360f * ms / PROP_CYCLE
    }
}

/** Still, then a quick rattle that dies away, every 2.4 seconds. */
private class Shake(
    private val deg: Float,
) : Motion {
    override fun GraphicsLayerScope.at(
        ms: Float,
        unit: Float,
    ) {
        val t = ms % 2400f
        rotationZ = if (t < 700f) deg * sin(2 * PI * t / 140f).toFloat() * (1f - t / 700f) else 0f
    }
}

/** Still, then over in 0.6 seconds, twice a cycle, so it ends a full turn round and never jumps. */
private object Turn : Motion {
    override fun GraphicsLayerScope.at(
        ms: Float,
        unit: Float,
    ) {
        val half = PROP_CYCLE / 2f
        val t = ms % half
        val over = if (t < half - 600f) 0f else 180f * FastOutSlowInEasing.transform((t - (half - 600f)) / 600f)
        rotationZ = (if (ms >= half) 180f else 0f) + over
    }
}
