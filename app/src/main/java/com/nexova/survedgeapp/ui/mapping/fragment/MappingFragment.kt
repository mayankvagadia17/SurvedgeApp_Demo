package com.nexova.survedgeapp.ui.mapping.fragment

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.lifecycle.ViewModelProvider
import com.nexova.survedgeapp.R
import com.nexova.survedgeapp.data.model.SurveyPoint
import com.nexova.survedgeapp.databinding.FragmentMappingBinding
import com.nexova.survedgeapp.ui.mapping.viewmodel.MappingViewModel
import org.osmdroid.api.IMapController
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.util.concurrent.atomic.AtomicBoolean

class MappingFragment : Fragment() {

    private var _binding: FragmentMappingBinding? = null
    private val binding get() = _binding!!

    protected lateinit var viewModel: MappingViewModel
    private var mapView: MapView? = null
    private var mapController: IMapController? = null
    private val isMapReady = AtomicBoolean(false)
    private var hasCenteredOnLocation = false
    
    // Overlays for lines, points, and current location
    private val lineOverlays = mutableListOf<Polyline>()
    private val pointMarkers = mutableListOf<Marker>()
    private val pointTextOverlays = mutableListOf<TextOverlay>()
    private var currentLocationMarker: Marker? = null
    private var compassOverlay: CompassOverlay? = null
    private var rotationGestureOverlay: RotationGestureOverlay? = null
    
    // Smooth animation for location marker
    private var locationAnimator: ValueAnimator? = null
    private var targetLocation: GeoPoint? = null
    private var currentAnimatedLocation: GeoPoint? = null
    

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMappingBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[MappingViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize map
        initializeMap()
        
        // Setup header controls
        setupHeaderControls()
        
        // Setup left control panel
        setupLeftControlPanel()
        
        // Setup zoom controls
        setupZoomControls()
        
        // Setup collect button
        setupCollectButton()
        
        // Observe ViewModel
        observeViewModel()
        
        // Start location tracking
        startLocationTracking()
    }

    private fun initializeMap() {
        mapView = binding.mapView
        
        // Configure osmdroid for Android 11+ compatibility with high-quality tiles
        mapView?.setTileSource(TileSourceFactory.MAPNIK) // OpenStreetMap tiles
        mapView?.setMultiTouchControls(true) // Enable multi-touch controls
        
        mapView?.isHorizontalMapRepetitionEnabled = false
        mapView?.isVerticalMapRepetitionEnabled = false
        
        // Enable rotation gestures with RotationGestureOverlay
        // Add it first so it can handle gestures before other overlays
        mapView?.let { map ->
            val rotationOverlay = RotationGestureOverlay(map).apply {
                isEnabled = true
            }
            rotationGestureOverlay = rotationOverlay
            // Add at index 0 to ensure it handles gestures first
            map.overlays.add(0, rotationOverlay)
        }

        mapView?.zoomController?.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        
        // Additional Android 11+ compatibility settings
        mapView?.setUseDataConnection(true) // Enable network tile loading
        
        // Enable tile scaling for better quality at high zoom levels
        mapView?.setTilesScaledToDpi(true) // Scale tiles to device DPI for crisp rendering
        
        // Optimize for high-resolution rendering at cm-level zoom
        // Use software layer for better quality at extreme zoom levels
        mapView?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        
        // Enable high-quality rendering
        mapView?.isDrawingCacheEnabled = false
        
        // Set tile cache size for high zoom levels (more tiles needed)
        org.osmdroid.config.Configuration.getInstance().apply {
            // Increase tile cache size for high zoom levels
            tileFileSystemCacheMaxBytes = 50L * 1024 * 1024  // 50MB cache
            tileFileSystemCacheTrimBytes = 40L * 1024 * 1024  // Trim at 40MB
            // Enable tile download threads for faster loading
            tileDownloadThreads = 4
            // Set user agent
            userAgentValue = "SurvedgeApp/1.0"
        }
        
        mapView?.isClickable = true
        mapView?.isFocusable = true
        mapView?.isFocusableInTouchMode = true
        
        // Get map controller
        mapController = mapView?.controller
        
        // Set default zoom and center (Delhi area)
        mapController?.setZoom(13.0)
        mapController?.setCenter(GeoPoint(28.7041, 77.1025))
        
        // Set zoom limits - Maximum zoom for centimeter-level precision
        // Zoom 23 = ~3.75 cm per pixel, Zoom 24 = ~1.87 cm per pixel
        mapView?.minZoomLevel = 1.0
        mapView?.maxZoomLevel = 25.0  // Increased for cm-level precision
        
        // Force tile refresh on zoom to ensure high-quality tiles load
        mapView?.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                return false
            }
            
