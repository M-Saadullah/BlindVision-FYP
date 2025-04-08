package com.example.tflitetest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignInActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSignIn: Button
    private lateinit var btnCreateAccount: Button
    private lateinit var tvForgotPassword: TextView
    private lateinit var cbRememberMe: CheckBox
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_inn)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Initialize UI elements
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnSignIn = findViewById(R.id.btnSignIn)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        cbRememberMe = findViewById(R.id.cbRemember) // Checkbox for "Remember Me"

        // Check if user was remembered
        checkRememberedUser()

        // Sign In Button Click
        btnSignIn.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val rememberMe = cbRememberMe.isChecked

            if (validateInputs(email, password)) {
                signInWithEmail(email, password, rememberMe)
            }
        }

        // Create Account Button Click
        btnCreateAccount.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        if (email.isEmpty()) {
            etEmail.error = "Email is required"
            return false
        }
        if (password.isEmpty()) {
            etPassword.error = "Password is required"
            return false
        }
        return true
    }

    private fun signInWithEmail(email: String, password: String, rememberMe: Boolean) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        checkUserExistsInFirestore(userId) { exists ->
                            if (exists) {
                                if (rememberMe) {
                                    saveRememberMeState(email, password)
                                }
                                Toast.makeText(this, "Sign-in successful!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, HomeActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(this, "User does not exist in database!", Toast.LENGTH_LONG).show()
                                auth.signOut()
                            }
                        }
                    }
                } else {
                    Toast.makeText(this, "Sign-in failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun checkUserExistsInFirestore(userId: String, callback: (Boolean) -> Unit) {
        db.collection("users").document(userId).get()
            .addOnSuccessListener { document -> callback(document.exists()) }
            .addOnFailureListener { callback(false) }
    }

    private fun saveRememberMeState(email: String, password: String) {
        val sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("rememberedEmail", email)
        editor.putString("rememberedPassword", password)
        editor.putLong("rememberMeTimestamp", System.currentTimeMillis()) // Save current time
        editor.putBoolean("isUserRemembered", true) // Mark user as remembered
        editor.apply()
    }

    private fun checkRememberedUser() {
        val sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val email = sharedPreferences.getString("rememberedEmail", null)
        val password = sharedPreferences.getString("rememberedPassword", null)
        val timestamp = sharedPreferences.getLong("rememberMeTimestamp", 0)
        val currentTime = System.currentTimeMillis()

        // Check if 30 days have passed
        if (email != null && password != null && currentTime - timestamp < 30 * 24 * 60 * 60 * 1000) {
            signInWithEmail(email, password, rememberMe = true) // Auto-login
        }
    }
}
