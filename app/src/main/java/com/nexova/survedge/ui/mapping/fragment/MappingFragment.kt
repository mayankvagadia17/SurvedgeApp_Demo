package com.nexova.survedge.ui.mapping.fragment

import android.animation.ValueAnimator
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nexova.survedge.databinding.BottomSheetEditLineBinding
import com.nexova.survedge.databinding.FragmentMappingBinding
import com.nexova.survedge.ui.mapping.adapter.EditPointAdapter
import com.nexova.survedge.ui.mapping.adapter.IndicatorType
import com.nexova.survedge.ui.mapping.overlay.ClickablePolylineOverlay
import com.nexova.survedge.ui.mapping.overlay.DoubleTapInterceptorOverlay
import com.nexova.survedge.ui.mapping.overlay.LabeledPoint
import com.nexova.survedge.ui.mapping.overlay.PointClickHandlerOverlay
import com.nexova.survedge.ui.mapping.overlay.RotationGestureOverlay
import org.osmdroid.api.IMapController
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.util.LinkedList
import android.os.Handler
import com.nexova.survedge.ui.stakeout.model.*
import com.nexova.survedge.ui.mapping.overlay.BullseyeOverlay
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.nexova.survedge.ui.mapping.viewmodel.MappingViewModel
import kotlinx.coroutines.launch
import com.nexova.survedge.ui.mapping.mapper.toLabeledPoint

class MappingFragment : Fragment() {

    lateinit var binding: FragmentMappingBinding
    val viewModel: MappingViewModel by viewModels() 
    lateinit var logic: MappingFragmentLogic
    val helper = MappingFragmentHelper(this)
    internal var mapController: IMapController? = null
    internal var locationMarker: Marker? = null
    internal var fusedLocationClient: FusedLocationProviderClient? = null
    internal var locationCallback: LocationCallback? = null
    internal var isFirstLocationUpdate = true
    internal var sensorManager: SensorManager? = null
    internal var orientationSensor: Sensor? = null
    internal var sensorEventListener: SensorEventListener? = null
    internal var currentHeading: Float = 0f
    internal var currentLocation: GeoPoint? = null
    internal var isLockMode: Boolean = false
    internal var locationRequest: com.google.android.gms.location.LocationRequest? = null
    internal var rotationGestureOverlay: RotationGestureOverlay? = null
    internal var doubleTapInterceptorOverlay: DoubleTapInterceptorOverlay? = null
    internal var pointClickHandlerOverlay: PointClickHandlerOverlay? = null
    internal val collectedPointMarkers = mutableListOf<Marker>()
    internal val markerToPointMap = mutableMapOf<Marker, LabeledPoint>()
    internal var polylineOverlay: Any? = null
    internal var liveTrackingLineOverlay: Any? = null
    internal var pointCounter = 1
    internal var pointIdPrefix: String? = null
    internal var pointIdNumericCounter = 1
    internal var collectedLabeledPoints = LinkedList<LabeledPoint>()
    internal var lastZoomLevel: Double = 0.0
    internal var selectedPointCodeId: String = ""
    internal var selectedPointIndicatorType: IndicatorType = IndicatorType.POINT
    internal var isShapeClosed: Boolean = false
    internal var lineSegmentStartIndex: Int = 0
    internal var addFromBeginning: Boolean = false
    internal var hasStartedNewLine: Boolean = false
    internal var wasCollectingBeforePointDetails: Boolean = false
    internal val completedLineOverlays = mutableListOf<Any>()
    internal var currentLineCodeId: String? = null
    internal var highlightedLineOverlay: ClickablePolylineOverlay? = null
    internal var closingSegmentOverlay: ClickablePolylineOverlay? = null
    internal var isSelectingPointForEditLine: Boolean = false
    internal var pendingEditLineSegment: ClickablePolylineOverlay? = null
    internal var restoreLineSegmentAfterStakeout: ClickablePolylineOverlay? = null
    internal val lineCodeSequenceCounters = mutableMapOf<String, Int>()
    internal var currentEditLineAdapter: EditPointAdapter? = null
    internal var currentEditLineBinding: BottomSheetEditLineBinding? = null
    internal var confirmDialogBottomSheet: BottomSheetDialog? = null
    internal var isDraggingEditLinePoint: Boolean = false
    internal var selectedPoint: LabeledPoint? = null
    internal var isCreatingNewLine: Boolean = false
    internal val newLinePoints = LinkedList<LabeledPoint>()
    internal var newLineOverlay: Any? = null
    internal var newLineCodeId: String = "L1"
    internal var isNewLineClosed: Boolean = false
    internal var isNewLineSaved: Boolean = false
    internal var currentPinText: String = "M"
    internal val labelOverlapDistanceX = 35
    internal val labelOverlapDistanceY = 25
    internal var targetLocation: GeoPoint? = null
    internal var isAnimatingLocation = false
    internal var currentMapAnimator: ValueAnimator? = null
    internal var isMapFitted = false
    internal val locationUpdateHandler = android.os.Handler(Looper.getMainLooper())
    internal val locationSmoothingRunnable = object : Runnable {
        override fun run() {
            smoothMoveToTarget()
            if (isAnimatingLocation) {
                locationUpdateHandler.postDelayed(this, 16)
            }
        }
    }

