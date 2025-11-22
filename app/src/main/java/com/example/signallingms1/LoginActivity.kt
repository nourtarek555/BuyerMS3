package com.example.signallingms1

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEt: EditText
    private lateinit var passEt: EditText
    private lateinit var loginBtn: Button
    private lateinit var registerLink: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        emailEt = findViewById(R.id.etEmail)
        passEt = findViewById(R.id.etPassword)
        loginBtn = findViewById(R.id.btnLogin)
        registerLink = findViewById(R.id.tvRegister)
        progressBar = findViewById(R.id.loginProgress)

        loginBtn.setOnClickListener {
            val email = emailEt.text.toString().trim()
            val password = passEt.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null) {
                            checkUserInBuyers(user.uid)
                        }
                    } else {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                    }
                }
        }

        registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun checkUserInBuyers(uid: String) {
        val dbRef = FirebaseDatabase.getInstance().getReference("Buyers").child(uid)
        dbRef.get().addOnSuccessListener {
            progressBar.visibility = View.GONE
            if (it.exists()) {
                Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, HomeActivity::class.java))
                finish()
            } else {
                Toast.makeText(this, "This account is not registered as a Buyer.", Toast.LENGTH_LONG).show()
                FirebaseAuth.getInstance().signOut()
            }
        }.addOnFailureListener {
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Error checking account: ${it.message}", Toast.LENGTH_LONG).show()
            FirebaseAuth.getInstance().signOut()
        }
    }
}
