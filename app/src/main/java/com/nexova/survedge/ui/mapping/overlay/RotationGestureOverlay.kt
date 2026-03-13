package com.nexova.survedge.ui.mapping.overlay

import android.view.MotionEvent
import kotlin.math.atan2
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class RotationGestureOverlay(private val mapView: MapView) : Overlay() {
    
    private var rotationAngle: Float = 0f
    private var targetRotationAngle: Float = 0f
    private var previousAngle: Float = 0f
    private var isRotating: Boolean = false
    private var pivotX: Float = 0f
    private var pivotY: Float = 0f
    
    // Smoothing factor (0.0 to 1.0) - higher value = more responsive, lower = more smoothing
    // Using a high value for responsive rotation while still smoothing out jitter
    private val smoothingFactor: Float = 0.75f
    
    private var pointer1Id: Int = -1
    private var pointer2Id: Int = -1
    private var pointer1X: Float = 0f
    private var pointer1Y: Float = 0f
    private var pointer2X: Float = 0f
    private var pointer2Y: Float = 0f
    
    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (!isEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointer1Id = event.getPointerId(0)
                pointer1X = event.getX(0)
                pointer1Y = event.getY(0)
                isRotating = false
            }
            
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    pointer2Id = event.getPointerId(1)
                    pointer2X = event.getX(1)
                    pointer2Y = event.getY(1)
                    
                    // Calculate pivot point (center between two fingers)
                    pivotX = (pointer1X + pointer2X) / 2f
                    pivotY = (pointer1Y + pointer2Y) / 2f
                    
                    // Calculate initial angle
                    previousAngle = calculateAngle(pointer1X, pointer1Y, pointer2X, pointer2Y)
                    
                    // Sync target rotation with current map orientation to avoid jump
                    rotationAngle = mapView.mapOrientation
                    targetRotationAngle = rotationAngle
                    
                    isRotating = true
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isRotating && event.pointerCount == 2) {
                    val index1 = event.findPointerIndex(pointer1Id)
                    val index2 = event.findPointerIndex(pointer2Id)
                    
                    if (index1 >= 0 && index2 >= 0) {
                        val x1 = event.getX(index1)
                        val y1 = event.getY(index1)
                        val x2 = event.getX(index2)
                        val y2 = event.getY(index2)
                        
                        // Update pivot point
                        pivotX = (x1 + x2) / 2f
                        pivotY = (y1 + y2) / 2f
                        
                        // Calculate current angle
                        val currentAngle = calculateAngle(x1, y1, x2, y2)
                        
                        // Calculate rotation delta
                        var deltaAngle = currentAngle - previousAngle
                        
                        // Normalize angle to -180 to 180 range
                        while (deltaAngle > 180) deltaAngle -= 360
                        while (deltaAngle < -180) deltaAngle += 360
                        
                        // Update target rotation angle
                        targetRotationAngle += deltaAngle
                        
                        // Normalize target angle to 0-360 range
                        targetRotationAngle = (targetRotationAngle % 360 + 360) % 360
                        
                        previousAngle = currentAngle
                        
                        // Apply smooth rotation to map view
                        applySmoothRotation()
                    }
                }
            }
            
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) {
                    val pointerIndex = event.actionIndex
                    val pointerId = event.getPointerId(pointerIndex)
                    
                    if (pointerId == pointer1Id || pointerId == pointer2Id) {
                        isRotating = false
                    }
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isRotating = false
                pointer1Id = -1
                pointer2Id = -1
            }
        }
        
        return false // Let other overlays handle the event too
    }
    
    private fun calculateAngle(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val deltaX = x2 - x1
        val deltaY = y2 - y1
        return Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()
    }
    
    private fun applySmoothRotation() {
        // Calculate the shortest rotation path
        var angleDiff = targetRotationAngle - rotationAngle
        
        // Normalize to -180 to 180 range for shortest path
        while (angleDiff > 180) angleDiff -= 360
        while (angleDiff < -180) angleDiff += 360
        
        // Apply exponential smoothing (low-pass filter)
        rotationAngle += angleDiff * smoothingFactor
        
        // Normalize rotation angle to 0-360 range
        rotationAngle = (rotationAngle % 360 + 360) % 360
        
        // Use osmdroid's native rotation method - this properly handles tile loading
        mapView.setMapOrientation(rotationAngle)
    }
    
    fun resetRotation() {
        rotationAngle = 0f
        targetRotationAngle = 0f
        mapView.setMapOrientation(0f)
    }
    
    fun getRotation(): Float = rotationAngle
}

