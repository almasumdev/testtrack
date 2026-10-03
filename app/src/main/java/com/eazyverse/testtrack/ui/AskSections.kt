package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt

@Composable
internal fun AskIntro() {
    Column {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    "A FRESH PERSPECTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Tt.colors.accentInk,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Better apps start\nwith feedback.",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Tt.colors.text,
                )
            }
            Spacer(Modifier.width(10.dp))
            Image(painterResource(R.drawable.testtrack_robot), null, Modifier.size(90.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Get useful feedback from other developers.",
            style = MaterialTheme.typography.bodyMedium,
            color = Tt.colors.text2,
        )
    }
}

@Composable
internal fun AskAvailability(
    isLive: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Text(
        "Where can people try your app?",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Tt.colors.text,
    )
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(true to "Live on Play", false to "Closed testing").forEach { (live, label) ->
            val selected = isLive == live
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(if (selected) Tt.colors.tile else androidx.compose.ui.graphics.Color.Transparent)
                    .border(1.dp, if (selected) Tt.colors.tileInk else Tt.colors.glassBorderStrong, shape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onChange(live) })
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Tt.colors.tileInk else Tt.colors.text2)
            }
        }
    }
}

@Composable
internal fun AskSection(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Tt.colors.text)
        Spacer(Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Tt.colors.text2)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
internal fun AskBudget(
    cost: Int,
    balance: Int,
    reviews: Int,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Request total", style = MaterialTheme.typography.titleSmall, color = Tt.colors.text)
            Text("$cost tix", style = MaterialTheme.typography.titleSmall, color = Tt.colors.accentInk, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "$reviews ${if (reviews == 1) "review" else "reviews"} · Your balance: $balance tix",
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.text2,
        )
        if (cost > balance) {
            Spacer(Modifier.height(6.dp))
            Text(
                "You need ${cost - balance} more tix. Earn tix by reviewing other apps.",
                style = MaterialTheme.typography.bodySmall,
                color = Tt.colors.bad,
            )
        }
    }
}

@Composable
internal fun AskMessage(failure: AskFailure) {
    Row(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Lucide.CircleAlert, "Error", Modifier.size(20.dp), tint = Tt.colors.bad)
        Column {
            Text(failure.title, color = Tt.colors.bad, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(failure.detail, color = Tt.colors.bad, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun AskAppPlaceholder() {
    Box(Modifier.fillMaxSize().background(Tt.colors.tile), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.testtrack_robot), null, Modifier.size(36.dp))
    }
}
