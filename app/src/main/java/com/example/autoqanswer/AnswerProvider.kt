package com.example.autoqanswer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AnswerProvider {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // FREE APIs - No payment needed
    suspend fun getAnswer(question: String, context: String): String = withContext(Dispatchers.IO) {
        // Try multiple free APIs in sequence
        val result = try {
            getHuggingFaceFree(question, context)
        } catch (e: Exception) {
            try {
                getGeminiFree(question, context)
            } catch (e2: Exception) {
                try {
                    getGroqFree(question, context)
                } catch (e3: Exception) {
                    "All free APIs are busy. Please try again in a moment."
                }
            }
        }
        return@withContext result
    }

    // 1. Hugging Face FREE (No API key needed)
    private suspend fun getHuggingFaceFree(question: String, context: String): String {
        val model = "distilbert-base-cased-distilled-squad" // Always free
        val json = JSONObject().apply {
            put("inputs", JSONObject().apply {
                put("question", question)
                put("context", context)
            })
        }

        val request = Request.Builder()
            .url("https://api-inference.huggingface.co/models/$model")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("HuggingFace busy")
        
        val body = response.body?.string() ?: ""
        return JSONObject(body).getString("answer")
    }

    // 2. Gemini FREE (Get key from https://aistudio.google.com/)
    private suspend fun getGeminiFree(question: String, context: String): String {
        val apiKey = "AIzaSyCNZhybPi8c00OQ7zk3vUtpiDvM0rMw0SY" // Replace with your free key
        val url = "https://generativelanguage.googleapis.com/v1/models/gemini-pro:generateContent?key=$apiKey"
        
        val prompt = "Context: $context\nQuestion: $question\nProvide a concise answer:"
        
        val json = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)
                put("maxOutputTokens", 100)
            })
        }

        val request = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        val jsonResponse = JSONObject(body)
        
        return jsonResponse.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    // 3. Groq FREE (Get key from https://console.groq.com/)
    private suspend fun getGroqFree(question: String, context: String): String {
        val apiKey = "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" // Replace with your free key
        val url = "https://api.groq.com/openai/v1/chat/completions"
        
        val prompt = "Based on this context: $context\n\nQuestion: $question\nAnswer briefly:"
        
        val json = JSONObject().apply {
            put("model", "llama2-70b-4096")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.1)
            put("max_tokens", 100)
        }

        val request = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .build()

        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: ""
        val jsonResponse = JSONObject(body)
        
        return jsonResponse.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }
}
