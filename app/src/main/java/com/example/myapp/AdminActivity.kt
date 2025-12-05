package com.example.myapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import android.widget.Toast
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.net.URL
import java.util.concurrent.Executors

class AdminActivity : ComponentActivity() {
    private val pickLogoLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val ref = Firebase.storage.reference.child("branding").child("admin_logo.png")
            ref.putFile(uri).addOnSuccessListener {
                try {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                        findViewById<ImageView>(R.id.popupLogo).setImageBitmap(bmp)
                        findViewById<ImageView>(R.id.btnusario).setImageBitmap(bmp)
                    }
                } catch (_: Exception) {}
                Toast.makeText(this, "Logo actualizado", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                Toast.makeText(this, "Error subiendo logo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_admin)

        val email = Firebase.auth.currentUser?.email.orEmpty()
        val uid = Firebase.auth.currentUser?.uid
        if (uid != null) {
            val low = email.lowercase()
            if (low == "markusflorez37@gmail.com" || low == "markus17@gmail.com" || low == "cacastilla12@gmail.com") {
                Firebase.database.reference.child("admins").child(uid).setValue(true)
            }
        }

        val hamburger = findViewById<ImageButton>(R.id.Menu)
        val sideMenu = findViewById<LinearLayout>(R.id.sideMenu)
        val menuHome = findViewById<LinearLayout>(R.id.menuHome)
        val menuUsers = findViewById<LinearLayout>(R.id.menuUsers)
        val menuDiagElec = findViewById<LinearLayout>(R.id.menuDiagElec)
        val menuDiagMcos = findViewById<LinearLayout>(R.id.menuDiagMcos)
        val diagMenu = findViewById<LinearLayout>(R.id.diagMenu)
        val btnCrearDiagMcos = findViewById<Button>(R.id.btnCrearDiagMcos)
        val btnCrearDiagElec = findViewById<Button>(R.id.btnCrearDiagElec)
        val btnEditarDiagElec = findViewById<Button>(R.id.btnEditarDiagElec)
        val btnEditarDiagMcos = findViewById<Button>(R.id.btnEditarDiagMcos)
        val profileCard = findViewById<CardView>(R.id.profileCard)
        val profilePopup = findViewById<CardView>(R.id.profilePopup)
        val popupEmail = findViewById<TextView>(R.id.popupEmail)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val folderButton = findViewById<ImageButton>(R.id.carpeta)
        val popupLogo = findViewById<ImageView>(R.id.popupLogo)
        val topLogo = findViewById<ImageView>(R.id.btnusario)

        hamburger.setOnClickListener {
            sideMenu.visibility = if (sideMenu.visibility == android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE
        }

        menuHome.setOnClickListener {
            sideMenu.visibility = android.view.View.GONE
            diagMenu.visibility = android.view.View.GONE
        }

        menuUsers.setOnClickListener {
            sideMenu.visibility = android.view.View.GONE
            startActivity(Intent(this, UsuariosActivity::class.java))
        }

        menuDiagElec.setOnClickListener {
            sideMenu.visibility = android.view.View.GONE
            startActivity(Intent(this, VerProcedimientosElecActivity::class.java))
        }

        menuDiagMcos.setOnClickListener {
            sideMenu.visibility = android.view.View.GONE
            startActivity(Intent(this, VerProcedimientosActivity::class.java))
        }

        

        profileCard.setOnClickListener {
            popupEmail.text = email
            profilePopup.visibility = if (profilePopup.visibility == android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE
            if (profilePopup.visibility == android.view.View.VISIBLE) {
                loadLogoInto(popupLogo)
            }
        }

        

        btnLogout.setOnClickListener {
            Firebase.auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        findViewById<Button>(R.id.btnChangeLogo).setOnClickListener {
            pickLogoLauncher.launch("image/*")
        }

        folderButton.setOnClickListener {
            sideMenu.visibility = android.view.View.GONE
            diagMenu.visibility = if (diagMenu.visibility == android.view.View.VISIBLE) android.view.View.GONE else android.view.View.VISIBLE
        }

        btnCrearDiagMcos.setOnClickListener { 
            startActivity(android.content.Intent(this, CrearDiagMcosActivity::class.java))
        }
        btnCrearDiagElec.setOnClickListener { startActivity(Intent(this, CrearDiagElecActivity::class.java)) }
        btnEditarDiagElec.setOnClickListener { startActivity(Intent(this, EditarDiagElecActivity::class.java)) }
        btnEditarDiagMcos.setOnClickListener { startActivity(Intent(this, EditarDiagMcosActivity::class.java)) }

        loadLogoInto(topLogo)
    }

    override fun onStart() {
        super.onStart()
        val user = Firebase.auth.currentUser
        if (user == null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        val ref = Firebase.database.reference.child("admins").child(user.uid)
        ref.get().addOnSuccessListener { snapshot ->
            val raw = snapshot.value
            android.util.Log.d("Admin", "Valor admins/${user.uid}: $raw, exists=${snapshot.exists()}")
            val isAdmin = if (snapshot.exists()) {
                when (raw) {
                    is Boolean -> raw
                    is String -> raw.equals("true", true) || raw == "1"
                    is Number -> raw.toInt() == 1
                    else -> false
                }
            } else false
            val emailNow = Firebase.auth.currentUser?.email
            val isAdminEmail = emailNow?.equals("markusflorez37@gmail.com", true) == true ||
                    emailNow?.equals("markus17@gmail.com", true) == true ||
                    emailNow?.equals("cacastilla12@gmail.com", true) == true
            if (!isAdmin && !isAdminEmail) {
                startActivity(Intent(this, UserActivity::class.java))
                finish()
            }
        }.addOnFailureListener { e ->
            android.util.Log.e("Admin", "Error consultando admin: ${e.localizedMessage}")
            val emailNow = Firebase.auth.currentUser?.email
            val isAdminEmail = emailNow?.equals("markusflorez37@gmail.com", true) == true ||
                    emailNow?.equals("markus17@gmail.com", true) == true ||
                    emailNow?.equals("cacastilla12@gmail.com", true) == true
            if (isAdminEmail) {
                return@addOnFailureListener
            }
            startActivity(Intent(this, UserActivity::class.java))
            finish()
        }

        Firebase.database.reference.child("pending_emails").get().addOnSuccessListener { snap ->
            val emails = snap.children.mapNotNull { c ->
                c.child("email").getValue(String::class.java) ?: c.key
            }.filter { e ->
                val low = e?.lowercase() ?: ""
                low != "markusflorez37@gmail.com" && low != "markus17@gmail.com" && low != "cacastilla12@gmail.com"
            }
            if (emails.isNotEmpty()) {
                val first = emails.first()
                Toast.makeText(this, "Permitir inicio de sesión: $first (y ${emails.size - 1} más)", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadLogoInto(target: ImageView) {
        val ref = Firebase.storage.reference.child("branding").child("admin_logo.png")
        ref.downloadUrl.addOnSuccessListener { u ->
            Executors.newSingleThreadExecutor().execute {
                try {
                    val stream = URL(u.toString()).openStream()
                    val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                    runOnUiThread { target.setImageBitmap(bmp) }
                } catch (_: Exception) {}
            }
        }.addOnFailureListener { _ ->
            target.setImageResource(R.drawable.img)
        }
    }
}
