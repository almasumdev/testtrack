package com.eazyverse.testtrack

import android.animation.ValueAnimator
import android.app.Activity
import android.os.Build
import androidx.core.splashscreen.SplashScreen
import androidx.core.view.doOnDetach
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.eazyverse.testtrack.domain.remainingSplashAnimationMillis

/** Compose loads normally behind the system splash; no network or pre-draw gate. */
fun SplashScreen.finishIconAnimation(
    activity: Activity,
    owner: LifecycleOwner,
) {
    setOnExitAnimationListener { splash ->
        if (Build.VERSION.SDK_INT < 31 || !ValueAnimator.areAnimatorsEnabled()) {
            splash.remove()
            return@setOnExitAnimationListener
        }
        val remaining =
            remainingSplashAnimationMillis(
                splash.iconAnimationStartMillis,
                splash.iconAnimationDurationMillis,
                System.currentTimeMillis(),
            )
        val exit =
            Runnable {
                if (activity.isFinishing || activity.isDestroyed) {
                    splash.remove()
                } else {
                    splash.view
                        .animate()
                        .alpha(0f)
                        .setDuration(120)
                        .withEndAction { splash.remove() }
                        .start()
                }
            }
        val observer =
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    splash.view.removeCallbacks(exit)
                    splash.view.animate().cancel()
                    splash.remove()
                    owner.lifecycle.removeObserver(this)
                }
            }
        owner.lifecycle.addObserver(observer)
        splash.view.doOnDetach { owner.lifecycle.removeObserver(observer) }
        splash.view.postDelayed(exit, remaining)
    }
}
