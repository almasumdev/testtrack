package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.LogIn
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Barlow
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * What an account still needs before it can take a seat, drawn inside the crew box.
 *
 * Only the missing steps are listed, as plain rows numbered from 1: a step that is done is never
 * drawn, and a step whose answer has not arrived yet is not drawn either (a row that appears and
 * then goes would be worse than one that appears a moment later). Whether a particular app is ready
 * is not asked here; that is the seat sheet's job, once an app is picked.
 */

internal const val CREW_JOIN_TAG = "crew-join"

/** The account steps, in the order they are listed and done. */
enum class JoinStep(
    val title: String,
    /** What the bar says after "Next:". */
    val next: String,
    val icon: ImageVector,
) {
    SIGN_IN("Sign in", "Sign in", Lucide.LogIn),
    GROUP("Join the testers group", "Join the testers group", Lucide.ArrowRight),
    APP("Add an app in a closed test", "Add an app", Lucide.ArrowRight),
    DRIVE("Connect Google Drive", "Connect Drive", Lucide.ArrowRight),
    TIX("Have ${CrewRules.SEAT_COST} tix", "Earn tix in Circle", Lucide.ArrowRight),
}

/**
 * What is known about the account. A null is "not answered yet": [closedApp] until My apps has
 * been read, [drive] until Google has been asked, [tix] until the balance has arrived.
 */
data class CrewJoinState(
    val signedOut: Boolean = false,
    val member: Boolean = true,
    /** Any app of yours that is still in a closed test (not live on Play), verified or not. */
    val closedApp: Boolean? = null,
    val drive: Boolean? = null,
    val tix: Int? = null,
    /** Usage access and the timer over other apps; asked for apart from the steps. */
    val permissions: Boolean = true,
) {
    /** The steps still to do, in order. Signed out, signing in is the only one that can be known. */
    val missing: List<JoinStep>
        get() =
            if (signedOut) {
                listOf(JoinStep.SIGN_IN)
            } else {
                listOfNotNull(
                    JoinStep.GROUP.takeIf { !member },
                    JoinStep.APP.takeIf { closedApp == false },
                    JoinStep.DRIVE.takeIf { drive == false },
                    JoinStep.TIX.takeIf { tix != null && tix < CrewRules.SEAT_COST },
                )
            }
}

/** The heading over the list: how many are left, or just that something is, when nobody is signed in. */
internal fun joinHeading(
    count: Int,
    signedOut: Boolean,
): String =
    when {
        signedOut -> "Before you can join"
        count == 1 -> "1 step before you can join"
        else -> "$count steps before you can join"
    }

private val HeadingStyle = TextStyle(fontFamily = Barlow, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
private val NowStyle = tileStyle(15f, 19f, FontWeight.SemiBold)
private val TodoStyle = tileStyle(14f, 19f, FontWeight.Normal)
private val BodyStyle = TextStyle(fontFamily = Barlow, fontSize = 13.sp, lineHeight = 18.sp)
private val DotStyle =
    TextStyle(fontFamily = Barlow, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

/**
 * A hairline, the heading, the missing steps and the one bar that does the first of them. The
 * first step is open with its one plain line under it; the rest are one line each. [steps] is
 * never empty.
 */
@Composable
internal fun CrewJoinChecklist(
    steps: List<JoinStep>,
    signedOut: Boolean,
    tix: Int?,
    onNext: (JoinStep) -> Unit,
) {
    val t = Tt.colors
    Column(Modifier.fillMaxWidth().testTag(CREW_JOIN_TAG)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.cardLine))
        Spacer(Modifier.height(14.dp))
        Text(joinHeading(steps.size, signedOut), style = HeadingStyle, color = t.text)
        Spacer(Modifier.height(6.dp))
        steps.forEachIndexed { index, step ->
            StepRow(index + 1, step.title, now = index == 0)
            if (index == 0) StepLine(step, tix, more = steps.size > 1)
        }
        Spacer(Modifier.height(14.dp))
        FloatingActionBar(
            label = steps.first().next,
            onClick = { onNext(steps.first()) },
            icon = rememberVectorPainter(steps.first().icon),
            gutter = 0.dp,
            lead = "Next:",
        )
    }
}

/** A numbered dot and the step's name: the one being done is filled and bolder, the others a ring. */
@Composable
private fun StepRow(
    number: Int,
    title: String,
    now: Boolean,
) {
    val t = Tt.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(22.dp)
                .then(if (now) Modifier.background(t.accent, CircleShape) else Modifier.border(1.5.dp, t.cardLine, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = DotStyle, color = if (now) t.onAccent else t.text4, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            Modifier.weight(1f),
            style = if (now) NowStyle else TodoStyle,
            color = if (now) t.text else t.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The one plain line under the open step. */
@Composable
private fun StepLine(
    step: JoinStep,
    tix: Int?,
    more: Boolean,
) {
    val t = Tt.colors
    val bold = SpanStyle(fontWeight = FontWeight.SemiBold, color = t.text)
    val text =
        buildAnnotatedString {
            when (step) {
                JoinStep.SIGN_IN -> append("Use the Google account you test apps with.")
                JoinStep.GROUP -> append("Your account joins it, so you can install the other apps in your crew from Play.")
                JoinStep.APP -> append("Add the app you want tested, from your phone or from Play. It has to be in a closed test.")
                JoinStep.DRIVE -> append("Each night's proof pictures are saved there, for the app owners.")
                JoinStep.TIX -> {
                    append("A seat is ")
                    withStyle(bold) { append("${CrewRules.SEAT_COST} tix") }
                    append(". You have ")
                    withStyle(bold) { append("${tix ?: 0}") }
                    append(". Review apps in Circle to earn the rest.")
                }
            }
        }
    Text(text, Modifier.padding(top = 2.dp, bottom = if (more) 6.dp else 0.dp), style = BodyStyle, color = t.text2)
}
