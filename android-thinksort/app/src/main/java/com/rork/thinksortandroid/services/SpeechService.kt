package com.rork.thinksortandroid.services

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface SpeechState {
    data object Idle : SpeechState
    data object Recording : SpeechState
    data class Denied(val message: String) : SpeechState
    data class Unavailable(val message: String) : SpeechState
}

/**
 * Continuous dictation on top of Android's SpeechRecognizer. The system recognizer
 * stops after a pause, so this restarts it and stitches results together until
 * the user taps stop. Must be used from the main thread.
 */
class SpeechService(private val context: Context) {
    private val _state = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val state: StateFlow<SpeechState> = _state.asStateFlow()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var committed: String = ""

    val isRecording: Boolean get() = _state.value == SpeechState.Recording

    fun start(seed: String) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = SpeechState.Unavailable("Voice input isn't available on this device. You can type instead — everything still works.")
            return
        }
        committed = seed.trim()
        _transcript.value = committed
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(listener) }
        _state.value = SpeechState.Recording
        listen()
    }

    fun markDenied() {
        _state.value = SpeechState.Denied("Microphone access is off. Turn it on in Android Settings to talk, or just type.")
    }

    fun stop() {
        val wasRecording = isRecording
        if (_state.value == SpeechState.Recording) _state.value = SpeechState.Idle
        _audioLevel.value = 0f
        if (wasRecording) recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }

    fun reset() {
        stop()
        committed = ""
        _transcript.value = ""
        _state.value = SpeechState.Idle
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }
        recognizer?.startListening(intent)
    }

    private fun join(base: String, addition: String): String =
        listOf(base, addition.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            _audioLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            _transcript.value = join(committed, partial)
        }

        override fun onResults(results: Bundle?) {
            val best = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            committed = join(committed, best)
            _transcript.value = committed
            if (isRecording) listen()
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (isRecording) listen()
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    stop()
                    markDenied()
                }
                else -> {
                    _transcript.value = committed
                    stop()
                }
            }
        }
    }
}
