package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.delay

/**
 * A screen's passing message, laid into the page under its top bar rather than floated over it.
 *
 * This app has no snackbars. One appeared at the bottom over the dock and the page's own button,
 * and covered the thing it was talking about. This opens a line in the page instead, pushes the
 * content down by its own height, and closes itself after a few seconds or on a tap.
 *
 * [text] is the screen's message; [onDone] clears it, which is what lets the same words be said
 * twice in a row.
 */
@Composable
fun PageMessage(
    text: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Kept while it closes, so the words do not vanish before the line does.
    var shown by remember { mutableStateOf("") }
    if (text != null) shown = text
    LaunchedEffect(text) {
        if (text != null) {
            delay(SHOWN_MS)
            onDone()
        }
    }
    AnimatedVisibility(
        visible = text != null,
        modifier = modifier,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val t = Tt.colors
        val shape = RoundedCornerShape(14.dp)
        Box(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(t.glass)
                    .border(1.dp, t.glassBorder, shape)
                    .clickable(onClick = onDone)
                    .padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.Info, null, Modifier.size(17.dp), tint = t.accentInk)
                Spacer(Modifier.width(10.dp))
                Text(
                    shown,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
                    color = t.text,
                )
                Spacer(Modifier.width(8.dp))
                Icon(Lucide.X, "Dismiss", Modifier.size(16.dp), tint = t.text3)
            }
        }
    }
}

private const val SHOWN_MS = 5_000L
