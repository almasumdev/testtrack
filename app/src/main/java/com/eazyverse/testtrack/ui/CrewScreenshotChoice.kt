package com.eazyverse.testtrack.ui

import androidx.compose.runtime.Composable

/** Both app tiles and Run updates recover the same saved visit without losing either proof path. */
@Composable
internal fun CrewScreenshotChoice(
    app: CrewApp,
    actions: CrewActions,
    onDismiss: () -> Unit,
) {
    Ask(
        title = "Add a screenshot",
        body = "Your time is saved. Add one so this test counts.",
        confirm = "Retake",
        onConfirm = {
            onDismiss()
            actions.onOpen(app)
        },
        onDismiss = onDismiss,
        alt = "Use saved",
        onAlt = {
            onDismiss()
            actions.onPickPicture(app)
        },
    )
}
