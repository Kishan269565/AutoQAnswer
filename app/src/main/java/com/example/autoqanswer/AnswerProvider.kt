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

    // ✅ Insert your real API keys here
    private val huggingFaceToken = "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"
    private val deepSeekApiKey = "sk-8ebb7f0113f641a4af7f2a91f08ab349"
    private val openRouterApiKey = "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"
    private val cohereApiKey = "bElEz0T4mTigdR0cKtYod6WdEgztUQBQiT6Hn9W7"

    // ✅ Rate limit protection
    private var apiCallCount = 0
    private val maxCallsPerHour = 50

    suspend fun getAnswer(question: String): String = withContext(Dispatchers.IO) {
        try {
            if (apiCallCount >= maxCallsPerHour) return@withContext getOfflineAnswer(question)
            apiCallCount++
            return@withContext getAIAnswerWithFallback(question)
        } catch (e: Exception) {
            return@withContext "AI services unavailable. ${getOfflineAnswer(question)}"
        }
    }

    private suspend fun getAIAnswerWithFallback(question: String): String {
        try {
            println("[AI] Trying HuggingFace...")
            return getHuggingFaceAnswer(question)
        } catch (e: Exception) {
            println("[AI] HuggingFace failed: ${e.message}")
        }

        try {
            println("[AI] Trying DeepSeek...")
            return getDeepSeekAnswer(question)
        } catch (e: Exception) {
            println("[AI] DeepSeek failed: ${e.message}")
        }

        try {
            println("[AI] Trying OpenRouter...")
            return getOpenRouterAnswer(question)
        } catch (e: Exception) {
            println("[AI] OpenRouter failed: ${e.message}")
        }

        try {
            println("[AI] Trying Cohere...")
            return getCohereAnswer(question)
        } catch (e: Exception) {
            println("[AI] Cohere failed: ${e.message}")
        }

        println("[AI] All APIs failed → Using Offline Answer")
        return getOfflineAnswer(question)
    }

    // ✅ API #1 — HuggingFace (Stable & Fast Model)
    private suspend fun getHuggingFaceAnswer(question: String): String {
        if (huggingFaceToken.isBlank()) throw Exception("Missing Key")
        val model = "mistralai/Mistral-7B-Instruct-v0.2"

        val json = JSONObject().apply {
            put("inputs", "Answer clearly: $question")
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
        return parseHuggingFaceResponse(response.body?.string()!!)
    }

    // ✅ API #2 — DeepSeek (fixed endpoint)
    private suspend fun getDeepSeekAnswer(question: String): String {
        if (deepSeekApiKey.isBlank()) throw Exception("Missing Key")

        val json = JSONObject().apply {
            put("model", "deepseek-chat")
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", question)
            }))
        }

        val request = Request.Builder()
            .url("https://api.deepseek.com/v1/chat/completions")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $deepSeekApiKey")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        return parseChatResponse(response.body?.string()!!)
    }

    // ✅ API #3 — OpenRouter (clean headers)
    private suspend fun getOpenRouterAnswer(question: String): String {
        if (openRouterApiKey.isBlank()) throw Exception("Missing Key")

        val json = JSONObject().apply {
            put("model", "gryphe/mythomax-l2-13b")
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", question)
            }))
        }

        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $openRouterApiKey")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        return parseChatResponse(response.body?.string()!!)
    }

    // ✅ API #4 — Cohere
    private suspend fun getCohereAnswer(question: String): String {
        if (cohereApiKey.isBlank()) throw Exception("Missing Key")

        val json = JSONObject().apply {
            put("model", "command-r")
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", question)
            }))
        }

        val request = Request.Builder()
            .url("https://api.cohere.ai/v1/chat")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $cohereApiKey")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        return parseChatResponse(response.body?.string()!!)
    }

    // ✅ Response Parsers
    private fun parseHuggingFaceResponse(response: String): String {
        val arr = JSONArray(response)
        return arr.getJSONObject(0).getString("generated_text").trim()
    }

    private fun parseChatResponse(response: String): String {
        val obj = JSONObject(response)
        return obj.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content").trim()
    }

    // ✅ Offline backup
    private fun getOfflineAnswer(question: String): String {
        val q = question.lowercase(Locale.ROOT)
        return when {
            q.contains("father of c") -> "Dennis Ritchie"
            else -> "All AI services are temporarily unavailable."
        }
    }
}
