package com.nexova.survedgeapp.ui.mapping.viewmodel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexova.survedgeapp.data.model.SurveyCode
import com.nexova.survedgeapp.data.model.SurveyLine
import com.nexova.survedgeapp.data.model.SurveyPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class MappingViewModel : ViewModel() {

    private val _points = MutableLiveData<List<SurveyPoint>>(emptyList())
    val points: LiveData<List<SurveyPoint>> = _points

    private val _lines = MutableLiveData<List<SurveyLine>>(emptyList())
    val lines: LiveData<List<SurveyLine>> = _lines

    private val _codes = MutableLiveData<List<SurveyCode>>(emptyList())
    val codes: LiveData<List<SurveyCode>> = _codes

    private val _isGeneratingPoints = MutableLiveData<Boolean>(false)
    val isGeneratingPoints: LiveData<Boolean> = _isGeneratingPoints

    private val _currentLocation = MutableLiveData<Location?>(null)
    val currentLocation: LiveData<Location?> = _currentLocation

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    init {
        // Initialize with default NO-CODE
        _codes.value = listOf(
            SurveyCode("NO-CODE", "NO-CODE", SurveyCode.CodeType.POINT)
        )
    }

    /**
     * Generate dummy points similar to the screenshot
     * Creates points P1-P11, B1-B4, T1, RF1, RF3
     * Based on coordinates around Delhi area (28.7041, 77.1025)
     */
    fun generateDummyPoints() {
        viewModelScope.launch {
            _isGeneratingPoints.value = true
            withContext(Dispatchers.Default) {
                val generatedPoints = mutableListOf<SurveyPoint>()
                
                // Base coordinates (Delhi area - matching screenshot pattern)
                val baseLat = 28.7041
                val baseLng = 77.1025
                
                // Generate P1-P11 points in a specific polygonal shape
                // Creating a more structured polygon pattern
                val polygonPoints = listOf(
                    Pair(baseLat + 0.0010, baseLng + 0.0000),  // P1
                    Pair(baseLat + 0.0009, baseLng + 0.0005),  // P2
                    Pair(baseLat + 0.0014, baseLng + 0.0015),  // P3
                    Pair(baseLat + 0.0011, baseLng + 0.0025),  // P4
                    Pair(baseLat + 0.0004, baseLng + 0.0030),  // P5
                    Pair(baseLat - 0.0006, baseLng + 0.0027),  // P6
                    Pair(baseLat - 0.0013, baseLng + 0.0023),  // P7
                    Pair(baseLat - 0.0016, baseLng + 0.0013),  // P8
                    Pair(baseLat - 0.0011, baseLng + 0.0003),  // P9
                    Pair(baseLat - 0.0006, baseLng - 0.0005),  // P10
                    Pair(baseLat - 0.0001, baseLng - 0.0007)   // P11
                )
                
                polygonPoints.forEachIndexed { index, point ->
                    generatedPoints.add(
                        SurveyPoint(
                            id = "P${index + 1}",
                            name = "P${index + 1}",
                            code = "BLD1",
                            latitude = point.first,
                            longitude = point.second
                        )
                    )
                }
                
                // Generate B1-B4 points near P10 and P11 area
                val bPoints = listOf(
                    Pair(baseLat - 0.0003, baseLng - 0.0010),  // B1
                    Pair(baseLat - 0.0008, baseLng - 0.0012),  // B2
                    Pair(baseLat - 0.0011, baseLng - 0.0007),  // B3
                    Pair(baseLat - 0.0006, baseLng - 0.0002)   // B4
                )
                
                bPoints.forEachIndexed { index, point ->
                    generatedPoints.add(
                        SurveyPoint(
                            id = "B${index + 1}",
                            name = "B${index + 1}",
                            code = "BLD1",
                            latitude = point.first,
                            longitude = point.second
                        )
                    )
                }
                
                // Generate T1 point (Tree) - positioned outside the polygon
                generatedPoints.add(
                    SurveyPoint(
                        id = "T1",
                        name = "T1",
                        code = "TRE",
                        latitude = baseLat + 0.0015,
                        longitude = baseLng - 0.0005
                    )
                )
                
                // Generate RF1 and RF3 points
                generatedPoints.add(
                    SurveyPoint(
                        id = "RF1",
                        name = "RF1",
                        code = "RF",
                        latitude = baseLat + 0.0004,
                        longitude = baseLng + 0.0005
                    )
                )
                
                generatedPoints.add(
                    SurveyPoint(
                        id = "RF3",
                        name = "RF3",
                        code = "RF",
                        latitude = baseLat - 0.0011,
                        longitude = baseLng + 0.0030
                    )
                )
                
                // Generate line connecting P1-P11 (closed polygon)
                val polygonLinePoints = polygonPoints.mapIndexed { index, point ->
                    SurveyPoint(
                        id = "P${index + 1}",
                        name = "P${index + 1}",
                        code = "BLD1",
                        latitude = point.first,
                        longitude = point.second
                    )
                }
                
                val polygonLine = SurveyLine(
                    id = "LINE_P1_P11",
                    name = "Polygon Line",
                    code = "BLD1",
                    points = polygonLinePoints,
                    isClosed = true
                )
                
                withContext(Dispatchers.Main) {
                    _points.value = generatedPoints
                    _lines.value = listOf(polygonLine)
                    _isGeneratingPoints.value = false
                }
            }
        }
    }
    
    /**
     * Generate N points randomly scattered and connect them
     * Some points will be highlighted for better visibility
     * Points are generated near the current device location
     * @param numberOfPoints Number of points to generate (minimum 3)
     */
    fun generatePoints(numberOfPoints: Int) {
        if (numberOfPoints < 3) {
            return // Need at least 3 points to form a polygon
        }
        
        viewModelScope.launch {
            _isGeneratingPoints.value = true
            withContext(Dispatchers.Default) {
                val generatedPoints = mutableListOf<SurveyPoint>()
                
                // Get current location or use default (Delhi area)
                val currentLocation = _currentLocation.value
                val baseLat: Double
                val baseLng: Double
                
                if (currentLocation != null) {
                    // Use current location as base
                    baseLat = currentLocation.latitude
                    baseLng = currentLocation.longitude
                    android.util.Log.d("MappingViewModel", "Generating points near current location: lat=$baseLat, lng=$baseLng")
                } else {
                    // Fallback to default location if current location is not available
                    baseLat = 28.7041
                    baseLng = 77.1025
                    android.util.Log.d("MappingViewModel", "Current location not available, using default location: lat=$baseLat, lng=$baseLng")
                }
                
                // Define area bounds for random distribution
                // Using a larger area to ensure points are well spread and visible
                val latRange = 0.0030  // ~330 meters north-south
                val lngRange = 0.0030  // ~330 meters east-west
                
                // Use a seeded random for consistent results if needed, or system random for variety
                val random = Random(System.currentTimeMillis())
                
                // Calculate how many points should be highlighted (approximately 30% of points)
                val highlightCount = maxOf(1, (numberOfPoints * 0.3).toInt())
                val highlightIndices = (0 until numberOfPoints).shuffled(random).take(highlightCount).toSet()
                
                // Generate points randomly scattered within the area
                for (i in 0 until numberOfPoints) {
                    // Generate random offset from base coordinates
                    val latOffset = random.nextDouble(-latRange, latRange)
                    val lngOffset = random.nextDouble(-lngRange, lngRange)
                    
                    val lat = baseLat + latOffset
                    val lng = baseLng + lngOffset
                    
                    // Mark some points as highlighted
                    val isHighlighted = highlightIndices.contains(i)
                    
                    generatedPoints.add(
                        SurveyPoint(
                            id = "P${i + 1}",
                            name = "P${i + 1}",
                            code = "BLD1",
                            latitude = lat,
                            longitude = lng,
                            isHighlighted = isHighlighted
                        )
                    )
                }
                
                // Create a line connecting all points in order (closed polygon)
                // Points are connected in the order they were generated
                val polygonLine = SurveyLine(
                    id = "LINE_P1_P$numberOfPoints",
                    name = "Polygon Line",
                    code = "BLD1",
                    points = generatedPoints,
                    isClosed = true
                )
                
                withContext(Dispatchers.Main) {
                    _points.value = generatedPoints
                    _lines.value = listOf(polygonLine)
                    _isGeneratingPoints.value = false
                }
            }
        }
    }
    
    /**
     * Clear all points and lines
     */
    fun clearAll() {
        _points.value = emptyList()
        _lines.value = emptyList()
    }
    
    /**
     * Add a single point
     */
    fun addPoint(point: SurveyPoint) {
        val currentPoints = _points.value?.toMutableList() ?: mutableListOf()
        currentPoints.add(point)
        _points.value = currentPoints
    }
    
    /**
     * Remove a point by ID
     */
    fun removePoint(pointId: String) {
        val currentPoints = _points.value?.toMutableList() ?: mutableListOf()
        currentPoints.removeAll { it.id == pointId }
        _points.value = currentPoints
    }

    /**
     * Start tracking device location
     */
    fun startLocationTracking(context: Context) {
        if (locationManager != null) {
            android.util.Log.d("MappingViewModel", "Location tracking already started")
            return // Already tracking
        }

        locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Check if location permission is granted
        val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            android.util.Log.w("MappingViewModel", "Location permission not granted")
            return
        }

        // Check if location services are enabled
        val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false
        
        if (!isGpsEnabled && !isNetworkEnabled) {
            android.util.Log.w("MappingViewModel", "Location services not enabled")
            return
        }

        android.util.Log.d("MappingViewModel", "Starting location tracking...")

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                android.util.Log.d("MappingViewModel", "Location updated: lat=${location.latitude}, lng=${location.longitude}")
                _currentLocation.postValue(location)
            }

            override fun onProviderEnabled(provider: String) {
                android.util.Log.d("MappingViewModel", "Provider enabled: $provider")
            }
            
            override fun onProviderDisabled(provider: String) {
                android.util.Log.d("MappingViewModel", "Provider disabled: $provider")
            }
        }

        try {
            // Try to get last known location first
            val lastKnownLocation = if (isGpsEnabled) {
                locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else null
                ?: if (isNetworkEnabled) {
                    locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                } else null
                
            if (lastKnownLocation != null) {
                android.util.Log.d("MappingViewModel", "Using last known location: lat=${lastKnownLocation.latitude}, lng=${lastKnownLocation.longitude}")
                _currentLocation.postValue(lastKnownLocation)
            } else {
                android.util.Log.d("MappingViewModel", "No last known location available")
            }

            // Request location updates with maximum frequency for cm-level precision tracking
            // Using the smallest possible values to get updates for even 1cm movements
            if (isGpsEnabled) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0L, // Update as fast as possible (0ms = minimum interval, typically ~100ms)
                    0f, // Update on every movement, even cm-level changes (0f = no minimum distance)
                    locationListener!!
                )
                android.util.Log.d("MappingViewModel", "Requested GPS location updates (maximum frequency for cm-level precision)")
            }
            
            // Also request passive location updates for additional location sources
            // This helps catch location updates from other apps that might have better precision
            if (isGpsEnabled || isNetworkEnabled) {
                try {
                    locationManager?.requestLocationUpdates(
                        LocationManager.PASSIVE_PROVIDER,
                        0L,
                        0f,
                        locationListener!!
                    )
                    android.util.Log.d("MappingViewModel", "Requested Passive location updates")
                } catch (e: Exception) {
                    android.util.Log.d("MappingViewModel", "Passive provider not available: ${e.message}")
                }
            }
            
            if (isNetworkEnabled) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    100L, // Network updates less frequently (100ms) as it's less precise
                    0f, // Still update on every movement
                    locationListener!!
                )
                android.util.Log.d("MappingViewModel", "Requested Network location updates (100ms interval)")
            }
        } catch (e: SecurityException) {
            // Permission not granted
            android.util.Log.e("MappingViewModel", "SecurityException: Permission not granted", e)
            e.printStackTrace()
        } catch (e: Exception) {
            android.util.Log.e("MappingViewModel", "Error starting location tracking", e)
            e.printStackTrace()
        }
    }

    /**
     * Stop tracking device location
     */
    fun stopLocationTracking() {
        locationListener?.let { listener ->
            locationManager?.removeUpdates(listener)
        }
        locationListener = null
        locationManager = null
    }

    /**
     * Add a new code
     */
    fun addCode(name: String, type: SurveyCode.CodeType) {
        val currentCodes = _codes.value?.toMutableList() ?: mutableListOf()
        val newCode = SurveyCode(
            id = "C-${System.currentTimeMillis()}",
            name = name.trim(),
            type = type
        )
        currentCodes.add(newCode)
        _codes.value = currentCodes
    }

    /**
     * Get next available point ID
     */
    fun getNextPointId(): String {
        val currentPoints = _points.value ?: emptyList()
        val pointNumbers = currentPoints.mapNotNull { point ->
            point.id.removePrefix("P").toIntOrNull()
        }
        val nextNumber = if (pointNumbers.isEmpty()) 1 else (pointNumbers.maxOrNull() ?: 0) + 1
        return "P$nextNumber"
    }

    /**
     * Check if point ID already exists
     */
    fun pointIdExists(pointId: String): Boolean {
        return _points.value?.any { it.id == pointId } == true
    }

    /**
     * Collect a point with measurement settings
     * @param pointId Optional point ID (auto-generated if empty)
     * @param codeId Code ID to assign
     * @param fixOnly Whether to only collect when GPS fix is available
     * @param averagingSeconds Total averaging time in seconds
     * @param onProgress Callback for progress updates (0-100)
     * @param onComplete Callback when measurement is complete with the created point
     * @param onError Callback for errors
     */
    fun collectPoint(
        pointId: String?,
        codeId: String,
        fixOnly: Boolean,
        averagingSeconds: Int,
        onProgress: (Int, String) -> Unit,
        onComplete: (SurveyPoint) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val location = _currentLocation.value
            if (location == null) {
                onError("No GPS location available. Waiting for GPS fix...")
                return@launch
            }

            // Check GPS fix if required
            if (fixOnly) {
                val accuracy = location.accuracy
                if (accuracy == null || accuracy < 0 || accuracy >= 15) {
                    onError("GPS fix is not available. Current accuracy: ${accuracy?.let { String.format("%.1f", it) } ?: "N/A"}m (need < 15m). Please wait for better signal or disable 'FIX only' option.")
                    return@launch
                }
            }

            val finalPointId = pointId?.trim()?.takeIf { it.isNotEmpty() } ?: getNextPointId()

            // Check for duplicate ID
            if (pointIdExists(finalPointId)) {
                onError("A point with ID '$finalPointId' already exists.")
                return@launch
            }

            // If no averaging needed, create point immediately
            if (averagingSeconds <= 0) {
                val point = SurveyPoint(
                    id = finalPointId,
                    name = finalPointId,
                    code = _codes.value?.find { it.id == codeId }?.name ?: "NO-CODE",
                    latitude = location.latitude,
                    longitude = location.longitude,
                    elevation = if (location.hasAltitude()) location.altitude else null
                )
                addPoint(point)
                onComplete(point)
                return@launch
            }

            // Start averaging process
            val readings = mutableListOf<Location>()
            val startTime = System.currentTimeMillis()
            val totalTimeMs = averagingSeconds * 1000L
            val updateInterval = 200L // Update progress every 200ms
            val readingInterval = 500L // Collect GPS reading every 500ms

            var progressJob: kotlinx.coroutines.Job? = null
            var readingJob: kotlinx.coroutines.Job? = null

            // Progress update job
            progressJob = launch {
                while (true) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = ((elapsed.toFloat() / totalTimeMs) * 100).toInt().coerceIn(0, 100)
                    val remainingSeconds = ((totalTimeMs - elapsed) / 1000).coerceAtLeast(0)
                    val status = if (remainingSeconds > 0) {
                        "Measuring... ${remainingSeconds}s remaining"
                    } else {
                        "Finalizing..."
                    }
                    onProgress(progress, status)
                    if (elapsed >= totalTimeMs) break
                    kotlinx.coroutines.delay(updateInterval)
                }
            }

            // GPS reading collection job
            readingJob = launch {
                while (System.currentTimeMillis() - startTime < totalTimeMs) {
                    val currentLocation = _currentLocation.value
                    if (currentLocation != null) {
                        // Check fix if required
                        if (!fixOnly || (currentLocation.accuracy != null && currentLocation.accuracy >= 0 && currentLocation.accuracy < 15)) {
                            readings.add(currentLocation)
                        }
                    }
                    kotlinx.coroutines.delay(readingInterval)
                }
            }

            // Wait for averaging to complete
            progressJob.join()
            readingJob.join()

            if (readings.isEmpty()) {
                onError("Could not collect GPS readings. Please try again.")
                return@launch
            }

            // Calculate average coordinates
            val avgLat = readings.map { it.latitude }.average()
            val avgLon = readings.map { it.longitude }.average()
            val avgAlt = readings.filter { it.hasAltitude() }.map { it.altitude }.average().takeIf { !it.isNaN() }

            // Create point with averaged coordinates
            val point = SurveyPoint(
                id = finalPointId,
                name = finalPointId,
                code = _codes.value?.find { it.id == codeId }?.name ?: "NO-CODE",
                latitude = avgLat,
                longitude = avgLon,
                elevation = avgAlt
            )

            addPoint(point)
            onComplete(point)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationTracking()
    }
}