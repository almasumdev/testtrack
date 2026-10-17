package com.eazyverse.testtrack.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Globe
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mail
import com.eazyverse.testtrack.BuildConfig
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt
import java.util.Calendar

/**
 * What TestTrack is, who made it, and how to reach them: the studio's shared About spine (mkit
 * docs/about-screen.md), in this app's own colours, type and buttons.
 *
 * No cards, though every other screen here is made of them. Groups are made by space alone:
 * [HEAD_GAP] under a heading against [BANK_GAP] under a bank, and [PLATE_GAP] where the page
 * changes subject, after the app and before the footer. Every action hands the reader to something
 * this app does not own, so every one can fail, and they fail in silence.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var lowStar by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { PageBar("About", onBack = onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter),
        ) {
            Spacer(Modifier.height(16.dp))
            AppPlate()
            Spacer(Modifier.height(PLATE_GAP))
            RatingBank(
                onRate = { stars ->
                    // Only the fifth reaches Play. The first four ask before anything opens: see
                    // mkit docs/in-app-review.md, which settled this and why.
                    // With no mail app on the phone the low stars have nowhere to go but Play.
                    if (stars == 5 || !canMail(context)) {
                        markAsked(context)
                        openPlayListing(context)
                    } else {
                        lowStar = stars
                    }
                },
            )
            Spacer(Modifier.height(BANK_GAP))
            MakerBank()
            Spacer(Modifier.height(BANK_GAP))
            MoreAppsBank()
            Spacer(Modifier.height(PLATE_GAP))
            LegalBank()
            Spacer(Modifier.height(28.dp))
        }
    }

    if (lowStar > 0) {
        FeedbackAsk(
            onSend = {
                markAsked(context)
                sendFeedback(context, stars = lowStar)
                lowStar = 0
            },
            onLater = { lowStar = 0 },
        )
    }
}

/**
 * The mark, the name, the build and one sentence.
 *
 * The launcher's foreground layer with nothing behind it: its navy robot, cream face and coloured
 * cards read on both grounds, so it needs neither its background tile nor the monochrome layer.
 * Sized off the artwork, not the canvas: the robot spans 352 to 900 across and 294 to 964 down a
 * 1254 viewport, so the canvas is drawn at [ICON_ART] with `requiredSize` inside an [ICON_MARK]
 * box and the transparent safe-zone margin overflows it.
 */
@Composable
private fun ColumnScope.AppPlate() {
    val t = Tt.colors
    Box(Modifier.align(Alignment.CenterHorizontally).size(ICON_MARK), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(ICON_ART))
    }
    Spacer(Modifier.height(14.dp))
    Text(
        "TestTrack",
        Modifier.align(Alignment.CenterHorizontally),
        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 28.sp),
        fontWeight = FontWeight.Bold,
        color = t.text,
    )
    Spacer(Modifier.height(4.dp))
    // Bare, and the short form: a build number answers a question only the developer asks.
    Text(
        "v${BuildConfig.VERSION_NAME}",
        Modifier.align(Alignment.CenterHorizontally),
        style = MaterialTheme.typography.labelMedium,
        color = t.text3,
    )
    Spacer(Modifier.height(14.dp))
    Text(
        "Open each other's apps for fourteen days to get through Play's closed testing, and trade " +
            "real feedback for tix.",
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 21.sp),
        color = t.text2,
        textAlign = TextAlign.Center,
    )
}

/**
 * Five stars, a control rather than a score: hollow at rest, filling to the star under the finger,
 * and routed by the one it lifts on. Nothing shown back, no number beside them.
 */
@Composable
private fun ColumnScope.RatingBank(onRate: (Int) -> Unit) {
    val t = Tt.colors
    BankHeading("Enjoying TestTrack?")
    Spacer(Modifier.height(HEAD_GAP))
    BankLine("Rate it on Google Play and support our work.")
    Spacer(Modifier.height(10.dp))
    var held by remember { mutableIntStateOf(0) }
    Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (star in 1..5) {
            Box(
                Modifier
                    .size(TOUCH_MIN)
                    .pointerInput(star) {
                        // Fill while pressed, rate on release over the same star, nothing if the
                        // finger slid off: a rating is a deliberate press, not a brush past.
                        awaitEachGesture {
                            awaitFirstDown()
                            held = star
                            val up = waitForUpOrCancellation()
                            held = 0
                            if (up != null) onRate(star)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(if (star <= held) R.drawable.ic_star_filled else R.drawable.ic_star_outline),
                    "$star of 5 stars",
                    Modifier.size(30.dp),
                    colorFilter = ColorFilter.tint(t.gold),
                )
            }
        }
    }
}

