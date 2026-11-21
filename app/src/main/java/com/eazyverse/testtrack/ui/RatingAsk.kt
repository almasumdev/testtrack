package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.ReviewMoment

/**
 * The five stars, the one rating control in the app: the About page's bank and the fallback ask
 * both use this, so it is learned once.
 *
 * Hollow at rest, filling to the star under the finger, and rating on release over the same star.
 * A finger that slides off rates nothing: a rating is a deliberate press, not a brush past.
 */
@Composable
fun RatingStars(
    modifier: Modifier = Modifier,
    /** Each star's touch box and its drawn size: 48 and 30 on the About page, smaller in a dialog. */
    box: Dp = 48.dp,
    glyph: Dp = 30.dp,
    onRate: (Int) -> Unit,
) {
    val gold = Tt.colors.gold
    var held by remember { mutableIntStateOf(0) }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (star in 1..5) {
            Box(
                Modifier
                    .size(box)
                    .pointerInput(star) {
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
                    Modifier.size(glyph),
                    colorFilter = ColorFilter.tint(gold),
                )
            }
        }
    }
}

/**
 * The ask after a value moment, when Play's own card could not be shown: the About page's rating
 * bank in a dialog, with the two ways out an unbidden ask owes somebody.
 *
 * A low star swaps this same dialog's contents for the feedback ask. One dialog on screen, never a
 * stack to dismiss. "Don't ask again" is only on the first: a low star already answered.
 */
@Composable
fun RatingAsk(onClose: () -> Unit) {
    val context = LocalContext.current
    var low by remember { mutableIntStateOf(0) }

    fun never() {
        ReviewMoment.optOut(context)
        onClose()
    }

    fun later() {
        ReviewMoment.markAsked(context)
        onClose()
    }

    if (low == 0) {
        Ask(
            title = "Enjoying TestTrack?",
            body = "Rate it on Google Play.",
            dismiss = "Not now",
            onDismiss = ::later,
            alt = "Don't ask again",
            onAlt = ::never,
            altQuiet = true,
        ) {
            RatingStars(Modifier.align(Alignment.CenterHorizontally), box = 44.dp, glyph = 28.dp) { stars ->
                if (stars == 5 || !canMail(context)) {
                    ReviewMoment.markRated(context)
                    openPlayListing(context)
                    onClose()
                } else {
                    low = stars
                }
            }
        }
    } else {
        Ask(
            title = "Tell us what could be better",
            body = "We read every message.",
            confirm = "Send feedback",
            onConfirm = {
                ReviewMoment.markAsked(context)
                sendFeedback(context, stars = low)
                onClose()
            },
            dismiss = "Not now",
            onDismiss = ::later,
        )
    }
}
