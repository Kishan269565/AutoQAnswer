package com.example.autoqanswer

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.autoqanswer.databinding.ActivityCameraBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityCameraBinding
    private lateinit var answerProvider: AnswerProvider
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var lastProcessedText = ""
    private var isProcessing = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        answerProvider = AnswerProvider()
        startCamera()
        
        binding.statusText.text = "Camera active - Scanning for technical questions..."
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }
            
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis
                )
            } catch (exc: Exception) {
                Log.e("CameraActivity", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun processImage(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }
        
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }
        
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val detectedText = visionText.text.trim()
                
                if (isTechnicalQuestion(detectedText) && detectedText != lastProcessedText) {
                    isProcessing = true
                    lastProcessedText = detectedText
                    
                    runOnUiThread {
                        binding.statusText.text = "Question detected: Processing..."
                    }
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                             val answer = answerProvider.getAnswer(detectedText)
                            runOnUiThread {
                                displayQuestionAndAnswer(detectedText, answer)
                                binding.statusText.text = "Answer displayed - Scanning for next question..."
                            }
                            
                            // Reset after delay
                            android.os.Handler().postDelayed({
                                isProcessing = false
                                runOnUiThread {
                                    clearDisplay()
                                    binding.statusText.text = "Scanning for next question..."
                                }
                            }, 15000)
                        } catch (e: Exception) {
                            Log.e("CameraActivity", "Error getting answer", e)
                            runOnUiThread {
                                binding.statusText.text = "Error processing question"
                            }
                            isProcessing = false
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("TextRecognition", "Text recognition failed", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
    
    private fun isTechnicalQuestion(text: String): Boolean {
        if (text.length < 10) return false
        
        val questionIndicators = listOf(
            "what is", "how to", "explain", "difference between", "code for",
            "function", "algorithm", "program", "class", "method", "variable",
            "array", "list", "string", "integer", "boolean", "python", "java",
            "javascript", "c++", "html", "css", "react", "angular", "node",
            "database", "sql", "api", "endpoint", "framework", "library",
            "git", "docker", "kubernetes", "aws", "azure", "google cloud",
            "?", "explain", "define", "create", "build", "implement"
        )
        
        val cleanText = text.toLowerCase(Locale.ROOT)
        return questionIndicators.any { indicator ->
            cleanText.contains(indicator) && text.split(" ").size > 3
        }
    }
    
    private fun displayQuestionAndAnswer(question: String, answer: String) {
        binding.questionText.text = "Q: ${question.take(100)}${if (question.length > 100) "..." else ""}"
        binding.answerText.text = "A: $answer"
        binding.answerContainer.visibility = android.view.View.VISIBLE
    }
    
    private fun clearDisplay() {
        binding.questionText.text = ""
        binding.answerText.text = ""
        binding.answerContainer.visibility = android.view.View.GONE
        lastProcessedText = ""
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
