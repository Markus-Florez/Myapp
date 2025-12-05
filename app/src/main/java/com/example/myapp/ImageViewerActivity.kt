package com.example.myapp

import android.graphics.Matrix
import android.graphics.PointF
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import android.view.WindowManager
import com.google.firebase.FirebaseApp
import com.google.firebase.ktx.Firebase
import com.google.firebase.database.ktx.database
import com.google.firebase.auth.ktx.auth
import java.net.URL
import android.graphics.BitmapFactory

class ImageViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_image_viewer)
        applySecureFlagForUser()

        val url = intent.getStringExtra("url") ?: return
        val image = findViewById<ImageView>(R.id.zoomImage)
        Thread {
            runCatching {
                val bmp = BitmapFactory.decodeStream(URL(url).openStream())
                runOnUiThread { image.setImageBitmap(bmp) }
            }
        }.start()

        val matrix = Matrix()
        val savedMatrix = Matrix()
        val startPoint = PointF()
        var mode = 0
        var oldDist = 1f

        image.scaleType = ImageView.ScaleType.MATRIX
        image.imageMatrix = matrix

        image.setOnTouchListener { v, event ->
            when (event.action and android.view.MotionEvent.ACTION_MASK) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    savedMatrix.set(matrix)
                    startPoint.set(event.x, event.y)
                    mode = 1
                }
                android.view.MotionEvent.ACTION_POINTER_DOWN -> {
                    oldDist = spacing(event)
                    if (oldDist > 10f) {
                        savedMatrix.set(matrix)
                        midPoint(startPoint, event)
                        mode = 2
                    }
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (mode == 1) {
                        matrix.set(savedMatrix)
                        matrix.postTranslate(event.x - startPoint.x, event.y - startPoint.y)
                    } else if (mode == 2) {
                        val newDist = spacing(event)
                        if (newDist > 10f) {
                            val scale = newDist / oldDist
                            matrix.set(savedMatrix)
                            matrix.postScale(scale, scale, startPoint.x, startPoint.y)
                        }
                    }
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_POINTER_UP -> {
                    mode = 0
                }
            }
            image.imageMatrix = matrix
            true
        }
    }

    private fun spacing(event: android.view.MotionEvent): Float {
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(x * x + y * y)
    }

    private fun midPoint(point: PointF, event: android.view.MotionEvent) {
        val x = (event.getX(0) + event.getX(1)) / 2
        val y = (event.getY(0) + event.getY(1)) / 2
        point.set(x, y)
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
