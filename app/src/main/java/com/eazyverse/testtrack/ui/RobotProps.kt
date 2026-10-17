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
    /** How the face alone moves, such as eyes glancing side to side. */
    internal val faceMotion: Motion? = null,
    /** False where something worn on the head, a hard hat or a cloud, takes the antenna's place. */
    internal val antenna: Boolean = true,
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

    /** Nobody on the board yet: holding the trophy, its eyes glancing round for anybody else. */
    Trophy(
        PropLayer(R.drawable.robot_prop_trophy),
        PropLayer(R.drawable.robot_prop_trophy_spark1, Pulse(0f, 1600f)),
        PropLayer(R.drawable.robot_prop_trophy_spark2, Pulse(0.5f, 1600f)),
        face = R.drawable.robot_prop_trophy_face,
        faceMotion = Rock(dx = 22f),
        head = listOf(PropLayer(R.drawable.robot_prop_trophy_cheeks)),
    ),

    /** No apps sent yet: a phone with empty slots and one to fill, looked at hopefully. */
    Phone(
        PropLayer(R.drawable.robot_prop_phone),
        PropLayer(R.drawable.robot_prop_phone_plus, Rock(dy = 7f, period = 1600f)),
        face = R.drawable.robot_prop_phone_face,
    ),

    /** Signed out: a padlock on your reviews, the key in the other hand nudging toward its keyhole. */
    Lock(
        PropLayer(R.drawable.robot_prop_lock_padlock),
        PropLayer(R.drawable.robot_prop_lock_key, Rock(dx = -18f, period = 1600f)),
        PropLayer(R.drawable.robot_prop_lock_glint, Pulse(0f, 1600f)),
        face = R.drawable.robot_prop_lock_face,
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

    /** No crew waiting to start: stacking blocks one by one, the way a crew fills; the top one drops in. */
    Blocks(
        PropLayer(R.drawable.robot_prop_blocks_low),
        PropLayer(R.drawable.robot_prop_blocks_top, Drop),
        face = R.drawable.robot_prop_blocks_face,
    ),

    /** Nothing running: flying a kite from one hand while it waits; the kite sways in the wind. */
    Kite(
        PropLayer(R.drawable.robot_prop_kite_kite, Rock(deg = 5f), px = 953f, py = 900f),
        face = R.drawable.robot_prop_kite_face,
    ),

    /** Nothing finished: day 13 flips over to 14, the day a crew lands in the record. */
    Calendar(
        PropLayer(R.drawable.robot_prop_calendar_base),
        PropLayer(R.drawable.robot_prop_calendar_page, FlipUp, py = 870f),
        face = R.drawable.robot_prop_calendar_face,
    ),

    /** Did not load: the two ends of a cable, not quite meeting; worried brows and an "oh". */
    Plug(
        PropLayer(R.drawable.robot_prop_plug_left, Rock(dx = 10f, period = 1200f)),
        PropLayer(R.drawable.robot_prop_plug_right, Rock(dx = -10f, period = 1200f)),
        PropLayer(R.drawable.robot_prop_plug_spark, Pulse(0.25f, 1200f)),
        face = R.drawable.robot_prop_plug_face,
    ),

    /** Loading: hard hat on, tongue out in concentration, the gears turning. */
    Gears(
        PropLayer(R.drawable.robot_prop_gear_big, Spin(2f), px = 545f, py = 902f),
        PropLayer(R.drawable.robot_prop_gear_small, Spin(-3f), px = 715f, py = 797f),
        face = R.drawable.robot_prop_gear_face,
        head = listOf(PropLayer(R.drawable.robot_prop_gear_hat)),
        antenna = false,
    ),

    /** Sent: a sealed letter with a heart floats up out of its hands, off to the developer. */
    Letter(
        PropLayer(R.drawable.robot_prop_letter_whoosh, Pulse(0f, 1200f)),
        PropLayer(R.drawable.robot_prop_letter_letter, Rise(dx = 120f, dy = -260f, delay = 0.2f), px = 627f, py = 920f),
        face = R.drawable.robot_prop_letter_face,
    ),

    /** Approved: the green seal, and eyes squeezed shut with a big smile. */
    Seal(
        PropLayer(R.drawable.robot_prop_seal, Rock(deg = 4f), px = 627f, py = 870f),
        PropLayer(R.drawable.robot_prop_seal_spark1, Pulse(0f, 1600f)),
        PropLayer(R.drawable.robot_prop_seal_spark2, Pulse(0.5f, 1600f)),
        face = R.drawable.robot_prop_seal_face,
    ),

    /** Being decided: the scales tipping, looked up at with one brow raised. */
    Scales(
        PropLayer(R.drawable.robot_prop_scales_stand),
        PropLayer(R.drawable.robot_prop_scales_beam, Rock(deg = 7f), px = 627f, py = 786f),
        // The beam's ends rise and fall by its half-length times sin 7°.
        PropLayer(R.drawable.robot_prop_scales_pan_left, Rock(dy = -23f)),
        PropLayer(R.drawable.robot_prop_scales_pan_right, Rock(dy = 23f)),
        face = R.drawable.robot_prop_scales_face,
    ),

    /** Not paid: teary under its own little rain cloud, the wallet empty and a moth flying out. */
    Wallet(
        PropLayer(R.drawable.robot_prop_wallet),
        PropLayer(R.drawable.robot_prop_wallet_moth, Rock(deg = 10f, dy = -16f, period = 1200f), px = 627f, py = 768f),
        face = R.drawable.robot_prop_wallet_face,
        head =
            listOf(
                PropLayer(R.drawable.robot_prop_wallet_cloud),
                PropLayer(R.drawable.robot_prop_wallet_drop1, Rise(dx = 0f, dy = 140f, delay = 0f, period = 1200f), px = 540f, py = 280f),
                PropLayer(
                    R.drawable.robot_prop_wallet_drop2,
                    Rise(dx = 0f, dy = 140f, delay = 0.33f, period = 1200f),
                    px = 627f,
                    py = 280f,
                ),
                PropLayer(
                    R.drawable.robot_prop_wallet_drop3,
                    Rise(dx = 0f, dy = 140f, delay = 0.66f, period = 1200f),
                    px = 714f,
                    py = 280f,
                ),
            ),
        antenna = false,
    ),

    /** Given up: an awkward grin and a sweat drop, the white flag fluttering. */
    Flag(
        PropLayer(R.drawable.robot_prop_flag_pole),
        PropLayer(R.drawable.robot_prop_flag_cloth, Rock(deg = 6f, period = 1200f), px = 492f, py = 782f),
        face = R.drawable.robot_prop_flag_face,
        head =
            listOf(
                PropLayer(R.drawable.robot_prop_flag_sweat, Rise(dx = 0f, dy = 60f, delay = 0f, period = 1600f), px = 905f, py = 330f),
            ),
    ),

    /** Ran out: fast asleep while the alarm clock rattles away in its hands. */
    Clock(
        PropLayer(R.drawable.robot_prop_clock, Shake(10f), px = 627f, py = 1040f),
        PropLayer(R.drawable.robot_prop_clock_lines, Pulse(0.05f, 1200f)),
        face = R.drawable.robot_prop_clock_face,
        head =
            listOf(
                PropLayer(R.drawable.robot_prop_clock_z1, Rise(dx = 26f, dy = -50f, delay = 0f), px = 900f, py = 440f),
                PropLayer(R.drawable.robot_prop_clock_z2, Rise(dx = 26f, dy = -50f, delay = 0.5f), px = 955f, py = 375f),
            ),
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

/** Falls in from above, lands with a small bounce, sits, then fades for the next one. */
private object Drop : Motion {
    override fun GraphicsLayerScope.at(
        ms: Float,
        unit: Float,
    ) {
        val p = (ms % 2400f) / 2400f
        val fall = (p / 0.45f).coerceAtMost(1f)
        translationY =
            when {
                p < 0.45f -> -220f * (1f - fall * fall) * unit
                p < 0.6f -> -24f * sin(PI * (p - 0.45f) / 0.15f).toFloat() * unit
                else -> 0f
            }
        alpha =
            when {
                p < 0.15f -> p / 0.15f
                p > 0.9f -> (1f - p) / 0.1f
                else -> 1f
            }
    }
}

/**
 * A calendar page folding up about its top edge and gone once edge-on, so its back (which would
 * show the number mirrored) is never seen; the next page fades in at the start of the cycle.
 */
private object FlipUp : Motion {
    override fun GraphicsLayerScope.at(
        ms: Float,
        unit: Float,
    ) {
        val p = (ms % 2400f) / 2400f
        scaleY =
            when {
                p < 0.55f -> 1f
                p < 0.75f -> 1f - FastOutSlowInEasing.transform((p - 0.55f) / 0.2f)
                else -> 0f
            }
        alpha =
            when {
                p < 0.08f -> p / 0.08f
                p < 0.75f -> 1f
                else -> 0f
            }
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
