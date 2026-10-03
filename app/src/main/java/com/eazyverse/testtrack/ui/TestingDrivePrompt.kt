package com.eazyverse.testtrack.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.findActivity
import com.eazyverse.testtrack.viewmodel.TestingDriveViewModel

@Composable
fun TestingDrivePrompt(
    onConnected: () -> Unit,
    onDismiss: () -> Unit,
    vm: TestingDriveViewModel = viewModel(),
) {
    val activity = LocalContext.current.findActivity()
    val consent =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            vm.consentResult(activity, it.data, onConnected)
        }
    Ask(
        title = "Connect Drive for test proof",
        body =
            "This test saves screenshot proof to your Google Drive. Connect it to continue testing. " +
                "You can still browse and request feedback without it.",
        confirm = "Connect Drive",
        onConfirm = { vm.connect(activity, consent::launch, onConnected) },
        onDismiss = onDismiss,
        dismiss = "Not now",
    ) {
        if (vm.busy) Text("Connecting...", style = MaterialTheme.typography.bodySmall)
        vm.message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
