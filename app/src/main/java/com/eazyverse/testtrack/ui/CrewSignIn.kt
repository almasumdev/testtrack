package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.delay

/*
 * How the crew gets past an app's sign-in, asked the way Play Console's App access asks it: does
 * it need one at all, and if so the username, the password, and anything else. A crew is fourteen
 * people doing for a fortnight what Play's reviewers do once, so they need the same three things.
 *
 * The server keeps one short note per seat, so the three are written into it as labelled lines
 * and read back out of it into labelled rows with a copy button each. A note written before this,
 * or one that does not follow the shape, is shown as it was written.
 */

/** A seat note's sign-in, read back out of it. */
data class SignIn(
    val username: String,
    val password: String,
    val also: String,
)

internal const val SIGN_IN_USER_MAX = 80
internal const val SIGN_IN_PASS_MAX = 80
internal const val SIGN_IN_ALSO_MAX = 90

private const val USER = "Username: "
private const val PASS = "Password: "
private const val ALSO = "Also: "

/** The note for a seat: the given parts as labelled lines, empty ones left out. Fits in 280. */
fun signInNotes(
    username: String,
    password: String,
    also: String,
): String =
    listOfNotNull(
        username
            .trim()
            .take(SIGN_IN_USER_MAX)
            .takeIf { it.isNotEmpty() }
            ?.let { USER + it },
        password
            .trim()
            .take(SIGN_IN_PASS_MAX)
            .takeIf { it.isNotEmpty() }
            ?.let { PASS + it },
        also
            .trim()
            .take(SIGN_IN_ALSO_MAX)
            .takeIf { it.isNotEmpty() }
            ?.let { ALSO + it },
    ).joinToString("\n")

/** The sign-in in a seat's note, or null when the note is not one of ours. */
fun readSignIn(notes: String): SignIn? {
    val lines = notes.trim().lines()
    if (lines.none { it.startsWith(USER) || it.startsWith(PASS) }) return null
    var user = ""
    var pass = ""
    val also = StringBuilder()
    var inAlso = false
    for (line in lines) {
        when {
            line.startsWith(USER) && !inAlso -> user = line.removePrefix(USER).trim()
            line.startsWith(PASS) && !inAlso -> pass = line.removePrefix(PASS).trim()
            line.startsWith(ALSO) && !inAlso -> {
                inAlso = true
                also.append(line.removePrefix(ALSO).trim())
            }
            // Anything after "Also:" is part of it, a second line of instructions included.
            inAlso -> also.append('\n').append(line.trim())
            else -> return null
        }
    }
    return SignIn(user, pass, also.toString().trim())
}

/** What the add page asks, Play Console's App access in four fields. */
@Composable
internal fun SignInFields(
    needed: Boolean,
    onNeeded: (Boolean) -> Unit,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    also: String,
    onAlso: (String) -> Unit,
) {
    TwoChoice("Does your app need a sign-in?", "No sign-in", "Needs a sign-in", !needed) { onNeeded(!it) }
    if (!needed) return
    Spacer(Modifier.height(10.dp))
    Text(
        "A test account the crew can use, as Play Console's App access asks for. Fourteen testers " +
            "see it, so never your own.",
        style = MaterialTheme.typography.bodySmall,
        color = Tt.colors.text3,
    )
    Spacer(Modifier.height(14.dp))
    DialogField(
        value = username,
        onValueChange = { onUsername(it.take(SIGN_IN_USER_MAX)) },
        label = "Username, email or phone",
        placeholder = "test@example.com",
        keyboardType = KeyboardType.Email,
        imeAction = ImeAction.Next,
    )
    Spacer(Modifier.height(12.dp))
    DialogField(
        value = password,
        onValueChange = { onPassword(it.take(SIGN_IN_PASS_MAX)) },
        label = "Password",
        placeholder = "The test account's password",
        imeAction = ImeAction.Next,
    )
    Spacer(Modifier.height(12.dp))
    DialogField(
        value = also,
        onValueChange = { onAlso(it.take(SIGN_IN_ALSO_MAX)) },
        label = "Anything else needed (optional)",
        placeholder = "A one-time code, or which screen to skip",
        singleLine = false,
        minHeight = 72.dp,
        maxLength = SIGN_IN_ALSO_MAX,
    )
}

/**
 * A seat's sign-in as a member sees it: each part on its own row with a copy button, because it is
 * about to be typed into somebody else's sign-in screen. Any other note is shown as written.
 */
@Composable
internal fun SignInDetails(
    notes: String,
    modifier: Modifier = Modifier,
) {
    val signIn = readSignIn(notes)
    if (signIn == null) {
        SelectionContainer {
            Text(notes, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (signIn.username.isNotEmpty()) CopyRow("Username", signIn.username)
        if (signIn.password.isNotEmpty()) CopyRow("Password", signIn.password)
        if (signIn.also.isNotEmpty()) {
            SelectionContainer {
                Text(
                    signIn.also,
                    Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun CopyRow(
    label: String,
    value: String,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500)
            copied = false
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(72.dp), style = MaterialTheme.typography.bodySmall, color = Tt.colors.text3)
        SelectionContainer(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Tt.colors.text)
        }
        IconButton(
            onClick = {
                clipboard.setText(AnnotatedString(value))
                copied = true
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                if (copied) Lucide.Check else Lucide.Copy,
                contentDescription = if (copied) "Copied" else "Copy $label",
                modifier = Modifier.size(16.dp),
                tint = if (copied) Tt.colors.ok else Tt.colors.text3,
            )
        }
    }
}
