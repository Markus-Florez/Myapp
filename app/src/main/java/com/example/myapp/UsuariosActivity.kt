package com.example.myapp

import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database

class UsuariosActivity : ComponentActivity() {
    private lateinit var listView: ListView
    private lateinit var search: EditText
    private lateinit var header: TextView
    private var pendingCount: TextView? = null
    private var onlyPending: CheckBox? = null
    private var users: MutableList<UserRow> = mutableListOf()

    data class UserRow(val uid: String, val email: String, val disabled: Boolean, val pending: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_usuarios)

        header = findViewById(R.id.usersHeader)
        listView = findViewById(R.id.usersList)
        search = findViewById(R.id.usersSearch)
        pendingCount = findViewById(R.id.pendingCount)
        onlyPending = findViewById(R.id.onlyPending)

        header.text = "Permitir acceso"
        val ready = com.google.firebase.FirebaseApp.getApps(this).isNotEmpty()
        if (!ready) {
            Toast.makeText(this, "Configura Firebase para ver usuarios", Toast.LENGTH_LONG).show()
            return
        }
        listView.adapter = UsersAdapter(this, users)
        try {
            loadUsers()
        } catch (e: Exception) {
            Toast.makeText(this, "Error inicializando usuarios: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }

        search.addTextChangedListener(object: android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                render(s?.toString().orEmpty())
            }
        })

        onlyPending?.isChecked = true
        onlyPending?.setOnCheckedChangeListener { _, _ ->
            render(lastQuery)
        }

