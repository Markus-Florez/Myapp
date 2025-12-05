package com.example.myapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.google.firebase.FirebaseApp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.net.URL
import java.util.concurrent.Executors

data class Item(val title: String, val imageUrl: String?)

class DiagElecActivity : ComponentActivity() {
    private val executor = Executors.newFixedThreadPool(3)
    private lateinit var listView: ListView
    private lateinit var search: EditText
    private lateinit var header: TextView
    private var currentItems: MutableList<Item> = mutableListOf()
    private var step = 1
    private var selectedMarca: String? = null
    private var selectedModelo: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_diag_elec)

        header = findViewById(R.id.diagHeader)
        listView = findViewById(R.id.diagList)
        search = findViewById(R.id.diagSearch)

        loadMarcas()

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = currentItems[position]
            when (step) {
                1 -> { selectedMarca = item.title; step = 2; loadModelos(selectedMarca!!) }
                2 -> { selectedModelo = item.title; step = 3; loadAnios(selectedMarca!!, selectedModelo!!) }
                3 -> { Toast.makeText(this, "Seleccionado: ${selectedMarca}-${selectedModelo}-${item.title}", Toast.LENGTH_SHORT).show() }
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
        val base = when(step) { 1 -> "Marca"; 2 -> "Modelo"; else -> "Año" }
        header.text = "${base}s"
        val adapter = DiagAdapter(this, currentItems.filter { it.title.contains(q, true) })
        listView.adapter = adapter
    }

    private fun loadMarcas() {
        header.text = "Marcas"
        val ref = Firebase.database.reference.child("diag_elec")
        ref.get().addOnSuccessListener { snap ->
            val items = mutableListOf<Item>()
            snap.children.forEach { marcaNode ->
                val url = findFirstUrl(marcaNode, "imgMarca")
                val title = marcaNode.key ?: ""
                if (title.isNotBlank()) items.add(Item(title, url))
            }
            currentItems = items
            listView.adapter = DiagAdapter(this, items)
            if (items.isEmpty()) {
                Toast.makeText(this, "Sin marcas disponibles", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            android.util.Log.e("DiagElec", "Error cargando marcas: ${e.localizedMessage}")
            Toast.makeText(this, "Error cargando marcas", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadModelos(marca: String) {
        header.text = "Modelos"
        val ref = Firebase.database.reference.child("diag_elec").child(marca)
        ref.get().addOnSuccessListener { snap ->
            val items = mutableListOf<Item>()
            snap.children.forEach { modeloNode ->
                val url = findFirstUrl(modeloNode, "imgModelo")
                items.add(Item(modeloNode.key ?: "", url))
            }
            currentItems = items
            listView.adapter = DiagAdapter(this, items)
        }.addOnFailureListener { Toast.makeText(this, "Error cargando modelos", Toast.LENGTH_SHORT).show() }
    }

    private fun loadAnios(marca: String, modelo: String) {
        header.text = "Años"
        val ref = Firebase.database.reference.child("diag_elec").child(marca).child(modelo)
        ref.get().addOnSuccessListener { snap ->
            val items = mutableListOf<Item>()
            snap.children.forEach { anioNode ->
                val url = anioNode.child("imgAnio").getValue(String::class.java)
                items.add(Item(anioNode.key ?: "", url))
            }
            currentItems = items
            listView.adapter = DiagAdapter(this, items)
        }.addOnFailureListener { Toast.makeText(this, "Error cargando años", Toast.LENGTH_SHORT).show() }
    }

    private fun findFirstUrl(node: DataSnapshot, key: String): String? {
        node.children.forEach { child ->
            val v = child.child(key).getValue(String::class.java)
            if (!v.isNullOrBlank()) return v
            child.children.forEach { gchild ->
                val v2 = gchild.child(key).getValue(String::class.java)
                if (!v2.isNullOrBlank()) return v2
            }
        }
        return null
    }

    inner class DiagAdapter(private val ctx: android.content.Context, private val data: List<Item>): BaseAdapter() {
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
            if (url != null && (url.startsWith("http") || url.startsWith("https"))) {
                executor.execute {
                    runCatching {
                        val bmp = BitmapFactory.decodeStream(URL(url).openStream())
                        runOnUiThread { img.setImageBitmap(bmp) }
                    }
                }
            }
            return view
        }
    }
}