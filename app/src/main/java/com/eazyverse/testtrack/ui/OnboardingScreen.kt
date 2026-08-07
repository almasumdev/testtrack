package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private enum class Art { THREAD, CAPTURE, GRID }

private data class Page(val title: String, val body: String, val art: Art)

private val PAGES = listOf(
    Page(
        "No more group chat screenshots",
        "Google asks for 12 testers who stay opted in for 14 days in a row. Most groups try to " +
            "track that in a chat thread and lose count by the second day. We keep the record so " +
            "you don't have to.",
        Art.THREAD
    ),
    Page(
        "One tap and your day is done",
        "Open an app from your list. We time how long you spend in it, save the proof to your own " +
            "Drive, and bring you straight back. Nothing to screenshot, nothing to post.",
        Art.CAPTURE
    ),
    Page(
        "See who tested, every day",
        "Your app gets a grid with everyone testing it and all 14 days. If someone quietly stops " +
            "on day two, you'll know on day two instead of at the end.",
        Art.GRID
    )
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.lastIndex
    val skipAlpha by animateFloatAsState(if (last) 0f else 1f, label = "skip")

    Column(Modifier.fillMaxSize()) {

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TestTrack",
                Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone, enabled = !last, modifier = Modifier.alpha(skipAlpha)) {
                Text("Skip")
            }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = PAGES[index]

            // Distance of this page from the settled position, in pages. Zero when it is the one
            // being read, ±1 at rest either side of it — so it doubles as a parallax driver.
            val distance = (pager.currentPage - index) + pager.currentPageOffsetFraction
            val nearness = 1f - distance.absoluteValue.coerceIn(0f, 1f)

            Column(
                Modifier.fillMaxSize().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Illustration(
                    page.art,
                    Modifier
                        .fillMaxWidth(0.82f)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            alpha = nearness
                            scaleX = 0.86f + 0.14f * nearness
                            scaleY = 0.86f + 0.14f * nearness
                            // Drifts against the swipe, so the art trails the text slightly.
                            translationX = distance * size.width * 0.22f
                        }
                )

                Spacer(Modifier.height(44.dp))

                Text(
                    page.title,
                    Modifier.alpha(nearness),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    page.body,
                    Modifier.alpha(nearness * 0.9f),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.bodyLarge.fontSize * 1.5f,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Indicator(pager, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 24.dp))

        Button(
            onClick = {
                if (last) onDone()
                else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).height(56.dp)
        ) {
            Text(
                if (last) "Get started" else "Next",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (!last) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** Dots for the pages behind you, a pill for the one you are on. */
@Composable
private fun Indicator(pager: PagerState, modifier: Modifier = Modifier) {
    Row(modifier) {
        repeat(PAGES.size) { index ->
            val selected = pager.currentPage == index
            val width by animateDpAsState(if (selected) 28.dp else 8.dp, label = "dot$index")
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
                    )
            )
        }
    }
}

/**
 * The page artwork, drawn rather than shipped.
 *
 * Three vectors would be three more files to keep in step with the palette; drawn from the colour
 * scheme they follow the device's dynamic colour for free, in light and dark.
 */
@Composable
private fun Illustration(art: Art, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val danger = MaterialTheme.colorScheme.error
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val halo = listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
    )

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(44.dp))
                .background(Brush.linearGradient(halo))
        )
        Canvas(Modifier.fillMaxSize().padding(30.dp)) {
            when (art) {
                Art.THREAD -> drawThread(accent, muted, surface)
                Art.CAPTURE -> drawCapture(accent, muted)
                Art.GRID -> drawGrid(accent, danger, muted)
            }
        }
    }
}

/** A chat thread: screenshots stacked in bubbles, older ones fading out of reach. */
private fun DrawScope.drawThread(accent: Color, muted: Color, surface: Color) {
    val bubbleH = size.height * 0.21f
    val gap = size.height * 0.075f
    val top = (size.height - (3 * bubbleH + 2 * gap)) / 2f

    repeat(3) { row ->
        // Only the newest message is solid; the ones above it recede into the backlog. Their
        // contents flip to the dark ink because white would vanish on a near-transparent bubble.
        val newest = row == 2
        val bw = size.width * 0.72f
        val x = if (row % 2 == 1) size.width - bw else 0f
        val y = top + row * (bubbleH + gap)
        val ink = if (newest) surface else muted

        drawRoundRect(
            color = if (newest) accent else muted.copy(alpha = 0.14f + 0.10f * row),
            topLeft = Offset(x, y),
            size = Size(bw, bubbleH),
            cornerRadius = CornerRadius(bubbleH * 0.38f)
        )

        val pad = bubbleH * 0.19f
        val thumb = bubbleH - pad * 2
        drawRoundRect(
            color = ink.copy(alpha = if (newest) 0.90f else 0.30f),
            topLeft = Offset(x + pad, y + pad),
            size = Size(thumb, thumb),
            cornerRadius = CornerRadius(thumb * 0.22f)
        )

        val lineX = x + pad * 2 + thumb
        val lineW = bw - (lineX - x) - pad
        drawRoundRect(
            color = ink.copy(alpha = if (newest) 0.85f else 0.28f),
            topLeft = Offset(lineX, y + bubbleH * 0.33f),
            size = Size(lineW, bubbleH * 0.12f),
            cornerRadius = CornerRadius(bubbleH * 0.06f)
        )
        drawRoundRect(
            color = ink.copy(alpha = if (newest) 0.60f else 0.20f),
            topLeft = Offset(lineX, y + bubbleH * 0.55f),
            size = Size(lineW * 0.58f, bubbleH * 0.12f),
            cornerRadius = CornerRadius(bubbleH * 0.06f)
        )
    }
}

