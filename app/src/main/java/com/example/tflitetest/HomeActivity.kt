package com.example.tflitetest

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.tflitetest.R
import com.google.android.material.card.MaterialCardView

import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class HomeActivity : AppCompatActivity() {

//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.home)
//
//        // Initialize UI elements
//        val menuButton: ImageButton = findViewById(R.id.menuButton)
//        val indoorCard: MaterialCardView = findViewById(R.id.indoorCard)
//        val outdoorCard: MaterialCardView = findViewById(R.id.outdoorCard)
//        val obstacleCard: MaterialCardView = findViewById(R.id.obstacleCard)
//
//        // Set click listeners to start CameraActivity
//        indoorCard.setOnClickListener {
//            startActivity(Intent(this, LiveCameraActivity::class.java))
//        }
//
//        outdoorCard.setOnClickListener {
//            startActivity(Intent(this, LiveCameraActivity::class.java))
//        }
//
//        obstacleCard.setOnClickListener {
//            startActivity(Intent(this, LiveCameraActivity::class.java))
//        }
//    }


//gesture+system feedback

        private lateinit var objectSpeech: ObjectSpeech
        private lateinit var gestureDetector: GestureDetector
        private lateinit var speechRecognizer: SpeechRecognizer
        private var tapCount = 0
        private val tapResetDelay: Long = 1000
        private val SPEECH_REQUEST_CODE = 1001
        private var isListening = false // Flag to control listening state

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.home)

            objectSpeech = ObjectSpeech(this)
            Handler(Looper.getMainLooper()).postDelayed({
                objectSpeech.speakDefault()
            }, 1000)
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            val menuButton: ImageButton = findViewById(R.id.menuButton)
            val rootLayout: View = findViewById(android.R.id.content)
            gestureDetector = GestureDetector(this, GestureListener())
            rootLayout.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                true
            }
        }

        // Check and request audio permission
        private fun checkAudioPermission() {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    100
                )
            } else {
                startSpeechRecognition()
            }
        }

        override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
        ) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeechRecognition()
            } else {
                objectSpeech.speak("Audio permission denied. Voice input won't work.")
            }
        }

        // Start speech recognition to listen for "URDU" or "CONTINUE"
        fun startSpeechRecognition() {
            if (!isListening) {
                isListening = true
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
                    putExtra(
                        RecognizerIntent.EXTRA_PROMPT,
                        "Say 'URDU' to switch to Urdu or 'CONTINUE' to stay in English"
                    )
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                startActivityForResult(intent, SPEECH_REQUEST_CODE)
            }
        }

        // Handle the speech recognition result
        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
                val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                results?.let {
                    if (it.isNotEmpty()) {
                        when (it[0].uppercase(Locale.US)) { // Case-insensitive comparison
                            "URDU" -> {
                                objectSpeech.setLanguageToUrdu()
                                objectSpeech.speak("زبان اردو میں تبدیل ہو گئی ہے")
                                Handler(Looper.getMainLooper()).postDelayed({
                                    objectSpeech.speakMessage1() // Speak Urdu instructions
                                }, 2500)
                                isListening = false // Stop listening after switching to Urdu
                            }

                            "CONTINUE" -> {
                                objectSpeech.speak("Continuing in English mode")
                                Handler(Looper.getMainLooper()).postDelayed({
                                    objectSpeech.speakMessage1() // Speak English instructions
                                }, 1500)
                                isListening = false // Stop listening after choosing English
                            }

                            else -> {
                                objectSpeech.speak("Please say 'URDU' to switch to Urdu or 'CONTINUE' to stay in English.")
                                Handler(Looper.getMainLooper()).postDelayed({
                                    startSpeechRecognition() // Restart listening if invalid input
                                }, 2000)
                            }
                        }
                    }
                }
            } else {
                // Restart listening if no result or error, unless explicitly stopped
                if (isListening) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        startSpeechRecognition()
                    }, 1000)
                }
            }
        }

        private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                Log.d("Gesture", "Single tap confirmed")
                handleTap(1) // Handle single tap
                return true
            }

            override fun onDown(e: MotionEvent): Boolean {
                Log.d("Gesture", "Touch down detected")
                return true
            }
        }

        private fun handleTap(tapType: Int) {
            tapCount++
            Log.d("TAP", "Tap detected: $tapCount")

            Handler(Looper.getMainLooper()).postDelayed({
                when (tapCount) {
                    1 -> {
                        objectSpeech.speak(if (objectSpeech.isUrdu()) "انڈور موڈ چالو ہو گیا" else "Indoor mode activated")
                        startActivity(Intent(this@HomeActivity, LiveCameraActivity::class.java))
                    }

                    2 -> {
                        objectSpeech.speak(if (objectSpeech.isUrdu()) "آؤٹ ڈور موڈ چالو ہو گیا" else "Outdoor mode activated")
                        startActivity(Intent(this@HomeActivity, LiveCameraActivity::class.java))
                    }

                    3 -> {
                        objectSpeech.speak(if (objectSpeech.isUrdu()) "آبجیکٹ فائنڈر موڈ چالو ہو گیا" else "Object Finder mode activated")
                        startActivity(Intent(this@HomeActivity, LiveCameraActivity::class.java))
                    }
                }
                tapCount = 0
            }, tapResetDelay)
        }

        override fun onDestroy() {
            objectSpeech.shutdown()
            speechRecognizer.destroy()
            super.onDestroy()
        }
    }
