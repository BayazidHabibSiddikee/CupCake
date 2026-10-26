package com.cupcake.stt

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.google.common.truth.Truth.assertThat
import io.mockk.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SpeechToTextManagerTest {

    private lateinit var recognizer: SpeechRecognizer
    private lateinit var listener: RecognitionListener
    private lateinit var manager: SpeechToTextManager

    @Before
    fun setUp() {
        recognizer = mockk(relaxed = true)
        val application = RuntimeEnvironment.application
        shadowOf(application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val listenerSlot = slot<RecognitionListener>()
        manager = SpeechToTextManager(application, recognizer)
        verify(exactly = 1) { recognizer.setRecognitionListener(capture(listenerSlot)) }
        listener = listenerSlot.captured
    }

    private fun bundle(vararg texts: String): Bundle =
        Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(*texts))
        }

    @Test
    fun `startListening enables partial results and prefers Bangla`() {
        val intentSlot = slot<Intent>()
        manager.startListening()
        verify(exactly = 1) { recognizer.startListening(capture(intentSlot)) }
        val intent = intentSlot.captured
        assertThat(intent.getBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)).isTrue()
        assertThat(intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)).isEqualTo("bn-BD")
        assertThat(intent.getIntExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 0)).isAtLeast(1)
        assertThat(intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL))
            .isEqualTo(RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }

    @Test
    fun `onPartialResults captures live text and keeps listening`() {
        manager.startListening()
        listener.onReadyForSpeech(null) // real engine signals ready before partials arrive
        listener.onPartialResults(bundle("হ্যালো", "hello"))
        assertThat(manager.transcript.value).isEqualTo("হ্যালো")
        assertThat(manager.isListening.value).isTrue()
    }

    @Test
    fun `onResults uses best match and stops listening`() {
        listener.onPartialResults(bundle("partial"))
        val results = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("hello world", "hallo world"))
        }
        listener.onResults(results)
        assertThat(manager.transcript.value).isEqualTo("hello world")
        assertThat(manager.isListening.value).isFalse()
    }

    @Test
    fun `onResults with no matches preserves partial transcript instead of wiping`() {
        listener.onPartialResults(bundle("some speech"))
        assertThat(manager.transcript.value).isEqualTo("some speech")
        listener.onResults(Bundle())
        assertThat(manager.transcript.value).isEqualTo("some speech")
        assertThat(manager.isListening.value).isFalse()
    }

    @Test
    fun `onResults with no matches and no prior transcript leaves transcript unset`() {
        listener.onResults(Bundle())
        assertThat(manager.transcript.value).isNull()
        assertThat(manager.isListening.value).isFalse()
    }

    @Test
    fun `onError preserves transcript instead of wiping it`() {
        listener.onPartialResults(bundle("captured before error"))
        assertThat(manager.transcript.value).isEqualTo("captured before error")
        listener.onError(SpeechRecognizer.ERROR_NETWORK_TIMEOUT)
        assertThat(manager.transcript.value).isEqualTo("captured before error")
        assertThat(manager.isListening.value).isFalse()
    }

    @Test
    fun `onError with no transcript does not crash`() {
        listener.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        assertThat(manager.transcript.value).isNull()
        assertThat(manager.isListening.value).isFalse()
    }

    @Test
    fun `startListening uses the first language preference (Bangla bn-BD)`() {
        val intentSlot = slot<Intent>()
        manager.startListening()
        verify(exactly = 1) { recognizer.startListening(capture(intentSlot)) }
        assertThat(intentSlot.captured.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)).isEqualTo("bn-BD")
    }

    @Test
    fun `after one cycle startListening uses Bangla bn`() {
        val intentSlot = slot<Intent>()
        manager.cycleLanguage()
        manager.startListening()
        verify(exactly = 1) { recognizer.startListening(capture(intentSlot)) }
        assertThat(intentSlot.captured.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)).isEqualTo("bn")
    }

    @Test
    fun `after two cycles startListening uses English en`() {
        val intentSlot = slot<Intent>()
        manager.cycleLanguage()
        manager.cycleLanguage()
        manager.startListening()
        verify(exactly = 1) { recognizer.startListening(capture(intentSlot)) }
        assertThat(intentSlot.captured.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)).isEqualTo("en")
    }

    @Test
    fun `stopListening stops recognizer and clears listening flag`() {
        manager.stopListening()
        verify(exactly = 1) { recognizer.stopListening() }
        assertThat(manager.isListening.value).isFalse()
    }

    @Test
    fun `destroy releases recognizer`() {
        manager.destroy()
        verify(exactly = 1) { recognizer.destroy() }
    }

    @Test
    fun `clearTranscript resets transcript and language state`() {
        listener.onPartialResults(bundle("something"))
        assertThat(manager.transcript.value).isEqualTo("something")
        manager.clearTranscript()
        assertThat(manager.transcript.value).isNull()
    }
}
