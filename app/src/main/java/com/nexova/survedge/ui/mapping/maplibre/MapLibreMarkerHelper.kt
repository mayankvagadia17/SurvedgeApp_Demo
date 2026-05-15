package com.nexova.survedge.ui.mapping.maplibre

import android.graphics.Bitmap
import com.nexova.survedge.ui.mapping.overlay.LabeledPoint
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/**
 * Helper class for managing markers in MapLibre using SymbolLayers and GeoJsonSources.
 */
object MapLibreMarkerHelper {

    private const val POINTS_SOURCE_ID = "points_source"
    private const val POINTS_LAYER_ID = "points_layer"
    private const val ICON_PROPERTY = "icon_id"

    /**
     * Update all points on the map.
     */
    fun updateMarkers(
        mapLibreMap: MapLibreMap,
        points: List<LabeledPoint>,
        selectedPointId: String? = null
    ) {
        val style = mapLibreMap.style ?: return
        
        val features = points.map { point ->
            val feature = Feature.fromGeometry(
                Point.fromLngLat(point.latLng.longitude, point.latLng.latitude)
            )
            feature.addStringProperty("id", point.id)
            feature.addStringProperty("code", point.codeId)
            feature.addStringProperty(ICON_PROPERTY, "icon_${point.id}")
            feature
        }

        val source = style.getSourceAs<GeoJsonSource>(POINTS_SOURCE_ID)
        if (source == null) {
            style.addSource(GeoJsonSource(POINTS_SOURCE_ID, FeatureCollection.fromFeatures(features)))
            val layer = SymbolLayer(POINTS_LAYER_ID, POINTS_SOURCE_ID).apply {
                setProperties(
                    PropertyFactory.iconImage("{${ICON_PROPERTY}}"),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            }
            style.addLayer(layer)
        } else {
            source.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }

    /**
     * Add a custom bitmap icon for a specific point.
     */
    fun addOrUpdateIcon(mapLibreMap: MapLibreMap, iconId: String, bitmap: Bitmap) {
        mapLibreMap.style?.let { style ->
            style.addImage(iconId, bitmap)
        }
    }

    /**
     * Update or create the user location marker.
     */
    fun updateLocationMarker(mapLibreMap: MapLibreMap, latLng: LatLng, iconId: String) {
        val style = mapLibreMap.style ?: return
        val sourceId = "user_location_source"
        val layerId = "user_location_layer"

        val feature = Feature.fromGeometry(Point.fromLngLat(latLng.longitude, latLng.latitude))
        
        var source = style.getSourceAs<GeoJsonSource>(sourceId)
        if (source == null) {
            style.addSource(GeoJsonSource(sourceId, feature))
            val layer = SymbolLayer(layerId, sourceId).apply {
                setProperties(
                    PropertyFactory.iconImage(iconId),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP)
                )
            }
            style.addLayer(layer)
        } else {
            source.setGeoJson(feature)
        }
    }
    
    /**
     * Clear all points from the map.
     */
    fun clearMarkers(mapLibreMap: MapLibreMap) {
        val style = mapLibreMap.style ?: return
        val source = style.getSourceAs<GeoJsonSource>(POINTS_SOURCE_ID)
        source?.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }
}
