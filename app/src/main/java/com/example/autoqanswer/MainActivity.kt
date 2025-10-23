package com.example.autoqanswer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create a simple UI to prevent crashes
        val layout = LinearLayoutCompat(this).apply {
            orientation = LinearLayoutCompat.VERTICAL
            setPadding(50, 50, 50, 50)
        }
        
        val title = TextView(this).apply {
            text = "AutoQAnswer"
            textSize = 24f
            setPadding(0, 0, 0, 50)
        }
        
        val status = TextView(this).apply {
            text = "App is working!"
            textSize = 16f
            setPadding(0, 0, 0, 30)
        }
        
        val startButton = Button(this).apply {
            text = "Start Capture"
            setOnClickListener {
                status.text = "Capture started - Processing..."
                // Add your capture logic here later
            }
        }
        
        val stopButton = Button(this).apply {
            text = "Stop Capture"
            setOnClickListener {
                status.text = "Capture stopped"
                // Add your stop logic here later
            }
        }
        
        layout.addView(title)
        layout.addView(status)
        layout.addView(startButton)
        layout.addView(stopButton)
        
        setContentView(layout)
    }
}
