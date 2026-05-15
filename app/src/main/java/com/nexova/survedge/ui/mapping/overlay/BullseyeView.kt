package com.nexova.survedge.ui.mapping.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.nexova.survedge.R

class BullseyeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.neutral_dark_2)
        style = Paint.Style.FILL
    }
    
    private val successBgPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.light_green_secondary)
        style = Paint.Style.FILL
    }

    private val crosshairPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.gray_300)
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val targetCirclePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.dark_overlay_low)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val roverSuccessPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.green_800)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val roverSuccessBorderPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 35f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 120f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }

    private val trianglePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.slate_gray)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private var roverX: Float = 0f
    private var roverY: Float = 0f

    // Compatibility properties
    var verticalDistance: Double = 0.0 
    var isInTolerance: Boolean = false

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        
        val distFromCenter = kotlin.math.hypot(roverX, roverY)
        val isInsideRing = distFromCenter < 340f 

        // 1. Draw Background
        if (isInsideRing) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), successBgPaint)
        } else {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        }
        
        // 2. Draw Target Circle
        canvas.drawCircle(centerX, centerY, 280f, targetCirclePaint)

        // 3. Draw Crosshairs
        val crosshairRadius = 330f 
        canvas.drawLine(centerX - crosshairRadius, centerY, centerX + crosshairRadius, centerY, crosshairPaint)
        canvas.drawLine(centerX, centerY - crosshairRadius, centerX, centerY + crosshairRadius, crosshairPaint)

        // 4. Draw Rover
        val currentX = centerX + roverX
        val currentY = centerY + roverY

        if (!isInsideRing) {
            val angleRad = kotlin.math.atan2(-roverY, -roverX)
            val angleToCenter = Math.toDegrees(angleRad.toDouble()).toFloat() + 90f
            drawArch(canvas, currentX, currentY, angleToCenter)
        }

        canvas.drawCircle(currentX, currentY, 200f, roverSuccessPaint)
        canvas.drawCircle(currentX, currentY, 200f, roverSuccessBorderPaint)

        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds("R", 0, 1, textBounds)
        val textY = currentY + (textBounds.height() / 2f)
        canvas.drawText("R", currentX, textY, textPaint)
    }

    private fun drawArch(canvas: Canvas, cx: Float, cy: Float, rotation: Float) {
        val outerRingRadius = 220f
        val virtualWidth = outerRingRadius / 0.216f
        
        val gap = -virtualWidth * 0.05f
        val triangleBottomDisplacement = -outerRingRadius - gap
        val triangleTopDisplacement = -virtualWidth * 0.4f
        
        val triangleWidth = outerRingRadius * 1.5f
        val curveDepth = virtualWidth * 0.12f
        
        val path = android.graphics.Path()
        val topY = triangleTopDisplacement
        val bottomY = triangleBottomDisplacement
        val leftX = -triangleWidth / 2f
        val rightX = triangleWidth / 2f
        
        val controlY = bottomY - curveDepth
        
        path.moveTo(0f, topY)
        path.lineTo(leftX, bottomY)
        path.quadTo(0f, controlY, rightX, bottomY)
        path.close()
        
        val matrix = android.graphics.Matrix()
        matrix.postRotate(rotation)
        matrix.postTranslate(cx, cy)
        path.transform(matrix)
        
        canvas.drawPath(path, trianglePaint)
    }

    fun updateRoverPosition(offsetXPixels: Float, offsetYPixels: Float) {
        roverX = offsetXPixels
        roverY = offsetYPixels
        invalidate()
    }
}
