package com.nexova.survedge.ui.custom

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.widget.HorizontalScrollView
import kotlin.math.abs

class SnappingHorizontalScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private var velocityTracker: VelocityTracker? = null

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(ev)

        val action = ev.action
        when (action) {
            MotionEvent.ACTION_UP -> {
                velocityTracker?.computeCurrentVelocity(1000)
                val velocityX = velocityTracker?.xVelocity?.toInt() ?: 0

                val width = width
                if (width > 0) {
                    val currentScrollX = scrollX
                    val targetPage: Int

                    // VelocityX > 0 means swiping right (content moves right, seeing left content) -> Previous Page (0)
                    // VelocityX < 0 means swiping left (content moves left, seeing right content) -> Next Page (1)
                    
                    if (abs(velocityX) > 500) {
                        // Strong fling: obey direction
                        targetPage = if (velocityX < 0) 1 else 0
                    } else {
                        // Weak fling: obey position
                        targetPage = if (currentScrollX > width / 2) 1 else 0
                    }
                    
                    smoothScrollTo(targetPage * width, 0)
                }

                velocityTracker?.recycle()
                velocityTracker = null
                
                // Return true to prevent super.onTouchEvent from triggering a native fling
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                snapToNearestPage()
                velocityTracker?.recycle()
                velocityTracker = null
                return true // Consume cancel
            }
        }

        return super.onTouchEvent(ev)
    }

    private fun snapToNearestPage() {
        val width = width
        if (width > 0) {
            val scrollX = scrollX
            val page = if (scrollX > width / 2) 1 else 0
            smoothScrollTo(page * width, 0)
        }
    }
}
