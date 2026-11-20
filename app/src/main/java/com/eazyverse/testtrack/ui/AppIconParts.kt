package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/*
 * An app's icon on its own page and on the way in: the tap that changes it.
 */

/**
 * Tapping an app's icon to change it, without the ripple. The tap area is the square round the
 * icon and its pencil, so a ripple flashed a hard grey square over a rounded icon; what changes
 * next, the gallery opening, is the answer to the tap.
 */
@Composable
internal fun Modifier.iconTap(onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        role = Role.Button,
        onClickLabel = "Change icon",
        onClick = onClick,
    )
