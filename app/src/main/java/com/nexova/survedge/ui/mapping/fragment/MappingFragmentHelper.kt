package com.nexova.survedge.ui.mapping.fragment

import android.graphics.Color
import android.hardware.SensorManager
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.nexova.survedge.R
import com.nexova.survedge.ui.mapping.maplibre.OsmdroidPolylineHelper
import com.nexova.survedge.ui.mapping.overlay.BullseyeOverlay
import com.nexova.survedge.ui.mapping.overlay.DashedPolylineOverlay
import com.nexova.survedge.ui.mapping.overlay.RotationGestureOverlay
import com.nexova.survedge.ui.stakeout.model.StakeoutMeasurement
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint

class MappingFragmentHelper(private val fragment: MappingFragment) {

    fun initializeMap() {
        fragment.binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        fragment.binding.mapView.setMultiTouchControls(true)
        fragment.binding.mapView.minZoomLevel = 2.0
        fragment.binding.mapView.maxZoomLevel = 25.0
        fragment.binding.mapView.isHorizontalMapRepetitionEnabled = true
        fragment.binding.mapView.isVerticalMapRepetitionEnabled = false
        fragment.binding.mapView.isTilesScaledToDpi = true
        fragment.binding.mapView.setBuiltInZoomControls(false)
        fragment.binding.mapView.setUseDataConnection(true)

        fragment.binding.mapView.setScrollableAreaLimitDouble(
            BoundingBox(85.0, 180.0, -85.0, -180.0)
        )

        fragment.binding.mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                fragment.isMapFitted = false
                fragment.logic.updateCompassRotation()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                fragment.isMapFitted = false
                event?.let {
                    val currentZoom = it.zoomLevel
                    val maxZoom = fragment.binding.mapView.maxZoomLevel
                    val isNearMaxZoom = currentZoom >= maxZoom - 0.1
                    val zoomChanged = kotlin.math.abs(currentZoom - fragment.lastZoomLevel) > 0.5

                    if ((zoomChanged || isNearMaxZoom) && fragment.collectedLabeledPoints.isNotEmpty()) {
                        fragment.lastZoomLevel = currentZoom
                        fragment.binding.mapView.post { fragment.logic.updateMarkersForZoom() }
                    }
                }
                fragment.logic.updateCompassRotation()
                return false
            }
        })

        fragment.mapController = fragment.binding.mapView.controller
        fragment.mapController?.setZoom(15.0)
        fragment.lastZoomLevel = 15.0

        fragment.rotationGestureOverlay = RotationGestureOverlay(fragment.binding.mapView)
        fragment.binding.mapView.overlays.add(0, fragment.rotationGestureOverlay)

        fragment.logic.createLocationPin()
    }

    fun setupZoomControls() {
        fragment.binding.imgZoomIn.setOnClickListener {
            val currentZoom = fragment.binding.mapView.zoomLevelDouble
            if (currentZoom < fragment.binding.mapView.maxZoomLevel) {
                fragment.logic.animateZoom(
                    currentZoom,
                    kotlin.math.min(currentZoom + 1.0, fragment.binding.mapView.maxZoomLevel)
                )
            }
        }
        fragment.binding.imgZoomOut.setOnClickListener {
            val currentZoom = fragment.binding.mapView.zoomLevelDouble
            if (currentZoom > fragment.binding.mapView.minZoomLevel) {
                fragment.logic.animateZoom(
                    currentZoom,
                    kotlin.math.max(currentZoom - 1.0, fragment.binding.mapView.minZoomLevel)
                )
            }
        }
    }


    // ---------------------------------------------------------------------
    // Stakeout UI Helpers
    // ---------------------------------------------------------------------

    fun setupStakeoutUI() {
        val sheet = fragment.binding.stakeoutBottomSheet

        sheet.btnCloseStakeout.setOnClickListener {
            fragment.logic.stopStakeoutSession()
            hideStakeoutUI()
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

    fun showStakeoutUI() {
        fragment.logic.hideBottomNavigation {
            val root = fragment.binding.stakeoutBottomSheet.root
            root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            root.requestLayout()
            root.visibility = View.VISIBLE
            // Hide the main Collect FAB to prevent overlap and interaction
            fragment.binding.btnCollect.visibility = View.GONE
            
            fragment.logic.animateSheetTransition(null, root, MappingFragmentLogic.BottomSheetTransition.SLIDE_UP)
            fragment.logic.adjustMapsButtonsForBottomSheet(root.height)
            
            // Draw connection line immediately after UI is ready
            drawInitialConnectionLine()
            
            fragment.logic.setupSwipeToDismiss(root) {
                fragment.logic.stopStakeoutSession()
                hideStakeoutUI()
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
        val targetGeo = GeoPoint(currentTarget.latitude, currentTarget.longitude, currentTarget.elevation)
        
        // Get current location from various sources
        val currentGeo = fragment.currentLocation 
            ?: fragment.locationMarker?.position
        
        if (currentGeo != null) {
            // Draw connection line immediately
            updateConnectionLine(currentGeo, targetGeo)
            
            // Calculate distance for proximity circle
            val dist = currentGeo.distanceToAsDouble(targetGeo)
            updateProximityCircle(targetGeo, dist < 10.0)
        } else {
            // No location available yet, try to get last known location
            try {
                fragment.fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
                    if (location != null) {
                        val geoPoint = GeoPoint(location.latitude, location.longitude, location.altitude)
                        fragment.currentLocation = geoPoint
                        updateConnectionLine(geoPoint, targetGeo)
                        val dist = geoPoint.distanceToAsDouble(targetGeo)
                        updateProximityCircle(targetGeo, dist < 10.0)
                    }
                }
            } catch (e: SecurityException) {
                // Location permission not granted
            }
        }
    }

    fun hideStakeoutUI(showNav: Boolean = true) {
        val root = fragment.binding.stakeoutBottomSheet.root
        fragment.logic.animateSheetTransition(root, null, MappingFragmentLogic.BottomSheetTransition.SLIDE_DOWN) {
            fragment.logic.adjustMapsButtonsForBottomSheet()
            // Restore the main Collect FAB
            fragment.binding.btnCollect.visibility = View.VISIBLE
            if (showNav) {
                fragment.logic.restoreStateAfterClosingInfoSheet()
            } else {
                // Transitioning to an info sheet, hide visuals
                clearStakeoutMarkers()
                if (fragment.isInBullseyeMode) hideBullseyeView()
            }
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

    fun updateConnectionLine(start: GeoPoint, end: GeoPoint) {
        if (fragment.connectionLineOverlay == null) {
            fragment.connectionLineOverlay = OsmdroidPolylineHelper.createPolyline(
                fragment.binding.mapView,
                listOf(start, end),
                ContextCompat.getColor(fragment.requireContext(), R.color.stakeout_connection_line),
                6f,
                closed = false,
                dashed = true
            )
        } else {
            val overlay = fragment.connectionLineOverlay
            if (overlay is org.osmdroid.views.overlay.Polyline) {
                overlay.setPoints(listOf(start, end))
            } else if (overlay is DashedPolylineOverlay) {
                overlay.setPoints(listOf(start, end))
            }
        }
        fragment.binding.mapView.invalidate()
    }

    fun updateProximityCircle(target: GeoPoint, show: Boolean) {
        val mapView = fragment.binding.mapView
        if (!show) {
            fragment.stakeoutProximityCircles.forEach {
                if (it is org.osmdroid.views.overlay.Polygon) {
                    mapView.overlays.remove(it)
                }
            }
            fragment.stakeoutProximityCircles.clear()
            mapView.invalidate()
            return
        }

        val radiuses = listOf(0.4) // Only 0.4m ring

        if (fragment.stakeoutProximityCircles.isEmpty()) {
            radiuses.forEach { radius ->
                val circle = org.osmdroid.views.overlay.Polygon(mapView)
                circle.points = org.osmdroid.views.overlay.Polygon.pointsAsCircle(target, radius)
                circle.fillPaint.color = ContextCompat.getColor(fragment.requireContext(), R.color.gray_overlay_50) // 50% transparency grey
                circle.outlinePaint.color = Color.TRANSPARENT // No outline
                fragment.stakeoutProximityCircles.add(circle)
                mapView.overlays.add(0, circle)
            }
        } else {
            // Clean up if we had more rings before
            if (fragment.stakeoutProximityCircles.size > radiuses.size) {
                for (i in fragment.stakeoutProximityCircles.size - 1 downTo radiuses.size) {
                    val overlay = fragment.stakeoutProximityCircles.removeAt(i)
                    if (overlay is org.osmdroid.views.overlay.Polygon) {
                        mapView.overlays.remove(overlay)
                    }
                }
            }

            fragment.stakeoutProximityCircles.filterIsInstance<org.osmdroid.views.overlay.Polygon>()
                .forEachIndexed { index, circle ->
                    radiuses.getOrNull(index)?.let { radius ->
                        circle.points =
                            org.osmdroid.views.overlay.Polygon.pointsAsCircle(target, radius)
                    }
                }
        }
        mapView.invalidate()
    }


    fun clearStakeoutMarkers() {
        fragment.connectionLineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(fragment.binding.mapView, it)
        }
        fragment.connectionLineOverlay = null

        fragment.stakeoutProximityCircles.forEach {
            if (it is org.osmdroid.views.overlay.Polygon) {
                fragment.binding.mapView.overlays.remove(it)
            }
        }
        fragment.stakeoutProximityCircles.clear()
    }

    private var savedMapOrientation: Float = 0f
    private var hiddenOverlayIndices = mutableListOf<Int>()

    fun showBullseyeView() {
        if (fragment.bullseyeOverlay == null) {
            fragment.bullseyeOverlay = BullseyeOverlay(fragment.requireContext())
            fragment.binding.mapView.overlays.add(fragment.bullseyeOverlay)
        }

        // Center on the TARGET point
        val target = fragment.stakeoutSession?.let { it.targetPoints.getOrNull(it.currentIndex) }
        val currentZoom = fragment.binding.mapView.zoomLevelDouble
        val targetZoom = if (currentZoom > 22.0) currentZoom else 22.0

        if (target != null) {
            val geo = GeoPoint(target.latitude, target.longitude, target.elevation)
            fragment.mapController?.setCenter(geo)

            if (fragment.binding.mapView.zoomLevelDouble < 20.0) {
                fragment.mapController?.setZoom(22.0)
            }
        }

        fragment.isLockMode = true // Lock the map in bullseye

        // Lock Map Orientation to North Up (0) to prevent rotation confusion
        savedMapOrientation = fragment.binding.mapView.mapOrientation
        fragment.binding.mapView.mapOrientation = 0f

        // Hide ALL other overlays to prevent clutter (markers, lines, etc.)
        hiddenOverlayIndices.clear()
        fragment.binding.mapView.overlays.forEachIndexed { index, overlay ->
            if (overlay != fragment.bullseyeOverlay && overlay.isEnabled) {
                overlay.isEnabled = false
                hiddenOverlayIndices.add(index)
            }
        }

        // Explicitly default collected points to hidden (safety net)
        fragment.collectedPointMarkers.forEach { it.isEnabled = false }
        fragment.logic.updateMarkersForZoom() // Will act as a "Clear" or "Don't Draw" due to Logic patch

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
        fragment.rotationGestureOverlay?.isEnabled = false

        // Disable manual map interaction while in precision mode
        fragment.binding.mapView.setMultiTouchControls(false)
        fragment.binding.mapView.invalidate()
    }

    fun hideBullseyeView() {
        fragment.bullseyeOverlay?.let {
            fragment.binding.mapView.overlays.remove(it)
            fragment.bullseyeOverlay = null
        }
        fragment.isLockMode = false // Unlock the map

        // Restore Map Orientation ONLY if we were in Bullseye mode (which locks it)
        if (fragment.isInBullseyeMode) {
            fragment.binding.mapView.mapOrientation = savedMapOrientation
        }

        // Restore map buttons
        fragment.binding.llMapsButtons.visibility = View.VISIBLE

        // Restore visibility of previously hidden overlays
        hiddenOverlayIndices.forEach { index ->
            if (index < fragment.binding.mapView.overlays.size) {
                fragment.binding.mapView.overlays[index].isEnabled = true
            }
        }
        hiddenOverlayIndices.clear()

        // Explicitly enable standard markers if they were missed logic (safety net)
        fragment.locationMarker?.isEnabled = true
        fragment.connectionLineOverlay?.let {
            if (it is org.osmdroid.views.overlay.Polyline) it.isEnabled = true
            else if (it is DashedPolylineOverlay) it.isEnabled = true
        }
        fragment.stakeoutProximityCircles.forEach {
            if (it is org.osmdroid.views.overlay.Polygon) it.isEnabled = true
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
        fragment.rotationGestureOverlay?.isEnabled = true

        // Re-enable manual map interaction
        fragment.binding.mapView.setMultiTouchControls(true)
        fragment.binding.mapView.invalidate()
    }
}
