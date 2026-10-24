package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.ProofEditing
import kotlinx.coroutines.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * A chosen photo, framed for a profile: drag and pinch it inside a circle, the way everybody will
 * see the face, then keep that square. Cropped here rather than on the server so the person decides
 * what is in frame; the server only redraws what it is sent.
 */

/** Pixels on a side of the square handed back: what the server keeps. */
private const val OUT = 400

/** Most it zooms past filling the circle. Beyond this a phone photo is only blur. */
private const val MAX_ZOOM = 4f

/** The circle's share of the frame's width, leaving the dimmed edge that says "outside". */
private const val RING = 0.86f

/**
 * Where the picture is drawn in a frame [frame] px square, holding a circle [ring] px across: its
 * scale and its top left. The picture always covers the circle, so [pan] is held to what keeps it
 * covered; [clamp] gives back the pan that was used.
 */
internal class CropFit(
    private val imageWidth: Int,
    private val imageHeight: Int,
    frame: Float,
    val ring: Float,
    zoom: Float,
    pan: Offset,
) {
    val scale = max(ring / imageWidth, ring / imageHeight) * zoom
    private val width = imageWidth * scale
    private val height = imageHeight * scale
    val clamp =
        Offset(
            pan.x.coerceIn(-(width - ring) / 2, (width - ring) / 2),
            pan.y.coerceIn(-(height - ring) / 2, (height - ring) / 2),
        )
    val left = frame / 2 - width / 2 + clamp.x
    val top = frame / 2 - height / 2 + clamp.y
    private val ringLeft = (frame - ring) / 2

    /** The circle's square, in the picture's own pixels, held inside the picture against rounding. */
    val source: android.graphics.Rect
        get() {
            val side = (ring / scale).roundToInt().coerceIn(1, minOf(imageWidth, imageHeight))
            val x = ((ringLeft - left) / scale).roundToInt().coerceIn(0, imageWidth - side)
            val y = ((ringLeft - top) / scale).roundToInt().coerceIn(0, imageHeight - side)
            return android.graphics.Rect(x, y, x + side, y + side)
        }
}

/** The square inside the circle, as an [OUT] px bitmap of its own. */
private fun cut(
    picture: Bitmap,
    fit: CropFit,
): Bitmap {
    val out = Bitmap.createBitmap(OUT, OUT, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(out).drawBitmap(
        picture,
        fit.source,
        android.graphics.Rect(0, 0, OUT, OUT),
        Paint(Paint.FILTER_BITMAP_FLAG),
    )
    return out
}

@Composable
internal fun PhotoCrop(
    uri: Uri,
    onUse: (Bitmap) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val t = Tt.colors
    var picture by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var pan by remember(uri) { mutableStateOf(Offset.Zero) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(uri) {
        try {
            picture = ProofEditing.load(context, uri)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
        }
    }
    DisposableEffect(picture) {
        val loaded = picture
        onDispose { loaded?.recycle() }
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(t.bg)
                .safeDrawingPadding()
                .padding(Gutter),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Move and pinch to fit",
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 25.sp),
                fontWeight = FontWeight.Bold,
                color = t.text,
            )
            Text(
                if (failed) "That photo could not be opened. Choose another one." else "Your photo shows in a circle.",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = if (failed) t.bad else t.text3,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(CardRadius))
                    .background(t.glass2)
                    .clipToBounds()
                    .onSizeChanged { frame = it }
                    .pointerInput(picture, frame) {
                        val shown = picture ?: return@pointerInput
                        detectTransformGestures { _, moved, scaled, _ ->
                            zoom = (zoom * scaled).coerceIn(1f, MAX_ZOOM)
                            val side = frame.width.toFloat()
                            pan = CropFit(shown.width, shown.height, side, side * RING, zoom, pan + moved).clamp
                        }
                    },
            ) {
                val shown = picture
                if (shown != null && frame.width > 0) {
                    val side = frame.width.toFloat()
                    val fit = CropFit(shown.width, shown.height, side, side * RING, zoom, pan)
                    val image = remember(shown) { shown.asImageBitmap() }
                    val scrim = t.scrim
                    val edge = t.onAccent
                    Canvas(Modifier.fillMaxSize()) {
                        drawImage(
                            image,
                            dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
                            dstSize = IntSize((shown.width * fit.scale).roundToInt(), (shown.height * fit.scale).roundToInt()),
                        )
                        // Everything outside the circle dimmed, so what will be kept is what is lit.
                        val hole = Path().apply { addOval(Rect(center, fit.ring / 2)) }
                        clipPath(hole, ClipOp.Difference) { drawRect(scrim) }
                        drawCircle(edge, fit.ring / 2, center, style = Stroke(2.dp.toPx()))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Secondary("Cancel", Modifier.weight(1f), onClick = onCancel)
                Primary("Use photo", Modifier.weight(1f), enabled = picture != null) {
                    val shown = picture ?: return@Primary
                    val side = frame.width.toFloat()
                    onUse(cut(shown, CropFit(shown.width, shown.height, side, side * RING, zoom, pan)))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
