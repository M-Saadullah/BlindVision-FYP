package com.example.tflitetest

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class ObjectSpeech(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var lastSpokenObject: String? = null
    private var lastSpokenTime: Long = 0
    private val speechCooldown = 2000
    private var isTtsInitialized = false
    private var currentLanguage = Locale.US

    init {
        tts = TextToSpeech(context, this, "com.google.android.tts") // Force Google TTS
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = currentLanguage
            tts?.setSpeechRate(1.0f)
            isTtsInitialized = true
            Log.d("TTS", "TextToSpeech initialized successfully")
        } else {
            Log.e("TTS", "Initialization failed with status: $status")
        }
    }

    fun setLanguageToUrdu() {
        if (isTtsInitialized) {
            currentLanguage = Locale("ur", "PK")
            val result = tts?.setLanguage(currentLanguage)
            when (result) {
                TextToSpeech.LANG_MISSING_DATA -> Log.e("TTS", "Urdu language data is missing")
                TextToSpeech.LANG_NOT_SUPPORTED -> Log.e("TTS", "Urdu is not supported by this TTS engine")
                TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_AVAILABLE -> {
                    Log.d("TTS", "Language set to Urdu successfully")
                }
                else -> Log.e("TTS", "Unknown error setting language: $result")
            }
        } else {
            Log.e("TTS", "TTS not initialized yet. Retrying in 500ms...")
            Handler(Looper.getMainLooper()).postDelayed({
                setLanguageToUrdu()
            }, 500)
        }
    }

    fun speakDefault() {
        val utteranceId = "SPEAK_DEFAULT"
        tts?.speak("Vision Assist has started. The default language is set to English. Speak Urdu to switch to Urdu or say continue", TextToSpeech.QUEUE_FLUSH, null, utteranceId)

        tts?.setOnUtteranceCompletedListener { id ->
            if (id == utteranceId) {
                (context as HomeActivity).runOnUiThread {
                    (context as HomeActivity).startSpeechRecognition()
                }
            }
        }
    }

    fun speakMessage1() {
        val message1 = if (currentLanguage.language == "ur") {
            "ایک بار ٹیپ کریں اندرونی موڈ کے لیے، دو بار ٹیپ کریں بیرونی موڈ کے لیے، اور تین بار ٹیپ کریں چیز تلاش کرنے والے موڈ کے لیے۔"
        } else {
            "Tap once for Indoor Mode, twice for Outdoor Mode, and three times for Object Finder Mode."
        }
        if (isTtsInitialized) {
            tts?.speak(message1, TextToSpeech.QUEUE_FLUSH, null, null)
            Log.d("TTS", "Speaking message1: $message1")
        } else {
            Log.e("TTS", "TTS not initialized yet. Retrying in 500ms...")
            Handler(Looper.getMainLooper()).postDelayed({
                speakMessage1()
            }, 500)
        }
    }

    fun speak(m: String) {
        if (isTtsInitialized) {
            tts?.speak(m, TextToSpeech.QUEUE_FLUSH, null, null)
            Log.d("TTS", "Speaking: $m")
        } else {
            Log.e("TTS", "TTS not initialized yet. Retrying in 500ms...")
            Handler(Looper.getMainLooper()).postDelayed({
                speak(m)
            }, 500)
        }
    }


    fun isUrdu():Boolean{
        if(currentLanguage.language=="ur"){
            return true
        }
        else{
            return false
        }

    }

    fun speakDetectedObject(objectName: String, distance: Double) {
        val message = if (currentLanguage.language == "ur") {
            "$objectName ${String.format("%.1f", distance)} میٹر دور ہے"
        } else {
            "$objectName is ${String.format("%.1f", distance)} meters away"
        }
        val currentTime = System.currentTimeMillis()
        if (message != lastSpokenObject || (currentTime - lastSpokenTime > speechCooldown)) {
            lastSpokenObject = message
            lastSpokenTime = currentTime
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        isTtsInitialized = false
    }
}