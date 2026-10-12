package com.muruga.localvoiceassistant

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.io.FileOutputStream

/**
 * Wraps MediaPipe's on-device LLM Inference API.
 *
 * Model is bundled inside the APK at app/src/main/assets/<MODEL_ASSET_NAME>.
 * On first run, it's copied from the compressed asset into internal storage
 * (context.filesDir), since MediaPipe's LlmInference needs a real file path
 * on disk -- it can't read directly out of the APK's assets.
 *
 * IMPORTANT: place your model file at app/src/main/assets/gemma3-1B-it-int4.task
 * (filename must match MODEL_ASSET_NAME exactly, including case).
 */
class LocalLlmEngine(private val context: Context) {

    companion object {
        const val MODEL_ASSET_NAME = "gemma3-1B-it-int4.task"
    }

    private fun ensureModelOnDisk(): String {
        val outFile = File(context.filesDir, MODEL_ASSET_NAME)
        if (!outFile.exists()) {
            context.assets.open(MODEL_ASSET_NAME).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output, bufferSize = 8 * 1024 * 1024)
                }
            }
        }
        return outFile.absolutePath
    }

    private val llmInference: LlmInference by lazy {
        val modelPath = ensureModelOnDisk()
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(1024)
            .setMaxTopK(40)
            .build()
        LlmInference.createFromOptions(context, options)
    }

    fun generateResponse(prompt: String): String = llmInference.generateResponse(prompt)

    fun generateResponseAsync(
        prompt: String,
        onPartial: (String) -> Unit,
        onDone: (String) -> Unit
    ) {
        val fullResponse = StringBuilder()
        llmInference.generateResponseAsync(prompt) { partialResult, done ->
            fullResponse.append(partialResult)
            onPartial(partialResult)
            if (done) onDone(fullResponse.toString())
        }
    }

    fun close() {
        llmInference.close()
    }
}
