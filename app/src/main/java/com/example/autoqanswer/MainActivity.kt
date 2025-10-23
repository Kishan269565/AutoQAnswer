package com.example.autoqanswer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.autoqanswer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.startButton.setOnClickListener {
            // Start camera activity
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }
        
        binding.stopButton.setOnClickListener {
            finish() // Close app
        }
    }
}
