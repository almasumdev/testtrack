package com.eazyverse.testtrack.ui

import android.os.SystemClock
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis

/*
 * The one clock every robot moves to.
 *
 * Each robot used to start its own infinite animation when it appeared, so going to another tab
 * and back began every hover, blink and prop from the top, and two robots on screen at once were
 * never in step. Everything here is worked out from the phone's own frame time instead: a robot
 * that appears is already wherever it would have been, and all of them breathe together.
 */

/**
 * The frame time in milliseconds, ticking every frame while [active]; frozen when it is not, so a
 * phone with animations off draws one still picture. Starts from the current time rather than
 * zero, so the first frame is already in place.
 */
@Composable
internal fun robotTime(active: Boolean = true): LongState {
    val time = remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    if (active) {
        LaunchedEffect(Unit) {
            while (true) withFrameMillis { time.longValue = it }
        }
    }
    return time
}

/** 0 to 1 and back over twice [half] milliseconds, eased each way, as a reversing tween is. */
internal fun pingPong(
    ms: Long,
    half: Int,
    easing: Easing = FastOutSlowInEasing,
): Float {
    val p = (ms % (2L * half)).toFloat() / half
    return easing.transform(if (p <= 1f) p else 2f - p)
}

/** Eyelids: open, then shut and open again in a quarter of a second at [at] into each [period]. */
internal fun blinkAt(
    ms: Long,
    period: Int,
    at: Int,
): Float {
    val t = (ms % period).toInt()
    return when {
        t < at -> 1f
        t < at + 110 -> 1f - 0.92f * (t - at) / 110f
        t < at + 230 -> 0.08f + 0.92f * (t - at - 110) / 120f
        else -> 1f
    }
}
