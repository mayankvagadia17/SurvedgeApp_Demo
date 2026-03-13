package com.nexova.survedge.ui.mapping.maplibre

import android.graphics.Color
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import com.nexova.survedge.ui.mapping.overlay.DashedPolylineOverlay

/**
 * Helper class for creating polylines on osmdroid MapView
 */
object OsmdroidPolylineHelper {
    
    /**
     * Create or update a polyline on the map
     * Returns either Polyline (for solid) or DashedPolylineOverlay (for dashed)
     */
    fun createPolyline(
        mapView: MapView,
        points: List<GeoPoint>,
        color: Int = Color.BLACK,
        width: Float = 6f,
        closed: Boolean = false,
        dashed: Boolean = false
    ): Any {
        // Only close the loop if explicitly requested
        val finalPoints = if (closed && points.isNotEmpty() && points.first() != points.last()) {
            points + points.first()
        } else {
            points
        }
        
        return if (dashed) {
            val dashedOverlay = DashedPolylineOverlay(finalPoints, color, width)
            val locationMarkerIndex = mapView.overlays.indexOfFirst { 
                it is org.osmdroid.views.overlay.Marker
            }
            if (locationMarkerIndex >= 0) {
                mapView.overlays.add(locationMarkerIndex, dashedOverlay)
            } else {
                val lastMarkerIndex = mapView.overlays.indexOfLast {
                    it is org.osmdroid.views.overlay.Marker
                }
                if (lastMarkerIndex >= 0) {
                    mapView.overlays.add(lastMarkerIndex, dashedOverlay)
                } else {
                    mapView.overlays.add(dashedOverlay)
                }
            }
            mapView.invalidate()
            dashedOverlay
        } else {
            // Use standard Polyline for solid lines
            val polyline = Polyline().apply {
                setPoints(finalPoints)
                this.color = color
                this.width = width
                isGeodesic = true
            }
            // Insert polyline before markers to ensure lines render below points
            val firstMarkerIndex = mapView.overlays.indexOfFirst { 
                it is org.osmdroid.views.overlay.Marker
            }
            if (firstMarkerIndex >= 0) {
                mapView.overlays.add(firstMarkerIndex, polyline)
            } else {
                mapView.overlays.add(polyline)
            }
            mapView.invalidate()
            polyline
        }
    }
    
    /**
     * Remove polyline from map (works with both Polyline and DashedPolylineOverlay)
     */
    fun removePolyline(mapView: MapView, overlay: Any) {
        mapView.overlays.remove(overlay)
        mapView.invalidate()
    }
}

