package com.eazyverse.testtrack.ui

import android.animation.ValueAnimator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.exchangeBackground
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private data class IntroPage(
    val image: Int,
    val eyebrow: Int,
    val title: Int,
    val body: Int,
)

private val IntroPages =
    listOf(
        IntroPage(
            R.drawable.onboarding_together,
            R.string.intro_together_label,
            R.string.intro_together_title,
            R.string.intro_together_body,
        ),
        IntroPage(
            R.drawable.onboarding_feedback,
            R.string.intro_feedback_label,
            R.string.intro_feedback_title,
            R.string.intro_feedback_body,
        ),
        IntroPage(
            R.drawable.onboarding_progress,
            R.string.intro_progress_label,
            R.string.intro_progress_title,
            R.string.intro_progress_body,
        ),
        IntroPage(
            R.drawable.onboarding_share,
            R.string.intro_share_label,
            R.string.intro_share_title,
            R.string.intro_share_body,
        ),
        IntroPage(
            R.drawable.onboarding_explore,
            R.string.intro_explore_label,
            R.string.intro_explore_title,
            R.string.intro_explore_body,
        ),
    )

/** Owns only the pager and the optional, existing Google sign-in sheet. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { IntroPages.size }
    val scope = rememberCoroutineScope()
    var signIn by rememberSaveable { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    val finish = {
        if (!finished) {
            finished = true
            onDone()
        }
    }
    val move: (Int) -> Unit = { page ->
        if (!pager.isScrollInProgress) {
            scope.launch {
                if (ValueAnimator.areAnimatorsEnabled()) {
                    pager.animateScrollToPage(page, animationSpec = tween(280))
                } else {
                    pager.scrollToPage(page)
                }
            }
        }
    }
    BackHandler(enabled = pager.currentPage > 0 && !signIn) { move(pager.currentPage - 1) }
    OnboardingBody(
        pager = pager,
        motionEnabled = ValueAnimator.areAnimatorsEnabled(),
        onNext = { if (pager.currentPage == IntroPages.lastIndex) finish() else move(pager.currentPage + 1) },
        onSkip = finish,
        onSignIn = { signIn = true },
    )
    if (signIn) {
        SignInSheet(reason = StartReason, onDismiss = { signIn = false }, onSignedIn = finish)
    }
}

/** Shared Ocean palette on every page; all state and navigation are supplied by the caller. */
@Composable
fun OnboardingBody(
    pager: PagerState,
    motionEnabled: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onSignIn: () -> Unit,
) {
    val c = Tt.colors
    Column(
        Modifier.fillMaxSize().exchangeBackground(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.testtrack_robot), null, Modifier.size(44.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.intro_brand),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = onSkip) { Text(stringResource(R.string.intro_skip), color = c.text2) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), key = { it }) { index ->
            val distance = if (motionEnabled) (pager.currentPage - index) + pager.currentPageOffsetFraction else 0f
            IntroSlide(IntroPages[index], distance)
        }
        Column(
            Modifier.widthIn(max = 460.dp).fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val progress = stringResource(R.string.intro_page, pager.currentPage + 1, IntroPages.size)
            Row(
                Modifier.padding(vertical = 18.dp).semantics { contentDescription = progress },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(IntroPages.size) { index ->
                    Box(
                        Modifier
                            .size(if (index == pager.currentPage) 24.dp else 7.dp, 7.dp)
                            .clip(CircleShape)
                            .background(if (index == pager.currentPage) c.accent else c.glassBorderStrong),
                    )
                }
            }
            Button(
                onClick = onNext,
                enabled = !pager.isScrollInProgress,
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonHeight),
                shape = RoundedCornerShape(percent = 50),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            ) {
                Text(
                    stringResource(if (pager.currentPage == IntroPages.lastIndex) R.string.intro_start else R.string.intro_next),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
            TextButton(onClick = onSignIn, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)) {
                Text(stringResource(R.string.intro_sign_in), color = c.accentInk, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun IntroSlide(
    page: IntroPage,
    distance: Float,
) {
    val c = Tt.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Art gives up room first. At large text sizes the slide scrolls, never scales down text.
        val artSize = minOf(maxWidth - 36.dp, (maxHeight * .63f).coerceIn(130.dp, 340.dp))
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painterResource(page.image),
                contentDescription = null,
                modifier =
                    Modifier.size(artSize).graphicsLayer {
                        val away = distance.absoluteValue.coerceAtMost(1f)
                        alpha = 1f - away * .35f
                        scaleX = 1f - away * .06f
                        scaleY = scaleX
                        translationX = distance * size.width * .12f * if (rtl) -1f else 1f
                    },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(page.eyebrow),
                style = MaterialTheme.typography.labelLarge,
                color = c.accentInk,
                letterSpacing = 1.4.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(page.title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 35.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(page.body),
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                color = c.text2,
                textAlign = TextAlign.Center,
            )
        }
    }
}
