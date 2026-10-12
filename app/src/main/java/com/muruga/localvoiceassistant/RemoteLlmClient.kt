package com.muruga.localvoiceassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sends prompts to another device's LocalLlmServer over the LAN,
 * instead of running the model on this device. Used in Client mode.
 */
class RemoteLlmClient {

    suspend fun generateResponse(serverIp: String, prompt: String): String =
        withContext(Dispatchers.IO) {
            val url = URL("http://$serverIp:8080/generate")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 60000

            val body = JSONObject().put("prompt", prompt).toString()
            val os: OutputStream = connection.outputStream
            os.write(body.toByteArray(Charsets.UTF_8))
            os.close()

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(responseText)
            if (json.has("response")) {
                json.getString("response")
            } else {
                throw Exception(json.optString("error", "Unknown server error"))
            }
        }
}
