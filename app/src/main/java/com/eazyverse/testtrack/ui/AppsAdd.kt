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

/** The way in on a list that already has apps: Circle's floating bar, with a plus ahead of the words. */
@Composable
internal fun AddAppBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionBar("Add an app", onClick, modifier, icon = rememberVectorPainter(Lucide.Plus))
}
