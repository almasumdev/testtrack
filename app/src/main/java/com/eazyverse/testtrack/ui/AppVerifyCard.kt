package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

private const val ALSO_VERIFIED = "It's also verified when two crew members install it, or you accept a review."

/**
 * An app nothing has verified, as a plain card: what to do in one line, with the other ways it gets
 * verified behind the (i). A closed test gets the group's address and the steps; a live app only
 * waits for Play, so it has neither. Verify now asks Play again at once.
 */
@Composable
internal fun AppVerifyCard(
    live: Boolean,
    busy: Boolean,
    onVerify: () -> Unit,
) {
    var how by remember { mutableStateOf(false) }
    val t = Tt.colors
    CrewCard(padding = PaddingValues(16.dp)) {
        Text(
            if (live) "Waiting for Play" else "Add our testers group",
            style = tileStyle(18f, 23f, FontWeight.SemiBold),
            color = t.text,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Top) {
            Text(
                if (live) {
                    "Google Play didn't answer when you added it. We keep asking, or you can check now."
                } else {
                    "Add this address to your closed track's testers, then tap Verify now."
                },
                Modifier.weight(1f, fill = false),
                style = tileStyle(13f, 18f, FontWeight.Normal),
                color = t.text2,
            )
            AlsoVerifiedInfo()
        }
        if (!live) {
            Spacer(Modifier.height(12.dp))
            SeatAddressRow()
            Spacer(Modifier.height(4.dp))
            InlineAction("How to add it", Modifier.offset(x = (-8).dp)) { how = true }
        }
        Spacer(Modifier.height(if (live) 16.dp else 10.dp))
        Primary("Verify now", busy = busy, onClick = onVerify)
    }
    if (how) GroupHowSheet { how = false }
}

/** The (i) after the card's line, opening a callout with the other ways an app gets verified. */
@Composable
private fun AlsoVerifiedInfo() {
    var open by remember { mutableStateOf(false) }
    val t = Tt.colors
    Box {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            IconButton(onClick = { open = !open }, modifier = Modifier.padding(start = 2.dp).size(24.dp)) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .then(if (open) Modifier.background(t.accentInk.copy(alpha = 0.1f)) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Lucide.Info, "Other ways it gets verified", Modifier.size(14.dp), tint = if (open) t.accentInk else t.text3)
                }
            }
        }
        Callout(open, { open = false }, "Other ways it gets verified", iconInset = 5.dp) {
            Text(ALSO_VERIFIED, style = tileStyle(13f, 18f, FontWeight.Normal), color = t.text2)
        }
    }
}
