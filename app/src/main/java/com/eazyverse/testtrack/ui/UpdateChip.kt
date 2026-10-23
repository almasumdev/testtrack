package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowDownToLine
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCw
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.delay

/**
 * An optional update, as the app offers it: the header's icon and, while it exists, a row in
 * Settings beside About. Both read this one value, so they never disagree.
 *
 * Nothing starts or opens by itself. [State.AVAILABLE] downloads in the background when tapped;
 * [State.DOWNLOADING] is a spinner; [State.READY] is the Restart icon, and tapping it asks before
 * restarting. A forced update is not this: it is the full screen in [ForceUpdateScreen].
 */
class UpdateOffer(
    val state: State,
    /** The version waiting, when it is known for certain; null reads "Update available". */
    val version: String? = null,
    /** How far the download is, 0 to 1, or -1 before Play says how big it is. */
    val progress: Float = -1f,
    /** The download landed after a tap in this session, so the restart dialog may open by itself. */
    val autoAsk: Boolean = false,
    /** Download, restart (once confirmed), or nothing while downloading. */
    val onTap: () -> Unit = {},
) {
    enum class State { AVAILABLE, DOWNLOADING, READY }

    internal fun withTap(tap: () -> Unit) = UpdateOffer(state, version, progress, autoAsk, tap)
}

/** The offer the shell is showing, for the header and the Settings row. */
val LocalUpdateOffer = compositionLocalOf<UpdateOffer?> { null }

/**
 * Hosts the restart dialog and hands the screens an offer whose Restart asks first. The dialog
 * opens by itself only when a download started from here lands while the app is on its main
 * screen, which is the only place this is composed; anywhere else the Restart icon says it.
 */
@Composable
fun ProvideUpdateOffer(
    offer: UpdateOffer?,
    content: @Composable () -> Unit,
) {
    var asking by remember { mutableStateOf(false) }
    val auto = offer?.state == UpdateOffer.State.READY && offer.autoAsk
    LaunchedEffect(auto) { if (auto) asking = true }
    val shown = if (offer?.state == UpdateOffer.State.READY) offer.withTap { asking = true } else offer
    androidx.compose.runtime.CompositionLocalProvider(LocalUpdateOffer provides shown) { content() }
    if (asking && offer?.state == UpdateOffer.State.READY) {
        Ask(
            title = "Restart to update?",
            body =
                (offer.version?.let { "Version $it is ready. " } ?: "The update is ready. ") +
                    "TestTrack closes and opens again on it in a few seconds.",
            confirm = "Restart",
            dismiss = "Later",
            onConfirm = {
                asking = false
                offer.onTap()
            },
            onDismiss = { asking = false },
        )
    }
}

/** The header's icon: download, a spinner while it downloads, then Restart. */
@Composable
internal fun UpdateChip(offer: UpdateOffer) {
    val t = Tt.colors
    when (offer.state) {
        UpdateOffer.State.READY ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(t.accent)
                    .clickable(onClickLabel = "Restart to update", role = Role.Button, onClick = offer.onTap)
                    .padding(start = 11.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.RotateCw, null, Modifier.size(16.dp), tint = t.onAccent)
                Spacer(Modifier.width(7.dp))
                Text("Restart", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = t.onAccent)
            }
        UpdateOffer.State.DOWNLOADING -> {
            // A second tap says it is already under way rather than asking Play again.
            var saying by remember { mutableStateOf(false) }
            LaunchedEffect(saying) {
                if (saying) {
                    delay(2_000)
                    saying = false
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (saying) {
                    Text("Downloading", style = MaterialTheme.typography.labelMedium, color = t.text2)
                    Spacer(Modifier.width(8.dp))
                }
                RoundButton("Update downloading", onClick = { saying = true }) {
                    if (offer.progress >= 0f) {
                        CircularProgressIndicator(
                            progress = { offer.progress },
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = t.accent,
                            trackColor = t.glass2,
                        )
                    } else {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = t.accent, trackColor = t.glass2)
                    }
                }
            }
        }
        UpdateOffer.State.AVAILABLE ->
            // A plain round button with a gold dot: there is something new, and nothing is asked of you.
            Box {
                RoundButton("Update TestTrack", onClick = offer.onTap) {
                    Icon(Lucide.ArrowDownToLine, "Update available", Modifier.size(19.dp), tint = t.accentInk)
                }
                // Placed on the 40dp circle, inside the 48dp it is touched by.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-3).dp, y = 3.dp)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(t.gold)
                        .border(2.dp, t.glass, CircleShape),
                )
            }
    }
}

@Composable
private fun RoundButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    // 40 to look at, 48 to touch: the extra 4 on each side is the gap the header leaves around it.
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CircleShape)
            .background(Tt.colors.glass)
            .border(1.dp, Tt.colors.glassBorder, CircleShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The Settings row, shown only while an update exists: the same state as the header's icon. */
@Composable
internal fun UpdateRow(
    offer: UpdateOffer,
    place: TilePlace,
    icon: ImageVector,
) {
    val t = Tt.colors
    val title = offer.version?.let { "Version $it" } ?: "Update available"
    val subtitle =
        when (offer.state) {
            UpdateOffer.State.AVAILABLE -> "Ready to download"
            UpdateOffer.State.DOWNLOADING -> "Downloading"
            UpdateOffer.State.READY -> "Restart to finish updating"
        }
    TileRow(
        icon,
        title,
        place,
        subtitle = subtitle,
        trailing = {
            when (offer.state) {
                UpdateOffer.State.AVAILABLE ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NewTag()
                        SmallPill("Update", Lucide.ArrowDownToLine, offer.onTap)
                    }
                UpdateOffer.State.READY -> SmallPill("Restart", Lucide.RotateCw, offer.onTap)
                UpdateOffer.State.DOWNLOADING ->
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(72.dp)) {
                        Text(
                            if (offer.progress >= 0f) "${(offer.progress * 100).toInt()}%" else "",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = t.accentInk,
                        )
                        Spacer(Modifier.height(4.dp))
                        val bar = Modifier.width(72.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
                        if (offer.progress >= 0f) {
                            LinearProgressIndicator(progress = { offer.progress }, modifier = bar, color = t.accent, trackColor = t.glass2)
                        } else {
                            LinearProgressIndicator(modifier = bar, color = t.accent, trackColor = t.glass2)
                        }
                    }
            }
        },
        onClick = if (offer.state == UpdateOffer.State.DOWNLOADING) null else offer.onTap,
    )
}

@Composable
private fun NewTag() {
    Text(
        "New",
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Tt.colors.gold.copy(alpha = 0.18f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
        fontWeight = FontWeight.Bold,
        color = Tt.colors.gold,
    )
}

@Composable
private fun SmallPill(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Tt.colors.accent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = Tt.colors.onAccent)
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Tt.colors.onAccent)
    }
}
