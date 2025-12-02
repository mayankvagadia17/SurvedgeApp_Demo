package com.nexova.survedgeapp.ui.mapping.fragment

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import android.os.Handler
import android.os.Looper
import com.nexova.survedgeapp.R
import kotlin.jvm.JvmField

class MappingFragment : Fragment() {

    private var _binding: FragmentMappingBinding? = null
    private val binding get() = _binding!!

    protected lateinit var viewModel: MappingViewModel
    private var mapView: MapView? = null
    private var mapController: IMapController? = null
    private val isMapReady = AtomicBoolean(false)
    private var hasCenteredOnLocation = false
    
    // Overlays for points, lines, and current location
    private val pointMarkers = mutableListOf<Marker>()
    private val pointLabelOverlays = mutableListOf<TextOverlay>()
    private val lineOverlays = mutableListOf<Polyline>()
    private var currentLocationMarker: Marker? = null
    private var pointsOverlay: OptimizedPointsOverlay? = null // Ultra-optimized overlay for massive datasets
    private val processingJob = SupervisorJob()
    private val processingScope = CoroutineScope(Dispatchers.Default + processingJob)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentRenderTask: Job? = null
    private var scrollUpdatePending = false
    private var isUserInteracting = false // Track if user is actively zooming/panning
    private var lastUpdateTime = 0L // Throttle updates
    private val minUpdateInterval = 500L // Minimum time between updates (ms)

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
        mapView?.setMultiTouchControls(true)

        mapView?.zoomController?.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
        
        // Enable hardware acceleration for better performance and tile quality
        mapView?.isHorizontalMapRepetitionEnabled = false
        mapView?.isVerticalMapRepetitionEnabled = false
        
        // Additional Android 11+ compatibility settings
        mapView?.setUseDataConnection(true) // Enable network tile loading
        
        // Enable tile scaling for better quality at high zoom levels
        mapView?.setTilesScaledToDpi(true) // Scale tiles to device DPI for crisp rendering
        
