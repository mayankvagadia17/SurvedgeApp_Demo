package com.nexova.survedge.ui.mapping.maplibre

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

/**
 * Helper class for creating markers on osmdroid MapView
 */
object OsmdroidMarkerHelper {

    fun createMarker(
        mapView: MapView,
        bitmap: Bitmap,
        position: GeoPoint,
        anchorX: Float = 0.5f,
        anchorY: Float = 0.5f
    ): Marker {
        val bitmapDrawable = BitmapDrawable(mapView.context.resources, bitmap)
        
        val marker = Marker(mapView).apply {
            icon = bitmapDrawable
            this.position = position
            setAnchor(anchorX, anchorY)
            isDraggable = false
            infoWindow = null
        }
        
        mapView.overlays.add(marker)
        mapView.invalidate()
        
        return marker
    }
    
    /**
     * Remove marker from map
     */
    fun removeMarker(mapView: MapView, marker: Marker) {
        mapView.overlays.remove(marker)
        mapView.invalidate()
    }
}

