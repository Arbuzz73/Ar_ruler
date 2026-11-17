package com.example.ar_ruler

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.HitResult
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private var arFragment: ArFragment? = null
    private lateinit var arMeasurementText: TextView
    private val anchors = mutableListOf<AnchorNode>()

    private lateinit var demoMeasurementText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val availability = ArCoreApk.getInstance().checkAvailability(this)
        val hasCamera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

        if (availability == ArCoreApk.Availability.SUPPORTED_INSTALLED && hasCamera) {
            setContentView(R.layout.activity_main)
            arMeasurementText = findViewById(R.id.measurementText)
            setupArMode()
        } else {
            fallbackToTestMode()
        }
    }

    private fun setupArMode() {
        arFragment = ArFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.ar_container, arFragment!!)
            .commit()

        arFragment?.setOnTapArPlaneListener { hitResult, _, _ ->
            if (anchors.size >= 2) clearAnchors()

            val anchorNode = createAnchorNode(hitResult)
            anchors.add(anchorNode)

            if (anchors.size == 2) {
                val p1 = anchors[0].worldPosition
                val p2 = anchors[1].worldPosition
                val distanceMeters = Vector3.subtract(p1, p2).length()
                showDistanceResult(distanceMeters * 100f)
            } else {
                arMeasurementText.text = "Поставьте вторую точку"
            }
        }
    }

    private fun createAnchorNode(hitResult: HitResult): AnchorNode {
        val anchor: Anchor = hitResult.createAnchor()
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arFragment?.arSceneView?.scene)

        MaterialFactory.makeOpaqueWithColor(this, Color(android.graphics.Color.RED))
            .thenAccept { material ->
                val sphere = ShapeFactory.makeSphere(0.02f, Vector3.zero(), material)
                val node = TransformableNode(arFragment?.transformationSystem)
                node.renderable = sphere
                node.setParent(anchorNode)
            }
        return anchorNode
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

    private fun showDistanceResult(distanceCm: Float) {
        val resultText = String.format(Locale.getDefault(), "Расстояние: %.1f см", distanceCm)
        if (::arMeasurementText.isInitialized) {
            arMeasurementText.text = resultText
        }
    }
}