package com.example.ar_ruler

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var arSceneView: ArSceneView? = null
    private var session: Session? = null
    private var installRequested = false

    private lateinit var arMeasurementText: TextView
    private val anchors = mutableListOf<AnchorNode>()

    private lateinit var demoMeasurementText: TextView

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            setupArMode()
        } else {
            fallbackToTestMode()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val availability = ArCoreApk.getInstance().checkAvailability(this)
        val hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        if (availability == ArCoreApk.Availability.SUPPORTED_INSTALLED && hasCamera) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                setupArMode()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } else {
            fallbackToTestMode()
        }
    }

    private fun setupArMode() {
        try {
            setContentView(R.layout.activity_main)
            arMeasurementText = findViewById(R.id.measurementText)
            arSceneView = findViewById(R.id.arSceneView)

            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> {}
            }

            session = Session(this)
            val config = Config(session)
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            config.lightEstimationMode = Config.LightEstimationMode.DISABLED
            session?.configure(config)

            arSceneView?.session = session

            // ❗ ВАЖНО: убираем setOnTouchListener с ArSceneView.
            // Вместо этого вешаем слушатель на сцену — он получает события раньше,
            // чем они будут использованы для управления камерой.
            arSceneView?.scene?.addOnPeekTouchListener { _, motionEvent ->
                if (motionEvent.action == MotionEvent.ACTION_UP) { // используем ACTION_UP для чёткого «тапа»
                    handleTap(motionEvent.x, motionEvent.y)
                }
            }

            // Запускаем и сессию, и ArSceneView
            session?.resume()
            arSceneView?.resume()
            arMeasurementText.text = "Наведите камеру на пол и коснитесь экрана"

        } catch (e: Exception) {
            Log.e("AR_CRASH", "Ошибка запуска AR: ${e.message}")
            fallbackToTestMode()
            Toast.makeText(this, "AR не запустился, включен Демо-режим", Toast.LENGTH_LONG).show()
        }
    }

    private fun handleTap(x: Float, y: Float) {
        val frame = arSceneView?.arFrame ?: run {
            Log.d("AR_TAP", "arFrame == null")
            return
        }

        Log.d("AR_TAP", "TrackingState = ${frame.camera.trackingState}")
        if (frame.camera.trackingState != TrackingState.TRACKING) {
            arMeasurementText.text = "Подождите, идёт инициализация AR…"
            return
        }

        // 1) Пытаемся найти плоскость
        val hitTestList = frame.hitTest(x, y)
        Log.d("AR_TAP", "hitTest (plane) results = ${hitTestList.size}")

        var hitResult: com.google.ar.core.HitResult? = null
        for (hit in hitTestList) {
            val trackable = hit.trackable
            if (trackable is com.google.ar.core.Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                hitResult = hit
                break
            }
        }

        // 2) Если плоскость не найдена — используем Instant Placement (резервный вариант)
        if (hitResult == null) {
            Log.d("AR_TAP", "No plane hit, trying Instant Placement")
            // ИСПРАВЛЕНО: берем первый элемент из списка (или null, если список пуст)
            hitResult = frame.hitTestInstantPlacement(x, y, 1.0f).firstOrNull()
        }

        if (hitResult == null) {
            arMeasurementText.text = "Не удалось определить точку. Попробуйте навести камеру на пол."
            return
        }

        // Добавляем точку
        if (anchors.size >= 2) clearAnchors()

        val anchor = hitResult.createAnchor()
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arSceneView?.scene)

        MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.RED))
            .thenAccept { material ->
                val sphere = ShapeFactory.makeSphere(0.02f, Vector3.zero(), material)
                val node = Node()
                node.renderable = sphere
                node.setParent(anchorNode)
            }

        anchors.add(anchorNode)

        if (anchors.size == 2) {
            val p1 = anchors[0].worldPosition
            val p2 = anchors[1].worldPosition
            val distanceMeters = Vector3.subtract(p1, p2).length()
            val distanceCm = distanceMeters * 100f
            arMeasurementText.text = String.format(Locale.getDefault(), "Расстояние: %.1f см", distanceCm)
        } else {
            arMeasurementText.text = "Поставьте вторую точку"
        }
    }

    private fun clearAnchors() {
        anchors.forEach { it.setParent(null) }
        anchors.clear()
    }

    private fun fallbackToTestMode() {
        setContentView(R.layout.activity_test)
        demoMeasurementText = findViewById(R.id.measurementText)
        val pseudo3DView = findViewById<Pseudo3DView>(R.id.pseudo3DView)

        findViewById<View>(R.id.resetButton).setOnClickListener {
            pseudo3DView.clear()
        }

        pseudo3DView.onDistanceMeasured = { text ->
            demoMeasurementText.text = text
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            session?.resume()
            arSceneView?.resume()
        } catch (e: Exception) {
            Log.e("AR_CRASH", "Ошибка onResume: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            arSceneView?.pause()
            session?.pause()
        } catch (e: Exception) {
            Log.e("AR_CRASH", "Ошибка onPause: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            session?.close()
        } catch (e: Exception) {
            Log.e("AR_CRASH", "Ошибка onDestroy: ${e.message}")
        }
    }
}