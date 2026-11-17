package com.example.ar_ruler

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.sqrt
import java.util.Locale

class Pseudo3DView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#444444")
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val pointPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val linePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }

    private val points = mutableListOf<PointF>()
    var onDistanceMeasured: ((String) -> Unit)? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val horizon = h * 0.25f
        val centerX = w / 2f

        canvas.drawLine(0f, horizon, w, horizon, gridPaint)

        val numLines = 15
        for (i in 0..numLines) {
            val xBottom = (w / numLines) * i
            canvas.drawLine(centerX, horizon, xBottom, h, gridPaint)
        }

        val numHorizontalLines = 15
        for (i in 0..numHorizontalLines) {
            val t = i.toFloat() / numHorizontalLines
            val y = horizon + (h - horizon) * t * t
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        if (points.size == 2) {
            canvas.drawLine(points[0].x, points[0].y, points[1].x, points[1].y, linePaint)
        }

        for (p in points) {
            canvas.drawCircle(p.x, p.y, 24f, pointPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val horizon = height * 0.25f
            if (event.y <= horizon) return true

            if (points.size >= 2) {
                points.clear()
            }
            points.add(PointF(event.x, event.y))
            invalidate()

            if (points.size == 2) {
                val w = width.toFloat()
                val h = height.toFloat()

                // 3D-математика, которая ТОЧНО совпадает с нарисованной сеткой
                fun to3D(p: PointF): Pair<Float, Float> {
                    // u от 0 (горизонт) до 1 (низ экрана)
                    val u = (p.y - horizon) / (h - horizon)
                    // t от 0 до 1 (т.к. сетка рисуется по формуле t^2)
                    val t = sqrt(u.coerceIn(0.001f, 1f))

                    // Условная глубина: 0 внизу, 150 см у горизонта
                    val z = (1f - t) * 150f

                    // Условная ширина: 160 см на всю ширину экрана внизу
                    val x = (p.x - w / 2f) / u.coerceIn(0.01f, 1f) * (160f / w)

                    return Pair(x, z)
                }

                val (x1, z1) = to3D(points[0])
                val (x2, z2) = to3D(points[1])

                val cmDist = hypot(x2 - x1, z2 - z1)

                val text = String.format(Locale.getDefault(), "Расстояние: %.1f см", cmDist)
                onDistanceMeasured?.invoke(text)
                invalidate()
            } else {
                onDistanceMeasured?.invoke("Поставьте вторую точку")
            }
        }
        return true
    }

    fun clear() {
        points.clear()
        invalidate()
        onDistanceMeasured?.invoke("ДЕМО-3D РЕЖИМ: Коснитесь пола")
    }
}