/** A phone with the app open, a countdown running, and the shot framed by capture brackets. */
private fun DrawScope.drawCapture(accent: Color, muted: Color) {
    val stroke = size.width * 0.020f
    val phoneW = size.width * 0.46f
    val phoneH = size.height * 0.76f
    val x = (size.width - phoneW) / 2f
    val y = (size.height - phoneH) / 2f
    val radius = CornerRadius(phoneW * 0.17f)

    drawRoundRect(accent.copy(alpha = 0.08f), Offset(x, y), Size(phoneW, phoneH), radius)
    drawRoundRect(
        accent, Offset(x, y), Size(phoneW, phoneH), radius,
        style = Stroke(stroke * 1.3f)
    )

    // The app that was opened.
    val tile = phoneW * 0.34f
    drawRoundRect(
        color = accent,
        topLeft = Offset(x + (phoneW - tile) / 2f, y + phoneH * 0.14f),
        size = Size(tile, tile),
        cornerRadius = CornerRadius(tile * 0.30f)
    )

    // The dwell before the shot is taken.
    val ring = phoneW * 0.21f
    val cx = x + phoneW / 2f
    val cy = y + phoneH * 0.63f
    drawCircle(muted.copy(alpha = 0.25f), ring, Offset(cx, cy), style = Stroke(stroke * 1.6f))
    drawArc(
        color = accent,
        startAngle = -90f,
        sweepAngle = 260f,
        useCenter = false,
        topLeft = Offset(cx - ring, cy - ring),
        size = Size(ring * 2, ring * 2),
        style = Stroke(stroke * 1.6f, cap = StrokeCap.Round)
    )
    drawCircle(accent, ring * 0.34f, Offset(cx, cy))

    // Framing brackets — the shot being taken around it.
    val arm = size.width * 0.13f
    val inset = size.width * 0.01f
    val corners = listOf(
        Triple(Offset(inset, inset), Offset(inset + arm, inset), Offset(inset, inset + arm)),
        Triple(
            Offset(size.width - inset, inset),
            Offset(size.width - inset - arm, inset),
            Offset(size.width - inset, inset + arm)
        ),
        Triple(
            Offset(inset, size.height - inset),
            Offset(inset + arm, size.height - inset),
            Offset(inset, size.height - inset - arm)
        ),
        Triple(
            Offset(size.width - inset, size.height - inset),
            Offset(size.width - inset - arm, size.height - inset),
            Offset(size.width - inset, size.height - inset - arm)
        )
    )
    corners.forEach { (corner, horizontal, vertical) ->
        drawLine(muted.copy(alpha = 0.45f), corner, horizontal, stroke * 1.4f, StrokeCap.Round)
        drawLine(muted.copy(alpha = 0.45f), corner, vertical, stroke * 1.4f, StrokeCap.Round)
    }
}

/** The owner's grid: testers down the side, days across, one gap standing out. */
private fun DrawScope.drawGrid(accent: Color, danger: Color, muted: Color) {
    // '*' posted, '.' missed, ' ' still to come. Mostly kept, with the gaps easy to pick out —
    // which is the point the page is making.
    val pattern = listOf(
        "****** ",
        "***.** ",
        "****** ",
        "*****. "
    )
    val cols = pattern[0].length
    val rows = pattern.size

    val cell = size.width * 0.088f
    val gap = size.width * 0.030f
    val nameW = size.width * 0.20f
    val nameGap = size.width * 0.05f

    val gridW = cols * cell + (cols - 1) * gap
    val gridH = rows * cell + (rows - 1) * gap
    val left = (size.width - (nameW + nameGap + gridW)) / 2f
    val top = (size.height - gridH) / 2f + cell * 0.6f
    val gridLeft = left + nameW + nameGap

    // Day ticks along the top.
    repeat(cols) { c ->
        drawRoundRect(
            color = muted.copy(alpha = 0.30f),
            topLeft = Offset(gridLeft + c * (cell + gap) + cell * 0.25f, top - cell * 0.9f),
            size = Size(cell * 0.5f, cell * 0.22f),
            cornerRadius = CornerRadius(cell * 0.11f)
        )
    }

    pattern.forEachIndexed { r, row ->
        val y = top + r * (cell + gap)

        drawRoundRect(
            color = muted.copy(alpha = 0.28f),
            topLeft = Offset(left, y + cell * 0.28f),
            size = Size(nameW, cell * 0.44f),
            cornerRadius = CornerRadius(cell * 0.22f)
        )

        row.forEachIndexed { c, mark ->
            val color = when (mark) {
                '*' -> accent
                '.' -> danger.copy(alpha = 0.85f)
                else -> muted.copy(alpha = 0.16f)
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(gridLeft + c * (cell + gap), y),
                size = Size(cell, cell),
                cornerRadius = CornerRadius(cell * 0.32f)
            )
        }
    }
}
