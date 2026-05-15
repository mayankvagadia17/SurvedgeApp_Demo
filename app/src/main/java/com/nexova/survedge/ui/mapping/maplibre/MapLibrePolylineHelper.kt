package com.nexova.survedge.ui.mapping.maplibre

import android.graphics.Color
import com.nexova.survedge.ui.mapping.overlay.LabeledPoint
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Helper class for creating polylines on MapLibre MapView using GeoJsonSources and LineLayers.
 */
object MapLibrePolylineHelper {
    
    private const val SOURCE_PREFIX = "source_line_"
    private const val LAYER_PREFIX = "layer_line_"

    /**
     * Create or update a polyline on the map.
     * In MapLibre, we use Sources and Layers instead of Overlay objects.
     * Returns a string ID representing the layer/source.
     */
    fun createPolyline(
        mapLibreMap: MapLibreMap,
        lineId: String,
        points: List<LatLng>,
        color: Int = Color.parseColor("#717680"),
        width: Float = 2f,
        closed: Boolean = false,
        dashed: Boolean = false
    ): String {
        val sourceId = SOURCE_PREFIX + lineId
        val layerId = LAYER_PREFIX + lineId
        
        val finalPoints = if (closed && points.isNotEmpty() && points.first() != points.last()) {
            points + points.first()
        } else {
            points
        }
        
        val lineString = LineString.fromLngLats(finalPoints.map { Point.fromLngLat(it.longitude, it.latitude) })
        val feature = Feature.fromGeometry(lineString)
        feature.addStringProperty("id", lineId)
        
        val style = mapLibreMap.style ?: return ""
        
        var source = style.getSourceAs<GeoJsonSource>(sourceId)
        if (source == null) {
            source = GeoJsonSource(sourceId, feature)
            style.addSource(source)
        } else {
            source.setGeoJson(feature)
        }
        
        var layer = style.getLayerAs<LineLayer>(layerId)
        if (layer == null) {
            layer = LineLayer(layerId, sourceId).apply {
                setProperties(
                    PropertyFactory.lineColor(color),
                    PropertyFactory.lineWidth(width),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND)
                )
                if (dashed) {
                    setProperties(PropertyFactory.lineDasharray(arrayOf(2f, 2f)))
                }
            }
            // Add polyline below the points layer to ensure markers and labels stay on top
            if (style.getLayer("points_layer") != null) {
                style.addLayerBelow(layer, "points_layer")
            } else {
                style.addLayer(layer)
            }
        } else {
            layer.setProperties(
                PropertyFactory.lineColor(color),
                PropertyFactory.lineWidth(width)
            )
            if (dashed) {
                layer.setProperties(PropertyFactory.lineDasharray(arrayOf(2f, 2f)))
            } else {
                layer.setProperties(PropertyFactory.lineDasharray(null as Array<Float>?))
            }
        }
        
        return layerId
    }
    
    /**
     * Remove polyline from map.
     */
    fun removePolyline(mapLibreMap: MapLibreMap, layerId: String) {
        val style = mapLibreMap.style ?: return
        style.removeLayer(layerId)
        val sourceId = layerId.replace(LAYER_PREFIX, SOURCE_PREFIX)
        style.removeSource(sourceId)
    }

    /**
     * Alias for createPolyline to maintain compatibility with legacy calls.
     */
    fun addPolyline(
        mapLibreMap: MapLibreMap,
        lineId: String,
        points: List<LatLng>,
        color: Int = Color.BLACK,
        width: Float = 1f,
        isClosed: Boolean = false,
        isDashed: Boolean = false
    ): String = createPolyline(mapLibreMap, lineId, points, color, width, isClosed, isDashed)

    /**
     * Bulk update polylines on the map.
     */
    fun updatePolylines(mapLibreMap: MapLibreMap, lines: List<LineData>) {
        // Simple implementation: remove old and add new or update existing.
        // For performance, we could compare and only update changed ones.
        lines.forEach { line ->
            createPolyline(
                mapLibreMap,
                line.id,
                line.points,
                line.color,
                line.width,
                line.isClosed
            )
        }
    }

    /**
     * Highlight a polyline by changing its color.
     */
    fun highlightPolyline(mapLibreMap: MapLibreMap, layerId: String, color: Int) {
        val style = mapLibreMap.style ?: return
        val layer = style.getLayerAs<LineLayer>(layerId)
        layer?.setProperties(PropertyFactory.lineColor(color), PropertyFactory.lineWidth(2f))
    }

    /**
     * Unhighlight a polyline by resetting its color.
     */
    fun unhighlightPolyline(mapLibreMap: MapLibreMap, layerId: String, color: Int = Color.parseColor("#717680")) {
        val style = mapLibreMap.style ?: return
        val layer = style.getLayerAs<LineLayer>(layerId)
        layer?.setProperties(PropertyFactory.lineColor(color), PropertyFactory.lineWidth(2f))
    }

    /**
     * Clear all polylines created by this helper from the map.
     */
    fun clearPolylines(mapLibreMap: MapLibreMap) {
        val style = mapLibreMap.style ?: return
        
        // Create copies of the lists to avoid ConcurrentModificationException
        val layersToRemove = style.layers.filter { it.id.startsWith(LAYER_PREFIX) }.map { it.id }
        val sourcesToRemove = style.sources.filter { it.id.startsWith(SOURCE_PREFIX) }.map { it.id }
        
        layersToRemove.forEach { style.removeLayer(it) }
        sourcesToRemove.forEach { style.removeSource(it) }
    }
}

data class LineData(
    val id: String,
    val points: List<LatLng>,
    val color: Int,
    val width: Float,
    val isClosed: Boolean
)
