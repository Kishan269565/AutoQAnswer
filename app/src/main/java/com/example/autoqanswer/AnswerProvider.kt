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
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
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
            // 1. First try: Hugging Face (most generous free tier)
            getHuggingFaceAnswer(question)
        } catch (e: Exception) {
            try {
                // 2. Second try: DeepSeek (completely free API)
                getDeepSeekAnswer(question)
            } catch (e: Exception) {
                try {
                    // 3. Third try: OpenRouter free models
                    getOpenRouterAnswer(question)
                } catch (e: Exception) {
                    try {
                        // 4. Fourth try: Cohere (free tier)
                        getCohereAnswer(question)
                    } catch (e: Exception) {
                        // 5. Final fallback: Local answers
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
        
        val model = "microsoft/DialoGPT-large" // Free model
        
        val json = JSONObject().apply {
            put("inputs", "Provide a concise technical answer to: $question")
            put("parameters", JSONObject().apply {
                put("max_new_tokens", 300)
                put("temperature", 0.7)
            })
        }
        
        val request = Request.Builder()
            .url("https://api-inference.huggingface.co/models/$model")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $huggingFaceToken")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Hugging Face API failed: ${response.code}")
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
                    put("content", "Answer this technical question concisely: $question")
                })
            })
            put("max_tokens", 500)
            put("temperature", 0.7)
        }
        
        val request = Request.Builder()
            .url("https://api.deepseek.com/chat/completions")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $deepSeekApiKey")
            .header("Content-Type", "application/json")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("DeepSeek API failed: ${response.code}")
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
                    put("content", "Provide technical answer: $question")
                })
            })
            put("max_tokens", 400)
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
            throw Exception("OpenRouter API failed: ${response.code}")
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
            put("model", "command")
            put("prompt", "Provide a concise technical answer to: $question")
            put("max_tokens", 300)
            put("temperature", 0.7)
        }
        
        val request = Request.Builder()
            .url("https://api.cohere.ai/v1/generate")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $cohereApiKey")
            .header("Content-Type", "application/json")
            .build()
        
        val response = client.newCall(request).execute()
        
        if (!response.isSuccessful) {
            throw Exception("Cohere API failed: ${response.code}")
        }
        
        val responseBody = response.body?.string()
        return parseCohereResponse(responseBody ?: "")
    }
    
    // Response Parsing Methods
    private fun parseHuggingFaceResponse(response: String): String {
        return try {
            val jsonArray = JSONArray(response)
            if (jsonArray.length() > 0) {
                val firstItem = jsonArray.getJSONObject(0)
                firstItem.getString("generated_text")
            } else {
                throw Exception("Empty response from Hugging Face")
            }
        } catch (e: Exception) {
            throw Exception("Failed to parse Hugging Face response")
        }
    }
    
    private fun parseChatCompletionResponse(response: String): String {
        return try {
            val jsonObject = JSONObject(response)
            val choices = jsonObject.getJSONArray("choices")
            if (choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.getJSONObject("message")
                message.getString("content").trim()
            } else {
                throw Exception("No choices in response")
            }
        } catch (e: Exception) {
            throw Exception("Failed to parse chat completion response")
        }
    }
    
    private fun parseCohereResponse(response: String): String {
        return try {
            val jsonObject = JSONObject(response)
            val generations = jsonObject.getJSONArray("generations")
            if (generations.length() > 0) {
                val firstGen = generations.getJSONObject(0)
                firstGen.getString("text").trim()
            } else {
                throw Exception("No generations in response")
            }
        } catch (e: Exception) {
            throw Exception("Failed to parse Cohere response")
        }
    }
    
    // Offline fallback with your existing answers
    private fun getOfflineAnswer(question: String): String {
        val lowerQuestion = question.toLowerCase(Locale.ROOT)
        
        // Your existing predefined answers
        return when {
            lowerQuestion.contains("what is python") -> 
                "Python is a high-level, interpreted programming language known for its simplicity and readability. It's widely used for web development, data analysis, AI, and scientific computing."
            
            lowerQuestion.contains("what is java") -> 
                "Java is an object-oriented programming language designed to have minimal implementation dependencies. It follows 'write once, run anywhere' principle using JVM."
            
            lowerQuestion.contains("what is javascript") -> 
                "JavaScript is a scripting language primarily used for web development to create interactive effects within web browsers. It's essential for front-end development."
            
            lowerQuestion.contains("how to create a function") -> 
                "To create a function:\nPython: def function_name(params):\nJava: public void functionName(params) {}\nJavaScript: function functionName(params) {}"
            
            lowerQuestion.contains("difference between list and array") -> 
                "List: Dynamic size, flexible operations\nArray: Fixed size, better performance\nIn Python, lists are dynamic while arrays need import from array module."
            
            lowerQuestion.contains("what is oop") || lowerQuestion.contains("object oriented programming") -> 
                "OOP principles:\n- Encapsulation: Bundling data with methods\n- Inheritance: Creating new classes from existing ones\n- Polymorphism: Same interface, different implementations\n- Abstraction: Hiding complex reality while exposing essentials"
            
            lowerQuestion.contains("what is html") -> 
                "HTML (HyperText Markup Language) is the standard markup language for documents designed to be displayed in a web browser. It structures web content."
            
            lowerQuestion.contains("what is css") -> 
                "CSS (Cascading Style Sheets) is used to describe the presentation of HTML documents, including colors, layout, and fonts. It separates content from design."
            
            lowerQuestion.contains("what is react") -> 
                "React is a JavaScript library for building user interfaces, particularly web applications. It uses component-based architecture and virtual DOM for efficient updates."
            
            lowerQuestion.contains("what is linked list") -> 
                "A linked list is a linear data structure where elements are stored in nodes, each containing a data field and reference to the next node. Types: singly, doubly, circular."
            
            lowerQuestion.contains("bubble sort") -> 
                "Bubble Sort repeatedly steps through the list, compares adjacent elements and swaps them if they are in wrong order. Time complexity: O(n²)"
            
            lowerQuestion.contains("binary search") -> 
                "Binary Search finds the position of a target value within a sorted array by repeatedly dividing the search interval in half. Time complexity: O(log n)"
            
            lowerQuestion.contains("what is sql") -> 
                "SQL (Structured Query Language) is used to communicate with databases. Common commands: SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP."
            
            lowerQuestion.contains("difference between sql and nosql") -> 
                "SQL: Structured schema, ACID properties, vertical scaling\nNoSQL: Flexible schema, BASE properties, horizontal scaling\nSQL: MySQL, PostgreSQL | NoSQL: MongoDB, Cassandra"
            
            lowerQuestion.contains("what is git") -> 
                "Git is a distributed version control system. Key commands: git clone, git add, git commit, git push, git pull, git branch, git merge."
            
            lowerQuestion.contains("what is docker") -> 
                "Docker is a platform for developing, shipping, and running applications in containers. Containers package an application with all its dependencies."
            
            lowerQuestion.contains("what is api") -> 
                "API (Application Programming Interface) is a set of rules that allows programs to talk to each other. REST APIs use HTTP methods: GET, POST, PUT, DELETE."
            
            lowerQuestion.contains("reverse a string") -> 
                "Python: text[::-1]\nJava: new StringBuilder(text).reverse().toString()\nJavaScript: text.split('').reverse().join('')"
            
            lowerQuestion.contains("fibonacci sequence") -> 
                "Fibonacci: Each number is sum of two preceding ones.\nPython:\ndef fib(n):\n    a, b = 0, 1\n    for _ in range(n):\n        print(a)\n        a, b = b, a+b"
            
            lowerQuestion.contains("palindrome check") -> 
                "Palindrome: Reads same forwards and backwards.\nPython: text == text[::-1]\nJava: text.equals(new StringBuilder(text).reverse().toString())"
            
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
}
