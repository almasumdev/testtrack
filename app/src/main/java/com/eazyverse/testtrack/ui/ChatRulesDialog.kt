package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/** The full rules, on the site. */
private const val RULES_URL = "https://eazyverse.com/apps/testtrack/rules"

private val RuleLines =
    listOf(
        "Be kind. No insults, bullying or hate.",
        "Keep it about testing. No spam or ads.",
        "No sexual or violent content.",
        "Don't share anyone's private details.",
        "Report anything that breaks these rules.",
    )

/**
 * The chat rules, asked once before a member's first message. "I agree" sends what was waiting;
 * "Not now" (or backing out) sends nothing.
 */
@Composable
internal fun ChatRulesDialog(
    onAgree: () -> Unit,
    onNotNow: () -> Unit,
) {
    val context = LocalContext.current
    Ask(
        title = "Chat rules",
        note = "The team can remove messages and stop members from chatting.",
        confirm = "I agree",
        onConfirm = onAgree,
        dismiss = "Not now",
        onDismiss = onNotNow,
    ) {
        DialogSteps(RuleLines.map { AnnotatedString(it) })
        Spacer(Modifier.height(4.dp))
        // The link's padding goes out into the dialog's margin, so its words line up with the steps.
        InlineAction("Read the full rules", Modifier.offset(x = (-8).dp)) { openUrl(context, RULES_URL) }
    }
}
