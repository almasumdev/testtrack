package com.eazyverse.testtrack.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Ticket
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.SignInUi

// The pieces the sign-in sheet is built from, kept apart from the sheet itself (SignInSheet.kt),
// which is what decides which of them to show.

/** What a failure says when nothing more specific than "it did not work" is known. */
internal const val DEFAULT_FAILURE =
    "Something went wrong between Google and TestTrack. Your account is fine. Try again in a moment."

/** What a failure says when the phone is online but TestTrack's own server could not be reached. */
internal const val UNREACHABLE_FAILURE = "Can't reach TestTrack right now. Try again in a moment."

/** The line under the title: 14 on 20, in the second ink. */
@Composable
internal fun Why(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        color = Tt.colors.text2,
    )
}

/** Whatever the person can do, 20 below the line above it. */
@Composable
internal fun Actions(content: @Composable () -> Unit) {
    Spacer(Modifier.height(20.dp))
    content()
}

/**
 * Google asks for its own mark on a plain surface rather than a tinted button, which also keeps it
 * distinct from the app's own primary actions. The plain surface is the sheet's own in light and
 * the page's in dark, where it reads as sunk into the sheet.
 */
@Composable
internal fun GooglePill(onClick: () -> Unit) {
    val t = Tt.colors
    val fill = if (t.isDark) t.bg else t.sheet
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        elevation = null,
        border = BorderStroke(1.dp, t.sheetOutline),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = fill,
                contentColor = t.text,
            ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight),
    ) {
        GoogleMark(Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            "Continue with Google",
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

/**
 * An account row with nothing in it yet: the shape of what is arriving, not a spinner.
 * A circle and two bars, at 46 and 72 per cent of the width.
 */
@Composable
internal fun AccountSkeleton() {
    AccountRowFrame {
        Skeleton(width = 36.dp, height = 36.dp, corner = 18.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Skeleton(height = 11.dp, modifier = Modifier.fillMaxWidth(0.46f))
            Spacer(Modifier.height(8.dp))
            Skeleton(height = 11.dp, modifier = Modifier.fillMaxWidth(0.72f))
        }
    }
}

/** The account row's frame: 64 high, 16 corners, one hairline. */
@Composable
internal fun AccountRowFrame(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .border(1.dp, Tt.colors.sheetOutline, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Who just signed in: their Google picture, or their initial where there is none, with a faint
 * ring either way so a pale picture still has an edge. Without a name the address stands alone.
 */
@Composable
internal fun AccountRow(
    name: String?,
    email: String,
    photo: String?,
) {
    val t = Tt.colors
    val letter = initialOf(name ?: email)
    AccountRowFrame {
        Box(Modifier.size(36.dp).border(1.dp, t.ring, CircleShape)) {
            Avatar(
                photo = photo.orEmpty(),
                letter = letter,
                size = 36.dp,
                letterStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name ?: email,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                color = t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (name != null) {
                Text(
                    email,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = t.text4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * It worked. The account, then either a thin bar while the app loads and the sheet goes on its own,
 * or, when there is something to read first, the card and an OK that the sheet waits for.
 */
@Composable
internal fun SignedIn(
    state: SignInUi.SignedIn,
    onOk: () -> Unit,
) {
    val t = Tt.colors
    // The title row gives 12; the account row sits 16 under it.
    Spacer(Modifier.height(4.dp))
    AccountRow(state.name, state.email, state.photo)
    if (state.starterUsed) {
        Spacer(Modifier.height(12.dp))
        StarterCard()
        Spacer(Modifier.height(20.dp))
        Primary("OK", onClick = onOk)
    } else {
        Spacer(Modifier.height(14.dp))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = t.primary,
            trackColor = t.track,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
    }
}

/** Shown once per account, when this phone gave its starter tix to another one. */
@Composable
internal fun StarterCard() {
    val t = Tt.colors
    Column(
        Modifier
            .fillMaxWidth()
            .tileFrame()
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Lucide.Ticket, size = 30.dp, tone = Tone.WARN)
            Spacer(Modifier.width(10.dp))
            Text(
                "Starter tix already used",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                color = t.text,
            )
        }
        Spacer(Modifier.height(9.dp))
        Text(
            "Each phone gets ${FeedRules.STARTER_TIX} starter tix once, and this one already has. " +
                "You start at 0 and earn tix by reviewing other apps.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = t.text2,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
