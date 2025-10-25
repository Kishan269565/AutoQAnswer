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

    // ✅ Your HuggingFace API key
    private val huggingFaceToken = "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"

    // ✅ Rate limit
    private var apiCallCount = 0
    private val maxCallsPerHour = 50

    // Main function to get answer - UPDATED for MCQ format
    suspend fun getAnswer(question: String, context: String = ""): String = withContext(Dispatchers.IO) {
        try {
            if (apiCallCount >= maxCallsPerHour) return@withContext getOfflineAnswer(question)
            apiCallCount++
            
            // Use the correct model for MCQ answering
            return@withContext getHuggingFaceAnswer(question, context)
        } catch (e: Exception) {
            println("[AI ERROR] ${e.message}")
            return@withContext getOfflineAnswer(question)
        }
    }

    // ✅ UPDATED: Use deepset/roberta-base-squad2 for MCQ answering
    private suspend fun getHuggingFaceAnswer(question: String, context: String): String {
        if (huggingFaceToken.isBlank()) throw Exception("Missing HuggingFace API key")

        // Use the MCQ model that worked in your test
        val model = "deepset/roberta-base-squad2"
        
        val json = JSONObject().apply {
            put("inputs", JSONObject().apply {
                put("question", question)
                put("context", context)
            })
        }

        val request = Request.Builder()
            .url("https://api-inference.huggingface.co/models/$model")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $huggingFaceToken")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.body?.string()}")

        val body = response.body?.string() ?: ""
        return parseMCQResponse(body)
    }

    // ✅ UPDATED: Parse MCQ response format
    private fun parseMCQResponse(response: String): String {
        return try {
            val jsonObject = JSONObject(response)
            val answer = jsonObject.getString("answer")
            val score = jsonObject.getDouble("score")
            
            // Format: "Answer (Confidence: XX%)"
            val confidence = (score * 100).toInt()
            "$answer (Confidence: $confidence%)"
        } catch (e: Exception) {
            throw Exception("Failed to parse MCQ response: ${e.message}")
        }
    }

    // ✅ Enhanced offline fallback answers for technical MCQs
    private fun getOfflineAnswer(question: String): String {
        val q = question.lowercase(Locale.ROOT)
        return when {
            q.contains("time complexity of binary search") -> "O(log n)"
            q.contains("father of c") -> "Dennis Ritchie"
            q.contains("what is python") -> 
                "Python is a high-level, interpreted programming language known for its simplicity and readability."
            q.contains("what is java") -> 
                "Java is an object-oriented programming language designed to have minimal implementation dependencies."
            q.contains("fifo") || q.contains("first in first out") -> "Queue"
            q.contains("lifo") || q.contains("last in first out") -> "Stack"
            q.contains("time complexity of quick sort") -> "O(n²) worst case, O(n log n) average case"
            q.contains("sql") && q.contains("stand for") -> "Structured Query Language"
            else -> "Please provide context for accurate MCQ answering."
        }
    }

    // ✅ New method specifically for MCQ with context
    suspend fun getMCQAnswer(question: String, options: List<String> = emptyList(), context: String = ""): String {
        val fullQuestion = if (options.isNotEmpty()) {
            "$question Options: ${options.joinToString(", ")}"
        } else {
            question
        }
        return getAnswer(fullQuestion, context)
    }

    // Reset API call counter
    fun resetApiCounter() {
        apiCallCount = 0
    }
}
