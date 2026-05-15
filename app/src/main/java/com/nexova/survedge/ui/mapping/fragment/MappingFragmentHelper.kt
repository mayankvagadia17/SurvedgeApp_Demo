package com.nexova.survedge.ui.mapping.fragment

import android.graphics.Color
import android.graphics.RectF
import android.hardware.SensorManager
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.nexova.survedge.R
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import com.nexova.survedge.ui.mapping.maplibre.MapLibrePolylineHelper
import com.nexova.survedge.ui.mapping.maplibre.MapLibreMarkerHelper
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.geojson.Feature
import org.maplibre.geojson.Point
import org.maplibre.geojson.LineString
import com.nexova.survedge.ui.stakeout.model.*
import com.nexova.survedge.ui.stakeout.util.*
import com.nexova.survedge.data.db.entity.LineWithPoints
import android.widget.TextView

class MappingFragmentHelper(private val fragment: MappingFragment) {

    fun initializeMap() {
        fragment.binding.mapView.getMapAsync { mapLibreMap ->
            fragment.mapLibreMap = mapLibreMap
            
            // Set style using OpenStreetMap raster tiles
            val osmTileSet = TileSet("2.1.0", "https://tile.openstreetmap.org/{z}/{x}/{y}.png")
            osmTileSet.minZoom = 0f
            osmTileSet.maxZoom = 19f
            
            val styleBuilder = Style.Builder()
                .withSource(RasterSource("osm-source", osmTileSet, 256))
                .withLayer(RasterLayer("osm-layer", "osm-source"))

            mapLibreMap.setStyle(styleBuilder) { style ->
                // Basic setup
                mapLibreMap.uiSettings.isCompassEnabled = false
                mapLibreMap.uiSettings.isLogoEnabled = false
                mapLibreMap.uiSettings.isAttributionEnabled = false
                
                mapLibreMap.addOnCameraIdleListener {
                    fragment.isMapFitted = false
                    fragment.logic.updateCompassRotation()
                    
                    val currentZoom = mapLibreMap.cameraPosition.zoom
                    if (kotlin.math.abs(currentZoom - fragment.lastZoomLevel) > 0.5 && fragment.collectedLabeledPoints.isNotEmpty()) {
                        fragment.lastZoomLevel = currentZoom
                        fragment.logic.updateMarkersForZoom()
                    }
                }

                mapLibreMap.addOnCameraMoveListener {
                    fragment.logic.updateCompassRotation()
                }

                mapLibreMap.addOnMapClickListener { latLng ->
                    val point = mapLibreMap.projection.toScreenLocation(latLng)
                    val rect = RectF(point.x - 20f, point.y - 20f, point.x + 20f, point.y + 20f)
                    val features = mapLibreMap.queryRenderedFeatures(rect)
                    
                    // 1. Detect point clicks (check geometry type and id property)
                    val clickedPoint = features.find { it.geometry() is Point && it.getStringProperty("id") != null }
                    if (clickedPoint != null) {
                        val pointId = clickedPoint.getStringProperty("id")
                        fragment.collectedLabeledPoints.find { it.id == pointId }?.let {
                            fragment.logic.handlePointClick(it)
                        }
                        true
                    } else {
                        // 2. Detect polyline clicks (check geometry type and id property)
                        val clickedLine = features.find { it.geometry() is LineString && it.getStringProperty("id") != null }
                        if (clickedLine != null) {
                            val lineId = clickedLine.getStringProperty("id")
                            fragment.logic.handleLineSegmentClick("layer_line_" + lineId)
                            true
                        } else {
                            // If we didn't click anything relevant
                            fragment.logic.hideLineSegmentDetailsBottomSheet()
                            fragment.logic.hidePointDetailsBottomSheet()
                            false
                        }
                    }
                }

                fragment.lastZoomLevel = 15.0
                mapLibreMap.moveCamera(CameraUpdateFactory.zoomTo(15.0))
                
                fragment.logic.createLocationPin()
                
                // Trigger database observe after map is ready
                // Actually, they are already observed in Fragment, but we might need to refresh
                fragment.logic.refreshMapData()
            }
        }
    }

