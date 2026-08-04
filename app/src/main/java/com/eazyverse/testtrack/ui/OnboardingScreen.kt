package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class Page(val title: String, val body: String)

private val PAGES = listOf(
    Page(
        "The thread, replaced",
        "Play wants 12 testers opted in for 14 straight days. Coordinating that in a chat thread " +
            "means scrolling back through hundreds of screenshots to work out who missed a day."
    ),
    Page(
        "Open, and it's recorded",
        "Tap an app in your daily list. It opens, TestTrack captures proof a few seconds later " +
            "and brings you straight back. No screenshotting, no posting, no captions."
    ),
    Page(
        "See your own run",
        "Your app gets a grid: every tester down one side, all 14 days across the top. Gaps are " +
            "obvious on day two instead of day thirteen."
    )
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.lastIndex

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(
            onClick = onDone,
            modifier = Modifier.align(Alignment.End)
        ) { Text(if (last) "" else "Skip") }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = PAGES[index]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    page.body,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(PAGES.size) { index ->
                val selected = pager.currentPage == index
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (selected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        Button(
            onClick = {
                if (last) onDone()
                else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (last) "Get started" else "Next") }
        Spacer(Modifier.height(16.dp))
    }
}
