package com.nexova.survedge.ui.mapping.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

data class LabeledPoint(
    val id: String,
    val codeId: String,
    val coords: List<Double>, // [longitude, latitude]
    val elevation: Double,
    val ts: String // ISO 8601 timestamp
) {
    // Helper property to get GeoPoint from coords
    val geoPoint: GeoPoint
        get() = GeoPoint(coords[1], coords[0]) // coords is [lon, lat], GeoPoint needs (lat, lon)
}

//class LabeledPointOverlay(
//    private val mapView: MapView,
//    private val point: LabeledPoint
//) {
//    private var marker: Marker? = null
//
//    fun createMarker(): Marker {
//        val bitmap = createLabeledPointBitmap(point.id, point.code)
//        val bitmapDrawable = BitmapDrawable(mapView.context.resources, bitmap)
//
//        val density = mapView.context.resources.displayMetrics.density
//        val padding = 8 * density
//        val pointRadius = 6 * density
//        val idTextSize = 12 * density
//        val codeTextSize = 10 * density
//
//        val idBounds = android.graphics.Rect()
//        val idPaint = Paint(Paint.ANTI_ALIAS_FLAG)
//        idPaint.textSize = idTextSize
//        idPaint.getTextBounds(point.id, 0, point.id.length, idBounds)
//
//        val codeBounds = android.graphics.Rect()
//        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG)
//        codePaint.textSize = codeTextSize
//        codePaint.getTextBounds(point.code, 0, point.code.length, codeBounds)
//
//        val height = (idBounds.height() + codeBounds.height() + pointRadius * 2 + padding * 2).toInt()
//        val pointY = height - padding - pointRadius
//
//        val anchorY = pointY / height.toFloat()
//
//        marker = Marker(mapView).apply {
//            icon = bitmapDrawable
//            position = point.geoPoint
//            setAnchor(0.5f, anchorY)
//            isDraggable = false
//            infoWindow = null
//        }
//
//        return marker!!
//    }
//
//    private fun createLabeledPointBitmap(id: String, code: String): Bitmap {
//        val density = mapView.context.resources.displayMetrics.density
//        val padding = 8 * density
//        val idTextSize = 12 * density
//        val codeTextSize = 10 * density
//        val pointRadius = 6 * density
//
//        val idPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//            textSize = idTextSize
//            typeface = Typeface.DEFAULT_BOLD
//            color = android.graphics.Color.BLACK
//        }
//        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//            textSize = codeTextSize
//            typeface = Typeface.DEFAULT
//            color = android.graphics.Color.parseColor("@color/text_secondary")
//        }
//
//        val idBounds = android.graphics.Rect()
//        idPaint.getTextBounds(id, 0, id.length, idBounds)
//        val codeBounds = android.graphics.Rect()
//        codePaint.getTextBounds(code, 0, code.length, codeBounds)
//
//        val maxWidth = maxOf(idBounds.width(), codeBounds.width()).toFloat()
//        val height = (idBounds.height() + codeBounds.height() + pointRadius * 2 + padding * 2).toInt()
//        val width = (maxWidth + padding * 2).toInt()
//
//        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        val canvas = Canvas(bitmap)
//
//        val pointY = height - padding - pointRadius
//        val pointX = width / 2f
//        val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//            color = android.graphics.Color.parseColor("@color/stakeout_connection_line")
//            style = Paint.Style.FILL
//        }
//        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
//            color = android.graphics.Color.WHITE
//            style = Paint.Style.STROKE
//            strokeWidth = 2 * density
//        }
//
//        canvas.drawCircle(pointX, pointY, pointRadius, pointPaint)
//        canvas.drawCircle(pointX, pointY, pointRadius, strokePaint)
//
//        val idY = pointY - pointRadius - padding
//        val idX = (width - idBounds.width()) / 2f
//        canvas.drawText(id, idX, idY, idPaint)
//
//        val codeY = pointY + pointRadius + padding + codeBounds.height()
//        val codeX = (width - codeBounds.width()) / 2f
//        canvas.drawText(code, codeX, codeY, codePaint)
//
//        return bitmap
//    }
//
//    fun remove() {
//        marker?.let {
//            mapView.overlays.remove(it)
//            mapView.invalidate()
//        }
//    }
//}

