package com.example.autoqanswer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class AnswerProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // ✅ Paste your HuggingFace API key here
    private val huggingFaceToken = "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"

    // ✅ Rate limit
    private var apiCallCount = 0
    private val maxCallsPerHour = 50

    // Main function to get answer
    suspend fun getAnswer(question: String): String = withContext(Dispatchers.IO) {
        try {
            if (apiCallCount >= maxCallsPerHour) return@withContext getOfflineAnswer(question)
            apiCallCount++
            return@withContext getHuggingFaceAnswer(question)
        } catch (e: Exception) {
            println("[AI ERROR] ${e.message}")
            return@withContext getOfflineAnswer(question)
        }
    }

    // ✅ HuggingFace API call (stable free model)
    private suspend fun getHuggingFaceAnswer(question: String): String {
        if (huggingFaceToken.isBlank()) throw Exception("Missing HuggingFace API key")

        val model = "mistralai/Mistral-7B-Instruct-v0.2"
        val json = JSONObject().apply {
            put("inputs", question)
            put("parameters", JSONObject().apply {
                put("max_new_tokens", 200)
                put("temperature", 0.3)
            })
        }

        val request = Request.Builder()
            .url("https://api-inference.huggingface.co/models/$model")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $huggingFaceToken")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

        val body = response.body?.string() ?: ""
        return parseHuggingFaceResponse(body)
    }

    // ✅ Parse HuggingFace JSON response
    private fun parseHuggingFaceResponse(response: String): String {
        return try {
            val arr = JSONArray(response)
            arr.getJSONObject(0).getString("generated_text").trim()
        } catch (e: Exception) {
            throw Exception("Failed to parse HuggingFace response: ${e.message}")
        }
    }

    // ✅ Offline fallback answers
    private fun getOfflineAnswer(question: String): String {
        val q = question.lowercase(Locale.ROOT)
        return when {
            q.contains("father of c") -> "Dennis Ritchie"
            q.contains("what is python") ->
                "Python is a high-level, interpreted programming language known for its simplicity and readability."
            q.contains("what is java") ->
                "Java is an object-oriented programming language designed to have minimal implementation dependencies."
            else -> "All AI services are temporarily unavailable."
        }
    }

    // Optional: reset API call counter
    fun resetApiCounter() {
        apiCallCount = 0
    }
}
