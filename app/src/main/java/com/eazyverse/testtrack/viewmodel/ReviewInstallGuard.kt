package com.eazyverse.testtrack.viewmodel

import android.content.Context
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.ui.ReviewViewModel

/** Re-read PackageManager at the action, including after a permission dialog returns. */
internal fun ReviewViewModel.requirePlayInstall(context: Context): Boolean {
    val current = job ?: return false
    if (InstalledApps.info(context, current.post.packageName).fromPlay) return true
    keep(current.copy(requiresPlayInstall = true, onPhone = false))
    return false
}
