package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * Posting a closed testing app while our Play check is down.
 *
 * The check is a browser on our server signed in as an account on the testers group, and when
 * that session lapses the owner's app is no less real. The server takes a post on its package and
 * name alone, so the owner types the name and goes on; the package still comes out of their link,
 * read the way the server reads it, so the one thing reviewers install by cannot be mistyped.
 */

/** The package from the link, the name typed, and on to the request. */
@Composable
internal fun ByHandForm(vm: AskViewModel) {
    val pkg = vm.typedPackage
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Package",
                style = MaterialTheme.typography.labelMedium,
                color = Tt.colors.text3,
                modifier = Modifier.width(72.dp),
            )
            Text(
                pkg ?: "Not in this link",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (pkg == null) Tt.colors.bad else Tt.colors.text,
                modifier = Modifier.weight(1f),
            )
            if (pkg != null) Icon(Lucide.Check, null, Modifier.size(18.dp), tint = Tt.colors.ok)
        }
        Spacer(Modifier.height(14.dp))
        DialogField(
            value = vm.handName,
            onValueChange = vm::typeName,
            label = "App name",
            placeholder = "As it appears on Google Play",
            singleLine = true,
            imeAction = ImeAction.Done,
            maxLength = FeedRules.APP_NAME_MAX,
        )
        Spacer(Modifier.height(12.dp))
        UncheckedNote()
        Spacer(Modifier.height(16.dp))
        Primary("Continue", enabled = vm.canUseByHand, tall = true, onClick = vm::useByHand)
    }
}

/** Said wherever a typed app is shown, because nothing has looked at it on Play. */
@Composable
internal fun UncheckedNote() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Lucide.Info, null, Modifier.size(16.dp), tint = Tt.colors.text3)
        Text(
            "Not checked on Play. Make sure the testers group is on your closed track, " +
                "or reviewers won't be able to install it.",
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.text3,
        )
    }
}
