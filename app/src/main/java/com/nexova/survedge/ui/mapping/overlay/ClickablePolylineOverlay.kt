package com.nexova.survedge.ui.mapping.overlay

import android.graphics.Color
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import com.nexova.survedge.ui.mapping.overlay.LabeledPoint

/**
 * Clickable polyline overlay that can be highlighted
 */
class ClickablePolylineOverlay(
    points: List<GeoPoint>,
    color: Int = Color.BLACK,
    width: Float = 6f,
    closed: Boolean = false,
    dashed: Boolean = false,
    private var onPolylineClick: (() -> Unit)? = null
) : Polyline() {
    
    private val originalColor: Int = color
    private var isHighlighted: Boolean = false
    
    // Line segment information
    var codeId: String = ""
    var featureCode: String = "LINE"
    var pointCount: Int = 0
    var length: Double = 0.0 // in meters
    var labeledPoints: List<LabeledPoint> = emptyList() // Store the points for editing
    var isClosed: Boolean = false
    
    init {
        val finalPoints = if (closed && points.isNotEmpty() && points.first() != points.last()) {
            points + points.first()
        } else {
            points
        }
        
        setPoints(finalPoints)
        this.color = color
        this.width = width
        isGeodesic = true

        if (dashed) {
            paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
        }
    }
    
    /**
     * Highlight the polyline with primary color
     */
    fun highlight(primaryColor: Int) {
        if (!isHighlighted) {
            color = primaryColor
            isHighlighted = true
        }
    }
    
    /**
     * Remove highlight and restore original color
     */
    fun unhighlight() {
        if (isHighlighted) {
            color = originalColor
            isHighlighted = false
        }
    }
    
    /**
     * Set click listener
     */
    fun setOnClickListener(listener: () -> Unit) {
        onPolylineClick = listener
    }
    
    /**
     * Check if a point is near the polyline (using pixel coordinates for accuracy)
     */
    fun isPointNearPolyline(mapView: MapView, eventX: Float, eventY: Float, tolerance: Float = 30f): Boolean {
        val projection = mapView.projection
        val clickX = eventX
        val clickY = eventY
        
        val distance = distanceToPolyline(mapView, eventX, eventY)
        return distance <= tolerance
    }

    /**
     * Get the minimum pixel distance from a point to this polyline
     */
    fun distanceToPolyline(mapView: MapView, eventX: Float, eventY: Float): Float {
        val projection = mapView.projection
        val points = actualPoints
        if (points.size < 2) return Float.MAX_VALUE
        
        val pixelPoints = points.map { point ->
            val pixelPoint = android.graphics.Point()
            projection.toPixels(point, pixelPoint)
            android.graphics.PointF(pixelPoint.x.toFloat(), pixelPoint.y.toFloat())
        }
        
        var minDistance = Float.MAX_VALUE
        for (i in 0 until pixelPoints.size - 1) {
            val dist = distanceToLineSegmentPixels(
                android.graphics.PointF(eventX, eventY),
                pixelPoints[i],
                pixelPoints[i + 1]
            )
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }
    
    /**
     * Calculate distance from a point to a line segment in pixel coordinates
     */
    private fun distanceToLineSegmentPixels(
        point: android.graphics.PointF,
        lineStart: android.graphics.PointF,
        lineEnd: android.graphics.PointF
    ): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        
        if (dx == 0f && dy == 0f) {
            // Line segment is a point
            val distX = point.x - lineStart.x
            val distY = point.y - lineStart.y
            return kotlin.math.sqrt(distX * distX + distY * distY)
        }
        
        val t = ((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) / 
                (dx * dx + dy * dy)
        
        val clampedT = t.coerceIn(0f, 1f)
        val closestX = lineStart.x + clampedT * dx
        val closestY = lineStart.y + clampedT * dy
        
        val distX = point.x - closestX
        val distY = point.y - closestY
        return kotlin.math.sqrt(distX * distX + distY * distY)
    }
    
    override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: MapView?): Boolean {
        if (e != null && mapView != null) {
            if (isPointNearPolyline(mapView, e.x, e.y)) {
                onPolylineClick?.invoke()
                return true
            }
        }
        return super.onSingleTapConfirmed(e, mapView)
    }
}

