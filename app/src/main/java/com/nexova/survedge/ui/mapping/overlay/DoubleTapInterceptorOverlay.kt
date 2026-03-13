package com.nexova.survedge.ui.mapping.overlay

import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class DoubleTapInterceptorOverlay(
    private val protectedViews: List<View>
) : Overlay() {

    private var gestureDetector: GestureDetector? = null

    init {
        protectedViews.firstOrNull()?.context?.let { context ->
            gestureDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                return isTouchInProtectedView(e)
            }

            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                return isTouchInProtectedView(e)
            }
                }
            )
        }
    }

    private fun isTouchInProtectedView(event: MotionEvent): Boolean {
        val screenX = event.rawX
        val screenY = event.rawY

        for (view in protectedViews) {
            if (isPointInView(screenX, screenY, view)) {
                return true
            }
        }
        return false
    }

    private fun isPointInView(x: Float, y: Float, view: View): Boolean {
        if (!view.isShown) return false
        
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]
        val right = left + view.width
        val bottom = top + view.height
        
        return x >= left && x <= right && y >= top && y <= bottom
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        val screenX = event.rawX
        val screenY = event.rawY

        for (view in protectedViews) {
            if (isPointInView(screenX, screenY, view)) {
                if (gestureDetector?.onTouchEvent(event) == true) {
                    return true
                }
                return false
            }
        }

        return false
    }
}

