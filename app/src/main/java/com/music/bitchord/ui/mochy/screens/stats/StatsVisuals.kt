// Ported from Mochy Music (GPL-3.0): ui/screens/recap/RecapVisuals.kt
// (OdometerText + easing helpers) and RecapStory.kt (Emphasized).
// StatsVisuals.kt
//
// The Stats page's motion pieces: the odometer figures that roll into place
// and the easing helpers its charts share.

package com.music.bitchord.ui.mochy.screens.stats

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.floor

internal val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

internal fun easeOutCubic(t: Float): Float = 1f - (1f - t) * (1f - t) * (1f - t)

internal fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Item [index] of [count]'s share of a staggered 0..1 [progress]; each item runs for [length] of it. */
internal fun stagger(progress: Float, index: Int, count: Int, length: Float = 0.55f): Float {
    if (count <= 1) return progress.coerceIn(0f, 1f)
    val start = index * (1f - length) / (count - 1)
    return ((progress - start) / length).coerceIn(0f, 1f)
}

/**
 * [text] whose digits roll into place like an odometer as [progress] goes 0..1, staggered left to
 * right, while letters rise in. Drawn glyph by glyph with tabular digits, so nothing reflows.
 */
@Composable
internal fun OdometerText(
    text: String,
    style: TextStyle,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: () -> Color = { style.color },
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tnum = remember(style) { style.copy(fontFeatureSettings = "tnum") }
    val full = remember(text, tnum) { measurer.measure(text, tnum, softWrap = false, maxLines = 1) }
    val lefts = remember(full) { FloatArray(text.length) { full.getBoundingBox(it).left } }
    val glyphs = remember(text, tnum) {
        val chars = (text.toSet() + ('0'..'9')).filter { it != ' ' }
        chars.associateWith { measurer.measure(it.toString(), tnum, softWrap = false, maxLines = 1) }
    }
    val width = with(density) { full.size.width.toDp() }
    val height = with(density) { full.size.height.toDp() }
    Spacer(
        modifier = modifier
            .size(width, height)
            .drawWithCache {
                val lineH = full.size.height.toFloat()
                onDrawBehind {
                    val p = progress().coerceIn(0f, 1f)
                    val c = color()
                    for (i in text.indices) {
                        val ch = text[i]
                        if (ch == ' ') continue
                        val local = stagger(p, i, text.length, length = 0.62f)
                        if (local <= 0f) continue
                        val x = lefts[i]
                        if (ch.isDigit()) {
                            val digit = ch - '0'
                            val spins = 1 + i % 3
                            val value = (spins * 10 + digit) * easeOutCubic(local)
                            val base = floor(value).toInt()
                            val frac = value - base
                            val fadeIn = (local * 5f).coerceAtMost(1f)
                            clipRect(left = x - 4f, top = 0f, right = x + (glyphs[ch]?.size?.width ?: 0) + 4f, bottom = lineH) {
                                glyphs['0' + base % 10]?.let { drawText(it, color = c, topLeft = Offset(x, -frac * lineH), alpha = (1f - frac) * fadeIn) }
                                if (frac > 0.001f) {
                                    glyphs['0' + (base + 1) % 10]?.let { drawText(it, color = c, topLeft = Offset(x, (1f - frac) * lineH), alpha = frac * fadeIn) }
                                }
                            }
                        } else {
                            val e = easeOutCubic(local)
                            glyphs[ch]?.let { drawText(it, color = c, topLeft = Offset(x, (1f - e) * lineH * 0.35f), alpha = e) }
                        }
                    }
                }
            },
    )
}
