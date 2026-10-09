package com.eazyverse.testtrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * [onShow] each time this is on screen and the app in front, [onHide] when either stops.
 *
 * A tab leaving the composition counts as hidden, so switching tabs and putting the phone down
 * are one case.
 */
@Composable
fun WhileShown(
    onShow: () -> Unit,
    onHide: () -> Unit,
) {
    val owner = LocalLifecycleOwner.current
    val show by rememberUpdatedState(onShow)
    val hide by rememberUpdatedState(onHide)
    DisposableEffect(owner) {
        val watcher =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> show()
                    Lifecycle.Event.ON_PAUSE -> hide()
                    else -> Unit
                }
            }
        owner.lifecycle.addObserver(watcher)
        onDispose {
            owner.lifecycle.removeObserver(watcher)
            hide()
        }
    }
}
