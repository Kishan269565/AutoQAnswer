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
        .connectTimeout(30, TimeUnit.SECONDS)  // Increased timeout
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    // API configuration - you can get these free keys
    private val huggingFaceToken = "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" // Free from huggingface.co
    private val cohereApiKey = "bElEz0T4mTigdR0cKtYod6WdEgztUQBQiT6Hn9W7" // Free tier from cohere.ai
    private val openRouterApiKey = "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" // Free from openrouter.ai
    private val deepSeekApiKey = "sk-8ebb7f0113f641a4af7f2a91f08ab349" // Free from deepseek.com
    
    // Track API usage to avoid hitting limits
    private var apiCallCount = 0
    private val maxCallsPerHour = 50 // Conservative limit
    
    suspend fun getAnswer(question: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            // Check if we're within rate limits
            if (apiCallCount >= maxCallsPerHour) {
                return@withContext getOfflineAnswer(question)
            }
            
            apiCallCount++
            getAIAnswerWithFallback(question)
        } catch (e: Exception) {
            "I apologize, but I'm having trouble connecting to AI services right now. ${getOfflineAnswer(question)}"
        }
    }
    
    private suspend fun getAIAnswerWithFallback(question: String): String {
        // Try APIs in order of reliability/free tier generosity
        return try {
            getHuggingFaceAnswer(question)
        } catch (e: Exception) {
            println("Hugging Face failed: ${e.message}")
            try {
                getDeepSeekAnswer(question)
            } catch (e: Exception) {
                println("DeepSeek failed: ${e.message}")
                try {
                    getOpenRouterAnswer(question)
                } catch (e: Exception) {
                    println("OpenRouter failed: ${e.message}")
                    try {
                        getCohereAnswer(question)
                    } catch (e: Exception) {
                        println("All APIs failed: ${e.message}")
                        getOfflineAnswer(question)
                    }
                }
            }
        }
    }
    
    // Method 1: Hugging Face Inference API (Free - Most Generous)
    private suspend fun getHuggingFaceAnswer(question: String): String {
        if (huggingFaceToken == "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") {
            throw Exception("Hugging Face token not configured")
        }
        
        // Use a more reliable model for Q&A
        val model = "google/flan-t5-large" // Better for Q&A tasks
        
        val json = JSONObject().apply {
            put("inputs", "Answer this question concisely and accurately: $question")
            put("parameters", JSONObject().apply {
                put("max_new_tokens", 150)
                put("temperature", 0.3)
                put("do_sample", true)
            })
        }
        
        val request = Request.Builder()
            .url("https://api-inference.huggingface.co/models/$model")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $huggingFaceToken")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Hugging Face API failed: ${response.code} - ${response.body?.string()}")
        }
        
        val responseBody = response.body?.string()
        return parseHuggingFaceResponse(responseBody ?: "")
    }
    
    // Method 2: DeepSeek API (Completely Free)
    private suspend fun getDeepSeekAnswer(question: String): String {
        if (deepSeekApiKey == "sk-8ebb7f0113f641a4af7f2a91f08ab349") {
            throw Exception("DeepSeek API key not configured")
        }
        
        val json = JSONObject().apply {
            put("model", "deepseek-chat")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Provide a clear and concise answer to: $question")
                })
            })
            put("max_tokens", 500)
            put("temperature", 0.7)
            put("stream", false)
        }
        
        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $deepSeekApiKey")
            .header("Content-Type", "application/json")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("DeepSeek API failed: ${response.code} - ${response.body?.string()}")
        }
        
        val responseBody = response.body?.string()
        return parseChatCompletionResponse(responseBody ?: "")
    }
    
    // Method 3: OpenRouter Free Models
    private suspend fun getOpenRouterAnswer(question: String): String {
        if (openRouterApiKey == "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") {
            throw Exception("OpenRouter API key not configured")
        }
        
        val json = JSONObject().apply {
            put("model", "huggingfaceh4/zephyr-7b-beta:free") // Free model
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Answer this question accurately: $question")
                })
            })
            put("max_tokens", 400)
            put("temperature", 0.7)
        }
        
        val request = Request.Builder()
            .url("https://openrouter.ai/api/v1/chat/completions")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $openRouterApiKey")
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "https://autoqanswer.com")
            .header("X-Title", "AutoQAnswer")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("OpenRouter API failed: ${response.code} - ${response.body?.string()}")
        }
        
        val responseBody = response.body?.string()
        return parseChatCompletionResponse(responseBody ?: "")
    }
    
    // Method 4: Cohere API (Free Tier)
    private suspend fun getCohereAnswer(question: String): String {
        if (cohereApiKey == "bElEz0T4mTigdR0cKtYod6WdEgztUQBQiT6Hn9W7") {
            throw Exception("Cohere API key not configured")
        }
        
        val json = JSONObject().apply {
            put("model", "command")  // Cohere's best model for instructions
            put("prompt", "Provide a concise and accurate answer to this question: $question")
            put("max_tokens", 300)
            put("temperature", 0.7)
            put("stop_sequences", JSONArray().put("\n"))
        }
        
        val request = Request.Builder()
            .url("https://api.cohere.ai/v1/generate")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $cohereApiKey")
            .header("Content-Type", "application/json")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Cohere API failed: ${response.code} - ${response.body?.string()}")
        }
        
        val responseBody = response.body?.string()
        return parseCohereResponse(responseBody ?: "")
    }
    
    // Response Parsing Methods
    private fun parseHuggingFaceResponse(response: String): String {
        return try {
            // Handle different response formats from Hugging Face
            if (response.startsWith("[")) {
                val jsonArray = JSONArray(response)
                if (jsonArray.length() > 0) {
                    val firstItem = jsonArray.getJSONObject(0)
                    if (firstItem.has("generated_text")) {
                        return firstItem.getString("generated_text").trim()
                    }
                }
            } else {
                val jsonObject = JSONObject(response)
                if (jsonObject.has("generated_text")) {
                    return jsonObject.getString("generated_text").trim()
                }
            }
            throw Exception("Unexpected Hugging Face response format")
        } catch (e: Exception) {
            throw Exception("Failed to parse Hugging Face response: ${e.message}")
        }
    }
    
    private fun parseChatCompletionResponse(response: String): String {
        return try {
            val jsonObject = JSONObject(response)
            val choices = jsonObject.getJSONArray("choices")
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                if (firstChoice.has("message")) {
                    val message = firstChoice.getJSONObject("message")
                    message.getString("content").trim()
                } else if (firstChoice.has("text")) {
                    firstChoice.getString("text").trim()
                } else {
                    throw Exception("No message content in response")
                }
            } else {
                throw Exception("No choices in response")
            }
        } catch (e: Exception) {
            throw Exception("Failed to parse chat completion response: ${e.message}")
        }
    }
    
    private fun parseCohereResponse(response: String): String {
        return try {
            val jsonObject = JSONObject(response)
            if (jsonObject.has("generations")) {
                val generations = jsonObject.getJSONArray("generations")
                if (generations.length() > 0) {
                    val firstGen = generations.getJSONObject(0)
                    firstGen.getString("text").trim()
                } else {
                    throw Exception("No generations in response")
                }
            } else if (jsonObject.has("text")) {
                jsonObject.getString("text").trim()
            } else {
                throw Exception("Unexpected Cohere response format")
            }
        } catch (e: Exception) {
            throw Exception("Failed to parse Cohere response: ${e.message}")
        }
    }
    
    // Offline fallback with your existing answers
    private fun getOfflineAnswer(question: String): String {
        val lowerQuestion = question.toLowerCase(Locale.ROOT)
        
        // Add the father of C language answer
        return when {
            lowerQuestion.contains("father of c language") || 
            lowerQuestion.contains("who created c") || 
            lowerQuestion.contains("who developed c") -> 
                "Dennis Ritchie"
            
            lowerQuestion.contains("what is python") -> 
                "Python is a high-level, interpreted programming language known for its simplicity and readability. It's widely used for web development, data analysis, AI, and scientific computing."
            
            lowerQuestion.contains("what is java") -> 
                "Java is an object-oriented programming language designed to have minimal implementation dependencies. It follows 'write once, run anywhere' principle using JVM."
            
            lowerQuestion.contains("what is javascript") -> 
                "JavaScript is a scripting language primarily used for web development to create interactive effects within web browsers. It's essential for front-end development."
            
            // ... keep your other offline answers the same ...
            
            else -> 
                "I'm currently using offline responses. For AI-powered answers to any question, please configure free API keys from:\n\n" +
                "1. Hugging Face (huggingface.co) - Most recommended\n" +
                "2. DeepSeek (deepseek.com) - Completely free\n" +
                "3. OpenRouter (openrouter.ai) - Free models available\n" +
                "4. Cohere (cohere.ai) - Free tier available\n\n" +
                "Configure your API keys in the AnswerProvider.kt file."
        }
    }
    
    // Reset counter (could call this hourly or based on app lifecycle)
    fun resetApiCounter() {
        apiCallCount = 0
    }
    
    // Get current API status for debugging
    fun getApiStatus(): String {
        return "API calls this session: $apiCallCount/$maxCallsPerHour"
    }
}
