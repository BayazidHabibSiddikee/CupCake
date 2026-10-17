package com.cupcake.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays the companion's voice on the phone itself, streamed straight out of the
 * on-device TTS. Built deliberately WITHOUT any audio-focus request, so background
 * music (Spotify, YT Music...) keeps playing and the voice simply mixes on top of it.
 */
@Singleton
class LocalSpeaker @Inject constructor(
    private val context: Context,
    private val ttsManager: TtsManager
) {

    private val current = AtomicReference<Player?>(null)
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private class Player(
        val normalize: (String) -> String = { it },
        @Volatile var track: AudioTrack? = null,
        @Volatile var header: ByteArrayOutputStream? = null,
        @Volatile var sampleRate: Int = 22050,
        @Volatile var channels: Int = 1,
        @Volatile var bitsPerSample: Int = 16,
        @Volatile var dataStart: Int = -1
    )

    /** True when a voice line is currently playing or being synthesized. */
    @Volatile
    var isSpeaking: Boolean = false
        private set

    fun speak(rawText: String, normalize: (String) -> String = { it }) {
        if (!VoiceSettings.isEnabled(context)) return
        stop()
        val text = normalize(rawText)
        if (text.isBlank()) return

        val player = Player(normalize)
        current.set(player)
        isSpeaking = true

        scope.launch {
            try {
                val stream = ttsManager.speakStreaming(text) { chunk ->
                    feed(player, chunk)
                }
                for (_unit in stream) { /* wait for synthesis to finish */ }
            } catch (e: Exception) {
                Log.w(TAG, "local voice failed: ${e.message}")
            } finally {
                if (current.get() === player) {
                    current.compareAndSet(player, null)
                    isSpeaking = false
                }
                player.track?.let { t ->
                    runCatching { t.stop() }
                    runCatching { t.release() }
                }
            }
        }
    }

    fun stop() {
        val old = current.getAndSet(null)
        if (old != null) {
            isSpeaking = false
            ttsManager.stop()
            old.track?.let { t ->
                runCatching { t.pause() }
                runCatching { t.stop() }
                runCatching { t.release() }
            }
        }
    }

    private fun feed(player: Player, chunk: ByteArray) {
        if (current.get() !== player) return
        if (player.dataStart < 0) {
            val buf = player.header ?: ByteArrayOutputStream(chunk.size + 1024).also { player.header = it }
            buf.write(chunk)
            val bytes = buf.toByteArray()
            if (!parseWavHeader(player, bytes)) return
            val start = player.dataStart
            if (start > bytes.size) return // wait for more data
            startTrack(player)
            writePcm(player, bytes, start, bytes.size - start)
            buf.reset()
        } else {
            writePcm(player, chunk, 0, chunk.size)
        }
    }

    /** Finds the 'data' chunk and fills in fmt parameters. Returns true when parsed. */
    private fun parseWavHeader(player: Player, bytes: ByteArray): Boolean {
        if (bytes.size < 12) return false
        var i = 12
        while (i + 8 <= bytes.size) {
            val id = String(bytes, i, 4, Charsets.US_ASCII)
            val size = readInt(bytes, i + 4)
            when (id) {
                "fmt " -> {
                    if (i + 8 + 16 > bytes.size) return false
                    player.channels = readShort(bytes, i + 8 + 2)
                    player.sampleRate = readInt(bytes, i + 8 + 4)
                    player.bitsPerSample = readShort(bytes, i + 8 + 14)
                }
                "data" -> {
                    player.dataStart = i + 8
                    return true
                }
            }
            if (size <= 0) return false
            i += 8 + size + (size and 1)
        }
        return false
    }

    private fun startTrack(player: Player) {
        val chMask = if (player.channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val encoding = if (player.bitsPerSample == 8) AudioFormat.ENCODING_PCM_8BIT else AudioFormat.ENCODING_PCM_16BIT
        val minBuf = AudioTrack.getMinBufferSize(
            player.sampleRate, chMask, encoding
        ).coerceAtLeast(4096)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(player.sampleRate)
                    .setChannelMask(chMask)
                    .setEncoding(encoding)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBuf)
            .build()
        player.track = track
        track.play()
    }

    private fun writePcm(player: Player, bytes: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        val track = player.track ?: return
        var written = 0
        while (written < len) {
            if (current.get() !== player) return
            written += track.write(bytes.copyOfRange(off + written, off + len), 0, len - written)
            if (written <= 0) break
        }
    }

    private fun readInt(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun readShort(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private companion object {
        const val TAG = "LocalSpeaker"
    }
}
