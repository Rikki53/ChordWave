// Ported from Mochy Music (GPL-3.0): ui/screens/setup/welcome/GirlRig.kt
// ChordWave's robot character: the same layered idle rig as Mochy's welcome
// girl — breathing, flowing hair, small easy sways — re-cut from ChordWave's
// own robot master art (white robot, blue bob, mint ribbon on her chest).
// The motion math is Mochy's, unchanged; only the art coordinates differ.

package com.music.bitchord.ui.mochy.screens.setup.welcome

import com.music.bitchord.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// one cut-out layer of the art, placed by its top-left corner in the 1360 x 1824 canvas it was cut from
internal class RobotLayer(val res: Int, val x: Float, val y: Float, val w: Float, val h: Float) {
    val right get() = x + w
    val bottom get() = y + h
}

internal object RobotArt {
    // the robot plus a little room for her hair to swing into, the rest of the canvas is empty
    const val FrameLeft = 200f
    const val FrameTop = 140f
    const val FrameWidth = 1020f
    const val FrameHeight = 1684f

    // how far across the frame her fingertips reach, so layouts can keep the phone on screen
    const val FingertipsAcross = 0.975f

    val Base = RobotLayer(R.drawable.onboarding_robot_base, 220f, 150f, 980f, 1674f)
    val Hair = RobotLayer(R.drawable.onboarding_robot_hair, 370f, 157f, 569f, 606f)
    val EyeLeft = RobotLayer(R.drawable.onboarding_robot_eye_l, 492f, 430f, 112f, 60f)
    val EyeRight = RobotLayer(R.drawable.onboarding_robot_eye_r, 672f, 445f, 116f, 60f)
    val EyeLeftShut = RobotLayer(R.drawable.onboarding_robot_eye_l_closed, 492f, 455f, 112f, 36f)
    val EyeRightShut = RobotLayer(R.drawable.onboarding_robot_eye_r_closed, 672f, 470f, 116f, 36f)
    val Mouth = RobotLayer(R.drawable.onboarding_robot_mouth, 588f, 542f, 110f, 100f)
    val Hand = RobotLayer(R.drawable.onboarding_robot_phone_hand, 880f, 475f, 325f, 535f)
    val MotionLines = RobotLayer(R.drawable.onboarding_robot_motion, 855f, 790f, 70f, 200f)

    // the points things turn on: her neck joint, the wrist under the phone, the wrist of the hand on her hip
    const val NeckX = 655f
    const val NeckY = 730f
    const val WristX = 1050f
    const val WristY = 1030f
    const val RestingWristX = 405f
    const val RestingWristY = 1430f

    // eyes shut toward the bottom of the closed lash curve, the mouth eases from its top lip
    val EyeLeftLid = EyeLeftShut.y + EyeLeftShut.h * 0.8f
    val EyeRightLid = EyeRightShut.y + EyeRightShut.h * 0.8f
    val MouthTop = Mouth.y + 10f
}

// everything that moves this frame, filled in place so drawing never allocates
internal class RobotPose {
    var breath = 0f
    var headDeg = 0f
    var phoneDeg = 0f
    var phoneX = 0f
    var phoneY = 0f
    var eyesOpen = 1f
    var mouthOpen = 1f
    val baseMesh = FloatArray((BaseCols + 1) * (BaseRows + 1) * 2)
    val hairMesh = FloatArray((HairCols + 1) * (HairRows + 1) * 2)

    // where a point of the phone ends up this frame, the same turn the fingers and the cuff get
    fun phoneMoveX(x: Float, y: Float): Float {
        val a = Math.toRadians(phoneDeg.toDouble())
        return RobotArt.WristX + (x - RobotArt.WristX) * cos(a).toFloat() - (y - RobotArt.WristY) * sin(a).toFloat() + phoneX
    }

    fun phoneMoveY(x: Float, y: Float): Float {
        val a = Math.toRadians(phoneDeg.toDouble())
        return RobotArt.WristY + (x - RobotArt.WristX) * sin(a).toFloat() + (y - RobotArt.WristY) * cos(a).toFloat() + phoneY
    }

    companion object {
        // fine enough that a drawstring a few pixels wide can swing on its own
        const val BaseCols = 54
        const val BaseRows = 92
        const val HairCols = 14
        const val HairRows = 14
    }
}

