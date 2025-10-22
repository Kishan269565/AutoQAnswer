package com.example.autoqanswer

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

class ScreenCaptureService : Service() {
    companion object {
        const val TAG = "ScreenCaptureService"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "RESULT_CODE"
        const val EXTRA_RESULT_INTENT = "RESULT_INTENT"
    }

    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var handler: Handler? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Rate limiting
    private val lastCallTs = AtomicLong(0L)
    private val MIN_CALL_INTERVAL_MS = 15000L // 15 seconds between LLM calls

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val thread = HandlerThread("cap-thread")
        thread.start()
        handler = Handler(thread.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_INTENT)
                startForeground(1, makeNotification())
                startProjection(resultCode, resultData)
            }
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun makeNotification(): Notification {
        val channelId = "autoq_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "AutoQ", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(chan)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AutoQ Answer: Capturing Screen")
            .setContentText("Looking for questions...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }

    private fun startProjection(resultCode: Int, resultData: Intent?) {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, resultData!!)

        val width = 720
        val height = 1280
        val density = resources.displayMetrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
        projection?.createVirtualDisplay("AutoQDisplay", width, height, density,
            0, imageReader?.surface, null, handler)

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            processBitmap(cropped)
        }, handler)
    }

    private fun processBitmap(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.text
                Log.d(TAG, "Recognized text: $text")
                if (text.isNotBlank() && looksLikeQuestion(text)) {
                    scope.launch {
                        // Choose model based on question type
                        val model = if (text.contains("import ") || text.contains("def ") || 
                                       text.contains("class ") || text.contains("function ") ||
                                       text.contains("printf") || text.contains("cout")) {
                            "bigcode/starcoder"
                        } else {
                            "google/flan-t5-large"
                        }
                        val answer = askHuggingFaceModel(text, model)
                        Log.d(TAG, "LLM answer: $answer")
                        
                        // Show answer in overlay
                        showAnswerOverlay(answer)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "OCR failed", e)
            }
    }

    private fun looksLikeQuestion(text: String): Boolean {
        val s = text.trim().lowercase()
        if (s.contains("?")) return true
        
        val qWords = listOf("what", "why", "how", "when", "where", "which", "who", 
                           "explain", "solve", "find", "implement", "calculate")
        for (w in qWords) {
            if (s.startsWith("$w ") || s.contains(" $w ") || s.contains("\n$w ")) return true
        }

        // Code detection
        val codeMarkers = listOf("def ", "class ", "import ", "public ", "console.log", 
                               "System.out.println", "printf(", "function ", "void ", "int main")
        if (codeMarkers.any { s.contains(it) }) return true

        return false
    }

    private suspend fun askHuggingFaceModel(question: String, modelName: String = "bigcode/starcoder"): String {
        // Throttle calls
        val now = System.currentTimeMillis()
        val prev = lastCallTs.get()
        if (now - prev < MIN_CALL_INTERVAL_MS) {
            val wait = (MIN_CALL_INTERVAL_MS - (now - prev)) / 1000
            return "(Throttled: Please wait ${wait}s)"
        }
        lastCallTs.set(now)

        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val payload = JSONObject()
                val prompt = "You are an expert programmer and teacher. Answer concisely and clearly. If code is required, include code blocks.\n\nQuestion:\n$question\n\nAnswer:"
                payload.put("inputs", prompt)

                val params = JSONObject()
                params.put("max_new_tokens", 300)
                params.put("temperature", 0.2)
                payload.put("parameters", params)

                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = RequestBody.create(mediaType, payload.toString())

                val url = "https://api-inference.huggingface.co/models/$modelName"

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                val resp = client.newCall(request).execute()
                val respBody = resp.body?.string() ?: ""

                if (!resp.isSuccessful) {
                    return@withContext "(Error ${resp.code}: ${respBody.take(100)}...)"
                }

                // Parse response
                return@withContext parseHuggingFaceResponse(respBody)
            } catch (e: Exception) {
                Log.e(TAG, "HF call failed", e)
                return@withContext "(Error: ${e.localizedMessage})"
            }
        }
    }

    private fun parseHuggingFaceResponse(respBody: String): String {
        return try {
            if (respBody.trim().startsWith("[")) {
                val arr = org.json.JSONArray(respBody)
                if (arr.length() > 0) {
                    val first = arr.getJSONObject(0)
                    if (first.has("generated_text")) first.getString("generated_text")
                    else respBody.take(200)
                } else {
                    "Empty response array"
                }
            } else {
                val jo = JSONObject(respBody)
                if (jo.has("generated_text")) jo.getString("generated_text")
                else if (jo.has("choices")) {
                    val ch = jo.getJSONArray("choices").getJSONObject(0)
                    if (ch.has("text")) ch.getString("text") else respBody.take(200)
                } else {
                    respBody.take(200)
                }
            }
        } catch (e: Exception) {
            respBody.take(200)
        }
    }

    private fun showAnswerOverlay(answer: String) {
        val intent = Intent(this, AnswerOverlayActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("answer", answer)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        projection?.stop()
        imageReader?.close()
        scope.cancel()
        handler?.looper?.quit()
    }
}