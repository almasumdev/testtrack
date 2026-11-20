package com.eazyverse.testtrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.viewmodel.AskFailure
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/*
 * Adding an app: the news that My apps changed elsewhere, and how a failure to add one is said.
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

/**
 * Why adding or finding an app failed: the title in bold and what to do about it under it, as one
 * line in amber when the fault is ours and a way on is offered, red when something needs fixing.
 */
@Composable
internal fun FailureLine(failure: AskFailure) {
    InfoLine(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(failure.title) }
            append("\n")
            append(failure.detail)
        },
        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        tone = if (failure.ours) Tone.WARN else Tone.BAD,
        icon = Lucide.CircleAlert,
    )
}
