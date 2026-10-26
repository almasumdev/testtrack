package com.eazyverse.testtrack.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.WindowManager
import com.eazyverse.testtrack.domain.DockScreen

/**
 * The screen a floating control lives on, in the overlay window's own coordinates: its size, the
 * status and navigation bars it should stay clear of, and the small gap it keeps from the edge.
 *
 * Shared by every control that floats over another app (the night's testing control and the
 * review's camera button), so they stick to the same edges in the same way.
 */
fun overlayScreen(context: Context): DockScreen {
    val res = context.resources
    val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val bounds =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windows.currentWindowMetrics.bounds.let { it.width().toFloat() to it.height().toFloat() }
        } else {
            res.displayMetrics.let { it.widthPixels.toFloat() to it.heightPixels.toFloat() }
        }

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    fun bar(name: String) = res.getIdentifier(name, "dimen", "android").takeIf { it > 0 }?.let { res.getDimension(it) } ?: 0f
    return DockScreen(
        width = bounds.first,
        height = bounds.second,
        top = bar("status_bar_height"),
        bottom = bar("navigation_bar_height"),
        margin = 4 * res.displayMetrics.density,
    )
}
