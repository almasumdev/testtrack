package com.eazyverse.testtrack.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Puts the keyboard away: the focused field lets go, and the keyboard goes with it.
 *
 * Every button calls this before doing its job. A tap on Find my app or Post that left the
 * keyboard up covered the very answer it was waiting for.
 */
@Composable
fun rememberDismissKeyboard(): () -> Unit {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focus, keyboard) {
        {
            focus.clearFocus()
            keyboard?.hide()
        }
    }
}

/**
 * A tap on the page itself, not on a field or a button, puts the keyboard away.
 *
 * Fields and buttons take their own taps first, so only empty space reaches this.
 */
fun Modifier.dismissKeyboardOnTap(dismiss: () -> Unit): Modifier = pointerInput(dismiss) { detectTapGestures { dismiss() } }
