package com.eazyverse.testtrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/*
 * Adding an app, from the tab that lists them, and the news that a list changed elsewhere.
 */

/**
 * Something you put up, or took down, from another screen: a seat taken or given up on Crew, a
 * request posted on Ask for feedback. My apps hears it and reads its list again, so by the time
 * somebody switches to it the app is already there. The server's own news only reaches My apps
 * for crews it already knew about, which a brand new seat is not.
 */
object MyAppsChanged {
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val events = changes.asSharedFlow()

    fun tell() {
        changes.tryEmit(Unit)
    }
}

/** The way in on a list that already has apps: a pill over the bottom of the list. */
@Composable
internal fun AddAppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Primary("Add app", modifier, mark = rememberVectorPainter(Lucide.Plus), onClick = onClick)
}