    fun setupZoomControls() {
        fragment.binding.imgZoomIn.setOnClickListener {
            val currentZoom = fragment.mapLibreMap?.cameraPosition?.zoom ?: return@setOnClickListener
            fragment.mapLibreMap?.animateCamera(CameraUpdateFactory.zoomTo(currentZoom + 1.0))
        }
        fragment.binding.imgZoomOut.setOnClickListener {
            val currentZoom = fragment.mapLibreMap?.cameraPosition?.zoom ?: return@setOnClickListener
            fragment.mapLibreMap?.animateCamera(CameraUpdateFactory.zoomTo(currentZoom - 1.0))
        }
    }


    // ---------------------------------------------------------------------
    // Stakeout UI Helpers
    // ---------------------------------------------------------------------

    fun setupStakeoutUI() {
        val sheet = fragment.binding.stakeoutBottomSheet

        sheet.btnCloseStakeout.setOnClickListener {
            fragment.logic.stopStakeoutSession()
            hideStakeoutUI(previousSheet = fragment.previousStakeoutSheet)
        }

        // Initialize Page Widths and Scroll Listener
        sheet.hsvCards.viewTreeObserver.addOnGlobalLayoutListener {
            val width = sheet.hsvCards.width
            if (width > 0) {
                val params1 = sheet.llPage1.layoutParams
                if (params1.width != width) {
                    params1.width = width
                    sheet.llPage1.layoutParams = params1
                }

                val params2 = sheet.llPage2.layoutParams
                if (params2.width != width) {
                    params2.width = width
                    sheet.llPage2.layoutParams = params2
                }
            }
        }

        sheet.hsvCards.setOnScrollChangeListener { v, scrollX, scrollY, oldScrollX, oldScrollY ->
            val w = v.width
            if (w > 0) {
                // If scroll is past halfway, page 1 (index 1), else page 0
                val page = if (scrollX > w / 2) 1 else 0
                
                // Safety check for context availability
                if (fragment.context != null) {
                    val active = ContextCompat.getDrawable(fragment.requireContext(), R.drawable.dot_active)
                    val inactive = ContextCompat.getDrawable(fragment.requireContext(), R.drawable.dot_inactive)

                    sheet.dot1.background = if (page == 0) active else inactive
                    sheet.dot2.background = if (page == 1) active else inactive
                }
            }
        }
    }

    fun showStakeoutUI() = fragment.logic.hideMenu {
        fragment.logic.hideBottomNavigation {
            val root = fragment.binding.stakeoutBottomSheet.root

            // Store the current active sheet so we can return to it
            fragment.previousStakeoutSheet = fragment.logic.currentActiveSheet

            root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            root.requestLayout()
            root.visibility = View.VISIBLE
            // Hide the main Collect FAB to prevent overlap and interaction
            fragment.binding.btnCollect.visibility = View.GONE

            fragment.logic.animateSheetTransition(null, root, MappingFragmentLogic.BottomSheetTransition.SLIDE_UP)
            fragment.logic.adjustMapsButtonsForBottomSheet(root.height)

            // Update current active sheet
            fragment.logic.currentActiveSheet = MappingFragmentLogic.SheetType.STAKEOUT

            // Draw connection line immediately after UI is ready
            drawInitialConnectionLine()

            fragment.logic.setupSwipeToDismiss(root) {
                fragment.logic.stopStakeoutSession()
                hideStakeoutUI(previousSheet = fragment.previousStakeoutSheet)
            }
        }
    }
    
