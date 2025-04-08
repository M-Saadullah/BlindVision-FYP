package com.example.tflitetest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Handler(Looper.getMainLooper()).postDelayed({
            val sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)

            val isUserRemembered = sharedPreferences.getBoolean("isUserRemembered", false)
            val isUserSignedIn = sharedPreferences.getBoolean("isUserSignedIn", false)
            val isUserSignedUp = sharedPreferences.getBoolean("isUserSignedUp", false)

            val nextActivity = when {
                isUserRemembered -> HomeActivity::class.java
                isUserSignedIn -> HomeActivity::class.java
                isUserSignedUp -> SignInActivity::class.java
                else -> SignUpActivity::class.java
            }

            startActivity(Intent(this, nextActivity))
            finish()
        }, 3000)
    }
}

