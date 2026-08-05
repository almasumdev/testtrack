package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.data.*
import kotlinx.coroutines.launch

class SubmitViewModel : ViewModel() {
    var submitting by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    var nameInput by mutableStateOf("")
    var packageInput by mutableStateOf("")
    var confirmed by mutableStateOf(false)

    /** Rough Android package shape: at least two dot-separated segments starting with a letter. */
    val packageValid: Boolean
        get() = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$").matches(packageInput)

    val canSubmit: Boolean get() = packageValid && nameInput.isNotBlank() && confirmed && !submitting

    /**
     * Registers one app for review.
     *
     * Not machine-checked: reading Play track state needs developer-level authorization from every
     * owner, which costs more than it is worth. An admin confirms instead — and since approving is
     * the same act as placing the app in a group, that judgement cannot be automated anyway.
     */
    fun submit(onDone: () -> Unit) {
        if (!canSubmit) return
        submitting = true
        message = null
        viewModelScope.launch {
            val uid = AuthRepo.uid
            val email = Session.email
            if (uid == null || email == null) {
                message = "Not signed in"
            } else {
                runCatching { Repo.submitApp(uid, email, packageInput, nameInput.trim()) }
                    .onSuccess { onDone() }
                    .onFailure { message = it.message ?: "Could not submit" }
            }
            submitting = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubmitScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    vm: SubmitViewModel = viewModel()
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Submit an app", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Publish a release to closed testing and add the group as testers, then submit " +
                    "the package name. An admin places it into a testing group — that placement " +
                    "is the approval, and the 14 days start once the group is full enough.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))
            Field(
                label = "App name",
                value = vm.nameInput,
                onValueChange = { vm.nameInput = it },
                placeholder = "As it appears on Play",
                imeAction = ImeAction.Next
            )
            Spacer(Modifier.height(22.dp))
            Field(
                label = "Package name",
                value = vm.packageInput,
                onValueChange = { vm.packageInput = it.trim() },
                placeholder = "com.example.myapp",
                support = if (vm.packageInput.isNotEmpty() && !vm.packageValid)
                    "Expected something like com.example.myapp"
                else
                    "Play Console → your app → App information",
                error = vm.packageInput.isNotEmpty() && !vm.packageValid,
                imeAction = ImeAction.Done
            )

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.confirmed = !vm.confirmed }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = vm.confirmed, onCheckedChange = { vm.confirmed = it })
                Spacer(Modifier.width(6.dp))
                Text(
                    "It's live on a closed track and the group is added as testers.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(24.dp))
            Primary(
                "Submit for approval",
                busy = vm.submitting,
                enabled = vm.canSubmit,
                tall = true,
                onClick = { vm.submit(onDone) }
            )

            vm.message?.let {
                Spacer(Modifier.height(20.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(40.dp))
            Text(
                "One app per group. If you're already in a group, this one goes to another.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