        // Los botones de acción están dentro de cada fila; no usamos menú contextual
    }

    private var lastQuery: String = ""
    private fun filtered(position: Int): UserRow = users.filter { it.email.contains(lastQuery, true) }[position]

    private fun render(q: String) {
        lastQuery = q
        val data = users.filter { it.email.contains(q, true) && ((onlyPending?.isChecked ?: true) == false || it.pending) }
        listView.adapter = UsersAdapter(this, data)
        val count = users.count { it.pending }
        pendingCount?.text = "Pendientes: $count"
    }

    private fun loadUsers() {
        val db = Firebase.database.reference
        db.child("users").get().addOnSuccessListener { usersSnap ->
            runCatching {
                val list = mutableListOf<UserRow>()
                usersSnap.children.forEach { child ->
                    val uid = child.key ?: return@forEach
                    val email = child.child("email").getValue(String::class.java)?.trim() ?: return@forEach
                    if (!isAdminEmail(email)) list.add(UserRow(uid, email, false, false))
                }
                db.child("users_emails").get().addOnSuccessListener { snap ->
                    runCatching {
                        val emailKeys = mutableSetOf<String>()
                        snap.children.forEach { child ->
                            val email = child.child("email").getValue(String::class.java)?.trim() ?: child.key ?: return@forEach
                            val uid = child.child("uid").getValue(String::class.java) ?: ""
                            val pendingAny = child.child("needs_approval").value
                            val pending = when (pendingAny) {
                                is Boolean -> pendingAny
                                is String -> pendingAny.equals("true", true) || pendingAny == "1"
                                is Number -> pendingAny.toInt() == 1
                                else -> false
                            }
                            val k = child.key ?: sanitizeEmail(email)
                            emailKeys.add(k)
                            val existing = list.indexOfFirst { it.email.equals(email, true) }
                            if (existing >= 0) {
                                val prev = list[existing]
                                list[existing] = prev.copy(uid = if (prev.uid.isBlank()) uid else prev.uid, pending = prev.pending || pending)
                            } else if (!isAdminEmail(email)) {
                                list.add(UserRow(uid, email, false, pending))
                            }
                        }
                        db.child("disabled_emails").get().addOnSuccessListener { dSnap ->
                            runCatching {
                                val disabledSet = mutableSetOf<String>()
                                dSnap.children.forEach { c ->
                                    val v = (c.value as? Boolean) == true || (c.value as? String)?.equals("true", true) == true
                                    if (v) disabledSet.add(c.key ?: "")
                                }
                                db.child("pending_emails").get().addOnSuccessListener { pSnap ->
                                    runCatching {
                                        val pendingSet = mutableSetOf<String>()
                                        pSnap.children.forEach { c ->
                                            val key = c.key ?: return@forEach
                                            pendingSet.add(key)
                                        }
                                        val merged = mutableMapOf<String, UserRow>()
                                        list.forEach { u ->
                                            val key = sanitizeEmail(u.email)
                                            val prev = merged[key]
                                            val uidFinal = if ((prev?.uid ?: "").isBlank()) u.uid else prev!!.uid
                                            val emailFinal = prev?.email ?: u.email
                                            val disabledFinal = disabledSet.contains(key)
                                            val pendingFinal = (prev?.pending == true) || u.pending || pendingSet.contains(key)
                                            merged[key] = UserRow(uidFinal, emailFinal, disabledFinal, pendingFinal)
                                        }
                                        pendingSet.forEach { key ->
                                            if (!merged.containsKey(key)) {
                                                val emailFromPending = pSnap.child(key).child("email").getValue(String::class.java)?.trim()
                                                    ?: snap.child(key).child("email").getValue(String::class.java)?.trim() ?: key
                                                if (!isAdminEmail(emailFromPending)) {
                                                    merged[key] = UserRow("", emailFromPending, disabledSet.contains(key), true)
                                                }
                                            }
                                        }
                                        users = merged.values.toMutableList()
                                        render("")
                                        if (users.isEmpty()) Toast.makeText(this, "Sin usuarios", Toast.LENGTH_LONG).show()
                                    }.onFailure { e ->
                                        users = list.toMutableList(); render(""); Toast.makeText(this, "Error leyendo pendientes: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }.addOnFailureListener { e ->
                                    users = list.toMutableList(); render(""); Toast.makeText(this, "Error leyendo pendientes: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }.onFailure { e ->
                                users = list.toMutableList(); render(""); Toast.makeText(this, "Error leyendo deshabilitados: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }.addOnFailureListener { e ->
                            users = list.toMutableList(); render(""); Toast.makeText(this, "Error leyendo deshabilitados: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }.onFailure { e ->
                        users = list.toMutableList(); render(""); Toast.makeText(this, "Error leyendo emails: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }.addOnFailureListener { e ->
                    users = list.toMutableList(); render(""); Toast.makeText(this, "Error leyendo emails: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }.onFailure { e ->
                Toast.makeText(this, "Error procesando usuarios: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                users = mutableListOf(); render("")
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error leyendo usuarios: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setDisabled(uid: String, value: Boolean) {
        // Keep UID-based flag for completeness
        val db = Firebase.database.reference
        if (uid.isNotBlank()) db.child("disabled_users").child(uid).setValue(value)
        // Email-based flag (primary)
        val email = filtered(0).email // placeholder; we'll use clicked item in menu
    }

    private fun setDisabledByEmail(email: String, value: Boolean) {
        val db = Firebase.database.reference
        val key = sanitizeEmail(email)
        val uid = users.firstOrNull { it.email.equals(email, true) }?.uid.orEmpty()
        val updates = hashMapOf<String, Any?>()
        updates["/disabled_emails/$key"] = value
        if (uid.isNotBlank()) {
            updates["/disabled_users/$uid"] = value
        }
        db.updateChildren(updates).addOnSuccessListener {
            loadUsers()
            Toast.makeText(this, if (value) "Usuario inhabilitado" else "Usuario habilitado", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error cambiando estado: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeFromList(uid: String) {
        Firebase.database.reference.child("users").child(uid).removeValue().addOnSuccessListener { loadUsers() }
    }

    private fun removeEmail(email: String) {
        if (isAdminEmail(email)) {
            Toast.makeText(this, "No se puede eliminar un admin", Toast.LENGTH_SHORT).show()
            return
        }
        val key = sanitizeEmail(email)
        val db = Firebase.database.reference
        val uid = users.firstOrNull { it.email.equals(email, true) }?.uid.orEmpty()
        val failures = mutableListOf<String>()
        var pendingOps = 0
        val critical = mutableSetOf(
            "users_emails/$key",
            "pending_emails/$key"
        )
        fun attempt(path: String) {
            pendingOps++
            db.child(path).removeValue()
                .addOnFailureListener { e -> failures.add("Permiso denegado en $path: ${e.localizedMessage}") }
                .addOnCompleteListener {
                    pendingOps--
                    if (pendingOps == 0) {
                        loadUsers()
                        val criticalFailures = failures.filter { f -> critical.any { f.contains(it) } }
                        if (criticalFailures.isEmpty()) {
                            Toast.makeText(this, "Correo eliminado", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, criticalFailures.first(), Toast.LENGTH_LONG).show()
                        }
                    }
                }
        }
        attempt("users_emails/$key")
        attempt("pending_emails/$key")
        attempt("disabled_emails/$key")
        if (uid.isNotBlank()) {
            attempt("disabled_users/$uid")
            attempt("users/$uid")
        }
    }

    private fun approveEmail(email: String) {
        if (email.isBlank()) {
            Toast.makeText(this, "Correo no válido", Toast.LENGTH_SHORT).show()
            return
        }
        val key = sanitizeEmail(email)
        val db = Firebase.database.reference
        val updates = hashMapOf<String, Any?>()
        updates["/users_emails/$key/needs_approval"] = false
        updates["/pending_emails/$key"] = null
        db.updateChildren(updates).addOnSuccessListener {
            Toast.makeText(this, "Permiso concedido", Toast.LENGTH_SHORT).show()
            loadUsers()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error al permitir: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            loadUsers()
        }
    }

    private fun sanitizeEmail(email: String): String {
        return email.lowercase().replace(Regex("[.#$\\[\\]/]"), "_")
    }

    private fun isAdminEmail(email: String): Boolean {
        val e = email.lowercase()
        return e == "markusflorez37@gmail.com" || e == "markus17@gmail.com" || e == "cacastilla12@gmail.com"
    }

    inner class UsersAdapter(private val ctx: android.content.Context, private val data: List<UserRow>): BaseAdapter() {
        override fun getCount(): Int = data.size
        override fun getItem(position: Int): Any = data[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup?): android.view.View {
            val view = convertView ?: layoutInflater.inflate(R.layout.row_user_item, parent, false)
            val emailText = view.findViewById<TextView>(R.id.rowEmail)
            val btnToggle = view.findViewById<Button>(R.id.rowToggle)
            val btnDelete = view.findViewById<Button>(R.id.rowDelete)
            val btnApprove = view.findViewById<Button>(R.id.rowApprove)
            val item = data[position]
            val status = when {
                item.pending -> " (pendiente)"
                item.disabled -> " (inhabilitado)"
                else -> ""
            }
            emailText.text = item.email + status
            btnToggle.text = if (item.disabled) "Habilitar" else "Inhabilitar"
            btnToggle.setOnClickListener { setDisabledByEmail(item.email, !item.disabled) }
            btnDelete.isEnabled = true
            btnDelete.setOnClickListener {
                btnDelete.isEnabled = false
                removeEmail(item.email)
            }
            if (item.pending) {
                btnApprove.visibility = android.view.View.VISIBLE
                btnApprove.text = "Permitir"
                btnApprove.isEnabled = true
                btnApprove.setOnClickListener {
                    btnApprove.isEnabled = false
                    approveEmail(item.email)
                }
            } else {
                btnApprove.visibility = android.view.View.GONE
            }
            return view
        }
    }
}
