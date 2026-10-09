package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.ProofEditing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/** Edits local pixels before Drive upload. Masks become opaque pixels, never removable overlays. */
@Composable
internal fun ProofEditor(
    uri: Uri,
    onKeep: (Uri) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val pictureHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.55f).coerceIn(240.dp, 600.dp)
    val scope = rememberCoroutineScope()
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    val masks = remember(uri) { mutableStateListOf<RectF>() }
    var draft by remember { mutableStateOf<RectF?>(null) }
    var hiding by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transform =
        rememberTransformableState { scale, offset, _ ->
            zoom = (zoom * scale).coerceIn(1f, 4f)
            pan = if (zoom == 1f) Offset.Zero else pan + offset
        }
    LaunchedEffect(uri) {
        try {
            bitmap = ProofEditing.load(context, uri)
        } catch (
            e: CancellationException,
        ) {
            throw e
        } catch (_: Exception) {
            error = "That image could not be opened. Choose another screenshot."
        }
    }
    DisposableEffect(bitmap) {
        val loaded = bitmap
        onDispose { loaded?.recycle() }
    }
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("Check your screenshot", style = MaterialTheme.typography.titleLarge)
            Text(
                "Anyone with the link can view the image saved in your Drive. Hide names, messages and other personal details.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (hiding) "Drag a rectangle to hide details." else "Pinch to zoom and drag to inspect.",
                style = MaterialTheme.typography.bodySmall,
            )
            val picture = bitmap
            val maskInk = Tt.colors.scrim.copy(alpha = 1f)
            Box(
                Modifier
                    .height(pictureHeight)
                    .fillMaxWidth()
                    .clipToBounds()
                    .padding(vertical = 8.dp),
            ) {
                if (picture != null) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .then(if (hiding) Modifier else Modifier.transformable(transform))
                            .graphicsLayer {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                            }.pointerInput(picture, hiding) {
                                if (!hiding) return@pointerInput
                                val fit = min(size.width.toFloat() / picture.width, size.height.toFloat() / picture.height)
                                val width = picture.width * fit
                                val height = picture.height * fit
                                val left = (size.width - width) / 2
                                val top = (size.height - height) / 2

                                fun point(at: Offset) =
                                    Offset(
                                        ((at.x - left) / width).coerceIn(0f, 1f),
                                        ((at.y - top) / height).coerceIn(0f, 1f),
                                    )
                                var start = Offset.Zero
                                detectDragGestures(
                                    onDragStart = {
                                        start = point(it)
                                        draft = null
                                    },
                                    onDragCancel = { draft = null },
                                    onDragEnd = {
                                        draft?.takeIf { it.width() > 0.003f && it.height() > 0.003f }?.let { masks.add(it) }
                                        draft = null
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val end = point(change.position)
                                        draft =
                                            RectF(
                                                min(start.x, end.x),
                                                min(start.y, end.y),
                                                maxOf(start.x, end.x),
                                                maxOf(start.y, end.y),
                                            )
                                    },
                                )
                            },
                    ) {
                        val fit = min(size.width / picture.width, size.height / picture.height)
                        val width = picture.width * fit
                        val height = picture.height * fit
                        val left = (size.width - width) / 2
                        val top = (size.height - height) / 2
                        drawImage(
                            picture.asImageBitmap(),
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize = IntSize(width.roundToInt(), height.roundToInt()),
                        )
                        for (mask in masks.toList() + listOfNotNull(draft)) {
                            drawRect(
                                maskInk,
                                Offset(left + mask.left * width, top + mask.top * height),
                                Size(mask.width() * width, mask.height() * height),
                            )
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("${masks.size} hidden areas", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = !busy, onClick = {
                    hiding = !hiding
                    zoom = 1f
                    pan = Offset.Zero
                }) {
                    Text(if (hiding) "Inspect" else "Hide details")
                }
                TextButton(enabled = masks.isNotEmpty() && !busy, onClick = { masks.removeAt(masks.lastIndex) }) { Text("Undo") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = !busy, onClick = onClose) { Text("Cancel") }
                Button(enabled = picture != null && !busy, onClick = {
                    val image = picture ?: return@Button
                    busy = true
                    scope.launch {
                        try {
                            onKeep(ProofEditing.save(context, image, masks.toList()))
                            onClose()
                        } catch (
                            e: CancellationException,
                        ) {
                            throw e
                        } catch (
                            _: Exception,
                        ) {
                            error = "The edited screenshot could not be saved. Try again."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(if (busy) "Saving" else "Use screenshot") }
            }
        }
    }
}
