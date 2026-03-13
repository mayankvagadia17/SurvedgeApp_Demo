package com.nexova.survedge.ui.mapping.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class BullseyeOverlay(context: Context) : Overlay() {

    private val bgPaint = Paint().apply {
        color = androidx.core.content.ContextCompat.getColor(context, com.nexova.survedge.R.color.neutral_dark_2) // Dark background (outside)
        style = Paint.Style.FILL
    }
    
    private val successBgPaint = Paint().apply {
        color = androidx.core.content.ContextCompat.getColor(context, com.nexova.survedge.R.color.light_green_secondary) // Muted Green Success Background
        style = Paint.Style.FILL
    }

    private val crosshairPaint = Paint().apply {
        color = androidx.core.content.ContextCompat.getColor(context, com.nexova.survedge.R.color.gray_300) // Medium grey crosshairs (matching reference)
        style = Paint.Style.STROKE
        strokeWidth = 4f // Thicker lines
        isAntiAlias = true
    }

    private val targetCirclePaint = Paint().apply {
        color = androidx.core.content.ContextCompat.getColor(context, com.nexova.survedge.R.color.dark_overlay_low) // Filled Grey Circle (darker than BG)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    // Rover Circle (Blue)
    private val roverPaint = Paint().apply {
        color = androidx.core.content.ContextCompat.getColor(context, com.nexova.survedge.R.color.blue_500) // Material Blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Rover Inside (Green with Black Border)
    private val roverSuccessPaint = Paint().apply {
        color = androidx.core.content.ContextCompat.getColor(context, com.nexova.survedge.R.color.green_800) // Darker Green for Rover
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val roverSuccessBorderPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 35f // Thick black border
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 120f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private var roverX: Float = 0f
    private var roverY: Float = 0f

    // Compatibility properties (accessed by MappingFragmentLogic)
    var verticalDistance: Double = 0.0 
    var isInTolerance: Boolean = false

    private val trianglePaint = Paint().apply {
        color = androidx.core.content.ContextCompat.getColor(context, com.nexova.survedge.R.color.slate_gray)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        
        // FORCE North-Up: If something rotates the map, reset it immediately.
        if (mapView.mapOrientation != 0f) {
            mapView.mapOrientation = 0f
        }

        val centerX = mapView.width / 2f
        val centerY = mapView.height / 2f
        val width = mapView.width.toFloat()
        val height = mapView.height.toFloat()
        
        // Check Visual Entry (Rover Center inside 340px Ring)
        val distFromCenter = kotlin.math.hypot(roverX, roverY)
        val isInsideRing = distFromCenter < 340f 

        // 1. Draw Background (Green if inside ring, Light Grey if outside)
        if (isInsideRing) {
            canvas.drawRect(0f, 0f, width, height, successBgPaint)
        } else {
            canvas.drawRect(0f, 0f, width, height, bgPaint)
        }
        
        // 2. Draw Target Circle
        canvas.drawCircle(centerX, centerY, 280f, targetCirclePaint)

        // 3. Draw Crosshairs
        val crosshairRadius = 330f 
        canvas.drawLine(centerX - crosshairRadius, centerY, centerX + crosshairRadius, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - crosshairRadius, centerX, centerY + crosshairRadius, crosshairPaint)

        // 4. Draw Rover (Moving)
        val currentX = centerX + roverX
        val currentY = centerY + roverY

        // Draw Arch if Outside
        if (!isInsideRing) {
            // Calculate bearing to center (0,0) from rover (roverX, roverY)
            // Vector to center is (-roverX, -roverY)
            val angleRad = kotlin.math.atan2(-roverY, -roverX)
            // Convert to degrees and add 90 because the drawing defaults to pointing UP (North/-Y)
            // standard atan2: 0 is Right, -90 is Up. Our shape points Up.
            // If we want it to point Up, angle is -90. -90 + 90 = 0 rotation. Correct.
            val angleToCenter = Math.toDegrees(angleRad.toDouble()).toFloat() + 90f
            
            drawArch(canvas, currentX, currentY, angleToCenter)
        }

        // Always Draw Rover Body (Green with Black Border)
        canvas.drawCircle(currentX, currentY, 200f, roverSuccessPaint)
        canvas.drawCircle(currentX, currentY, 200f, roverSuccessBorderPaint)

        // "R" Text on Rover
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds("R", 0, 1, textBounds)
        val textY = currentY + (textBounds.height() / 2f)
        canvas.drawText("R", currentX, textY, textPaint)
    }

    private fun drawArch(canvas: Canvas, cx: Float, cy: Float, rotation: Float) {
        // Adapt logic from CustomLocationPinDrawable to fit our 200f radius
        // CustomPin Logic: 
        // circleRadius = 0.12 * width
        // outerRingRadius = 0.216 * width
        // In our case, effective outer radius is ~220f (200 + half border)
        // Let's assume outerRingRadius = 220f
        // Then width = 220 / 0.216 ~= 1018
        
        val outerRingRadius = 220f
        val virtualWidth = outerRingRadius / 0.216f
        val virtualHeight = virtualWidth // Assuming square aspect ratio for props
        
        // Calcs derived from CustomLocationPinDrawable
        // In that file, triangleBottomY is calculated relative to circleCenterY.
        // Here, (cx, cy) is our circleCenter.
        // triangleBottomY = circleCenterY - outerRingRadius - gap
        // gap = -width * 0.05
        
        val gap = -virtualWidth * 0.05f
        val triangleBottomDisplacement = -outerRingRadius - gap // Displacement from center Y (Upwards)
        
        // triangleTopY = height * 0.3
        // circleCenterY = height * 0.7
        // Dist Top to Center = 0.4 * height = 0.4 * virtualWidth
        val triangleTopDisplacement = -virtualWidth * 0.4f // Displacement from center Y (Upwards)
        
        val triangleWidth = outerRingRadius * 1.5f
        val curveDepth = virtualWidth * 0.12f
        
        // Construct Path relative to (0,0) then translate/rotate
        val path = android.graphics.Path()
        
        val topY = triangleTopDisplacement
        val bottomY = triangleBottomDisplacement
        val leftX = -triangleWidth / 2f
        val rightX = triangleWidth / 2f
        
        // logic: moveTo(center, top), lineTo(bottomLeft), quadTo(center, control, bottomRight), close
        val controlY = bottomY - curveDepth
        
        path.moveTo(0f, topY)
        path.lineTo(leftX, bottomY)
        path.quadTo(0f, controlY, rightX, bottomY)
        path.close()
        
        // Transform
        val matrix = android.graphics.Matrix()
        // 1. Rotate around (0,0) - which corresponds to center of rover
        matrix.postRotate(rotation)
        // 2. Translate to current rover position
        matrix.postTranslate(cx, cy)
        
        path.transform(matrix)
        
        canvas.drawPath(path, trianglePaint)
    }

    fun updateRoverPosition(offsetXPixels: Float, offsetYPixels: Float) {
        roverX = offsetXPixels
        roverY = offsetYPixels
    }
}