    // Stakeout state
    internal var stakeoutSession: StakeoutSession? = null
    internal var currentStakeoutMode: StakeoutMode = StakeoutMode.NONE
    internal var currentMeasurement: StakeoutMeasurement? = null
    internal var connectionLineOverlay: Any? = null
    internal var stakeoutProximityCircles = mutableListOf<Any>()
    internal var bullseyeOverlay: BullseyeOverlay? = null
    internal var isInBullseyeMode: Boolean = false
    internal var autoFollowHandler: Handler? = null
    internal var currentCoordinateSystem: CoordinateSystem = CoordinateSystem.LOCAL // Default to Local based on previous "m E" texts

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        logic = MappingFragmentLogic(this, viewModel)
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
        binding.bottomSheetSelectCode.root.isFocusable = false

        binding.bottomSheetObjectList.root.isClickable = false
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
        
        // Stakeout initialization
        logic.setupStakeoutMode()
        helper.setupStakeoutUI()

        binding.imgBack.setOnClickListener {
            requireActivity().onBackPressed()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!logic.onBackPressed()) {
                    val projectFragment = parentFragmentManager.findFragmentByTag("project")
                    if (projectFragment != null) {
                        parentFragmentManager.beginTransaction()
                            .remove(this@MappingFragment)
                            .show(projectFragment)
                            .commit()
                        (activity as? com.nexova.survedge.ui.main.activity.MainActivity)?.setActiveFragment(projectFragment)
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressed()
                    }
                }
            }
        })

        
        // Check for Project ID argument
        arguments?.getLong("project_id")?.let { id ->
            if (id > 0) {
                viewModel.setProjectId(id)
            }
        }
        
        // Observe Database Points
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentPoints.collect { entities ->
                val labeledPoints = entities.map { it.toLabeledPoint() }
                logic.updatePointsFromDatabase(labeledPoints)
            }
        }

        // Observe Database Lines
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.currentLines.collect { lines ->
                logic.updateLinesFromDatabase(lines)
            }
        }
    }

    private fun setupEdgeToEdgeInsets() = logic.setupEdgeToEdgeInsets()

    private fun initializeMap() = helper.initializeMap()

    private fun setupZoomControls() = helper.setupZoomControls()

    internal fun animateZoom(fromZoom: Double, toZoom: Double) = logic.animateZoom(fromZoom, toZoom)

    private fun setupCompassButton() = logic.setupCompassButton()

    internal fun updateCompassRotation() = logic.updateCompassRotation()

    internal fun animateRotationTo(targetAngle: Float) = logic.animateRotationTo(targetAngle)

    private fun setupCenterButton() = logic.setupCenterButton()

    internal fun cancelOngoingAnimations() = logic.cancelOngoingAnimations()

    internal fun animateToLocationWithZoom(targetLocation: GeoPoint, targetZoom: Double) =
        logic.animateToLocationWithZoom(targetLocation, targetZoom)

    internal fun hideBottomNavigation() = logic.hideBottomNavigation()

    internal fun showBottomNavigation() = logic.showBottomNavigation()

    private fun setupPointClickHandler() = logic.setupPointClickHandler()

    internal fun ensurePointClickHandlerAtEnd() = logic.ensurePointClickHandlerAtEnd()

    internal fun findNearestPoint(clickedGeoPoint: GeoPoint) = logic.findNearestPoint(clickedGeoPoint)

    internal fun updateLiveTrackingLine() = logic.updateLiveTrackingLine()

    private fun preventDoubleTapZoomOnNonMapViews() = logic.preventDoubleTapZoomOnNonMapViews()

    internal fun updateMarkersForZoom() = logic.updateMarkersForZoom()

    private fun bringLabelsToTop() = logic.bringLabelsToTop()

    internal fun bringLocationMarkerToTop() = logic.bringLocationMarkerToTop()

    private fun getVisibleLabelIndices(
        points: List<LabeledPoint>,
        projection: org.osmdroid.views.Projection
    ) = logic.getVisibleLabelIndices(points, projection)

    private fun createPointOnlyBitmap(isSelected: Boolean = false) =
        logic.createPointOnlyBitmap(isSelected)

    internal fun createLabeledPointBitmap(
        id: String, 
        code: String, 
        isSelected: Boolean = false,
        drawPoint: Boolean = true
    ) = logic.createLabeledPointBitmap(id, code, isSelected, drawPoint)

    private fun clearCollectedPoints() = logic.clearCollectedPoints()

    internal fun createLocationPin(pinText: String = "M") = logic.createLocationPin(pinText)

    private fun updatePinBitmap() = logic.updatePinBitmap()

    private fun setupCompassOrientation() = logic.setupCompassOrientation()

    private fun updatePinOrientation() = logic.updatePinOrientation()

    fun updatePinText(newText: String) = logic.updatePinText(newText)

    private fun setupLocationTracking() = logic.setupLocationTracking()

    private fun stopLocationUpdates() = logic.stopLocationUpdates()

    private fun updateLocationMarker(latitude: Double, longitude: Double, altitude: Double) =
        logic.updateLocationMarker(latitude, longitude, altitude)

    internal fun smoothMoveToTarget() = logic.smoothMoveToTarget()

    private fun setupCollectButton() = logic.setupCollectButton()

    private fun setupResizeButton() = logic.setupResizeButton()

    private fun setupMenuButton() = logic.setupMenuButton()

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

enum class StakeoutMode {
    NONE,
    MAP_NAVIGATION,
    BULLSEYE_PRECISION
}

enum class CoordinateSystem {
    GLOBAL,
    LOCAL
}
