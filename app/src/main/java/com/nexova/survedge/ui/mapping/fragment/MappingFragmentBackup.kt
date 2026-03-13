package com.nexova.survedge.ui.mapping.fragment

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.nexova.survedge.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nexova.survedge.databinding.BottomSheetCollectPointBinding
import com.nexova.survedge.databinding.BottomSheetConfirmDialogBinding
import com.nexova.survedge.databinding.BottomSheetEditLineBinding
import com.nexova.survedge.databinding.BottomSheetLineSegmentBinding
import com.nexova.survedge.databinding.FragmentMappingBinding
import com.nexova.survedge.ui.mapping.adapter.CodeAdapter
import com.nexova.survedge.ui.mapping.adapter.CodeItem
import com.nexova.survedge.ui.mapping.adapter.EditPointAdapter
import com.nexova.survedge.ui.mapping.adapter.IndicatorType
import com.nexova.survedge.ui.mapping.adapter.ObjectListAdapter
import com.nexova.survedge.ui.mapping.adapter.ObjectListItem
import com.nexova.survedge.ui.mapping.drawable.CustomLocationPinDrawable
import com.nexova.survedge.ui.mapping.maplibre.OsmdroidMarkerHelper
import com.nexova.survedge.ui.mapping.maplibre.OsmdroidPolylineHelper
import com.nexova.survedge.ui.mapping.overlay.ClickablePolylineOverlay
import com.nexova.survedge.ui.mapping.overlay.DoubleTapInterceptorOverlay
import com.nexova.survedge.ui.mapping.overlay.LabeledPoint
import com.nexova.survedge.ui.mapping.overlay.PointClickHandlerOverlay
import com.nexova.survedge.ui.mapping.overlay.RotationGestureOverlay
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.api.IMapController
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.util.LinkedList

class MappingFragmentBackup : Fragment() {

    lateinit var binding: FragmentMappingBinding
    private var mapController: IMapController? = null
    private var locationMarker: Marker? = null
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var isFirstLocationUpdate = true
    private var sensorManager: SensorManager? = null
    private var orientationSensor: Sensor? = null
    private var sensorEventListener: SensorEventListener? = null
    private var currentHeading: Float = 0f
    private var currentLocation: GeoPoint? = null
    private var rotationGestureOverlay: RotationGestureOverlay? = null
    private var doubleTapInterceptorOverlay: DoubleTapInterceptorOverlay? = null
    private var pointClickHandlerOverlay: PointClickHandlerOverlay? = null
    private val collectedPointMarkers = mutableListOf<Marker>()
    private val markerToPointMap = mutableMapOf<Marker, LabeledPoint>()
    private var polylineOverlay: Any? = null
    private var liveTrackingLineOverlay: Any? = null
    private var pointCounter = 1
    private var pointIdPrefix: String? = null
    private var pointIdNumericCounter = 1
    private var collectedLabeledPoints = LinkedList<LabeledPoint>()
    private var lastZoomLevel: Double = 0.0
    private var selectedPointCodeId: String = ""
    private var selectedPointIndicatorType: IndicatorType = IndicatorType.POINT
    private var isShapeClosed: Boolean = false
    private var lineSegmentStartIndex: Int = 0
    private var addFromBeginning: Boolean = false
    private var hasStartedNewLine: Boolean = false
    private var wasCollectingBeforePointDetails: Boolean = false
    private val completedLineOverlays = mutableListOf<Any>()
    private var currentLineCodeId: String? = null
    private var highlightedLineOverlay: ClickablePolylineOverlay? = null
    private var isSelectingPointForEditLine: Boolean = false
    private var pendingEditLineSegment: ClickablePolylineOverlay? = null
    private val lineCodeSequenceCounters = mutableMapOf<String, Int>()
    private var currentEditLineAdapter: EditPointAdapter? = null
    private var currentEditLineBinding: BottomSheetEditLineBinding? = null
    private var confirmDialogBottomSheet: BottomSheetDialog? = null
    private var selectedPoint: LabeledPoint? = null

    companion object {
        private const val PREFS_NAME = "survedge_prefs"
        private const val KEY_CUSTOM_CODES = "custom_codes"
    }

    private val labelOverlapDistanceX = 35
    private val labelOverlapDistanceY = 25
    private var targetLocation: GeoPoint? = null
    private var isAnimatingLocation = false
    private var currentMapAnimator: ValueAnimator? = null
    private var isMapFitted = false
    private val locationUpdateHandler = android.os.Handler(Looper.getMainLooper())
    private val locationSmoothingRunnable = object : Runnable {
        override fun run() {
            smoothMoveToTarget()
            if (isAnimatingLocation) {
                locationUpdateHandler.postDelayed(this, 16)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMappingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.bottomSheetCollectPoint.root.isClickable = false
        binding.bottomSheetCollectPoint.root.isFocusable = false
        binding.bottomSheetLineSegment.root.isClickable = false
        binding.bottomSheetLineSegment.root.isFocusable = false
        binding.bottomSheetEditLine.root.isClickable = false
        binding.bottomSheetEditLine.root.isFocusable = false
        binding.bottomSheetNewPoint.root.isClickable = false
        binding.bottomSheetNewPoint.root.isFocusable = false
        binding.bottomSheetSelectCode.root.isClickable = false

        binding.bottomSheetObjectList.root.isFocusable = false
        setupEdgeToEdgeInsets()
        initializeMap()
        setupZoomControls()
        setupCompassButton()
        setupCenterButton()
        setupCollectButton()
        setupResizeButton()
        setupMenuButton()
        setupLocationTracking()
        setupCompassOrientation()
        preventDoubleTapZoomOnNonMapViews()
        setupPointClickHandler()
    }

    private fun setupEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top

            binding.imgBack.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = statusBarHeight + resources.getDimensionPixelSize(
                    resources.getIdentifier("_10sdp", "dimen", requireContext().packageName)
                )
            }

            binding.imgMenu.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = statusBarHeight + resources.getDimensionPixelSize(
                    resources.getIdentifier("_10sdp", "dimen", requireContext().packageName)
                )
            }

