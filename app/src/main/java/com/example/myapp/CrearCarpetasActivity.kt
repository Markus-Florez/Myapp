package com.example.myapp

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.FirebaseApp
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

class CrearCarpetasActivity : ComponentActivity() {
    private val selectedFiles = mutableListOf<Pair<Uri, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_crear_carpetas)

        val marca = intent.getStringExtra("marca").orEmpty()
        val modelo = intent.getStringExtra("modelo").orEmpty()
        val anio = intent.getStringExtra("anio").orEmpty()
        val sMarca = sanitizeKey(marca)
        val sModelo = sanitizeKey(modelo)
        val sAnio = sanitizeKey(anio)
        val baseNode = intent.getStringExtra("baseNode") ?: "diag_mcos"

        if (marca.isBlank() || modelo.isBlank() || anio.isBlank()) {
            Toast.makeText(this, "Faltan marca/modelo/año", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val inputNombreCarpeta = findViewById<EditText>(R.id.inputNombreCarpeta)
        val btnAddFile = findViewById<Button>(R.id.btnAddFile)
        val btnGuardar = findViewById<Button>(R.id.btnGuardarFolder)
        val listView = findViewById<ListView>(R.id.filesList)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        listView.adapter = adapter

        val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val name = queryDisplayName(contentResolver, uri)
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                selectedFiles.add(uri to name)
                adapter.add(name)
                adapter.notifyDataSetChanged()
            }
        }

        btnAddFile.setOnClickListener {
            picker.launch(arrayOf(
                "image/*",
                "application/pdf",
                "text/plain",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            ))
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val (uri, name) = selectedFiles[position]
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, contentResolver.getType(uri) ?: "application/octet-stream")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { startActivity(intent) }.onFailure {
                Toast.makeText(this, "No hay app para abrir $name", Toast.LENGTH_SHORT).show()
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val (uri, name) = selectedFiles[position]
            val popup = PopupMenu(this, listView)
            popup.menu.add("Renombrar")
            popup.menu.add("Eliminar")
            popup.setOnMenuItemClickListener {
                when (it.title) {
                    "Renombrar" -> {
                        val input = EditText(this)
                        input.setText(name)
                        val dialog = android.app.AlertDialog.Builder(this)
                            .setTitle("Renombrar archivo")
                            .setView(input)
                            .setPositiveButton("OK") { _, _ ->
                                val newName = input.text.toString().trim()
                                if (newName.isNotEmpty()) {
                                    selectedFiles[position] = uri to newName
                                    adapter.insert(newName, position)
                                    adapter.remove(name)
                                    adapter.notifyDataSetChanged()
                                }
                            }
                            .setNegativeButton("Cancelar", null)
                            .create()
                        dialog.show()
                    }
                    "Eliminar" -> {
                        selectedFiles.removeAt(position)
                        adapter.remove(name)
                        adapter.notifyDataSetChanged()
                    }
                }
                true
            }
            popup.show()
            true
        }

        btnGuardar.setOnClickListener {
            val folderName = inputNombreCarpeta.text?.toString()?.trim().orEmpty()
            if (folderName.isBlank()) {
                Toast.makeText(this, "Ingresa nombre de carpeta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val ts = System.currentTimeMillis()
            val db = Firebase.database.reference
            val dbRef = db
                .child(baseNode)
                .child(sMarca)
                .child(sModelo)
                .child(sAnio)
                .child("folders")
                .child(sanitizeKey(folderName))
            val meta = mapOf(
                "nombre" to folderName,
                "created_at" to ts
            )
            dbRef.child("meta").setValue(meta).addOnFailureListener { e ->
                android.util.Log.e("CrearCarpetas", "Error meta ${e.localizedMessage}")
                Toast.makeText(this, "Error guardando meta: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }

            val storage = Firebase.storage.reference
                .child(baseNode)
                .child(sMarca)
                .child(sModelo)
                .child(sAnio)
                .child("folders")
                .child(sanitizeKey(folderName))
            selectedFiles.forEach { (uri, name) ->
                val safeName = sanitizeKey(name)
                val ref = storage.child(safeName)
                ref.putFile(uri).addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { url ->
                        val type = contentResolver.getType(uri) ?: "application/octet-stream"
                        val fileNode = mapOf(
                            "name" to name,
                            "type" to type,
                            "url" to url.toString(),
                            "uploaded_at" to System.currentTimeMillis()
                        )
                        dbRef.child("files").child(safeName).setValue(fileNode).addOnFailureListener { e ->
                            android.util.Log.e("CrearCarpetas", "Error archivo ${e.localizedMessage}")
                            Toast.makeText(this, "Error guardando archivo: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                }
            }.addOnFailureListener {
                android.util.Log.e("CrearCarpetas", "Error subiendo $name: ${it.localizedMessage}")
                Toast.makeText(this, "Error subiendo $name: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
            }
            Toast.makeText(this, "Carpeta guardada", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sanitizeKey(name: String): String {
        return name.replace(Regex("[.#$\\[\\]/]"), "_")
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String {
        return resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else uri.lastPathSegment ?: "archivo"
        } ?: (uri.lastPathSegment ?: "archivo")
    }
}
