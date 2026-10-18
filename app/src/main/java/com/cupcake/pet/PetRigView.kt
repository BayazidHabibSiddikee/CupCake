package com.cupcake.pet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Companion gait the floating pet is currently performing. */
enum class Gait { IDLE, WALK, DANCE }

/**
 * Code-drawn articulated copy of the CupCake robot sprite.
 *
 * The sprite art is a flat robot: rounded head with a dark screen face, torso with
 * a belly panel, capsule arms hanging from shoulder bolts, and two rounded feet.
 * Redrawing it in code lets every part animate independently — arms swing while
 * walking, feet step, the head bobs, and the screen face redraws itself per
 * emotion with blinking and a talking mouth — with no asset slicing at all.
 *
 * Geometry lives in the original 512x512 sprite coordinate space and is uniformly
 * scaled to the view, so the rig reads as the same robot as the avatar art.
 */
class PetRigView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    companion object {
        private const val FRAME_MS = 33L

        const val EMOTION_NORMAL = "normal"
        private val EMOTION_SET = setOf(
            "normal", "happy", "angry", "sad", "sleepy",
            "surprised", "confused", "thinking", "annoyed",
        )

        private const val HEAD_BLUE = 0xFF5B8CEF.toInt()
        private const val HEAD_TOPBAR = 0xFF78A4F7.toInt()
        private const val SIDE_TAB = 0x969DBBF4.toInt()
        private const val BORDER_WHITE = 0xFFECF1FA.toInt()
        private const val SCREEN_DARK = 0xFF0A0F1C.toInt()
        private const val FACE_CYAN = 0xFF6FE8FF.toInt()
        private const val TORSO_BLUE = 0xFF5D8DF0.toInt()
        private const val BELLY_WHITE = 0xFFF4F6FA.toInt()
        private const val BELLY_DOT = 0xFF41C4F2.toInt()
        private const val BELLY_SPECK = 0x66E2E6F0.toInt()
        private const val LIMB_OUTLINE = 0xFF4679DB.toInt()
        private const val BOLT_NAVY = 0xFF3E6FCC.toInt()
        private const val SOLE_LIGHT = 0xFF6F97E8.toInt()
        private const val FLOOR_SHADOW = 0x593A4257.toInt()

        private const val HEAD_L = 130f
        private const val HEAD_R = 382f
        private const val HEAD_B = 266f
        private const val HEAD_RAD = 64f
        private const val TOPBAR_R = 22f

        private const val BORDER_L = 168f
        private const val BORDER_R = 344f
        private const val BORDER_T = 112f
        private const val BORDER_B = 234f
        private const val BORDER_RAD = 42f
        private const val SCREEN_INSET = 13f

        private const val EYE_W = 38f
        private const val EYE_H = 48f
        private const val EYE_RAD = 17f
        private const val EYE_GAP = 128f
        private const val EYE_CY = 178f
        private const val MOUTH_CX = 256f
        private const val MOUTH_CY = 196f

        private const val TORSO_L = 140f
        private const val TORSO_R = 372f
        private const val TORSO_T = 262f
        private const val TORSO_B = 448f
        private const val TORSO_RAD = 50f
        private const val BELLY_L = 187f
        private const val BELLY_R = 327f
        private const val BELLY_T = 290f
        private const val BELLY_B = 402f
        private const val BELLY_RAD = 40f
        private const val BELLY_DOT_R = 11f
        private const val BELLY_DOT_CY = 318f
        private const val VENT_X_L = 214f
        private const val VENT_X_R = 298f
        private const val VENT_Y0 = 342f

        private const val SHOULDER_L = 152f
        private const val SHOULDER_R = 360f
        private const val SHOULDER_CY = 316f
        private const val BOLT_R = 17f
        private const val ARM_W = 52f
        private const val ARM_LEN = 116f
        private const val HAND_R = 20f

        private const val FOOT_TOP = 438f
        private const val FOOT_H = 50f
        private const val FOOT_RAD = 20f
        private const val FOOT_L0 = 124f
        private const val FOOT_L1 = 252f
        private const val FOOT_R0 = 262f
        private const val FOOT_R1 = 394f
        private const val SHADOW_CY = 486f
        private const val SHADOW_RX = 152f
        private const val SHADOW_RY = 14f

