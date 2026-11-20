package com.eazyverse.testtrack.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.TextStyles.HintStyle
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * How to add the testers group, full screen, the one how-to every page opens: the address again,
 * then each step in Play Console with a picture of where to tap. A picture zooms where it is, see
 * [ZoomableImage].
 */

/** One step of adding the testers group in Play Console, and its picture. */
internal class PlayStep(
    val text: String,
    @param:DrawableRes val picture: Int,
)

/** The four steps, in order. The pictures keep these names when they are replaced. */
internal val PlaySteps =
    listOf(
        PlayStep("Open Closed testing in Play Console", R.drawable.play_step1),
        PlayStep("Open your track, then its Testers tab", R.drawable.play_step2),
        PlayStep("Pick Google Groups, paste the address and save", R.drawable.play_step3),
        PlayStep("Roll out a release to that track", R.drawable.play_step4),
    )

private val StepStyle = tileStyle(15f, 20f, FontWeight.SemiBold)

/**
 * The steps. With [verify] and [onVerify], as from the seat page, the check sits at the end;
 * without them the page is only the address and the steps.
 */
@Composable
internal fun GroupHowToDialog(
    onDismiss: () -> Unit,
    verify: SeatGroupState? = null,
    onVerify: (() -> Unit)? = null,
) {
    val t = Tt.colors
    // The zoom hint sits under the first picture until a picture is zoomed once.
    var hintSeen by rememberSaveable { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogSystemBars(t.isDark)
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxSize().background(t.bg).safeDrawingPadding()) {
            val hair = t.hair
            val lined = scroll.value > 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .drawBehind {
                        if (lined) {
                            val y = size.height - 0.5.dp.toPx()
                            drawLine(hair, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        }
                    }.padding(start = Gutter, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Add the testers group",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    fontWeight = FontWeight.Bold,
                    color = t.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Lucide.X, "Close", Modifier.size(24.dp), tint = t.text)
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(start = Gutter, end = Gutter, top = 8.dp, bottom = 20.dp),
            ) {
                SeatAddressRow()
                PlaySteps.forEachIndexed { index, step ->
                    Spacer(Modifier.height(if (index == 0) 20.dp else 24.dp))
                    StepHeading(index + 1, step.text)
                    Spacer(Modifier.height(10.dp))
                    StepPicture(step) { hintSeen = true }
                    if (index == 0 && !hintSeen) {
                        Spacer(Modifier.height(8.dp))
                        Text("Pinch or double-tap to zoom", style = HintStyle, color = t.text3)
                    }
                }
                Spacer(Modifier.height(28.dp))
                if (verify != null && onVerify != null) {
                    SeatBar(verify.verifyLabel, Lucide.RefreshCw, busy = verify.checking, enabled = !verify.checking, onClick = onVerify)
                }
            }
        }
    }
}

/**
 * Makes this dialog's own window an edge-to-edge page like the rest of the app: no dim, clear bars,
 * and bar icons in the ink the theme needs. A dialog window is not the activity's, so the
 * [TestTrackTheme] flags do not reach it. Call it inside the Dialog content.
 */
@Composable
internal fun DialogSystemBars(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.setDimAmount(0f)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

@Composable
private fun StepHeading(
    number: Int,
    text: String,
) {
    val t = Tt.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(t.tile), contentAlignment = Alignment.Center) {
            Text("$number", style = tileStyle(12.5f, 14f, FontWeight.Bold), color = t.tileInk)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = StepStyle, color = t.text)
    }
}

/** A step's picture across the page at its own shape. It zooms in place; [onZoomed] on the first zoom. */
@Composable
private fun StepPicture(
    step: PlayStep,
    onZoomed: () -> Unit,
) {
    val t = Tt.colors
    val painter = painterResource(step.picture)
    val size = painter.intrinsicSize
    val ratio = if (size.isSpecified && size.height > 0f) size.width / size.height else 16f / 10f
    val shape = RoundedCornerShape(12.dp)
    ZoomableImage(
        painter,
        step.text,
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(shape)
            .border(1.dp, t.glassBorder, shape),
        onZoomed = onZoomed,
    )
}
