package com.nexova.survedge.ui.mapping.overlay

import android.view.View
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Overlay to handle clicks on points in the map
 */
class PointClickHandlerOverlay(
    private val onPointClick: (GeoPoint) -> Boolean,
    private val protectedViews: List<View> = emptyList()
) : Overlay() {
    
    override fun onTouchEvent(event: android.view.MotionEvent?, mapView: MapView?): Boolean {
        // Intercept touch events early to check protected views before single tap is confirmed
        if (event != null && event.action == android.view.MotionEvent.ACTION_DOWN) {
            if (isTouchInProtectedView(event)) {
                // Don't consume, but mark that this is a protected view so onSingleTapConfirmed can skip it
                return false // Let other overlays process, but we'll skip in onSingleTapConfirmed
            }
        }
        return super.onTouchEvent(event, mapView)
    }
    
    override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: MapView?): Boolean {
        if (e != null && mapView != null) {
            // Check if click is on a protected view (like left panel, buttons, etc.)
            if (isTouchInProtectedView(e)) {
                return false // Don't process clicks on protected views
            }
            
            val projection = mapView.projection
            val clickedIGeoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt())
            // Convert IGeoPoint to GeoPoint
            val clickedGeoPoint = GeoPoint(clickedIGeoPoint.latitude, clickedIGeoPoint.longitude)
            // Only consume the event if a point was actually found and handled
            return onPointClick(clickedGeoPoint)
        }
        return super.onSingleTapConfirmed(e, mapView)
    }
    
    private fun isTouchInProtectedView(event: android.view.MotionEvent): Boolean {
        val screenX = event.rawX
        val screenY = event.rawY

        for (view in protectedViews) {
            if (isPointInView(screenX, screenY, view)) {
                return true
            }
        }
        return false
    }

    private fun isPointInView(x: Float, y: Float, view: View): Boolean {
        if (!view.isShown) return false
        
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]
        val right = left + view.width
        val bottom = top + view.height
        
        return x >= left && x <= right && y >= top && y <= bottom
    }
}

