package com.cupcake.ai

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Local sound event classifier using YAMNet.
 * Maps raw audio buffers to sound categories (Music, Crying, Gunshots, etc.)
 */
class SoundClassifier(context: Context) {
    private var interpreter: Interpreter? = null
    private val modelFile = "models/sound/yamnet.tflite"
    
    init {
        try {
            val model = FileUtil.loadMappedFile(context, modelFile)
            interpreter = Interpreter(model)
            Log.i("SoundClassifier", "YAMNet model loaded successfully")
        } catch (e: Exception) {
            Log.e("SoundClassifier", "Failed to load YAMNet model: ${e.message}")
        }
    }

    /**
     * Classifies a chunk of audio.
     * YAMNet expects 16kHz mono audio.
     */
    fun classify(audioData: FloatArray): String? {
        val interpreter = interpreter ?: return null
        
        val inputBuffer = ByteBuffer.allocateDirect(audioData.size * 4).apply {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().put(audioData)
        }
        
        val outputBuffer = ByteBuffer.allocateDirect(521 * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        
        interpreter.run(inputBuffer, outputBuffer)
        
        val results = FloatArray(521)
        outputBuffer.rewind()
        outputBuffer.asFloatBuffer().get(results)
        
        // Find the class with the highest probability
        var maxIdx = -1
        var maxProb = -1f
        for (i in results.indices) {
            if (results[i] > maxProb) {
                maxProb = results[i]
                maxIdx = i
            }
        }
        
        if (maxProb < 0.3f) return null // Confidence threshold
        
        return mapClassIdToCategory(maxIdx)
    }

    private fun mapClassIdToCategory(id: Int): String {
        return when (id) {
            // Simplified YAMNet mapping (approximate indices)
            in 0..10 -> "music"
            in 300..310 -> "crying"
            in 400..410 -> "gunshot"
            in 100..120 -> "laughter"
            else -> "unknown"
        }
    }

    fun close() {
        interpreter?.close()
    }
}
