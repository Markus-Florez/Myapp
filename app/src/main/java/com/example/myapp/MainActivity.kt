package com.example.myapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.FirebaseApp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.EmailAuthProvider
 

class MainActivity : ComponentActivity() {
    private lateinit var loginButtonRef: Button
    private var loginThrottleUntil: Long = 0L
    private var linkPendingEmail: String? = null
    private var linkPendingPassword: String? = null
    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken.isNullOrBlank()) {
                Toast.makeText(this, "Configura default_web_client_id en Google Services", Toast.LENGTH_LONG).show()
                return@registerForActivityResult
            }
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            Firebase.auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                if (!authTask.isSuccessful) {
                    val msg = authTask.exception?.localizedMessage ?: getString(R.string.login_error_auth)
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    return@addOnCompleteListener
                }
                val auth = Firebase.auth
                val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
                val emailNow = auth.currentUser?.email.orEmpty()
                val emailKey = sanitizeEmail(emailNow)

                val pendingEmail = linkPendingEmail
                val pendingPass = linkPendingPassword
                if (!pendingEmail.isNullOrBlank() && !pendingPass.isNullOrBlank() && emailNow.equals(pendingEmail, true)) {
                    val cred = EmailAuthProvider.getCredential(pendingEmail, pendingPass)
                    auth.currentUser?.linkWithCredential(cred)?.addOnSuccessListener {
                        Toast.makeText(this, "Contraseña vinculada a tu cuenta", Toast.LENGTH_LONG).show()
                        linkPendingEmail = null
                        linkPendingPassword = null
                    }?.addOnFailureListener { e ->
                        Toast.makeText(this, e.localizedMessage ?: "Error vinculando contraseña", Toast.LENGTH_LONG).show()
                        linkPendingEmail = null
                        linkPendingPassword = null
                    }
                }
                val db = Firebase.database.reference
                db.child("users_emails").child(emailKey).child("needs_approval").get().addOnSuccessListener { nSnap ->
                    val needsApproval = !nSnap.exists() || (nSnap.value as? Boolean) == true || (nSnap.value as? String)?.equals("true", true) == true
                    val isAdminEmail = emailNow.equals("markusflorez37@gmail.com", true) || emailNow.equals("markus17@gmail.com", true) || emailNow.equals("cacastilla12@gmail.com", true)
                    if (needsApproval && !isAdminEmail) {
                        db.child("pending_emails").child(emailKey).setValue(mapOf(
                            "email" to emailNow,
                            "ts" to System.currentTimeMillis()
                        ))
                        Toast.makeText(this, "Tu cuenta está pendiente de aprobación", Toast.LENGTH_LONG).show()
                        Firebase.auth.signOut()
                        return@addOnSuccessListener
                    }
                    db.child("disabled_users").child(uid).get().addOnSuccessListener { dSnap ->
                        val disabled = (dSnap.value as? Boolean) == true || (dSnap.value as? String)?.equals("true", true) == true
                        if (disabled) {
                            Toast.makeText(this, "Cuenta inhabilitada por admin", Toast.LENGTH_LONG).show()
                            Firebase.auth.signOut()
                            return@addOnSuccessListener
                        }
                        db.child("disabled_emails").child(emailKey).get().addOnSuccessListener { eSnap ->
                            val disabledEmail = (eSnap.value as? Boolean) == true || (eSnap.value as? String)?.equals("true", true) == true
                            if (disabledEmail) {
                                Toast.makeText(this, "Cuenta inhabilitada por admin", Toast.LENGTH_LONG).show()
                                Firebase.auth.signOut()
                                return@addOnSuccessListener
                            }
                            db.child("pending_emails").child(emailKey).get().addOnSuccessListener { pSnap ->
                                val pending = pSnap.exists() && pSnap.value != null
                                if (pending && needsApproval) {
                                    Toast.makeText(this, "Tu cuenta está pendiente de aprobación", Toast.LENGTH_LONG).show()
                                    Firebase.auth.signOut()
                                    return@addOnSuccessListener
                                }
                                db.child("users").child(uid).updateChildren(mapOf(
                                    "email" to emailNow,
                                    "last_login" to System.currentTimeMillis()
                                ))
                                db.child("users_emails").child(emailKey).updateChildren(mapOf(
                                    "email" to emailNow,
                                    "uid" to uid,
                                    "last_login" to System.currentTimeMillis()
                                ))
                                val adminsRef = db.child("admins").child(uid)
                                adminsRef.get().addOnSuccessListener { snapshot ->
                                    val raw = snapshot.value
                                    val isAdmin = if (snapshot.exists()) {
                                        when (raw) {
                                            is Boolean -> raw
                                            is String -> raw.equals("true", true) || raw == "1"
                                            is Number -> raw.toInt() == 1
                                            else -> false
                                        }
                                    } else false
                                    val isAdminEmail2 = emailNow.equals("markusflorez37@gmail.com", true) || emailNow.equals("markus17@gmail.com", true) || emailNow.equals("cacastilla12@gmail.com", true)
                                    if (isAdmin || isAdminEmail2) {
                                        adminsRef.setValue(true)
                                        Toast.makeText(this@MainActivity, getString(R.string.login_welcome_admin), Toast.LENGTH_SHORT).show()
                                        startActivity(Intent(this@MainActivity, AdminActivity::class.java))
                                        finish()
                                    } else {
                                        Toast.makeText(this@MainActivity, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                                        startActivity(Intent(this@MainActivity, UserActivity::class.java))
                                        finish()
                                    }
                                }.addOnFailureListener {
                                    val isAdminEmail3 = emailNow.equals("markusflorez37@gmail.com", true) || emailNow.equals("markus17@gmail.com", true) || emailNow.equals("cacastilla12@gmail.com", true)
                                    if (isAdminEmail3) startActivity(Intent(this@MainActivity, AdminActivity::class.java)) else startActivity(Intent(this@MainActivity, UserActivity::class.java))
                                    finish()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: ApiException) {
            val code = e.statusCode
            val msg = when (code) {
                12501 -> "Inicio cancelado"
                10 -> "Configura SHA-1/SHA-256 y Google en Firebase"
                7 -> "Sin conexión a Internet"
                8 -> "Error interno de Google Play Services"
                else -> "Error al iniciar con Google ($code)"
            }
            Log.e("GoogleSignIn", "Fallo GoogleSignIn: code=$code", e)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContentView(R.layout.login)

        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.PasswordEditText)
        loginButtonRef = findViewById<Button>(R.id.logInButton)
        val signUpButton = findViewById<Button>(R.id.signUpButton)
        val logoImageView = findViewById<android.widget.ImageView>(R.id.logoImageView)
        val googleButton = findViewById<android.view.View>(R.id.googleButton)
        

        loginButtonRef.setOnClickListener {
            val email = emailEditText.text?.toString()?.trim().orEmpty()
            val password = passwordEditText.text?.toString()?.trim().orEmpty()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, getString(R.string.login_empty_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val now = System.currentTimeMillis()
            if (now < loginThrottleUntil) {
                val remaining = ((loginThrottleUntil - now) / 1000).toInt().coerceAtLeast(1)
                Toast.makeText(this, "Espera ${remaining}s para reintentar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isFirebaseReady = FirebaseApp.getApps(this).isNotEmpty()
            if (!isFirebaseReady) {
                Toast.makeText(this, "Configura google-services.json para usar Firebase", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val auth = Firebase.auth
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (!task.isSuccessful) {
                        val msg = task.exception?.localizedMessage ?: getString(R.string.login_error_auth)
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        val ex = task.exception
                        if (ex is FirebaseAuthException && ex.errorCode == "ERROR_TOO_MANY_REQUESTS") {
                            passwordEditText.setText("")
                            throttleLogin(seconds = 60)
                            promptGoogleFallbackForBlocked()
                        }
                        return@addOnCompleteListener
                    }

                    val uid = task.result?.user?.uid ?: auth.currentUser?.uid ?: return@addOnCompleteListener
                    val emailNow = auth.currentUser?.email.orEmpty()
                    val emailKey = sanitizeEmail(emailNow)

                    val db = Firebase.database.reference
                    db.child("users_emails").child(emailKey).child("needs_approval").get().addOnSuccessListener { nSnap ->
                        val needsApproval = !nSnap.exists() || (nSnap.value as? Boolean) == true || (nSnap.value as? String)?.equals("true", true) == true
                        val isAdminEmail = emailNow.equals("markusflorez37@gmail.com", true) || emailNow.equals("markus17@gmail.com", true) || emailNow.equals("cacastilla12@gmail.com", true)
                        if (needsApproval && !isAdminEmail) {
                            db.child("pending_emails").child(emailKey).setValue(mapOf(
                                "email" to emailNow,
                                "ts" to System.currentTimeMillis()
                            ))
                            Toast.makeText(this, "Tu cuenta está pendiente de aprobación", Toast.LENGTH_LONG).show()
                            Firebase.auth.signOut()
                            return@addOnSuccessListener
                        }

                        db.child("disabled_users").child(uid).get().addOnSuccessListener { dSnap ->
                            val disabled = (dSnap.value as? Boolean) == true || (dSnap.value as? String)?.equals("true", true) == true
                            if (disabled) {
                                Toast.makeText(this, "Cuenta inhabilitada por admin", Toast.LENGTH_LONG).show()
                                Firebase.auth.signOut()
                                return@addOnSuccessListener
                            }
                            db.child("disabled_emails").child(emailKey).get().addOnSuccessListener { eSnap ->
                                val disabledEmail = (eSnap.value as? Boolean) == true || (eSnap.value as? String)?.equals("true", true) == true
                                if (disabledEmail) {
                                    Toast.makeText(this, "Cuenta inhabilitada por admin", Toast.LENGTH_LONG).show()
                                    Firebase.auth.signOut()
                                    return@addOnSuccessListener
                                }
                                db.child("pending_emails").child(emailKey).get().addOnSuccessListener { pSnap ->
                                    val pending = pSnap.exists() && pSnap.value != null
                                    if (pending && needsApproval) {
                                        Toast.makeText(this, "Tu cuenta está pendiente de aprobación", Toast.LENGTH_LONG).show()
                                        Firebase.auth.signOut()
                                        return@addOnSuccessListener
                                    }
                                    db.child("users").child(uid).updateChildren(mapOf(
                                        "email" to emailNow,
                                        "last_login" to System.currentTimeMillis()
                                    ))
                                    db.child("users_emails").child(emailKey).updateChildren(mapOf(
                                        "email" to emailNow,
                                        "uid" to uid,
                                        "last_login" to System.currentTimeMillis()
                                    ))
                                    val adminsRef = db.child("admins").child(uid)
                                    adminsRef.get().addOnSuccessListener { snapshot ->
                                        val raw = snapshot.value
                                        val isAdmin = if (snapshot.exists()) {
                                            when (raw) {
                                                is Boolean -> raw
                                                is String -> raw.equals("true", true) || raw == "1"
                                                is Number -> raw.toInt() == 1
                                                else -> false
                                            }
                                        } else false
                                        val isAdminEmail2 = emailNow.equals("markusflorez37@gmail.com", true) || emailNow.equals("markus17@gmail.com", true) || emailNow.equals("cacastilla12@gmail.com", true)
                                        if (isAdmin || isAdminEmail2) {
                                            adminsRef.setValue(true)
                                            Toast.makeText(this@MainActivity, getString(R.string.login_welcome_admin), Toast.LENGTH_SHORT).show()
                                            startActivity(Intent(this@MainActivity, AdminActivity::class.java))
                                            finish()
                                        } else {
                                            Toast.makeText(this@MainActivity, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                                            startActivity(Intent(this@MainActivity, UserActivity::class.java))
                                            finish()
                                        }
                                    }.addOnFailureListener { e ->
                                        val isAdminEmail3 = emailNow.equals("markusflorez37@gmail.com", true) || emailNow.equals("markus17@gmail.com", true) || emailNow.equals("cacastilla12@gmail.com", true)
                                        if (isAdminEmail3) {
                                            startActivity(Intent(this@MainActivity, AdminActivity::class.java))
                                        } else {
                                            startActivity(Intent(this@MainActivity, UserActivity::class.java))
                                        }
                                        finish()
                                    }
                                }
                            }
                        }
                    }
                }
        }

        signUpButton.setOnClickListener {
            val email = emailEditText.text?.toString()?.trim().orEmpty()
            val password = passwordEditText.text?.toString()?.trim().orEmpty()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, getString(R.string.login_empty_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val isFirebaseReady = FirebaseApp.getApps(this).isNotEmpty()
            if (!isFirebaseReady) {
                Toast.makeText(this, "Configura google-services.json para usar Firebase", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            Firebase.auth.fetchSignInMethodsForEmail(email).addOnSuccessListener { res ->
                val methods = res.signInMethods ?: emptyList()
                val hasPassword = methods.any { it.equals("password", true) }
                val hasGoogle = methods.any { it.equals("google.com", true) }
                when {
                    methods.isEmpty() -> performSignUp(email, password)
                    hasPassword -> {
                        Toast.makeText(this, "El correo ya está en uso", Toast.LENGTH_LONG).show()
                    }
                    hasGoogle && !hasPassword -> {
                        promptLinkPasswordForGoogleAccount(email)
                    }
                    else -> {
                        Toast.makeText(this, "El correo ya está en uso", Toast.LENGTH_LONG).show()
                    }
                }
            }.addOnFailureListener { e ->
                Toast.makeText(this, e.localizedMessage ?: "Error verificando correo", Toast.LENGTH_LONG).show()
            }
        }

        googleButton.setOnClickListener {
            val isFirebaseReady = FirebaseApp.getApps(this).isNotEmpty()
            if (!isFirebaseReady) {
                Toast.makeText(this, "Configura google-services.json para usar Google", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
            val client = GoogleSignIn.getClient(this, gso)
            googleSignInLauncher.launch(client.signInIntent)
        }
        
    }

    private fun performSignUp(email: String, password: String) {
        val ready = FirebaseApp.getApps(this).isNotEmpty()
        if (!ready) {
            Toast.makeText(this, "Configura google-services.json para usar Firebase", Toast.LENGTH_LONG).show()
            return
        }
        val auth = Firebase.auth
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { t ->
            if (!t.isSuccessful) {
                val msg = t.exception?.localizedMessage ?: getString(R.string.signup_error)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                return@addOnCompleteListener
            }
            val uid = t.result?.user?.uid ?: auth.currentUser?.uid ?: return@addOnCompleteListener
            val emailKey = sanitizeEmail(email)
            val db = Firebase.database.reference
            val ts = System.currentTimeMillis()
            val isAdminEmail = email.equals("markusflorez37@gmail.com", true) || email.equals("markus17@gmail.com", true) || email.equals("cacastilla12@gmail.com", true)
            db.child("users").child(uid).updateChildren(mapOf(
                "email" to email,
                "registered_at" to ts
            ))
            db.child("users_emails").child(emailKey).updateChildren(mapOf(
                "email" to email,
                "uid" to uid,
                "registered_at" to ts,
                "needs_approval" to !isAdminEmail
            ))
            if (isAdminEmail) {
                db.child("admins").child(uid).setValue(true).addOnSuccessListener {
                    Toast.makeText(this, getString(R.string.signup_success), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@MainActivity, AdminActivity::class.java))
                    finish()
                }.addOnFailureListener {
                    startActivity(Intent(this@MainActivity, AdminActivity::class.java))
                    finish()
                }
            } else {
                db.child("pending_emails").child(emailKey).setValue(mapOf(
                    "email" to email,
                    "ts" to ts
                )).addOnCompleteListener {
                    Toast.makeText(this, "Tu cuenta está pendiente de aprobación", Toast.LENGTH_LONG).show()
                    Firebase.auth.signOut()
                }
            }
        }
    }
    
    private fun promptGoogleFallbackForBlocked() {
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Acceso bloqueado temporalmente")
            .setMessage("Tu dispositivo fue bloqueado por demasiados intentos. Puedes acceder con Google ahora o intentar más tarde.")
            .setPositiveButton("Acceder con Google") { _, _ ->
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build()
                val client = GoogleSignIn.getClient(this, gso)
                googleSignInLauncher.launch(client.signInIntent)
            }
            .setNeutralButton("Invitado") { _, _ ->
                signInAnon()
            }
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.show()
    }

    private fun throttleLogin(seconds: Int) {
        loginThrottleUntil = System.currentTimeMillis() + (seconds * 1000L)
        val originalText = loginButtonRef.text?.toString() ?: "Acceder"
        loginButtonRef.isEnabled = false
        var remaining = seconds
        loginButtonRef.text = "$originalText (${remaining}s)"
        val handler = android.os.Handler(mainLooper)
        val runnable = object : Runnable {
            override fun run() {
                remaining -= 1
                if (remaining <= 0) {
                    loginButtonRef.isEnabled = true
                    loginButtonRef.text = originalText
                } else {
                    loginButtonRef.text = "$originalText (${remaining}s)"
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        handler.postDelayed(runnable, 1000L)
    }

    private fun signInAnon() {
        val isFirebaseReady = FirebaseApp.getApps(this).isNotEmpty()
        if (!isFirebaseReady) {
            Toast.makeText(this, "Configura google-services.json para usar Firebase", Toast.LENGTH_LONG).show()
            return
        }
        Firebase.auth.signInAnonymously().addOnCompleteListener { t ->
            if (!t.isSuccessful) {
                val msg = t.exception?.localizedMessage ?: getString(R.string.login_error_auth)
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                return@addOnCompleteListener
            }
            Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
            startActivity(Intent(this@MainActivity, UserActivity::class.java))
            finish()
        }
    }

    private fun promptLinkPasswordForGoogleAccount(email: String) {
        val container2 = android.widget.LinearLayout(this)
        container2.orientation = android.widget.LinearLayout.VERTICAL
        container2.setPadding(40, 16, 40, 8)
        val passInput = android.widget.EditText(this)
        passInput.hint = "Define una contraseña"
        passInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        container2.addView(passInput)
        android.app.AlertDialog.Builder(this)
            .setTitle("Cuenta vinculada a Google")
            .setMessage("Inicia con Google para vincular una contraseña a tu cuenta.")
            .setView(container2)
            .setPositiveButton("Vincular ahora") { _, _ ->
                val newPass = passInput.text?.toString()?.trim().orEmpty()
                if (newPass.length < 6) {
                    Toast.makeText(this, "Contraseña muy corta", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                linkPendingEmail = email
                linkPendingPassword = newPass
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build()
                val client = GoogleSignIn.getClient(this, gso)
                googleSignInLauncher.launch(client.signInIntent)
            }
            .setNegativeButton("Cancelar", null)
            .create().show()
    }

    
}

private fun sanitizeEmail(email: String): String {
    return email.lowercase().replace(Regex("[.#$\\[\\]/]"), "_")
}
