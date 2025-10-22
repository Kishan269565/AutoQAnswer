package com.example.autoqanswer

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AnswerOverlayActivity : AppCompatActivity() {

    private lateinit var answerText: TextView
    private lateinit var closeBtn: Button

    private val answerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "UPDATE_ANSWER" -> {
                    val answer = intent.getStringExtra("answer") ?: "No answer"
                    answerText.text = answer
                }
                "HIDE_OVERLAY" -> {
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make the activity look like an overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.END
            params.x = 0
            params.y = 100
            window.attributes = params
        }

        setContentView(R.layout.activity_overlay)

        answerText = findViewById(R.id.answerText)
        closeBtn = findViewById(R.id.closeBtn)

        closeBtn.setOnClickListener {
            finish()
        }

        // Register broadcast receiver
        val filter = IntentFilter().apply {
            addAction("UPDATE_ANSWER")
            addAction("HIDE_OVERLAY")
        }
        registerReceiver(answerReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(answerReceiver)
    }

    companion object {
        fun showAnswer(context: Context, answer: String) {
            val intent = Intent(context, AnswerOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("answer", answer)
            }
            context.startActivity(intent)
        }
    }
}