/** Who made it, then the studio's ask and its two ways of answering it. */
@Composable
private fun ColumnScope.MakerBank() {
    val context = LocalContext.current
    val t = Tt.colors
    BankHeading("Meet the developer")
    Spacer(Modifier.height(HEAD_GAP))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Open almasum.dev", role = Role.Button) { openUrl(context, PORTFOLIO) }
            .heightIn(min = TOUCH_MIN)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cropped to the circle, never letterboxed into it.
        Image(
            painterResource(R.drawable.dev_masum),
            null,
            Modifier.size(48.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Nurullah Al-Masum",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                color = t.text,
            )
            Text(
                "Mobile app developer at EazyVerse Studio",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                color = t.text3,
            )
        }
        Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = t.text4)
    }
    Spacer(Modifier.height(14.dp))
    BankLine(
        "We design and build mobile apps, for Android and iOS. TestTrack is one of ours, and there " +
            "are more on Google Play. If you have one in mind, tell us about it.",
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Filled("Start a project", Modifier.weight(1f), icon = { Icon(Lucide.Mail, null, Modifier.size(17.dp)) }) {
            mail(context, "A project, from TestTrack")
        }
        Outlined("eazyverse.com", Modifier.weight(1f), icon = { Icon(Lucide.Globe, null, Modifier.size(17.dp)) }) {
            openUrl(context, STUDIO_SITE)
        }
    }
}

/** The rest of the studio's shelf, as one button: a list of apps inside an app only rots. */
@Composable
private fun ColumnScope.MoreAppsBank() {
    val context = LocalContext.current
    BankHeading("More from EazyVerse")
    Spacer(Modifier.height(HEAD_GAP))
    BankLine("Everything the studio has on the store, in one place.")
    Spacer(Modifier.height(16.dp))
    Outlined(
        "Google Play",
        Modifier.fillMaxWidth(),
        // Never tinted: it is a trademark, and one colour would flatten all four of its own.
        icon = { Image(painterResource(R.drawable.ic_google_play), null, Modifier.size(18.dp)) },
    ) { openDeveloperPage(context) }
}

/**
 * The two documents and the line that says who stands behind the app. The privacy link is
 * TestTrack's own policy rather than the studio's, because this app has accounts and keeps data the
 * studio's general page does not describe. The year comes from the clock, so it cannot rot.
 */
@Composable
private fun ColumnScope.LegalBank() {
    val context = LocalContext.current
    val t = Tt.colors
    Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        LegalLink("Privacy") { openUrl(context, APP_PRIVACY) }
        Text("·", color = t.text4)
        LegalLink("Terms") { openUrl(context, STUDIO_TERMS) }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "© ${Calendar.getInstance().get(Calendar.YEAR)} EazyVerse Studio",
        Modifier.align(Alignment.CenterHorizontally),
        style = MaterialTheme.typography.labelMedium,
        color = t.text4,
    )
}

@Composable
private fun LegalLink(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .heightIn(min = TOUCH_MIN)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Tt.colors.accentInk)
    }
}

/** A bank's heading, in the app's own section style. */
@Composable
private fun BankHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 19.sp),
        fontWeight = FontWeight.Bold,
        color = Tt.colors.text,
    )
}

@Composable
private fun BankLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        color = Tt.colors.text2,
    )
}

/**
 * The filled pill, sized with [Outlined] so the two sit side by side as a pair: [Primary] is taller
 * and its type larger, which wrapped "Start a project" onto two lines at half the page's width.
 */
@Composable
private fun Filled(
    label: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(percent = 50),
        elevation = null,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides t.onAccent,
            ) { icon() }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold, color = t.onAccent, fontSize = 14.5.sp, maxLines = 1)
    }
}

/** The app's outlined pill, the partner of [Primary] that the "did not load" pages already use. */
@Composable
private fun Outlined(
    label: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(percent = 50),
        border = BorderStroke(1.5.dp, t.accentInk),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides t.accentInk,
            ) { icon() }
        }
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Bold, color = t.accentInk, fontSize = 14.5.sp, maxLines = 1)
    }
}

/**
 * The low-star ask: two answers, and only `Send feedback` opens anything. No `Never` here: this
 * dialog only ever follows a star the person pressed themselves, so there is nothing to opt out of.
 * `Never` belongs to the ask that arrives unbidden after a value moment (mkit docs/in-app-review.md).
 */
@Composable
private fun FeedbackAsk(
    onSend: () -> Unit,
    onLater: () -> Unit,
) = Ask(
    title = "Tell us what could be better",
    body = "We read every message, and it is the fastest way to get it fixed.",
    confirm = "Send feedback",
    onConfirm = onSend,
    dismiss = "Not now",
    onDismiss = onLater,
)

private val ICON_MARK = 84.dp

/** The canvas that puts an [ICON_MARK]-tall robot on screen: 84 × 1254 / 670. */
private val ICON_ART = 157.dp
private val TOUCH_MIN = 48.dp
private val HEAD_GAP = 10.dp
private val BANK_GAP = 26.dp
private val PLATE_GAP = 34.dp