    /**
     * Draws the initial connection line when stakeout starts.
     * Called after the stakeout UI is shown to ensure proper timing.
     */
    private fun drawInitialConnectionLine() {
        val session = fragment.stakeoutSession ?: return
        val currentTarget = session.targetPoints.getOrNull(session.currentIndex) ?: return
        val targetGeo = LatLng(currentTarget.latitude, currentTarget.longitude, currentTarget.elevation)
        
        // Get current location from various sources
        val currentGeo = fragment.currentLocation
        
        if (currentGeo != null) {
            // Draw connection line immediately
            updateConnectionLine(currentGeo, targetGeo)
            
            // Calculate distance for proximity circle
            val dist = currentGeo.distanceTo(targetGeo)
            updateProximityCircle(targetGeo, dist < 10.0)
        } else {
            // No location available yet, try to get last known location
            try {
                fragment.fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
                    if (location != null) {
                        val geoPoint = LatLng(location.latitude, location.longitude, location.altitude)
                        fragment.currentLocation = geoPoint
                        updateConnectionLine(geoPoint, targetGeo)
                        val dist = geoPoint.distanceTo(targetGeo)
                        updateProximityCircle(targetGeo, dist < 10.0)
                    }
                }
            } catch (e: SecurityException) {
                // Location permission not granted
            }
        }
    }

    fun hideStakeoutUI(showNav: Boolean = true, onHidden: (() -> Unit)? = null, previousSheet: MappingFragmentLogic.SheetType? = null) {
        val root = fragment.binding.stakeoutBottomSheet.root

        val afterAnimation: () -> Unit = {
            onHidden?.invoke()
            fragment.logic.adjustMapsButtonsForBottomSheet()
            // Restore the main Collect FAB
            fragment.binding.btnCollect.visibility = View.VISIBLE
            val restoreLine = fragment.restoreLineSegmentAfterStakeout
            fragment.restoreLineSegmentAfterStakeout = null
            if (restoreLine != null) {
                val lineWithPoints = fragment.viewModel.currentLines.value.find { it.line.id == restoreLine }
                if (lineWithPoints != null) {
                    fragment.logic.showLineSegmentDetailsBottomSheet(
                        lineWithPoints,
                        MappingFragmentLogic.BottomSheetTransition.SLIDE_UP
                    )
                }
            } else if (showNav) {
                fragment.logic.restoreStateAfterClosingInfoSheet()
            } else {
                // Transitioning to an info sheet, hide visuals
                clearStakeoutMarkers()
                if (fragment.isInBullseyeMode) hideBullseyeView()
            }
        }

        // If we know which sheet was active before, restore it instead of closing all
        if (previousSheet != null && previousSheet != MappingFragmentLogic.SheetType.NONE) {
            val incomingView = fragment.logic.getBindingRootForType(previousSheet)
            fragment.logic.currentActiveSheet = previousSheet
            fragment.logic.animateSheetTransition(root, incomingView, MappingFragmentLogic.BottomSheetTransition.SLIDE_DOWN, afterAnimation)
        } else {
            fragment.logic.animateSheetTransition(root, null, MappingFragmentLogic.BottomSheetTransition.SLIDE_DOWN, afterAnimation)
        }
    }


    fun updateStakeoutBottomSheet(m: StakeoutMeasurement) {
        val sheet = fragment.binding.stakeoutBottomSheet
        val context = fragment.requireContext()

        // Update Header
        sheet.tvCurrentPoint.text = m.targetPointId
        fragment.stakeoutSession?.targetPoints?.getOrNull(
            fragment.stakeoutSession?.currentIndex ?: 0
        )?.let { point ->
            sheet.tvTargetCode.text =
                point.name
            sheet.llCodeIdContainer.visibility = if (point.isLine) View.VISIBLE else View.GONE
        }

        // --- PAGE 1: Cartesian Metrics ---
        // Update Direction Indicators - Card 1 (North/South)
        val northText = if (m.northOffset > 0) "To North" else "To South"
        sheet.tvNorthSouth.text = String.format("%.2fm", kotlin.math.abs(m.northOffset))
        sheet.lblNorthSouth.text = northText
        sheet.imgNorthSouth.setImageResource(R.drawable.ic_arrow_right_so)
        sheet.imgNorthSouth.rotation = if (m.northOffset > 0) -90f else 90f
        if (!m.inTolerance) {
            sheet.imgNorthSouth.setColorFilter(
                if (kotlin.math.abs(m.northOffset) < 0.05) ContextCompat.getColor(
                    context,
                    R.color.stakeout_green
                ) else ContextCompat.getColor(context, R.color.stakeout_orange)
            )
        }

        // Update Direction Indicators - Card 2 (East/West)
        val eastText = if (m.eastOffset > 0) "To East" else "To West"
        sheet.tvEastWest.text = String.format("%.2fm", kotlin.math.abs(m.eastOffset))
        sheet.lblEastWest.text = eastText
        sheet.imgEastWest.setImageResource(R.drawable.ic_arrow_right_so)
        sheet.imgEastWest.rotation = if (m.eastOffset > 0) 0f else 180f
        if (!m.inTolerance) {
            sheet.imgEastWest.setColorFilter(
                if (kotlin.math.abs(m.eastOffset) < 0.05) ContextCompat.getColor(
                    context,
                    R.color.stakeout_green
                ) else ContextCompat.getColor(context, R.color.stakeout_orange)
            )
        }

        // Update Direction Indicators - Card 3 (Cut/Fill)
        val cutFillText = if (m.verticalDistance > 0) "Fill" else "Cut"
        sheet.tvCutFill.text = String.format("%.2fm", kotlin.math.abs(m.verticalDistance))
        sheet.lblCutFill.text = cutFillText
        sheet.imgCutFill.rotation = if (m.verticalDistance > 0) 0f else 180f
        if (!m.inTolerance) {
            sheet.imgCutFill.setColorFilter(
                if (kotlin.math.abs(m.verticalDistance) < 0.05) ContextCompat.getColor(
                    context,
                    R.color.stakeout_green
                ) else ContextCompat.getColor(context, R.color.slate_gray)
            )
        }
        
        // --- PAGE 2: Polar Metrics ---
        // 1. Distance
        val dist = kotlin.math.hypot(m.northOffset, m.eastOffset)
        sheet.tvDistance.text = String.format("%.2fm", dist)
        
        // Rotate Distance Arrow to dominant axis (4-side logic)
        val distRotation = if (kotlin.math.abs(m.northOffset) > kotlin.math.abs(m.eastOffset)) {
             if (m.northOffset > 0) -90f else 90f // Up (N) or Down (S)
        } else {
             if (m.eastOffset > 0) 0f else 180f   // Right (E) or Left (W)
        }
        sheet.imgDistance.rotation = distRotation

        if (!m.inTolerance) {
            sheet.imgDistance.setColorFilter(ContextCompat.getColor(context, R.color.stakeout_orange))
        }
        
        // 2. Azimuth
        val bearing = Math.toDegrees(kotlin.math.atan2(m.eastOffset, m.northOffset))
        val azimuth = if (bearing < 0) bearing + 360 else bearing
        sheet.tvAzimuth.text = String.format("%.2f°", azimuth)
        // sheet.imgAzimuth.rotation = azimuth.toFloat() // Rotation removed as per user edit
        if (!m.inTolerance) {
            sheet.imgAzimuth.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary)) // Neutral color for compass? Or Orange? Defaulting to Grey.
        }
        
        // 3. Cut/Fill (Repeated)
        sheet.tvCutFill2.text = String.format("%.2fm", kotlin.math.abs(m.verticalDistance))
        sheet.lblCutFill2.text = cutFillText
        sheet.imgCutFill2.rotation = if (m.verticalDistance > 0) 0f else 180f
        if (!m.inTolerance) {
            sheet.imgCutFill2.setColorFilter(
                if (kotlin.math.abs(m.verticalDistance) < 0.05) ContextCompat.getColor(
                    context,
                    R.color.stakeout_green
                ) else ContextCompat.getColor(context, R.color.slate_gray)
            )
        }


        // In Tolerance View
        if (m.inTolerance) {
            sheet.layoutInTolerance.visibility = View.VISIBLE
        } else {
            sheet.layoutInTolerance.visibility = View.GONE
        }
    }

    fun updateConnectionLine(start: LatLng, end: LatLng) {
        val map = fragment.mapLibreMap ?: return
        fragment.connectionLineOverlay = MapLibrePolylineHelper.createPolyline(
            map,
            "connection_line",
            listOf(start, end),
            ContextCompat.getColor(fragment.requireContext(), R.color.stakeout_connection_line),
            1f,
            closed = false,
            dashed = true
        )
    }

    fun updateProximityCircle(target: LatLng, show: Boolean) {
        val map = fragment.mapLibreMap ?: return
        if (!show) {
            MapLibrePolylineHelper.removePolyline(map, "proximity_circle")
            return
        }

        // Generate circle points
        val points = mutableListOf<LatLng>()
        val radius = 0.4 / 111319.9 // approx conversion to degrees at equator, simple for now
        for (i in 0..360 step 10) {
            val angle = Math.toRadians(i.toDouble())
            points.add(LatLng(target.latitude + radius * kotlin.math.cos(angle), target.longitude + radius * kotlin.math.sin(angle)))
        }

        MapLibrePolylineHelper.createPolyline(
            map,
            "proximity_circle",
            points,
            ContextCompat.getColor(fragment.requireContext(), R.color.gray_overlay_50),
            2f,
            closed = true
        )
    }


    fun clearStakeoutMarkers() {
        val map = fragment.mapLibreMap ?: return
        MapLibrePolylineHelper.removePolyline(map, "connection_line")
        MapLibrePolylineHelper.removePolyline(map, "proximity_circle")
        fragment.connectionLineOverlay = null
        fragment.stakeoutProximityCircles.clear()
    }

    private var savedMapOrientation: Float = 0f
    private var hiddenOverlayIndices = mutableListOf<Int>()

    fun showBullseyeView() {
        val bullseyeView = fragment.binding.bullseyeView
        bullseyeView.visibility = View.VISIBLE

        // Center on the TARGET point
        val target = fragment.stakeoutSession?.let { it.targetPoints.getOrNull(it.currentIndex) }
        val currentZoom = fragment.mapLibreMap?.cameraPosition?.zoom ?: 15.0
        val targetZoom = if (currentZoom > 22.0) currentZoom else 22.0

        if (target != null) {
            val geo = LatLng(target.latitude, target.longitude, target.elevation)
            fragment.mapLibreMap?.moveCamera(CameraUpdateFactory.newLatLng(geo))

            if ((fragment.mapLibreMap?.cameraPosition?.zoom ?: 0.0) < 20.0) {
                fragment.mapLibreMap?.moveCamera(CameraUpdateFactory.zoomTo(22.0))
            }
        }

        fragment.isLockMode = true // Lock the map in bullseye

        // Lock Map Orientation to North Up (0) to prevent rotation confusion
        // Lock Map Orientation to North Up (0)
        savedMapOrientation = fragment.mapLibreMap?.cameraPosition?.bearing?.toFloat() ?: 0f
        fragment.mapLibreMap?.animateCamera(CameraUpdateFactory.bearingTo(0.0))

        // In MapLibre, we hide layers
        fragment.mapLibreMap?.style?.let { style ->
            style.layers.forEach { layer ->
                layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.NONE))
            }
        }

        // Hide map buttons
        fragment.binding.llMapsButtons.visibility = View.GONE

        // Stop sensor updates (Compass) to prevent rotation
        fragment.sensorEventListener?.let {
            fragment.sensorManager?.unregisterListener(it)
        }

        // Stop location smoothing (Map Centering) to prevent jitter/fighting
        fragment.locationUpdateHandler.removeCallbacks(fragment.locationSmoothingRunnable)
        fragment.isAnimatingLocation = false // Important to reset this flag

        // Disable Rotation Gesture
        fragment.mapLibreMap?.uiSettings?.isRotateGesturesEnabled = false

        // Disable manual map interaction while in precision mode
        fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(false)
    }

    fun hideBullseyeView() {
        fragment.binding.bullseyeView.visibility = View.GONE
        fragment.isLockMode = false // Unlock the map

        // Restore Map Orientation
        if (fragment.isInBullseyeMode) {
            fragment.mapLibreMap?.animateCamera(CameraUpdateFactory.bearingTo(savedMapOrientation.toDouble()))
        }

        // Restore map buttons
        fragment.binding.llMapsButtons.visibility = View.VISIBLE

        // Restore visibility of layers
        fragment.mapLibreMap?.style?.let { style ->
            style.layers.forEach { layer ->
                layer.setProperties(org.maplibre.android.style.layers.PropertyFactory.visibility(org.maplibre.android.style.layers.Property.VISIBLE))
            }
        }

        // Restore Sensor Logic (Compass)
        fragment.orientationSensor?.let { sensor ->
            fragment.sensorEventListener?.let { listener ->
                fragment.sensorManager?.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
        }

        // re-enable rotation gesture
        fragment.mapLibreMap?.uiSettings?.isRotateGesturesEnabled = true

        // Re-enable manual map interaction
        fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(true)
    }
}