            insets
        }
    }

    private fun initializeMap() {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.minZoomLevel = 2.0
        binding.mapView.maxZoomLevel = 25.0
        binding.mapView.isHorizontalMapRepetitionEnabled = true
        binding.mapView.isVerticalMapRepetitionEnabled = false
        binding.mapView.isTilesScaledToDpi = true
        binding.mapView.setBuiltInZoomControls(false)
        binding.mapView.setUseDataConnection(true)

        binding.mapView.setScrollableAreaLimitDouble(
            org.osmdroid.util.BoundingBox(85.0, 180.0, -85.0, -180.0)
        )

        binding.mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                isMapFitted = false
                updateCompassRotation()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                isMapFitted = false
                event?.let {
                    val currentZoom = it.zoomLevel
                    val maxZoom = binding.mapView.maxZoomLevel
                    val isNearMaxZoom = currentZoom >= maxZoom - 0.1
                    val zoomChanged = Math.abs(currentZoom - lastZoomLevel) > 0.5

                    if ((zoomChanged || isNearMaxZoom) && collectedLabeledPoints.isNotEmpty()) {
                        lastZoomLevel = currentZoom
                        binding.mapView.post { updateMarkersForZoom() }
                    }
                }
                updateCompassRotation()
                return false
            }
        })

        mapController = binding.mapView.controller
        mapController?.setZoom(15.0)
        lastZoomLevel = 15.0

        rotationGestureOverlay = RotationGestureOverlay(binding.mapView)
        binding.mapView.overlays.add(0, rotationGestureOverlay)

        createLocationPin()
    }

    private fun setupZoomControls() {
        binding.imgZoomIn.setOnClickListener {
            val currentZoom = binding.mapView.zoomLevelDouble
            if (currentZoom < binding.mapView.maxZoomLevel) {
                animateZoom(currentZoom, minOf(currentZoom + 1.0, binding.mapView.maxZoomLevel))
            }
        }

        binding.imgZoomOut.setOnClickListener {
            val currentZoom = binding.mapView.zoomLevelDouble
            if (currentZoom > binding.mapView.minZoomLevel) {
                animateZoom(currentZoom, maxOf(currentZoom - 1.0, binding.mapView.minZoomLevel))
            }
        }
    }

    private fun animateZoom(fromZoom: Double, toZoom: Double) {
        currentMapAnimator?.cancel()

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 150
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val fraction = animation.animatedValue as Float
            val currentZoom = fromZoom + (toZoom - fromZoom) * fraction
            binding.mapView.controller.setZoom(currentZoom)
        }
        currentMapAnimator = animator
        animator.start()
    }

    private fun setupCompassButton() {
        binding.imgCompass.setOnClickListener {
            animateRotationTo(0f)
        }
    }

    private fun updateCompassRotation() {
        val mapRotation = binding.mapView.mapOrientation
        binding.imgCompass.rotation = mapRotation
    }

    private fun animateRotationTo(targetAngle: Float) {
        val currentAngle = binding.mapView.mapOrientation
        val startAngle = currentAngle
        val endAngle = targetAngle

        var angleDiff = endAngle - startAngle
        while (angleDiff > 180) angleDiff -= 360
        while (angleDiff < -180) angleDiff += 360
        val finalAngle = startAngle + angleDiff

        val animator = ValueAnimator.ofFloat(startAngle, finalAngle)
        animator.duration = 500
        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Float
            binding.mapView.setMapOrientation(animatedValue)
            binding.imgCompass.rotation = animatedValue
        }
        animator.start()
        rotationGestureOverlay?.resetRotation()
    }

    private fun setupCenterButton() {
        binding.imgCenter.setOnClickListener {
            val targetLocation = currentLocation ?: locationMarker?.position
            targetLocation?.let { location ->
                if (collectedLabeledPoints.isEmpty()) {
                    val currentRotation = binding.mapView.mapOrientation
                    val padding = 240
                    val offset = 0.0001
                    val boundingBox = org.osmdroid.util.BoundingBox(
                        location.latitude + offset,
                        location.longitude + offset,
                        location.latitude - offset,
                        location.longitude - offset
                    )
                    cancelOngoingAnimations()
                    binding.mapView.post {
                        binding.mapView.zoomToBoundingBox(
                            boundingBox,
                            true,
                            padding,
                            binding.mapView.maxZoomLevel,
                            400L
                        )
                        binding.mapView.postDelayed({
                            binding.mapView.setMapOrientation(currentRotation)
                            binding.imgCompass.rotation = currentRotation
                        }, 50)
                    }
                } else {
                    animateToLocationWithZoom(location, binding.mapView.zoomLevelDouble)
                }
            }
        }
    }

    private fun cancelOngoingAnimations() {
        currentMapAnimator?.cancel()
        currentMapAnimator = null

        isAnimatingLocation = false
        locationUpdateHandler.removeCallbacks(locationSmoothingRunnable)

        binding.mapView.controller.stopAnimation(false)
    }

    private fun animateToLocationWithZoom(targetLocation: GeoPoint, targetZoom: Double) {
        cancelOngoingAnimations()

        val startLat = binding.mapView.mapCenter.latitude
        val startLon = binding.mapView.mapCenter.longitude
        val startZoom = binding.mapView.zoomLevelDouble

        val endLat = targetLocation.latitude
        val endLon = targetLocation.longitude
        val endZoom = targetZoom

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 400
        animator.interpolator = android.view.animation.DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val fraction = animation.animatedValue as Float

            val currentLat = startLat + (endLat - startLat) * fraction
            val currentLon = startLon + (endLon - startLon) * fraction
            val currentZoom = startZoom + (endZoom - startZoom) * fraction

            binding.mapView.controller.setCenter(GeoPoint(currentLat, currentLon))
            binding.mapView.controller.setZoom(currentZoom)
        }
        currentMapAnimator = animator
        animator.start()
    }

    private fun hideBottomNavigation() {
        (activity as? com.nexova.survedge.ui.main.activity.MainActivity)?.binding?.bottomNavigationView?.let { bottomNav ->
            // Cancel any ongoing animations
            bottomNav.animate().cancel()

            // Save current padding
            val currentPaddingBottom = bottomNav.paddingBottom

            // Immediately hide without state checks
            bottomNav.visibility = View.VISIBLE // Ensure it's visible first so animation works
            bottomNav.animate()
                .translationY(bottomNav.height.toFloat())
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    bottomNav.visibility = View.GONE
                    // Restore padding after animation
                    bottomNav.setPadding(0, 0, 0, currentPaddingBottom)
                }
                .start()
        }
    }

    private fun showBottomNavigation() {
        (activity as? com.nexova.survedge.ui.main.activity.MainActivity)?.binding?.bottomNavigationView?.let { bottomNav ->
            if (bottomNav.visibility != View.VISIBLE) {
                val currentPaddingBottom = bottomNav.paddingBottom
                bottomNav.visibility = View.VISIBLE
                bottomNav.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(300)
                    .withEndAction {
                        // Ensure padding is preserved after animation
                        bottomNav.setPadding(0, 0, 0, currentPaddingBottom)
                    }
                    .start()
            }
        }
    }

    private fun setupCollectButton() {
        binding.btnCollect.setOnClickListener {
            showCollectPointBottomSheet()
        }
    }

    private fun showCollectPointBottomSheet() {
        val bottomSheetView = binding.bottomSheetCollectPoint.root
        val sheetBinding = binding.bottomSheetCollectPoint

        // Hide bottom navigation when bottom sheet is shown
        hideBottomNavigation()

        bottomSheetView.elevation = 10f * resources.displayMetrics.density
        bottomSheetView.translationZ = 10f * resources.displayMetrics.density

        bottomSheetView.isClickable = true
        bottomSheetView.isFocusable = true

        sheetBinding.llDataCollectionSettings.visibility = View.GONE

        sheetBinding.llButtonContainer.isClickable = true
        sheetBinding.llButtonContainer.isFocusable = true
        sheetBinding.llButtonContainer.isPressed = false
        sheetBinding.llButtonContainer.isSelected = false
        sheetBinding.llButtonContainer.jumpDrawablesToCurrentState()

        bottomSheetView.visibility = View.VISIBLE
        bottomSheetView.alpha = 0f

        bottomSheetView.post {
            val height = bottomSheetView.height
            bottomSheetView.translationY = height.toFloat()
            bottomSheetView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start()
        }

        setupSwipeGestureForDataCollectionSettings(bottomSheetView, sheetBinding)

        // Check if LINE code already has collected points/segments and increment if needed
        // This ensures that when reopening the bottom sheet, if a line ID already exists,
        // it will be incremented to start a new segment
        if (selectedPointIndicatorType == IndicatorType.LINE &&
            isLineCodeFromCodeId(selectedPointCodeId) &&
            currentLineCodeId == null) { // Only increment if not currently collecting a segment

            // Check if there are completed line segments with this codeId
            val hasCompletedSegments = completedLineOverlays.any { overlay ->
                overlay is ClickablePolylineOverlay && overlay.codeId == selectedPointCodeId
            }

            // Check if there are collected points with this codeId (that are not part of current segment)
            val hasCollectedPoints = collectedLabeledPoints.any { point ->
                isLineCodeFromCodeId(point.codeId) && point.codeId == selectedPointCodeId
            }

            // If there are existing segments or points, increment the line code
            if (hasCompletedSegments || hasCollectedPoints) {
                advanceLineCodeForNewSegment(sheetBinding)
            }
        }

        sheetBinding.btnCloseCollectPoint.setOnClickListener {
            sheetBinding.clLineMenu.visibility = View.GONE
            // Use getAllPointsInCurrentLineSegment to count ALL points in the segment,
            // including points before lineSegmentStartIndex when continuing collection
            val allLinePoints = if (selectedPointIndicatorType == IndicatorType.LINE &&
                currentLineCodeId != null) {
                getAllPointsInCurrentLineSegment()
            } else {
                getConsecutiveLineCodePoints()
            }

            // If we have 2 or more points, just finalize the line segment
            if (selectedPointIndicatorType == IndicatorType.LINE &&
                currentLineCodeId != null &&
                allLinePoints.size >= 2) {
                // Finalize the current line segment with existing points
                hideCollectPointBottomSheet(shouldStartNewSegment = false)
                return@setOnClickListener
            }

            // Only ask for confirmation if there's exactly 1 point (which would be invalid for a line)
            val shouldAskConfirm =
                selectedPointIndicatorType == IndicatorType.LINE &&
                        currentLineCodeId != null &&
                        allLinePoints.size == 1

            if (shouldAskConfirm) {
                showConfirmDialogBottomSheet(
                    onYesClick = {
                        // Only delete if there's actually only 1 point in the entire segment
                        // Check again to make sure we're not deleting points from a valid segment
                        val currentAllPoints = getAllPointsInCurrentLineSegment()
                        if (currentAllPoints.size == 1 && collectedLabeledPoints.isNotEmpty() &&
                            isLineCodeFromCodeId(collectedLabeledPoints.last().codeId)
                        ) {
                            collectedLabeledPoints.removeAt(collectedLabeledPoints.size - 1)
                            val markersToRemove = markerToPointMap.entries.filter { (_, point) ->
                                point.codeId == currentLineCodeId
                            }
                            markersToRemove.forEach { (marker, _) ->
                                binding.mapView.overlays.remove(marker)
                                collectedPointMarkers.remove(marker)
                                markerToPointMap.remove(marker)
                            }
                            polylineOverlay?.let {
                                OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
                                polylineOverlay = null
                            }
                            liveTrackingLineOverlay?.let {
                                OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
                                liveTrackingLineOverlay = null
                            }
                            lineSegmentStartIndex = collectedLabeledPoints.size
                            currentLineCodeId = null
                            updateMarkersForZoom()
                            binding.mapView.invalidate()
                        }
                        hideCollectPointBottomSheet(shouldStartNewSegment = true)
                    },
                    onNoClick = {
                        // onNoClick handles "Continue Collecting" (or Cancel closing).
                        // Do nothing here, so the dialog just dismisses and we stay in collection mode.
                    }
                )
            } else {
                hideCollectPointBottomSheet(shouldStartNewSegment = true)
            }
        }

        val pointId = if (pointIdPrefix != null) {
            "$pointIdPrefix$pointIdNumericCounter"
        } else {
            pointCounter.toString()
        }
        sheetBinding.etPointId.setText(pointId)
        sheetBinding.etPointId.setHint(pointId)


        var selectedCodeId = selectedPointCodeId
        var currentIndicatorType = selectedPointIndicatorType

        updatePointTypeIndicator(sheetBinding.viewTypeDot, selectedPointIndicatorType)
        sheetBinding.tvPointType.text = selectedPointCodeId.ifEmpty { "" }
// Initial visibility will be set by updateCloseShapeVisibility

        if (currentIndicatorType == IndicatorType.LINE && collectedLabeledPoints.isNotEmpty() && !hasStartedNewLine) {
            val lastPoint = collectedLabeledPoints.last()
            if (lastPoint.codeId == selectedCodeId && isLineCodeFromCodeId(selectedCodeId)) {
                currentLineCodeId = selectedCodeId
                var startIndex = collectedLabeledPoints.size
                for (i in collectedLabeledPoints.size - 1 downTo 0) {
                    val point = collectedLabeledPoints[i]
                    if (point.codeId == selectedCodeId && isLineCodeFromCodeId(point.codeId)) {
                        startIndex = i
                    } else {
                        break
                    }
                }
                lineSegmentStartIndex = startIndex
                redrawPolyline()
            }
        }
        val updateCloseShapeVisibility: () -> Unit = {
            val lineCodePoints = if (currentIndicatorType == IndicatorType.LINE && currentLineCodeId != null) {
                getAllPointsInCurrentLineSegment()
            } else {
                emptyList()
            }
            val hasEnoughLinePoints = currentIndicatorType == IndicatorType.LINE &&
                    lineCodePoints.size >= 3
            sheetBinding.llCloseShape.visibility =
                if (hasEnoughLinePoints) View.VISIBLE else View.GONE

            // Disable "From Other End" if we don't have enough points (need at least 2 to have "ends")
            val hasEnoughPointsForOtherEnd = currentIndicatorType == IndicatorType.LINE &&
                    lineCodePoints.size >= 2
            sheetBinding.llFromOtherSide.isEnabled = hasEnoughPointsForOtherEnd
            sheetBinding.llFromOtherSide.alpha = if (hasEnoughPointsForOtherEnd) 1.0f else 0.5f

            updateLineMenuVisibility(sheetBinding.btnLineMenu, currentIndicatorType)
        }
        updateCloseShapeVisibility()

        sheetBinding.llPointTypeSelector.setOnClickListener {
            sheetBinding.clLineMenu.visibility = View.GONE
            showSelectCodeBottomSheet(sheetBinding) { codeId, indicatorType ->
                val applyCodeChange = {
                    if (currentIndicatorType == IndicatorType.LINE &&
                        (indicatorType != IndicatorType.LINE || currentLineCodeId != codeId)) {
                        finalizeCurrentLineSegment(closeFlag = isShapeClosed)
                    }

                    selectedCodeId = codeId
                    selectedPointCodeId = codeId
                    selectedPointIndicatorType = indicatorType
                    currentIndicatorType = indicatorType
                    updatePointTypeIndicator(sheetBinding.viewTypeDot, indicatorType)
                    sheetBinding.tvPointType.text = codeId
// updateLineMenuVisibility will be handled by updateCloseShapeVisibility below
                    if (indicatorType == IndicatorType.LINE) {
                        trackLineCodeUsage(codeId)
                    }

                    if (indicatorType == IndicatorType.LINE && collectedLabeledPoints.isNotEmpty() && !hasStartedNewLine) {
                        val lastPoint = collectedLabeledPoints.last()
                        if (lastPoint.codeId == codeId && isLineCodeFromCodeId(codeId)) {
                            currentLineCodeId = codeId
                            var startIndex = collectedLabeledPoints.size
                            for (i in collectedLabeledPoints.size - 1 downTo 0) {
                                val point = collectedLabeledPoints[i]
                                if (point.codeId == codeId && isLineCodeFromCodeId(point.codeId)) {
                                    startIndex = i
                                } else {
                                    break
                                }
                            }
                            lineSegmentStartIndex = startIndex
                            redrawPolyline()
                        }
                    }

                    updateCloseShapeVisibility()
                }

                val currentPoints = if (currentIndicatorType == IndicatorType.LINE && currentLineCodeId != null) {
                    getAllPointsInCurrentLineSegment()
                } else {
                    emptyList()
                }

                if (currentIndicatorType == IndicatorType.LINE && currentLineCodeId != null &&
                    currentPoints.size == 1 && (indicatorType != IndicatorType.LINE || currentLineCodeId != codeId)) {

                    showConfirmDialogBottomSheet(
                        onYesClick = {
                            if (collectedLabeledPoints.isNotEmpty() &&
                                isLineCodeFromCodeId(collectedLabeledPoints.last().codeId)) {
                                collectedLabeledPoints.removeAt(collectedLabeledPoints.size - 1)
                                val markersToRemove = markerToPointMap.entries.filter { (_, point) ->
                                    point.codeId == currentLineCodeId
                                }
                                markersToRemove.forEach { (marker, _) ->
                                    binding.mapView.overlays.remove(marker)
                                    collectedPointMarkers.remove(marker)
                                    markerToPointMap.remove(marker)
                                }
                                polylineOverlay?.let {
                                    OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
                                    polylineOverlay = null
                                }
                                liveTrackingLineOverlay?.let {
                                    OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
                                    liveTrackingLineOverlay = null
                                }
                                lineSegmentStartIndex = collectedLabeledPoints.size
                                currentLineCodeId = null
                                updateMarkersForZoom()
                                binding.mapView.invalidate()
                            }
                            applyCodeChange()
                        },
                        onNoClick = {
                            // Do nothing, cancel change
                        }
                    )
                } else {
                    applyCodeChange()
                }
            }
        }

        val hideMenu = {
            if (sheetBinding.clLineMenu.visibility == View.VISIBLE) {
                sheetBinding.clLineMenu.visibility = View.GONE
            }
        }

        sheetBinding.root.setOnClickListener { hideMenu() }
        sheetBinding.etPointId.setOnClickListener { hideMenu() }
        sheetBinding.etNote.setOnClickListener { hideMenu() }
        sheetBinding.switchFixOnly.setOnClickListener { hideMenu() }
        sheetBinding.viewInstantIndicator.setOnClickListener { hideMenu() }
        sheetBinding.viewAverageIndicator.setOnClickListener { hideMenu() }

        sheetBinding.clLineMenu.setOnClickListener {
            // Consume click to prevent hiding menu when clicking on empty space within the menu
        }

        sheetBinding.btnLineMenu.setOnClickListener {
            updateCloseShapeVisibility()
            sheetBinding.clLineMenu.visibility =
                if (sheetBinding.clLineMenu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        sheetBinding.llStartNewLine.setOnClickListener {
            if (currentIndicatorType == IndicatorType.LINE) {
                val lineCodePoints = getConsecutiveLineCodePoints()
                if (lineCodePoints.size == 1) {
                    showConfirmDialogBottomSheet(
                        onYesClick = {
                            if (collectedLabeledPoints.isNotEmpty() &&
                                isLineCodeFromCodeId(collectedLabeledPoints.last().codeId)) {
                                collectedLabeledPoints.removeAt(collectedLabeledPoints.size - 1)
                                val markersToRemove = markerToPointMap.entries.filter { (_, point) ->
                                    point.codeId == currentLineCodeId
                                }
                                markersToRemove.forEach { (marker, _) ->
                                    binding.mapView.overlays.remove(marker)
                                    collectedPointMarkers.remove(marker)
                                    markerToPointMap.remove(marker)
                                }
                                polylineOverlay?.let {
                                    OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
                                    polylineOverlay = null
                                }
                                liveTrackingLineOverlay?.let {
                                    OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
                                    liveTrackingLineOverlay = null
                                }
                                lineSegmentStartIndex = collectedLabeledPoints.size
                                currentLineCodeId = null
                                updateMarkersForZoom()
                                binding.mapView.invalidate()
                            }
                            finalizeCurrentLineSegment(closeFlag = isShapeClosed)
                            isShapeClosed = false
                            addFromBeginning = false
                            hasStartedNewLine = true
                            advanceLineCodeForNewSegment(sheetBinding)?.let { nextCode ->
                                selectedCodeId = nextCode
                            }
                            updateCloseShapeVisibility()
                            sheetBinding.clLineMenu.visibility = View.GONE
                        },
                        onNoClick = {
                            sheetBinding.clLineMenu.visibility = View.GONE
                        }
                    )
                } else {
                    finalizeCurrentLineSegment(closeFlag = isShapeClosed)
                    isShapeClosed = false
                    addFromBeginning = false
                    hasStartedNewLine = true
                    advanceLineCodeForNewSegment(sheetBinding)?.let { nextCode ->
                        selectedCodeId = nextCode
                    }
                    updateCloseShapeVisibility()
                    sheetBinding.clLineMenu.visibility = View.GONE
                }
            } else {
                isShapeClosed = false
                addFromBeginning = false
                updateCloseShapeVisibility()
                sheetBinding.clLineMenu.visibility = View.GONE
            }
        }

        sheetBinding.llFromOtherSide.setOnClickListener {
            addFromBeginning = !addFromBeginning
            if (currentIndicatorType == IndicatorType.LINE && getConsecutiveLineCodePoints().isNotEmpty()) {
                updateLiveTrackingLine()
            }
            sheetBinding.clLineMenu.visibility = View.GONE
        }

        sheetBinding.llCloseShape.setOnClickListener {
            if (currentIndicatorType == IndicatorType.LINE) {
                finalizeCurrentLineSegment(closeFlag = true)
                hasStartedNewLine = true
                advanceLineCodeForNewSegment(sheetBinding)?.let { nextCode ->
                    selectedCodeId = nextCode
                }
                updateCloseShapeVisibility()
            }
            sheetBinding.clLineMenu.visibility = View.GONE
        }

        sheetBinding.btnSave.setOnClickListener {
            sheetBinding.clLineMenu.visibility = View.GONE
            val pointIdText = sheetBinding.etPointId.text.toString()
            val note = sheetBinding.etNote.text.toString()

            val location = currentLocation ?: locationMarker?.position
            if (location != null && pointIdText.isNotEmpty()) {
                val pointIdRegex = Regex("^([a-zA-Z])(\\d+)$")
                val numericOnlyRegex = Regex("^\\d+$")

                var actualPointId = pointIdText

                when {
                    pointIdRegex.matches(pointIdText) -> {
                        val matchResult = pointIdRegex.find(pointIdText)
                        val prefix = matchResult?.groupValues?.get(1) ?: ""
                        val numericPart = matchResult?.groupValues?.get(2)?.toIntOrNull() ?: 1
                        pointIdPrefix = prefix
                        pointIdNumericCounter = numericPart + 1
                    }
                    pointIdText.length == 1 && pointIdText[0].isLetter() -> {
                        pointIdPrefix = pointIdText[0].toString()
                        actualPointId = "${pointIdPrefix}1"
                        pointIdNumericCounter = 2
                    }
                    numericOnlyRegex.matches(pointIdText) -> {
                        val numericValue = pointIdText.toIntOrNull() ?: pointCounter
                        pointCounter = numericValue + 1
                        pointIdPrefix = null
                        pointIdNumericCounter = 1
                    }
                    else -> {
                        val numericValue = pointIdText.filter { it.isDigit() }.toIntOrNull()
                        if (numericValue != null) {
                            pointCounter = numericValue + 1
                            pointIdPrefix = null
                            pointIdNumericCounter = 1
                        } else {
                            pointCounter++
                        }
                    }
                }

                addPointAtLocation(location, actualPointId, selectedPointCodeId, currentIndicatorType)
                updateCloseShapeVisibility()
                val nextPointId = if (pointIdPrefix != null) {
                    "$pointIdPrefix$pointIdNumericCounter"
                } else {
                    pointCounter.toString()
                }
                sheetBinding.etPointId.setText(nextPointId)
                sheetBinding.etPointId.setHint(nextPointId)
            }
        }
    }

    private fun hideCollectPointBottomSheet(shouldStartNewSegment: Boolean = false, finalizeSegment: Boolean = true) {
        if (finalizeSegment && selectedPointIndicatorType == IndicatorType.LINE && currentLineCodeId != null) {
            finalizeCurrentLineSegment(closeFlag = isShapeClosed)
            if (shouldStartNewSegment) {
                hasStartedNewLine = true
                advanceLineCodeForNewSegment(binding.bottomSheetCollectPoint)
            }
        }

        if (finalizeSegment) {
            polylineOverlay?.let {
                OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
                polylineOverlay = null
            }
            liveTrackingLineOverlay?.let {
                OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
                liveTrackingLineOverlay = null
            }
        }

        val bottomSheetView = binding.bottomSheetCollectPoint.root
        val height = bottomSheetView.height
        bottomSheetView.animate()
            .alpha(0f)
            .translationY(if (height > 0) height.toFloat() else 500f)
            .setDuration(300)
            .withEndAction {
                bottomSheetView.visibility = View.GONE
                bottomSheetView.isClickable = false
                bottomSheetView.isFocusable = false
                binding.bottomSheetCollectPoint.llDataCollectionSettings.visibility = View.GONE

                // Show bottom navigation when bottom sheet is hidden
                showBottomNavigation()
            }
            .start()
    }

    private fun setupSwipeGestureForDataCollectionSettings(
        bottomSheetView: View,
        sheetBinding: BottomSheetCollectPointBinding
    ) {
        val minSwipeDistance = 100f
        var initialY = 0f
        var initialX = 0f
        var isTrackingSwipe = false
        var hasMoved = false
        var touchStartView: View? = null
        var hasTriggeredAction = false

        fun isInteractiveView(view: View?): Boolean {
            if (view == null) return false
            return view is android.widget.EditText ||
                    view is android.widget.Button ||
                    view is com.google.android.material.button.MaterialButton ||
                    view is android.widget.ImageButton ||
                    view.isClickable ||
                    view.isFocusable
        }

        bottomSheetView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.y
                    initialX = event.x
                    isTrackingSwipe = true
                    hasMoved = false
                    hasTriggeredAction = false

                    touchStartView = findViewAt(bottomSheetView, event.x.toInt(), event.y.toInt())

                    if (isInteractiveView(touchStartView)) {
                        isTrackingSwipe = false
                    }

                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isTrackingSwipe && !hasTriggeredAction) {
                        val deltaY = initialY - event.y
                        val deltaX = initialX - event.x
                        val absDeltaY = Math.abs(deltaY)
                        val absDeltaX = Math.abs(deltaX)

                        if (absDeltaY > 20f && absDeltaY > absDeltaX) {
                            hasMoved = true

                            if (deltaY > minSwipeDistance && sheetBinding.llDataCollectionSettings.visibility != View.VISIBLE) {
                                showDataCollectionSettings(sheetBinding)
                                hasTriggeredAction = true
                            }
                            else if (deltaY < -minSwipeDistance && sheetBinding.llDataCollectionSettings.visibility == View.VISIBLE) {
                                hideDataCollectionSettings(sheetBinding)
                                hasTriggeredAction = true
                            }
                        }
                    }
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTrackingSwipe = false
                    hasMoved = false
                    touchStartView = null
                    hasTriggeredAction = false
                    false
                }

                else -> false
            }
        }
    }

    private fun findViewAt(parent: View, x: Int, y: Int): View? {
        if (parent is ViewGroup) {
            for (i in parent.childCount - 1 downTo 0) {
                val child = parent.getChildAt(i)
                val childLeft = child.left
                val childTop = child.top
                val childRight = child.right
                val childBottom = child.bottom

                if (x >= childLeft && x < childRight && y >= childTop && y < childBottom) {
                    val childX = x - childLeft
                    val childY = y - childTop
                    val found = findViewAt(child, childX, childY)
                    return found ?: child
                }
            }
        }
        return parent
    }

    private fun showDataCollectionSettings(sheetBinding: BottomSheetCollectPointBinding) {
        if (sheetBinding.llDataCollectionSettings.visibility != View.VISIBLE) {
            sheetBinding.llDataCollectionSettings.visibility = View.VISIBLE
            sheetBinding.llDataCollectionSettings.alpha = 0f
            sheetBinding.llDataCollectionSettings.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }

    private fun hideDataCollectionSettings(sheetBinding: BottomSheetCollectPointBinding) {
        if (sheetBinding.llDataCollectionSettings.visibility == View.VISIBLE) {
            sheetBinding.llDataCollectionSettings.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    sheetBinding.llDataCollectionSettings.visibility = View.GONE
                }
                .start()
        }
    }

    private fun triggerButtonContainerRipple(buttonContainer: View) {
        buttonContainer.isClickable = true
        buttonContainer.isFocusable = true

        buttonContainer.isPressed = true
        buttonContainer.refreshDrawableState()

        buttonContainer.postDelayed({
            buttonContainer.isPressed = false
            buttonContainer.refreshDrawableState()
        }, 200)
    }

    private fun setupSwipeGestureForPointLineSelection(
        bottomSheetView: View,
        sheetBinding: BottomSheetLineSegmentBinding
    ) {
        val minSwipeDistance = 100f
        var initialY = 0f
        var initialX = 0f
        var isTrackingSwipe = false
        var hasMoved = false
        var touchStartView: View? = null
        var hasTriggeredAction = false

        fun isInteractiveView(view: View?): Boolean {
            if (view == null) return false
            return view is android.widget.EditText ||
                    view is android.widget.Button ||
                    view is com.google.android.material.button.MaterialButton ||
                    view is android.widget.ImageButton ||
                    view.isClickable ||
                    view.isFocusable
        }

        bottomSheetView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.y
                    initialX = event.x
                    isTrackingSwipe = true
                    hasMoved = false
                    hasTriggeredAction = false

                    touchStartView = findViewAt(bottomSheetView, event.x.toInt(), event.y.toInt())

                    if (isInteractiveView(touchStartView)) {
                        isTrackingSwipe = false
                    }

                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isTrackingSwipe && !hasTriggeredAction) {
                        val deltaY = initialY - event.y
                        val deltaX = initialX - event.x
                        val absDeltaY = Math.abs(deltaY)
                        val absDeltaX = Math.abs(deltaX)

                        if (absDeltaY > 20f && absDeltaY > absDeltaX) {
                            hasMoved = true

                            if (deltaY > minSwipeDistance && sheetBinding.llPointLineInfo.visibility != View.VISIBLE) {
                                showPointLineSelection(sheetBinding)
                                hasTriggeredAction = true
                            }
                            else if (deltaY < -minSwipeDistance && sheetBinding.llPointLineInfo.visibility == View.VISIBLE) {
                                hidePointLineSelection(sheetBinding)
                                hasTriggeredAction = true
                            }
                        }
                    }
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!hasMoved && sheetBinding.clLineMenu.visibility == View.VISIBLE) {
                        val x = event.x.toInt()
                        val y = event.y.toInt()

                        val clickedView = findViewAt(bottomSheetView, x, y)

                        var isMenuOrChild = false
                        var currentView: View? = clickedView
                        while (currentView != null) {
                            if (currentView == sheetBinding.clLineMenu) {
                                isMenuOrChild = true
                                break
                            }
                            currentView = currentView.parent as? View
                        }

                        if (!isMenuOrChild) {
                            sheetBinding.clLineMenu.visibility = View.GONE
                        }
                    }

                    isTrackingSwipe = false
                    hasMoved = false
                    touchStartView = null
                    hasTriggeredAction = false
                    false
                }

                else -> false
            }
        }
    }

    private fun showPointLineSelection(sheetBinding: BottomSheetLineSegmentBinding) {
        if (sheetBinding.llPointLineInfo.visibility != View.VISIBLE) {
            sheetBinding.llPointLineInfo.visibility = View.VISIBLE
            sheetBinding.llPointLineInfo.alpha = 0f
            sheetBinding.llPointLineInfo.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }

    private fun hidePointLineSelection(sheetBinding: BottomSheetLineSegmentBinding) {
        if (sheetBinding.llPointLineInfo.visibility == View.VISIBLE) {
            sheetBinding.llPointLineInfo.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    sheetBinding.llPointLineInfo.visibility = View.GONE
                }
                .start()
        }
    }

    private fun setupBottomSheetClickToHideMenu(
        bottomSheetView: View,
        sheetBinding: BottomSheetLineSegmentBinding
    ) {
        val hideMenuIfVisible = {
            if (sheetBinding.clLineMenu.visibility == View.VISIBLE) {
                sheetBinding.clLineMenu.visibility = View.GONE
            }
        }

        sheetBinding.llMainContent.setOnClickListener { hideMenuIfVisible() }
        sheetBinding.llCodeIdContainer.setOnClickListener { hideMenuIfVisible() }
        sheetBinding.txtPointId.setOnClickListener { hideMenuIfVisible() }
        sheetBinding.tvCodeId.setOnClickListener { hideMenuIfVisible() }
        sheetBinding.tvSegmentInfo.setOnClickListener { hideMenuIfVisible() }
        sheetBinding.viewTypeDot.setOnClickListener { hideMenuIfVisible() }
        sheetBinding.btnStackout.setOnClickListener { hideMenuIfVisible() }
        // Don't set click listener on llButtonContainer as it contains the menu button
        // and might interfere with menu button clicks

    }

    private fun showSelectCodeBottomSheet(
        collectSheetBinding: BottomSheetCollectPointBinding?,
        onCodeSelected: (String, IndicatorType) -> Unit = { _, _ -> }
    ) {
        val bottomSheetView = binding.bottomSheetSelectCode.root
        val codeSheetBinding = binding.bottomSheetSelectCode

        bottomSheetView.isClickable = true
        bottomSheetView.isFocusable = true

        bottomSheetView.elevation = 10f * resources.displayMetrics.density
        bottomSheetView.translationZ = 10f * resources.displayMetrics.density

        val defaultCodes = listOf(
            CodeItem("", "No code", IndicatorType.POINT),
            CodeItem("P", "Standard Point", IndicatorType.POINT),
            CodeItem("L", "Standard line", IndicatorType.LINE)
        )

        var allCodes = mutableListOf<CodeItem>()

        fun loadCodes() {
            val customCodes = getCustomCodes()
            allCodes.clear()
            allCodes.addAll(defaultCodes)
            allCodes.addAll(customCodes)
        }

        loadCodes()

        fun createAdapter(codesList: List<CodeItem>): CodeAdapter {
            return CodeAdapter(codesList) { selectedCode ->
                var finalCodeId = selectedCode.abbreviation

                if (selectedCode.indicatorType == IndicatorType.LINE) {
                    val (base, initialNumber) = normalizeLineCode(finalCodeId) ?: (finalCodeId to null)

                    // Strategy: Find the maximum number used by this base code and increment it (Max + 1)
                    var maxNumber = 0
                    var baseExists = false

                    // Check completed lines
                    val existingLines = completedLineOverlays.filterIsInstance<ClickablePolylineOverlay>()
                    existingLines.forEach { line ->
                        val (lineBase, lineNum) = normalizeLineCode(line.codeId) ?: (null to null)
                        if (lineBase != null && lineBase.equals(base, ignoreCase = true)) {
                            if (lineNum != null) {
                                maxNumber = kotlin.math.max(maxNumber, lineNum)
                            } else {
                                baseExists = true
                            }
                        }
                    }

                    // Check collected points/labels
                    collectedLabeledPoints.forEach { point ->
                        val (pointBase, pointNum) = normalizeLineCode(point.codeId) ?: (null to null)
                        if (pointBase != null && pointBase.equals(base, ignoreCase = true)) {
                            if (pointNum != null) {
                                maxNumber = kotlin.math.max(maxNumber, pointNum)
                            } else {
                                baseExists = true
                            }
                        }
                    }

                    if (maxNumber == 0 && !baseExists) {
                        finalCodeId = base
                    } else {
                        val nextNumber = maxNumber + 1
                        finalCodeId = "$base$nextNumber"
                    }

                    // Verify uniqueness just in case
                    var currentNumber = if (maxNumber == 0 && !baseExists) 0 else maxNumber + 1
                    while (
                        completedLineOverlays.any { (it as? ClickablePolylineOverlay)?.codeId.equals(finalCodeId, ignoreCase = true) } ||
                        collectedLabeledPoints.any { point ->
                            point.codeId.equals(finalCodeId, ignoreCase = true) &&
                                    (currentLineCodeId == null || !point.codeId.equals(currentLineCodeId, ignoreCase = true))
                        }
                    ) {
                        currentNumber++
                        finalCodeId = "$base$currentNumber"
                        if (currentNumber > (maxNumber + 1) + 1000) break
                    }
                }

                onCodeSelected(finalCodeId, selectedCode.indicatorType)
                hideSelectCodeBottomSheet()
            }
        }

        val adapter = createAdapter(allCodes)
        codeSheetBinding.rvCodes.layoutManager = LinearLayoutManager(requireContext())
        codeSheetBinding.rvCodes.adapter = adapter

        val dividerItemDecoration = DividerItemDecoration(
            requireContext(),
            LinearLayoutManager.VERTICAL
        )
        dividerItemDecoration.setDrawable(
            ContextCompat.getDrawable(requireContext(), R.drawable.divider_horizontal)!!
        )
        codeSheetBinding.rvCodes.addItemDecoration(dividerItemDecoration)

        codeSheetBinding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                val filteredCodes = if (query.isEmpty()) {
                    allCodes
                } else {
                    allCodes.filter {
                        it.abbreviation.contains(query, ignoreCase = true) ||
                                it.description.contains(query, ignoreCase = true)
                    }
                }
                codeSheetBinding.rvCodes.adapter = createAdapter(filteredCodes)
            }
        })

        codeSheetBinding.btnClose.setOnClickListener {
            hideSelectCodeBottomSheet()
        }

        codeSheetBinding.btnAddCode.setOnClickListener {
            // Backup Fragment no longer supports adding codes
            hideSelectCodeBottomSheet()
        }

        bottomSheetView.visibility = View.VISIBLE
        bottomSheetView.alpha = 0f

        bottomSheetView.post {
            val height = bottomSheetView.height
            bottomSheetView.translationY = height.toFloat()
            bottomSheetView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start()
        }
    }

    private fun hideSelectCodeBottomSheet() {
        val bottomSheetView = binding.bottomSheetSelectCode.root
        val height = bottomSheetView.height
        bottomSheetView.animate()
            .alpha(0f)
            .translationY(if (height > 0) height.toFloat() else 500f)
            .setDuration(300)
            .withEndAction {
                bottomSheetView.visibility = View.GONE
                bottomSheetView.isClickable = false
                bottomSheetView.isFocusable = false
                // Don't show bottom navigation here - this sheet is usually hidden
                // when transitioning to another sheet (e.g., collect point sheet)
            }
            .start()
    }

    private fun showObjectListBottomSheet() {
        val bottomSheetView = binding.bottomSheetObjectList.root
        val objectListBinding = binding.bottomSheetObjectList

        hideBottomNavigation()

        bottomSheetView.isClickable = true
        bottomSheetView.isFocusable = true

        bottomSheetView.elevation = 10f * resources.displayMetrics.density
        bottomSheetView.translationZ = 10f * resources.displayMetrics.density

        objectListBinding.btnCloseObjectList.setOnClickListener {
            hideObjectListBottomSheet()
        }

        objectListBinding.btnAddObject.setOnClickListener {
            hideObjectListBottomSheet()
            showNewPointBottomSheet(null)
        }

        val objectListItems = processCollectedPointsForObjectList()

        val adapter = ObjectListAdapter(objectListItems) { item ->
            val editingLine = if (isSelectingPointForEditLine) pendingEditLineSegment else null

            if (isSelectingPointForEditLine && editingLine != null && item.indicatorType == IndicatorType.POINT) {
                // Add selected point to the editing line
                val index = collectedLabeledPoints.indexOfFirst { it.id == item.id }
                if (index >= 0) {
                    val originalPoint = collectedLabeledPoints[index]
                    val updatedPoint = originalPoint.copy(codeId = editingLine.codeId)
                    collectedLabeledPoints[index] = updatedPoint
                    updateMarkersForZoom()

                    addExistingPointToLineSegment(updatedPoint, editingLine)

                    hideObjectListBottomSheet()
                    isSelectingPointForEditLine = false
                    pendingEditLineSegment = null

                    // Re-open edit bottom sheet with updated points
                    showEditLineBottomSheet(editingLine)
                } else {
                    hideObjectListBottomSheet()
                    isSelectingPointForEditLine = false
                    pendingEditLineSegment = null
                }
            } else {
                hideObjectListBottomSheet()

                when (item.indicatorType) {
                    IndicatorType.POINT -> {
                        val point = collectedLabeledPoints.find { it.id == item.id }
                        if (point != null) {
                            showPointDetailsBottomSheet(point)
                        }
                    }
                    IndicatorType.LINE -> {
                        val lineSegment = completedLineOverlays.find { overlay ->
                            overlay is ClickablePolylineOverlay &&
                                    overlay.codeId == item.codeId
                        } as? ClickablePolylineOverlay

                        if (lineSegment != null) {
                            showLineSegmentDetailsBottomSheet(lineSegment)
                        } else {
                            val firstPoint = collectedLabeledPoints.find { it.codeId == item.codeId }
                            if (firstPoint != null) {
                                showPointDetailsBottomSheet(firstPoint)
                            }
                        }
                    }
                }
            }
        }
        objectListBinding.rvObjectList.layoutManager = LinearLayoutManager(requireContext())
        objectListBinding.rvObjectList.adapter = adapter

        objectListBinding.etSearchObject.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                val filteredItems = if (query.isEmpty()) {
                    objectListItems
                } else {
                    objectListItems.filter {
                        it.id.contains(query, ignoreCase = true) ||
                                it.codeId.contains(query, ignoreCase = true)
                    }
                }
                objectListBinding.rvObjectList.adapter = ObjectListAdapter(filteredItems) { item ->
                    hideObjectListBottomSheet()

                    when (item.indicatorType) {
                        IndicatorType.POINT -> {
                            val point = collectedLabeledPoints.find { it.id == item.id }
                            if (point != null) {
                                showPointDetailsBottomSheet(point)
                            }
                        }
                        IndicatorType.LINE -> {
                            val lineSegment = completedLineOverlays.find { overlay ->
                                overlay is ClickablePolylineOverlay &&
                                        overlay.codeId == item.codeId
                            } as? ClickablePolylineOverlay

                            if (lineSegment != null) {
                                showLineSegmentDetailsBottomSheet(lineSegment)
                            } else {
                                val firstPoint = collectedLabeledPoints.find { it.codeId == item.codeId }
                                if (firstPoint != null) {
                                    showPointDetailsBottomSheet(firstPoint)
                                }
                            }
                        }
                    }
                }
            }
        })

        bottomSheetView.visibility = View.VISIBLE
        bottomSheetView.alpha = 0f

        bottomSheetView.post {
            val height = bottomSheetView.height
            bottomSheetView.translationY = height.toFloat()
            bottomSheetView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start()
        }
    }

    private fun hideObjectListBottomSheet() {
        val bottomSheetView = binding.bottomSheetObjectList.root
        val height = bottomSheetView.height
        bottomSheetView.animate()
            .alpha(0f)
            .translationY(if (height > 0) height.toFloat() else 500f)
            .setDuration(300)
            .withEndAction {
                bottomSheetView.visibility = View.GONE
                bottomSheetView.isClickable = false
                bottomSheetView.isFocusable = false
            }
            .start()
    }


    private fun processCollectedPointsForObjectList(): List<ObjectListItem> {
        val items = mutableListOf<ObjectListItem>()
        if (collectedLabeledPoints.isEmpty()) {
            return items
        }

        val processedLineCodes = mutableSetOf<String>()
        val reversedPoints = collectedLabeledPoints.reversed()

        for (point in reversedPoints) {
            val isLineCode = isLineCodeFromCodeId(point.codeId)

            if (isLineCode) {
                if (processedLineCodes.contains(point.codeId)) {
                    continue
                }

                // Gather ALL points for this line code from the main list (to preserve order)
                val allLinePoints = collectedLabeledPoints.filter { it.codeId == point.codeId }

                if (allLinePoints.isEmpty()) continue

                var totalDistance = 0.0
                val geoPoints = allLinePoints.map { it.geoPoint }

                for (k in 0 until geoPoints.size - 1) {
                    totalDistance += geoPoints[k].distanceToAsDouble(geoPoints[k + 1])
                }

                // Check if this line is closed by looking at completed overlays
                val isClosed = completedLineOverlays.any { overlay ->
                    overlay is ClickablePolylineOverlay &&
                            overlay.codeId == point.codeId &&
                            overlay.isClosed
                }

                if (isClosed && geoPoints.size >= 2) {
                    totalDistance += geoPoints.last().distanceToAsDouble(geoPoints.first())
                }

                // Use the latest point's timestamp (which is 'point' since we are iterating reversed)
                val dateTime = formatTimestamp(point.ts)

                // Use the first point for the ID display to be consistent with naming if needed, 
                // but usually the Code ID is the main identifier.
                // The original code used firstPoint.codeId.ifEmpty { "Line ${firstPoint.id}" }
                // We can stick to point.codeId since they all share it.
                val displayId = point.codeId.ifEmpty { "Line ${allLinePoints.first().id}" }

                items.add(
                    ObjectListItem(
                        id = displayId,
                        codeId = point.codeId,
                        dateTime = dateTime,
                        indicatorType = IndicatorType.LINE,
                        pointCount = allLinePoints.size,
                        distance = totalDistance
                    )
                )
                processedLineCodes.add(point.codeId)

            } else {
                val dateTime = formatTimestamp(point.ts)
                items.add(
                    ObjectListItem(
                        id = point.id,
                        codeId = point.codeId,
                        dateTime = dateTime,
                        indicatorType = IndicatorType.POINT
                    )
                )
            }
        }

        return items
    }

    private fun formatTimestamp(timestamp: String): String {
        return try {
            val inputFormat =
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
            inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(timestamp)

            val outputFormat = java.text.SimpleDateFormat("dd-MM-yyyy h:mm a", java.util.Locale.US)
            outputFormat.format(date ?: java.util.Date())
        } catch (e: Exception) {
            timestamp
        }
    }


    private fun updatePointTypeIndicator(
        indicatorView: android.widget.ImageView,
        indicatorType: IndicatorType
    ) {
        when (indicatorType) {
            IndicatorType.POINT -> {
                indicatorView.setImageResource(R.drawable.point_type_dot)
                indicatorView.setColorFilter(null)
            }

            IndicatorType.LINE -> {
                indicatorView.setImageResource(R.drawable.point_type_line)
                indicatorView.setColorFilter(null)
            }
        }
    }

    private fun updateLineMenuVisibility(
        menuButton: android.widget.ImageButton,
        indicatorType: IndicatorType
    ) {
        val pointCount = if (indicatorType == IndicatorType.LINE && currentLineCodeId != null) {
            getAllPointsInCurrentLineSegment().size
        } else {
            0
        }

        menuButton.visibility = if (pointCount >= 2) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun saveCustomCode(codeName: String, codeDesc: String, type: String) {
        val prefs: SharedPreferences =
            requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val existingCodesJson = prefs.getString(KEY_CUSTOM_CODES, "[]")
        val codesArray = JSONArray(existingCodesJson)

        val newCode = JSONObject().apply {
            put("name", codeName)
            put("description", codeDesc)
            put("type", type)
        }

        codesArray.put(newCode)

        editor.putString(KEY_CUSTOM_CODES, codesArray.toString())
        editor.apply()
    }

    private fun getCustomCodes(): List<CodeItem> {
        val prefs: SharedPreferences =
            requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val codesJson = prefs.getString(KEY_CUSTOM_CODES, "[]") ?: "[]"
        val codesArray = JSONArray(codesJson)

        val customCodes = mutableListOf<CodeItem>()
        for (i in 0 until codesArray.length()) {
            val codeObj = codesArray.getJSONObject(i)
            val name = codeObj.getString("name")
            val description = codeObj.optString("description", "")
            val type = codeObj.getString("type")

            val indicatorType = if (type.equals("Line", ignoreCase = true)) {
                IndicatorType.LINE
            } else {
                IndicatorType.POINT
            }

            customCodes.add(CodeItem(name, description, indicatorType))
        }

        return customCodes
    }

    private fun getCodeDescription(codeId: String): String {
        val defaultCodes = listOf(
            CodeItem("", "No code", IndicatorType.POINT),
            CodeItem("P", "Standard Point", IndicatorType.POINT),
            CodeItem("L", "Standard line", IndicatorType.LINE)
        )

        val defaultCode = defaultCodes.find { it.abbreviation.equals(codeId, ignoreCase = true) }
        if (defaultCode != null) {
            return defaultCode.description
        }

        val customCodes = getCustomCodes()
        val customCode = customCodes.find { it.abbreviation.equals(codeId, ignoreCase = true) }
        return customCode?.description ?: ""
    }

    private fun setupResizeButton() {
        binding.imgResize.setOnClickListener {
            fitMapToPoints()
        }
    }

    private fun setupMenuButton() {
        binding.imgMenu.setOnClickListener {
            if (binding.clMenu.visibility == View.VISIBLE) {
                binding.clMenu.visibility = View.GONE
            } else {
                binding.clMenu.visibility = View.VISIBLE
            }
        }

        binding.llObjectList.setOnClickListener {
            showObjectListBottomSheet()
        }

        binding.llProjectDetails.setOnClickListener {
        }
    }

    private fun fitMapToPoints() {
        if (isMapFitted) return

        cancelOngoingAnimations()

        val locationPoint = currentLocation ?: locationMarker?.position

        if (collectedLabeledPoints.isEmpty() && locationPoint == null) return

        val currentRotation = binding.mapView.mapOrientation

        var minLat = Double.MAX_VALUE
        var maxLat = Double.MIN_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = Double.MIN_VALUE

        collectedLabeledPoints.forEach { point ->
            val lat = point.geoPoint.latitude
            val lon = point.geoPoint.longitude
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
        }

        locationPoint?.let { location ->
            val lat = location.latitude
            val lon = location.longitude
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
        }

        val boundingBox = org.osmdroid.util.BoundingBox(maxLat, maxLon, minLat, minLon)
        val padding = 240

        binding.mapView.post {
            binding.mapView.zoomToBoundingBox(
                boundingBox,
                true,
                padding,
                binding.mapView.maxZoomLevel,
                400L
            )
            binding.mapView.postDelayed({
                binding.mapView.setMapOrientation(currentRotation)
                binding.imgCompass.rotation = currentRotation
            }, 50)
        }
    }

    private fun addPointAtLocation(
        location: GeoPoint,
        pointId: String,
        codeId: String,
        indicatorType: IndicatorType
    ) {
        if (indicatorType == IndicatorType.LINE) {
            val switchingCode = currentLineCodeId != null && currentLineCodeId != codeId
            if (switchingCode) {
                finalizeCurrentLineSegment(closeFlag = isShapeClosed)
            }
            currentLineCodeId = codeId
            trackLineCodeUsage(codeId)
        } else {
            finalizeCurrentLineSegment(closeFlag = isShapeClosed)
            addFromBeginning = false
        }

        val timestamp =
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date())

        val coords = listOf(location.longitude, location.latitude)
        val elevation = 0.0

        val labeledPoint = LabeledPoint(
            id = pointId,
            codeId = codeId,
            coords = coords,
            elevation = elevation,
            ts = timestamp
        )

        val previousPointWasLineCode = collectedLabeledPoints.isNotEmpty() &&
                isLineCodeFromCodeId(collectedLabeledPoints.last().codeId)

        if (indicatorType == IndicatorType.LINE && addFromBeginning) {
            // Add point at the beginning of the current line segment (which might be in the middle of the list)
            // instead of the absolute beginning of the list
            val insertIndex = if (lineSegmentStartIndex >= 0 && lineSegmentStartIndex <= collectedLabeledPoints.size) {
                lineSegmentStartIndex
            } else {
                0
            }
            collectedLabeledPoints.add(insertIndex, labeledPoint)
        } else {
            collectedLabeledPoints.addLast(labeledPoint)
        }

        if (indicatorType == IndicatorType.LINE) {
            redrawPolyline()
            hasStartedNewLine = false
        }

        lastZoomLevel = binding.mapView.zoomLevelDouble
        updateMarkersForZoom()
    }

    private fun isLineCodeFromCodeId(codeId: String): Boolean {
        if (codeId.isEmpty()) return false

        val lineCodes = listOf("L", "BLDG", "RCL", "LINE", "ROAD", "WALL", "FENCE")
        val upper = codeId.uppercase()
        val baseFromNormalize = normalizeLineCode(codeId)?.first
        val base = baseFromNormalize ?: upper

        if (lineCodes.contains(base)) return true

        val customCodes = getCustomCodes()
        val customCode = customCodes.find { custom ->
            custom.abbreviation.equals(codeId, ignoreCase = true) ||
                    (baseFromNormalize != null && custom.abbreviation.equals(baseFromNormalize, ignoreCase = true))
        }
        return customCode?.indicatorType == IndicatorType.LINE
    }

    private fun getConsecutiveLineCodePoints(): List<LabeledPoint> {
        val consecutiveLinePoints = mutableListOf<LabeledPoint>()
        val startIndex = lineSegmentStartIndex.coerceAtLeast(0)

        if (currentLineCodeId != null) {
            for (i in startIndex until collectedLabeledPoints.size) {
                val point = collectedLabeledPoints[i]
                if (point.codeId == currentLineCodeId) {
                    consecutiveLinePoints.add(point)
                }
            }
        } else {
            for (i in collectedLabeledPoints.size - 1 downTo startIndex) {
                val point = collectedLabeledPoints[i]
                if (isLineCodeFromCodeId(point.codeId)) {
                    consecutiveLinePoints.add(0, point)
                } else {
                    break
                }
            }
        }

        return consecutiveLinePoints
    }

    /**
     * Gets all points in the current line segment, including points before lineSegmentStartIndex
     * if they have the same codeId. This is used when continuing collection to count all points
     * in the segment, not just from the continuation point.
     */
    private fun getAllPointsInCurrentLineSegment(): List<LabeledPoint> {
        val targetCodeId = currentLineCodeId ?: return getConsecutiveLineCodePoints()

        // Filter all points that match the current line code ID
        // This ensures we get all points for the line even if they are not contiguous in the list
        // (e.g. if other points were collected in between)
        return collectedLabeledPoints.filter { point ->
            isLineCodeFromCodeId(point.codeId) && point.codeId == targetCodeId
        }
    }

    private fun normalizeLineCode(codeId: String): Pair<String, Int?>? {
        val regex = Regex("^([A-Za-z]+)(\\d+)?$")
        val match = regex.find(codeId.trim())
        val base = match?.groupValues?.getOrNull(1)?.uppercase() ?: return null
        val number = match.groupValues.getOrNull(2)?.toIntOrNull()
        return base to number
    }

    private fun trackLineCodeUsage(codeId: String) {
        val (base, number) = normalizeLineCode(codeId) ?: return
        val currentMax = lineCodeSequenceCounters[base] ?: 0
        val updatedMax = when {
            number != null && number > currentMax -> number
            currentMax == 0 && number == null -> 0
            else -> currentMax
        }
        lineCodeSequenceCounters[base] = updatedMax
    }

    private fun getNextLineCode(currentCodeId: String): String {
        val (base, number) = normalizeLineCode(currentCodeId) ?: return currentCodeId
        val lastUsed = lineCodeSequenceCounters[base]?.let { existing ->
            if (number != null) maxOf(existing, number) else existing
        } ?: (number ?: 0)
        val nextIndex = lastUsed + 1
        lineCodeSequenceCounters[base] = nextIndex
        return "$base$nextIndex"
    }

    private fun advanceLineCodeForNewSegment(sheetBinding: BottomSheetCollectPointBinding? = null): String? {
        if (selectedPointIndicatorType != IndicatorType.LINE) return null
        val nextCode = getNextLineCode(selectedPointCodeId)
        selectedPointCodeId = nextCode
        currentLineCodeId = null
        sheetBinding?.let {
            it.tvPointType.text = nextCode
            updatePointTypeIndicator(it.viewTypeDot, IndicatorType.LINE)
        }
        return nextCode
    }

    private fun finalizeCurrentLineSegment(closeFlag: Boolean = isShapeClosed) {
        // When finalizing, get all points in the current segment (including points before lineSegmentStartIndex)
        // This ensures that when continuing collection, we finalize the entire segment, not just from the continuation point
        val lineCodePoints = if (currentLineCodeId != null) {
            getAllPointsInCurrentLineSegment()
        } else {
            getConsecutiveLineCodePoints()
        }

        polylineOverlay?.let { OsmdroidPolylineHelper.removePolyline(binding.mapView, it) }
        polylineOverlay = null

        liveTrackingLineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
        }
        liveTrackingLineOverlay = null

        if (lineCodePoints.size >= 2) {
            val solidColor = ContextCompat.getColor(requireContext(), R.color.slate_gray_light)
            val geoPoints = lineCodePoints.map { it.geoPoint }

            var totalLength = 0.0
            for (i in 0 until geoPoints.size - 1) {
                totalLength += geoPoints[i].distanceToAsDouble(geoPoints[i + 1])
            }
            if (closeFlag && geoPoints.size >= 2) {
                totalLength += geoPoints.last().distanceToAsDouble(geoPoints.first())
            }

            val codeId = lineCodePoints.firstOrNull()?.codeId ?: ""

            // Remove any existing completed overlays for this codeId to prevent duplicates
            // This is crucial when continuing collection on an existing line
            val existingOverlays = completedLineOverlays.filter {
                it is ClickablePolylineOverlay && it.codeId == codeId
            }
            existingOverlays.forEach { overlay ->
                if (overlay is ClickablePolylineOverlay) {
                    OsmdroidPolylineHelper.removePolyline(binding.mapView, overlay)
                }
                completedLineOverlays.remove(overlay)
            }

            val clickablePolyline = ClickablePolylineOverlay(
                geoPoints,
                solidColor,
                6f,
                closed = closeFlag
            )

            clickablePolyline.codeId = codeId
            clickablePolyline.pointCount = lineCodePoints.size
            clickablePolyline.length = totalLength
            clickablePolyline.labeledPoints = lineCodePoints
            clickablePolyline.isClosed = closeFlag

            clickablePolyline.setOnClickListener {
                handleLineSegmentClick(clickablePolyline)
            }

            binding.mapView.overlays.add(clickablePolyline)
            binding.mapView.invalidate()

            completedLineOverlays.add(clickablePolyline)

            ensurePointClickHandlerAtEnd()

            updateMarkersForZoom()

            bringLocationMarkerToTop()
        } else {
            binding.mapView.invalidate()
        }

        lineSegmentStartIndex = collectedLabeledPoints.size
        isShapeClosed = false
        addFromBeginning = false
        currentLineCodeId = null
        hasStartedNewLine = true
    }

    private fun handleLineSegmentClick(clickedPolyline: ClickablePolylineOverlay) {
        val previousHighlightedLine = highlightedLineOverlay
        highlightedLineOverlay?.unhighlight()

        if (previousHighlightedLine != null && previousHighlightedLine != clickedPolyline) {
            // Redraw all to ensure highlighting state is correct
            updateMarkersForZoom()
        }

        if (highlightedLineOverlay == clickedPolyline) {
            highlightedLineOverlay = null
            binding.mapView.invalidate()
            updateMarkersForZoom()
        } else {
            val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
            clickedPolyline.highlight(primaryColor)
            highlightedLineOverlay = clickedPolyline
            binding.mapView.invalidate()
            showLineSegmentDetailsBottomSheet(clickedPolyline)

            // Update the markers for the clicked line
            updateMarkersForZoom()
        }
    }

    private fun showLineSegmentDetailsBottomSheet(lineSegment: ClickablePolylineOverlay) {
        selectedPoint = null
        hideLineSegmentDetailsBottomSheet(clearState = false)
        val wasCollecting = binding.bottomSheetCollectPoint.root.visibility == View.VISIBLE &&
                binding.bottomSheetCollectPoint.root.alpha > 0f &&
                !isShapeClosed &&
                currentLineCodeId != null
        wasCollectingBeforePointDetails = wasCollecting
        hideCollectPointBottomSheet(finalizeSegment = false)

        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        highlightedLineOverlay?.unhighlight()
        lineSegment.highlight(primaryColor)
        highlightedLineOverlay = lineSegment
        binding.mapView.invalidate()

        binding.mapView.invalidate()

        // Update markers to reflect selection immediately
        updateMarkersForZoom()

        // Hide bottom navigation when bottom sheet is shown
        hideBottomNavigation()

        val bottomSheetView = binding.bottomSheetLineSegment.root
        val sheetBinding = binding.bottomSheetLineSegment
        bottomSheetView.isClickable = true
        bottomSheetView.isFocusable = true
        bottomSheetView.elevation = 10f * resources.displayMetrics.density
        bottomSheetView.translationZ = 10f * resources.displayMetrics.density

        // Add touch listener to bottom sheet to see if touches reach it
        bottomSheetView.setOnTouchListener { view, event ->
            false // Don't consume, let children handle it
        }
        sheetBinding.tvCodeId.text = lineSegment.codeId.ifEmpty { "No code" }
        sheetBinding.llCodeIdContainer.visibility = View.VISIBLE

        sheetBinding.viewTypeDot.visibility = View.GONE
        sheetBinding.txtPointId.visibility = View.GONE

        val pointText = if (lineSegment.pointCount == 1) {
            "${lineSegment.pointCount} Point"
        } else {
            "${lineSegment.pointCount} Points"
        }
        val lengthText = String.format("%.1f M", lineSegment.length)
        sheetBinding.tvSegmentInfo.text = "$pointText | $lengthText"

        // Function to set up Edit button listeners
        val setupEditButton = {
            sheetBinding.llEdit.isClickable = true
            sheetBinding.llEdit.isFocusable = true
            sheetBinding.llEdit.setOnTouchListener { view, event ->
                false
            }
            sheetBinding.llEdit.setOnClickListener {
                sheetBinding.clLineMenu.visibility = View.GONE
                hideLineSegmentDetailsBottomSheet()
                showEditLineBottomSheet(lineSegment)
            }
        }

        // Function to set up Continue collect button listeners
        val setupContinueCollectButton = {
            sheetBinding.llContinueCollect.isClickable = true
            sheetBinding.llContinueCollect.isFocusable = true
            // Make sure child views don't intercept clicks
            sheetBinding.tvContinueCollect.isClickable = false
            sheetBinding.tvContinueCollect.isFocusable = false
            sheetBinding.ivContinueCollect.isClickable = false
            sheetBinding.ivContinueCollect.isFocusable = false

            sheetBinding.llContinueCollect.setOnTouchListener { view, event ->
                false // Don't consume, let click listener handle it
            }

            sheetBinding.llContinueCollect.setOnClickListener {
                sheetBinding.clLineMenu.visibility = View.GONE

                // Get the last point from the line segment
                val segmentPoints = lineSegment.labeledPoints
                val lastPoint: LabeledPoint? = if (segmentPoints.isNotEmpty()) {
                    segmentPoints.last()
                } else {
                    // Fallback: get last point from polyline's actual points
                    val polylinePoints = lineSegment.actualPoints
                    if (polylinePoints.isNotEmpty()) {
                        val lastGeoPoint = if (lineSegment.isClosed && polylinePoints.size > 1) {
                            polylinePoints[polylinePoints.size - 2]
                        } else {
                            polylinePoints.last()
                        }
                        collectedLabeledPoints.find {
                            it.codeId == lineSegment.codeId &&
                                    (it.geoPoint == lastGeoPoint || it.geoPoint.distanceToAsDouble(lastGeoPoint) < 0.1)
                        }
                    } else {
                        null
                    }
                }

                if (lastPoint == null) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Could not find last point of line segment",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                // Set the selected code and type
                selectedPointCodeId = lineSegment.codeId
                selectedPointIndicatorType = IndicatorType.LINE
                // Clear the highlighted overlay first to avoid "ghost" highlighting
                // We need to unhighlight the current one, update its markers (to remove red), and then clear the reference
                highlightedLineOverlay?.unhighlight()
                highlightedLineOverlay?.let { updateMarkersForZoom() }
                highlightedLineOverlay = null
                binding.mapView.invalidate()

                // Set the selected code and type
                selectedPointCodeId = lineSegment.codeId
                selectedPointIndicatorType = IndicatorType.LINE
                currentLineCodeId = lineSegment.codeId

                // Update the line segment start index to continue from the last point
                // Use indexOfFirst to ensure we get the start of the segment for "From Other End" functionality
                val firstPointIndex = collectedLabeledPoints.indexOfFirst { it.codeId == lineSegment.codeId }
                lineSegmentStartIndex = if (firstPointIndex >= 0) {
                    firstPointIndex
                } else {
                    collectedLabeledPoints.size
                }

                // Set flag so that hideLineSegmentDetailsBottomSheet will automatically show collect point bottom sheet
                wasCollectingBeforePointDetails = true
                isShapeClosed = false // Ensure shape is not closed
                addFromBeginning = false // Ensure we continue from the end, not from the other end

                // Hide the line segment details bottom sheet
                // The withEndAction in hideLineSegmentDetailsBottomSheet will automatically call showCollectPointBottomSheet()
                // if wasCollectingBeforePointDetails is true and conditions are met
                hideLineSegmentDetailsBottomSheet()

                // Update the live tracking line after a short delay to ensure bottom sheet is shown
                binding.root.postDelayed({
                    updateLiveTrackingLine()
                }, 350) // Slightly longer than animation duration (300ms)
            }
        }

        sheetBinding.btnCloseLineSegment.setOnClickListener {
            sheetBinding.clLineMenu.visibility = View.GONE
            hideLineSegmentDetailsBottomSheet()
        }

        sheetBinding.btnMenu.setOnClickListener {
            val newVisibility = if (sheetBinding.clLineMenu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            sheetBinding.clLineMenu.visibility = newVisibility
            // Ensure continue collect visibility is correct when menu is toggled
            sheetBinding.llContinueCollect.visibility =
                if (lineSegment.isClosed) View.GONE else View.VISIBLE

            // When menu becomes visible, ensure buttons are clickable and menu is on top
            if (newVisibility == View.VISIBLE) {
                // Bring menu to front to ensure it's above other views
                sheetBinding.clLineMenu.bringToFront()
                // Menu container should be clickable to allow touch event dispatch to children
                // but we'll use a touch listener that doesn't consume events
                sheetBinding.clLineMenu.isClickable = true
                sheetBinding.clLineMenu.isFocusable = true

                // Re-setup buttons when menu becomes visible to ensure listeners are active
                setupEditButton()
                setupContinueCollectButton()

                sheetBinding.llEdit.bringToFront()
                sheetBinding.llContinueCollect.bringToFront()
            }
        }

        // Set up Edit button initially
        setupEditButton()

        // Hide continue collect if segment is closed
        sheetBinding.llContinueCollect.visibility =
            if (lineSegment.isClosed) View.GONE else View.VISIBLE

        // Set up Continue collect button initially
        setupContinueCollectButton()

        setupBottomSheetClickToHideMenu(bottomSheetView, sheetBinding)

        // Ensure menu container allows child views to receive clicks
        sheetBinding.clLineMenu.setOnTouchListener { view, event ->
            // Don't consume touch events, let children handle them
            false
        }

        bottomSheetView.visibility = View.VISIBLE
        bottomSheetView.alpha = 0f

        bottomSheetView.post {
            val height = bottomSheetView.height
            bottomSheetView.translationY = height.toFloat()
            bottomSheetView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start()
        }
    }

    private fun hideLineSegmentDetailsBottomSheet(clearState: Boolean = true) {
        val bottomSheetView = binding.bottomSheetLineSegment.root
        val sheetBinding = binding.bottomSheetLineSegment
        val height = bottomSheetView.height
        bottomSheetView.animate()
            .alpha(0f)
            .translationY(if (height > 0) height.toFloat() else 500f)
            .setDuration(300)
            .withEndAction {
                bottomSheetView.visibility = View.GONE
                bottomSheetView.isClickable = false
                bottomSheetView.isFocusable = false
                sheetBinding.llPointLineInfo.visibility = View.GONE

                if (clearState) {
                    highlightedLineOverlay?.let {
                        it.unhighlight()
                        updateMarkersForZoom()
                    }
                    val wasClosed = highlightedLineOverlay?.isClosed == true
                    highlightedLineOverlay = null
                    binding.mapView.invalidate()

                    val shouldResumeCollect =
                        wasCollectingBeforePointDetails && !wasClosed && currentLineCodeId != null && !isShapeClosed
                    wasCollectingBeforePointDetails = false

                    selectedPoint = null
                    updateMarkersForZoom()

                    if (shouldResumeCollect) {
                        showCollectPointBottomSheet()
                    } else {
                        // Only show bottom navigation if NOT resuming collection
                        // (collect point sheet will handle hiding it)
                        showBottomNavigation()
                    }
                }
            }
            .start()
    }

    private fun setupPointClickHandler() {
        // Get protected views to prevent clicks on UI elements
        val protectedViews = mutableListOf<View>()
        fun collectProtectedViews(view: View) {
            protectedViews.add(view)
            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    collectProtectedViews(view.getChildAt(i))
                }
            }
        }

        binding.leftControlPanel?.let {
            // Make left panel intercept touches to prevent them from reaching map view
            it.isClickable = true
            it.isFocusable = true
            it.setOnTouchListener { view, event ->
                // Only consume touches that are actually on the left panel
                // Don't consume if the touch is outside the view bounds
                val location = IntArray(2)
                view.getLocationOnScreen(location)
                val x = event.rawX
                val y = event.rawY
                val isOnView = x >= location[0] && x <= location[0] + view.width &&
                        y >= location[1] && y <= location[1] + view.height
                // Only consume if touch is on the left panel, otherwise let it pass through
                isOnView
            }
            collectProtectedViews(it)
        }
        listOf(
            binding.clMenu,
            binding.llRightPanel,
            binding.imgBack,
            binding.imgMenu,
            binding.btnCollect
        ).forEach { view ->
            view?.let { collectProtectedViews(it) }
        }

        // Also protect bottom sheet views to prevent point clicks when interacting with bottom sheets
        listOf(
            binding.bottomSheetLineSegment.root,
            binding.bottomSheetEditLine.root,
            binding.bottomSheetCollectPoint.root,
            binding.bottomSheetSelectCode.root,
            binding.bottomSheetObjectList.root
        ).forEach { view ->
            collectProtectedViews(view)
        }

        pointClickHandlerOverlay = PointClickHandlerOverlay(
            onPointClick = { _ ->
                false
            },
            protectedViews = protectedViews
        )
        ensurePointClickHandlerAtEnd()
    }

    private fun ensurePointClickHandlerAtEnd() {
        pointClickHandlerOverlay?.let {
            binding.mapView.overlays.remove(it)
        }
        pointClickHandlerOverlay?.let {
            binding.mapView.overlays.add(it)
        }
    }

    private fun findNearestPoint(clickedGeoPoint: GeoPoint): LabeledPoint? {
        val projection = binding.mapView.projection
        val clickPixel = android.graphics.Point()
        projection.toPixels(clickedGeoPoint, clickPixel)

        val tolerancePixels =
            100f * resources.displayMetrics.density

        var nearestPoint: LabeledPoint? = null
        var minDistance = Float.MAX_VALUE

        for ((marker, point) in markerToPointMap) {
            val pointPixel = android.graphics.Point()
            projection.toPixels(point.geoPoint, pointPixel)

            val dx = (clickPixel.x - pointPixel.x).toDouble()
            val dy = (clickPixel.y - pointPixel.y).toDouble()
            val distance = kotlin.math.sqrt(dx * dx + dy * dy).toFloat()

            if (distance < minDistance && distance <= tolerancePixels) {
                minDistance = distance
                nearestPoint = point
            }
        }

        for (point in collectedLabeledPoints) {
            if (nearestPoint?.id == point.id && nearestPoint?.codeId == point.codeId) {
                continue
            }

            val pointPixel = android.graphics.Point()
            projection.toPixels(point.geoPoint, pointPixel)

            val dx = (clickPixel.x - pointPixel.x).toDouble()
            val dy = (clickPixel.y - pointPixel.y).toDouble()
            val distance = kotlin.math.sqrt(dx * dx + dy * dy).toFloat()

            if (distance < minDistance && distance <= tolerancePixels) {
                minDistance = distance
                nearestPoint = point
            }
        }

        for (overlay in completedLineOverlays) {
            if (overlay is ClickablePolylineOverlay) {
                for (point in overlay.labeledPoints) {
                    val pointPixel = android.graphics.Point()
                    projection.toPixels(point.geoPoint, pointPixel)

                    val dx = (clickPixel.x - pointPixel.x).toDouble()
                    val dy = (clickPixel.y - pointPixel.y).toDouble()
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy).toFloat()

                    if (distance < minDistance && distance <= tolerancePixels) {
                        minDistance = distance
                        nearestPoint = point
                    }
                }
            }
        }

        return nearestPoint
    }

    private fun showPointDetailsBottomSheet(point: LabeledPoint) {
        hideLineSegmentDetailsBottomSheet(clearState = false)

        // Manually clear line highlight if present, as we are switching to point mode
        highlightedLineOverlay?.let {
            it.unhighlight()
            updateMarkersForZoom()
        }
        highlightedLineOverlay = null

        val wasCollecting = binding.bottomSheetCollectPoint.root.visibility == View.VISIBLE &&
                binding.bottomSheetCollectPoint.root.alpha > 0f
        wasCollectingBeforePointDetails = wasCollecting
        if (wasCollectingBeforePointDetails) {
            hideCollectPointBottomSheet(finalizeSegment = false)
        }

        selectedPoint = point
        updateMarkersForZoom()

        // Hide bottom navigation when bottom sheet is shown
        hideBottomNavigation()

        val bottomSheetView = binding.bottomSheetLineSegment.root
        val sheetBinding = binding.bottomSheetLineSegment
        bottomSheetView.isClickable = true
        bottomSheetView.isFocusable = true

        bottomSheetView.elevation = 10f * resources.displayMetrics.density
        bottomSheetView.translationZ = 10f * resources.displayMetrics.density

        sheetBinding.llPointLineInfo.visibility = View.GONE

        sheetBinding.llButtonContainer.isClickable = true
        sheetBinding.llButtonContainer.isFocusable = true
        sheetBinding.llButtonContainer.isPressed = false
        sheetBinding.llButtonContainer.isSelected = false
        sheetBinding.llButtonContainer.jumpDrawablesToCurrentState()

        val isLineCode = isLineCodeFromCodeId(point.codeId)

        if (isLineCode) {
            sheetBinding.txtPointId.text = point.id
            sheetBinding.txtPointId.visibility = View.VISIBLE
            sheetBinding.llCodeIdContainer.visibility = View.VISIBLE
            sheetBinding.tvCodeId.text = point.codeId.ifEmpty { "No code" }
        } else {
            sheetBinding.txtPointId.text = point.id
            sheetBinding.txtPointId.visibility = View.VISIBLE
            sheetBinding.llCodeIdContainer.visibility = View.GONE
        }

        val formattedTime = formatTimestamp(point.ts)
        val pointInfo = "$formattedTime"
        sheetBinding.tvSegmentInfo.text = pointInfo
        sheetBinding.clLineMenu.visibility = View.GONE

        sheetBinding.btnCloseLineSegment.setOnClickListener {
            hideLineSegmentDetailsBottomSheet()
        }

        sheetBinding.btnMenu.setOnClickListener {
            sheetBinding.clLineMenu.visibility =
                if (sheetBinding.clLineMenu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        sheetBinding.llContinueCollect.visibility = View.GONE

        setupBottomSheetClickToHideMenu(bottomSheetView, sheetBinding)

        setupSwipeGestureForPointLineSelection(bottomSheetView, sheetBinding)

        bottomSheetView.visibility = View.VISIBLE
        bottomSheetView.alpha = 0f

        bottomSheetView.post {
            val height = bottomSheetView.height
            bottomSheetView.translationY = height.toFloat()
            bottomSheetView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start()
        }
    }

    private fun showEditLineBottomSheet(lineSegment: ClickablePolylineOverlay) {
        hideCollectPointBottomSheet(finalizeSegment = false)

        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        highlightedLineOverlay?.unhighlight()
        lineSegment.highlight(primaryColor)
        highlightedLineOverlay = lineSegment
        binding.mapView.invalidate()

        val bottomSheetView = binding.bottomSheetEditLine.root
        val sheetBinding = binding.bottomSheetEditLine

        bottomSheetView.isClickable = true
        bottomSheetView.isFocusable = true

        bottomSheetView.elevation = 10f * resources.displayMetrics.density
        bottomSheetView.translationZ = 10f * resources.displayMetrics.density

        currentEditLineBinding = sheetBinding

        val points = lineSegment.labeledPoints.toMutableList()

        val codeId = lineSegment.codeId.ifEmpty { "No code" }
        sheetBinding.tvCodeId.text = codeId

        val actualCodeId = lineSegment.codeId
        val codeDescription = getCodeDescription(actualCodeId)
        sheetBinding.tvCodeDescription.text = codeDescription.ifEmpty { "No code" }

        val actualPoints = lineSegment.actualPoints
        val isShapeClosed = if (lineSegment.isClosed) {
            true
        } else {
            actualPoints.isNotEmpty() && actualPoints.size >= 2 &&
                    actualPoints.first().latitude == actualPoints.last().latitude &&
                    actualPoints.first().longitude == actualPoints.last().longitude
        }

        sheetBinding.cbClosedLine.isChecked = isShapeClosed

        val pointText = if (points.size == 1) {
            "${points.size} Point"
        } else {
            "${points.size} Points"
        }
        sheetBinding.tvPointsCount.text = pointText

        sheetBinding.rvPoints.layoutManager = LinearLayoutManager(requireContext())

        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val adapter = recyclerView.adapter as? EditPointAdapter ?: return false
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition
                adapter.moveItem(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            }

            override fun isLongPressDragEnabled(): Boolean {
                return false
            }
        })

        val adapter = EditPointAdapter(
            points,
            onRemoveClick = { position ->
                val currentAdapter = sheetBinding.rvPoints.adapter as? EditPointAdapter
                if (currentAdapter != null && currentAdapter.itemCount > 2) {
                    // Get the point before removing it
                    val pointToRemove = currentAdapter.getPoints()[position]
                    currentAdapter.removePoint(position)

                    // Update the point's codeId to empty in collectedLabeledPoints
                    val pointIndex = collectedLabeledPoints.indexOfFirst { it.id == pointToRemove.id }
                    if (pointIndex >= 0) {
                        collectedLabeledPoints[pointIndex] = collectedLabeledPoints[pointIndex].copy(codeId = "")
                    }

                    // Update markers to reflect the codeId change
                    updateMarkersForZoom()

                    val newPointText = if (currentAdapter.itemCount == 1) {
                        "${currentAdapter.itemCount} Point"
                    } else {
                        "${currentAdapter.itemCount} Points"
                    }
                    sheetBinding.tvPointsCount.text = newPointText
                } else {
                    Toast.makeText(
                        requireContext(),
                        "A line must have at least 2 points",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDragStart = { holder ->
                itemTouchHelper.startDrag(holder)
            }
        )
        sheetBinding.rvPoints.adapter = adapter
        itemTouchHelper.attachToRecyclerView(sheetBinding.rvPoints)

        currentEditLineAdapter = adapter

        sheetBinding.llCodeValue.setOnClickListener {
        }

        sheetBinding.tvAddPoint.setOnClickListener {
            // Enter point-selection mode: pick an existing point to append to this line
            isSelectingPointForEditLine = true
            pendingEditLineSegment = lineSegment
            hideEditLineBottomSheet()
            showObjectListBottomSheet()
        }

        sheetBinding.cbClosedLine.setOnCheckedChangeListener { _, isChecked ->
        }

        sheetBinding.btnSaveEdit.setOnClickListener {
            val currentAdapter = sheetBinding.rvPoints.adapter as? EditPointAdapter
            if (currentAdapter != null && currentAdapter.itemCount >= 2) {
                val reorderedPoints = currentAdapter.getPoints()
                val isClosed = sheetBinding.cbClosedLine.isChecked

                val geoPoints = reorderedPoints.map { it.geoPoint }

                var totalLength = 0.0
                for (i in 0 until geoPoints.size - 1) {
                    totalLength += geoPoints[i].distanceToAsDouble(geoPoints[i + 1])
                }
                if (isClosed && geoPoints.size >= 2) {
                    totalLength += geoPoints.last().distanceToAsDouble(geoPoints.first())
                }

                val wasHighlighted = highlightedLineOverlay == lineSegment

                // Update collectedLabeledPoints to match the reordered points
                // This ensures the order persists when continuing collection
                val codeId = lineSegment.codeId

                // Ensure all reordered points have the correct codeId
                // This is important for newly added points that might not have the codeId set
                val pointsWithCorrectCodeId = reorderedPoints.map { point ->
                    if (point.codeId != codeId) {
                        point.copy(codeId = codeId)
                    } else {
                        point
                    }
                }

                // Find all points in collectedLabeledPoints that belong to this line segment
                // Include points that might have been added with empty or different codeId
                val pointsToReorder = collectedLabeledPoints.filter { point ->
                    pointsWithCorrectCodeId.any { it.id == point.id }
                }

                // Only proceed with reordering if we found matching points
                if (pointsToReorder.isNotEmpty()) {
                    // Find the first index where any of these points appear
                    val firstIndex = collectedLabeledPoints.indexOfFirst { point ->
                        pointsToReorder.any { it.id == point.id }
                    }

                    // Remove all the old points from collectedLabeledPoints
                    collectedLabeledPoints.removeAll(pointsToReorder)

                    // Insert the reordered points (with correct codeId) at the same starting position
                    collectedLabeledPoints.addAll(firstIndex, pointsWithCorrectCodeId)
                }


                OsmdroidPolylineHelper.removePolyline(binding.mapView, lineSegment)
                completedLineOverlays.remove(lineSegment)

                val solidColor = ContextCompat.getColor(requireContext(), R.color.slate_gray_light)
                val updatedPolyline = ClickablePolylineOverlay(
                    geoPoints,
                    solidColor,
                    6f,
                    closed = isClosed
                )

                updatedPolyline.codeId = lineSegment.codeId
                updatedPolyline.pointCount = pointsWithCorrectCodeId.size
                updatedPolyline.length = totalLength
                updatedPolyline.labeledPoints = pointsWithCorrectCodeId
                updatedPolyline.isClosed = isClosed

                updatedPolyline.setOnClickListener {
                    handleLineSegmentClick(updatedPolyline)
                }

                binding.mapView.overlays.add(updatedPolyline)
                binding.mapView.invalidate()
                completedLineOverlays.add(updatedPolyline)

                ensurePointClickHandlerAtEnd()

                updateMarkersForZoom()

                bringLocationMarkerToTop()

                if (wasHighlighted) {
                    val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
                    updatedPolyline.highlight(primaryColor)
                    highlightedLineOverlay = updatedPolyline
                    binding.mapView.invalidate()
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    "A line must have at least 2 points",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            hideEditLineBottomSheet()
        }

        sheetBinding.btnCloseEditLine.setOnClickListener {
            hideEditLineBottomSheet()
        }

        bottomSheetView.visibility = View.VISIBLE
        bottomSheetView.alpha = 0f

        bottomSheetView.post {
            val height = bottomSheetView.height
            bottomSheetView.translationY = height.toFloat()
            bottomSheetView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start()
        }
    }

    private fun hideEditLineBottomSheet() {
        val bottomSheetView = binding.bottomSheetEditLine.root
        val height = bottomSheetView.height
        bottomSheetView.animate()
            .alpha(0f)
            .translationY(if (height > 0) height.toFloat() else 500f)
            .setDuration(300)
            .withEndAction {
                bottomSheetView.visibility = View.GONE
                bottomSheetView.isClickable = false
                bottomSheetView.isFocusable = false
                highlightedLineOverlay?.unhighlight()
                highlightedLineOverlay = null
                currentEditLineAdapter = null
                currentEditLineBinding = null
                binding.mapView.invalidate()
                if (binding.bottomSheetCollectPoint.root.visibility == View.GONE &&
                    currentLineCodeId != null) {
                    showCollectPointBottomSheet()
                }
            }
            .start()
    }

    private fun showNewPointBottomSheet(lineSegment: ClickablePolylineOverlay?) {
        val bottomSheetView = binding.bottomSheetNewPoint.root
        val sheetBinding = binding.bottomSheetNewPoint

        bottomSheetView.isClickable = true
        bottomSheetView.isFocusable = true

        bottomSheetView.elevation = 10f * resources.displayMetrics.density
        bottomSheetView.translationZ = 10f * resources.displayMetrics.density

        val nextPointId = if (pointIdPrefix != null) {
            "$pointIdPrefix$pointIdNumericCounter"
        } else {
            pointCounter.toString()
        }
        sheetBinding.etPointId.setText(nextPointId)
        sheetBinding.etPointId.hint = nextPointId

        val defaultCodeId = lineSegment?.codeId?.ifEmpty { "P" } ?: selectedPointCodeId.ifEmpty { "P" }
        sheetBinding.tvPointType.text = defaultCodeId
        val defaultIndicator = if (isLineCodeFromCodeId(defaultCodeId)) IndicatorType.LINE else IndicatorType.POINT
        updatePointTypeIndicator(sheetBinding.viewTypeDot, defaultIndicator)

        val location = currentLocation ?: locationMarker?.position
        location?.let {
            sheetBinding.etLongitude.setText(it.longitude.toString())
            sheetBinding.etLatitude.setText(it.latitude.toString())
        }

        sheetBinding.llPointTypeSelector.setOnClickListener {
            hideNewPointBottomSheet()
            showSelectCodeBottomSheet(null) { codeId, indicatorType ->
                selectedPointCodeId = codeId
                selectedPointIndicatorType = indicatorType
                sheetBinding.tvPointType.text = codeId
                updatePointTypeIndicator(sheetBinding.viewTypeDot, indicatorType)
                showNewPointBottomSheet(lineSegment)
            }
        }

        sheetBinding.llCoordinateSystem.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "Coordinate system selector coming soon",
                Toast.LENGTH_SHORT
            ).show()
        }

        sheetBinding.btnCloseNewPoint.setOnClickListener {
            hideNewPointBottomSheet()
        }

        sheetBinding.btnSaveNewPoint.setOnClickListener {
            val pointId = sheetBinding.etPointId.text.toString()
            val note = sheetBinding.etNote.text.toString()
            val longitude = sheetBinding.etLongitude.text.toString()
            val latitude = sheetBinding.etLatitude.text.toString()
            val elevation = sheetBinding.etElevation.text.toString()
            val codeId = sheetBinding.tvPointType.text.toString()

            if (pointId.isEmpty()) {
                Toast.makeText(requireContext(), "Point ID is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (longitude.isEmpty() || latitude.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Longitude and Latitude are required",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val longitudeValue = longitude.toDoubleOrNull()
            val latitudeValue = latitude.toDoubleOrNull()
            val elevationValue = elevation.toDoubleOrNull() ?: 0.0

            if (longitudeValue == null || latitudeValue == null) {
                Toast.makeText(requireContext(), "Invalid coordinates", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val geoPoint = GeoPoint(latitudeValue, longitudeValue)

            val timestamp =
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                    .apply {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }.format(java.util.Date())

            val coords = listOf(longitudeValue, latitudeValue)

            val actualPointId = if (pointId.length == 1 && pointId[0].isLetter()) {
                "${pointId}1"
            } else {
                pointId
            }

            val newPoint = LabeledPoint(
                id = actualPointId,
                codeId = codeId,
                coords = coords,
                elevation = elevationValue,
                ts = timestamp
            )

            if (lineSegment != null) {
                addExistingPointToLineSegment(newPoint, lineSegment)
            } else {
                // Add as a new point (or line vertex) to the main collection using the provided coordinates
                val indicatorTypeForNewPoint = if (isLineCodeFromCodeId(codeId)) IndicatorType.LINE else IndicatorType.POINT
                selectedPointIndicatorType = indicatorTypeForNewPoint
                selectedPointCodeId = codeId
                addPointAtLocation(geoPoint, actualPointId, codeId, indicatorTypeForNewPoint)
            }

            val pointIdToParse = actualPointId
            val pointIdRegex = Regex("^([a-zA-Z])(\\d+)$")
            val numericOnlyRegex = Regex("^\\d+$")

            when {
                pointIdRegex.matches(pointIdToParse) -> {
                    val matchResult = pointIdRegex.find(pointIdToParse)
                    val prefix = matchResult?.groupValues?.get(1) ?: ""
                    val numericPart = matchResult?.groupValues?.get(2)?.toIntOrNull() ?: 1

                    pointIdPrefix = prefix
                    pointIdNumericCounter = numericPart + 1
                }
                pointId.length == 1 && pointId[0].isLetter() -> {
                    pointIdPrefix = pointId[0].toString()
                    pointIdNumericCounter = 2
                }
                numericOnlyRegex.matches(pointIdToParse) -> {
                    val numericValue = pointIdToParse.toIntOrNull() ?: pointCounter
                    pointCounter = numericValue + 1
                    pointIdPrefix = null
                    pointIdNumericCounter = 1
                }
                else -> {
                    val numericValue = pointIdToParse.filter { it.isDigit() }.toIntOrNull()
                    if (numericValue != null) {
                        pointCounter = numericValue + 1
                        pointIdPrefix = null
                        pointIdNumericCounter = 1
                    } else {
                        pointCounter++
                    }
                }
            }

            hideNewPointBottomSheet()
        }

        bottomSheetView.visibility = View.VISIBLE
        bottomSheetView.alpha = 0f

        bottomSheetView.post {
            val height = bottomSheetView.height
            bottomSheetView.translationY = height.toFloat()
            bottomSheetView.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start()
        }
    }

    private fun addExistingPointToLineSegment(newPoint: LabeledPoint, lineSegment: ClickablePolylineOverlay) {
        // Ensure the new point has the correct codeId matching the line segment
        val pointWithCorrectCodeId = if (newPoint.codeId != lineSegment.codeId) {
            newPoint.copy(codeId = lineSegment.codeId)
        } else {
            newPoint
        }

        val updatedPoints = lineSegment.labeledPoints.toMutableList()
        updatedPoints.add(pointWithCorrectCodeId)
        lineSegment.labeledPoints = updatedPoints

        lineSegment.pointCount = updatedPoints.size

        val geoPoints = updatedPoints.map { it.geoPoint }
        var totalLength = 0.0
        for (i in 0 until geoPoints.size - 1) {
            totalLength += geoPoints[i].distanceToAsDouble(geoPoints[i + 1])
        }
        if (lineSegment.isClosed && geoPoints.size >= 2) {
            totalLength += geoPoints.last().distanceToAsDouble(geoPoints.first())
        }
        lineSegment.length = totalLength

        val finalGeoPoints =
            if (lineSegment.isClosed && geoPoints.isNotEmpty() && geoPoints.first() != geoPoints.last()) {
                geoPoints + geoPoints.first()
            } else {
                geoPoints
            }

        lineSegment.setPoints(finalGeoPoints)
        lineSegment.pointCount = updatedPoints.size
        lineSegment.length = totalLength
        lineSegment.labeledPoints = updatedPoints

        // Update the point's codeId in collectedLabeledPoints as well
        val pointIndex = collectedLabeledPoints.indexOfFirst { it.id == newPoint.id }
        if (pointIndex >= 0) {
            collectedLabeledPoints[pointIndex] = pointWithCorrectCodeId
        }

        updateMarkersForZoom()

        binding.mapView.invalidate()

        currentEditLineAdapter?.let { adapter ->
            adapter.updatePoints(updatedPoints)
        }

        currentEditLineBinding?.let { editSheetBinding ->
            val pointText = if (updatedPoints.size == 1) {
                "${updatedPoints.size} Point"
            } else {
                "${updatedPoints.size} Points"
            }
            editSheetBinding.tvPointsCount.text = pointText
        }
    }

    private fun hideNewPointBottomSheet() {
        val bottomSheetView = binding.bottomSheetNewPoint.root
        val height = bottomSheetView.height
        bottomSheetView.animate()
            .alpha(0f)
            .translationY(if (height > 0) height.toFloat() else 500f)
            .setDuration(300)
            .withEndAction {
                bottomSheetView.visibility = View.GONE
                bottomSheetView.isClickable = false
                bottomSheetView.isFocusable = false
            }
            .start()
    }

    private fun showConfirmDialogBottomSheet(
        onYesClick: () -> Unit = {},
        onNoClick: () -> Unit = {}
    ) {
        if (!isAdded || context == null) {
            return
        }

        confirmDialogBottomSheet?.dismiss()
        confirmDialogBottomSheet = null

        val dialogContext = activity ?: context ?: return

        val dialog = BottomSheetDialog(dialogContext, R.style.BottomSheetDialogTheme)
        val sheetBinding = BottomSheetConfirmDialogBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.btnYes.setOnClickListener {
            dialog.dismiss()
            onYesClick()
        }

        sheetBinding.btnNo.setOnClickListener {
            dialog.dismiss()
            onNoClick()
        }

        dialog.setOnDismissListener {
            if (confirmDialogBottomSheet == dialog) {
                confirmDialogBottomSheet = null
            }
        }

        confirmDialogBottomSheet = dialog

        try {
            if (isAdded) {
                dialog.show()
            }
        } catch (e: Exception) {
            confirmDialogBottomSheet = null
        }
    }

    private fun hideConfirmDialogBottomSheet() {
        confirmDialogBottomSheet?.dismiss()
        confirmDialogBottomSheet = null
    }

    private fun redrawPolyline() {
        polylineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
        }
        polylineOverlay = null

        liveTrackingLineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
        }
        liveTrackingLineOverlay = null

        binding.mapView.invalidate()

        val lineCodePoints = getConsecutiveLineCodePoints()

        if (lineCodePoints.size >= 2) {
            val geoPoints = lineCodePoints.map { it.geoPoint }

            if (isShapeClosed) {
                val greyColor = ContextCompat.getColor(requireContext(), R.color.slate_gray_light)
                polylineOverlay = OsmdroidPolylineHelper.createPolyline(
                    binding.mapView,
                    geoPoints,
                    greyColor,
                    6f,
                    closed = true,
                    dashed = false
                )
            } else {
                val greyColor = ContextCompat.getColor(requireContext(), R.color.slate_gray_light)
                polylineOverlay = OsmdroidPolylineHelper.createPolyline(
                    binding.mapView,
                    geoPoints,
                    greyColor,
                    6f,
                    closed = false,
                    dashed = false
                )
            }
        }

        if (lineCodePoints.isNotEmpty()) {
            updateLiveTrackingLine()
        }

        bringLabelsToTop()
        bringLocationMarkerToTop()
    }

    private fun updateLiveTrackingLine() {
        liveTrackingLineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
        }

        val lineCodePoints = getConsecutiveLineCodePoints()
        if (lineCodePoints.isNotEmpty()) {
            val referencePoint = if (addFromBeginning) {
                lineCodePoints.first().geoPoint
            } else {
                lineCodePoints.last().geoPoint
            }
            val currentMarkerPosition = locationMarker?.position ?: currentLocation

            if (currentMarkerPosition != null && referencePoint != currentMarkerPosition) {
                val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
                liveTrackingLineOverlay = OsmdroidPolylineHelper.createPolyline(
                    binding.mapView,
                    listOf(referencePoint, currentMarkerPosition),
                    primaryColor,
                    6f,
                    closed = false,
                    dashed = true
                )
            }
        }

        bringLabelsToTop()
        bringLocationMarkerToTop()
    }

    private fun redrawPolylineAsClosed() {
        isShapeClosed = true
        redrawPolyline()
    }

    private fun updateMarkersForZoom() {
        if (collectedLabeledPoints.isEmpty()) return

        collectedPointMarkers.forEach { marker ->
            binding.mapView.overlays.remove(marker)
            markerToPointMap.remove(marker)
        }
        collectedPointMarkers.clear()

        val projection = binding.mapView.projection
        val visibleLabelIndices = getVisibleLabelIndices(collectedLabeledPoints, projection)

        // Pass 1: Draw Points (Circles)
        collectedLabeledPoints.forEachIndexed { index, labeledPoint ->
            val isLineSelected = highlightedLineOverlay?.labeledPoints?.any { it == labeledPoint } == true
            val isSelected = labeledPoint == selectedPoint || isLineSelected

            // Only draw point marker if we are NOT showing the full label with point included
            // Actually, we want to separate them.
            // Let's draw ALL points as circles first.

            val bitmap = createPointOnlyBitmap(isSelected = isSelected)
            val marker = OsmdroidMarkerHelper.createMarker(
                binding.mapView,
                bitmap,
                labeledPoint.geoPoint,
                0.5f,
                0.5f
            )
            markerToPointMap[marker] = labeledPoint
            marker.setOnMarkerClickListener { clickedMarker, _ ->
                markerToPointMap[clickedMarker]?.let { point ->
                    showPointDetailsBottomSheet(point)
                }
                true
            }
            collectedPointMarkers.add(marker)
        }

        // Pass 2: Draw Labels (Text only)
        collectedLabeledPoints.forEachIndexed { index, labeledPoint ->
            val showLabel = visibleLabelIndices.contains(index)
            // Always show labels for points in the currently active line to ensure user knows what is connecting
            val isCurrentLinePoint = currentLineCodeId != null && labeledPoint.codeId == currentLineCodeId

            val isLineSelected = highlightedLineOverlay?.labeledPoints?.any { it == labeledPoint } == true
            val isSelected = labeledPoint == selectedPoint || isLineSelected

            if (showLabel || isCurrentLinePoint || isSelected) {

                // createLabeledPointBitmap will now optionally NOT draw the point circle
                val (bitmap, anchorY) = createLabeledPointBitmap(
                    labeledPoint.id,
                    labeledPoint.codeId,
                    isSelected = isSelected,
                    drawPoint = false // New param: Don't draw circle, just text
                )

                // If drawPoint=false, the anchor needs to be adjusted because the bitmap doesn't include the point circle space in the same way?
                // Actually my createLabeledPointBitmap logic calculates height including point radius.
                // I should update createLabeledPointBitmap to just return text bitmap and appropriate anchor.
                // Or better: keep the same bitmap dimensions but transparent circle, so anchor logic remains identical.

                val marker = OsmdroidMarkerHelper.createMarker(
                    binding.mapView,
                    bitmap,
                    labeledPoint.geoPoint,
                    0.5f,
                    anchorY
                )
                markerToPointMap[marker] = labeledPoint
                // Labels should also be clickable
                marker.setOnMarkerClickListener { clickedMarker, _ ->
                    markerToPointMap[clickedMarker]?.let { point ->
                        showPointDetailsBottomSheet(point)
                    }
                    true
                }
                collectedPointMarkers.add(marker)
            }
        }

        bringLocationMarkerToTop()

        binding.mapView.invalidate()
    }

    private fun bringLabelsToTop() {
        val markers = binding.mapView.overlays.filterIsInstance<Marker>()
        // Only reorder if markers are present and not already at the end
        if (markers.isNotEmpty()) {
            val nonMarkers = binding.mapView.overlays.filter { it !is Marker }
            binding.mapView.overlays.clear()
            binding.mapView.overlays.addAll(nonMarkers)
            binding.mapView.overlays.addAll(markers)
        }
    }

    private fun bringLocationMarkerToTop() {
        locationMarker?.let { marker ->
            // location marker is already in overlays list (it's a Marker), so bringLabelsToTop handles it too.
            // But we want location marker to be absolutely on TOP of other markers?
            // "Label must above all point and line elements" -> collected points labels.
            // Location marker is the user's position. Usually that should be on top of everything.
            binding.mapView.overlays.remove(marker)
            binding.mapView.overlays.add(marker)
        }
    }

    private fun getVisibleLabelIndices(
        points: List<LabeledPoint>,
        projection: org.osmdroid.views.Projection
    ): Set<Int> {
        val currentZoom = binding.mapView.zoomLevelDouble
        val maxZoom = binding.mapView.maxZoomLevel

        if (currentZoom >= maxZoom - 0.1) {
            return points.indices.toSet()
        }

        val visibleIndices = mutableSetOf<Int>()
        val visiblePixelPoints = mutableListOf<android.graphics.Point>()
        val density = resources.displayMetrics.density
        val overlapX = labelOverlapDistanceX * density
        val overlapY = labelOverlapDistanceY * density

        val screenWidth = binding.mapView.width
        val screenHeight = binding.mapView.height
        val buffer = 100 * density // How far off-screen we consider points "visible" for occlusion purposes

        for (i in points.indices) {
            val pixelPoint = android.graphics.Point()
            projection.toPixels(points[i].geoPoint, pixelPoint)

            // If the point is completely off-screen (with buffer), it shouldn't occlude on-screen points
            // But if it's visible or close to visible, we process it
            if (pixelPoint.x < -buffer || pixelPoint.x > screenWidth + buffer ||
                pixelPoint.y < -buffer || pixelPoint.y > screenHeight + buffer) {
                // If it's too far off screen, we assume it's "visible" in the sense that we don't hide it 
                // (it's off screen anyway), but importantly we don't add it to visiblePixelPoints
                // so it doesn't hide OTHER points.
                // However, the function returns indices to SHOW. 
                // If we return it, the code will try to draw it (and OS removes it).
                visibleIndices.add(i)
                continue
            }

            var isOverlapping = false
            for (visiblePoint in visiblePixelPoints) {
                val dx = Math.abs(pixelPoint.x - visiblePoint.x)
                val dy = Math.abs(pixelPoint.y - visiblePoint.y)

                if (dx < overlapX && dy < overlapY) {
                    isOverlapping = true
                    break
                }
            }

            if (!isOverlapping) {
                visibleIndices.add(i)
                visiblePixelPoints.add(pixelPoint)
            }
        }

        return visibleIndices
    }

    private fun createPointOnlyBitmap(isSelected: Boolean = false): Bitmap {
        val density = resources.displayMetrics.density
        val radius = 6 * density
        val size = (radius * 2).toInt()

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) {
                ContextCompat.getColor(requireContext(), R.color.primary)
            } else {
                ContextCompat.getColor(requireContext(), R.color.stakeout_connection_line)
            }
            style = android.graphics.Paint.Style.FILL
        }

        val centerX = size / 2f
        val centerY = size / 2f

        canvas.drawCircle(centerX, centerY, radius, fillPaint)

        if (isSelected) {
            val whitePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = android.graphics.Paint.Style.FILL
            }
            canvas.drawCircle(centerX, centerY, radius / 2f, whitePaint)
        }

        return bitmap
    }

    private fun createLabeledPointBitmap(
        id: String,
        code: String,
        isSelected: Boolean = false,
        drawPoint: Boolean = true
    ): Pair<Bitmap, Float> {
        val density = resources.displayMetrics.density
        val padding = 8 * density
        val idTextSize = 12 * density
        val codeTextSize = 10 * density
        val pointRadius = 6 * density
        val textSpacing = 4 * density
        val textStrokeWidth = 3 * density

        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)

        val idPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = idTextSize
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            color = if (isSelected) primaryColor else android.graphics.Color.BLACK
            style = android.graphics.Paint.Style.FILL
        }
        val codePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = codeTextSize
            typeface = android.graphics.Typeface.DEFAULT
            color = if (isSelected) primaryColor else ContextCompat.getColor(requireContext(), R.color.text_secondary)
            style = android.graphics.Paint.Style.FILL
        }

        val idStrokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = idTextSize
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = textStrokeWidth
            strokeJoin = android.graphics.Paint.Join.ROUND
        }
        val codeStrokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = codeTextSize
            typeface = android.graphics.Typeface.DEFAULT
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = textStrokeWidth
            strokeJoin = android.graphics.Paint.Join.ROUND
        }

        val idBounds = android.graphics.Rect()
        idPaint.getTextBounds(id, 0, id.length, idBounds)
        val codeBounds = android.graphics.Rect()
        codePaint.getTextBounds(code, 0, code.length, codeBounds)

        val maxWidth = maxOf(idBounds.width(), codeBounds.width()).toFloat()
        val width = (maxWidth + padding * 2 + textStrokeWidth * 2).toInt()

        val idHeight = idBounds.height().toFloat()
        val codeHeight = codeBounds.height().toFloat()
        val circleArea = pointRadius * 2

        val totalHeight =
            padding + idHeight + textSpacing + circleArea + textSpacing + codeHeight + padding
        val height = totalHeight.toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val pointX = width / 2f
        val pointY = padding + idHeight + textSpacing + pointRadius

        val pointPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) {
                ContextCompat.getColor(requireContext(), R.color.primary)
            } else {
                ContextCompat.getColor(requireContext(), R.color.stakeout_connection_line)
            }
            style = android.graphics.Paint.Style.FILL
        }

        val idX = (width - idBounds.width()) / 2f
        val idY = padding + idHeight
        canvas.drawText(id, idX, idY, idStrokePaint)
        canvas.drawText(id, idX, idY, idPaint)

        if (drawPoint) {
            canvas.drawCircle(pointX, pointY, pointRadius, pointPaint)

            if (isSelected) {
                val whitePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.WHITE
                    style = android.graphics.Paint.Style.FILL
                }
                canvas.drawCircle(pointX, pointY, pointRadius / 2f, whitePaint)
            }
        }

        val codeX = (width - codeBounds.width()) / 2f
        val codeY = pointY + pointRadius + textSpacing + codeHeight
        canvas.drawText(code, codeX, codeY, codeStrokePaint)
        canvas.drawText(code, codeX, codeY, codePaint)

        val anchorY = pointY / totalHeight
        return Pair(bitmap, anchorY)
    }

    private fun clearCollectedPoints() {
        collectedPointMarkers.forEach { marker ->
            OsmdroidMarkerHelper.removeMarker(binding.mapView, marker)
        }
        collectedPointMarkers.clear()
        markerToPointMap.clear()
        collectedLabeledPoints.clear()

        polylineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
        }
        polylineOverlay = null

        liveTrackingLineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
        }
        liveTrackingLineOverlay = null
        completedLineOverlays.forEach {
            OsmdroidPolylineHelper.removePolyline(binding.mapView, it)
            if (it is ClickablePolylineOverlay) {
                // lineSegmentPointMarkers logic removed
            }
        }
        completedLineOverlays.clear()
        highlightedLineOverlay = null
        isShapeClosed = false
        lineSegmentStartIndex = 0
        currentLineCodeId = null
    }

    private var currentPinText: String = "M"

    private fun createLocationPin(pinText: String = "M") {
        currentPinText = pinText
        updatePinBitmap()
    }

    private fun updatePinBitmap() {
        val baseSize = 60 * resources.displayMetrics.density
        val padding = baseSize * 0.25f
        val width = (baseSize + padding * 2).toInt()
        val height = (baseSize + padding * 2).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.translate(padding, padding)

        val pinDrawable = CustomLocationPinDrawable(requireContext(), currentPinText, currentHeading)
        pinDrawable.setBounds(0, 0, baseSize.toInt(), baseSize.toInt())
        pinDrawable.draw(canvas)

        val circleCenterY = (baseSize * 0.70f + padding) / height
        val anchorX = 0.5f
        val anchorY = circleCenterY

        val bitmapDrawable = android.graphics.drawable.BitmapDrawable(resources, bitmap)

        if (locationMarker == null) {
            locationMarker = Marker(binding.mapView).apply {
                icon = bitmapDrawable
                setAnchor(anchorX, anchorY)
                isDraggable = false
                infoWindow = null
                setOnMarkerClickListener { marker, mapView ->
                    val markerPosition = marker.position ?: return@setOnMarkerClickListener true
                    val nearestPoint = findNearestPoint(markerPosition)
                    if (nearestPoint != null) {
                        showPointDetailsBottomSheet(nearestPoint)
                        true
                    } else {
                        true
                    }
                }
            }
            binding.mapView.overlays.add(locationMarker)
        } else {
            locationMarker?.icon = bitmapDrawable
            locationMarker?.setAnchor(anchorX, anchorY)
            locationMarker?.infoWindow = null
            locationMarker?.setOnMarkerClickListener { marker, mapView ->
                val markerPosition = marker.position ?: return@setOnMarkerClickListener true
                val nearestPoint = findNearestPoint(markerPosition)
                if (nearestPoint != null) {
                    showPointDetailsBottomSheet(nearestPoint)
                    true
                } else {
                    true
                }
            }
            binding.mapView.invalidate()
        }
    }

    private fun preventDoubleTapZoomOnNonMapViews() {
        val protectedViews = mutableListOf<View>()

        fun collectProtectedViews(view: View) {
            protectedViews.add(view)
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    collectProtectedViews(view.getChildAt(i))
                }
            }
        }

        collectProtectedViews(binding.leftControlPanel)
        listOf(
            binding.clMenu,
            binding.llRightPanel,
            binding.imgBack,
            binding.imgMenu,
            binding.btnCollect
        ).forEach { view ->
            view?.let { collectProtectedViews(it) }
        }

        doubleTapInterceptorOverlay = DoubleTapInterceptorOverlay(protectedViews)
        binding.mapView.overlays.add(0, doubleTapInterceptorOverlay)
    }

    private fun setupCompassOrientation() {
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        orientationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

        sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        val rotationMatrix = FloatArray(9)
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        val orientationAngles = FloatArray(3)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                        currentHeading = (azimuth + 360) % 360
                    }

                    Sensor.TYPE_ORIENTATION -> {
                        currentHeading = (event.values[0] + 360) % 360
                    }
                }
                updatePinOrientation()
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        orientationSensor?.let { sensor ->
            sensorManager?.registerListener(
                sensorEventListener,
                sensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    private fun updatePinOrientation() {
        if (locationMarker != null) {
            updatePinBitmap()
        }
    }

    fun updatePinText(newText: String) {
        currentPinText = newText
        if (locationMarker != null) {
            updatePinBitmap()
        }
    }

    private fun setupLocationTracking() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 50)
            .setMinUpdateIntervalMillis(16)
            .setMaxUpdateDelayMillis(50)
            .setMinUpdateDistanceMeters(0f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    updateLocationMarker(location.latitude, location.longitude)
                }
            }
        }

        try {
            fusedLocationClient?.lastLocation?.addOnSuccessListener { location ->
                location?.let {
                    updateLocationMarker(it.latitude, it.longitude)
                }
            }

            fusedLocationClient?.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient?.removeLocationUpdates(it)
        }
    }

    private fun updateLocationMarker(latitude: Double, longitude: Double) {
        val newLocation = GeoPoint(latitude, longitude)

        if (locationMarker == null) {
            currentLocation = newLocation
            targetLocation = newLocation
            createLocationPin()
            locationMarker?.position = newLocation

            if (isFirstLocationUpdate && mapController != null) {
                mapController?.setCenter(newLocation)
                isFirstLocationUpdate = false
            }
            return
        }

        if (isFirstLocationUpdate && mapController != null) {
            currentLocation = newLocation
            targetLocation = newLocation
            locationMarker?.position = newLocation
            mapController?.setCenter(newLocation)
            isFirstLocationUpdate = false
            return
        }

        targetLocation = newLocation
        currentLocation = newLocation

        if (getConsecutiveLineCodePoints().isNotEmpty()) {
            updateLiveTrackingLine()
        }

        bringLocationMarkerToTop()

        if (!isAnimatingLocation) {
            isAnimatingLocation = true
            locationUpdateHandler.post(locationSmoothingRunnable)
        }
    }

    private fun smoothMoveToTarget() {
        val target = targetLocation ?: return
        val marker = locationMarker ?: return
        val currentPos = marker.position ?: return

        val smoothingFactor = 0.15f

        val newLat = currentPos.latitude + (target.latitude - currentPos.latitude) * smoothingFactor
        val newLon =
            currentPos.longitude + (target.longitude - currentPos.longitude) * smoothingFactor

        val smoothedPosition = GeoPoint(newLat, newLon)
        marker.position = smoothedPosition

        if (getConsecutiveLineCodePoints().isNotEmpty()) {
            updateLiveTrackingLine()
        }

        bringLocationMarkerToTop()
        binding.mapView.invalidate()

        val distance = smoothedPosition.distanceToAsDouble(target)
        if (distance < 0.01) {
            marker.position = target
            binding.mapView.invalidate()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        setupLocationTracking()
        orientationSensor?.let { sensor ->
            sensorEventListener?.let { listener ->
                sensorManager?.registerListener(
                    listener,
                    sensor,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        stopLocationUpdates()
        isAnimatingLocation = false
        locationUpdateHandler.removeCallbacks(locationSmoothingRunnable)
        sensorEventListener?.let {
            sensorManager?.unregisterListener(it)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentMapAnimator?.cancel()
        currentMapAnimator = null
        isAnimatingLocation = false
        locationUpdateHandler.removeCallbacks(locationSmoothingRunnable)
        stopLocationUpdates()
        sensorEventListener?.let {
            sensorManager?.unregisterListener(it)
        }
        clearCollectedPoints()
        confirmDialogBottomSheet?.dismiss()
        confirmDialogBottomSheet = null
        fusedLocationClient = null
        locationCallback = null
        sensorManager = null
        orientationSensor = null
        sensorEventListener = null
        mapController = null
        locationMarker = null
        currentLocation = null
        targetLocation = null
        rotationGestureOverlay = null
    }
}
