package com.example.tflitetest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignUpActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnCreateAccount: Button
    private lateinit var tvLogin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sign_up)

        // Initialize Firebase Auth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Initialize UI elements
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        tvLogin = findViewById(R.id.tvLogin)

        btnCreateAccount.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (validateInputs(email, password, confirmPassword)) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
                            val user = hashMapOf("email" to email)

                            // Show toast before Firestore operation
                            Toast.makeText(this, "User Registered Successfully", Toast.LENGTH_SHORT).show()

                            // Navigate to SignInActivity immediately
                            startActivity(Intent(this, SignInActivity::class.java))
                            finish()

                            // Save user data in Firestore (runs in background)
                            db.collection("users").document(userId)
                                .set(user)
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Firestore Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            val errorMessage = task.exception?.message ?: "Unknown error"
                            Toast.makeText(this, "Sign Up Failed: $errorMessage", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }



        // Navigate to Sign In page
        tvLogin.setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
        }
    }

    private fun validateInputs(email: String, password: String, confirmPassword: String): Boolean {
        if (email.isEmpty()) {
            etEmail.error = "Email is required"
            return false
        }

        if (password.length < 6) {
            etPassword.error = "Password must be at least 6 characters"
            return false
        }

        if (password != confirmPassword) {
            etConfirmPassword.error = "Passwords do not match"
            return false
        }

        return true
    }

//    private fun registerUser(email: String, password: String) {
//        auth.createUserWithEmailAndPassword(email, password)
//            .addOnCompleteListener { task ->
//                if (task.isSuccessful) {
//                    val userId = auth.currentUser?.uid ?: return@addOnCompleteListener
//                    val user = hashMapOf("email" to email)
//
//                    db.collection("users").document(userId)
//                        .set(user)
//                        .addOnSuccessListener {
//                            Toast.makeText(this, "User Registered Successfully", Toast.LENGTH_SHORT).show()
//                            startActivity(Intent(this, SignInActivity::class.java))
//                        }
//                        .addOnFailureListener { e ->
//                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
//                        }
//                } else {
//                    Toast.makeText(this, "Sign Up Failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
//                }
//            }
//    }


    private fun saveUserSignInState() {
        val sharedPreferences = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("isUserSignedIn", true).apply()
    }

}
