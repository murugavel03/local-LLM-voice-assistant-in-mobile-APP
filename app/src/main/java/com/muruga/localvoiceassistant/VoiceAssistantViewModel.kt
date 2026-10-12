package com.muruga.localvoiceassistant

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.NetworkInterface

enum class AssistantState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }
enum class Sender { USER, ASSISTANT }
enum class AppMode { SERVER, CLIENT }

data class ChatMessage(val sender: Sender, val text: String)

class VoiceAssistantViewModel(application: Application) : AndroidViewModel(application) {

    // Lazily created so the (potentially slow) model load doesn't block app launch,
    // and so Client mode never triggers loading the model at all.
    private val llm by lazy { LocalLlmEngine(application) }
    private val stt by lazy { SpeechToText(application) }
    private val tts by lazy { VoiceOutput(application) }
    private val remoteClient by lazy { RemoteLlmClient() }
    private val documentProcessor by lazy { DocumentProcessor(application) }
    private var server: LocalLlmServer? = null

    val state = mutableStateOf(AssistantState.IDLE)
    val messages = mutableStateListOf<ChatMessage>()
    val errorMessage = mutableStateOf("")
    val textInput = mutableStateOf("")

    // Server / Client mode
    val appMode = mutableStateOf(AppMode.SERVER)
    val serverRunning = mutableStateOf(false)
    val serverAddress = mutableStateOf("")
    val remoteServerIp = mutableStateOf("")

    // Document Q&A
    val attachedDocName = mutableStateOf<String?>(null)
    val documentContext = mutableStateOf<String?>(null)
    val isProcessingDocument = mutableStateOf(false)

    // ---- Voice input ----
    fun onMicTapped() {
        state.value = AssistantState.LISTENING
        errorMessage.value = ""

        stt.listen(
            onResult = { userText ->
                messages.add(ChatMessage(Sender.USER, userText))
                state.value = AssistantState.THINKING
                askLlm(buildPrompt(userText), speakReply = true)
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
        askLlm(buildPrompt(prompt), speakReply = false)
    }

    private fun buildPrompt(userText: String): String {
        val doc = documentContext.value
        return if (!doc.isNullOrBlank()) {
            "Document content:\n$doc\n\nQuestion: $userText"
        } else {
            userText
        }
    }

    private fun askLlm(prompt: String, speakReply: Boolean) {
        val assistantIndex = messages.size
        messages.add(ChatMessage(Sender.ASSISTANT, ""))

        viewModelScope.launch(Dispatchers.Default) {
            if (appMode.value == AppMode.CLIENT) {
                try {
                    val ip = remoteServerIp.value.trim()
                    if (ip.isBlank()) {
                        messages[assistantIndex] = messages[assistantIndex].copy(
                            text = "No server IP set. Enter the host phone's IP first."
                        )
                        state.value = AssistantState.ERROR
                        return@launch
                    }
                    val response = remoteClient.generateResponse(ip, prompt)
                    messages[assistantIndex] = messages[assistantIndex].copy(text = response)
                    if (speakReply) {
                        state.value = AssistantState.SPEAKING
                        tts.speak(response)
                    }
                    state.value = AssistantState.IDLE
                } catch (e: Exception) {
                    messages[assistantIndex] = messages[assistantIndex].copy(
                        text = "Couldn't reach server: ${e.message}"
                    )
                    state.value = AssistantState.ERROR
                }
            } else {
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
    }

    // ---- Local LAN server (Server mode only) ----
    fun toggleServer() {
        if (serverRunning.value) {
            server?.stop()
            server = null
            serverRunning.value = false
            serverAddress.value = ""
        } else {
            val s = LocalLlmServer(llm, port = 8080)
            s.start(5000, false)
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

    // ---- Document Q&A ----
    fun onDocumentAttached(uri: Uri, mimeType: String?, displayName: String) {
        isProcessingDocument.value = true
        attachedDocName.value = displayName
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val text = documentProcessor.extractText(uri, mimeType)
                documentContext.value = text
                messages.add(
                    ChatMessage(Sender.ASSISTANT, "I've read \"$displayName\" — ask me anything about it.")
                )
            } catch (e: Exception) {
                errorMessage.value = "Couldn't read that file: ${e.message}"
                attachedDocName.value = null
            } finally {
                isProcessingDocument.value = false
            }
        }
    }

    fun clearAttachedDocument() {
        attachedDocName.value = null
        documentContext.value = null
    }

    override fun onCleared() {
        super.onCleared()
        server?.stop()
        llm.close()
        stt.destroy()
        tts.shutdown()
    }
}