private const val TwoPi = (2 * PI).toFloat()

// the whole idle motion repeats every this many seconds, so it loops like a wallpaper
private const val LoopSeconds = 8f

private fun smoothstep(from: Float, to: Float, x: Float): Float {
    val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
}

// one blink as open-ness over its own seconds: an eased close, a short hold, a softer reopen
private fun blinkCurve(s: Float): Float = when {
    s < 0f -> 1f
    s < 0.12f -> 1f - smoothstep(0f, 0.12f, s)
    s < 0.18f -> 0f
    s < 0.4f -> smoothstep(0.18f, 0.4f, s)
    else -> 1f
}

// a hanging drawstring from where it leaves the hood down to its tip
private class Drawstring(val topX: Float, val topY: Float, val tipX: Float, val tipY: Float, val phase: Float)

// how much each base point follows one of the moving parts, worked out once
private class BaseWeights(val index: Int, val head: Float, val cuff: Float, val hand: Float, val string: Float, val stringPhase: Float)

internal class RobotMotion(seed: Long = System.nanoTime()) {
    val pose = RobotPose()

    private val random = Random(seed)
    private var blinkAt = 1.6f
    private var blinkTwice = false

    private val baseRest = restGrid(RobotArt.Base, RobotPose.BaseCols, RobotPose.BaseRows)
    private val hairRest = restGrid(RobotArt.Hair, RobotPose.HairCols, RobotPose.HairRows)
    private val moving: Array<BaseWeights> = baseWeights()

    // how far down a strand each hair point sits: roots at the crown stay put, tips swing the most
    private val hairReach = FloatArray(hairRest.size / 2) {
        ((hairRest[2 * it + 1] - HairRootY) / (HairTipY - HairRootY)).coerceIn(0f, 1f).pow(1.35f)
    }

    // the still pose, for when animations are switched off
    init {
        baseRest.copyInto(pose.baseMesh)
        hairRest.copyInto(pose.hairMesh)
    }

    fun update(t: Float) {
        val p = pose
        val loop = t / LoopSeconds
        val turn = TwoPi * loop
        p.breath = sin(turn * 2f)
        p.headDeg = 0.75f * sin(turn) + 0.2f * sin(turn * 2f + 0.8f)
        p.phoneDeg = 0.5f * sin(turn + 1.9f)
        p.phoneX = 1f * sin(turn + 2.4f)
        p.phoneY = 1.4f * sin(turn * 2f + 0.5f)
        p.eyesOpen = blinkAt(t)
        // the mouth eases a touch with each breath, never enough to look like talking
        p.mouthOpen = 1f - 0.07f * (0.5f - 0.5f * cos(turn * 2f))
        fillBaseMesh(p, turn)
        fillHairMesh(p, turn)
    }

    // blinks are the one thing left random, a few seconds apart and now and then a quick double
    private fun blinkAt(t: Float): Float {
        while (t > blinkAt + 1f) {
            blinkAt += 2.4f + random.nextFloat() * 3.4f
            blinkTwice = random.nextFloat() < 0.22f
        }
        val s = t - blinkAt
        val second = if (blinkTwice) blinkCurve(s - 0.42f) else 1f
        return min(blinkCurve(s), second)
    }

    // the face tilts from the neck, the cuff rides with the phone, the resting hand eases, the drawstrings swing
    private fun fillBaseMesh(p: RobotPose, turn: Float) {
        val out = p.baseMesh
        val handDeg = 1.1f * sin(turn + 0.6f) + 0.3f * sin(turn * 2f)
        for (w in moving) {
            val i = w.index
            var x = baseRest[2 * i]
            var y = baseRest[2 * i + 1]
            if (w.head > 0f) {
                val a = Math.toRadians((p.headDeg * w.head).toDouble())
                val c = cos(a).toFloat()
                val s = sin(a).toFloat()
                val dx = x - RobotArt.NeckX
                val dy = y - RobotArt.NeckY
                x = RobotArt.NeckX + dx * c - dy * s
                y = RobotArt.NeckY + dx * s + dy * c
            }
            if (w.cuff > 0f) {
                val mx = p.phoneMoveX(x, y)
                val my = p.phoneMoveY(x, y)
                x += (mx - x) * w.cuff
                y += (my - y) * w.cuff
            }
            if (w.hand > 0f) {
                val a = Math.toRadians((handDeg * w.hand).toDouble())
                val c = cos(a).toFloat()
                val s = sin(a).toFloat()
                val dx = x - RobotArt.RestingWristX
                val dy = y - RobotArt.RestingWristY
                x = RobotArt.RestingWristX + dx * c - dy * s
                y = RobotArt.RestingWristY + dx * s + dy * c
            }
            if (w.string > 0f) {
                x += 3.2f * w.string * sin(turn * 2f + w.stringPhase)
            }
            out[2 * i] = x
            out[2 * i + 1] = y
        }
    }