            override fun onZoom(event: ZoomEvent?): Boolean {
                // At high zoom levels, force tile refresh for clarity
                event?.let {
                    if (it.zoomLevel >= 19.0) {
                        // At zoom 19+, refresh tiles to ensure clarity
                        mapView?.invalidate()
                    }
                }
                return false
            }
        })
        
        isMapReady.set(true)
        
        // Hide built-in zoom controls after map is ready
        view?.postDelayed({
            try {
                // Hide via zoomController property
                mapView?.zoomController?.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            } catch (e: Exception) {
                Log.d("MappingFragment", "Could not hide zoom controls: ${e.message}")
            }
        }, 200) // Delay to ensure map is fully initialized
        
        // Ensure tiles are properly scaled for high zoom clarity
        view?.postDelayed({
            // Force a refresh to ensure tiles load at correct DPI
            mapView?.invalidate()
            // Re-apply tile scaling to ensure it takes effect at all zoom levels
            mapView?.setTilesScaledToDpi(true)
            // Force high-quality rendering
            mapView?.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }, 100) // Small delay to avoid blocking initialization
        
        // Setup compass overlay for rotation indicator
        setupCompassOverlay()
        
        // Try to center on current location if available
        centerOnCurrentLocationOrDefault()
    }
    
    private fun setupCompassOverlay() {
        mapView?.let { map ->
            try {
                // Create compass overlay with device orientation provider
                compassOverlay = CompassOverlay(
                    requireContext(),
                    InternalCompassOrientationProvider(requireContext()),
                    map
                ).apply {
                    // Position compass in top-right corner
                    enableCompass()
                    // Set compass size and position
                    setCompassCenter(0.95f, 0.1f) // 95% from left, 10% from top
                }
                map.overlays.add(compassOverlay)
                
                // Add a clickable overlay for the compass area to reset rotation
                val compassClickOverlay = object : Overlay() {
                    override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: MapView?): Boolean {
                        val projection = mapView?.projection ?: return false
                        val screenPoint = android.graphics.PointF(e?.x ?: 0f, e?.y ?: 0f)
                        
                        // Check if tap is in compass area (top-right corner)
                        val mapWidth = mapView.width.toFloat()
                        val mapHeight = mapView.height.toFloat()
                        val compassX = mapWidth * 0.95f
                        val compassY = mapHeight * 0.1f
                        val compassRadius = 50f * resources.displayMetrics.density // ~50dp radius
                        
                        val distance = kotlin.math.sqrt(
                            (screenPoint.x - compassX) * (screenPoint.x - compassX) +
                            (screenPoint.y - compassY) * (screenPoint.y - compassY)
                        )
                        
                        if (distance <= compassRadius) {
                            // Tap is on compass, reset rotation
                            map.setMapOrientation(0.0f)
                            return true
                        }
                        return false
                    }
                }
                map.overlays.add(compassClickOverlay)
            } catch (e: Exception) {
                Log.w("MappingFragment", "Compass overlay not available: ${e.message}")
                // If compass hardware is not available, still enable rotation but without compass
                compassOverlay = null
            }
        }
    }

    private fun setupHeaderControls() {
        // Back button
        binding.btnBack.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }
        
        // Menu button with popup
        binding.btnMenu.setOnClickListener { view ->
            showMenuPopup(view)
        }
    }
    
    private fun setupLeftControlPanel() {
        // Play/Pause button
        binding.btnPlayPause.setOnClickListener {
            // Toggle play/pause state
        }
        
        // Single button
        binding.btnSingle.setOnClickListener {
        }
    }
    
    private fun setupZoomControls() {
        binding.btnZoomIn.setOnClickListener {
            mapView?.let { map ->
                val currentZoom = map.zoomLevelDouble
                if (currentZoom < map.maxZoomLevel) {
                    mapController?.zoomIn()
                }
            }
        }
        
        binding.btnZoomOut.setOnClickListener {
            mapView?.let { map ->
                val currentZoom = map.zoomLevelDouble
                if (currentZoom > map.minZoomLevel) {
                    mapController?.zoomOut()
                }
            }
        }
        
        binding.btnCenter.setOnClickListener {
            mapView?.let { map ->
                // Center on current location if available
                val currentLocation = viewModel.currentLocation.value
                if (currentLocation != null) {
                    centerOnLocation(currentLocation)
                } else {
                    // Default center (Delhi area) if location is not available
                    mapController?.setCenter(GeoPoint(28.7041, 77.1025))
                    mapController?.setZoom(13.0)
                }
                // Reset rotation when centering
                map.setMapOrientation(0.0f)
            }
        }
        
        // Fit bounds button
        binding.btnFitBounds.setOnClickListener {
            mapView?.let { map ->
                // Fit to all points if available
                val points = viewModel.points.value
                if (points?.isNotEmpty() == true) {
                    val bounds = org.osmdroid.util.BoundingBox.fromGeoPoints(
                        points.map { GeoPoint(it.latitude, it.longitude) }
                    )
                    map.zoomToBoundingBox(bounds, true, 100)
                } else {
                    Toast.makeText(requireContext(), "No points to fit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun setupCollectButton() {
        binding.btnCollect.setOnClickListener {
            showCollectPointBottomSheet()
        }
    }
    
    private fun showCollectPointBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_collect_point, null)
        bottomSheetDialog.setContentView(view)
        
        val etPointId = view.findViewById<EditText>(R.id.etPointId)
        val etNote = view.findViewById<EditText>(R.id.etNote)
        val btnSave = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSave)
        val btnClose = view.findViewById<View>(R.id.btnClose)

        // Close button
        btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }
        
        // Save button - add point with current location
        btnSave.setOnClickListener {
            val pointId = etPointId.text.toString().trim()
            val note = etNote.text.toString().trim()
            
            val currentLocation = viewModel.currentLocation.value
            if (currentLocation == null) {
                Toast.makeText(requireContext(), "No GPS location available. Waiting for GPS fix...", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            
            // Generate point ID if empty
            val finalPointId = if (pointId.isEmpty()) {
                viewModel.getNextPointId()
            } else {
                pointId
            }
            
            // Check for duplicate ID
            if (viewModel.pointIdExists(finalPointId)) {
                Toast.makeText(requireContext(), "A point with ID '$finalPointId' already exists.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            
            // Create and add point
            val point = SurveyPoint(
                id = finalPointId,
                name = finalPointId,
                code = "NO-CODE",
                latitude = currentLocation.latitude,
                longitude = currentLocation.longitude,
                elevation = if (currentLocation.hasAltitude()) currentLocation.altitude else null
            )
            
            viewModel.addPoint(point)
            Toast.makeText(requireContext(), "Point $finalPointId saved successfully", Toast.LENGTH_SHORT).show()
            bottomSheetDialog.dismiss()
        }
        
        bottomSheetDialog.show()
    }
    
    
    private fun showMenuPopup(view: View) {
        val popup = PopupMenu(requireContext(), view, Gravity.END)
        popup.menuInflater.inflate(R.menu.mapping_menu, popup.menu)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_object_list -> {
                    // TODO: Show object list
                    Toast.makeText(requireContext(), "Object list", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_project_details -> {
                    // TODO: Show project details
                    Toast.makeText(requireContext(), "Project details", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }
    
    private fun observeViewModel() {
        viewModel.lines.observe(viewLifecycleOwner) { lines ->
            updateMapWithLines()
        }
        
        viewModel.points.observe(viewLifecycleOwner) { points ->
            updateMapWithPoints()
        }
        
        viewModel.currentLocation.observe(viewLifecycleOwner) { location ->
            updateCurrentLocationPin(location)
            // Center on location when it becomes available (only once)
            if (location != null && !hasCenteredOnLocation && isMapReady.get()) {
                view?.postDelayed({
                    centerOnLocation(location)
                    hasCenteredOnLocation = true
                }, 300) // Small delay to ensure pin is rendered
            }
        }
    }

    private fun updateMapWithPoints() {
        if (!isMapReady.get()) return
        
        val map = mapView ?: return
        val points = viewModel.points.value ?: return
        val codes = viewModel.codes.value ?: emptyList()
        
        // Remove existing point markers and text overlays
        pointMarkers.forEach { marker ->
            map.overlays.remove(marker)
        }
        pointMarkers.clear()
        
        pointTextOverlays.forEach { overlay ->
            map.overlays.remove(overlay)
        }
        pointTextOverlays.clear()
        
        // Get current viewport for viewport culling
        val bounds = map.boundingBox
        
        // Batch collect markers and overlays before adding
        val markersToAdd = mutableListOf<Marker>()
        val textOverlaysToAdd = mutableListOf<TextOverlay>()
        
        // Add markers for each point with viewport culling
        points.forEach { point ->
            val geoPoint = GeoPoint(point.latitude, point.longitude)
            
            // Viewport culling: only render points in visible area
            if (!bounds.contains(geoPoint)) {
                return@forEach
            }
            
            // Create marker
            val marker = Marker(map)
            marker.position = geoPoint
            marker.title = point.id
            
            // Use different colors for highlighted points
            val markerColor = if (point.isHighlighted) {
                Color.parseColor("#FF6B35") // Orange for highlighted
            } else {
                Color.WHITE
            }
            
            val strokeColor = Color.BLACK
            
            // Create a custom drawable for the marker (circle with border)
            // Use a bitmap drawable for better performance
            val markerSize = (6 * resources.displayMetrics.density).toInt()
            val bitmap = android.graphics.Bitmap.createBitmap(markerSize, markerSize, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL
                color = markerColor
            }
            val strokePaint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                color = strokeColor
                strokeWidth = 4f
            }
            val radius = (markerSize / 2f) - 2f
            canvas.drawCircle(markerSize / 2f, markerSize / 2f, radius, paint)
            canvas.drawCircle(markerSize / 2f, markerSize / 2f, radius, strokePaint)
            
            marker.icon = android.graphics.drawable.BitmapDrawable(resources, bitmap)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            
            // Create text overlay for point ID only (no code, no background)
            val textOverlay = TextOverlay(
                position = geoPoint,
                text = point.id,
                textColor = Color.parseColor("#111111"),
                backgroundColor = Color.TRANSPARENT,
                textSize = 11f
            )
            
            markersToAdd.add(marker)
            textOverlaysToAdd.add(textOverlay)
            pointMarkers.add(marker)
            pointTextOverlays.add(textOverlay)
        }
        
        // Batch add all markers and overlays at once
        map.overlays.addAll(markersToAdd)
        map.overlays.addAll(textOverlaysToAdd)
        
        // Throttle invalidate to prevent lag during zoom/pan
        view?.post {
            map.invalidate()
        }
    }
    
    private fun updateMapWithLines() {
        if (!isMapReady.get()) return
        
        val map = mapView ?: return
        val lines = viewModel.lines.value ?: return
        
        // Remove existing line overlays
        lineOverlays.forEach { polyline ->
            map.overlays.remove(polyline)
        }
        lineOverlays.clear()
        
        // Get current viewport for viewport culling
        val bounds = map.boundingBox
        
        // Batch collect polylines before adding
        val polylinesToAdd = mutableListOf<Polyline>()
        
        // Add new polylines for each line with viewport culling
        lines.forEach { line ->
            val geoPoints = line.points.map { GeoPoint(it.latitude, it.longitude) }
            
            // Viewport culling: check if any point of the line is in visible area
            val isVisible = geoPoints.any { bounds.contains(it) }
            if (!isVisible) {
                return@forEach
            }
            
            // If closed, add first point at the end
            val finalPoints = if (line.isClosed && geoPoints.isNotEmpty()) {
                geoPoints + geoPoints.first()
            } else {
                geoPoints
            }
            
            val polyline = Polyline()
            polyline.setPoints(finalPoints)
            polyline.color = Color.parseColor("#4A4A4A")
            polyline.width = 3f
            polyline.isGeodesic = true
            
            polylinesToAdd.add(polyline)
            lineOverlays.add(polyline)
        }
        
        // Batch add all polylines at once
        map.overlays.addAll(polylinesToAdd)
        
        // Throttle invalidate to prevent message queue buildup
        view?.post {
            map.invalidate()
        }
    }

    override fun onStart() {
        super.onStart()
        mapView?.onResume()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        // Re-enable compass when resumed
        compassOverlay?.enableCompass()
        // Restart location tracking if it was stopped
        startLocationTracking()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
        // Disable compass when paused to save battery
        compassOverlay?.disableCompass()
        // Optionally pause location tracking to save battery
        // viewModel.stopLocationTracking()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // osmdroid handles its own state management internally
        // No need to call mapView.onSaveInstanceState() as it's not available
    }

    override fun onLowMemory() {
        super.onLowMemory()
        // osmdroid MapView doesn't have onLowMemory() method
        // The map will automatically handle memory management
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stop location tracking
        viewModel.stopLocationTracking()
        
        // Clean up animations and overlays
        locationAnimator?.cancel()
        locationAnimator = null
        currentLocationMarker = null
        currentAnimatedLocation = null
        targetLocation = null
        lineOverlays.clear()
        pointMarkers.clear()
        pointTextOverlays.clear()
        
        // Clean up compass overlay
        compassOverlay?.disableCompass()
        compassOverlay = null
        
        mapView?.onDetach()
        _binding = null
    }

    private fun startLocationTracking() {
        // Start location tracking in ViewModel
        context?.let {
            viewModel.startLocationTracking(it)
        }
    }

    private fun updateCurrentLocationPin(location: android.location.Location?) {
        if (!isMapReady.get()) {
            return
        }

        val map = mapView ?: return

        if (location != null) {
            val lat = location.latitude
            val lng = location.longitude
            val newTarget = GeoPoint(lat, lng)
            
            // If marker doesn't exist, create it immediately
            if (currentLocationMarker == null) {
                val marker = Marker(map)
                marker.position = newTarget
                marker.title = "Current Location"
                
                // Use blue icon for current location
                val drawable = resources.getDrawable(android.R.drawable.ic_menu_mylocation, null)
                drawable?.setTint(Color.parseColor("#2196F3")) // Blue color
                marker.icon = drawable
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                
                map.overlays.add(marker)
                currentLocationMarker = marker
                currentAnimatedLocation = newTarget
                targetLocation = newTarget
                
                view?.post {
                    map.invalidate()
                }
                return
            }
            
            // Update target location
            val previousTarget = targetLocation
            val currentAnimated = currentAnimatedLocation
            
            // Always update target location to the latest
            targetLocation = newTarget
            
            // If this is the first update, set position immediately
            if (previousTarget == null || currentAnimated == null) {
                currentAnimatedLocation = newTarget
                currentLocationMarker?.position = newTarget
                view?.post {
                    map.invalidate()
                }
            } else {
                // Calculate distance from current animated position to new target (in meters)
                val distance = currentAnimated.distanceToAsDouble(newTarget)
                
                // For very small movements (< 1cm), update immediately without animation to prevent jumps
                // For larger movements, use smooth animation
                if (distance < 0.01) {
                    // Very small movement (< 1cm): update immediately for instant response
                    currentAnimatedLocation = newTarget
                    currentLocationMarker?.position = newTarget
                    // Cancel any ongoing animation to prevent conflicts
                    locationAnimator?.cancel()
                    locationAnimator = null
                    view?.post {
                        map.invalidate()
                    }
                } else {
                    // Larger movement: use smooth animation
                    animateMarkerToPosition(newTarget, distance)
                }
            }
        } else {
            // Remove current location marker if location is null
            locationAnimator?.cancel()
            locationAnimator = null
            currentLocationMarker?.let { marker ->
                map.overlays.remove(marker)
                view?.post {
                    map.invalidate()
                }
            }
            currentLocationMarker = null
            currentAnimatedLocation = null
            targetLocation = null
        }
    }
    
    private fun animateMarkerToPosition(target: GeoPoint, distance: Double) {
        val marker = currentLocationMarker ?: return
        val startLocation = currentAnimatedLocation ?: target
        
        // Cancel any existing animation to prevent conflicts and jumps
        locationAnimator?.cancel()
        locationAnimator = null
        
        // Calculate animation duration based on distance
        // Optimized for cm-level precision: very smooth for small movements, responsive for larger ones
        val duration = when {
            distance < 0.05 -> 50L   // < 5cm: very quick and smooth (50ms)
            distance < 0.1 -> 80L    // < 10cm: quick and smooth (80ms)
            distance < 0.5 -> 120L   // < 50cm: smooth movement (120ms)
            distance < 1.0 -> 150L   // < 1m: smooth (150ms)
            distance < 5.0 -> 200L   // < 5m: smooth (200ms)
            else -> (200 + (distance * 2).coerceAtMost(500.0)).toLong()  // > 5m: scale with distance, max 500ms
        }
        
        // Create animator for latitude
        val startLat = startLocation.latitude
        val endLat = target.latitude
        val latDiff = endLat - startLat
        
        // Create animator for longitude
        val startLng = startLocation.longitude
        val endLng = target.longitude
        val lngDiff = endLng - startLng
        
        // Use LinearInterpolator for constant speed movement (prevents jumps)
        // This ensures the pin moves smoothly at a constant rate
        locationAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            // Use LinearInterpolator for constant speed - prevents visual jumps
            interpolator = LinearInterpolator()
            
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                
                // Interpolate position with high precision
                val currentLat = startLat + (latDiff * fraction)
                val currentLng = startLng + (lngDiff * fraction)
                val animatedPosition = GeoPoint(currentLat, currentLng)
                
                // Update marker position immediately
                marker.position = animatedPosition
                currentAnimatedLocation = animatedPosition
                
                // Invalidate map to redraw - this will be called frequently for smooth animation
                mapView?.invalidate()
            }
            
            addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    // If animation is cancelled, ensure we're at the target position
                    marker.position = target
                    currentAnimatedLocation = target
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Ensure we end exactly at target position
                    marker.position = target
                    currentAnimatedLocation = target
                    locationAnimator = null
                    mapView?.invalidate()
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            
            start()
        }
    }

    private fun centerOnCurrentLocationOrDefault() {
        val location = viewModel.currentLocation.value
        if (location != null) {
            // Update the location pin first, then center after a small delay
            updateCurrentLocationPin(location)
            view?.postDelayed({
                if (!hasCenteredOnLocation) {
                    centerOnLocation(location)
                    hasCenteredOnLocation = true
                }
            }, 500) // Delay to ensure pin is rendered
        } else {
            // Default center (Delhi area) if location is not available
            mapController?.setCenter(GeoPoint(28.7041, 77.1025))
            mapController?.setZoom(13.0)
        }
    }

    private fun centerOnLocation(location: android.location.Location) {
        mapController?.let { controller ->
            val lat = location.latitude
            val lng = location.longitude
            val geoPoint = GeoPoint(lat, lng)
            Log.d("MappingFragment", "Centering map on location: lat=$lat, lng=$lng")
            
            controller.setCenter(geoPoint)
            controller.setZoom(15.0) // Zoom level for current location
            
            Log.d("MappingFragment", "Map centered at: lat=$lat, lng=$lng")
        }
    }
}

/**
 * Custom overlay to display text labels on the map above markers
 */
private class TextOverlay(
    private val position: GeoPoint,
    private val text: String,
    private val textColor: Int,
    private val backgroundColor: Int,
    private val textSize: Float
) : Overlay() {
    
    // Cache paint objects to avoid recreation
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    
    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val borderPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    
    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        if (shadow || canvas == null || mapView == null) return
        
        val projection = mapView.projection
        val screenPosition = projection.toPixels(position, null)
        
        // Viewport culling: check if point is visible
        val viewport = mapView.projection.getBoundingBox()
        if (!viewport.contains(position)) return
        
        // Update paint properties (size may change with zoom)
        val density = mapView.context.resources.displayMetrics.density
        textPaint.color = textColor
        textPaint.textSize = textSize * density
        
        bgPaint.color = backgroundColor
        borderPaint.strokeWidth = 1f * density
        
        // Handle multi-line text
        val lines = text.split("\n")
        val lineHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
        val lineSpacing = lineHeight * 0.2f
        val totalHeight = (lineHeight * lines.size) + (lineSpacing * (lines.size - 1))
        
        var maxWidth = 0f
        val lineWidths = mutableListOf<Float>()
        lines.forEach { line ->
            val width = textPaint.measureText(line)
            lineWidths.add(width)
            if (width > maxWidth) maxWidth = width
        }
        
        val padding = 4f * density
        val labelWidth = maxWidth + padding * 2
        val labelHeight = totalHeight + padding * 2
        
        val left = screenPosition.x - labelWidth / 2
        val top = screenPosition.y - labelHeight - 30f * density // Position above marker
        val right = screenPosition.x + labelWidth / 2
        val bottom = top + labelHeight
        
        // Draw background only if not transparent
        if (backgroundColor != Color.TRANSPARENT) {
            canvas.drawRect(left, top, right, bottom, bgPaint)
            // Draw border
            canvas.drawRect(left, top, right, bottom, borderPaint)
        }
        
        // Draw text lines
        var currentY = top + padding + lineHeight
        lines.forEachIndexed { index, line ->
            val lineWidth = lineWidths[index]
            val textX = screenPosition.x.toFloat()
            canvas.drawText(line, textX, currentY, textPaint)
            currentY += lineHeight + lineSpacing
        }
    }
}
