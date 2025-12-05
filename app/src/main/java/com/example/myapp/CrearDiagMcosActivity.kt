package com.example.myapp

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.FirebaseApp
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

class CrearDiagMcosActivity : ComponentActivity() {
    private var marcaUri: Uri? = null
    private var modeloUri: Uri? = null
    private var anioUri: Uri? = null
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_crear_diag_mcos)

        val imgMarca = findViewById<ImageView>(R.id.imgMarca)
        val imgModelo = findViewById<ImageView>(R.id.imgModelo)
        val imgAnio = findViewById<ImageView>(R.id.imgAnio)
        val inputMarca = findViewById<EditText>(R.id.inputMarca)
        val inputModelo = findViewById<EditText>(R.id.inputModelo)
        val inputAnio = findViewById<EditText>(R.id.inputAnio)
        val btnGuardar = findViewById<Button>(R.id.btnGuardarDatos)
        val btnCrearCarpetas = findViewById<Button>(R.id.btnCrearCarpetas)
        val suggestionsList = findViewById<android.widget.ListView>(R.id.suggestionsList)
        val suggestionsTitle = findViewById<android.widget.TextView>(R.id.suggestionsTitle)

        fun instantUpload(target: String, fileName: String, uri: Uri?) {
            val marca = inputMarca.text?.toString()?.trim().orEmpty()
            val modelo = inputModelo.text?.toString()?.trim().orEmpty()
            val anio = inputAnio.text?.toString()?.trim().orEmpty()
            if (marca.isBlank() || modelo.isBlank() || anio.isBlank()) {
                Toast.makeText(this, "Completa marca, modelo y año", Toast.LENGTH_SHORT).show()
                return
            }
            if (uri == null) return
            runCatching { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val storageBase = Firebase.storage.reference
                .child("diag_mcos").child(sanitizeKey(marca)).child(sanitizeKey(modelo)).child(sanitizeKey(anio)).child("meta")
            val ref = storageBase.child(fileName)
            ref.putFile(uri).addOnSuccessListener {
                ref.downloadUrl.addOnSuccessListener { u ->
                    Firebase.database.reference
                        .child("diag_mcos").child(sanitizeKey(marca)).child(sanitizeKey(modelo)).child(sanitizeKey(anio))
                        .child(target).setValue(u.toString()).addOnSuccessListener { Toast.makeText(this, "Imagen guardada", Toast.LENGTH_SHORT).show() }
                }
            }
        }

        val pickMarca = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                marcaUri = uri
                imgMarca.setImageURI(uri)
                instantUpload("imgMarca", "imgMarca.jpg", marcaUri)
            }
        }
        val pickModelo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                modeloUri = uri
                imgModelo.setImageURI(uri)
                instantUpload("imgModelo", "imgModelo.jpg", modeloUri)
            }
        }
        val pickAnio = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                anioUri = uri
                imgAnio.setImageURI(uri)
                instantUpload("imgAnio", "imgAnio.jpg", anioUri)
            }
        }

        imgMarca.setOnClickListener { pickMarca.launch(arrayOf("image/*")) }
        imgModelo.setOnClickListener { pickModelo.launch(arrayOf("image/*")) }
        imgAnio.setOnClickListener { pickAnio.launch(arrayOf("image/*")) }

        inputMarca.setOnClickListener { showPickMarcaInline(inputMarca, imgMarca, suggestionsTitle, suggestionsList) }
        inputModelo.setOnClickListener { showPickModeloInline(inputMarca, inputModelo, imgModelo, suggestionsTitle, suggestionsList) }
        inputAnio.setOnClickListener { showPickAnioInline(inputMarca, inputModelo, inputAnio, imgAnio, suggestionsTitle, suggestionsList) }

        btnGuardar.setOnClickListener {
            val marca = inputMarca.text?.toString()?.trim().orEmpty()
            val modelo = inputModelo.text?.toString()?.trim().orEmpty()
            val anio = inputAnio.text?.toString()?.trim().orEmpty()
            if (marca.isBlank() || modelo.isBlank() || anio.isBlank()) {
                Toast.makeText(this, "Completa marca, modelo y año", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val storageBase = Firebase.storage.reference
                .child("diag_mcos").child(sanitizeKey(marca)).child(sanitizeKey(modelo)).child(sanitizeKey(anio)).child("meta")
            fun upload(uri: Uri?, name: String, onUrl: (String) -> Unit, onDone: () -> Unit) {
                if (uri == null) { onUrl(""); onDone(); return }
                runCatching { contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                val ref = storageBase.child(name)
                ref.putFile(uri).addOnSuccessListener {
                    ref.downloadUrl.addOnSuccessListener { u -> onUrl(u.toString()); onDone() }
                }.addOnFailureListener { e ->
                    android.util.Log.e("CrearDiagMcos", "Error subiendo $name: ${e.localizedMessage}")
                    onUrl(""); onDone()
                }
            }
            var urlMarca = ""
            var urlModelo = ""
            var urlAnio = ""
            var pending = 3
            fun stepDone() { pending -= 1; if (pending == 0) {
                val data = mapOf(
                    "marca" to marca,
                    "modelo" to modelo,
                    "anio" to anio,
                    "imgMarca" to urlMarca,
                    "imgModelo" to urlModelo,
                    "imgAnio" to urlAnio
                )
                val ref = Firebase.database.reference
                    .child("diag_mcos").child(sanitizeKey(marca)).child(sanitizeKey(modelo)).child(sanitizeKey(anio))
                ref.setValue(data).addOnSuccessListener {
                    Toast.makeText(this, "Datos guardados", Toast.LENGTH_SHORT).show()
                }.addOnFailureListener { e ->
                    android.util.Log.e("CrearDiagMcos", "Error guardando: ${e.localizedMessage}")
                    Toast.makeText(this, "Error guardando: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }}
            upload(marcaUri, "imgMarca.jpg", { urlMarca = it }, ::stepDone)
            upload(modeloUri, "imgModelo.jpg", { urlModelo = it }, ::stepDone)
            upload(anioUri, "imgAnio.jpg", { urlAnio = it }, ::stepDone)
        }

        btnCrearCarpetas.setOnClickListener {
            val marca = inputMarca.text?.toString()?.trim().orEmpty()
            val modelo = inputModelo.text?.toString()?.trim().orEmpty()
            val anio = inputAnio.text?.toString()?.trim().orEmpty()
            if (marca.isBlank() || modelo.isBlank() || anio.isBlank()) {
                Toast.makeText(this, "Completa marca, modelo y año", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = android.content.Intent(this, CrearCarpetasActivity::class.java)
            intent.putExtra("marca", marca)
            intent.putExtra("modelo", modelo)
            intent.putExtra("anio", anio)
            startActivity(intent)
        }
    }

    private fun sanitizeKey(name: String): String {
        return name.replace(Regex("[.#$\\[\\]/]"), "_")
    }

    private fun showPickMarcaInline(inputMarca: EditText, imgMarca: ImageView, title: android.widget.TextView, list: android.widget.ListView) {
        title.text = "Cargando..."
        val ref = Firebase.database.reference.child("diag_mcos")
        ref.get().addOnSuccessListener { snap ->
            val marcas = mutableListOf<String>()
            val urls = mutableListOf<String?>()
            snap.children.forEach { marcaNode ->
                val m = marcaNode.key ?: return@forEach
                marcas.add(m)
                urls.add(findFirstUrl(marcaNode, arrayOf("imgMarca")))
            }
            if (marcas.isEmpty()) { Toast.makeText(this, "No hay marcas creadas", Toast.LENGTH_SHORT).show(); return@addOnSuccessListener }
            title.text = "Marcas"
            list.adapter = makeSuggestionsAdapter(marcas, urls)
            list.visibility = android.view.View.VISIBLE
            list.setOnItemClickListener { _, _, which, _ ->
                inputMarca.setText(marcas[which])
                val u = urls[which]
                if (!u.isNullOrBlank()) loadBitmapInto(imgMarca, u)
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error cargando marcas: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPickModeloInline(inputMarca: EditText, inputModelo: EditText, imgModelo: ImageView, title: android.widget.TextView, list: android.widget.ListView) {
        val marca = inputMarca.text?.toString()?.trim().orEmpty()
        title.text = "Cargando..."
        if (marca.isBlank()) {
            val refAll = Firebase.database.reference.child("diag_mcos")
            refAll.get().addOnSuccessListener { snap ->
                val display = mutableListOf<String>()
                val pairs = mutableListOf<Pair<String,String>>()
                val urls = mutableListOf<String?>()
                snap.children.forEach { marcaNode ->
                    val m = marcaNode.key ?: return@forEach
                    marcaNode.children.forEach { modeloNode ->
                        val mod = modeloNode.key ?: return@forEach
                        display.add("$m › $mod")
                        pairs.add(m to mod)
                        urls.add(findFirstUrl(modeloNode, arrayOf("imgModelo")))
                    }
                }
                if (display.isEmpty()) { Toast.makeText(this, "No hay modelos", Toast.LENGTH_SHORT).show(); return@addOnSuccessListener }
                title.text = "Modelos"
                list.adapter = makeSuggestionsAdapter(display, urls, android.R.drawable.ic_menu_sort_by_size)
                list.visibility = android.view.View.VISIBLE
                list.setOnItemClickListener { _, _, which, _ ->
                    val (m, mod) = pairs[which]
                    inputMarca.setText(m)
                    inputModelo.setText(mod)
                    val u = urls[which]
                    if (!u.isNullOrBlank()) loadBitmapInto(imgModelo, u)
                }
            }.addOnFailureListener { e -> Toast.makeText(this, "Error cargando modelos: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
            return
        }
        val ref = Firebase.database.reference.child("diag_mcos").child(sanitizeKey(marca))
        ref.get().addOnSuccessListener { snap ->
            val modelos = mutableListOf<String>()
            val urls = mutableListOf<String?>()
            snap.children.forEach { modeloNode ->
                val m = modeloNode.key ?: return@forEach
                modelos.add(m)
                urls.add(findFirstUrl(modeloNode, arrayOf("imgModelo")))
            }
            if (modelos.isEmpty()) { Toast.makeText(this, "No hay modelos", Toast.LENGTH_SHORT).show(); return@addOnSuccessListener }
            title.text = "Modelos"
            list.adapter = makeSuggestionsAdapter(modelos, urls, android.R.drawable.ic_menu_sort_by_size)
            list.visibility = android.view.View.VISIBLE
            list.setOnItemClickListener { _, _, which, _ ->
                inputModelo.setText(modelos[which])
                val u = urls[which]
                if (!u.isNullOrBlank()) loadBitmapInto(imgModelo, u)
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error cargando modelos: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showPickAnioInline(inputMarca: EditText, inputModelo: EditText, inputAnio: EditText, imgAnio: ImageView, title: android.widget.TextView, list: android.widget.ListView) {
        val marca = inputMarca.text?.toString()?.trim().orEmpty()
        val modelo = inputModelo.text?.toString()?.trim().orEmpty()
        if (marca.isBlank() || modelo.isBlank()) { Toast.makeText(this, "Selecciona marca y modelo", Toast.LENGTH_SHORT).show(); return }
        title.text = "Cargando..."
        val ref = Firebase.database.reference.child("diag_mcos").child(sanitizeKey(marca)).child(sanitizeKey(modelo))
        ref.get().addOnSuccessListener { snap ->
            val anios = mutableListOf<String>()
            val urls = mutableListOf<String?>()
            snap.children.forEach { anioNode ->
                val a = anioNode.key ?: return@forEach
                anios.add(a)
                urls.add(anioNode.child("imgAnio").getValue(String::class.java))
            }
            if (anios.isEmpty()) { Toast.makeText(this, "No hay años", Toast.LENGTH_SHORT).show(); return@addOnSuccessListener }
            title.text = "Años"
            list.adapter = makeSuggestionsAdapter(anios, urls, android.R.drawable.ic_menu_sort_by_size)
            list.visibility = android.view.View.VISIBLE
            list.setOnItemClickListener { _, _, which, _ ->
                inputAnio.setText(anios[which])
                val u = urls[which]
                if (!u.isNullOrBlank()) loadBitmapInto(imgAnio, u)
            }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error cargando años: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun findFirstUrl(node: com.google.firebase.database.DataSnapshot, keys: Array<String>): String? {
        keys.forEach { key ->
            node.children.forEach { child ->
                val v = child.child(key).getValue(String::class.java)
                if (!v.isNullOrBlank()) return v
                child.children.forEach { gchild ->
                    val v2 = gchild.child(key).getValue(String::class.java)
                    if (!v2.isNullOrBlank()) return v2
                }
            }
        }
        return null
    }

    private fun loadBitmapInto(img: ImageView, url: String) {
        executor.execute {
            try {
                val stream = java.net.URL(url).openStream()
                val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                runOnUiThread { img.setImageBitmap(bmp) }
            } catch (_: Exception) {}
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun makeSuggestionsAdapter(items: List<String>, urls: List<String?>, fixedIconResId: Int? = null): android.widget.ListAdapter {
        return object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val row = android.widget.LinearLayout(context)
                row.orientation = android.widget.LinearLayout.HORIZONTAL
                row.setPadding(dp(16), dp(8), dp(16), dp(8))

                val iv = android.widget.ImageView(context)
                val ivParams = android.widget.LinearLayout.LayoutParams(dp(40), dp(40))
                ivParams.rightMargin = dp(12)
                iv.layoutParams = ivParams
                iv.setImageResource(fixedIconResId ?: android.R.drawable.ic_menu_gallery)

                val tv = android.widget.TextView(context)
                tv.text = items[position]
                tv.setTextColor(android.graphics.Color.WHITE)
                tv.textSize = 16f
                tv.layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)

                row.addView(iv)
                row.addView(tv)

                if (fixedIconResId == null) {
                    val u = urls.getOrNull(position)
                    if (!u.isNullOrBlank()) {
                        loadBitmapInto(iv, u)
                    }
                }
                return row
            }
        }
    }
}