    // a soft wave runs down the strands and swells once a loop, all of it repeating every loop
    private fun fillHairMesh(p: RobotPose, turn: Float) {
        val out = p.hairMesh
        val swell = 1f + 0.22f * sin(turn)
        for (i in hairReach.indices) {
            val x = hairRest[2 * i]
            val y = hairRest[2 * i + 1]
            val f = hairReach[i]
            if (f <= 0f) {
                out[2 * i] = x
                out[2 * i + 1] = y
                continue
            }
            val side = (x - 665f) / 290f
            val phase = turn * 2f - 2.4f * f + 0.5f * side
            val dx = 5.5f * swell * f * sin(phase) + 1.6f * f * sin(2f * phase + 0.7f)
            out[2 * i] = x + dx
            out[2 * i + 1] = y - 0.25f * abs(dx) * f
        }
    }

    private fun baseWeights(): Array<BaseWeights> {
        val strings = listOf(
            Drawstring(443f, 845f, 438f, 1225f, phase = 0f),
            Drawstring(768f, 815f, 772f, 1145f, phase = 1.1f),
        )
        val list = ArrayList<BaseWeights>()
        for (i in 0 until baseRest.size / 2) {
            val x = baseRest[2 * i]
            val y = baseRest[2 * i + 1]
            val head = ellipseWeight(x, y, 640f, 470f, 215f, 245f, 1.25f)
            val cuff = smoothstep(0f, 1f, 1f - hypot(x - 1050f, y - 1030f) / 190f)
            val hand = ellipseWeight(x, y, 405f, 1500f, 95f, 120f, 1.3f)
            var string = 0f
            var stringPhase = 0f
            for (s in strings) {
                val weight = stringWeight(s, x, y)
                if (weight > string) {
                    string = weight
                    stringPhase = s.phase
                }
            }
            if (head > 0f || cuff > 0f || hand > 0f || string > 0f) list += BaseWeights(i, head, cuff, hand, string, stringPhase)
        }
        return list.toTypedArray()
    }

    private companion object {
        const val HairRootY = 230f
        const val HairTipY = 730f

        fun restGrid(layer: RobotLayer, cols: Int, rows: Int) = FloatArray((cols + 1) * (rows + 1) * 2).also { out ->
            var i = 0
            for (r in 0..rows) for (c in 0..cols) {
                out[i++] = layer.x + layer.w * c / cols
                out[i++] = layer.y + layer.h * r / rows
            }
        }

        // 1 inside the ellipse, fading out past it so a turn bends the edges instead of tearing them
        fun ellipseWeight(x: Float, y: Float, cx: Float, cy: Float, rx: Float, ry: Float, fadeTo: Float): Float {
            val nx = (x - cx) / rx
            val ny = (y - cy) / ry
            return 1f - smoothstep(1f, fadeTo, sqrt(nx * nx + ny * ny))
        }

        // still where the string leaves the hood, swinging most at its tip, and only right around it
        fun stringWeight(s: Drawstring, x: Float, y: Float): Float {
            val along = (y - s.topY) / (s.tipY - s.topY)
            if (along <= 0f || along > 1.1f) return 0f
            val lineX = s.topX + (s.tipX - s.topX) * along.coerceAtMost(1f)
            val across = 1f - smoothstep(9f, 24f, abs(x - lineX))
            val past = 1f - smoothstep(1f, 1.1f, along)
            return along.coerceAtMost(1f).pow(1.3f) * across * past
        }
    }
}
