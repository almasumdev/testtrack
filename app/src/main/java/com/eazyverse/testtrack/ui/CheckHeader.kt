package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Scale
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.launch

/*
 * The top of a To check page: which app this is, and the three numbers that matter.
 */

/** What kind of review a page is about, as its pill says it. */
enum class CheckKind(
    val label: String,
    val icon: ImageVector,
) {
    PLAY("Live app", Lucide.Play),
    CLOSED("Closed test", Lucide.FlaskConical),
    DISPUTED("Disputed", Lucide.Scale),
}

/**
 * The app, exactly as tall as its icon: the name on one line, and under it the kind of review
 * and an optional detail, also on one line. Either scrolls sideways when it does not fit rather
 * than wrapping and making the header taller than the icon.
 *
 * The package is used, not shown: it finds the installed app's icon and opens its Play page when
 * the app is tapped. Shown, it was the longest and least useful words on the page.
 */
@Composable
fun AppHeader(
    name: String,
    packageName: String,
    kind: CheckKind,
    /** A short line after the pill, such as who posted it; nothing when blank. */
    detail: String = "",
    /** The app's icon from the server, for a reader who has not installed it yet. */
    icon: String = "",
    trailing: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = packageName.isNotBlank()) { openPlay(context, packageName) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark(packageName, name, icon, size = HEADER_ICON)
        Spacer(Modifier.width(12.dp))
        // Two lines that add up to the icon's height: a 22dp name, 4dp, and a 22dp pill.
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                name,
                Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindPill(kind)
                if (detail.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        detail,
                        Modifier.weight(1f, fill = false).basicMarquee(iterations = Int.MAX_VALUE),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 16.sp),
                        color = Tt.colors.text3,
                        maxLines = 1,
                    )
                }
            }
        }
        trailing()
    }
}

/** The kind of review: a small rounded pill with its mark, never wrapping. */
@Composable
private fun KindPill(kind: CheckKind) {
    val t = Tt.colors
    val (ink, ground) = if (kind == CheckKind.DISPUTED) t.warn to t.warnBg else t.accentInk to t.glass2
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(ground)
            .height(22.dp)
            .padding(start = 7.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kind.icon, null, Modifier.size(12.dp), tint = ink)
        Spacer(Modifier.width(4.dp))
        Text(
            kind.label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 15.sp),
            fontWeight = FontWeight.SemiBold,
            color = ink,
            maxLines = 1,
        )
    }
}

/** One of the three numbers under the header. */
data class Stat(
    val label: String,
    val value: String,
    val warn: Boolean = false,
)

/**
 * One value, on one line. When it does not fit it ends in an ellipsis, and a tap shows the whole
 * of it in a tooltip; a value that fits does nothing when tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatValue(stat: Stat) {
    val t = Tt.colors
    var cut by remember { mutableStateOf(false) }
    val tip = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stat.value) } },
        state = tip,
        enableUserInput = false,
    ) {
        Text(
            stat.value,
            Modifier.clickable(
                enabled = cut,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { scope.launch { tip.show() } },
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp, lineHeight = 18.sp),
            fontWeight = FontWeight.Bold,
            color = if (stat.warn) t.warn else t.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { cut = it.hasVisualOverflow },
        )
    }
}

/** Three equal tiles; labels are words only, so they still fit with the system text turned up. */
@Composable
fun StatTiles(stats: List<Stat>) {
    val t = Tt.colors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { stat ->
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(if (stat.warn) t.warnBg else t.glass)
                    .border(1.dp, t.glassBorder, shape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    stat.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, letterSpacing = 0.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = t.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                StatValue(stat)
            }
        }
    }
}

private val HEADER_ICON = 48.dp
