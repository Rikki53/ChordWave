// Ported from Mochy Music (GPL-3.0): ui/player/BackdropBlurTransformation.kt
// BackdropBlurTransformation.kt
//
// ADAPTATIONS: package only. Needed by PlaylistDetail.kt's classic
// backdrop; no staged dir had it yet.

package com.music.bitchord.ui.mochy.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap
import coil3.size.Size
import coil3.transform.Transformation

class BackdropBlurTransformation(private val radiusPx: Int) : Transformation() {

    override val cacheKey: String = "${this::class.qualifiedName}-$radiusPx"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        if (radiusPx <= 0) return input
        val width = input.width
        val height = input.height
        if (width == 0 || height == 0) return input

        val pixels = IntArray(width * height)
        input.getPixels(pixels, 0, width, 0, 0, width, height)

        repeat(3) {
            boxBlurHorizontal(pixels, width, height, radiusPx)
            boxBlurVertical(pixels, width, height, radiusPx)
        }

        val output = createBitmap(width, height, input.config ?: Bitmap.Config.ARGB_8888)
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun boxBlurHorizontal(pixels: IntArray, width: Int, height: Int, radius: Int) {
        val row = IntArray(width)
        for (y in 0 until height) {
            val rowStart = y * width
            for (x in 0 until width) row[x] = pixels[rowStart + x]
            for (x in 0 until width) {
                var a = 0; var r = 0; var g = 0; var b = 0; var count = 0
                val from = (x - radius).coerceAtLeast(0)
                val to = (x + radius).coerceAtMost(width - 1)
                for (i in from..to) {
                    val p = row[i]
                    a += (p ushr 24) and 0xFF
                    r += (p ushr 16) and 0xFF
                    g += (p ushr 8) and 0xFF
                    b += p and 0xFF
                    count++
                }
                pixels[rowStart + x] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
    }

    private fun boxBlurVertical(pixels: IntArray, width: Int, height: Int, radius: Int) {
        val col = IntArray(height)
        for (x in 0 until width) {
            for (y in 0 until height) col[y] = pixels[y * width + x]
            for (y in 0 until height) {
                var a = 0; var r = 0; var g = 0; var b = 0; var count = 0
                val from = (y - radius).coerceAtLeast(0)
                val to = (y + radius).coerceAtMost(height - 1)
                for (i in from..to) {
                    val p = col[i]
                    a += (p ushr 24) and 0xFF
                    r += (p ushr 16) and 0xFF
                    g += (p ushr 8) and 0xFF
                    b += p and 0xFF
                    count++
                }
                pixels[y * width + x] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
    }
}

// Batch 55b: from Mochy's BackdropBlurTransformation.kt — builds a small
// coil-cached blurred image request for api < 31 so static module
// backgrounds blur once off the main thread.
@Composable
internal fun rememberFallbackBlurredImage(model: Any?, blurDp: Float, targetPx: Int = 192): Any? {
    val context = LocalContext.current
    val radiusPx = (blurDp * 0.45f).roundToInt().coerceIn(2, 24)
    return remember(context, model, radiusPx, targetPx) {
        if (model == null) null else buildFallbackBlurredRequest(context, model, radiusPx, targetPx)
    }
}

internal fun buildFallbackBlurredRequest(context: Context, model: Any, radiusPx: Int, targetPx: Int = 192): ImageRequest =
    ImageRequest.Builder(context)
        .data(model)
        .allowHardware(false)
        .transformations(BackdropBlurTransformation(radiusPx))
        .size(Size(targetPx, targetPx))
        .build()
