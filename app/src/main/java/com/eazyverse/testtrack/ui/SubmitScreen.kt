package com.eazyverse.testtrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazyverse.testtrack.data.*
import kotlinx.coroutines.launch

/**
 * What submitting an app commits you to.
 *
 * Four promises in the owner's own voice. What happens when one is broken is stated once,
 * underneath, in the app's voice — a person should not be made to recite their own penalty, and a
 * list that mixes "I will" with "and then you will be removed" reads as a warning rather than an
 * agreement.
 */
val SUBMIT_RULES = listOf(
    "My app is live on a closed testing track and I've added the testers group to it.",
    "I'll keep that release on the track for the whole 14 days.",
    "I'll open every other app in my group, every day, until the run ends.",
    "I won't opt out of anyone's test, or leave the group, before the 14 days are up."
)

class SubmitViewModel : ViewModel() {
    var submitting by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    var nameInput by mutableStateOf("")
    var packageInput by mutableStateOf("")

    /**
     * Optional, and staying that way. Plenty of apps open straight onto something a tester can
     * use, and making this a condition of submitting would have those owners inventing a sentence
     * to satisfy the form.
     */
    var notesInput by mutableStateOf("")

    /** One tick for the whole of [SUBMIT_RULES]. */
    var agreed by mutableStateOf(false)

    /**
     * Why a field is refused, or null while it is acceptable.
     *
     * Both return null for an empty field. A form that turns red before anything has been typed
     * is telling someone off for not having finished yet, so the message waits until there is
     * something to be wrong about, and the disabled button carries the "not yet" on its own.
     */
    val nameError: String?
        get() {
            val trimmed = nameInput.trim()
            return when {
                nameInput.isEmpty() -> null
                trimmed.isEmpty() -> "That's only spaces."
                trimmed.length < 2 -> "That looks too short to be an app name."
                trimmed.length > NAME_MAX -> "Keep it under $NAME_MAX characters."
                else -> null
            }
        }

    val packageError: String?
        get() = when {
            packageInput.isEmpty() -> null
            packageInput.length > PACKAGE_MAX -> "That's longer than a package name can be."
            !packageInput.contains('.') -> "A package name has at least one dot, like com.example.myapp."
            packageInput == OWN_PACKAGE -> "That's TestTrack itself. Use your own app's package name."
            !PACKAGE_SHAPE.matches(packageInput) -> "That should look like com.example.myapp."
            else -> null
        }

    private val nameOk: Boolean get() = nameInput.trim().length in 2..NAME_MAX
    private val packageOk: Boolean get() = packageInput.isNotEmpty() && packageError == null

    val canSubmit: Boolean get() = nameOk && packageOk && agreed && !submitting

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
                message = "You're not signed in any more. Sign in again and try this once more."
            } else {
                runCatching {
                    Repo.submitApp(
                        uid, email, packageInput.trim(), nameInput.trim(), notesInput.trim()
                    )
                }
                    .onSuccess {
                        // After the write, never instead of it. The queue is the record; this only
                        // saves an admin from finding out whenever they next happen to look.
                        // Silent and best effort, so a failure here cannot cost a submission that
                        // has already been accepted.
                        runCatching { Notify.submission(nameInput.trim()) }
                        onDone()
                    }
                    .onFailure {
                        message = it.friendly(
                            "We couldn't submit that. Check your connection and try again.",
                            denied = "That package is already registered by someone else."
                        )
                    }
            }
            submitting = false
        }
    }

    private companion object {
        const val NAME_MAX = 50
        /** Android's own ceiling for a package name. */
        const val PACKAGE_MAX = 255
        const val OWN_PACKAGE = "com.eazyverse.testtrack"

        /**
         * Two or more segments, each starting with a letter. Deliberately stricter than Android
         * strictly allows: a segment may legally begin with an underscore, but a tester typing one
         * has made a mistake far more often than they have a package that starts that way.
         */
        val PACKAGE_SHAPE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
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
                "Two things to do in Play Console first. Put a release on a closed testing " +
                    "track, and add the testers group to that track so everyone here can " +
                    "install it.\n\n" +
                    "Then tell us the package name below. An admin will put your app into a " +
                    "testing group, and being placed is the approval. Your 14 days start once " +
                    "that group has enough people in it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.5f
            )

            Spacer(Modifier.height(32.dp))
            DialogField(
                value = vm.nameInput,
                onValueChange = { vm.nameInput = it },
                label = "App name",
                placeholder = "The name testers will see on Play",
                error = vm.nameError,
                imeAction = ImeAction.Next
            )
            Spacer(Modifier.height(22.dp))
            DialogField(
                value = vm.packageInput,
                // Stripped as it is typed rather than checked afterwards. A package name pasted
                // from Play Console arrives with a trailing space often enough that rejecting it
                // would be blaming the tester for the clipboard.
                onValueChange = { vm.packageInput = it.trim() },
                label = "Package name",
                placeholder = "com.example.myapp",
                error = vm.packageError,
                supporting = "Find it in Play Console, under App information",
                imeAction = ImeAction.Done
            )
            Spacer(Modifier.height(22.dp))
            DialogField(
                value = vm.notesInput,
                // Trimmed to the cap as it is typed rather than refused on submit. The limit is
                // there to keep a list row readable, not to catch anyone out, and a form that
                // takes six hundred characters and then rejects them has wasted the typing.
                onValueChange = { vm.notesInput = it.take(NOTES_MAX) },
                label = "Notes for your testers",
                placeholder = "Test account: demo@example.com / pass1234",
                supporting = "Anything they need to get past the front door. Everyone in your " +
                    "group and the admins can read this, so use a throwaway account, never " +
                    "your own password.",
                singleLine = false,
                minHeight = 96.dp
            )

            Spacer(Modifier.height(32.dp))
            Text(
                "What you're agreeing to",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Your group is thirteen other developers waiting on the same fourteen days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            // Numbered rather than bulleted: a rule you can refer to by number is one an admin can
            // point at when a placement is refused.
            SUBMIT_RULES.forEachIndexed { index, rule ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(
                        "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(
                        rule,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.45f
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "If someone stops testing, they're removed from the group and everyone else " +
                    "stops testing their app. That's what keeps the rest of the run going.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.45f
            )

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.agreed = !vm.agreed }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = vm.agreed, onCheckedChange = { vm.agreed = it })
                Spacer(Modifier.width(6.dp))
                Text(
                    "I've read these and I agree.",
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
                "One app per group. If you already have an app being tested, this one will go " +
                    "into a different group.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}
