package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * The app as Play shows it, or as it was typed, directly on the page, to be confirmed before it
 * goes anywhere. Tap the row to choose a different one. Shared by Ask for feedback and Add to a
 * crew, so finding an app looks and works the same both ways.
 */
@Composable
internal fun FoundApp(
    pkg: String,
    name: String,
    /** Play's icon; blank when typed, and then the one on this phone. */
    icon: String,
    developer: String,
    unchecked: Boolean,
    onChange: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon.isBlank()) {
            // Typed by hand: no Play icon, so the one on this phone, as the feed will draw it.
            AppIcon(pkg, name, size = 48.dp)
        } else {
            SubcomposeAsyncImage(
                model = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)),
                loading = { AskAppPlaceholder() },
                error = { AskAppPlaceholder() },
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                developer.ifBlank { pkg },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Change",
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.accentInk,
            fontWeight = FontWeight.Medium,
        )
    }
    if (unchecked) {
        Spacer(Modifier.height(8.dp))
        UncheckedNote()
    }
}

/** Said under an app nothing has checked on Play yet. */
@Composable
internal fun UncheckedNote() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Lucide.Info, null, Modifier.size(16.dp), tint = Tt.colors.text3)
        Text(
            "Not checked on Play yet. Make sure the testers group is on your closed track, " +
                "or reviewers won't be able to install it.",
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.text3,
        )
    }
}
