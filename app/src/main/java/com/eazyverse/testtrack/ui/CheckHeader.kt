package com.eazyverse.testtrack.ui

import android.os.Build
import android.widget.Toast
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.FlaskConical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Scale
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular

/*
 * The top of a To check page: which app this is, and the three numbers that matter.
 */

/** What kind of review a page is about, as its pill says it. */
enum class CheckKind(
    val label: String,
    val icon: ImageVector,
) {
    PLAY("Play review", Lucide.Play),
    CLOSED("Closed test", Lucide.FlaskConical),
    DISPUTED("Disputed", Lucide.Scale),
}

/**
 * The app, exactly as tall as its icon: the name on one line, and under it the kind of review
 * and the package, also on one line. Either scrolls sideways when it does not fit rather than
 * wrapping and making the header taller than the icon.
 *
 * Tapping the app opens it on Play. Tapping the package copies it, with no ripple; Android 13
 * and later announce a copy themselves, so the toast is only for older ones.
 */
@Composable
fun AppHeader(
    name: String,
    packageName: String,
    kind: CheckKind,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val quiet = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = packageName.isNotBlank()) { openPlay(context, packageName) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName, name, size = HEADER_ICON) { AppTile(name, HEADER_ICON) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).height(HEADER_ICON), verticalArrangement = Arrangement.SpaceEvenly) {
            Text(
                name,
                Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, lineHeight = 21.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindPill(kind)
                if (packageName.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        packageName,
                        Modifier
                            .weight(1f, fill = false)
                            .clickable(interactionSource = quiet, indication = null) {
                                clipboard.setText(AnnotatedString(packageName))
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                    Toast.makeText(context, "Package name copied", Toast.LENGTH_SHORT).show()
                                }
                            }.basicMarquee(iterations = Int.MAX_VALUE),
                        style =
                            MaterialTheme.typography.bodySmall
                                .copy(fontSize = 12.sp)
                                .tabular(),
                        color = Tt.colors.text3,
                        maxLines = 1,
                    )
                }
            }
        }
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
            .padding(start = 7.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
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
 * Three equal tiles. A long value (a long reviewer name) takes a second line and then ends in an
 * ellipsis, rather than pushing the others; labels are words only so they fit large text too.
 */
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
                Text(
                    stat.value,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp, lineHeight = 18.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (stat.warn) t.warn else t.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val HEADER_ICON = 58.dp
