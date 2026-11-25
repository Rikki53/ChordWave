// Ported from Mochy Music (GPL-3.0): ui/screens/setup/welcome/WelcomeGirl.kt
// the welcome robot on screen: her layers drawn from the idle loop each frame.
// ChordWave adaptation: the phone is painted into the hand layer (the master
// art holds it up already), so there is no code-drawn phone body between her
// and her fingers — the whole hand-and-phone layer rides the same drift.

package com.music.bitchord.ui.mochy.screens.setup.welcome

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToInt

// the layers decoded at the size she's drawn, so the gpu never shrinks a big picture every frame
private class RobotBitmaps(
    val base: Bitmap,
    val hair: Bitmap,
    val eyeLeft: Bitmap,
    val eyeRight: Bitmap,
    val eyeLeftShut: Bitmap,
    val eyeRightShut: Bitmap,
    val mouth: Bitmap,
    val hand: Bitmap,
    val motionLines: Bitmap,
)

private suspend fun loadRobot(resources: Resources, scale: Float): RobotBitmaps = withContext(Dispatchers.IO) {
    fun load(layer: RobotLayer): Bitmap {
        var sample = 1
        while (scale * sample * 2 <= 1f) sample *= 2
        val raw = BitmapFactory.decodeResource(resources, layer.res, BitmapFactory.Options().apply { inSampleSize = sample })
        val width = (layer.w * scale).roundToInt().coerceAtLeast(1)
        val height = (layer.h * scale).roundToInt().coerceAtLeast(1)
        val sized = if (raw.width == width && raw.height == height) raw else Bitmap.createScaledBitmap(raw, width, height, true)
        if (sized !== raw) raw.recycle()
        // hands the upload to the render thread now, so the first frame she shows up in doesn't stall
        sized.prepareToDraw()
        return sized
    }
    RobotBitmaps(
        base = load(RobotArt.Base),
        hair = load(RobotArt.Hair),
        eyeLeft = load(RobotArt.EyeLeft),
        eyeRight = load(RobotArt.EyeRight),
        eyeLeftShut = load(RobotArt.EyeLeftShut),
        eyeRightShut = load(RobotArt.EyeRightShut),
        mouth = load(RobotArt.Mouth),
        hand = load(RobotArt.Hand),
        motionLines = load(RobotArt.MotionLines),
    )
}

// the robot, sized by the caller to the art frame's shape
@Composable
internal fun WelcomeRobot(
    motion: RobotMotion,
    seconds: FloatState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val rawScale = if (constraints.hasBoundedWidth && constraints.maxWidth > 0) {
            constraints.maxWidth / RobotArt.FrameWidth
        } else {
            0.5f
        }
        val scale = rawScale.coerceAtLeast(0.05f)
        // decoded in 5% steps and never above the art's own size, so a tiny resize doesn't decode again
        val bitmapScale = (ceil(scale * 20f) / 20f).coerceIn(0.05f, 1f)
        val resources = LocalContext.current.resources
        val bits by produceState<RobotBitmaps?>(null, bitmapScale) { value = loadRobot(resources, bitmapScale) }
        val appear = remember { Animatable(0f) }
        LaunchedEffect(bits != null) {
            if (bits != null) appear.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 90f))
        }
        val painter = remember { RobotPainter() }
        val lift = with(LocalDensity.current) { 40.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    seconds.floatValue
                    val shown = appear.value
                    alpha = shown.coerceIn(0f, 1f)
                    translationY = (1f - shown) * lift
                    // breathing lifts her shoulders a touch from the bottom of the frame
                    val breath = motion.pose.breath
                    scaleX = 1f + 0.0012f * breath
                    scaleY = 1f + 0.0045f * breath
                    transformOrigin = TransformOrigin(0.5f, 1f)
                },
        ) {
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        seconds.floatValue
                        val loaded = bits ?: return@drawBehind
                        drawIntoCanvas { painter.drawBody(it.nativeCanvas, scale, motion.pose, loaded) }
                    },
            )
            Spacer(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        seconds.floatValue
                        val loaded = bits ?: return@drawBehind
                        drawIntoCanvas { painter.drawHand(it.nativeCanvas, scale, motion.pose, loaded) }
                    },
            )
        }
    }
}

