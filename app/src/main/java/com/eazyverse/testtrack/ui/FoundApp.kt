package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.Config
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * The app as Play shows it, or as My apps has it, directly on the page, to be confirmed before it
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
    /** Null when the app is fixed, as on the seat screen: no Change, and the row is not tapped. */
    onChange: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onChange != null) Modifier.clickable(onClick = onChange) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Play's icon when there is one; blank, it is the one on this phone, as the feed will draw it.
        AppMark(pkg, name, icon, size = 48.dp, ownIcon = true)
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
        if (onChange != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                "Change",
                style = MaterialTheme.typography.bodySmall,
                color = Tt.colors.accentInk,
                fontWeight = FontWeight.Medium,
            )
        }
    }
    if (unchecked) {
        Spacer(Modifier.height(8.dp))
        InfoLine(
            "Not checked on Play yet. Add ${Config.GROUP_EMAIL} to your closed track's testers, " +
                "or reviewers won't be able to install it.",
        )
    }
}
