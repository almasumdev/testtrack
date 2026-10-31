package com.eazyverse.testtrack.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.exchangeBackground

/**
 * What an emulator gets instead of the app (data/EmulatorGate.kt). Back closes the app as usual.
 * The way out, for a real phone caught by mistake, is a mail saying what the app saw.
 */
@Composable
fun EmulatorBlockScreen(signs: List<String>) {
    val context = LocalContext.current
    Box(
        Modifier.fillMaxSize().exchangeBackground().safeDrawingPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RobotHolding(Prop.Gears, size = 140.dp, description = "Real phone needed")
            Text(
                text = "TestTrack needs a real phone",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = "Testers have to be real people on real phones, so TestTrack doesn't run on emulators.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "This is a real phone? Tell us",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Tt.colors.accent,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .clickable {
                            mail(
                                context,
                                "TestTrack says my phone is an emulator",
                                "\n\n---\n${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})\n${signs.joinToString("\n")}",
                            )
                        }.padding(12.dp),
            )
        }
    }
}
