package com.eazyverse.testtrack.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.ExternalLink
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Tt

/** Where the policy and the deletion request live. The site owns both pages. */
private const val SITE = "https://eazyverse.com/apps/testtrack"

/** Google's list of apps with access to the account, where Drive access can be removed. */
internal const val GOOGLE_CONNECTIONS = "https://myaccount.google.com/connections"

/**
 * Everything on Profile that is a setting rather than your record, in three groups by what it is
 * for: what testing needs, where to get help and say something, and the account itself.
 *
 * It was one group called Account holding all of it, so the testers group, the privacy policy and
 * Sign out read as the same kind of thing. Now each heading says what is under it, the account's
 * own rows come last with Sign out at the very end, and About has a page of its own.
 */
@Composable
internal fun MeSettings(
    onEdit: () -> Unit,
    onSignOut: () -> Unit,
    drive: Boolean?,
    onDrive: () -> Unit,
    group: Boolean,
    onGroup: () -> Unit,
    notifications: Boolean,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val links = LocalUriHandler.current
    val out: @Composable () -> Unit = { Icon(Lucide.ExternalLink, null, Modifier.size(17.dp), tint = Tt.colors.text4) }
    val gold = Tt.colors.gold

    Column {
        SectionTitle("Testing")
        TileGroup(count = 3) { index, place ->
            when (index) {
                0 ->
                    TileRow(
                        Lucide.Users,
                        "Testers group",
                        place,
                        subtitle = "So owners can add you to tests",
                        value = if (group) "Joined" else "Join",
                        onClick = onGroup,
                    )
                1 ->
                    TileRow(
                        Lucide.HardDrive,
                        "Google Drive",
                        place,
                        subtitle = "Keeps your proof screenshots",
                        value =
                            when (drive) {
                                true -> "Connected"
                                false -> "Connect"
                                null -> "Checking"
                            },
                        onClick = if (drive == null) null else onDrive,
                    )
                else ->
                    TileRow(
                        Lucide.Bell,
                        "Notifications",
                        place,
                        subtitle = "Crew news and nightly reminders",
                        value = if (notifications) "On" else "Off",
                        onClick = { openNotificationSettings(context) },
                    )
            }
        }

        SectionTitle("Support")
        TileGroup(count = 4) { index, place ->
            when (index) {
                0 -> TileRow(Lucide.Info, "About TestTrack", place, subtitle = "Who made it, and more apps", onClick = onAbout)
                1 ->
                    // The stars are drawn in the sentence, not beside it, and are not a control: the
                    // whole row goes to the listing, always (mkit docs/in-app-review.md).
                    TileRow(
                        Lucide.Star,
                        "Enjoying TestTrack?",
                        place,
                        subtitleRich =
                            buildAnnotatedString {
                                append("Rate us ")
                                withStyle(SpanStyle(color = gold)) { append("★★★★★") }
                                append(" on Google Play")
                            },
                        trailing = out,
                    ) { openPlayListing(context) }
                2 ->
                    TileRow(
                        Lucide.MessageSquare,
                        "Send feedback",
                        place,
                        subtitle = "Something broken, or missing",
                        trailing = out,
                    ) { sendFeedback(context) }
                else -> TileRow(Lucide.ShieldCheck, "Privacy policy", place, trailing = out) { links.openUri("$SITE/privacy") }
            }
        }

        SectionTitle("Account")
        TileGroup(count = 3) { index, place ->
            when (index) {
                0 -> TileRow(Lucide.Pencil, "Edit profile", place, subtitle = "Your name and picture", onClick = onEdit)
                1 ->
                    TileRow(
                        Lucide.Trash2,
                        "Request account deletion",
                        place,
                        subtitle = "What is removed, and what is kept",
                        trailing = out,
                    ) { links.openUri("$SITE/delete-account") }
                else -> TileRow(Lucide.LogOut, "Sign out", place, danger = true, onClick = onSignOut)
            }
        }
    }
}

/** This app's page in the system's notification settings, where its channel can be turned on or off. */
private fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
