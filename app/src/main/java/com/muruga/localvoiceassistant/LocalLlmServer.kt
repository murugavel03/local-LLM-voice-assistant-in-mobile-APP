package com.muruga.localvoiceassistant

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.IOException

/**
 * Exposes the on-device LLM over HTTP on the local network (Server mode).
 *
 * Once started, any device on the same WiFi can POST to:
 *   http://<phone-ip>:8080/generate
 *   Body: {"prompt": "your message here"}
 *   Response: {"response": "..."}
 */
class LocalLlmServer(
    private val llm: LocalLlmEngine,
    port: Int = 8080
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method != Method.POST || session.uri != "/generate") {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "application/json",
                """{"error":"POST /generate with {\"prompt\": \"...\"}"}"""
            )
        }

        return try {
            val files = HashMap<String, String>()
            session.parseBody(files)
            val body = files["postData"] ?: "{}"
            val prompt = JSONObject(body).optString("prompt", "")

            if (prompt.isBlank()) {
                return newFixedLengthResponse(
                    Response.Status.BAD_REQUEST,
                    "application/json",
                    """{"error":"Missing 'prompt' field"}"""
                )
            }

            val result = llm.generateResponse(prompt)
            val json = JSONObject().put("response", result).toString()
            newFixedLengthResponse(Response.Status.OK, "application/json", json)

        } catch (e: IOException) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"error":"${e.message}"}"""
            )
        }
    }
}
