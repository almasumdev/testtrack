package com.eazyverse.testtrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eazyverse.testtrack.viewmodel.requirePlayInstall
import com.eazyverse.testtrack.viewmodel.startCapture

/** Keep capture consent and its guide together, including the return from Android overlay settings. */
@Composable
internal fun rememberReviewCapture(
    vm: ReviewViewModel,
    packageName: String,
    live: Boolean,
): () -> Unit {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var guiding by remember(packageName) { mutableStateOf(false) }
    var askingOverlay by remember(packageName) { mutableStateOf(false) }
    var returningFromSettings by remember(packageName) { mutableStateOf(false) }
    val consent = rememberCaptureConsent(onGranted = { code, data -> vm.startCapture(context, code, data, packageName) })
    val start: () -> Unit = {
        if (vm.requirePlayInstall(context)) {
            if (vm.canFloat(context)) consent() else askingOverlay = true
        }
    }
    DisposableEffect(owner, vm, packageName) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && returningFromSettings) {
                    returningFromSettings = false
                    if (vm.canFloat(context)) start()
                }
            }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (guiding) {
        ReviewGuideDialog(
            live = live,
            onOk = {
                guiding = false
                vm.markGuideSeen(context)
                start()
            },
            onDismiss = { guiding = false },
        )
    }
    if (askingOverlay) {
        OverlayPermissionDialog(
            onOpen = {
                askingOverlay = false
                returningFromSettings = true
                vm.openOverlaySettings(context)
            },
            onDismiss = { askingOverlay = false },
        )
    }
    return {
        if (vm.requirePlayInstall(context)) {
            if (vm.job?.noBox == true || vm.guideSeen(context)) start() else guiding = true
        }
    }
}