        // For Android 11+, optimize rendering for crisp tiles
        // The key is ensuring tiles are loaded at the correct DPI scale
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // Android 11+ (API 30+): Use hardware layer but ensure quality
            mapView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        } else {
            // Android 10 and below: Use hardware acceleration
            mapView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        
        // Disable drawing cache to ensure fresh, high-quality rendering
        mapView?.isDrawingCacheEnabled = false
        
        mapView?.isClickable = true
        mapView?.isFocusable = true
        mapView?.isFocusableInTouchMode = true
        
        // Get map controller
        mapController = mapView?.controller
        
        // Set default zoom and center (Delhi area)
        mapController?.setZoom(13.0)
        mapController?.setCenter(GeoPoint(28.7041, 77.1025))
        
        // Set zoom limits - Increased to 23 for centimeter-level precision (~3.75 cm per tile)
        // Note: Standard OpenStreetMap tiles may only go up to zoom 19, but higher zoom allows for better precision
        mapView?.minZoomLevel = 1.0
        mapView?.maxZoomLevel = 23.0
        
        // Force tile refresh on zoom to ensure high-quality tiles load
        mapView?.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                // Mark user as interacting - skip point updates during active scrolling
                isUserInteracting = true
                
                val pointsSize = viewModel.points.value?.size ?: 0
                val isAndroid11Plus = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                
                // For large datasets, completely skip updates during scroll to prevent lag
                if (pointsSize > 10000 && isAndroid11Plus) {
                    // Just reset the flag after a delay, don't update points
                    if (!scrollUpdatePending) {
                        scrollUpdatePending = true
                        view?.postDelayed({
                            scrollUpdatePending = false
                            isUserInteracting = false
                            // Only update after user stops scrolling
        updateMapWithPoints()
                        }, 1000) // 1 second delay for very large datasets
                    }
                    return false
                }
                
                if (pointsSize > 100) {
                    if (!scrollUpdatePending) {
                        scrollUpdatePending = true
                        val delayMs = when {
                            isAndroid11Plus && pointsSize > 10000 -> 1000  // Very large: 1 second
                            isAndroid11Plus && pointsSize > 1000 -> 800     // Large: 800ms
                            isAndroid11Plus -> 600                         // Medium: 600ms
                            else -> 500                                    // Default: 500ms
                        }
                        view?.postDelayed({
                            scrollUpdatePending = false
                            isUserInteracting = false
                            updateMapWithPoints()
                        }, delayMs.toLong())
                    }
                }
                return false
            }
            
            override fun onZoom(event: ZoomEvent?): Boolean {
                // Mark user as interacting - skip point updates during active zooming
                isUserInteracting = true
                
                // DON'T invalidate during zoom - causes too many redraws
                // mapView?.invalidate() // Removed to prevent lag
                
                val pointsSize = viewModel.points.value?.size ?: 0
                val isAndroid11Plus = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                
                // For very large datasets, completely skip updates during zoom
                if (pointsSize > 10000 && isAndroid11Plus) {
                    if (!scrollUpdatePending) {
                        scrollUpdatePending = true
                        view?.postDelayed({
                            scrollUpdatePending = false
                            isUserInteracting = false
                            // Only update after user stops zooming
                            updateMapWithPoints()
                        }, 800) // 800ms delay for very large datasets
                    }
                    return false
                }
                
                if (!scrollUpdatePending) {
                    scrollUpdatePending = true
                    val delayMs = when {
                        isAndroid11Plus && pointsSize > 10000 -> 600  // Very large: 600ms
                        isAndroid11Plus && pointsSize > 1000 -> 400  // Large: 400ms
                        else -> 300                                   // Default: 300ms
                    }
                    view?.postDelayed({
                        scrollUpdatePending = false
                        isUserInteracting = false
                        updateMapWithPoints()
                    }, delayMs.toLong())
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
        
        // For Android 11+, ensure tiles are properly scaled after map is ready
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            view?.postDelayed({
                // Force a refresh to ensure tiles load at correct DPI (delayed to avoid blocking)
                mapView?.invalidate()
                // Re-apply tile scaling to ensure it takes effect
                mapView?.setTilesScaledToDpi(true)
            }, 100) // Small delay to avoid blocking initialization
        }
        
        // Load existing points if any
        updateMapWithPoints()
        
        // Try to center on current location if available
        centerOnCurrentLocationOrDefault()
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
            showGeneratePointsDialog()
        }
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
    
    private fun showGeneratePointsDialog() {
        val input = EditText(requireContext())
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Enter number of points (minimum 3)"
        // Add padding to the EditText
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)
        
        AlertDialog.Builder(requireContext())
            .setTitle("Generate Points")
            .setMessage("Enter the number of points you want to generate:")
            .setView(input)
            .setPositiveButton("Generate") { _, _ ->
                val inputText = input.text.toString()
                if (inputText.isNotEmpty()) {
                    try {
                        val numberOfPoints = inputText.toInt()
                        if (numberOfPoints >= 3) {
                            viewModel.generatePoints(numberOfPoints)
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Please enter at least 3 points",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: NumberFormatException) {
                        Toast.makeText(
                            requireContext(),
                            "Please enter a valid number",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Please enter a number",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.points.observe(viewLifecycleOwner) { points ->
            // Use post to avoid blocking UI thread
            view?.post {
            updateMapWithPoints()
            }
        }
        
        viewModel.lines.observe(viewLifecycleOwner) { lines ->
            updateMapWithLines()
        }
        
        viewModel.isGeneratingPoints.observe(viewLifecycleOwner) { isGenerating ->
            binding.btnCollect.isEnabled = !isGenerating
            binding.btnCollect.text = if (isGenerating) {
                "Collecting..."
            } else {
                "Collect"
            }
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
        
        // CRITICAL: Skip updates if user is actively interacting (zooming/panning)
        // This prevents lag and ANR during user interaction
        if (isUserInteracting) {
            return
        }
        
        // Throttle updates to prevent excessive redraws
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < minUpdateInterval) {
            return
        }
        lastUpdateTime = currentTime
        
        // Cancel any ongoing render task
        currentRenderTask?.cancel()
        
        // ALWAYS remove old overlays first before adding new ones
        clearAllPointOverlays(map)
        
        // If no points, just return after clearing
        if (points.isEmpty()) {
            // Throttle invalidate calls
            view?.post {
                map.invalidate()
            }
            return
        }
        
        // Dynamic threshold: Use individual markers for small datasets for better interactivity
        // For Android 11+, ensure proper rendering regardless of dataset size
        val useIndividualMarkers = points.size < 100 // Increased threshold for better UX
        
        if (useIndividualMarkers) {
            // Use individual markers for small datasets (better interactivity, labels, tooltips)
            updateWithIndividualMarkers(map, points)
            return
        }
        
        // For larger datasets, use ultra-optimized overlay with background processing
        // This works for any dataset size (100 to millions of points)
        currentRenderTask = processingScope.launch {
            try {
                val bounds = map.boundingBox
                val currentZoom = map.zoomLevelDouble
                val density = resources.displayMetrics.density
                // Show labels at higher zoom for medium datasets, hide for very large ones
                val showLabels = when {
                    points.size < 1000 -> currentZoom >= 14.0
                    points.size < 10000 -> currentZoom >= 15.0
                    else -> false // No labels for very large datasets
                }
                
                // Create optimized overlay with async point computation
                val overlay = OptimizedPointsOverlay(
                    points,
                    bounds,
                    currentZoom,
                    showLabels,
                    density,
                    coroutineContext // Pass coroutine context for cancellation
                )
                
                // Compute points in background
                overlay.computePointsAsync()
                
                // Android 11+ optimization: More aggressive delays for large datasets
                val isAndroid11Plus = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                if (points.size > 10000) {
                    val delayMs = if (isAndroid11Plus && points.size > 100000) {
                        100 // Longer delay for very large datasets on Android 11+
                    } else {
                        50
                    }
                    kotlinx.coroutines.delay(delayMs.toLong()) // Small delay to let computation start
                }
                
                // Update on main thread
                withContext(Dispatchers.Main) {
                    if (isActive && mapView != null) { // Check if not cancelled and map still exists
                        // Ensure old overlay is removed (safety check)
                        pointsOverlay?.let {
                            map.overlays.remove(it)
                        }
                    // Set and add new overlay
                    pointsOverlay = overlay
                    map.overlays.add(overlay)
                    // Throttle invalidate to prevent excessive redraws
                    view?.post {
                        if (!isUserInteracting) {
                            map.invalidate()
                        }
                    }
                    }
                }
            } catch (e: Exception) {
                // Fallback to individual markers if optimized overlay fails
                Log.e("MappingFragment", "Error creating optimized overlay, falling back to individual markers", e)
                withContext(Dispatchers.Main) {
                    if (isActive && mapView != null) {
                        updateWithIndividualMarkers(map, points)
                    }
                }
            }
        }
    }
    
    /**
     * Clears all point-related overlays from the map
     */
    private fun clearAllPointOverlays(map: MapView) {
        // Remove individual markers
        pointMarkers.forEach { marker ->
            map.overlays.remove(marker)
        }
        pointMarkers.clear()
        
        // Remove label overlays
        pointLabelOverlays.forEach { overlay ->
            map.overlays.remove(overlay)
        }
        pointLabelOverlays.clear()
        
        // Remove optimized points overlay if exists
        pointsOverlay?.let {
            map.overlays.remove(it)
            pointsOverlay = null
        }
    }
    
    private fun updateWithIndividualMarkers(map: MapView, points: List<com.nexova.survedgeapp.data.model.SurveyPoint>) {
        // Ensure old markers are cleared (should already be done, but double-check)
        clearAllPointOverlays(map)
        
        val bounds = map.boundingBox
        val currentZoom = map.zoomLevelDouble
        // Show labels at zoom 12+ for better visibility
        val showLabels = currentZoom >= 12.0
        
        // Pre-create drawables for better performance (Android 11+ compatible)
        val highlightedDrawable = try {
            resources.getDrawable(android.R.drawable.ic_menu_mylocation, null)?.apply {
                setTint(Color.parseColor("#FF5722"))
            }
        } catch (e: Exception) {
            Log.w("MappingFragment", "Error creating highlighted drawable", e)
            null
        }
        
        val regularDrawable = try {
            resources.getDrawable(android.R.drawable.ic_menu_mylocation, null)?.apply {
                setTint(Color.BLACK)
            }
        } catch (e: Exception) {
            Log.w("MappingFragment", "Error creating regular drawable", e)
            null
        }
        
        val markersToAdd = mutableListOf<Marker>()
        val labelsToAdd = mutableListOf<TextOverlay>()
        
        // Process all points (with viewport culling for performance)
        points.forEach { point ->
            val geoPoint = GeoPoint(point.latitude, point.longitude)
            
            // Viewport culling: Skip points outside visible area for better performance
            if (!bounds.contains(geoPoint)) return@forEach
            
            try {
                val marker = Marker(map)
                marker.position = geoPoint
                marker.title = point.name
                marker.snippet = point.code
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                
                // Set icon with proper error handling for Android 11+
                marker.icon = if (point.isHighlighted) {
                    highlightedDrawable?.constantState?.newDrawable()?.mutate()
                } else {
                    regularDrawable?.constantState?.newDrawable()?.mutate()
                }
                
                markersToAdd.add(marker)
                pointMarkers.add(marker)
                
                // Add labels if enabled
                if (showLabels) {
                    val labelText = if (point.name.isNotEmpty()) point.name else point.code
                    labelsToAdd.add(TextOverlay(geoPoint, labelText, Color.BLACK, Color.WHITE, 14f))
                }
            } catch (e: Exception) {
                Log.e("MappingFragment", "Error creating marker for point: ${point.name}", e)
                // Continue with next point instead of crashing
            }
        }
        
        // Batch add all markers and labels for better performance
        try {
            map.overlays.addAll(markersToAdd)
            if (showLabels && labelsToAdd.isNotEmpty()) {
                map.overlays.addAll(labelsToAdd)
                pointLabelOverlays.addAll(labelsToAdd)
            }
            // Throttle invalidate to prevent message queue buildup
            view?.post {
                if (!isUserInteracting) {
                    map.invalidate()
                }
            }
        } catch (e: Exception) {
            Log.e("MappingFragment", "Error adding markers to map", e)
            // Still invalidate to ensure map updates (throttled)
            view?.post {
                if (!isUserInteracting) {
                    map.invalidate()
                }
            }
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
            if (!isUserInteracting) {
                map.invalidate()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView?.onResume()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        // Restart location tracking if it was stopped
        startLocationTracking()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
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
        
        // Clean up overlays
        currentLocationMarker = null
        pointMarkers.clear()
        pointLabelOverlays.clear()
        lineOverlays.clear()
        pointsOverlay = null
        
        // Cancel background processing
        currentRenderTask?.cancel()
        processingJob.cancel()
        
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
            Log.d("MappingFragment", "Map not ready, skipping location pin update")
            return
        }

        val map = mapView ?: return

        if (location != null) {
            val lat = location.latitude
            val lng = location.longitude
            Log.d("MappingFragment", "Updating location pin: lat=$lat, lng=$lng")
            
            // Remove existing current location marker
            currentLocationMarker?.let { marker ->
                map.overlays.remove(marker)
            }
            
            // Create new marker for current location
            val marker = Marker(map)
            marker.position = GeoPoint(lat, lng)
            marker.title = "Current Location"
            
            // Use blue icon for current location
            val drawable = resources.getDrawable(android.R.drawable.ic_menu_mylocation, null)
            drawable?.setTint(Color.parseColor("#2196F3")) // Blue color
            marker.icon = drawable
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            
            map.overlays.add(marker)
            currentLocationMarker = marker
            
            // Throttle invalidate to prevent lag
            view?.post {
                if (!isUserInteracting) {
                    map.invalidate()
                }
            }
            
            Log.d("MappingFragment", "Location pin set at: lng=$lng, lat=$lat")
        } else {
            Log.d("MappingFragment", "Location is null, clearing location pin")
            // Remove current location marker
            currentLocationMarker?.let { marker ->
                map.overlays.remove(marker)
                // Throttle invalidate to prevent lag
                view?.post {
                    if (!isUserInteracting) {
                        map.invalidate()
                    }
                }
            }
            currentLocationMarker = null
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
        
        // Measure text bounds
        val textBounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        
        val padding = 4f * density
        val labelWidth = textBounds.width() + padding * 2
        val labelHeight = textBounds.height() + padding * 2
        
        val left = screenPosition.x - labelWidth / 2
        val top = screenPosition.y - labelHeight - 30f * density // Position above marker
        val right = screenPosition.x + labelWidth / 2
        val bottom = top + labelHeight
        
        // Draw background
        canvas.drawRect(left, top, right, bottom, bgPaint)
        
        // Draw border
        canvas.drawRect(left, top, right, bottom, borderPaint)
        
        // Draw text
        val textY = top + labelHeight / 2 + textBounds.height() / 2
        canvas.drawText(text, screenPosition.x.toFloat(), textY, textPaint)
    }
}

/**
 * Ultra-optimized overlay for rendering massive datasets (1 crore+ points)
 * Uses spatial indexing, aggressive LOD, clustering, and maximum render limits
 */
private class OptimizedPointsOverlay(
    private val allPoints: List<com.nexova.survedgeapp.data.model.SurveyPoint>,
    private val viewport: org.osmdroid.util.BoundingBox,
    private val zoom: Double,
    private val showLabels: Boolean,
    private val density: Float,
    private val coroutineContext: kotlin.coroutines.CoroutineContext
) : Overlay() {
    
    // Lazy-loaded visible points (computed asynchronously)
    @Volatile
    private var visiblePoints: List<PointData> = emptyList()
    @Volatile
    private var isComputing = false
    @Volatile
    private var isReady = false
    
    // Frame rate limiting to prevent GPU overload
    private var lastDrawTime = 0L // Throttle drawing
    private var frameSkipCounter = 0 // Skip frames during heavy load
    
    private data class PointData(val lat: Float, val lon: Float, val isHighlighted: Boolean)
    
    // Cache paint objects (reused, never recreated)
    private val regularPointPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        isAntiAlias = false // Disable anti-aliasing for better performance
    }
    
    private val highlightedPointPaint = Paint().apply {
        color = Color.parseColor("#FF5722")
        style = Paint.Style.FILL
        isAntiAlias = false
    }
    
    private val strokePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        isAntiAlias = false
    }
    
    // EXTREMELY small point radius to reduce GPU load
    private val pointRadius = when {
        zoom < 8 -> 1f * density   // Very small at low zoom (reduced from 1.5f)
        zoom < 10 -> 1.5f * density  // Reduced from 2f
        zoom < 12 -> 2f * density     // Reduced from 2.5f
        zoom < 14 -> 2.5f * density   // Reduced from 3f
        else -> 3f * density           // Reduced from 3.5f
    }
    
    // Maximum points to render per frame - dynamically calculated based on zoom
    // Android 11+ optimized: EXTREMELY reduced limits to prevent GPU overload
    private val maxPointsToRender: Int
        get() {
            val isAndroid11Plus = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
            val baseLimit = when {
                zoom < 6 -> 20       // Very low zoom: 20 points max (was 50)
                zoom < 8 -> 50      // Low zoom: 50 points max (was 150)
                zoom < 10 -> 100     // Medium zoom: 100 points max (was 300)
                zoom < 12 -> 200    // High zoom: 200 points max (was 600)
                zoom < 14 -> 300    // Very high zoom: 300 points max (was 1000)
                else -> 500         // Maximum zoom: 500 points max (was 1500)
            }
            // Reduce by 70% for Android 11+ to prevent GPU overload
            return if (isAndroid11Plus) (baseLimit * 0.3).toInt() else baseLimit
        }
    
    // Compute points asynchronously in background
    fun computePointsAsync() {
        if (isComputing) return
        isComputing = true
        
        kotlinx.coroutines.CoroutineScope(coroutineContext).launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val computed = computeVisiblePoints()
                if (isActive) {
                    visiblePoints = computed
                    isReady = true
                }
            } finally {
                isComputing = false
            }
        }
    }
    
    private fun computeVisiblePoints(): List<PointData> {
        val result = mutableListOf<PointData>()
        
        val totalPoints = allPoints.size
        val maxRender = maxPointsToRender
        
        // Dynamic, scalable sampling: Calculate sample rate to ensure we never exceed maxRender
        // Formula: sampleRate = max(1, totalPoints / (maxRender * safetyFactor))
        // Safety factor accounts for viewport culling (not all points are visible)
        val viewportArea = (viewport.latNorth - viewport.latSouth) * (viewport.lonEast - viewport.lonWest)
        val estimatedVisibleRatio = kotlin.math.min(1.0, viewportArea / 180.0) // Rough estimate
        val safetyFactor = kotlin.math.max(2.0, 1.0 / estimatedVisibleRatio.coerceAtLeast(0.01))
        
        // Calculate base sample rate to target maxRender points
        val targetPoints = maxRender * safetyFactor
        val baseSampleRate = kotlin.math.max(1, (totalPoints / targetPoints).toInt())
        
        // Adjust sample rate based on zoom level (more aggressive at lower zoom)
        val zoomFactor = when {
            zoom < 6 -> 10.0   // Very low zoom: 10x more aggressive
            zoom < 8 -> 5.0    // Low zoom: 5x more aggressive
            zoom < 10 -> 2.5   // Medium zoom: 2.5x more aggressive
            zoom < 12 -> 1.5   // High zoom: 1.5x more aggressive
            zoom < 14 -> 1.2   // Very high zoom: 1.2x more aggressive
            else -> 1.0        // Maximum zoom: no additional reduction
        }
        
        // Android 11+ optimization: EXTREMELY aggressive sampling to prevent GPU overload
        val android11Factor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            // For very large datasets on Android 11+, use EXTREMELY aggressive sampling
            when {
                totalPoints > 100000 -> 5.0  // 5x more aggressive for 100k+ points (was 3.0)
                totalPoints > 10000 -> 4.0   // 4x more aggressive for 10k+ points (was 2.5)
                totalPoints > 1000 -> 3.0    // 3x more aggressive for 1k+ points (new)
                else -> 2.0                   // 2x more aggressive for smaller datasets
            }
        } else {
            1.0
        }
        
        val sampleRate = kotlin.math.max(1, (baseSampleRate * zoomFactor * android11Factor).toInt())
        
        // Optimized: Early exit if we have enough points
        var count = 0
        var processed = 0
        
        // Spatial density culling: Minimum distance between points (in degrees)
        // Prevents overlapping points from being drawn
        // Scale based on zoom and viewport size
        val viewportWidth = viewport.lonEast - viewport.lonWest
        val viewportHeight = viewport.latNorth - viewport.latSouth
        val viewportSize = kotlin.math.max(viewportWidth, viewportHeight)
        
        // Minimum distance scales with zoom and viewport size
        // At low zoom: larger distance (fewer points visible)
        // At high zoom: smaller distance (more points visible)
        val minDistance = when {
            zoom < 8 -> viewportSize / 50.0   // ~2% of viewport
            zoom < 10 -> viewportSize / 100.0  // ~1% of viewport
            zoom < 12 -> viewportSize / 200.0  // ~0.5% of viewport
            zoom < 14 -> viewportSize / 400.0  // ~0.25% of viewport
            else -> viewportSize / 800.0      // ~0.125% of viewport
        }.coerceIn(0.0001, 0.01) // Clamp to reasonable bounds
        
        // Use density culling for all datasets, but optimize for large ones
        // For very large datasets, use a spatial hash instead of checking all points
        val useSpatialHash = totalPoints > 10_000
        val addedPoints = if (!useSpatialHash) mutableListOf<Pair<Float, Float>>() else null
        
        // Spatial hash for large datasets (O(1) lookup instead of O(n))
        val spatialHash = if (useSpatialHash) {
            val hashSize = kotlin.math.min(100, kotlin.math.max(10, (totalPoints / 1000).toInt()))
            mutableMapOf<Pair<Int, Int>, Boolean>()
        } else null
        
        // Unified algorithm that works for any dataset size
        // Use spatial grid for efficient viewport culling
        val gridSize = when {
            totalPoints < 1_000 -> 20      // Small datasets: fine grid
            totalPoints < 10_000 -> 30     // Medium datasets: medium grid
            totalPoints < 100_000 -> 40    // Large datasets: coarser grid
            else -> 50                      // Very large datasets: coarse grid
        }
        
        val latStep = (viewport.latNorth - viewport.latSouth) / gridSize
        val lonStep = (viewport.lonEast - viewport.lonWest) / gridSize
        
        // Use grid-based spatial indexing for all datasets
        val grid = Array(gridSize) { Array(gridSize) { mutableListOf<com.nexova.survedgeapp.data.model.SurveyPoint>() } }
        
        // Populate grid with sampling
        for (point in allPoints) {
            if (count >= maxRender) break
            
            // Apply sampling
            if (processed % sampleRate == 0) {
                val geoPoint = GeoPoint(point.latitude, point.longitude)
                if (viewport.contains(geoPoint)) {
                    val latIdx = ((point.latitude - viewport.latSouth) / latStep).toInt().coerceIn(0, gridSize - 1)
                    val lonIdx = ((point.longitude - viewport.lonWest) / lonStep).toInt().coerceIn(0, gridSize - 1)
                    grid[latIdx][lonIdx].add(point)
                }
            }
            processed++
        }
        
        // Extract points from grid with density culling
        for (i in 0 until gridSize) {
            for (j in 0 until gridSize) {
                for (point in grid[i][j]) {
                    if (count >= maxRender) break
                    
                    val lat = point.latitude.toFloat()
                    val lon = point.longitude.toFloat()
                    
                    // Density culling: Skip if too close to an already added point
                    var shouldSkip = false
                    
                    if (useSpatialHash && spatialHash != null) {
                        // Use spatial hash for O(1) lookup
                        val hashLat = (lat / minDistance).toInt()
                        val hashLon = (lon / minDistance).toInt()
                        val hashKey = Pair(hashLat, hashLon)
                        
                        if (spatialHash.containsKey(hashKey)) {
                            shouldSkip = true
                        } else {
                            spatialHash[hashKey] = true
                            // Limit hash size to prevent memory issues
                            if (spatialHash.size > 1000) {
                                spatialHash.clear() // Reset if too large
                            }
                        }
                    } else if (addedPoints != null) {
                        // For smaller datasets, check against recent points
                        if (addedPoints.size > 0) {
                            val checkCount = kotlin.math.min(50, addedPoints.size)
                            val startIdx = kotlin.math.max(0, addedPoints.size - checkCount)
                            for (idx in startIdx until addedPoints.size) {
                                val (addedLat, addedLon) = addedPoints[idx]
                                val latDiff = kotlin.math.abs(lat - addedLat)
                                val lonDiff = kotlin.math.abs(lon - addedLon)
                                if (latDiff < minDistance && lonDiff < minDistance) {
                                    shouldSkip = true
                                    break
                                }
                            }
                        }
                        if (!shouldSkip) {
                            addedPoints.add(Pair(lat, lon))
                            // Limit size to prevent memory issues
                            if (addedPoints.size > 200) {
                                addedPoints.removeAt(0)
                            }
                        }
                    }
                    
                    if (shouldSkip) continue
                    
                    result.add(PointData(lat, lon, point.isHighlighted))
                    count++
                }
                if (count >= maxRender) break
            }
            if (count >= maxRender) break
        }
        
        return result
    }
    
    override fun draw(canvas: Canvas?, mapView: MapView?, shadow: Boolean) {
        if (shadow || canvas == null || mapView == null) return
        
        // Don't draw if points aren't ready yet
        if (!isReady || visiblePoints.isEmpty()) return
        
        val isAndroid11Plus = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
        
        // Frame rate limiting to prevent GPU overload (less aggressive to prevent blinking)
        val currentTime = System.currentTimeMillis()
        // Only limit to 30 FPS (33ms) instead of 10 FPS to prevent blinking
        if (isAndroid11Plus && currentTime - lastDrawTime < 33) {
            return // Skip this frame - too soon since last draw
        }
        lastDrawTime = currentTime
        
        // Only skip frames for VERY large datasets (>10k points) and only every 3rd frame (33% skip)
        // This prevents blinking while still reducing GPU load
        if (isAndroid11Plus && visiblePoints.size > 10000) {
            frameSkipCounter++
            if (frameSkipCounter % 3 == 0) {
                return // Skip every 3rd frame (33% skip instead of 50%)
            }
        }
        
        val projection = mapView.projection
        val currentBounds = mapView.boundingBox
        
        // Quick bounds check - if viewport changed significantly, skip this frame
        val boundsChanged = kotlin.math.abs(currentBounds.latNorth - viewport.latNorth) > 0.2 ||
                kotlin.math.abs(currentBounds.latSouth - viewport.latSouth) > 0.2 ||
                kotlin.math.abs(currentBounds.lonEast - viewport.lonEast) > 0.2 ||
                kotlin.math.abs(currentBounds.lonWest - viewport.lonWest) > 0.2
        
        if (boundsChanged) return // Skip if viewport changed (will be redrawn with new overlay)
        
        // Android 11+ optimization: Skip drawing if zoom level changed significantly
        // This prevents rendering lag during active zooming
        val currentZoom = mapView.zoomLevelDouble
        val zoomChanged = kotlin.math.abs(currentZoom - zoom) > 0.3 // More sensitive threshold
        if (zoomChanged && isAndroid11Plus) {
            return // Skip drawing during active zoom on Android 11+
        }
        
        // Optimized drawing: Batch by paint type and skip redundant checks
        val maxDraw = maxPointsToRender.coerceAtMost(visiblePoints.size)
        if (maxDraw == 0) return
        
        // Pre-compute screen bounds for faster culling
        val screenWidth = mapView.width
        val screenHeight = mapView.height
        val margin = 50 // Margin for off-screen points
        
        // Draw points with minimal overhead
        var drawn = 0
        val pointsToDraw = if (isAndroid11Plus && maxDraw > 500) {
            // For Android 11+ with many points, process in smaller batches
            kotlin.math.min(maxDraw, 500)
        } else {
            maxDraw
        }
        
        // Android 11+ optimization: Limit drawing to prevent GPU overload (balanced to prevent blinking)
        val actualMaxDraw = if (isAndroid11Plus) {
            // Cap at 100 points per frame for Android 11+ (increased from 50 to prevent blinking)
            kotlin.math.min(pointsToDraw, 100)
        } else {
            kotlin.math.min(pointsToDraw, 150) // Also increased for older Android
        }
        
        // Optimized drawing for Android 11+ to prevent GPU overload
        if (isAndroid11Plus) {
            // Use efficient drawing with smart culling
            // Only skip points for VERY large datasets (>5k visible points) to prevent blinking
            val step = if (actualMaxDraw > 5000) 2 else 1 // Skip every 2nd point only if >5k points
            
            for (i in 0 until actualMaxDraw step step) {
                if (drawn >= maxPointsToRender) break
                
                val pointData = visiblePoints[i]
                
                // Fast geographic bounds check first (faster than projection)
                val lat = pointData.lat.toDouble()
                val lon = pointData.lon.toDouble()
                if (lat < currentBounds.latSouth || lat > currentBounds.latNorth ||
                    lon < currentBounds.lonWest || lon > currentBounds.lonEast) {
                    continue
                }
                
                // Only project if within bounds (saves expensive projection calls)
                val screenPoint = projection.toPixels(GeoPoint(lat, lon), null)
                if (screenPoint.x < -margin || screenPoint.x > screenWidth + margin ||
                    screenPoint.y < -margin || screenPoint.y > screenHeight + margin) {
                    continue // Skip off-screen points
                }
                
                // Use appropriate paint based on highlight status
                val paint = if (pointData.isHighlighted) highlightedPointPaint else regularPointPaint
                
                // Draw point (single circle, no stroke)
                canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), pointRadius, paint)
                
                drawn++
            }
        } else {
            // Standard drawing for older Android (also optimized)
            for (i in 0 until actualMaxDraw) {
                if (drawn >= maxPointsToRender) break
                
                val pointData = visiblePoints[i]
                val geoPoint = GeoPoint(pointData.lat.toDouble(), pointData.lon.toDouble())
                
                // Fast screen-space culling
                val screenPoint = projection.toPixels(geoPoint, null)
                if (screenPoint.x < -margin || screenPoint.x > screenWidth + margin ||
                    screenPoint.y < -margin || screenPoint.y > screenHeight + margin) {
                    continue // Skip off-screen points
                }
                
                val paint = if (pointData.isHighlighted) highlightedPointPaint else regularPointPaint
                
                // Draw point (single circle, no stroke for better performance)
                canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), pointRadius, paint)
                
                drawn++
            }
        }
    }
}
