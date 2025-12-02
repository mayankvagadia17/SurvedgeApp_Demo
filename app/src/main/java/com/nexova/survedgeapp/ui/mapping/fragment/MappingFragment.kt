package com.nexova.survedgeapp.ui.mapping.fragment

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.lifecycle.ViewModelProvider
import com.nexova.survedgeapp.R
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

class MappingFragment : Fragment() {

    private var _binding: FragmentMappingBinding? = null
    private val binding get() = _binding!!

    protected lateinit var viewModel: MappingViewModel
    private var mapView: MapView? = null
    private var mapController: IMapController? = null
    private val isMapReady = AtomicBoolean(false)
    private var hasCenteredOnLocation = false
    
    // Overlays for lines and current location
    private val lineOverlays = mutableListOf<Polyline>()
    private var currentLocationMarker: Marker? = null

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
                return false
            }
            
            override fun onZoom(event: ZoomEvent?): Boolean {
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
        
        // Save button
        btnSave.setOnClickListener {
            val pointId = etPointId.text.toString()
            val note = etNote.text.toString()
            
            // TODO: Handle save action
            Toast.makeText(requireContext(), "Point saved: $pointId", Toast.LENGTH_SHORT).show()
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
        lineOverlays.clear()
        
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
                map.invalidate()
            }
            
            Log.d("MappingFragment", "Location pin set at: lng=$lng, lat=$lat")
        } else {
            Log.d("MappingFragment", "Location is null, clearing location pin")
            // Remove current location marker
            currentLocationMarker?.let { marker ->
                map.overlays.remove(marker)
                // Throttle invalidate to prevent lag
                view?.post {
                    map.invalidate()
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