        private const val ROBOT_L = 96f
        private const val ROBOT_T = 57f
        private const val ROBOT_R = 416f
        private const val ROBOT_B = 476f
    }

    var emotion = EMOTION_NORMAL
        set(value) {
            field = if (value in EMOTION_SET) value else com.cupcake.ai.EmotionAssets.canonicalStem(value)
        }
    var gait: Gait = Gait.IDLE
    var facingLeft = false
    var speaking = false
    var frozen = false

    private var tick = 0L
    private var phase = 0.0
    private var nextBlinkAt = 90L
    private var blinkTicks = 0
    private var mouthTick = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    private val handler = Handler(Looper.getMainLooper())
    private val frame = object : Runnable {
        override fun run() {
            step()
            handler.postDelayed(this, FRAME_MS)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.post(frame)
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(frame)
        super.onDetachedFromWindow()
    }

    fun step() {
        tick++
        phase += when (gait) {
            Gait.WALK -> 0.14
            Gait.DANCE -> 0.22
            Gait.IDLE -> 0.035
        }
        if (phase > 1e9) phase = 0.0
        if (!frozen) {
            if (tick >= nextBlinkAt) {
                blinkTicks = 4
                nextBlinkAt = tick + 110 + (0..70).random()
            }
            if (blinkTicks > 0) blinkTicks--
        }
        if (speaking) mouthTick++ else mouthTick = 0
        invalidate()
    }

    /** Current canvas during a draw pass (set in onDraw, used by the draw helpers). */
    private lateinit var canvas: Canvas

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        canvas = c
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val robotW = ROBOT_R - ROBOT_L
        val robotH = ROBOT_B - ROBOT_T
        val s = min(w / robotW, h / robotH)
        val padX = (w - robotW * s) / 2f
        val padY = (h - robotH * s) / 2f

        c.save()
        if (facingLeft) c.scale(-1f, 1f, w / 2f, padY)
        c.translate(padX - ROBOT_L * s, padY - ROBOT_T * s)
        c.scale(s, s)

        val t = 2 * Math.PI * phase
        val bodyBob = when (gait) {
            Gait.WALK -> 4.0 * sin(2 * t)
            Gait.DANCE -> -14.0 * abs(sin(t))
            Gait.IDLE -> 2.0 * sin(t)
        }
        val bodySway = when (gait) {
            Gait.WALK, Gait.DANCE -> 4.0 * sin(t)
            Gait.IDLE -> 0.0
        }
        val rigTilt = when (gait) {
            Gait.DANCE -> 6.5f * sin(t).toFloat()
            else -> 0f
        }

        drawShadow(bodyBob, gait)
        drawFoot(gait, true, t, bodySway, rigTilt, bodyBob)
        drawFoot(gait, false, t, bodySway, rigTilt, bodyBob)

        canvas.save()
        canvas.translate((bodySway * 0.35f).toFloat(), bodyBob.toFloat())
        canvas.rotate(rigTilt, 256f, TORSO_B)
        drawTorso()
        drawArm(gait, true, t, rigTilt, bodyBob)
        drawArm(gait, false, t, rigTilt, bodyBob)
        drawHead(gait, t, bodyBob)
        canvas.restore()

        canvas.restore()
    }

    private fun drawShadow(bodyBob: Double, gait: Gait) {
        paint.style = Paint.Style.FILL
        paint.color = FLOOR_SHADOW
        val lift = max(0.0, -bodyBob) / (if (gait == Gait.DANCE) 14.0 else 4.0)
        val rx = SHADOW_RX * (1f - min(0.3f, lift.toFloat() * 0.35f))
        rect.set(256f - rx, SHADOW_CY - SHADOW_RY, 256f + rx, SHADOW_CY + SHADOW_RY)
        canvas.drawOval(rect, paint)
    }

    private fun drawFoot(gait: Gait, left: Boolean, t: Double, sway: Double, tilt: Float, bob: Double) {
        val x0 = if (left) FOOT_L0 else FOOT_R0
        val x1 = if (left) FOOT_L1 else FOOT_R1
        val stepDy = when (gait) {
            Gait.WALK -> -9.0 * max(0.0, (if (left) sin(t) else -sin(t)))
            Gait.DANCE -> -9.0 * max(0.0, (if ((tick / 5L + (if (left) 0L else 1L)) % 2L == 0L) 1.0 else 0.0))
            Gait.IDLE -> 0.0
        } + bob
        val stepSway = when (gait) {
            Gait.WALK -> (if (left) sway else -sway) * 0.6
            Gait.DANCE -> sway * 0.4
            Gait.IDLE -> 0.0
        }
        paint.color = BOLT_NAVY
        rect.set((x0 + stepSway).toFloat(), stepDy.toFloat() + FOOT_TOP,
            (x1 + stepSway).toFloat(), stepDy.toFloat() + FOOT_TOP + FOOT_H)
        canvas.drawRoundRect(rect, FOOT_RAD, FOOT_RAD, paint)
        paint.color = SOLE_LIGHT
        rect.inset(9f, 8f)
        if (rect.width() > 20) canvas.drawRoundRect(rect, FOOT_RAD - 8, FOOT_RAD - 8, paint)
    }

    private fun drawTorso() {
        paint.style = Paint.Style.FILL
        paint.color = TORSO_BLUE
        rect.set(TORSO_L, TORSO_T, TORSO_R, TORSO_B)
        canvas.drawRoundRect(rect, TORSO_RAD, TORSO_RAD, paint)

        paint.color = BELLY_WHITE
        rect.set(BELLY_L, BELLY_T, BELLY_R, BELLY_B)
        canvas.drawRoundRect(rect, BELLY_RAD, BELLY_RAD, paint)

        paint.color = BELLY_DOT
        canvas.drawCircle(256f, BELLY_DOT_CY, BELLY_DOT_R, paint)

        paint.color = BELLY_SPECK
        for (i in 0..2) {
            canvas.drawCircle(VENT_X_L, VENT_Y0 + i * 15f, 4.5f, paint)
            canvas.drawCircle(VENT_X_R, VENT_Y0 + i * 15f, 4.5f, paint)
        }
    }

    private fun drawArm(gait: Gait, left: Boolean, t: Double, tilt: Float, bob: Double) {
        val sx = if (left) SHOULDER_L else SHOULDER_R
        val angle = when (gait) {
            Gait.WALK -> 15.0 * (if (left) sin(t) else -sin(t))
            Gait.DANCE -> 42.0 * (if (left) sin(t * 1.5) else -sin(t * 1.5))
            Gait.IDLE -> (if (left) -3.0 else 3.0) + 2.5 * sin(t)
        }.toFloat()
        canvas.save()
        canvas.translate(0f, bob.toFloat())
        canvas.rotate(tilt, 256f, TORSO_B)
        canvas.rotate(angle, sx, SHOULDER_CY)

        paint.color = BOLT_NAVY
        canvas.drawCircle(sx, SHOULDER_CY, BOLT_R, paint)

        paint.color = LIMB_OUTLINE
        rect.set(sx - ARM_W / 2 - 5, SHOULDER_CY - 12, sx + ARM_W / 2 + 5, SHOULDER_CY + ARM_LEN)
        canvas.drawRoundRect(rect, 28f, 28f, paint)
        paint.color = Color.argb(255, 244, 246, 250)
        rect.set(sx - ARM_W / 2, SHOULDER_CY - 8, sx + ARM_W / 2, SHOULDER_CY + ARM_LEN - 10)
        canvas.drawRoundRect(rect, 24f, 24f, paint)

        paint.color = BOLT_NAVY
        canvas.drawCircle(sx, SHOULDER_CY + ARM_LEN - 14, HAND_R, paint)
        paint.color = Color.argb(255, 244, 246, 250)
        rect.set(sx - HAND_R + 5, SHOULDER_CY + ARM_LEN - 14 - HAND_R + 6,
            sx + HAND_R - 5, SHOULDER_CY + ARM_LEN - 14 + HAND_R - 4)
        canvas.drawRoundRect(rect, HAND_R - 5, HAND_R - 5, paint)
        canvas.restore()
    }

    private fun drawHead(gait: Gait, t: Double, bob: Double) {
        val headBob = when (gait) {
            Gait.WALK -> 2.5f * sin(2 * t + 0.9).toFloat()
            Gait.DANCE -> -4f * abs(sin(t)).toFloat()
            Gait.IDLE -> 1.5f * sin(t + 0.6).toFloat()
        }
        canvas.save()
        canvas.translate(0f, headBob + bob.toFloat())

        paint.color = SCREEN_DARK
        paint.alpha = 45
        rect.set(216f, HEAD_B - 8, 296f, HEAD_B + 10)
        canvas.drawRoundRect(rect, 10f, 10f, paint)
        paint.alpha = 255

        paint.style = Paint.Style.FILL
        paint.color = HEAD_BLUE
        rect.set(HEAD_L, 60f, HEAD_R, HEAD_B)
        canvas.drawRoundRect(rect, HEAD_RAD, HEAD_RAD, paint)

        paint.color = HEAD_TOPBAR
        rect.set(HEAD_L + 36, 70f, HEAD_R - 36, 114f)
        canvas.drawRoundRect(rect, TOPBAR_R, TOPBAR_R, paint)

        paint.color = SIDE_TAB
        rect.set(HEAD_L + 8, 148f, HEAD_L + 30, 218f)
        canvas.drawRoundRect(rect, 12f, 12f, paint)
        rect.set(HEAD_R - 30, 148f, HEAD_R - 8, 218f)
        canvas.drawRoundRect(rect, 12f, 12f, paint)

        paint.color = BORDER_WHITE
        rect.set(BORDER_L, BORDER_T, BORDER_R, BORDER_B)
        canvas.drawRoundRect(rect, BORDER_RAD, BORDER_RAD, paint)

        val sx0 = BORDER_L + SCREEN_INSET
        val sx1 = BORDER_R - SCREEN_INSET
        val sy0 = BORDER_T + SCREEN_INSET
        val sy1 = BORDER_B - SCREEN_INSET
        paint.color = SCREEN_DARK
        rect.set(sx0, sy0, sx1, sy1)
        canvas.drawRoundRect(rect, BORDER_RAD - 22, BORDER_RAD - 22, paint)

        drawFace()
        canvas.restore()
    }

    private fun drawFace() {
        val ex0 = 256f - EYE_GAP / 2f
        val ex1 = 256f + EYE_GAP / 2f
        val eyeL = ex0 - EYE_W / 2
        val eyeR = ex1 - EYE_W / 2
        val eyeT = EYE_CY - EYE_H / 2

        paint.style = Paint.Style.FILL
        paint.color = FACE_CYAN

        when (emotion) {
            "sleepy" -> {
                for (ex in listOf(eyeL, eyeR)) {
                    rect.set(ex, EYE_CY - 5, ex + EYE_W, EYE_CY + 5)
                    canvas.drawRoundRect(rect, 5f, 5f, paint)
                }
                rect.set(MOUTH_CX - 9, MOUTH_CY + 20, MOUTH_CX + 9, MOUTH_CY + 36)
                canvas.drawRoundRect(rect, 9f, 9f, paint)
            }
            "surprised" -> {
                for (ex in listOf(eyeL, eyeR)) {
                    canvas.drawCircle(ex + EYE_W / 2, EYE_CY, 22f, paint)
                }
                val r = if (speaking && mouthTick / 5 % 2 == 0) 15f else 9f
                canvas.drawCircle(MOUTH_CX, MOUTH_CY + 26, r, paint)
            }
            "confused" -> {
                rect.set(eyeL, eyeT, eyeL + EYE_W, eyeT + EYE_H)
                canvas.drawRoundRect(rect, EYE_RAD, EYE_RAD, paint)
                rect.set(eyeR + 6, eyeT - 14, eyeR + 6 + EYE_W - 10, eyeT - 14 + EYE_H - 10)
                canvas.drawRoundRect(rect, EYE_RAD, EYE_RAD, paint)
                strokeMouth { squiggle() }
            }
            "thinking" -> {
                val dy = 13f
                rect.set(eyeL - 10, eyeT - dy, eyeL - 10 + EYE_W, eyeT - dy + EYE_H)
                canvas.drawRoundRect(rect, EYE_RAD, EYE_RAD, paint)
                rect.set(eyeR + 4, eyeT - dy - 4, eyeR + 4 + EYE_W - 8, eyeT - dy - 4 + EYE_H - 8)
                canvas.drawRoundRect(rect, EYE_RAD, EYE_RAD, paint)
                rect.set(MOUTH_CX - 10, MOUTH_CY + 26, MOUTH_CX + 12, MOUTH_CY + 32)
                canvas.drawRoundRect(rect, 3f, 3f, paint)
            }
            "annoyed" -> {
                for (ex in listOf(eyeL, eyeR)) {
                    rect.set(ex, eyeT + EYE_H / 2.4f, ex + EYE_W, eyeT + EYE_H)
                    canvas.drawRoundRect(rect, EYE_RAD, EYE_RAD, paint)
                    paint.color = SCREEN_DARK
                    rect.set(ex - 2, eyeT + EYE_H / 2.4f - 14, ex + EYE_W + 2, eyeT + EYE_H / 2.4f)
                    canvas.drawRoundRect(rect, 4f, 4f, paint)
                    paint.color = FACE_CYAN
                }
                rect.set(MOUTH_CX - 14, MOUTH_CY + 30, MOUTH_CX + 14, MOUTH_CY + 36)
                canvas.drawRoundRect(rect, 3f, 3f, paint)
            }
            else -> {
                val squint = emotion == "angry"
                val eyeH = if (squint) EYE_H * 0.55f else EYE_H
                val eyeCy = if (squint) EYE_CY + 8f else EYE_CY
                for (ex in listOf(eyeL, eyeR)) {
                    rect.set(ex, eyeCy - eyeH / 2, ex + EYE_W, eyeCy + eyeH / 2)
                    canvas.drawRoundRect(rect, EYE_RAD, if (squint) 12f else EYE_RAD, paint)
                }

                if (emotion == "angry" || emotion == "sad") {
                    for ((ex, isLeft) in listOf(eyeL to true, eyeR to false)) {
                        val cx = ex + EYE_W / 2
                        canvas.save()
                        val deg = when {
                            emotion == "angry" && isLeft -> -26f
                            emotion == "angry" -> 26f
                            isLeft -> 14f
                            else -> -14f
                        }
                        canvas.rotate(deg, cx, EYE_CY - eyeH / 2 - 14)
                        rect.set(cx - 25, EYE_CY - eyeH / 2 - 24, cx + 25, EYE_CY - eyeH / 2 - 12)
                        canvas.drawRoundRect(rect, 6f, 6f, paint)
                        canvas.restore()
                    }
                }
                if (emotion != "angry" && blinkTicks > 0) {
                    paint.color = SCREEN_DARK
                    for (ex in listOf(eyeL, eyeR)) {
                        rect.set(ex - 2, eyeCy - eyeH / 2 - 2, ex + EYE_W + 2, eyeCy + eyeH / 2 + 2)
                        canvas.drawRoundRect(rect, 6f, 6f, paint)
                    }
                    paint.color = FACE_CYAN
                    for (ex in listOf(eyeL, eyeR)) {
                        rect.set(ex, eyeCy - 3, ex + EYE_W, eyeCy + 3)
                        canvas.drawRoundRect(rect, 3f, 3f, paint)
                    }
                }

                drawMouth()
            }
        }
    }

    private fun strokeMouth(drawPath: () -> Unit) {
        paint.color = FACE_CYAN
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
       drawPath()
        paint.style = Paint.Style.FILL
    }

    private fun squiggle() {
        path.reset()
        path.moveTo(MOUTH_CX - 24, MOUTH_CY + 30)
        path.quadTo(MOUTH_CX - 12, MOUTH_CY + 20, MOUTH_CX, MOUTH_CY + 30)
        path.quadTo(MOUTH_CX + 12, MOUTH_CY + 40, MOUTH_CX + 24, MOUTH_CY + 30)
        canvas.drawPath(path, paint)
    }

    private fun drawMouth() {
        val talking = speaking && mouthTick / 4 % 2 == 0
        paint.color = FACE_CYAN
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        when (emotion) {
            "angry", "sad" -> {
                rect.set(MOUTH_CX - 28, MOUTH_CY + 18, MOUTH_CX + 28, MOUTH_CY + 74)
                canvas.drawArc(rect, 200f, 140f, false, paint)
            }
            else -> {
                if (talking) {
                    paint.style = Paint.Style.FILL
                    rect.set(MOUTH_CX - 13, MOUTH_CY + 16, MOUTH_CX + 13, MOUTH_CY + 34)
                    canvas.drawOval(rect, paint)
                    paint.style = Paint.Style.STROKE
                } else {
                    val r = if (emotion == "happy") 30f else 26f
                    rect.set(MOUTH_CX - r, MOUTH_CY + 8, MOUTH_CX + r, MOUTH_CY + 8 + 62f)
                    canvas.drawArc(rect, 20f, 140f, false, paint)
                }
            }
        }
        paint.style = Paint.Style.FILL
    }
}
