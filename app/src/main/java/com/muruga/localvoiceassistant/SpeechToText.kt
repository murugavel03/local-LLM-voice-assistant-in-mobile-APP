package com.muruga.localvoiceassistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Push-to-talk offline speech recognition.
 *
 * NOTE: EXTRA_PREFER_OFFLINE only works if the user has downloaded the
 * on-device speech recognition pack:
 *   Settings > System > Languages & input > On-device speech recognition
 */
class SpeechToText(context: Context) {

    private val recognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

    fun listen(onResult: (String) -> Unit, onError: (String) -> Unit) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (text != null) onResult(text) else onError("No speech recognized")
            }

            override fun onError(error: Int) {
                onError("STT error code: $error")
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        recognizer.startListening(intent)
    }

    fun destroy() {
        recognizer.destroy()
    }
}
