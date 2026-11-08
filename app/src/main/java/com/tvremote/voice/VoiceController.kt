package com.tvremote.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Turns speech into text with the phone's speech recognizer (usually Google's).
 * Must be used from the main thread.
 */
class VoiceController(context: Context) {

    sealed interface State {
        data object Idle : State
        data class Listening(val partial: String = "", val level: Float = 0f) : State
        data class Error(val message: String) : State
    }

    private val appContext = context.applicationContext
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Called with the recognizer's guesses, best first. */
    var onResults: ((List<String>) -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start() {
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(appContext).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
        }
        _state.value = State.Listening()
        r.startListening(intent)
    }

    /** Stop listening and process what was heard so far. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun release() {
        recognizer?.destroy()
        recognizer = null
        _state.value = State.Idle
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            val current = _state.value
            if (current is State.Listening) {
                _state.value = current.copy(level = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            val current = _state.value
            if (current is State.Listening && text.isNotBlank()) {
                _state.value = current.copy(partial = text)
            }
        }

        override fun onResults(results: Bundle?) {
            val list = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                .orEmpty()
                .filter { it.isNotBlank() }
            if (list.isEmpty()) {
                _state.value = State.Error("Didn't catch that. Try again.")
            } else {
                _state.value = State.Idle
                onResults?.invoke(list)
            }
        }

        override fun onError(error: Int) {
            _state.value = if (error == SpeechRecognizer.ERROR_CLIENT) {
                State.Idle // usually "stopped before anything was said"
            } else {
                State.Error(messageFor(error))
            }
        }
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Try again."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap the mic and speak."
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Network problem. Voice needs internet (or an offline speech pack) on this phone."
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again."
        else -> "Voice error ($error). Try again."
    }

    private companion object {
        /**
         * Commands are English and the TV can only be typed to in English letters, so the
         * recognizer runs in Indian English. Change to "en-US", "en-GB", etc. if you prefer.
         */
        const val LANGUAGE = "en-IN"
    }
}
