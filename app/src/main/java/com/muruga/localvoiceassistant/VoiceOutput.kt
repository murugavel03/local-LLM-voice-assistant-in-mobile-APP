package com.muruga.localvoiceassistant

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceOutput(context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech = TextToSpeech(context, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
            ready = true
        }
    }

    fun speak(text: String) {
        if (ready && text.isNotBlank()) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utterance_${System.currentTimeMillis()}")
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
