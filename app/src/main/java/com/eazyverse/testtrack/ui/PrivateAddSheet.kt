package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.InviteRefusal
import com.eazyverse.testtrack.viewmodel.looksLikeEmail

/*
 * Asking somebody to test, by the exact email of their TestTrack account. The server answers only
 * when the invite is sent, so the field checks the address's shape and the answer comes after Send:
 * no such account under the field, an account that can't be asked beside it, or the sheet closes.
 */

/** Longest address the server takes. */
private const val EMAIL_MAX = 320

@Composable
internal fun PrivateAddSheet(
    appName: String,
    packageName: String,
    icon: String,
    crewName: String,
    busy: Boolean,
    refusal: InviteRefusal?,
    /** The address, and a way to close the sheet once the invite is sent. */
    onSend: (String, () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = Tt.colors
    var email by rememberSaveable { mutableStateOf("") }
    // A refusal stands for the address it was given for: change it and the sheet asks afresh.
    val refused = refusal?.takeIf { it.email.equals(email.trim(), ignoreCase = true) }
    val ready = looksLikeEmail(email) && refused == null && !busy
    PickerSheet(
        title = "Add a private tester",
        onDismiss = onDismiss,
        subtitle = "$appName, until $crewName ends",
        skipHalf = true,
        leading = { AppMark(packageName, appName, icon, size = 36.dp, ownIcon = true) },
    ) { close ->
        val send = { if (ready) onSend(email.trim()) { close {} } }
        Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp)) {
            OutlinedField(
                value = email,
                onValueChange = { email = it.take(EMAIL_MAX) },
                label = "Email",
                leadingIcon = Lucide.Mail,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Send,
                onImeAction = send,
                error = refused?.takeIf { it.noAccount }?.message,
                hint = if (refused != null && !refused.noAccount) refused.message else "The email of their TestTrack account, exactly.",
            )
            when {
                busy -> FoundRow(null, email.trim(), null, Modifier.padding(top = 12.dp))
                refused?.noAccount == true ->
                    IconLine(
                        Lucide.Info,
                        t.text4,
                        AnnotatedString("Check the spelling, or ask them to sign in to TestTrack with it first."),
                        tileStyle(12.5f, 17f, FontWeight.Normal),
                        t.text4,
                        Modifier.padding(top = 12.dp),
                    )
                refused != null -> FoundRow(refused.code, refused.email, refusedChip(refused.code), Modifier.padding(top = 12.dp))
            }
            Spacer(Modifier.height(14.dp))
            Primary(
                "Send invite",
                Modifier.fillMaxWidth(),
                busy = busy,
                enabled = ready,
                mark = rememberVectorPainter(Lucide.Mail),
                onClick = send,
            )
        }
    }
}

/** What the chip beside a refused address says: who that account is to this app. */
private fun refusedChip(code: String): Pair<String, Tone> =
    when (code) {
        InviteRefusal.OWN_ACCOUNT -> "You" to Tone.QUIET
        InviteRefusal.ALREADY_TESTING -> "Testing" to Tone.OK
        else -> "Declined" to Tone.BAD
    }

/**
 * The account an address belongs to, in a bordered row under the field: shimmering while the
 * invite is on its way ([code] null), else its address with a chip saying why it can't be asked.
 */
@Composable
private fun FoundRow(
    code: String?,
    email: String,
    chip: Pair<String, Tone>?,
    modifier: Modifier = Modifier,
) {
    CrewCard(modifier, padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (code == null) {
                Skeleton(width = 40.dp, height = 40.dp, corner = 20.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Skeleton(width = 120.dp, height = 15.dp)
                    Spacer(Modifier.height(6.dp))
                    Skeleton(width = 170.dp, height = 12.dp)
                }
                return@Row
            }
            Avatar("", initialOf(email), size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                email,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 19.sp),
                fontWeight = FontWeight.SemiBold,
                color = Tt.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            chip?.let { (text, tone) ->
                Spacer(Modifier.width(8.dp))
                StatusChip(text, tone, size = ChipSize.SMALL)
            }
        }
    }
}
