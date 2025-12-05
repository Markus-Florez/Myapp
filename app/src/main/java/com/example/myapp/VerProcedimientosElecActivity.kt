package com.example.myapp

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.view.WindowManager
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database
import com.google.firebase.auth.ktx.auth
import java.net.URL
import java.util.concurrent.Executors

class VerProcedimientosElecActivity : ComponentActivity() {
    private val executor = Executors.newFixedThreadPool(3)
    private lateinit var listView: ListView
    private lateinit var search: EditText
    private lateinit var header: TextView
    private var currentItems: MutableList<Item> = mutableListOf()
    private var step = 1
    private var selectedMarca: String? = null
    private var selectedModelo: String? = null
    private var selectedAnio: String? = null
    private var selectedFolder: String? = null

    private val imgCache = object {
        private val maxEntries = 64
        private val lru = object: java.util.LinkedHashMap<String, Bitmap>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > maxEntries
        }
        @Synchronized fun get(url: String): Bitmap? = lru[url]
        @Synchronized fun put(url: String, bmp: Bitmap) { lru[url] = bmp }
    }

    private fun prefetch(urls: List<String?>) {
        urls.filterNotNull().filter { it.startsWith("http") || it.startsWith("https") }.forEach { url ->
            if (imgCache.get(url) == null) {
                executor.execute {
                    runCatching {
                        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection)
                        conn.connectTimeout = 1500
                        conn.readTimeout = 1500
                        conn.inputStream.use {
                            val bmp = BitmapFactory.decodeStream(it)
                            if (bmp != null) imgCache.put(url, bmp)
                        }
                        conn.disconnect()
                    }
                }
            }
        }
    }

    data class Item(val title: String, val imageUrl: String?, val extra: String? = null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_ver_procedimientos)
        applySecureFlagForUser()

        header = findViewById(R.id.procHeader)
        listView = findViewById(R.id.procList)
        search = findViewById(R.id.procSearch)

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
                4 -> { val folder = item.title; step = 5; loadFiles(selectedMarca!!, selectedModelo!!, selectedAnio!!, folder) }
                5 -> { openFile(item) }
            }
        }

        search.addTextChangedListener(object: android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s?.toString().orEmpty())
            }
        })
    }

    private fun filterList(q: String) {
        val base = when(step) { 1 -> "Marca"; 2 -> "Modelo"; 3 -> "Año"; 4 -> "Carpeta"; else -> "Archivo" }
        header.text = "${base}s"
        val adapter = ProcAdapter(this, currentItems.filter { it.title.contains(q, true) })
        listView.adapter = adapter
    }

    private fun loadMarcas() {
        header.text = "Marcas"
        val ref = Firebase.database.reference.child("diag_elec")
        ref.get().addOnSuccessListener { snap ->
            val items = mutableListOf<Item>()
            snap.children.forEach { marcaNode ->
                val url = findFirstUrl(marcaNode, arrayOf("imgMarca", "imgModelo", "imgAnio"))
                val title = marcaNode.key ?: ""
                if (title.isNotBlank()) items.add(Item(title, url))
            }
            currentItems = items
            listView.adapter = ProcAdapter(this, items)
            prefetch(items.map { it.imageUrl })
            if (items.isEmpty()) Toast.makeText(this, "Sin marcas", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { Toast.makeText(this, "Error cargando marcas", Toast.LENGTH_SHORT).show() }
    }

    private fun loadModelos(marca: String) {
        header.text = "Modelos"
        val ref = Firebase.database.reference.child("diag_elec").child(marca)
        ref.get().addOnSuccessListener { snap ->
            val items = mutableListOf<Item>()
            snap.children.forEach { modeloNode ->
                val url = findFirstUrl(modeloNode, arrayOf("imgModelo", "imgMarca", "imgAnio"))
                val title = modeloNode.key ?: ""
                if (title.isNotBlank()) items.add(Item(title, url))
            }
            currentItems = items
            listView.adapter = ProcAdapter(this, items)
            prefetch(items.map { it.imageUrl })
        }.addOnFailureListener { Toast.makeText(this, "Error cargando modelos", Toast.LENGTH_SHORT).show() }
    }

    private fun loadAnios(marca: String, modelo: String) {
        header.text = "Años"
        val ref = Firebase.database.reference.child("diag_elec").child(marca).child(modelo)
        ref.get().addOnSuccessListener { snap ->
            val items = mutableListOf<Item>()
            snap.children.forEach { anioNode ->
                val url = anioNode.child("imgAnio").getValue(String::class.java)
                val title = anioNode.key ?: ""
                if (title.isNotBlank()) items.add(Item(title, url))
            }
            currentItems = items
            listView.adapter = ProcAdapter(this, items)
            prefetch(items.map { it.imageUrl })
        }.addOnFailureListener { Toast.makeText(this, "Error cargando años", Toast.LENGTH_SHORT).show() }
    }

    private fun loadFolders(marca: String, modelo: String, anio: String) {
        header.text = "Carpetas"
        val ref = Firebase.database.reference.child("diag_elec").child(marca).child(modelo).child(anio).child("folders")
        ref.get().addOnSuccessListener { snap ->
            val items = mutableListOf<Item>()
            snap.children.forEach { folderNode ->
                val metaName = folderNode.child("meta").child("nombre").getValue(String::class.java) ?: folderNode.key ?: ""
                if (metaName.isNotBlank()) items.add(Item(metaName, null))
            }
            currentItems = items
            listView.adapter = ProcAdapter(this, items)
            if (items.isEmpty()) Toast.makeText(this, "Sin carpetas", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { Toast.makeText(this, "Error cargando carpetas", Toast.LENGTH_SHORT).show() }
    }

    private fun loadFiles(marca: String, modelo: String, anio: String, folder: String) {
        header.text = "Archivos"
        val ref = Firebase.database.reference
            .child("diag_elec").child(marca).child(modelo).child(anio)
            .child("folders").child(sanitizeKey(folder)).child("files")
        ref.get().addOnSuccessListener { snap ->
            val items = mutableListOf<Item>()
            snap.children.forEach { fileNode ->
                val name = fileNode.child("name").getValue(String::class.java) ?: fileNode.key ?: ""
                val type = fileNode.child("type").getValue(String::class.java)
                val url = fileNode.child("url").getValue(String::class.java)
                if (name.isNotBlank()) items.add(Item(name, url, type))
            }
            currentItems = items
            listView.adapter = ProcAdapter(this, items)
            if (items.isEmpty()) Toast.makeText(this, "Sin archivos", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { Toast.makeText(this, "Error cargando archivos", Toast.LENGTH_SHORT).show() }
    }

    private fun openFile(item: Item) {
        val url = item.imageUrl ?: return
        val type = item.extra ?: "application/octet-stream"
        if (type.startsWith("image/")) {
            val intent = Intent(this, ImageViewerActivity::class.java)
            intent.putExtra("url", url)
            startActivity(intent)
        } else {
            runCatching {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setDataAndType(Uri.parse(url), type) }
                startActivity(intent)
            }.onFailure { Toast.makeText(this, "No hay app para abrir", Toast.LENGTH_SHORT).show() }
        }
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

    private fun sanitizeKey(name: String): String { return name.replace(Regex("[.#$\\[\\]/]"), "_") }

    inner class ProcAdapter(private val ctx: android.content.Context, private val data: List<Item>): BaseAdapter() {
        override fun getCount(): Int = data.size
        override fun getItem(position: Int): Any = data[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup?): android.view.View {
            val view = convertView ?: layoutInflater.inflate(R.layout.row_diag_item, parent, false)
            val title = view.findViewById<TextView>(R.id.rowTitle)
            val img = view.findViewById<ImageView>(R.id.rowImage)
            val item = data[position]
            title.text = item.title
            img.setImageResource(android.R.drawable.ic_menu_gallery)
            val url = item.imageUrl
            val cached = url?.let { imgCache.get(it) }
            if (cached != null) {
                img.setImageBitmap(cached)
            } else if (url != null && (url.startsWith("http") || url.startsWith("https"))) {
                executor.execute {
                    runCatching {
                        val conn = (URL(url).openConnection() as java.net.HttpURLConnection)
                        conn.connectTimeout = 1500
                        conn.readTimeout = 1500
                        conn.inputStream.use {
                            val bmp = BitmapFactory.decodeStream(it)
                            if (bmp != null) {
                                imgCache.put(url, bmp)
                                this@VerProcedimientosElecActivity.runOnUiThread { img.setImageBitmap(bmp) }
                            }
                        }
                        conn.disconnect()
                    }
                }
            }
            return view
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
