package com.muruga.localvoiceassistant

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.NetworkInterface

enum class AssistantState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }
enum class Sender { USER, ASSISTANT }

data class ChatMessage(val sender: Sender, val text: String)

class VoiceAssistantViewModel(application: Application) : AndroidViewModel(application) {

    // Lazily created so the (potentially slow) model load doesn't block app launch.
    // Shared by chat, voice, and the local server -- one loaded model, three entry points.
    private val llm by lazy { LocalLlmEngine(application) }
    private val stt by lazy { SpeechToText(application) }
    private val tts by lazy { VoiceOutput(application) }
    private var server: LocalLlmServer? = null

    val state = mutableStateOf(AssistantState.IDLE)
    val messages = mutableStateListOf<ChatMessage>()
    val errorMessage = mutableStateOf("")
    val textInput = mutableStateOf("")
    val serverRunning = mutableStateOf(false)
    val serverAddress = mutableStateOf("")

    // ---- Voice input ----
    fun onMicTapped() {
        state.value = AssistantState.LISTENING
        errorMessage.value = ""

        stt.listen(
            onResult = { userText ->
                messages.add(ChatMessage(Sender.USER, userText))
                state.value = AssistantState.THINKING
                askLlm(userText, speakReply = true)
            },
            onError = { err ->
                errorMessage.value = err
                state.value = AssistantState.ERROR
            }
        )
    }

    // ---- Text chat input ----
    fun onSendText() {
        val prompt = textInput.value.trim()
        if (prompt.isBlank()) return
        textInput.value = ""
        messages.add(ChatMessage(Sender.USER, prompt))
        state.value = AssistantState.THINKING
        askLlm(prompt, speakReply = false)
    }

    private fun askLlm(prompt: String, speakReply: Boolean) {
        val assistantIndex = messages.size
        messages.add(ChatMessage(Sender.ASSISTANT, ""))

        viewModelScope.launch(Dispatchers.Default) {
            llm.generateResponseAsync(
                prompt = prompt,
                onPartial = { partial ->
                    val updated = messages[assistantIndex].copy(
                        text = messages[assistantIndex].text + partial
                    )
                    messages[assistantIndex] = updated
                },
                onDone = { fullText ->
                    if (speakReply) {
                        state.value = AssistantState.SPEAKING
                        tts.speak(fullText)
                    }
                    state.value = AssistantState.IDLE
                }
            )
        }
    }

    // ---- Local LAN server ----
    fun toggleServer() {
        if (serverRunning.value) {
            server?.stop()
            server = null
            serverRunning.value = false
            serverAddress.value = ""
        } else {
            val s = LocalLlmServer(llm, port = 8080)
            s.start(NanoHTTPDStartTimeoutMs, false)
            server = s
            serverRunning.value = true
            serverAddress.value = "http://${getLocalIpAddress()}:8080/generate"
        }
    }

    private fun getLocalIpAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }
                ?.hostAddress ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    override fun onCleared() {
        super.onCleared()
        server?.stop()
        llm.close()
        stt.destroy()
        tts.shutdown()
    }

    companion object {
        private const val NanoHTTPDStartTimeoutMs = 5000
    }
}
