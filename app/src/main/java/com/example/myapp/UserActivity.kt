package com.example.myapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import android.view.WindowManager
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database

class UserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_user)
        applySecureFlagForUser()

        val email = Firebase.auth.currentUser?.email.orEmpty()
        val key = email.lowercase().replace(Regex("[.#$\\[\\]/]"), "_")
        val btnProc = findViewById<Button>(R.id.btnProcedimientos)
        val btnDiag = findViewById<Button>(R.id.btnDiagramas)
        btnProc.isEnabled = false
        btnDiag.isEnabled = false
        Firebase.database.reference.child("pending_emails").child(key).get().addOnSuccessListener { snap ->
            val pending = snap.exists() && snap.value != null
            if (pending) {
                Toast.makeText(this, "Tu cuenta está pendiente de aprobación", Toast.LENGTH_LONG).show()
                Firebase.auth.signOut()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                btnProc.isEnabled = true
                btnDiag.isEnabled = true
            }
        }

        findViewById<Button>(R.id.userLogoutButton).setOnClickListener {
            Firebase.auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        findViewById<Button>(R.id.btnProcedimientos).setOnClickListener {
            startActivity(Intent(this, VerProcedimientosActivity::class.java))
        }
        findViewById<Button>(R.id.btnDiagramas).setOnClickListener {
            startActivity(Intent(this, VerProcedimientosElecActivity::class.java))
        }
    }

    private fun applySecureFlagForUser() {
        val emailNow = Firebase.auth.currentUser?.email
        val uid = Firebase.auth.currentUser?.uid
        val isAdminEmail = emailNow?.equals("markusflorez37@gmail.com", true) == true || emailNow?.equals("markus17@gmail.com", true) == true || emailNow?.equals("cacastilla12@gmail.com", true) == true
        if (isAdminEmail) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            return
        }
        if (uid == null) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            return
        }
        Firebase.database.reference.child("admins").child(uid).get().addOnSuccessListener { snapshot ->
            val raw = snapshot.value
            val isAdmin = if (snapshot.exists()) {
                when (raw) {
                    is Boolean -> raw
                    is String -> raw.equals("true", true) || raw == "1"
                    is Number -> raw.toInt() == 1
                    else -> false
                }
            } else false
            if (isAdmin) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            }
        }.addOnFailureListener {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
