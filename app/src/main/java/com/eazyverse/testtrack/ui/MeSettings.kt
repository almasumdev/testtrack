package com.eazyverse.testtrack.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.CirclePlay
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.ReviewMoment

/** Where the policy and the deletion request live. The site owns both pages. */
private const val SITE = "https://eazyverse.com/apps/testtrack"

/**
 * Everything on Profile that is a setting rather than your record, in groups by what it is for:
 * what testing needs, how the app looks, where to get help and say something, and the account.
 *
 * It was one group called Account holding all of it, so the testers group, the privacy policy and
 * Sign out read as the same kind of thing. Now each heading says what is under it, the account's
 * own rows come last with Sign out at the very end, and About has a page of its own.
 */
@Composable
internal fun MeSettings(
    onEdit: () -> Unit,
    onSignOut: () -> Unit,
    group: Boolean,
    onGroup: () -> Unit,
    notifications: Boolean,
    onAbout: () -> Unit,
) {
    val context = LocalContext.current
    val links = LocalUriHandler.current

    Column {
        SectionHeading("Testing", top = MeHeadingTop, bottom = MeHeadingBottom)
        TileGroup(count = 2) { index ->
            if (index == 0) {
                // Joined, it opens the group on the web; not yet, the join steps open here.
                TileRow(
                    Lucide.Users,
                    "Testers group",
                    subtitle = if (group) "Joined, so owners can add you to tests" else "Not joined yet. Join so owners can add you",
                    end = if (group) RowEnd.OUT else RowEnd.PAGE,
                    onClick = onGroup,
                )
            } else {
                TileRow(
                    Lucide.Bell,
                    "Notifications",
                    subtitle = if (notifications) "On for crew news and nightly reminders" else "Off. Turn on for crew news and reminders",
                    end = RowEnd.OUT,
                    onClick = { openNotificationSettings(context) },
                )
            }
        }

        AppearanceGroup()

        SupportGroup(onAbout)

        SectionHeading("Account", top = MeHeadingTop, bottom = MeHeadingBottom)
        TileGroup(count = 2) { index ->
            if (index == 0) {
                TileRow(Lucide.Pencil, "Edit profile", subtitle = "Your name and picture", end = RowEnd.PAGE, onClick = onEdit)
            } else {
                TileRow(
                    Lucide.Trash2,
                    "Request account deletion",
                    subtitle = "What is removed, and what is kept",
                    end = RowEnd.OUT,
                ) { links.openUri("$SITE/delete-account") }
            }
        }
        // Sign out on its own, a step apart, so it is never read as one more account row.
        Spacer(Modifier.height(8.dp))
        TileGroup(count = 1) {
            TileRow(Lucide.LogOut, "Sign out", danger = true, onClick = onSignOut)
        }
    }
}

/**
 * About, rating and feedback: the one group that needs no account, so it is on the You page
 * whether or not anybody is signed in.
 */
@Composable
internal fun SupportGroup(onAbout: () -> Unit) {
    val context = LocalContext.current
    val gold = Tt.colors.gold
    var videos by remember { mutableStateOf(false) }
    if (videos) HowToSheet(onDismiss = { videos = false })
    Column {
        SectionHeading("Support", top = MeHeadingTop, bottom = MeHeadingBottom)
        // Left out on a phone with no mail app, where it could only open a dead end.
        val mailable = remember { canMail(context) }
        // The update row only while a newer version exists; without one the group is as it was.
        val update = LocalUpdateOffer.current
        val support = SUPPORT.filter { (mailable || it != SupportRow.FEEDBACK) && (update != null || it != SupportRow.UPDATE) }
        TileGroup(count = support.size) { index ->
            when (support[index]) {
                SupportRow.VIDEOS ->
                    TileRow(
                        Lucide.CirclePlay,
                        "How-to videos",
                        subtitle = "Step by step, in English and Bangla",
                        end = RowEnd.PAGE,
                        onClick = { videos = true },
                    )
                SupportRow.ABOUT ->
                    TileRow(
                        Lucide.Info,
                        "About TestTrack",
                        subtitle = "Who made it, and more apps",
                        end = RowEnd.PAGE,
                        onClick = onAbout,
                    )
                SupportRow.UPDATE -> update?.let { UpdateRow(it, Lucide.Download) }
                SupportRow.RATE ->
                    // The stars are drawn in the sentence, not beside it, and are not a control: the
                    // whole row goes to the listing, always.
                    TileRow(
                        Lucide.Star,
                        "Enjoying TestTrack?",
                        subtitleRich =
                            buildAnnotatedString {
                                append("Rate us ")
                                withStyle(SpanStyle(color = gold)) { append("★★★★★") }
                                append(" on Google Play")
                            },
                        end = RowEnd.OUT,
                    ) {
                        ReviewMoment.markRated(context)
                        openPlayListing(context)
                    }
                SupportRow.FEEDBACK ->
                    TileRow(
                        Lucide.MessageSquare,
                        "Send feedback",
                        subtitle = "Something broken, or missing",
                        end = RowEnd.OUT,
                    ) { sendFeedback(context) }
            }
        }
    }
}

/** The space over and under each heading on the You page. */
internal val MeHeadingTop = 22.dp
internal val MeHeadingBottom = 8.dp

// No privacy row: the About page carries the policy, in its footer.
private enum class SupportRow { VIDEOS, ABOUT, UPDATE, RATE, FEEDBACK }

private val SUPPORT = SupportRow.entries.toList()

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
