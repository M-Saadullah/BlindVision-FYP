package com.example.tflitetest

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MenuActivity : AppCompatActivity() {

    private lateinit var languageSpinner: Spinner
    private lateinit var notificationSwitch: Switch
    // private lateinit var privacyButton: Button
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings)

        // Initialize Views
        languageSpinner = findViewById(R.id.language_spinner)
        notificationSwitch = findViewById(R.id.notifications_switch)
        //privacyButton = findViewById(R.id.privacy_button)

        // Shared Preferences to Save User Settings
        sharedPreferences = getSharedPreferences("AppSettings", MODE_PRIVATE)

        // Load Saved Settings
        loadSettings()

        // Handle Language Change
        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedLanguage = parent?.getItemAtPosition(position).toString()
                saveSetting("language", selectedLanguage)
                Toast.makeText(this@MenuActivity, "Language set to $selectedLanguage", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Handle Notifications Switch
        notificationSwitch.setOnCheckedChangeListener { _, isChecked ->
            saveSetting("notifications", isChecked.toString())
            Toast.makeText(this, if (isChecked) "Notifications Enabled" else "Notifications Disabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveSetting(key: String, value: String) {
        val editor = sharedPreferences.edit()
        editor.putString(key, value)
        editor.apply()
    }

    private fun loadSettings() {
        val savedLanguage = sharedPreferences.getString("language", "English")
        val savedNotification = sharedPreferences.getString("notifications", "true")

        // Set Language Spinner Selection
        val languageArray = resources.getStringArray(R.array.language_options)
        val languageIndex = languageArray.indexOf(savedLanguage)
        if (languageIndex >= 0) {
            languageSpinner.setSelection(languageIndex)
        }

        // Set Notifications Switch State
        notificationSwitch.isChecked = savedNotification == "true"
    }
}
