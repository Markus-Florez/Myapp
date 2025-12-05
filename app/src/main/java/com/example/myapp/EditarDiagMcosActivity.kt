package com.example.myapp

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

class EditarDiagMcosActivity : ComponentActivity() {
    private lateinit var listView: ListView
    private lateinit var search: EditText
    private lateinit var header: TextView
    private lateinit var actionButton: Button
    private var currentItems: MutableList<Item> = mutableListOf()
    private var step = 1
    private var selectedMarca: String? = null
    private var selectedModelo: String? = null
    private var selectedAnio: String? = null
    private var selectedFolder: String? = null
    private var metaTarget: String? = null
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val marca = selectedMarca ?: return@registerForActivityResult
        val modelo = selectedModelo ?: return@registerForActivityResult
        val anio = selectedAnio ?: return@registerForActivityResult
        val folder = selectedFolder ?: return@registerForActivityResult
        if (uri != null) addFileToFolder(marca, modelo, anio, folder, uri)
    }

    private val pickMetaImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val marca = selectedMarca ?: return@registerForActivityResult
        val modelo = selectedModelo ?: return@registerForActivityResult
        val anio = selectedAnio ?: return@registerForActivityResult
        val target = metaTarget ?: return@registerForActivityResult
        if (uri != null) changeMetaImage(marca, modelo, anio, target, uri)
    }

    data class Item(val title: String, val imageUrl: String? = null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_editar_diag_mcos)

        header = findViewById(R.id.editHeader)
        listView = findViewById(R.id.editList)
        search = findViewById(R.id.editSearch)
        actionButton = findViewById(R.id.editActionButton)

        loadMarcas()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (step) {
                    5 -> { step = 4; val m = selectedMarca; val mo = selectedModelo; val a = selectedAnio; selectedFolder = null; if (m != null && mo != null && a != null) loadFolders(m, mo, a) else { step = 1; loadMarcas() } }
                    4 -> { step = 3; val m = selectedMarca; val mo = selectedModelo; selectedAnio = null; selectedFolder = null; if (m != null && mo != null) loadAnios(m, mo) else { step = 1; loadMarcas() } }
                    3 -> { step = 2; val m = selectedMarca; selectedModelo = null; selectedAnio = null; selectedFolder = null; if (m != null) loadModelos(m) else { step = 1; loadMarcas() } }
                    2 -> { step = 1; selectedMarca = null; selectedModelo = null; selectedAnio = null; selectedFolder = null; loadMarcas() }
                    else -> finish()
                }
            }
        })

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = currentItems[position]
            when (step) {
                1 -> { selectedMarca = item.title; step = 2; loadModelos(selectedMarca!!) }
                2 -> { selectedModelo = item.title; step = 3; loadAnios(selectedMarca!!, selectedModelo!!) }
                3 -> { selectedAnio = item.title; step = 4; loadFolders(selectedMarca!!, selectedModelo!!, selectedAnio!!) }
                4 -> { selectedFolder = item.title; step = 5; loadFiles(selectedMarca!!, selectedModelo!!, selectedAnio!!, selectedFolder!!) }
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val item = currentItems[position]
            when (step) {
                1 -> { showMarcaOptions(item.title); true }
                2 -> { showModeloOptions(item.title); true }
                3 -> { showAnioOptions(item.title); true }
                4 -> { showFolderOptions(item.title); true }
                5 -> { showFileOptions(item.title); true }
                else -> false
            }
        }

        search.addTextChangedListener(object: android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s?.toString().orEmpty())
            }
        })

        actionButton.setOnClickListener {
            when (step) {
                4 -> promptAddFolder()
                5 -> pickFile.launch(arrayOf(
                    "image/*",
                    "application/pdf",
                    "text/plain",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                ))
            }
        }
    }

    private fun filterList(q: String) {
        val base = when(step) { 1 -> "Marca"; 2 -> "Modelo"; 3 -> "Año"; 4 -> "Carpeta"; else -> "Archivo" }
        header.text = "${base}s"
        val items = currentItems.filter { it.title.contains(q, true) }
        val fixedIcon = if (step >= 4) android.R.drawable.ic_menu_sort_by_size else null
        listView.adapter = makeAdapter(items, fixedIcon)
        updateActionButton()
    }

    private fun updateActionButton() {
        actionButton.text = when(step) { 4 -> "Agregar carpeta"; 5 -> "Agregar archivo"; else -> "" }
        actionButton.visibility = if (step in 4..5) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun loadMarcas() {
        header.text = "Marcas"
        val ref = Firebase.database.reference.child("diag_mcos")
        ref.get().addOnSuccessListener { snap ->
            val items = mutableListOf<Item>()
            snap.children.forEach { marcaNode ->
                val title = marcaNode.key ?: ""
                val url = findFirstUrl(marcaNode, arrayOf("imgMarca"))
                if (title.isNotBlank()) items.add(Item(title, url))
            }
            currentItems = items
            listView.adapter = makeAdapter(items, null)
            updateActionButton()
        }.addOnFailureListener { Toast.makeText(this, "Error cargando marcas", Toast.LENGTH_SHORT).show() }
    }

    private fun loadModelos(marca: String) {
        header.text = "Modelos"
        val ref = Firebase.database.reference.child("diag_mcos").child(marca)
        ref.get().addOnSuccessListener { snap ->
            val items = mutableListOf<Item>()
            snap.children.forEach { modeloNode ->
                val title = modeloNode.key ?: ""
                val url = findFirstUrl(modeloNode, arrayOf("imgModelo"))
                if (title.isNotBlank()) items.add(Item(title, url))
            }
            currentItems = items
            listView.adapter = makeAdapter(items, null)
            updateActionButton()
        }.addOnFailureListener { Toast.makeText(this, "Error cargando modelos", Toast.LENGTH_SHORT).show() }
    }

    private fun loadAnios(marca: String, modelo: String) {
        header.text = "Años"
        val ref = Firebase.database.reference.child("diag_mcos").child(marca).child(modelo)
        ref.get().addOnSuccessListener { snap ->
            val items = mutableListOf<Item>()
            snap.children.forEach { anioNode ->
                val title = anioNode.key ?: ""
                val url = anioNode.child("imgAnio").getValue(String::class.java)
                if (title.isNotBlank()) items.add(Item(title, url))
            }
            currentItems = items
            listView.adapter = makeAdapter(items, null)
            updateActionButton()
        }.addOnFailureListener { Toast.makeText(this, "Error cargando años", Toast.LENGTH_SHORT).show() }
    }

    private fun loadFolders(marca: String, modelo: String, anio: String) {
        header.text = "Carpetas"
        val ref = Firebase.database.reference.child("diag_mcos").child(marca).child(modelo).child(anio).child("folders")
        ref.get().addOnSuccessListener { snap ->
            val items = mutableListOf<Item>()
            snap.children.forEach { folderNode ->
                val metaName = folderNode.child("meta").child("nombre").getValue(String::class.java) ?: folderNode.key ?: ""
                if (metaName.isNotBlank()) items.add(Item(metaName, null))
            }
            currentItems = items
            listView.adapter = makeAdapter(items, android.R.drawable.ic_menu_sort_by_size)
            updateActionButton()
        }.addOnFailureListener { Toast.makeText(this, "Error cargando carpetas", Toast.LENGTH_SHORT).show() }
    }

    private fun loadFiles(marca: String, modelo: String, anio: String, folder: String) {
        header.text = "Archivos"
        val ref = Firebase.database.reference
            .child("diag_mcos").child(marca).child(modelo).child(anio)
            .child("folders").child(sanitizeKey(folder)).child("files")
        ref.get().addOnSuccessListener { snap ->
            val items = mutableListOf<Item>()
            snap.children.forEach { fileNode ->
                val name = fileNode.child("name").getValue(String::class.java) ?: fileNode.key ?: ""
                if (name.isNotBlank()) items.add(Item(name, null))
            }
            currentItems = items
            listView.adapter = makeAdapter(items, android.R.drawable.ic_menu_agenda)
            updateActionButton()
        }.addOnFailureListener { Toast.makeText(this, "Error cargando archivos", Toast.LENGTH_SHORT).show() }
    }

    private fun promptAddFolder() {
        val marca = selectedMarca ?: return
        val modelo = selectedModelo ?: return
        val anio = selectedAnio ?: return
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Nombre de carpeta")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) createFolder(marca, modelo, anio, name)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun createFolder(marca: String, modelo: String, anio: String, folder: String) {
        val dbRef = Firebase.database.reference
            .child("diag_mcos").child(marca).child(modelo).child(anio)
            .child("folders").child(sanitizeKey(folder))
        val meta = mapOf("nombre" to folder, "created_at" to System.currentTimeMillis())
        dbRef.child("meta").setValue(meta).addOnSuccessListener {
            loadFolders(marca, modelo, anio)
            Toast.makeText(this, "Carpeta creada", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { Toast.makeText(this, "Error creando carpeta", Toast.LENGTH_SHORT).show() }
    }

    private fun showMarcaOptions(marca: String) {
        val popup = PopupMenu(this, listView)
        popup.menu.add("Eliminar marca")
        popup.setOnMenuItemClickListener {
            deleteMarca(marca)
            true
        }
        popup.show()
    }

    private fun showModeloOptions(modelo: String) {
        val marca = selectedMarca ?: return
        val popup = PopupMenu(this, listView)
        popup.menu.add("Eliminar modelo")
        popup.setOnMenuItemClickListener {
            deleteModelo(marca, modelo)
            true
        }
        popup.show()
    }

    private fun showAnioOptions(anio: String) {
        val marca = selectedMarca ?: return
        val modelo = selectedModelo ?: return
        val popup = PopupMenu(this, listView)
        popup.menu.add("Eliminar año")
        popup.menu.add("Cambiar imagen marca")
        popup.menu.add("Cambiar imagen modelo")
        popup.menu.add("Cambiar imagen año")
        popup.setOnMenuItemClickListener {
            when (it.title) {
                "Eliminar año" -> { deleteAnio(marca, modelo, anio); true }
                "Cambiar imagen marca" -> { metaTarget = "imgMarca"; pickMetaImage.launch(arrayOf("image/*")); true }
                "Cambiar imagen modelo" -> { metaTarget = "imgModelo"; pickMetaImage.launch(arrayOf("image/*")); true }
                "Cambiar imagen año" -> { metaTarget = "imgAnio"; pickMetaImage.launch(arrayOf("image/*")); true }
                else -> false
            }
        }
        selectedAnio = anio
        popup.show()
    }

    private fun changeMetaImage(marca: String, modelo: String, anio: String, target: String, uri: Uri) {
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val storage = Firebase.storage.reference
            .child("diag_mcos").child(marca).child(modelo).child(anio).child("meta")
        val name = when (target) { "imgMarca" -> "imgMarca.jpg"; "imgModelo" -> "imgModelo.jpg"; else -> "imgAnio.jpg" }
        val ref = storage.child(name)
        ref.putFile(uri).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { url ->
                Firebase.database.reference
                    .child("diag_mcos").child(marca).child(modelo).child(anio)
                    .child(target).setValue(url.toString()).addOnSuccessListener { Toast.makeText(this, "Imagen actualizada", Toast.LENGTH_SHORT).show() }
            }
        }.addOnFailureListener { Toast.makeText(this, "Error subiendo imagen", Toast.LENGTH_SHORT).show() }
    }

    private fun deleteMarca(marca: String) {
        val baseStorage = Firebase.storage.reference.child("diag_mcos").child(marca)
        deleteStorageTree(baseStorage) {
            Firebase.database.reference.child("diag_mcos").child(marca).removeValue().addOnSuccessListener { loadMarcas(); Toast.makeText(this, "Marca eliminada", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun deleteModelo(marca: String, modelo: String) {
        val baseStorage = Firebase.storage.reference.child("diag_mcos").child(marca).child(modelo)
        deleteStorageTree(baseStorage) {
            Firebase.database.reference.child("diag_mcos").child(marca).child(modelo).removeValue().addOnSuccessListener { loadModelos(marca); Toast.makeText(this, "Modelo eliminado", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun deleteAnio(marca: String, modelo: String, anio: String) {
        val baseStorage = Firebase.storage.reference.child("diag_mcos").child(marca).child(modelo).child(anio)
        deleteStorageTree(baseStorage) {
            Firebase.database.reference.child("diag_mcos").child(marca).child(modelo).child(anio).removeValue().addOnSuccessListener { loadAnios(marca, modelo); Toast.makeText(this, "Año eliminado", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun deleteStorageTree(ref: com.google.firebase.storage.StorageReference, onDone: () -> Unit) {
        ref.listAll().addOnSuccessListener { list ->
            val tasks = mutableListOf<com.google.android.gms.tasks.Task<Void>>()
            list.items.forEach { tasks.add(it.delete()) }
            var pending = list.prefixes.size
            if (pending == 0) {
                com.google.android.gms.tasks.Tasks.whenAllComplete(tasks).addOnSuccessListener { onDone() }
            } else {
                list.prefixes.forEach { child ->
                    deleteStorageTree(child) {
                        pending -= 1
                        if (pending == 0) {
                            com.google.android.gms.tasks.Tasks.whenAllComplete(tasks).addOnSuccessListener { onDone() }
                        }
                    }
                }
            }
        }.addOnFailureListener { onDone() }
    }

    private fun addFileToFolder(marca: String, modelo: String, anio: String, folder: String, uri: Uri) {
        val storage = Firebase.storage.reference
            .child("diag_mcos").child(marca).child(modelo).child(anio)
            .child("folders").child(sanitizeKey(folder))
        val name = queryDisplayName(contentResolver, uri)
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
                Firebase.database.reference
                    .child("diag_mcos").child(marca).child(modelo).child(anio)
                    .child("folders").child(sanitizeKey(folder)).child("files").child(safeName)
                    .setValue(fileNode).addOnSuccessListener {
                        loadFiles(marca, modelo, anio, folder)
                        Toast.makeText(this, "Archivo agregado", Toast.LENGTH_SHORT).show()
                    }
            }
        }.addOnFailureListener { Toast.makeText(this, "Error subiendo archivo", Toast.LENGTH_SHORT).show() }
    }

    private fun showFolderOptions(folder: String) {
        val marca = selectedMarca ?: return
        val modelo = selectedModelo ?: return
        val anio = selectedAnio ?: return
        val popup = PopupMenu(this, listView)
        popup.menu.add("Eliminar carpeta")
        popup.setOnMenuItemClickListener {
            when (it.title) {
                "Eliminar carpeta" -> deleteFolder(marca, modelo, anio, folder)
            }
            true
        }
        popup.show()
    }

    private fun deleteFolder(marca: String, modelo: String, anio: String, folder: String) {
        val baseStorage = Firebase.storage.reference
            .child("diag_mcos").child(marca).child(modelo).child(anio)
            .child("folders").child(sanitizeKey(folder))
        baseStorage.listAll().addOnSuccessListener { listResult ->
            val ops = mutableListOf<com.google.android.gms.tasks.Task<Void>>()
            listResult.items.forEach { ops.add(it.delete()) }
            com.google.android.gms.tasks.Tasks.whenAllComplete(ops).addOnSuccessListener {
                Firebase.database.reference
                    .child("diag_mcos").child(marca).child(modelo).child(anio)
                    .child("folders").child(sanitizeKey(folder)).removeValue().addOnSuccessListener {
                        loadFolders(marca, modelo, anio)
                        Toast.makeText(this, "Carpeta eliminada", Toast.LENGTH_SHORT).show()
                    }
            }
        }.addOnFailureListener { Toast.makeText(this, "Error eliminando archivos", Toast.LENGTH_SHORT).show() }
    }

    private fun showFileOptions(fileName: String) {
        val marca = selectedMarca ?: return
        val modelo = selectedModelo ?: return
        val anio = selectedAnio ?: return
        val folder = selectedFolder ?: return
        val popup = PopupMenu(this, listView)
        popup.menu.add("Eliminar archivo")
        popup.setOnMenuItemClickListener {
            when (it.title) {
                "Eliminar archivo" -> deleteFile(marca, modelo, anio, folder, fileName)
            }
            true
        }
        popup.show()
    }

    private fun deleteFile(marca: String, modelo: String, anio: String, folder: String, fileName: String) {
        val safeName = sanitizeKey(fileName)
        val ref = Firebase.storage.reference
            .child("diag_mcos").child(marca).child(modelo).child(anio)
            .child("folders").child(sanitizeKey(folder)).child(safeName)
        ref.delete().addOnSuccessListener {
            Firebase.database.reference
                .child("diag_mcos").child(marca).child(modelo).child(anio)
                .child("folders").child(sanitizeKey(folder)).child("files").child(safeName)
                .removeValue().addOnSuccessListener {
                    loadFiles(marca, modelo, anio, folder)
                    Toast.makeText(this, "Archivo eliminado", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener { Toast.makeText(this, "Error eliminando archivo", Toast.LENGTH_SHORT).show() }
    }

    private fun sanitizeKey(name: String): String {
        return name.replace(Regex("[.#$\\[\\]/]"), "_")
    }

    private fun findFirstUrl(node: DataSnapshot, keys: Array<String>): String? {
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun loadBitmapInto(img: ImageView, url: String) {
        executor.execute {
            try {
                val stream = java.net.URL(url).openStream()
                val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                runOnUiThread { img.setImageBitmap(bmp) }
            } catch (_: Exception) {}
        }
    }

    private fun makeAdapter(items: List<Item>, fixedIconResId: Int?): ListAdapter {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items.map { it.title }) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val row = LinearLayout(context)
                row.orientation = LinearLayout.HORIZONTAL
                row.setPadding(dp(16), dp(8), dp(16), dp(8))
                val iv = ImageView(context)
                val ivParams = LinearLayout.LayoutParams(dp(40), dp(40))
                ivParams.rightMargin = dp(12)
                iv.layoutParams = ivParams
                iv.setImageResource(fixedIconResId ?: android.R.drawable.ic_menu_gallery)
                val tv = TextView(context)
                tv.text = items[position].title
                tv.setTextColor(android.graphics.Color.WHITE)
                tv.textSize = 16f
                row.addView(iv)
                row.addView(tv)
                val u = items[position].imageUrl
                if (fixedIconResId == null && !u.isNullOrBlank()) loadBitmapInto(iv, u)
                return row
            }
        }
    }

    private fun queryDisplayName(resolver: android.content.ContentResolver, uri: Uri): String {
        return resolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else uri.lastPathSegment ?: "archivo"
        } ?: (uri.lastPathSegment ?: "archivo")
    }
}
