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
        title = "Complete your test",
        body = "Your app time is saved. Add an app screenshot so this test counts.",
        confirm = "Retake screenshot",
        onConfirm = {
            onDismiss()
            actions.onOpen(app)
        },
        onDismiss = onDismiss,
    ) {
        InlineAction("Use saved app screenshot") {
            onDismiss()
            actions.onPickPicture(app)
        }
    }
}
