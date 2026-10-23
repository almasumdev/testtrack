package com.eazyverse.testtrack.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * App icons, as the server keeps them: the apps on this phone to pick from, and an icon ready to send.
 *
 * Every app on My apps is shown to everybody in its crew and in Circle, and one without an icon looks
 * unfinished. Play has the icon once it shows the app; before that, the owner's own phone has it, and
 * so does a picture they choose. Either way it leaves here square, at [SIZE], as a PNG where it fits
 * (icons have transparent corners) and a WebP where a busy picture does not.
 */
object AppIconData {
    /** An app on this phone that can be picked: it has a home screen icon, as every Play app does. */
    data class Launchable(
        val pkg: String,
        val label: String,
    )

    /** An icon ready to send, and the same pixels to show before it is. */
    class Encoded(
        val base64: String,
        val preview: ImageBitmap,
    )

    /** Twice the largest place the app draws an icon; the server keeps it at this size too. */
    private const val SIZE = 192

    /** Base64 characters. The API refuses bodies over 128 KB, and an icon is never near it. */
    private const val BUDGET = 110_000

    /**
     * Every app with a launcher entry, this one aside, by name.
     *
     * The same `<queries>` block that lets a tester's list see the apps under test lets this see
     * them, with no new permission: an app on Play always has a launcher entry.
     */
    fun launchable(context: Context): List<Launchable> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm
            .queryIntentActivities(launcher, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                val label =
                    runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrNull()
                label?.let { Launchable(pkg, it) }
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }

    /** The icon an app has on this phone, ready to send, or null if it is not here. */
    fun ofInstalled(
        context: Context,
        pkg: String,
    ): Encoded? {
        val drawable = runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull() ?: return null
        val bitmap = runCatching { drawable.toBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return null
        return encode(bitmap)
    }

    /**
     * A picture somebody chose, cropped to its centre square, or null if it cannot be read.
     *
     * Bounds first, so a twelve megapixel photo is sampled down before its pixels are allocated, as
     * [Photo.encode] does for faces. ImageDecoder where there is one, because it also turns a
     * photo the right way up.
     */
    fun ofPicture(
        context: Context,
        uri: Uri,
    ): Encoded? {
        val source = runCatching { decode(context, uri) }.getOrNull() ?: return null
        val edge = min(source.width, source.height)
        if (edge <= 0) return null
        val square =
            Bitmap.createBitmap(source, (source.width - edge) / 2, (source.height - edge) / 2, edge, edge)
        val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        return encode(scaled)
    }

    private fun decode(
        context: Context,
        uri: Uri,
    ): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            return android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val smallest = min(info.size.width, info.size.height)
                val sample = max(1, smallest / (SIZE * 2))
                decoder.setTargetSampleSize(sample)
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val smallest = min(bounds.outWidth, bounds.outHeight)
        if (smallest <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = max(1, smallest / (SIZE * 2)) }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun encode(bitmap: Bitmap): Encoded? {
        val png = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, png)
        var text = Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP)
        if (text.length > BUDGET) {
            val lossy = ByteArrayOutputStream()

            @Suppress("DEPRECATION")
            val webp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
            bitmap.compress(webp, 85, lossy)
            text = Base64.encodeToString(lossy.toByteArray(), Base64.NO_WRAP)
        }
        if (text.length > BUDGET) return null
        return Encoded(text, bitmap.asImageBitmap())
    }
}
