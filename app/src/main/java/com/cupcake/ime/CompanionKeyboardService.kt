package com.cupcake.ime

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.Coil
import coil.request.ImageRequest

import com.cupcake.ai.EmotionAssets
import com.cupcake.service.ShimejiPetService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * CupCake companion keyboard.
 *
 * A lightweight typing surface with a companion strip on top. While the user
 * types in ANY app (WhatsApp, Messenger, Discord...), the companion watches
 * the draft and reacts at the right beat: speech bubble on the strip +
 * floating overlay pet + ESP32 robot face/motor move (+ a spoken quip on
 * the instant triggers).
 *
 * Privacy: password fields get ZERO commentary (buffer flushed, sleepy face).
 * 100% on-device, zero cloud logging.
 */
class CompanionKeyboardService : InputMethodService() {

    /** Hilt entry point so the IME (non-@AndroidEntryPoint) can reach singletons. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CompanionKbEntryPoint {
        fun localSpeaker(): com.cupcake.tts.LocalSpeaker
    }

    private var isPasswordField = false
    private var draft = ""
    private var lastDraftLen = 0
    private var justTyped: String = ""
    private var lastQuip = ""

    private val handler = Handler(Looper.getMainLooper())
    private val debounceRun = Runnable { react(spoken = false) }

    private var faceView: ImageView? = null
    private var bubbleView: TextView? = null

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }
    private fun currentMode(): PersonaMode =
        runCatching {
            PersonaMode.valueOf(prefs.getString(KEY_MODE, PersonaMode.AUTO.name) ?: PersonaMode.AUTO.name)
        }.getOrDefault(PersonaMode.AUTO)

    override fun onCreateInputView(): View {
        val d = resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()

        // Companion strip above the keys
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(px(10), px(6), px(10), px(6))
        }
        faceView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(px(34), px(34))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        bubbleView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).also {
                it.marginStart = px(8)
            }
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(0xFF222222.toInt())
            text = "${currentMode().label} · watching what you type ~"
        }
        // Persona chip — tap to cycle AUTO -> SLEEPY -> WINGMAN -> SNARKY
        val personaChip = TextView(this).apply {
            textSize = 12f
            setTextColor(0xFF222222.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFCE4EC.toInt())
                cornerRadius = px(12).toFloat()
            }
            setPadding(px(10), px(4), px(10), px(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                it.marginStart = px(8)
            }
            isHapticFeedbackEnabled = true
        }
        fun refreshChip() {
            personaChip.text = currentMode().label
            setFaceStem(currentMode().stemForChip())
        }
        personaChip.setOnClickListener {
            personaChip.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            val next = PersonaMode.entries[
                (PersonaMode.entries.indexOf(currentMode()) + 1) % PersonaMode.entries.size]
            prefs.edit().putString(KEY_MODE, next.name).apply()
            refreshChip()
            bubbleView?.text = "${next.label} · watching what you type ~"
        }
        strip.addView(personaChip)
        refreshChip()
        strip.addView(faceView)
        strip.addView(bubbleView)

        val boardBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFFF0F2F5.toInt())
        }
        val bg = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFFFFFFFF.toInt())
            cornerRadius = px(6).toFloat()
        }

        fun key(label: String, weight: Float, onFire: () -> Unit): View =
            TextView(this).apply {
                text = label
                gravity = android.view.Gravity.CENTER
                textSize = 15f
                setTextColor(0xFF222222.toInt())
                background = bg
                layoutParams = LinearLayout.LayoutParams(0, px(38), weight).also {
                    it.setMargins(px(1), px(1), px(1), px(1))
                }
                isHapticFeedbackEnabled = true
                setOnClickListener {
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    onFire()
                }
            }

        fun commitChar(c: Char) {
            currentInputConnection?.commitText(c.toString(), 1)
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = boardBg
            addView(strip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEach { rowStr ->
                val row = LinearLayout(this@CompanionKeyboardService).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER
                }
                if (rowStr.length == 9) {
                    val padW = 0.5f
                    row.addView(View(this@CompanionKeyboardService),
                        LinearLayout.LayoutParams(0, px(38), padW - px(1)))
                }
                rowStr.forEach { ch ->
                    row.addView(key("$ch", 1f) { commitChar(ch) })
                }
                if (rowStr.length == 9) {
                    row.addView(View(this@CompanionKeyboardService),
                        LinearLayout.LayoutParams(0, px(38), 0.5f - px(1)))
                }
                addView(row, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            // Bottom row: , space . backspace enter
            val bottom = LinearLayout(this@CompanionKeyboardService).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
            }
            bottom.addView(key(",", 1f) { commitChar(',') })
            bottom.addView(key("space", 5f) { currentInputConnection?.commitText(" ", 1) })
            bottom.addView(key(".", 1f) { commitChar('.') })
            bottom.addView(key("⌫", 1f) {
                handler.post { currentInputConnection?.deleteSurroundingText(1, 0) }
            })
            bottom.addView(key("⏎", 1f) {
                currentInputConnection?.sendKeyEvent(
                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                if (!isPasswordField && draft.isNotBlank()) react(spoken = true)
            })
            addView(bottom, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setFaceStem("happy")
        return root
    }

    // ----------------- selection / draft capture -----------------

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        isPasswordField = attribute != null &&
            (attribute.inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD
        if (isPasswordField) {
            draft = ""
            bubbleView?.text = "🔒 password field — i'm not looking"
            setFaceStem("sleepy")
        } else {
            bubbleView?.text = "${currentMode().label} · watching what you type ~"
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        handler.removeCallbacks(debounceRun)
        draft = ""
        lastDraftLen = 0
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (isPasswordField) return
        val ic: InputConnection? = currentInputConnection ?: return
        val before = ic?.getTextBeforeCursor(120, 0)?.toString() ?: ""

        // What changed since the last update — triggers treat it raw.
        justTyped = if (draft.isNotEmpty() && before.startsWith(draft)) {
            before.removePrefix(draft)
        } else {
            before.takeLast(1)
        }
        draft = before
        lastDraftLen = draft.length

        // Combined persona triggers: punctuation fires instantly; everything
        // else waits for the 1.2 s thinking pause.
        if (PersonaEngine.shouldFireInstantly(draft, lastDraftLen, justTyped)) {
            react(spoken = true)
        } else {
            handler.removeCallbacks(debounceRun)
            if (draft.isNotBlank()) handler.postDelayed(debounceRun, 1200)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ENTER && !isPasswordField && draft.isNotBlank()) {
            react(spoken = true)
        }
        return super.onKeyDown(keyCode, event)
    }

    // ----------------- persona reaction -----------------

    private fun react(spoken: Boolean) {
        val text = draft
        if (text.isBlank()) return
        var reaction = PersonaEngine.canned(currentMode())
        if (reaction.quip == lastQuip) {
            // Don't repeat the same line on every pause; try another personality voice.
            val varied = PersonaEngine.canned(PersonaMode.AUTO)
            reaction = varied.takeIf { it.quip != lastQuip } ?: reaction
        }
        lastQuip = reaction.quip

        bubbleView?.text = reaction.quip

        val tagText = "[${reaction.tag}]"
        val stem = EmotionAssets.stemForText(tagText)
        val faceName = EmotionAssets.espFaceForStem(stem)
        val faceAsset = EmotionAssets.assetForStem(stem)

        // 1) In-app floating pet speech bubble
        ShimejiPetService.showSpeech(this, reaction.quip, faceAsset)
        // 2) Physical ESP32 robot: OLED face + a small motor beat
        runCatching {
            com.cupcake.network.EspWebSocketServer.sendFaceExpression(
                expression = faceName,
                duration = 3000,
                emotion = reaction.tag,
                imagePath = EmotionAssets.httpPathForStem(stem)
            )
            val side = if (System.currentTimeMillis() % 2 == 0L) "left" else "right"
            com.cupcake.network.EspWebSocketServer.sendMotorAction("look_$side")
        }.onFailure { Log.w(TAG, "esp push failed: ${it.message}") }
        // 3) Voice-over — only on the instant triggers, keeps it snappy
        if (spoken) runCatching {
            val line = EmotionAssets.speechText(reaction.quip)
            if (line.isNotBlank()) {
                localSpeaker().speak(line)
            }
        }
        setFaceStem(stem)
    }

    private fun localSpeaker(): com.cupcake.tts.LocalSpeaker =
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            applicationContext,
            CompanionKbEntryPoint::class.java
        ).localSpeaker()

    // ----------------- face visual on the strip -----------------

    private fun setFaceStem(stem: String) {
        val fv = faceView ?: return
        Coil.imageLoader(this).enqueue(
            ImageRequest.Builder(this)
                .data(EmotionAssets.assetForStem(stem))
                .target(fv)
                .build()
        )
    }

    private companion object {
        const val TAG = "CompanionKB"
        const val PREFS_NAME = "companion_kb"
        const val KEY_MODE = "persona"
    }
}

private fun PersonaMode.stemForChip(): String = when (this) {
    PersonaMode.SLEEPY -> "sleepy"
    PersonaMode.WINGMAN -> "thinking"
    PersonaMode.SNARKY -> "annoyed"
    PersonaMode.AUTO -> "happy"
}
