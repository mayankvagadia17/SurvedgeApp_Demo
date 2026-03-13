package com.nexova.survedge.ui.mapping.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.nexova.survedge.R

class CustomLocationPinDrawable(
    private val context: Context,
    var text: String = "R",
    var triangleRotation: Float = 0f
) : Drawable() {

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.stakeout_connection_line)
        style = Paint.Style.FILL
    }

    private val orangeCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
        style = Paint.Style.FILL
    }

    private val trianglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.slate_gray)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.black_25)
        style = Paint.Style.FILL
    }

    private val outerRingStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
    }

    private val orangeCircleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
    }

    private val triangleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val centerX = width / 2f

        val circleRadius = width * 0.12f
        val outerRingRadius = circleRadius * 1.80f
        val circleCenterY = height * 0.70f
        val gapBetweenCircleAndTriangle = -width * 0.05f
        val shadowOffsetX = width * 0.015f
        val shadowOffsetY = width * 0.015f
        val strokeWidth = width * 0.03f

        outerRingStrokePaint.strokeWidth = strokeWidth
        orangeCircleStrokePaint.strokeWidth = 0f
        triangleStrokePaint.strokeWidth = 0f

        canvas.drawCircle(centerX + shadowOffsetX, circleCenterY + shadowOffsetY, outerRingRadius, shadowPaint)
        canvas.drawCircle(centerX + shadowOffsetX, circleCenterY + shadowOffsetY, circleRadius, shadowPaint)
        
        canvas.drawCircle(centerX, circleCenterY, outerRingRadius, outerRingPaint)
        canvas.drawCircle(centerX, circleCenterY, outerRingRadius, outerRingStrokePaint)
        canvas.drawCircle(centerX, circleCenterY, circleRadius, orangeCirclePaint)
        canvas.drawCircle(centerX, circleCenterY, circleRadius, orangeCircleStrokePaint)

        val trianglePath = Path().apply {
            val triangleTopY = height * 0.3f
            val triangleBottomY = circleCenterY - outerRingRadius - gapBetweenCircleAndTriangle
            val triangleWidth = outerRingRadius * 1.5f
            
            val bottomLeftX = centerX - triangleWidth / 2f
            val bottomRightX = centerX + triangleWidth / 2f
            val curveDepth = width * 0.12f
            val curveControlY = triangleBottomY - curveDepth

            moveTo(centerX, triangleTopY)
            lineTo(bottomLeftX, triangleBottomY)
            quadTo(
                centerX,
                curveControlY,
                bottomRightX,
                triangleBottomY
            )
            close()
        }
        
        val shadowTrianglePath = Path().apply {
            val triangleTopY = height * 0.25f
            val triangleBottomY = circleCenterY - outerRingRadius - gapBetweenCircleAndTriangle
            val triangleWidth = outerRingRadius * 2f
            
            val bottomLeftX = centerX - triangleWidth / 2f + shadowOffsetX
            val bottomRightX = centerX + triangleWidth / 2f + shadowOffsetX
            val curveDepth = width * 0.2f
            val curveControlY = triangleBottomY - curveDepth + shadowOffsetY

            moveTo(centerX + shadowOffsetX, triangleTopY + shadowOffsetY)
            lineTo(bottomLeftX, triangleBottomY + shadowOffsetY)
            quadTo(
                centerX + shadowOffsetX,
                curveControlY,
                bottomRightX,
                triangleBottomY + shadowOffsetY
            )
            close()
        }
        
        if (triangleRotation != 0f) {
            val rotatedShadowPath = Path()
            val shadowMatrix = Matrix()
            shadowMatrix.postRotate(triangleRotation, centerX + shadowOffsetX, circleCenterY + shadowOffsetY)
            canvas.drawPath(rotatedShadowPath, shadowPaint)
            
            val rotatedPath = Path()
            val matrix = Matrix()
            matrix.postRotate(triangleRotation, centerX, circleCenterY)
            trianglePath.transform(matrix, rotatedPath)
            canvas.drawPath(rotatedPath, trianglePaint)
        } else {
            canvas.drawPath(trianglePath, trianglePaint)
        }

        textPaint.textSize = circleRadius * 1.5f
        val textY = circleCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, centerX, textY, textPaint)
    }

    override fun setAlpha(alpha: Int) {
        outerRingPaint.alpha = alpha
        outerRingStrokePaint.alpha = alpha
        orangeCirclePaint.alpha = alpha
        orangeCircleStrokePaint.alpha = alpha
        trianglePaint.alpha = alpha
        triangleStrokePaint.alpha = alpha
        textPaint.alpha = alpha
        shadowPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        outerRingPaint.colorFilter = colorFilter
        outerRingStrokePaint.colorFilter = colorFilter
        orangeCirclePaint.colorFilter = colorFilter
        orangeCircleStrokePaint.colorFilter = colorFilter
        trianglePaint.colorFilter = colorFilter
        triangleStrokePaint.colorFilter = colorFilter
        textPaint.colorFilter = colorFilter
        shadowPaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int {
        return android.graphics.PixelFormat.TRANSLUCENT
    }
}

