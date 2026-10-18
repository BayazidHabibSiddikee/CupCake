package com.cupcake.ai

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A single recognised sound event. */
data class SoundEvent(
    val category: String,
    val label: String,
    val confidence: Float,
)

/**
 * Local sound-event classifier backed by YAMNet (MobileNet, AudioSet 521 classes).
 *
 * Input  : waveform_binary, float32 [15600]  (0.975 s @ 16 kHz mono, [-1, 1])
 * Output : float32 [1, 521] class scores
 *
 * Class names come from the bundled yamnet_class_map.csv, and are folded into a
 * small set of companion-friendly categories (music / crying / gunshot / …).
 */
class SoundClassifier(context: Context) {

    private var interpreter: Interpreter? = null
    private var labels: Array<String> = emptyArray()

    companion object {
        private const val TAG = "SoundClassifier"
        private const val MODEL = "models/sound/yamnet.tflite"
        private const val CLASS_MAP = "models/sound/yamnet_class_map.csv"
        const val SAMPLE_COUNT = 15_600
        const val SAMPLE_RATE = 16_000
        private const val MIN_CONFIDENCE = 0.28f
    }

    init {
        try {
            labels = loadClassMap(context)
            val model = FileUtil.loadMappedFile(context, MODEL)
            interpreter = Interpreter(model, Interpreter.Options().setNumThreads(2))
            Log.i(TAG, "YAMNet ready (${labels.size} classes)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load YAMNet: ${e.message}")
        }
    }

    val isReady: Boolean get() = interpreter != null

    /** Classifies one 15600-sample mono frame; returns the best event or null. */
    fun classify(audio: FloatArray): SoundEvent? {
        val interpreter = interpreter ?: return null
        if (audio.size < SAMPLE_COUNT) return null

        val input = ByteBuffer.allocateDirect(SAMPLE_COUNT * 4)
            .order(ByteOrder.nativeOrder())
            .apply { asFloatBuffer().put(audio, 0, SAMPLE_COUNT) }

        val output = ByteBuffer.allocateDirect(521 * 4).order(ByteOrder.nativeOrder())

        interpreter.run(input, output)

        val scores = FloatArray(521)
        output.rewind()
        output.asFloatBuffer().get(scores)

        var bestIdx = -1
        var best = 0f
        for (i in scores.indices) {
            if (scores[i] > best) {
                best = scores[i]
                bestIdx = i
            }
        }
        if (bestIdx < 0 || best < MIN_CONFIDENCE) return null

        val label = labels.getOrElse(bestIdx) { "class_$bestIdx" }
        return SoundEvent(categoryFor(label), label, best)
    }

    fun close() {
        runCatching { interpreter?.close() }
        interpreter = null
    }

    private fun loadClassMap(context: Context): Array<String> {
        val csv = context.assets.open(CLASS_MAP).bufferedReader().use { it.readLines() }
        // Header: index,mid,display_name — display_name may be quoted and contain commas.
        val map = HashMap<Int, String>()
        csv.drop(1).forEach { line ->
            if (line.isBlank()) return@forEach
            val firstComma = line.indexOf(',')
            if (firstComma <= 0) return@forEach
            val idx = line.substring(0, firstComma).toIntOrNull() ?: return@forEach
            val secondComma = line.indexOf(',', firstComma + 1)
            if (secondComma < 0) return@forEach
            val name = line.substring(secondComma + 1).trim().trim('"')
            if (name.isNotEmpty()) map[idx] = name
        }
        val size = (map.keys.maxOrNull() ?: 520) + 1
        return Array(size) { map[it] ?: "class_$it" }
    }

    /** Folds a raw AudioSet label into one companion-friendly category. */
    private fun categoryFor(label: String): String {
        val l = label.lowercase()
        return when {
            l.contains("gunfire") || l.contains("gunshot") || l.contains("machine gun") ||
                l.contains("explosion") || l.contains("fireworks") -> "gunshot"
            l.contains("crying") || l.contains("sobbing") || l.contains("whimper") ||
                l.contains("wail") -> "crying"
            l.contains("laughter") || l.contains("giggle") || l.contains("chuckle") -> "laughter"
            l.contains("scream") || l.contains("screaming") -> "scream"
            l.contains("applause") || l.contains("clapping") -> "applause"
            l.contains("music") || l.contains("singing") || l.contains("drum") ||
                l.contains("guitar") || l.contains("piano") || l.contains("instrument") ||
                l.contains("melody") || l.contains("beat") -> "music"
            l.contains("meow") || l.contains("dog") || l.contains("bark") ||
                l.contains("animal") -> "animal"
            l.contains("thunder") || l.contains("rain") || l.contains("water") -> "nature"
            l.contains("siren") || l.contains("alarm") || l.contains("beep") -> "alert"
            l.contains("speech") || l.contains("narration") || l.contains("conversation") -> "talk"
            else -> "other"
        }
    }
}
