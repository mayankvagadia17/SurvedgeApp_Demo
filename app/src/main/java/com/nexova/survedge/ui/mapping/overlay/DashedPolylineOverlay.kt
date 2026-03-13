package com.nexova.survedge.ui.mapping.overlay

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Custom overlay for drawing dashed polylines on OSMDroid map
 */
class DashedPolylineOverlay(
    private var points: List<GeoPoint>,
    private val color: Int,
    private val width: Float,
    private val dashPattern: FloatArray = floatArrayOf(15f, 8f)
) : Overlay() {

    fun setPoints(newPoints: List<GeoPoint>) {
        this.points = newPoints
    }

    private val paint = Paint().apply {
        this.color = this@DashedPolylineOverlay.color
        strokeWidth = this@DashedPolylineOverlay.width
        style = Paint.Style.STROKE
        isAntiAlias = true
        pathEffect = DashPathEffect(dashPattern, 0f)
    }

    private val path = Path()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || points.size < 2) {
            return
        }

        path.reset()
        val projection = mapView.projection

        // Convert first point to screen coordinates
        val firstPoint = projection.toPixels(points[0], null)
        path.moveTo(firstPoint.x.toFloat(), firstPoint.y.toFloat())

        // Add remaining points
        for (i in 1 until points.size) {
            val screenPoint = projection.toPixels(points[i], null)
            path.lineTo(screenPoint.x.toFloat(), screenPoint.y.toFloat())
        }

        // Draw the path
        canvas.drawPath(path, paint)
    }
}