// draws her straight onto the native canvas: two bent meshes, small patches for the eyes and mouth
private class RobotPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val baseMesh = BandedMesh(RobotPose.BaseCols, RobotPose.BaseRows)
    private val hairMesh = BandedMesh(RobotPose.HairCols, RobotPose.HairRows)

    fun drawBody(canvas: Canvas, scale: Float, pose: RobotPose, bits: RobotBitmaps) {
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(-RobotArt.FrameLeft, -RobotArt.FrameTop)
        baseMesh.draw(canvas, bits.base, pose.baseMesh)
        // the face in the base mesh already turns this much, so everything on it turns along
        canvas.save()
        canvas.rotate(pose.headDeg, RobotArt.NeckX, RobotArt.NeckY)
        drawEye(canvas, bits.eyeLeft, RobotArt.EyeLeft, bits.eyeLeftShut, RobotArt.EyeLeftShut, RobotArt.EyeLeftLid, pose.eyesOpen)
        drawEye(canvas, bits.eyeRight, RobotArt.EyeRight, bits.eyeRightShut, RobotArt.EyeRightShut, RobotArt.EyeRightLid, pose.eyesOpen)
        drawMouth(canvas, bits.mouth, pose.mouthOpen)
        hairMesh.draw(canvas, bits.hair, pose.hairMesh)
        canvas.restore()
        canvas.restore()
    }

    fun drawHand(canvas: Canvas, scale: Float, pose: RobotPose, bits: RobotBitmaps) {
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(-RobotArt.FrameLeft, -RobotArt.FrameTop)
        // the motion strokes sit on the sleeve, so they follow the hand only halfway
        canvas.save()
        canvas.translate(pose.phoneX * 0.5f, pose.phoneY * 0.5f)
        drawLayer(canvas, bits.motionLines, RobotArt.MotionLines, 1f)
        canvas.restore()
        canvas.translate(pose.phoneX, pose.phoneY)
        canvas.rotate(pose.phoneDeg, RobotArt.WristX, RobotArt.WristY)
        drawLayer(canvas, bits.hand, RobotArt.Hand, 1f)
        canvas.restore()
    }

    // the open eye squashes toward its lid as it fades into the shut lash curve, so a blink never pops
    private fun drawEye(canvas: Canvas, open: Bitmap, openLayer: RobotLayer, shut: Bitmap, shutLayer: RobotLayer, lidY: Float, amount: Float) {
        val shown = ((amount - 0.12f) / 0.33f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        if (shown > 0f) {
            canvas.save()
            canvas.scale(1f, 0.15f + 0.85f * amount, openLayer.x + openLayer.w / 2f, lidY)
            drawLayer(canvas, open, openLayer, shown)
            canvas.restore()
        }
        if (shown < 1f) drawLayer(canvas, shut, shutLayer, 1f - shown)
    }

    // the mouth eases from its top lip and widens a hair as it closes, like a soft breath
    private fun drawMouth(canvas: Canvas, mouth: Bitmap, open: Float) {
        val layer = RobotArt.Mouth
        canvas.save()
        canvas.scale(1f + 0.05f * (1f - open), open, layer.x + layer.w / 2f, RobotArt.MouthTop)
        drawLayer(canvas, mouth, layer, 1f)
        canvas.restore()
    }

    private fun drawLayer(canvas: Canvas, bitmap: Bitmap, layer: RobotLayer, alpha: Float) {
        if (alpha <= 0f) return
        paint.alpha = (alpha.coerceAtMost(1f) * 255f).roundToInt()
        rect.set(layer.x, layer.y, layer.right, layer.bottom)
        canvas.drawBitmap(bitmap, null, rect, paint)
        paint.alpha = 255
    }
}
