package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/** A quiet action surface; the explanation uses the same anchored callout as seat options. */
@Composable
internal fun CrewRunAppStatus(
    state: CrewRunAppState,
    busy: Boolean,
) {
    val wideText = LocalDensity.current.fontScale > 1.3f
    Column(
        Modifier
            .fillMaxWidth()
            .background(lerp(Tt.colors.bg, Tt.colors.text, 0.05f), RoundedCornerShape(10.dp))
            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
            BandLabel(state, Modifier.weight(1f))
            if (!wideText) BandActions(state, busy)
        }
        if (wideText && state.action != null) Row(Modifier.align(Alignment.End)) { BandActions(state, busy) }
    }
}

@Composable
private fun RowScope.BandLabel(
    state: CrewRunAppState,
    modifier: Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val ink = if (state.tone == Tone.QUIET) Tt.colors.text3 else state.tone.ink()
        Icon(state.icon, null, Modifier.size(15.dp), tint = ink)
        Spacer(Modifier.width(6.dp))
        Text(state.text, Modifier.weight(1f, fill = false), style = tileStyle(13f, 18f, FontWeight.Normal), color = ink)
        state.explanation?.let { why ->
            var open by remember(state.text) { mutableStateOf(false) }
            Box {
                IconButton(onClick = { open = !open }, modifier = Modifier.size(32.dp)) {
                    Icon(Lucide.Info, "About ${state.text}", Modifier.size(14.dp), tint = Tt.colors.text3)
                }
                Callout(open, { open = false }, state.text, iconInset = 9.dp) {
                    Text(why, style = tileStyle(13f, 18f, FontWeight.Normal), color = Tt.colors.text2)
                }
            }
        }
    }
}

@Composable
private fun BandActions(
    state: CrewRunAppState,
    busy: Boolean,
) {
    state.action?.let { InlineAction(it, enabled = !busy, onClick = state.run) }
    state.alternate?.let { InlineAction(it, enabled = !busy, onClick = state.runAlternate) }
}
