package com.nexova.survedge.ui.mapping.overlay

data class LabeledPoint(
    val id: String,
    val codeId: String,
    val coords: List<Double>, // [longitude, latitude]
    val elevation: Double,
    val ts: String // ISO 8601 timestamp
) {
    // Helper property to get LatLng from coords (MapLibre)
    val latLng: org.maplibre.android.geometry.LatLng
        get() = org.maplibre.android.geometry.LatLng(coords[1], coords[0])
}

