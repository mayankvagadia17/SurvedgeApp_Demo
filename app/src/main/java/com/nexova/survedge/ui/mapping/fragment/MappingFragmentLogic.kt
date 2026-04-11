// MappingFragmentLogic.kt
package com.nexova.survedge.ui.mapping.fragment

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.transition.TransitionManager
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import android.view.inputmethod.InputMethodManager

import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.internal.ViewUtils.hideKeyboard
import com.nexova.survedge.R
import com.nexova.survedge.databinding.BottomSheetCollectPointBinding
import com.nexova.survedge.databinding.BottomSheetConfirmDialogBinding
import com.nexova.survedge.databinding.BottomSheetEditLineBinding
import com.nexova.survedge.databinding.BottomSheetLineSegmentBinding
import com.nexova.survedge.databinding.BottomSheetObjectListBinding
import com.nexova.survedge.databinding.BottomSheetSelectCodeBinding
import com.nexova.survedge.ui.main.activity.MainActivity
import com.nexova.survedge.ui.mapping.adapter.CodeAdapter
import com.nexova.survedge.ui.mapping.adapter.CodeItem
import com.nexova.survedge.ui.mapping.adapter.EditPointAdapter
import com.nexova.survedge.ui.mapping.adapter.IndicatorType
import com.nexova.survedge.ui.mapping.adapter.ObjectListAdapter
import com.nexova.survedge.ui.mapping.adapter.ObjectListItem
import com.nexova.survedge.ui.mapping.drawable.CustomLocationPinDrawable
import com.nexova.survedge.ui.mapping.maplibre.OsmdroidMarkerHelper
import com.nexova.survedge.ui.mapping.maplibre.OsmdroidPolylineHelper
import com.nexova.survedge.ui.mapping.overlay.ClickablePolylineOverlay
import com.nexova.survedge.ui.mapping.overlay.DoubleTapInterceptorOverlay
import com.nexova.survedge.ui.mapping.overlay.LabeledPoint
import com.nexova.survedge.ui.mapping.overlay.PointClickHandlerOverlay
import com.nexova.survedge.ui.mapping.overlay.RotationGestureOverlay
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.nexova.survedge.ui.stakeout.model.*
import com.nexova.survedge.ui.stakeout.util.*
import com.nexova.survedge.ui.mapping.viewmodel.MappingViewModel
import com.nexova.survedge.ui.mapping.mapper.toPointEntity
import com.nexova.survedge.data.db.entity.LineEntity
import com.nexova.survedge.data.db.entity.LineWithPoints

/**
 * This class contains the bulk of the UI-handling logic that used to live inside MappingFragment.
 * It operates on the provided fragment instance, accessing its internal fields.
 * By moving the code here we dramatically shrink MappingFragment while keeping the same behaviour.
 */
// ... other imports ...
class MappingFragmentLogic(
    private val fragment: MappingFragment,
    private val viewModel: MappingViewModel
) {

    // State for Edit Line Reversion
    private var originalEditLineState: List<LabeledPoint>? = null
    private var originalEditLineCodeId: String? = null
    private var originalEditLineFeatureCode: String? = null
    private var isEditLineSaved = false
    private var selectCodeSearchWatcher: TextWatcher? = null
    private var collectSheetLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    // --- New Sheet Management System ---
    enum class SheetType {
        NONE, COLLECT_POINT, LINE_SEGMENT, EDIT_LINE, EDIT_POINT, NEW_LINE, NEW_POINT, SELECT_CODE, OBJECT_LIST, STAKEOUT, CONFIRM_DIALOG
    }

    private val sheetNavigationStack = ArrayDeque<Pair<SheetType, () -> Unit>>()
    var currentActiveSheet = SheetType.NONE
    private var isNavHidden = false

    enum class BottomSheetTransition {
        SLIDE_UP,
        SLIDE_DOWN,
        SLIDE_IN_RIGHT,
        SLIDE_OUT_LEFT,
        SLIDE_IN_LEFT,
        SLIDE_OUT_RIGHT
    }

    fun animateSheetTransition(
        outgoing: View?,
        incoming: View?,
        transition: BottomSheetTransition,
        onEnd: (() -> Unit)? = null
    ) {
        val duration = 280L
        val screenWidth = fragment.resources.displayMetrics.widthPixels.toFloat()
        val screenHeight = fragment.resources.displayMetrics.heightPixels.toFloat()
        val interpolator = FastOutSlowInInterpolator()

        // Manage bottom navigation based on sheet type
        if (incoming != null) {
            // Hide navigation for most sheets, except LINE_SEGMENT (point/line details)
            if (currentActiveSheet != SheetType.LINE_SEGMENT) {
                (fragment.activity as? MainActivity)?.binding?.bottomNavigationView?.apply {
                    animate().cancel()
                    visibility = View.GONE
                    alpha = 1f
                    translationY = 0f
                }
            }
        } else if (outgoing != null) {
            if (!isNavHidden) {
                // Show navigation when sheet closes (only if nav isn't explicitly hidden)
                (fragment.activity as? MainActivity)?.binding?.bottomNavigationView?.apply {
                    animate().cancel()
                    visibility = View.VISIBLE
                    alpha = 1f
                    translationY = 0f
                }
            }
        }

        // 1. Handle Outgoing View
        outgoing?.let { view ->
            if (getBindingRootForType(currentActiveSheet) == view) {
                currentActiveSheet = SheetType.NONE
            }
            view.animate().cancel() // Stop any ongoing animation
            val animator = view.animate().setDuration(duration).setInterpolator(interpolator)
            when (transition) {
                BottomSheetTransition.SLIDE_OUT_LEFT,
                BottomSheetTransition.SLIDE_IN_RIGHT -> animator.translationX(-screenWidth).alpha(0f)

                BottomSheetTransition.SLIDE_OUT_RIGHT,
                BottomSheetTransition.SLIDE_IN_LEFT -> animator.translationX(screenWidth).alpha(0f)

                BottomSheetTransition.SLIDE_DOWN -> animator.translationY(screenHeight).alpha(0f)
                else -> animator.alpha(0f)
            }
            animator.withEndAction {
                view.visibility = View.GONE
                view.translationX = 0f
                view.translationY = 0f
                view.alpha = 1f
                // If there's no incoming view, trigger onEnd here
                if (incoming == null) onEnd?.invoke()
            }.start()
        }

        // 2. Handle Incoming View
        incoming?.let { view ->
            view.animate().cancel() // Stop any ongoing animation
            view.visibility = View.VISIBLE
            view.alpha = 0f

            when (transition) {
                BottomSheetTransition.SLIDE_IN_RIGHT -> {
                    view.translationX = screenWidth
                    view.translationY = 0f
                }
                BottomSheetTransition.SLIDE_IN_LEFT -> {
                    view.translationX = -screenWidth
                    view.translationY = 0f
                }
                BottomSheetTransition.SLIDE_UP -> {
                    view.translationX = 0f
                    view.translationY = screenHeight
                }
                else -> {
                    view.translationX = 0f
                    view.translationY = 0f
                }
            }

            view.animate()
                .alpha(1f)
                .translationX(0f)
                .translationY(0f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withEndAction { onEnd?.invoke() }
                .start()
        } ?: run {
            // No incoming view, if there was also no outgoing view, call onEnd immediately
            if (outgoing == null) onEnd?.invoke()
        }
    }

    /**
     * Centralized way to show a sheet and manage navigation.
     */
    fun showSheet(
        type: SheetType,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP,
        isRestoring: Boolean = false,
        onSetup: (() -> Unit)? = null
    ) {
        if (currentActiveSheet == type && type != SheetType.NONE) {
            onSetup?.invoke() // Just re-run setup if already shown
            return
        }

        val outgoing = currentActiveSheet
        // We don't push to stack if we're NONE or if we're restoring
        // Instead of pushing here, we expect the CALLEE to have pushed the PREVIOUS state if needed,
        // OR we can capture it here if we had a way to know the current restoration.
        // Actually, let's have showSheet handle the push of the CURRENT state before it's replaced.

        currentActiveSheet = type
        val outgoingView = getBindingRootForType(outgoing)
        val incomingView = getBindingRootForType(type)

        onSetup?.invoke()
        animateSheetTransition(outgoingView, incomingView, transition)
    }

    /**
     * Pushes a restoration point to the stack. 
     * Call this before showSheet if you want to go back to the current state.
     */
    fun pushBackStack(type: SheetType, restoration: () -> Unit) {
        // Avoid duplicate consecutive types in stack
        if (sheetNavigationStack.lastOrNull()?.first == type) return
        sheetNavigationStack.addLast(type to restoration)
    }

    fun popSheet(transition: BottomSheetTransition = BottomSheetTransition.SLIDE_DOWN, onEnd: (() -> Unit)? = null) {
        if (sheetNavigationStack.isEmpty()) {
            hideAllSheets(transition, onEnd)
            return
        }
        val (type, restoration) = sheetNavigationStack.removeLast()
        // Run the restoration lambda which will call showSheet(..., isRestoring = true)
        restoration.invoke()
    }

    fun clearBackStack() {
        sheetNavigationStack.clear()
    }

    fun hideAllSheets(transition: BottomSheetTransition = BottomSheetTransition.SLIDE_DOWN, onEnd: (() -> Unit)? = null) {
        val outgoingView = getBindingRootForType(currentActiveSheet)
        sheetNavigationStack.clear()
        currentActiveSheet = SheetType.NONE
        animateSheetTransition(outgoingView, null, transition, onEnd)
    }

    fun getBindingRootForType(type: SheetType): View? = when (type) {
        SheetType.COLLECT_POINT -> fragment.binding.bottomSheetCollectPoint.root
        SheetType.LINE_SEGMENT -> fragment.binding.bottomSheetLineSegment.root
        SheetType.EDIT_LINE -> fragment.binding.bottomSheetEditLine.root
        SheetType.EDIT_POINT -> fragment.binding.bottomSheetEditPoint.root
        SheetType.NEW_LINE -> fragment.binding.bottomSheetNewLine.root
        SheetType.NEW_POINT -> fragment.binding.bottomSheetNewPoint.root
        SheetType.SELECT_CODE -> fragment.binding.bottomSheetSelectCode.root
        SheetType.OBJECT_LIST -> fragment.binding.bottomSheetObjectList.root
        SheetType.STAKEOUT -> fragment.binding.stakeoutBottomSheet.root
        // Need to check if confirm dialog is in the layout - it was imported but might be a separate dialog
        // For now, let's stick to these embedded ones.
        else -> null
    }
    private fun updateLineGeometry(ls: ClickablePolylineOverlay, points: List<LabeledPoint>) {
        ls.pointCount = points.size
        val gps = points.map { it.geoPoint }
        var dist = 0.0
        for (i in 0 until gps.size - 1) dist += gps[i].distanceToAsDouble(gps[i + 1])
        if (ls.isClosed) dist += gps.last().distanceToAsDouble(gps.first())
        ls.length = dist
        ls.setPoints(if (ls.isClosed && gps.isNotEmpty() && gps.first() != gps.last()) gps + gps.first() else gps)
        ls.labeledPoints = points

        // Ensure points on map are updated effectively
        updateMarkersForZoom(forceRefresh = true)
        fragment.binding.mapView.invalidate()
    }

    private val PREFS_NAME = "survedge_prefs"
    private val KEY_CUSTOM_CODES = "custom_codes"

    // Optimization: Cache for markers to prevent expensive recreation
    private data class PointMarkerCache(
        val pointMarker: Marker,
        var labelMarker: Marker? = null,
        var lastIsSelected: Boolean? = null,
        var lastCodeId: String? = null,
        var isLabelShown: Boolean = false
    )
    private val pointMarkersCache = mutableMapOf<String, PointMarkerCache>()

    // Local storage of line entities to check for closure status
    private var collectedLines = listOf<LineWithPoints>()

    fun updateLinesFromDatabase(lines: List<LineWithPoints>) {
        collectedLines = lines
        // If points are already loaded, we might need to refresh polylines to apply "closed" status
        if (fragment.collectedLabeledPoints.isNotEmpty()) {
            reconstructPolylines()
        }
    }

    fun updatePointsFromDatabase(points: List<LabeledPoint>) {
        fragment.collectedLabeledPoints.clear()
        fragment.collectedLabeledPoints.addAll(points)

        // Determine prefix mode based on the LAST collected point's type
        val regex = Regex("^([a-zA-Z][a-zA-Z ]*)(\\d+)$")
        val numRegex = Regex("^\\d+$")
        val alphaOnlyRegex = Regex("^[a-zA-Z][a-zA-Z ]*$")

        if (points.isNotEmpty()) {
            val lastPoint = points.last()
            val prefixMatch = regex.find(lastPoint.id)
            when {
                // Last point is prefixed like "A5" -> continue alphabetic
                prefixMatch != null -> {
                    val prefix = prefixMatch.groupValues[1]
                    fragment.pointIdPrefix = prefix
                }
                // Last point is pure alphabet like "A" -> continue with prefix numbering from 1
                alphaOnlyRegex.matches(lastPoint.id) -> {
                    val prefix = lastPoint.id
                    fragment.pointIdPrefix = prefix
                }
                // Last point is numeric like "13" -> continue numeric
                numRegex.matches(lastPoint.id) -> {
                    fragment.pointIdPrefix = null
                }
                // Fallback: unknown format
                else -> {
                    fragment.pointIdPrefix = null
                }
            }
        }
        refreshNextPointIdForCollectSheet()


        // Optimized: Incremental Update instead of Clear + Recreate
        updateMarkersForZoom()

        // Reconstruct all polylines from the database points
        reconstructPolylines()

        // Fix for Line Lag: Update line overlays after data is synced
        if (fragment.selectedPointIndicatorType == IndicatorType.LINE && fragment.currentLineCodeId != null) {
            redrawPolyline()
            updateLiveTrackingLine()
        }

        // Fix: Update line menu visibility when collect point sheet is open
        // This ensures btn_line_menu shows after 2 points are collected (not 3)
        if (fragment.binding.bottomSheetCollectPoint.root.visibility == View.VISIBLE) {
            updateCollectSheetLineMenuUI()
        }

    }

    /**
     * Updates the line menu button visibility and related UI elements in the collect point bottom sheet.
     * This should be called whenever the point list changes while the sheet is visible.
     */
    fun updateCollectSheetLineMenuUI() {
        val sheetBinding = fragment.binding.bottomSheetCollectPoint
        val pts =
            if (fragment.selectedPointIndicatorType == IndicatorType.LINE && fragment.currentLineCodeId != null) {
                getAllPointsInCurrentLineSegment()
            } else {
                emptyList()
            }
        sheetBinding.llCloseShape.visibility = if (pts.size >= 3) View.VISIBLE else View.GONE
        sheetBinding.llFromOtherSide.isEnabled = pts.size >= 2
        sheetBinding.llFromOtherSide.alpha = if (pts.size >= 2) 1f else 0.5f
        updateLineMenuVisibility(sheetBinding.btnLineMenu, fragment.selectedPointIndicatorType)
    }

    fun reconstructPolylines() {
        // Clear existing completed line overlays from the map
        fragment.completedLineOverlays.forEach {
            OsmdroidPolylineHelper.removePolyline(fragment.binding.mapView, it)
        }
        fragment.completedLineOverlays.clear()

        // Build a lookup map from point ID to LabeledPoint for quick resolution
        val pointLookup = fragment.collectedLabeledPoints.associateBy { it.id }

        // Track which line codes we've already handled from DB
        val handledCodes = mutableSetOf<String>()

        // 1. Recreate polylines from database lines (preserves user-arranged order via orderIndex)
        collectedLines.forEach { lineWithPoints ->
            val codeId = lineWithPoints.line.id
            if (codeId.isBlank()) return@forEach
            handledCodes.add(codeId)

            // Resolve PointEntity -> LabeledPoint using the lookup, preserving DB order
            val orderedPoints = lineWithPoints.points.mapNotNull { pointEntity ->
                pointLookup[pointEntity.id]
            }

            if (orderedPoints.size >= 2) {
                val geoPoints = orderedPoints.map { it.geoPoint }
                val isClosed = lineWithPoints.line.isClosed

                var length = 0.0
                for (i in 0 until geoPoints.size - 1) {
                    length += geoPoints[i].distanceToAsDouble(geoPoints[i + 1])
                }
                if (isClosed) {
                    length += geoPoints.last().distanceToAsDouble(geoPoints.first())
                }

                val clickablePolyline = ClickablePolylineOverlay(
                    geoPoints,
                    ContextCompat.getColor(fragment.requireContext(), R.color.slate_gray_light),
                    6f,
                    closed = isClosed
                ).apply {
                    this.codeId = codeId
                    this.featureCode = lineWithPoints.line.code.ifEmpty {
                        codeId.filter { it.isLetter() }.ifEmpty { "L" }
                    }
                    this.pointCount = orderedPoints.size
                    this.length = length
                    this.labeledPoints = ArrayList(orderedPoints)
                    this.isClosed = isClosed
                    setOnClickListener { handleLineSegmentClick(this) }
                }

                addPolylineBelowMarkers(clickablePolyline)
                fragment.completedLineOverlays.add(clickablePolyline)
            }
        }

        // 2. Fallback: handle any line-coded points not yet in the database (e.g., live tracking)
        val linesByCode = mutableMapOf<String, MutableList<LabeledPoint>>()
        fragment.collectedLabeledPoints.forEach { point ->
            if (isLineCodeFromCodeId(point.codeId) && point.codeId !in handledCodes) {
                linesByCode.getOrPut(point.codeId) { mutableListOf() }.add(point)
            }
        }

        linesByCode.forEach { (codeId, points) ->
            if (points.size >= 2) {
                val sortedPoints = points.sortedBy { it.ts }
                val geoPoints = sortedPoints.map { it.geoPoint }

                var length = 0.0
                for (i in 0 until geoPoints.size - 1) {
                    length += geoPoints[i].distanceToAsDouble(geoPoints[i + 1])
                }

                val clickablePolyline = ClickablePolylineOverlay(
                    geoPoints,
                    ContextCompat.getColor(fragment.requireContext(), R.color.slate_gray_light),
                    6f,
                    closed = false
                ).apply {
                    this.codeId = codeId
                    this.featureCode = codeId.filter { it.isLetter() }.ifEmpty { "L" }
                    this.pointCount = sortedPoints.size
                    this.length = length
                    this.labeledPoints = ArrayList(sortedPoints)
                    this.isClosed = false
                    setOnClickListener { handleLineSegmentClick(this) }
                }

                addPolylineBelowMarkers(clickablePolyline)
                fragment.completedLineOverlays.add(clickablePolyline)
            }
        }

        ensurePointClickHandlerAtEnd()
        fragment.binding.mapView.invalidate()
    }

    // ---------------------------------------------------------------------
    // Edge-to-edge inset handling
    // ---------------------------------------------------------------------
    private var bottomNavOffset: Float = 0f
    private var statusBarHeight: Int = 0

    fun setupEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(fragment.binding.root) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusBarHeight = systemBars.top
            val navBarHeight = systemBars.bottom
            val density = fragment.resources.displayMetrics.density

            // If keyboard is visible and a sheet is open, don't update nav visibility
            val isSheetOpen = fragment.binding.bottomSheetNewLine.root.visibility == View.VISIBLE ||
                    fragment.binding.bottomSheetEditLine.root.visibility == View.VISIBLE ||
                    fragment.binding.bottomSheetCollectPoint.root.visibility == View.VISIBLE ||
                    fragment.binding.bottomSheetNewPoint.root.visibility == View.VISIBLE

            if ((imeVisible || isNavHidden) && isSheetOpen) {
                // Keyboard is showing with a sheet open, or nav was explicitly hidden - don't show nav
                return@setOnApplyWindowInsetsListener insets
            }

            // Calculate total offset needed to clear Bottom Nav
            // Use actual BNV height if measured, else standard 80dp (M3) + system bars
            val navView = (fragment.activity as? MainActivity)?.binding?.bottomNavigationView
            val bnvHeight = if (navView != null && navView.height > 0) {
                navView.height.toFloat()
            } else {
                80f * density // Safer default for modern nav bars
            }

            // The constraintLayout of the map is FULL SCREEN (bottom to parent).
            // Bottom Sheet is also FULL SCREEN (bottom to parent).
            // When Nav Bar is VISIBLE, it sits ON TOP of the bottom content.
            // So we need to PUSH UP the bottom sheet by (Nav Height + System Bottom Inset).
            // HOWEVER, BNV usually handles its own system inset padding if fitSystemWindows is clear.
            // If BNV sits at the very bottom, its height INCLUDES the system nav bar area.
            // So we typically just need the BNV height.
            // Let's use max of (BNV Height, System Nav Height + 56dp) to be safe.

            bottomNavOffset = bnvHeight.coerceAtLeast(navBarHeight + (56f * density))

            fragment.binding.imgBack.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = statusBarHeight + fragment.resources.getDimensionPixelSize(
                    fragment.resources.getIdentifier(
                        "_10sdp",
                        "dimen",
                        fragment.requireContext().packageName
                    )
                )
            }

            fragment.binding.imgMenu.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = statusBarHeight + fragment.resources.getDimensionPixelSize(
                    fragment.resources.getIdentifier(
                        "_10sdp",
                        "dimen",
                        fragment.requireContext().packageName
                    )
                )
            }

            // Adjust Line Segment Sheet Top Margin to clear top buttons (approx _60sdp + status bar)
            val topUiClearance = fragment.resources.getDimensionPixelSize(
                fragment.resources.getIdentifier(
                    "_70sdp",
                    "dimen",
                    fragment.requireContext().packageName
                )
            )
            fragment.binding.bottomSheetLineSegment.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = statusBarHeight + topUiClearance
            }

            // Apply this offset to the bottom sheet if it's supposed to be visible above the nav bar
            // But NOT if a sheet is actively open (which would have already called hideBottomNavigation)
            if (!isSheetOpen) {
                fragment.binding.bottomSheetLineSegment.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    bottomMargin = bottomNavOffset.toInt()
                }
            }

            // Initial State: If BNV is visible (default), push btnCollect up
            // We use post to ensure view layout is ready/checked
            fragment.binding.root.post {
                val nav = (fragment.activity as? MainActivity)?.binding?.bottomNavigationView
                if (nav != null && nav.visibility == View.VISIBLE && !isSheetOpen) {
                    fragment.binding.btnCollect.translationY = -bottomNavOffset
                }
            }

            insets
        }
    }

    // ---------------------------------------------------------------------
    // Helper Methods
    // ---------------------------------------------------------------------

    fun formatTimestamp(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(timestamp)

            val outputFormat = SimpleDateFormat("dd-MM-yyyy h:mm a", Locale.US)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            timestamp
        }
    }

    fun updatePointTypeIndicator(indicatorView: ImageView, indicatorType: IndicatorType) {
        when (indicatorType) {
            IndicatorType.POINT -> {
                indicatorView.setImageResource(R.drawable.point_type_dot)
                indicatorView.colorFilter = null
            }

            IndicatorType.LINE -> {
                indicatorView.setImageResource(R.drawable.point_type_line)
                indicatorView.colorFilter = null
            }
        }
    }

    fun updateLineMenuVisibility(menuButton: ImageButton, indicatorType: IndicatorType) {
        val pointCount =
            if (indicatorType == IndicatorType.LINE && fragment.currentLineCodeId != null) {
                getAllPointsInCurrentLineSegment().size
            } else {
                0
            }

        menuButton.visibility = if (pointCount >= 2) View.VISIBLE else View.GONE
    }

    fun saveCustomCode(codeName: String, codeDesc: String, type: String) {
        val prefs = fragment.requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingCodesJson = prefs.getString(KEY_CUSTOM_CODES, "[]")
        val codesArray = JSONArray(existingCodesJson)

        val newCode = JSONObject().apply {
            put("name", codeName)
            put("description", codeDesc)
            put("type", type)
        }
        codesArray.put(newCode)
        prefs.edit().putString(KEY_CUSTOM_CODES, codesArray.toString()).apply()
    }

    fun getCustomCodes(): List<CodeItem> {
        val prefs = fragment.requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val codesJson = prefs.getString(KEY_CUSTOM_CODES, "[]") ?: "[]"
        val codesArray = JSONArray(codesJson)

        val customCodes = mutableListOf<CodeItem>()
        for (i in 0 until codesArray.length()) {
            val codeObj = codesArray.getJSONObject(i)
            val name = codeObj.getString("name")
            val description = codeObj.optString("description", "")
            val type = codeObj.getString("type")

            val indicatorType = if (type.equals(
                    "Line",
                    ignoreCase = true
                )
            ) IndicatorType.LINE else IndicatorType.POINT
            customCodes.add(CodeItem(name, description, indicatorType))
        }
        return customCodes
    }

    fun getCodeDescription(codeId: String): String {
        val defaultCodes = listOf(
            CodeItem("", "No code", IndicatorType.POINT),
            CodeItem("P", "Standard Point", IndicatorType.POINT),
            CodeItem("L", "Standard line", IndicatorType.LINE)
        )

        defaultCodes.find { it.abbreviation.equals(codeId, ignoreCase = true) }
            ?.let { return it.description }
        getCustomCodes().find { it.abbreviation.equals(codeId, ignoreCase = true) }
            ?.let { return it.description }
        return ""
    }

    fun isLineCodeFromCodeId(codeId: String): Boolean {
        if (codeId.isEmpty()) return false
        val lineCodes = listOf("L", "BLDG", "RCL", "LINE", "ROAD", "WALL", "FENCE")
        val normalized = normalizeLineCode(codeId) ?: return false
        val base = normalized.first.uppercase()
        if (lineCodes.contains(base)) return true

        return getCustomCodes().any {
            val customBase = normalizeLineCode(it.abbreviation)?.first ?: it.abbreviation
            customBase.equals(base, ignoreCase = true) && it.indicatorType == IndicatorType.LINE
        }
    }

    fun getConsecutiveLineCodePoints(): List<LabeledPoint> {
        val consecutiveLinePoints = mutableListOf<LabeledPoint>()
        val startIndex = fragment.lineSegmentStartIndex.coerceAtLeast(0)

        if (fragment.currentLineCodeId != null) {
            for (i in startIndex until fragment.collectedLabeledPoints.size) {
                val point = fragment.collectedLabeledPoints[i]
                if (point.codeId == fragment.currentLineCodeId) consecutiveLinePoints.add(point)
            }
        } else {
            for (i in fragment.collectedLabeledPoints.size - 1 downTo startIndex) {
                val point = fragment.collectedLabeledPoints[i]
                if (isLineCodeFromCodeId(point.codeId)) consecutiveLinePoints.add(
                    0,
                    point
                ) else break
            }
        }
        return consecutiveLinePoints
    }

    fun getAllPointsInCurrentLineSegment(): List<LabeledPoint> {
        val targetCodeId = fragment.currentLineCodeId ?: return getConsecutiveLineCodePoints()
        return fragment.collectedLabeledPoints.filter { it.codeId == targetCodeId }
    }

    fun normalizeLineCode(codeId: String): Pair<String, Int?>? {
        val trimmed = codeId.trim()
        if (trimmed.isEmpty()) return null

        // Match non-digits at the start, followed by digits at the end
        val regex = Regex("^(\\D+)(\\d+)?$")
        val match = regex.find(trimmed)

        if (match != null) {
            val base = match.groupValues[1]
            val number = match.groupValues.getOrNull(2)?.toIntOrNull()
            return base to number
        }

        // Fallback: whole thing as base if it's purely numeric or weird
        return trimmed to null
    }

    fun trackLineCodeUsage(codeId: String) {
        val (base, number) = normalizeLineCode(codeId) ?: return
        val currentMax = fragment.lineCodeSequenceCounters[base] ?: 0
        if (number != null && number > currentMax) {
            fragment.lineCodeSequenceCounters[base] = number
        }
    }

    private fun nextLineCode(currentCodeId: String): String {
        // Extract the alpha part (base) to find the next sequence
        val (base, _) = normalizeLineCode(currentCodeId) ?: return currentCodeId

        // 1. Check if the base code itself is available (e.g. "L")
        if (!isLineCodeUsed(base)) {
            return base
        }

        // 2. Iterate 1..N to find first gap
        var i = 1
        while (true) {
            val candidate = "$base$i"
            if (!isLineCodeUsed(candidate)) {
                return candidate
            }
            i++
            // Safety break to prevent infinite loops in extreme cases (unlikely)
            if (i > 9999) return "$base$i"
        }
    }

    private fun isLineCodeUsed(codeId: String): Boolean {
        if (fragment.collectedLabeledPoints.any { it.codeId == codeId }) return true
        if (fragment.completedLineOverlays.any { (it as? ClickablePolylineOverlay)?.codeId == codeId }) return true
        return false
    }

    // ---------------------------------------------------------------------
    // Manual Line Creation Logic
    // ---------------------------------------------------------------------

    fun toggleNewLineMode(transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP) {
        if (fragment.isCreatingNewLine) {
            hideNewLineBottomSheet(transition = transition)
        } else {
            showNewLineBottomSheet(transition = transition)
        }
    }
    private fun applyFullScreenConstraints(view: View, isRootView: Boolean = false) {
        val params = view.layoutParams as ConstraintLayout.LayoutParams

        if (params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            params.height = 0
            params.verticalBias = 0.5f // Default
            params.constrainedHeight = false
        } else {
            // For wrap_content sheets, anchor them to the bottom but constrain their top
            params.verticalBias = 1.0f
            params.constrainedHeight = true
        }

        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID

        val baseMargin = try {
            fragment.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._10sdp)
        } catch (e: Exception) {
            // Fallback to a reasonable dp value if sdp is not directly accessible via R
            (25 * fragment.resources.displayMetrics.density).toInt()
        }

        // Ensure margin is at least statusBarHeight + baseMargin
        params.topMargin = statusBarHeight + baseMargin

        // No bottom margin — sheets overlap the bottom nav bar
        params.bottomMargin = 0

        view.layoutParams = params
        view.requestLayout()
    }

    private fun resetLineSegmentSheetToDefaultState() {
        val sheetBinding = fragment.binding.bottomSheetLineSegment
        val params = sheetBinding.root.layoutParams as? ConstraintLayout.LayoutParams ?: return

        val topUiClearance = fragment.resources.getDimensionPixelSize(
            fragment.resources.getIdentifier(
                "_70sdp",
                "dimen",
                fragment.requireContext().packageName
            )
        )

        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
        params.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        params.verticalBias = 1.0f
        params.constrainedHeight = true
        params.topMargin = statusBarHeight + topUiClearance
        params.bottomMargin = bottomNavOffset.toInt()

        sheetBinding.root.layoutParams = params
        sheetBinding.root.translationX = 0f
        sheetBinding.root.translationY = 0f
        sheetBinding.root.alpha = 1f

        // Always reopen with collapsed/default content state.
        sheetBinding.nsvInfo.visibility = View.GONE
        sheetBinding.llPointLineInfo.visibility = View.GONE
        sheetBinding.clLineMenu.visibility = View.GONE
        sheetBinding.root.requestLayout()
    }

    fun showNewLineBottomSheet(
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP,
        isRestoring: Boolean = false
    ) = hideMenu {
        showSheet(SheetType.NEW_LINE, transition, isRestoring) {
        // Ensure any other competing modes are closed/cleared
        if (!isRestoring && (fragment.binding.bottomSheetEditLine.root.visibility == View.VISIBLE || fragment.pendingEditLineSegment != null)) {
            // Force hide edit sheet and clear its state to prevent "ghost line" protection
            fragment.binding.bottomSheetEditLine.root.visibility = View.GONE

            // CRITICAL FIX: Restore the original line and remove the temporary edit overlay
            val original = fragment.pendingEditLineSegment
            val temporary = fragment.highlightedLineOverlay

            if (temporary != null && temporary != original) {
                // The highlighted overlay is a temporary edit visual (orange), remove it
                if (fragment.binding.mapView.overlays.contains(temporary)) {
                    fragment.binding.mapView.overlays.remove(temporary)
                }
            }

            if (original != null) {
                original.unhighlight() // Ensure it returns to normal color (slate gray)
                if (!fragment.binding.mapView.overlays.contains(original)) {
                    // Restore the original line to the map
                    fragment.binding.mapView.overlays.add(original)
                }
            }

            fragment.pendingEditLineSegment = null
            fragment.isSelectingPointForEditLine = false
            fragment.highlightedLineOverlay = null
            fragment.binding.mapView.invalidate()
        }

        if (!isRestoring) {
            fragment.isCreatingNewLine = true
            fragment.newLinePoints.clear()
            fragment.newLineOverlay = null
            fragment.newLineCodeId = nextLineCode("L") // Default or next available
            fragment.isNewLineClosed = false
        }

        hideBottomNavigation {
            val sheetBinding = fragment.binding.bottomSheetNewLine
            sheetBinding.root.elevation = 24f * fragment.resources.displayMetrics.density
            sheetBinding.root.translationZ = 24f * fragment.resources.displayMetrics.density

            // Reset height to WRAP_CONTENT (like collect point bottom sheet)
            sheetBinding.root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            sheetBinding.root.requestLayout()

            sheetBinding.tvCodeId.text = fragment.newLineCodeId
            sheetBinding.cbClosedLine.isChecked = false
            sheetBinding.btnSaveLine.isEnabled = false
            sheetBinding.tvPointsCount.text = "0 Points • 0.0 m"
            sheetBinding.rvPoints.visibility = View.GONE
            sheetBinding.tvEmptyState.visibility = View.VISIBLE

            sheetBinding.btnCloseNewLine.setOnClickListener { hideNewLineBottomSheet() }

            // Code selection click listener (matches edit line behavior)
            sheetBinding.llCodeValue.setOnClickListener {
                // Keep new line sheet visible underneath; show select code above it
                showSelectCodeBottomSheet(
                    null,
                    onlyPoints = false,
                    onlyLines = true,
                    transition = BottomSheetTransition.SLIDE_UP,
                    showNavOnCloseOverride = false
                ) { codeId, indicatorType ->
                    if (indicatorType != IndicatorType.LINE) {
                        return@showSelectCodeBottomSheet
                    }
                    fragment.newLineCodeId = codeId
                    sheetBinding.tvCodeId.text = fragment.newLineCodeId
                    updateNewLineUI()
                }
            }

            sheetBinding.cbClosedLine.setOnCheckedChangeListener { _, isChecked ->
                fragment.isNewLineClosed = isChecked
                updateNewLineUI()
                updateNewLineOverlay()
            }

            sheetBinding.tvAddPointFromList.setOnClickListener {
            // Keep new line sheet visible underneath; show object list above it
            showObjectListBottomSheetInternalForNewLine(BottomSheetTransition.SLIDE_UP)
        }

            sheetBinding.btnSaveLine.setOnClickListener {
                saveNewLine()
            }

            // Setup RecyclerView for drag/drop
            val initialItems = fragment.newLinePoints.map { point ->
                ObjectListItem(
                    id = point.id,
                    codeId = point.codeId,
                    dateTime = formatTimestamp(point.ts),
                    indicatorType = IndicatorType.POINT
                )
            }.toMutableList()

            var currentItemTouchHelper: ItemTouchHelper? = null
            val adapter = ObjectListAdapter(initialItems, isOrderable = true, onDelete = { item ->
                val point = fragment.newLinePoints.find { it.id == item.id }
                if (point != null) removePointFromNewLine(point)
            }, onItemClick = { item ->
                fragment.collectedLabeledPoints.find { it.id == item.id }?.let {
                    animateToLocationWithZoom(
                        it.geoPoint,
                        fragment.binding.mapView.zoomLevelDouble.coerceAtLeast(18.0)
                    )
                }
                Unit
            }, onDragStart = { vh -> currentItemTouchHelper?.startDrag(vh) })

            sheetBinding.rvPoints.layoutManager = LinearLayoutManager(fragment.requireContext())
            sheetBinding.rvPoints.adapter = adapter

            currentItemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
            ) {
                override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        viewHolder?.itemView?.let { v ->
                            v.animate().cancel()
                            v.elevation = 24f * fragment.resources.displayMetrics.density
                            v.translationZ = 24f * fragment.resources.displayMetrics.density
                            v.setBackgroundResource(R.drawable.bg_edit_point_item)
                        }
                    }
                }

                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.let { v ->
                        v.animate().cancel()
                        v.elevation = 0f
                        v.translationZ = 0f
                    }
                    updateNewLineUI()
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val fromPos = viewHolder.adapterPosition
                    val toPos = target.adapterPosition
                    if (fromPos == toPos) return false

                    java.util.Collections.swap(fragment.newLinePoints, fromPos, toPos)
                    (recyclerView.adapter as? ObjectListAdapter)?.moveItem(fromPos, toPos)

                    // Update only specific overlay without re-reading the whole list
                    updateNewLineOverlay()

                    // Light UI update: just refresh the count label, don't recalculate distance during active drag
                    val count = fragment.newLinePoints.size
                    sheetBinding.tvPointsCount.text = "$count Points"

                    return true
                }

                override fun onChildDraw(
                    c: Canvas,
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    dX: Float,
                    dY: Float,
                    actionState: Int,
                    isCurrentlyActive: Boolean
                ) {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                        val itemView = viewHolder.itemView
                        val recyclerView = recyclerView
                        val itemHeight = itemView.height

                        val recyclerTop = recyclerView.top
                        val recyclerBottom = recyclerView.bottom
                        val itemTop = itemView.top + dY.toInt()
                        val itemBottom = itemView.bottom + dY.toInt()

                        val scrollThreshold = itemHeight
                        val maxScrollSpeed = 50

                        when {
                            itemTop <= recyclerTop + scrollThreshold -> {
                                val distanceFromTop = (itemTop - recyclerTop).toFloat()
                                val scrollSpeed = if (distanceFromTop > 0) {
                                    (maxScrollSpeed * (1 - (distanceFromTop / scrollThreshold))).toInt()
                                } else {
                                    maxScrollSpeed
                                }
                                recyclerView.scrollBy(0, -scrollSpeed)
                            }
                            itemBottom >= recyclerBottom - scrollThreshold -> {
                                val distanceFromBottom = (recyclerBottom - itemBottom).toFloat()
                                val scrollSpeed = if (distanceFromBottom > 0) {
                                    (maxScrollSpeed * (1 - (distanceFromBottom / scrollThreshold))).toInt()
                                } else {
                                    maxScrollSpeed
                                }
                                recyclerView.scrollBy(0, scrollSpeed)
                            }
                        }
                    }
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
                override fun isLongPressDragEnabled() = false
            })
            currentItemTouchHelper.attachToRecyclerView(sheetBinding.rvPoints)

            // Keyboard Handling: Keep bottom nav hidden while sheet is open
            sheetBinding.root.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (!sheetBinding.root.isAttachedToWindow) {
                        sheetBinding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        return
                    }
                    if (sheetBinding.root.visibility != View.VISIBLE) return

                    // Ensure bottom nav stays hidden while sheet is visible
                    hideBottomNavigation()
                }
            })

            setupSwipeToDismiss(sheetBinding.root) { hideNewLineBottomSheet() }
            }
        }
    }

    fun showObjectListBottomSheetInternalForNewLine(transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP) {
        pushBackStack(SheetType.NEW_LINE) {
            showNewLineBottomSheet(BottomSheetTransition.SLIDE_DOWN, isRestoring = true)
            // Update UI after showing in case points were added
            updateNewLineUI()
        }
        showObjectListBottomSheet(transition = transition, showAddButton = false, showTitle = true, sheetTitle = "Select Point")
    }

    fun showObjectListBottomSheetInternalForEditLine(transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP) {
        pushBackStack(SheetType.EDIT_LINE) {
            val ls = fragment.pendingEditLineSegment ?: return@pushBackStack
            showEditLineBottomSheet(ls, BottomSheetTransition.SLIDE_DOWN, isRestoring = true)
        }
        showObjectListBottomSheet(transition = transition, showAddButton = false, showTitle = true, sheetTitle = "Select Point")
    }

    fun hideNewLineBottomSheet(
        showNav: Boolean = true,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_DOWN,
        onHidden: (() -> Unit)? = null
    ) {
        val sheetBinding = fragment.binding.bottomSheetNewLine
        adjustMapsButtonsForBottomSheet(closingView = sheetBinding.root)

        val afterAnimation: () -> Unit = {
            fragment.isCreatingNewLine = false
            // Revert code IDs for discarded points ONLY if not saved
            if (!fragment.isNewLineSaved) {
                fragment.newLinePoints.forEach { pt ->
                    val idx = fragment.collectedLabeledPoints.indexOfFirst { it.id == pt.id }
                    if (idx >= 0) {
                        // Clear the temporary line code we gave them
                        fragment.collectedLabeledPoints[idx] =
                            fragment.collectedLabeledPoints[idx].copy(codeId = "")
                    }
                }
            }
            // Reset the saved flag for next time
            fragment.isNewLineSaved = false

            fragment.newLinePoints.clear()
            fragment.newLineOverlay?.let {
                OsmdroidPolylineHelper.removePolyline(fragment.binding.mapView, it)
            }
            fragment.newLineOverlay = null

            fragment.closingSegmentOverlay?.let {
                if (fragment.binding.mapView.overlays.contains(it)) {
                    fragment.binding.mapView.overlays.remove(it)
                }
            }
            fragment.closingSegmentOverlay = null

            // Force refresh markers to show reverted state
            updateMarkersForZoom(forceRefresh = true)
            fragment.binding.mapView.invalidate()

            if (showNav) {
                restoreStateAfterClosingInfoSheet()
                showBottomNavigation(force = true)
            }
            onHidden?.invoke()
        }

        popSheet(transition, afterAnimation)
    }

    fun transferPointToCurrentLine(point: LabeledPoint): String? {
        // Iterate through all completed lines to find if this point belongs to one
        // We must avoid the currently edited line (fragment.pendingEditLineSegment) if relevant
        val iterator = fragment.completedLineOverlays.iterator()
        var inheritedCodeId: String? = null

        while (iterator.hasNext()) {
            val line = iterator.next()
            if (line is ClickablePolylineOverlay) {
                if (line == fragment.pendingEditLineSegment) continue // Skip the one we are editing

                // Check if point is in this line
                val index = line.labeledPoints.indexOfFirst { it.id == point.id }
                if (index != -1) {
                    // Found it. Remove it.
                    // safely remove
                    if (line.labeledPoints is MutableList) {
                        (line.labeledPoints as MutableList).removeAt(index)
                    } else {
                        val mutable = line.labeledPoints.toMutableList()
                        mutable.removeAt(index)
                        line.labeledPoints = ArrayList(mutable)
                    }

                    // If line has < 2 points, delete it
                    if (line.labeledPoints.size < 2) {
                        inheritedCodeId = line.codeId // Capture code before deletion

                        // Clean up any potential visual artifacts (ghost lines)
                        if (fragment.highlightedLineOverlay == line) {
                            fragment.highlightedLineOverlay = null
                        }
                        if (fragment.pendingEditLineSegment == line) {
                            fragment.pendingEditLineSegment = null
                        }

                        // Convert remaining "orphan" points to No Code
                        line.labeledPoints.forEach { orphan ->
                            val mainIndex =
                                fragment.collectedLabeledPoints.indexOfFirst { it.id == orphan.id }
                            if (mainIndex != -1) {
                                val updatedPoint =
                                    fragment.collectedLabeledPoints[mainIndex].copy(codeId = "")
                                fragment.collectedLabeledPoints[mainIndex] = updatedPoint

                                // UPDATE DATABASE: Save the point with cleared code
                                viewModel.currentProjectId.value?.let { projectId ->
                                    val pointEntity = updatedPoint.toPointEntity(projectId)
                                    viewModel.savePoint(pointEntity)
                                }
                            }
                        }

                        // UPDATE DATABASE: Delete the dissolved line
                        val lineEntity = collectedLines.find { it.line.id == line.codeId }?.line
                        if (lineEntity != null) {
                            viewModel.deleteLine(lineEntity)
                        }

                        OsmdroidPolylineHelper.removePolyline(fragment.binding.mapView, line)
                        iterator.remove()
                        continue // Move to next line check
                    } else {
                        // Correctly update the line's visual points on the map
                        updateLineGeometry(line, line.labeledPoints)
                    }
                    fragment.binding.mapView.invalidate()
                    // Continue checking other lines in case of duplicates
                }
            }
        }
        return inheritedCodeId
    }

    fun addPointToNewLine(point: LabeledPoint) {
        // Robust check: Allow if either creating new line OR sheet is visible
        if (!fragment.isCreatingNewLine) return

        // Prevent adding the same point multiple times
        if (fragment.newLinePoints.any { it.id == point.id }) {
            return
        }

        // Prevent adding points that are already part of an existing line
        // Check 1: Visual Overlays (Most accurate for current session state)
        val isVisuallyConnected = fragment.completedLineOverlays.any { overlay ->
            (overlay is ClickablePolylineOverlay) && overlay.labeledPoints.any { it.id == point.id }
        }

        // Check 2: Database State (Backup)
        val isDbConnected = collectedLines.any { line -> line.points.any { it.id == point.id } }

        if (isVisuallyConnected || isDbConnected) {
            Toast.makeText(
                fragment.requireContext(),
                "Point is already part of another line",
                Toast.LENGTH_SHORT
            ).show()
            android.util.Log.d(
                "MappingLogic",
                "Blocked adding point ${point.id} - VisuallyConnected: $isVisuallyConnected, DbConnected: $isDbConnected"
            )
            return
        }

        // Ensure point is not "selected" in the UI sense (orange highlight)
        if (fragment.selectedPoint == point) {
            fragment.selectedPoint = null
            // Force redraw to remove the orange highlight immediately
            updateMarkersForZoom()
        }

        // STEAL POINT from existing lines
        val inheritedCode = transferPointToCurrentLine(point)

        // Inherit code if we are just starting (or have minimal points) and the stolen line gave us a code
        // This handles: L (p1, p7) -> Dissolve L -> New line starts with p8, adds p7 -> should become L
        if (inheritedCode != null && (fragment.newLinePoints.size <= 1)) {
            fragment.newLineCodeId = inheritedCode
        }

        fragment.newLinePoints.add(point)

        // Update the global point list so the map marker reflects the new Code ID immediately
        val idx = fragment.collectedLabeledPoints.indexOfFirst { it.id == point.id }
        if (idx >= 0) {
            fragment.collectedLabeledPoints[idx] =
                fragment.collectedLabeledPoints[idx].copy(codeId = fragment.newLineCodeId)
        }

        // Force refresh markers to show "L1" (or whatever new code is)
        updateMarkersForZoom(forceRefresh = true)

        updateNewLineUI()
        updateNewLineOverlay()
    }

    fun removePointFromNewLine(point: LabeledPoint) {
        fragment.newLinePoints.remove(point)

        // Revert Code ID for this point
        val idx = fragment.collectedLabeledPoints.indexOfFirst { it.id == point.id }
        if (idx >= 0) {
            fragment.collectedLabeledPoints[idx] =
                fragment.collectedLabeledPoints[idx].copy(codeId = "")
        }
        updateMarkersForZoom(forceRefresh = true)

        updateNewLineUI()
        updateNewLineOverlay()
    }

    fun updateNewLineUI() {
        val sheetBinding = fragment.binding.bottomSheetNewLine
        val count = fragment.newLinePoints.size

        var length = 0.0
        val geoPoints = fragment.newLinePoints.map { it.geoPoint }
        if (geoPoints.size >= 2) {
            for (i in 0 until geoPoints.size - 1) {
                length += (geoPoints[i] as GeoPoint).distanceToAsDouble(geoPoints[i + 1])
            }
            if (fragment.isNewLineClosed) {
                length += (geoPoints.last() as GeoPoint).distanceToAsDouble(
                    geoPoints.first()
                )
            }
        }

        sheetBinding.tvPointsCount.text = "$count ${if (count == 1) "Point" else "Points"}"
        sheetBinding.btnSaveLine.isEnabled = count >= 2

        // Disable closed line option if less than 3 points
        val canClose = count >= 3
        sheetBinding.cbClosedLine.isEnabled = canClose
        val colorRes = if (canClose) R.color.text_primary else R.color.neutral_dark
        sheetBinding.tvClosedLineLabel.setTextColor(
            ContextCompat.getColor(
                fragment.requireContext(),
                colorRes
            )
        )

        if (!canClose && sheetBinding.cbClosedLine.isChecked) {
            sheetBinding.cbClosedLine.isChecked = false
            fragment.isNewLineClosed = false
            // Recalculate length without closing
            // (Simpler to just let next update cycle handle or ignore slightly off length for a split second,
            // but effectively we should trigger updateNewLineOverlay if it changed.
            // However, the user interaction flow typically adds points then checks box.)
            // Let's just update the overlay to be safe if we unchecked it
            updateNewLineOverlay()
        }

        sheetBinding.tvEmptyState.visibility = if (count > 0) View.GONE else View.VISIBLE
        sheetBinding.rvPoints.visibility = if (count > 0) View.VISIBLE else View.GONE

        val items = fragment.newLinePoints.map { point ->
            ObjectListItem(
                id = point.id,
                codeId = point.codeId,
                dateTime = point.ts,
                indicatorType = IndicatorType.POINT
            )
        }
        (sheetBinding.rvPoints.adapter as? ObjectListAdapter)?.updateItems(items)
    }

    fun updateNewLineOverlay() {
        fragment.newLineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(fragment.binding.mapView, it)
        }
        fragment.newLineOverlay = null
        
        fragment.closingSegmentOverlay?.let {
            if (fragment.binding.mapView.overlays.contains(it)) {
                fragment.binding.mapView.overlays.remove(it)
            }
        }
        fragment.closingSegmentOverlay = null

        if (fragment.newLinePoints.size >= 2) {
            val geoPoints = fragment.newLinePoints.map { it.geoPoint }
            val mainColor = ContextCompat.getColor(fragment.requireContext(), R.color.primary)
            
            // 1. Regular Polyline (Solid)
            val polyline = ClickablePolylineOverlay(
                geoPoints,
                mainColor,
                6f,
                closed = false, 
                dashed = false
            ).apply {
                this.codeId = fragment.newLineCodeId
                this.labeledPoints = ArrayList(fragment.newLinePoints)
            }
            addPolylineBelowMarkers(polyline)
            fragment.newLineOverlay = polyline

            // 2. Closing Segment (Only if closed) - Dashed
            if (fragment.isNewLineClosed && geoPoints.size >= 3) {
                val closingPoints = listOf(geoPoints.last(), geoPoints.first())
                val closingPolyline = ClickablePolylineOverlay(
                    closingPoints,
                    mainColor,
                    6f,
                    closed = false,
                    dashed = true
                ).apply {
                    this.codeId = fragment.newLineCodeId
                }
                addPolylineBelowMarkers(closingPolyline)
                fragment.closingSegmentOverlay = closingPolyline
            }
        }
        fragment.binding.mapView.invalidate()
    }

    fun addPointToEditLine(point: LabeledPoint) {
        if (fragment.isSelectingPointForEditLine) {
            fragment.isSelectingPointForEditLine = false
            // hideObjectListBottomSheetInternalForNewLine() // Not needed if we just update adapter
        }

        val adapter = fragment.currentEditLineAdapter
        if (adapter != null) {
            val currentPoints = adapter.getPoints()
            // Prevent duplicate adjacent points?
            if (currentPoints.isNotEmpty() && currentPoints.last().id == point.id) return

            // Prevent adding points that are already part of another line (consistent with Add Line)
            val currentLine = fragment.pendingEditLineSegment
            val isVisuallyConnected = fragment.completedLineOverlays.any { overlay ->
                (overlay is ClickablePolylineOverlay) && overlay != currentLine && overlay.labeledPoints.any { it.id == point.id }
            }
            val currentCode = currentLine?.codeId ?: ""
            val isDbConnected = collectedLines.any { line ->
                line.line.id != currentCode && line.points.any { it.id == point.id }
            }

            if (isVisuallyConnected || isDbConnected) {
                Toast.makeText(
                    fragment.requireContext(),
                    "Point is already part of another line",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // STEAL POINT from existing lines
            val inheritedCode = transferPointToCurrentLine(point)

            // Check for "Shift Down" rename: If we dissolved a line (inheritedCode)
            // and it is the immediate predecessor of our current code (e.g. L dissolved, we are L1),
            // rename this line to the dissolved code (L).
            if (inheritedCode != null) {
                // Get current code from binding or adapter
                val currentCode = fragment.currentEditLineBinding?.tvCodeId?.text?.toString() ?: ""

                if (areCodesAdjacent(inheritedCode, currentCode)) {
                    // Update the UI
                    fragment.currentEditLineBinding?.tvCodeId?.text = inheritedCode
                    fragment.currentEditLineBinding?.tvCodeDescription?.text =
                        getCodeDescription(inheritedCode)

                    // Update pending segment code so it saves correctly
                    fragment.pendingEditLineSegment?.codeId = inheritedCode

                    // Also update ALL points in the adapter to reflect new code
                    val updatedPoints = currentPoints.map { it.copy(codeId = inheritedCode) }
                    adapter.updatePoints(updatedPoints)
                }
            }

            // Ensure the added point adopts the (possibly new) code
            val finalCode =
                fragment.currentEditLineBinding?.tvCodeId?.text?.toString() ?: point.codeId
            val pointToAdd =
                if (point.codeId != finalCode) point.copy(codeId = finalCode) else point

            adapter.addPoint(pointToAdd)
            updateEditLineOverlay()
            updateMarkersForZoom(forceRefresh = true) // Instant marker color refresh

            val count = adapter.itemCount
            fragment.currentEditLineBinding?.tvPointsCount?.text =
                "$count ${if (count == 1) "Point" else "Points"}"

            fragment.currentEditLineBinding?.let { b ->
                val canClose = count >= 3
                b.cbClosedLine.isEnabled = canClose

                val colorRes = if (canClose) R.color.text_primary else R.color.neutral_dark
                b.tvClosedLineLabel.setTextColor(
                    ContextCompat.getColor(
                        fragment.requireContext(),
                        colorRes
                    )
                )

                if (!canClose && b.cbClosedLine.isChecked) {
                    b.cbClosedLine.isChecked = false
                }
            }

            updateEditLineOverlay()
        }

        if (fragment.selectedPoint == point) {
            fragment.selectedPoint = null
            updateMarkersForZoom()
        }
    }

    private fun areCodesAdjacent(lowerCode: String, higherCode: String): Boolean {
        // Parse codes: Base + Number. e.g. "L" -> ("L", 0). "L1" -> ("L", 1).
        val (base1, num1) = normalizeLineCode(lowerCode) ?: return false
        val (base2, num2) = normalizeLineCode(higherCode) ?: return false

        if (base1 != base2) return false

        // Check exact decrement: num2 - num1 == 1
        // Treat null as 0.
        val n1 = num1 ?: 0
        val n2 = num2 ?: 0

        return (n2 - n1) == 1
    }

    fun updateEditLineOverlay() {
        val adapter = fragment.currentEditLineAdapter ?: return
        val points = adapter.getPoints()
        val geoPoints = points.map { it.geoPoint }
        val isClosed = fragment.currentEditLineBinding?.cbClosedLine?.isChecked == true
        val editingCodeId = fragment.pendingEditLineSegment?.codeId
            ?: points.firstOrNull()?.codeId.orEmpty()

        // Cleanup existing overlays
        fragment.highlightedLineOverlay?.let {
            if (fragment.binding.mapView.overlays.contains(it)) {
                fragment.binding.mapView.overlays.remove(it)
            }
        }
        fragment.closingSegmentOverlay?.let {
            if (fragment.binding.mapView.overlays.contains(it)) {
                fragment.binding.mapView.overlays.remove(it)
            }
        }

        if (editingCodeId.isNotEmpty()) {
            val staleSameCodeOverlays =
                fragment.binding.mapView.overlays.filterIsInstance<ClickablePolylineOverlay>()
                    .filter {
                        it.codeId == editingCodeId &&
                                it !== fragment.highlightedLineOverlay &&
                                it !== fragment.closingSegmentOverlay
                    }
                    .toList()
            staleSameCodeOverlays.forEach { fragment.binding.mapView.overlays.remove(it) }
        }
        fragment.closingSegmentOverlay = null

        if (geoPoints.size >= 2) {
            // 1. Main Segment
            // Always Solid + Primary Color (per user request)
            // The closing segment will be Dashed + Primary Color
            val mainColor = ContextCompat.getColor(fragment.requireContext(), R.color.primary)

            val mainPolyline = ClickablePolylineOverlay(
                geoPoints,
                mainColor,
                6f,
                closed = false, // We render the closing segment separately if closed
                dashed = false  // User wants main segment solid
            ).apply {
                this.codeId = fragment.pendingEditLineSegment?.codeId
                    ?: (if (points.isNotEmpty()) points[0].codeId else "")
                this.featureCode = fragment.pendingEditLineSegment?.featureCode
                    ?: (this.codeId.filter { it.isLetter() }.ifEmpty { "L" })
                this.labeledPoints = ArrayList(points)
            }
            addPolylineBelowMarkers(mainPolyline)
            fragment.highlightedLineOverlay = mainPolyline

            // 2. Closing Segment (Only if closed)
            // Dashed + Primary Color
            if (isClosed) {
                val closingPoints = listOf(geoPoints.last(), geoPoints.first())
                val closingPolyline = ClickablePolylineOverlay(
                    closingPoints,
                    ContextCompat.getColor(fragment.requireContext(), R.color.primary),
                    6f,
                    closed = false,
                    dashed = true
                )
                addPolylineBelowMarkers(closingPolyline)
                fragment.closingSegmentOverlay = closingPolyline
            }
        }
        fragment.binding.mapView.invalidate()
        updateMarkersForZoom(forceRefresh = true) // Sync markers with the new overlay points
    }

    fun saveNewLine() {
        if (fragment.newLinePoints.size < 2) return

        // Update points to have the new Line Code
        val newCode = fragment.newLineCodeId
        val updatedLinePoints = ArrayList<LabeledPoint>()

        fragment.newLinePoints.forEach { point ->
            val index = fragment.collectedLabeledPoints.indexOfFirst { it.id == point.id }
            if (index != -1) {
                val oldPoint = fragment.collectedLabeledPoints[index]
                // Only update if it doesn't already have a valid code?
                // User request implies forcing it to behave like line point.
                val newPoint = oldPoint.copy(codeId = newCode)
                fragment.collectedLabeledPoints[index] = newPoint
                updatedLinePoints.add(newPoint)
            } else {
                // Should not happen for existing points, but if so keep as is
                updatedLinePoints.add(point)
            }
        }

        val geoPoints = updatedLinePoints.map { it.geoPoint }
        var length = 0.0
        for (i in 0 until geoPoints.size - 1) length += geoPoints[i].distanceToAsDouble(geoPoints[i + 1])
        if (fragment.isNewLineClosed) length += geoPoints.last()
            .distanceToAsDouble(geoPoints.first())

        val clickablePolyline = ClickablePolylineOverlay(
            geoPoints,
            ContextCompat.getColor(fragment.requireContext(), R.color.slate_gray_light),
            6f,
            closed = fragment.isNewLineClosed
        ).apply {
            this.codeId = fragment.newLineCodeId
            this.featureCode = fragment.newLineCodeId.filter { it.isLetter() }
                .let { if (it.isEmpty()) "L" else it }
            this.pointCount = updatedLinePoints.size
            this.length = length
            this.labeledPoints = updatedLinePoints
            this.isClosed = fragment.isNewLineClosed
            setOnClickListener { handleLineSegmentClick(this) }
        }

        addPolylineBelowMarkers(clickablePolyline)
        fragment.completedLineOverlays.add(clickablePolyline)
        ensurePointClickHandlerAtEnd()

        // Save to Database
        val lineEntity = LineEntity(
            id = fragment.newLineCodeId,
            projectId = viewModel.currentProjectId.value ?: 0, // Fallback, should be set
            code = fragment.newLineCodeId.filter { it.isLetter() }, // "L", "BLDG" etc
            isClosed = fragment.isNewLineClosed
        )
        val pointEntities =
            updatedLinePoints.map { it.toPointEntity(viewModel.currentProjectId.value ?: 0) }
        viewModel.saveLine(lineEntity, pointEntities)

        // Force refresh of labels to show new codes
        updateMarkersForZoom()

        // Reset tracking state to prevent live tracking line from connecting to these points
        fragment.lineSegmentStartIndex = fragment.collectedLabeledPoints.size
        fragment.liveTrackingLineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(fragment.binding.mapView, it)
        }
        fragment.liveTrackingLineOverlay = null

        // Mark as saved to prevent hideNewLineBottomSheet from reverting codeIds
        fragment.isNewLineSaved = true

        // Reset and hide
        hideNewLineBottomSheet()

        // Maybe select/highlight the new line?
        // handleLineSegmentClick(clickablePolyline)
    }

    fun findViewAt(parent: View, x: Int, y: Int): View? {
        if (parent is ViewGroup) {
            for (i in parent.childCount - 1 downTo 0) {
                val child = parent.getChildAt(i)
                if (child.visibility != View.VISIBLE) continue

                val childLeft = child.left
                val childTop = child.top
                val childRight = child.right
                val childBottom = child.bottom

                if (x >= childLeft && x < childRight && y >= childTop && y < childBottom) {
                    val childX = x - childLeft
                    val childY = y - childTop
                    val found = findViewAt(child, childX, childY)
                    return found ?: child
                }
            }
        }
        return null
    }

    fun triggerButtonContainerRipple(buttonContainer: View) {
        buttonContainer.post {
            buttonContainer.isPressed = true
            buttonContainer.postDelayed({ buttonContainer.isPressed = false }, 100)
        }
    }

    fun adjustMapsButtonsForBottomSheet(overrideHeight: Int? = null, closingView: View? = null) {
        val mapsButtons = fragment.binding.llMapsButtons

        // Ensure translationY is always 0 — we use bottomMargin instead to avoid
        // any element going past img_menu or being hidden behind the bottom sheet.
        if (mapsButtons.translationY != 0f) {
            mapsButtons.translationY = 0f
        }

        // When called at show time (overrideHeight given, no closing view), the sheet's
        // height property is stale from the PREVIOUS session — it hasn't been laid out yet
        // in this new open. Defer to the next frame so Android can measure it properly.
        // Close/reset calls (closingView != null, or no args) execute immediately.
        if (overrideHeight != null && closingView == null) {
            mapsButtons.post { adjustMapsButtonsForBottomSheet() }
            return
        }

        var height = 0
        val collectSheet = fragment.binding.bottomSheetCollectPoint.root
        val lineSheet = fragment.binding.bottomSheetLineSegment.root
        val stakeoutSheet = fragment.binding.stakeoutBottomSheet.root

        if (collectSheet.visibility == View.VISIBLE && collectSheet.alpha > 0 && collectSheet != closingView) {
            height = maxOf(height, collectSheet.height)
        }
        if (lineSheet.visibility == View.VISIBLE && lineSheet.alpha > 0 && lineSheet != closingView) {
            height = maxOf(height, lineSheet.height)
        }
        if (stakeoutSheet.visibility == View.VISIBLE && stakeoutSheet.alpha > 0 && stakeoutSheet != closingView) {
            height = maxOf(height, stakeoutSheet.height)
        }
        val newLineSheet = fragment.binding.bottomSheetNewLine.root
        if (newLineSheet.visibility == View.VISIBLE && newLineSheet.alpha > 0 && newLineSheet != closingView) {
            height = maxOf(height, newLineSheet.height)
        }

        val density = fragment.resources.displayMetrics.density
        val baseMarginPx = (100 * density).toInt()
        val sheetPaddingPx = (8 * density).toInt()

        val params = mapsButtons.layoutParams as ConstraintLayout.LayoutParams
        val currentBottomMargin = params.bottomMargin

        var targetBottomMargin = baseMarginPx
        if (height > 0) {
            val lowestBtn = fragment.binding.imgResize
            val location = IntArray(2)
            lowestBtn.getLocationOnScreen(location)
            val lowestBtnBottomY = location[1] + lowestBtn.height

            val rootLocation = IntArray(2)
            fragment.binding.root.getLocationOnScreen(rootLocation)
            val screenHeight = rootLocation[1] + fragment.binding.root.height
            val sheetTopY = screenHeight - height

            // img_resize lives inside a weightSum=3 layout (weight-1 for compass, weight-2 for
            // the buttons). When bottomMargin increases by X, ll_maps_buttons shrinks by X, and
            // img_resize moves up by only X/3 (the weight-1 section absorbs the rest).
            // So to move img_resize up by `overlap`, we must increase the margin by 3×overlap.
            //
            // To get a stable overlap value that doesn't drift during animation, we reconstruct
            // img_resize's natural (resting) position: current screen Y + (currentMarginDelta / 3).
            val currentMarginDelta = currentBottomMargin - baseMarginPx
            // Resting bottom Y = where img_resize would be if margin were at baseMarginPx
            val naturalLowestBtnBottomY = lowestBtnBottomY + currentMarginDelta / 3f

            if (naturalLowestBtnBottomY + sheetPaddingPx > sheetTopY) {
                val overlapFromNatural = naturalLowestBtnBottomY + sheetPaddingPx - sheetTopY
                // Multiply by 3 to compensate for weight-based movement ratio
                targetBottomMargin = baseMarginPx + (3 * overlapFromNatural).toInt()
            }
        }

        if (currentBottomMargin == targetBottomMargin) return

        val animator = ValueAnimator.ofInt(currentBottomMargin, targetBottomMargin)
        animator.duration = 200
        animator.interpolator = FastOutSlowInInterpolator()
        animator.addUpdateListener { anim ->
            val value = anim.animatedValue as Int
            val lp = mapsButtons.layoutParams as ConstraintLayout.LayoutParams
            lp.bottomMargin = value
            mapsButtons.layoutParams = lp
        }
        animator.start()
    }

    // ---------------------------------------------------------------------
    // Logic / Action Methods
    // ---------------------------------------------------------------------

    fun addPointAtLocation(
        location: GeoPoint,
        pointId: String,
        codeId: String,
        indicatorType: IndicatorType
    ): Boolean {
        // Check for duplicate ID
        if (fragment.collectedLabeledPoints.any { it.id == pointId }) {
            Toast.makeText(
                fragment.requireContext(),
                "Point ID already exists",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }


        if (indicatorType == IndicatorType.LINE) {
            if (fragment.currentLineCodeId != null && fragment.currentLineCodeId != codeId) {
                finalizeCurrentLineSegment(closeFlag = fragment.isShapeClosed)
            }
            fragment.currentLineCodeId = codeId
            trackLineCodeUsage(codeId)
        } else {
            finalizeCurrentLineSegment(closeFlag = fragment.isShapeClosed)
            fragment.addFromBeginning = false
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        var date = Date()

        // Fix: Backdate timestamp if adding to the beginning of a line
        if (indicatorType == IndicatorType.LINE && fragment.addFromBeginning) {
            val currentPoints = getConsecutiveLineCodePoints()
            if (currentPoints.isNotEmpty()) {
                val firstPoint = currentPoints.first()
                try {
                    val firstPointDate = dateFormat.parse(firstPoint.ts)
                    if (firstPointDate != null) {
                        // Subtract 1 second to ensure it's strictly before
                        date = Date(firstPointDate.time - 1000)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val timestamp = dateFormat.format(date)

        val labeledPoint = LabeledPoint(
            id = pointId,
            codeId = codeId,
            coords = listOf(location.longitude, location.latitude),
            elevation = 0.0,
            ts = timestamp
        )

        // Database Integration: Save point via ViewModel
        // Note: insertIndex logic for 'addFromBeginning' is implied by timestamp or Line order.
        // For standalone points, we rely on timestamp.
        // If strict ordering is needed for Lines, it's handled in LineEntity logic.
        // We use a safe default project ID (1L) if none is set, as per ViewModel init.
        val currentProjectId = viewModel.currentProjectId.value ?: 1L
        viewModel.savePoint(labeledPoint.toPointEntity(currentProjectId))
        // Manual list update removed; relying on Flow observation updatePointsFromDatabase.

        if (indicatorType == IndicatorType.LINE) {
            // redrawPolyline() moved to updatePointsFromDatabase to ensure it uses updated data
            fragment.hasStartedNewLine = false
        }

        fragment.lastZoomLevel = fragment.binding.mapView.zoomLevelDouble
        updateMarkersForZoom()
        return true
    }


    fun showDeleteLineOptionsDialog(lineSegment: ClickablePolylineOverlay) {
        val dialog = BottomSheetDialog(fragment.requireContext())
        dialog.setContentView(R.layout.bottom_sheet_delete_line_options)

        val btnKeepPoints = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_keep_points)
        val btnDeleteAll = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_delete_all)
        val btnCancel = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)

        btnKeepPoints?.setOnClickListener {
            deleteLineOnlyKeepPoints(lineSegment)
            dialog.dismiss()
        }

        btnDeleteAll?.setOnClickListener {
            deleteLineAndPoints(lineSegment)
            dialog.dismiss()
        }

        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    fun deleteLineOnlyKeepPoints(lineSegment: ClickablePolylineOverlay) {
        // 1. Get line details before removal
        val pointsToConvert = lineSegment.labeledPoints.toList()
        val lineCode = lineSegment.codeId

        // 2. Remove from completedLineOverlays
        fragment.completedLineOverlays.remove(lineSegment)

        // 3. Remove line from map overlays
        fragment.binding.mapView.overlays.remove(lineSegment)

        // 4. Remove line entity from DB
        val lineEntity = collectedLines.find { it.line.id == lineSegment.codeId }?.line
        if (lineEntity != null) {
            viewModel.deleteLine(lineEntity)
        }

        // 5. Convert line points to individual points (remove line code)
        pointsToConvert.forEach { labeledPoint ->
            val pointEntity = collectedLines
                .flatMap { it.points }
                .find { it.id == labeledPoint.id }

            if (pointEntity != null) {
                val updatedEntity = pointEntity.copy(code = "")
                viewModel.savePoint(updatedEntity)
            }
        }

        // 6. Update local collectedLabeledPoints to remove line code from points
        val pointIds = pointsToConvert.map { it.id }.toSet()
        fragment.collectedLabeledPoints.forEach { point ->
            if (point.id in pointIds && point.codeId == lineCode) {
                val index = fragment.collectedLabeledPoints.indexOf(point)
                if (index >= 0) {
                    fragment.collectedLabeledPoints[index] = point.copy(codeId = "")
                }
            }
        }

        // 7. Update UI
        if (fragment.highlightedLineOverlay == lineSegment) {
            fragment.highlightedLineOverlay = null
        }
        fragment.selectedPoint = null
        updateMarkersForZoom()
        fragment.binding.mapView.invalidate()
        hideLineSegmentDetailsBottomSheet()
        refreshNextPointIdForCollectSheet()

        // 8. Refresh object list if visible
        val sheetBinding = fragment.binding.bottomSheetObjectList
        if (sheetBinding.root.visibility == View.VISIBLE) {
            val allItems = processCollectedPointsForObjectList()
            val items = if (fragment.isSelectingPointForEditLine || fragment.isCreatingNewLine) {
                allItems.filter { it.indicatorType == IndicatorType.POINT }
            } else {
                allItems
            }
            (sheetBinding.rvObjectList.adapter as? ObjectListAdapter)?.updateItems(items.toMutableList())
        }

        Toast.makeText(fragment.requireContext(), "Line deleted (Points kept)", Toast.LENGTH_SHORT).show()
    }

    fun deleteLineAndPoints(lineSegment: ClickablePolylineOverlay) {
        // 1. Remove from completedLineOverlays
        fragment.completedLineOverlays.remove(lineSegment)

        // 2. Remove line from map
        OsmdroidPolylineHelper.removePolyline(fragment.binding.mapView, lineSegment)

        // 3. Remove line entity from DB
        val lineEntity = collectedLines.find { it.line.id == lineSegment.codeId }?.line
        if (lineEntity != null) {
            viewModel.deleteLine(lineEntity)
        }

        // 4. Remove points associated with this line via ViewModel
        val pointIdsToRemove = lineSegment.labeledPoints.map { it.id }.toSet()
        pointIdsToRemove.forEach { id ->
            viewModel.deletePointById(id)
        }
        // Local list update handled by Flow

        // Remove markers
        val markersToRemove =
            fragment.markerToPointMap.entries.filter { it.value.id in pointIdsToRemove }
        markersToRemove.forEach { (marker, _) ->
            fragment.binding.mapView.overlays.remove(marker)
            fragment.collectedPointMarkers.remove(marker)
            fragment.markerToPointMap.remove(marker)
        }

        // 5. Update UI
        if (fragment.highlightedLineOverlay == lineSegment) {
            fragment.highlightedLineOverlay = null
        }
        fragment.selectedPoint = null
        updateMarkersForZoom()
        fragment.binding.mapView.invalidate()
        hideLineSegmentDetailsBottomSheet()
        refreshNextPointIdForCollectSheet()

        // 6. Refresh object list if visible
        val sheetBinding = fragment.binding.bottomSheetObjectList
        if (sheetBinding.root.visibility == View.VISIBLE) {
            val allItems = processCollectedPointsForObjectList()
            val items = if (fragment.isSelectingPointForEditLine || fragment.isCreatingNewLine) {
                allItems.filter { it.indicatorType == IndicatorType.POINT }
            } else {
                allItems
            }
            (sheetBinding.rvObjectList.adapter as? ObjectListAdapter)?.updateItems(items.toMutableList())
        }

        Toast.makeText(fragment.requireContext(), "Line and points deleted", Toast.LENGTH_SHORT).show()
    }

    fun deleteLineSegment(lineSegment: ClickablePolylineOverlay) {
        deleteLineAndPoints(lineSegment)
    }

    fun deletePoint(point: LabeledPoint) {
        // 1. Remove from DB using ID to ensure the correct pk is found and deleted
        viewModel.deletePointById(point.id)
        // Local list update handled by Flow


        // 2. Remove markers - MATCH ALL MARKERS for this point (Point + Label)
        val markersToRemove = fragment.markerToPointMap.entries.filter { it.value.id == point.id }
        markersToRemove.forEach { (marker, _) ->
            fragment.binding.mapView.overlays.remove(marker)
            fragment.collectedPointMarkers.remove(marker)
            fragment.markerToPointMap.remove(marker)
        }

        // 3. Check and update lines
        val linesToRemove = mutableListOf<Any>()
        for (overlay in fragment.completedLineOverlays) {
            if (overlay is ClickablePolylineOverlay) {
                if (overlay.labeledPoints.any { it.id == point.id }) {
                    val newPoints = overlay.labeledPoints.toMutableList()
                    newPoints.removeIf { it.id == point.id }

                    if (newPoints.size < 2) {
                        linesToRemove.add(overlay)
                    } else {
                        overlay.labeledPoints = newPoints
                        overlay.setPoints(newPoints.map { it.geoPoint })
                        overlay.pointCount = newPoints.size

                        // Recalculate length
                        var length = 0.0
                        for (i in 0 until newPoints.size - 1) {
                            length += newPoints[i].geoPoint.distanceToAsDouble(newPoints[i + 1].geoPoint)
                        }
                        if (overlay.isClosed && newPoints.size > 2) {
                            length += newPoints.last().geoPoint.distanceToAsDouble(newPoints.first().geoPoint)
                        }
                        overlay.length = length
                    }
                }
            }
        }

        linesToRemove.forEach {
            OsmdroidPolylineHelper.removePolyline(
                fragment.binding.mapView,
                it as org.osmdroid.views.overlay.Polyline
            )
            fragment.completedLineOverlays.remove(it)
        }

        // 4. Update UI
        if (fragment.selectedPoint?.id == point.id) {
            fragment.selectedPoint = null
        }
        updateMarkersForZoom()
        fragment.binding.mapView.invalidate()
        hideLineSegmentDetailsBottomSheet()
        refreshNextPointIdForCollectSheet()
        Toast.makeText(fragment.requireContext(), "Point deleted", Toast.LENGTH_SHORT).show()
    }

    fun finalizeCurrentLineSegment(closeFlag: Boolean = fragment.isShapeClosed) {
        val lineCodePoints =
            if (fragment.currentLineCodeId != null) getAllPointsInCurrentLineSegment() else getConsecutiveLineCodePoints()

        fragment.polylineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(
                fragment.binding.mapView,
                it
            )
        }
        fragment.polylineOverlay = null
        fragment.liveTrackingLineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(
                fragment.binding.mapView,
                it
            )
        }
        fragment.liveTrackingLineOverlay = null

        if (lineCodePoints.size >= 2) {
            val geoPoints = lineCodePoints.map { it.geoPoint }
            var length = 0.0
            for (i in 0 until geoPoints.size - 1) length += geoPoints[i].distanceToAsDouble(
                geoPoints[i + 1]
            )
            if (closeFlag) length += geoPoints.last().distanceToAsDouble(geoPoints.first())

            val codeId = lineCodePoints.firstOrNull()?.codeId ?: ""

            // Persist Line State (Closure & Length)
            val projectId = viewModel.currentProjectId.value ?: 1L
            val existingLine = collectedLines.find { it.line.id == codeId }?.line
            // Extract feature code (e.g. "L" from "L1") if creating new
            val featureCode = codeId.filter { it.isLetter() }

            val lineEntity = existingLine?.copy(
                length = length,
                isClosed = closeFlag
            ) ?: LineEntity(
                id = codeId,
                projectId = projectId,
                code = featureCode.ifEmpty { "L" },
                length = length,
                isClosed = closeFlag
            )
            val pointEntities = lineCodePoints.map { it.toPointEntity(projectId) }
            viewModel.saveLine(lineEntity, pointEntities)

            fragment.completedLineOverlays.filter { it is ClickablePolylineOverlay && it.codeId == codeId }
                .forEach {
                    OsmdroidPolylineHelper.removePolyline(fragment.binding.mapView, it)
                    fragment.completedLineOverlays.remove(it)
                }

            val clickablePolyline = ClickablePolylineOverlay(
                geoPoints,
                ContextCompat.getColor(fragment.requireContext(), R.color.slate_gray_light),
                6f,
                closed = closeFlag
            ).apply {
                this.codeId = codeId
                this.featureCode = featureCode.ifEmpty { "L" }
                this.pointCount = lineCodePoints.size
                this.length = length
                this.labeledPoints = lineCodePoints
                this.isClosed = closeFlag
                setOnClickListener { handleLineSegmentClick(this) }
            }

            addPolylineBelowMarkers(clickablePolyline)
            fragment.completedLineOverlays.add(clickablePolyline)
            ensurePointClickHandlerAtEnd()
            updateMarkersForZoom()
            bringLocationMarkerToTop()
        }

        fragment.lineSegmentStartIndex = fragment.collectedLabeledPoints.size
        fragment.isShapeClosed = false
        fragment.addFromBeginning = false
        fragment.currentLineCodeId = null
        fragment.hasStartedNewLine = true
        fragment.binding.mapView.invalidate()
    }

    fun handleLineSegmentClick(clickedPolyline: ClickablePolylineOverlay) {
        // Block all map interactions when Select Code, Collect Point, or New Line sheets are open
        if (fragment.binding.bottomSheetSelectCode.root.visibility == View.VISIBLE ||
            fragment.binding.bottomSheetCollectPoint.root.visibility == View.VISIBLE ||
            fragment.binding.bottomSheetNewLine.root.visibility == View.VISIBLE) {
            return
        }

        hideLineSegmentMenuThen {
            // Hide stakeout bottom sheet if it's currently visible (prevents overlap)
            if (fragment.currentStakeoutMode != StakeoutMode.NONE) {
                fragment.helper.hideStakeoutUI(showNav = false)
            }

            val previousHighlightedLine = fragment.highlightedLineOverlay
            fragment.highlightedLineOverlay?.unhighlight()
            if (previousHighlightedLine != null && previousHighlightedLine != clickedPolyline) updateMarkersForZoom(
                forceRefresh = true
            )

            // Always highlight and show the bottom sheet (don't deselect on second click, like points)
            clickedPolyline.highlight(
                ContextCompat.getColor(
                    fragment.requireContext(),
                    R.color.primary
                )
            )
            fragment.highlightedLineOverlay = clickedPolyline
            showLineSegmentDetailsBottomSheet(clickedPolyline)
            updateMarkersForZoom(forceRefresh = true)
            fragment.binding.mapView.invalidate()
        }
    }

    fun showLineSegmentDetailsBottomSheet(
        lineSegment: ClickablePolylineOverlay,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP,
        showNav: Boolean = true
    ) = hideMenu {
        showSheet(SheetType.LINE_SEGMENT, transition) {
            fragment.selectedPoint = null
            lineSegment.highlight(ContextCompat.getColor(fragment.requireContext(), R.color.primary))
            fragment.highlightedLineOverlay = lineSegment
            updateMarkersForZoom()

            // Ensure accurate offset is applied
            val sheetBinding = fragment.binding.bottomSheetLineSegment
            sheetBinding.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
                bottomMargin = bottomNavOffset.toInt()
            }

            // Ensure bottom navigation is visible for line segment sheet
            showBottomNavigation(force = true)

            sheetBinding.root.elevation = 24f * fragment.resources.displayMetrics.density
            sheetBinding.root.translationZ = 24f * fragment.resources.displayMetrics.density
            sheetBinding.tvCodeId.text = lineSegment.codeId.ifEmpty { "No code" }
            sheetBinding.llCodeIdContainer.visibility = View.VISIBLE
            sheetBinding.viewTypeDot.visibility = View.GONE
            sheetBinding.txtPointId.visibility = View.GONE
            sheetBinding.tvSegmentInfo.text =
                "${lineSegment.pointCount} Points | ${String.format("%.1f M", lineSegment.length)}"
            sheetBinding.txtPointInfo.text = "${lineSegment.pointCount}"
            sheetBinding.txtDistanceInfo.text = String.format("%.1f M", lineSegment.length)
            sheetBinding.txtSlopeDistanceInfo.text = String.format("%.1f M", lineSegment.length)

            sheetBinding.llContinueCollect.setOnClickListener {
                sheetBinding.clLineMenu.visibility = View.GONE
                lineSegment.labeledPoints.lastOrNull() ?: return@setOnClickListener
                fragment.selectedPointCodeId = lineSegment.codeId
                fragment.selectedPointIndicatorType = IndicatorType.LINE
                fragment.currentLineCodeId = lineSegment.codeId
                fragment.lineSegmentStartIndex =
                    fragment.collectedLabeledPoints.indexOfFirst { it.codeId == lineSegment.codeId }
                        .coerceAtLeast(0)
                fragment.wasCollectingBeforePointDetails = true
                fragment.isShapeClosed = false
                fragment.addFromBeginning = false
                hideLineSegmentDetailsBottomSheet()
                fragment.binding.root.postDelayed({ updateLiveTrackingLine() }, 200)
            }

            sheetBinding.btnCloseLineSegment.setOnClickListener {
                sheetBinding.clLineMenu.visibility = View.GONE
                // Close button always dismisses everything — back stack cleared.
                clearBackStack()
                hideLineSegmentDetailsBottomSheet()
            }

            sheetBinding.btnMenu.setOnClickListener {
                if (sheetBinding.clLineMenu.visibility == View.VISIBLE) {
                    hidePointLineSelection(sheetBinding)
                } else {
                    showPointLineSelection(sheetBinding)
                }
                sheetBinding.llContinueCollect.visibility =
                    if (lineSegment.isClosed) View.GONE else View.VISIBLE
            }

            sheetBinding.llContinueCollect.visibility =
                if (lineSegment.isClosed) View.GONE else View.VISIBLE
            
            // ENSURE Edit button and Menu button are visible and active for Lines
            sheetBinding.llEdit.visibility = View.VISIBLE
            sheetBinding.btnMenu.visibility = View.VISIBLE

            // Reset button to three-dot menu for line segments
            sheetBinding.btnMenu.setImageResource(R.drawable.ic_more)
            sheetBinding.btnMenu.rotation = 90f
            sheetBinding.btnMenu.scaleType = ImageView.ScaleType.CENTER_INSIDE
            sheetBinding.btnMenu.setPadding(
                (10 * fragment.resources.displayMetrics.density).toInt(),
                (10 * fragment.resources.displayMetrics.density).toInt(),
                (10 * fragment.resources.displayMetrics.density).toInt(),
                (10 * fragment.resources.displayMetrics.density).toInt()
            )

            setupBottomSheetClickToHideMenu(sheetBinding.root, sheetBinding)
            setupSwipeGestureForPointLineSelection(sheetBinding.root, sheetBinding)

            // If user taps anywhere in the fragment while menu is open, hide it first.
            val hideMenuOnOutsideTouch: (MotionEvent) -> Unit = hideMenuOnOutsideTouch@{ event ->
                if (event.action != MotionEvent.ACTION_DOWN) return@hideMenuOnOutsideTouch
                if (sheetBinding.clLineMenu.visibility != View.VISIBLE) return@hideMenuOnOutsideTouch

                val menuRect = Rect().also { sheetBinding.clLineMenu.getGlobalVisibleRect(it) }
                val btnRect = Rect().also { sheetBinding.btnMenu.getGlobalVisibleRect(it) }
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                if (!menuRect.contains(x, y) && !btnRect.contains(x, y)) {
                    hidePointLineSelection(sheetBinding)
                }
            }

            fragment.binding.root.setOnTouchListener { _, event ->
                hideMenuOnOutsideTouch(event)
                false
            }
            setMapTouchForLineSegment(blockMap = false)

            sheetBinding.llEdit.setOnClickListener {
                sheetBinding.clLineMenu.visibility = View.GONE
                // Push current sheet so EditLine's Close goes back here.
                // pushBackStack { showLineSegmentDetailsBottomSheet(lineSegment, BottomSheetTransition.SLIDE_IN_LEFT) ; Unit }
                hideLineSegmentDetailsBottomSheet(clearState = false, showNav = false)
                showEditLineBottomSheet(lineSegment)
            }

            // Populate Points List
            val items = lineSegment.labeledPoints.map { point ->
                ObjectListItem(
                    id = point.id,
                    codeId = point.codeId,
                    dateTime = point.ts,
                    indicatorType = IndicatorType.POINT
                )
            }
            val adapter = ObjectListAdapter(items.toMutableList(), onItemClick = { item ->
                // Handle item click if needed, e.g. center on point
                fragment.collectedLabeledPoints.find { it.id == item.id }
                    ?.let {
                        animateToLocationWithZoom(
                            it.geoPoint,
                            fragment.binding.mapView.zoomLevelDouble.coerceAtLeast(18.0)
                        )
                        showPointDetailsBottomSheet(it)
                    }
                Unit
            })

            sheetBinding.llDeleteLineButton.setOnClickListener {
                showDeleteLineOptionsDialog(lineSegment)
            }

            sheetBinding.btnStakeout.setSwipeSafeClickListener {
                sheetBinding.clLineMenu.visibility = View.GONE
                val points = lineSegment.labeledPoints.map {
                    StakeoutPoint(
                        id = it.id,
                        name = it.codeId,
                        latitude = it.geoPoint.latitude,
                        longitude = it.geoPoint.longitude,
                        elevation = it.elevation,
                        isLine = true // From lineSegment, so it's a line
                    )
                }

                // Manually clear selection state without triggering bottom nav show
                fragment.highlightedLineOverlay?.unhighlight()
                fragment.highlightedLineOverlay = null
                fragment.selectedPoint = null
                updateMarkersForZoom()

                fragment.restoreLineSegmentAfterStakeout = lineSegment
                hideLineSegmentDetailsBottomSheet(clearState = false, showNav = false)
                startStakeoutSession(points)
            }

            sheetBinding.root.visibility = View.VISIBLE
            adjustMapsButtonsForBottomSheet(overrideHeight = sheetBinding.root.height)
        }
    }

    fun hideLineSegmentDetailsBottomSheet(
        clearState: Boolean = true,
        showNav: Boolean = true,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_DOWN,
        onHidden: (() -> Unit)? = null
    ) {
        val sheetBinding = fragment.binding.bottomSheetLineSegment
        adjustMapsButtonsForBottomSheet(closingView = sheetBinding.root)
        fragment.binding.root.setOnTouchListener(null)
        fragment.binding.mapView.setOnTouchListener(null)

        val afterAnimation: () -> Unit = {
            onHidden?.invoke()
            resetLineSegmentSheetToDefaultState()
            sheetBinding.llPointLineInfo.visibility = View.GONE

            // RESTORE map interaction and buttons when closed
            fragment.binding.mapView.setMultiTouchControls(true)
            fragment.binding.mapView.setOnTouchListener(null)
            fragment.binding.llMapsButtons.visibility = View.VISIBLE

            if (clearState) {
                fragment.highlightedLineOverlay?.unhighlight()
                val wasCollectingStr = fragment.wasCollectingBeforePointDetails
                fragment.highlightedLineOverlay = null
                fragment.wasCollectingBeforePointDetails = false
                fragment.selectedPoint = null
                updateMarkersForZoom(forceRefresh = true)
                fragment.binding.mapView.invalidate()
                if (wasCollectingStr && fragment.currentLineCodeId != null) showCollectPointBottomSheet() else if (showNav) restoreStateAfterClosingInfoSheet()
            } else if (showNav) {
                restoreStateAfterClosingInfoSheet()
            }
        }

        popSheet(transition, afterAnimation)
    }

    fun showPointDetailsBottomSheet(
        point: LabeledPoint,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP,
        showNav: Boolean = true
    ) {
        hideMenu {
            // Guard: If Creating New Line, add point instead
            if (fragment.isCreatingNewLine || fragment.binding.bottomSheetNewLine.root.visibility == View.VISIBLE) {
                addPointToNewLine(point)
                return@hideMenu
            }

            // Guard: If Editing Line, add point instead
            if (fragment.isSelectingPointForEditLine || fragment.currentEditLineAdapter != null) {
                addPointToEditLine(point)
                return@hideMenu
            }

            showSheet(SheetType.LINE_SEGMENT, transition) {
                fragment.highlightedLineOverlay?.unhighlight()
                fragment.highlightedLineOverlay = null

                fragment.selectedPoint = point
                updateMarkersForZoom(forceRefresh = true)

                // Ensure bottom navigation is visible for detail sheets
                showBottomNavigation(force = true)

                // Start from a clean default state
                resetLineSegmentSheetToDefaultState()

                val sheetBinding = fragment.binding.bottomSheetLineSegment
                sheetBinding.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
                    bottomMargin = bottomNavOffset.toInt()
                }

                sheetBinding.root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                sheetBinding.root.requestLayout()

                sheetBinding.root.elevation = 24f * fragment.resources.displayMetrics.density
                sheetBinding.root.translationZ = 24f * fragment.resources.displayMetrics.density
                sheetBinding.llPointLineInfo.visibility = View.GONE
                sheetBinding.txtPointId.text = point.id
                sheetBinding.txtPointId.visibility = View.VISIBLE

                val isLine = isLineCodeFromCodeId(point.codeId)
                sheetBinding.llCodeIdContainer.visibility = if (isLine) View.VISIBLE else View.GONE
                if (isLine) sheetBinding.tvCodeId.text = point.codeId.ifEmpty { "No code" }

                sheetBinding.tvSegmentInfo.text = formatTimestamp(point.ts)
                sheetBinding.clLineMenu.visibility = View.GONE
                sheetBinding.btnCloseLineSegment.setOnClickListener {
                    // Close button always dismisses everything — back stack cleared.
                    clearBackStack()
                    hideLineSegmentDetailsBottomSheet()
                }
                sheetBinding.btnMenu.setOnClickListener {
                    if (!isLine) {
                        hideLineSegmentDetailsBottomSheet(clearState = false, showNav = false)
                        showEditPointBottomSheet(point)
                    } else {
                        if (sheetBinding.clLineMenu.visibility == View.VISIBLE) {
                            hidePointLineSelection(sheetBinding)
                        } else {
                            showPointLineSelection(sheetBinding)
                        }
                    }
                }
                sheetBinding.llContinueCollect.visibility = View.GONE // Selection from point doesn't support continue yet
                
                // Only individual standalone points can be editable
                sheetBinding.llEdit.visibility = if (!isLine) View.VISIBLE else View.GONE
                sheetBinding.llEdit.setOnClickListener {
                    sheetBinding.clLineMenu.visibility = View.GONE
                    if (!isLine) {
                        hideLineSegmentDetailsBottomSheet(clearState = false, showNav = false)
                        showEditPointBottomSheet(point)
                    }
                }

                // Hide the menu entirely if it's a line point (since it has no edit options)
                sheetBinding.btnMenu.visibility = if (!isLine) View.VISIBLE else View.GONE

                // For standalone points: show Edit icon; for line points: show three-dot menu
                if (!isLine) {
                    sheetBinding.btnMenu.setImageResource(R.drawable.ic_edit)
                    sheetBinding.btnMenu.rotation = 0f
                    sheetBinding.btnMenu.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    sheetBinding.btnMenu.setPadding(
                        (4 * fragment.resources.displayMetrics.density).toInt(),
                        (4 * fragment.resources.displayMetrics.density).toInt(),
                        (4 * fragment.resources.displayMetrics.density).toInt(),
                        (4 * fragment.resources.displayMetrics.density).toInt()
                    )
                }

                setupBottomSheetClickToHideMenu(sheetBinding.root, sheetBinding)
                setupSwipeGestureForPointLineSelection(sheetBinding.root, sheetBinding)

                // Hide delete for line points — only standalone points can be deleted from here
                sheetBinding.llDeletePointButton.visibility = if (isLine) View.GONE else View.VISIBLE
                sheetBinding.llDeletePointButton.setOnClickListener {
                    deletePoint(point)
                }

                setupFilterDropdown(sheetBinding, point)

                sheetBinding.btnStakeout.setSwipeSafeClickListener {
                    hidePointLineSelection(sheetBinding)
                    val sp = StakeoutPoint(
                        id = point.id,
                        name = point.codeId,
                        latitude = point.geoPoint.latitude,
                        longitude = point.geoPoint.longitude,
                        elevation = point.elevation,
                        isLine = isLineCodeFromCodeId(point.codeId)
                    )

                    // Manually clear selection state without triggering bottom nav show
                    fragment.highlightedLineOverlay?.unhighlight()
                    fragment.highlightedLineOverlay = null
                    fragment.selectedPoint = null
                    updateMarkersForZoom()

                    hideLineSegmentDetailsBottomSheet(clearState = false, showNav = false)
                    startStakeoutSession(listOf(sp))
                }

                sheetBinding.root.visibility = View.VISIBLE
                adjustMapsButtonsForBottomSheet(overrideHeight = sheetBinding.root.height)
            }
        }
    }


    fun showObjectListBottomSheet(
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP,
        showAddButton: Boolean = true,
        showTitle: Boolean = true,
        sheetTitle: String = "Object list"
    ) = hideMenu {
        val sheetBinding = fragment.binding.bottomSheetObjectList
        showSheet(SheetType.OBJECT_LIST, transition) {
            hideBottomNavigation {
            // Ensure object list is above other sheets (e.g., edit line)
            sheetBinding.root.elevation = 32f * fragment.resources.displayMetrics.density
            sheetBinding.root.translationZ = 32f * fragment.resources.displayMetrics.density
            sheetBinding.root.bringToFront()

            applyFullScreenConstraints(sheetBinding.root)

            // Control Add button visibility based on context
            sheetBinding.btnAddObject.visibility = if (showAddButton) View.VISIBLE else View.INVISIBLE
            sheetBinding.btnAddObject.alpha = if (showAddButton) 1f else 0f

            sheetBinding.tvSheetTitle.text = sheetTitle
            sheetBinding.tvSheetTitle.visibility = if (showTitle) View.VISIBLE else View.INVISIBLE

            sheetBinding.cvAddOptions.visibility = View.GONE
            sheetBinding.viewOutsideTouch.visibility = View.GONE
            sheetBinding.btnCloseObjectList.setOnClickListener { hideObjectListBottomSheet() }

            sheetBinding.viewOutsideTouch.setOnClickListener {
                sheetBinding.cvAddOptions.visibility = View.GONE
                sheetBinding.viewOutsideTouch.visibility = View.GONE
            }

            sheetBinding.btnAddObject.setOnClickListener {
                if (sheetBinding.cvAddOptions.visibility == View.VISIBLE) {
                    sheetBinding.cvAddOptions.visibility = View.GONE
                    sheetBinding.viewOutsideTouch.visibility = View.GONE
                } else {
                    sheetBinding.cvAddOptions.visibility = View.VISIBLE
                    sheetBinding.viewOutsideTouch.visibility = View.VISIBLE
                }
            }
            sheetBinding.llOptionPoint.setOnClickListener {
                sheetBinding.cvAddOptions.visibility = View.GONE
                sheetBinding.viewOutsideTouch.visibility = View.GONE

                fragment.selectedPointCodeId = "P"
                fragment.selectedPointIndicatorType = IndicatorType.POINT

                // Slide new point sheet up above object list (keep object list visible underneath)
                showNewPointBottomSheet(null)
            }

            sheetBinding.llOptionLine.setOnClickListener {
                sheetBinding.cvAddOptions.visibility = View.GONE
                sheetBinding.viewOutsideTouch.visibility = View.GONE
                hideObjectListBottomSheet(showNav = true) {
                    toggleNewLineMode()
                }
            }

            // Note: The UI for hiding add line buttons or changing title is
            // handled differently or not supported by bottom_sheet_object_list.xml currently.

            val allItems = processCollectedPointsForObjectList()
            val items = if (fragment.isSelectingPointForEditLine || fragment.isCreatingNewLine) {
                allItems.filter { it.indicatorType == IndicatorType.POINT }
            } else {
                allItems
            }
            val handleItemAction = { item: ObjectListItem ->
                if (fragment.isCreatingNewLine && item.indicatorType == IndicatorType.POINT) {
                    val pointToAdd = fragment.collectedLabeledPoints.find { it.id == item.id }
                    if (pointToAdd != null) {
                        // Add point first
                        addPointToNewLine(pointToAdd)
                        // Update overlay visualization
                        updateNewLineOverlay()
                        // Then hide object list, which will trigger popSheet to restore New Line sheet
                        hideObjectListBottomSheet(showNav = false, transition = BottomSheetTransition.SLIDE_OUT_RIGHT)
                    }
                } else if (fragment.isSelectingPointForEditLine && item.indicatorType == IndicatorType.POINT) {
                    val point = fragment.collectedLabeledPoints.find { it.id == item.id }
                    if (point != null && fragment.pendingEditLineSegment != null) {
                        hideObjectListBottomSheet(showNav = false, transition = BottomSheetTransition.SLIDE_OUT_RIGHT)
                        addExistingPointToLineSegment(point, fragment.pendingEditLineSegment!!)
                    }
                    fragment.isSelectingPointForEditLine = false
                } else {
                    // Push Object List so PointDetails/LineSegment Close comes back here.
//                    pushBackStack { showObjectListBottomSheet(BottomSheetTransition.SLIDE_IN_LEFT); Unit }
                    hideObjectListBottomSheet(showNav = false) {
                        if (item.indicatorType == IndicatorType.POINT) {
                            fragment.collectedLabeledPoints.find { it.id == item.id }
                                ?.let {
                                    animateToLocationWithZoom(
                                        it.geoPoint,
                                        fragment.binding.mapView.zoomLevelDouble.coerceAtLeast(18.0)
                                    )
                                    showPointDetailsBottomSheet(it)
                                }
                        } else {
                            fragment.completedLineOverlays.find { (it as? ClickablePolylineOverlay)?.codeId == item.codeId }
                                ?.let {
                                    val ls = it as ClickablePolylineOverlay
                                    zoomToLine(ls)
                                    handleLineSegmentClick(ls)
                                }
                        }
                    }
                }
                Unit
            }

            val adapter = ObjectListAdapter(
                objects = items.toMutableList(),
                onItemClick = handleItemAction
            )
            sheetBinding.rvObjectList.layoutManager = LinearLayoutManager(fragment.requireContext())
            sheetBinding.rvObjectList.adapter = adapter

            sheetBinding.etSearchObject.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s?.toString()?.lowercase() ?: ""
                    val filtered = items.filter {
                        it.id.lowercase().contains(query) || it.codeId.lowercase().contains(query)
                    }
                    sheetBinding.rvObjectList.adapter =
                        ObjectListAdapter(
                            objects = filtered.toMutableList(),
                            onItemClick = handleItemAction
                        )
                }
            })

            // Keyboard Handling: Keep bottom nav hidden while sheet is open
            sheetBinding.root.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (!sheetBinding.root.isAttachedToWindow) {
                        sheetBinding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        return
                    }
                    if (sheetBinding.root.visibility != View.VISIBLE) return

                    // Ensure bottom nav stays hidden while sheet is visible
                    hideBottomNavigation()
                }
            })

            adjustMapsButtonsForBottomSheet(overrideHeight = sheetBinding.root.height)
            // Don't enable swipe-to-dismiss for object list; only allow closing via close button
            // to prevent accidental swipes to the right when users interact with the list
            }
        }
    }

    private fun updateObjectListIfVisible() {
        val sheetBinding = fragment.binding.bottomSheetObjectList
        if (sheetBinding.root.visibility != View.VISIBLE) return

        val allItems = processCollectedPointsForObjectList()
        val items = if (fragment.isSelectingPointForEditLine || fragment.isCreatingNewLine) {
            allItems.filter { it.indicatorType == IndicatorType.POINT }
        } else {
            allItems
        }

        val handleItemAction = { item: ObjectListItem ->
            if (fragment.isCreatingNewLine && item.indicatorType == IndicatorType.POINT) {
                hideObjectListBottomSheet(showNav = false, transition = BottomSheetTransition.SLIDE_OUT_RIGHT)
                val point = fragment.collectedLabeledPoints.find { it.id == item.id }
                if (point != null) {
                    addPointToNewLine(point)
                }
            } else if (fragment.isSelectingPointForEditLine && item.indicatorType == IndicatorType.POINT) {
                hideObjectListBottomSheet(showNav = false, transition = BottomSheetTransition.SLIDE_OUT_RIGHT)
                val point = fragment.collectedLabeledPoints.find { it.id == item.id }
                if (point != null && fragment.pendingEditLineSegment != null) {
                    addExistingPointToLineSegment(point, fragment.pendingEditLineSegment!!)
                    showEditLineBottomSheet(fragment.pendingEditLineSegment!!, transition = BottomSheetTransition.SLIDE_IN_LEFT, isRestoring = true)
                }
            } else {
                hideObjectListBottomSheet(showNav = false) {
                    if (item.indicatorType == IndicatorType.POINT) {
                        fragment.collectedLabeledPoints.find { it.id == item.id }
                            ?.let {
                                animateToLocationWithZoom(
                                    it.geoPoint,
                                    fragment.binding.mapView.zoomLevelDouble.coerceAtLeast(18.0)
                                )
                                showPointDetailsBottomSheet(it)
                            }
                    } else {
                        fragment.completedLineOverlays.find { (it as? ClickablePolylineOverlay)?.codeId == item.codeId }
                            ?.let {
                                val ls = it as ClickablePolylineOverlay
                                zoomToLine(ls)
                                handleLineSegmentClick(ls)
                            }
                    }
                }
            }
            Unit
        }

        val query = sheetBinding.etSearchObject.text?.toString()?.lowercase() ?: ""
        val filtered = if (query.isEmpty()) {
            items
        } else {
            items.filter { it.id.lowercase().contains(query) || it.codeId.lowercase().contains(query) }
        }
        val adapter = sheetBinding.rvObjectList.adapter as? ObjectListAdapter
        if (adapter != null) {
            adapter.updateItems(filtered)
        } else {
            sheetBinding.rvObjectList.layoutManager = LinearLayoutManager(fragment.requireContext())
            sheetBinding.rvObjectList.adapter =
                ObjectListAdapter(objects = filtered.toMutableList(), onItemClick = handleItemAction)
        }
    }

    fun hideObjectListBottomSheet(
        showNav: Boolean = true,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_DOWN,
        onHidden: (() -> Unit)? = null
    ) {
        // Re-enable map touch and show map buttons
        fragment.binding.mapView.setMultiTouchControls(true)
        fragment.binding.mapView.setOnTouchListener(null)
        fragment.binding.llMapsButtons.visibility = View.VISIBLE

        // Hide keyboard when closing bottom sheet with search input
        hideKeyboard(fragment.binding.bottomSheetObjectList.root)

        val sheetBinding = fragment.binding.bottomSheetObjectList
        adjustMapsButtonsForBottomSheet(closingView = sheetBinding.root)

        val afterAnimation: () -> Unit = {
            if (showNav && fragment.binding.bottomSheetEditLine.root.visibility != View.VISIBLE && fragment.binding.bottomSheetNewLine.root.visibility != View.VISIBLE) {
                restoreStateAfterClosingInfoSheet()
            } else if (!showNav) {
                // If showNav is false, explicitly hide bottom navigation when returning to Edit/New Line sheets
                hideBottomNavigation()
            }
            // If edit line is open underneath, refresh its list/counts after closing object list
            if (fragment.binding.bottomSheetEditLine.root.visibility == View.VISIBLE) {
                val ls = fragment.pendingEditLineSegment
                val b = fragment.currentEditLineBinding
                val adapter = fragment.currentEditLineAdapter
                if (ls != null && b != null && adapter != null) {
                    val currentPoints = adapter.getPoints()
                    // Keep pending line synced to current unsaved edit state while edit sheet is open.
                    ls.labeledPoints = currentPoints.toList()

                    val count = currentPoints.size
                    b.tvPointsCount.text = "${count} ${if (count == 1) "Point" else "Points"}"
                    val canClose = count >= 3
                    b.cbClosedLine.isEnabled = canClose
                    val colorRes = if (canClose) R.color.text_primary else R.color.neutral_dark
                    b.tvClosedLineLabel.setTextColor(
                        ContextCompat.getColor(fragment.requireContext(), colorRes)
                    )
                    if (!canClose && b.cbClosedLine.isChecked) b.cbClosedLine.isChecked = false
                    updateEditLineOverlay()
                }
                // Ensure bottom nav is hidden when returning to Edit Line sheet
                hideBottomNavigation()
            } else if (fragment.binding.bottomSheetNewLine.root.visibility == View.VISIBLE) {
                // Ensure bottom nav is hidden when returning to New Line sheet
                hideBottomNavigation()
            }
            onHidden?.invoke()
        }

        popSheet(transition, afterAnimation)
    }

    fun processCollectedPointsForObjectList(): List<ObjectListItem> {
        val items = mutableListOf<ObjectListItem>()
        val processedLineCodes = mutableSetOf<String>()

        // 1. Process points from collected list
        fragment.collectedLabeledPoints.reversed().forEach { point ->
            if (isLineCodeFromCodeId(point.codeId)) {
                if (!processedLineCodes.contains(point.codeId)) {
                    val allLinePoints =
                        fragment.collectedLabeledPoints.filter { it.codeId == point.codeId }
                    var dist = 0.0
                    val gps = allLinePoints.map { it.geoPoint }
                    if (gps.size >= 2) {
                        for (i in 0 until gps.size - 1) dist += gps[i].distanceToAsDouble(gps[i + 1])
                    }
                    val isClosed =
                        fragment.completedLineOverlays.any { it is ClickablePolylineOverlay && it.codeId == point.codeId && it.isClosed }
                    if (isClosed && gps.size >= 2) dist += gps.last()
                        .distanceToAsDouble(gps.first())

                    val nestedItems = allLinePoints.map { lp ->
                        ObjectListItem(
                            lp.id,
                            lp.codeId,
                            formatTimestamp(lp.ts),
                            IndicatorType.POINT
                        )
                    }
                    items.add(
                        ObjectListItem(
                            point.codeId,
                            point.codeId,
                            "${allLinePoints.size} Points | ${String.format("%.1f M", dist)}",
                            IndicatorType.LINE,
                            allLinePoints.size,
                            dist,
                            nestedPoints = nestedItems
                        )
                    )
                    processedLineCodes.add(point.codeId)
                }
            } else {
                items.add(
                    ObjectListItem(
                        point.id,
                        point.codeId,
                        formatTimestamp(point.ts),
                        IndicatorType.POINT
                    )
                )
            }
        }

        // 2. Process manual lines from completedLineOverlays that weren't caught above
        for (overlay in fragment.completedLineOverlays) {
            if (overlay is ClickablePolylineOverlay && !processedLineCodes.contains(overlay.codeId)) {
                val nestedItems = overlay.labeledPoints.map { lp ->
                    ObjectListItem(
                        lp.id,
                        lp.codeId,
                        formatTimestamp(lp.ts),
                        IndicatorType.POINT
                    )
                }
                items.add(
                    ObjectListItem(
                        overlay.codeId ?: "Line",
                        overlay.codeId ?: "Line",
                        "${overlay.pointCount} Points | ${String.format("%.1f M", overlay.length)}",
                        IndicatorType.LINE,
                        overlay.pointCount,
                        overlay.length,
                        nestedPoints = nestedItems
                    )
                )
                processedLineCodes.add(overlay.codeId ?: "")
            }
        }

        // Sort effectively by implicit order of insertion (newest points first),
        // but manual lines (added last in list) might need to be at top if they are newer.
        // Simple fix: Sort by nothing or reverse pending?
        // For now, appending them at the end means they show at BOTTOM.
        // Ideally we want them mixed in relative to time, but we don't have time for overlays easily.
        // Let's assume user wants to see them.

        return items
    }

    fun showCollectPointBottomSheet(transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP) = hideMenu {
        val sheetBinding = fragment.binding.bottomSheetCollectPoint
        showSheet(SheetType.COLLECT_POINT, transition) {
            hideBottomNavigation {
                sheetBinding.root.elevation = 24f * fragment.resources.displayMetrics.density
                sheetBinding.root.translationZ = 24f * fragment.resources.displayMetrics.density

                // Reset height to WRAP_CONTENT
                sheetBinding.root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                sheetBinding.root.requestLayout()

                sheetBinding.llDataCollectionSettings.visibility = View.GONE
                sheetBinding.etNote.setText("")

                adjustMapsButtonsForBottomSheet(overrideHeight = sheetBinding.root.height)

                setupSwipeGestureForDataCollectionSettings(sheetBinding.scrollContent, sheetBinding)

                // Keyboard Handling: Keep bottom nav hidden while sheet is open
                collectSheetLayoutListener?.let {
                    sheetBinding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
                }
                val listener = object :
                    ViewTreeObserver.OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        if (!sheetBinding.root.isAttachedToWindow) {
                            sheetBinding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            collectSheetLayoutListener = null
                            return
                        }
                        if (sheetBinding.root.visibility != View.VISIBLE) return

                        // Ensure bottom nav stays hidden while sheet is visible
                        hideBottomNavigation()
                    }
                }
                collectSheetLayoutListener = listener
                sheetBinding.root.viewTreeObserver.addOnGlobalLayoutListener(listener)

            if (fragment.selectedPointIndicatorType == IndicatorType.LINE && isLineCodeFromCodeId(
                    fragment.selectedPointCodeId
                ) && fragment.currentLineCodeId == null
            ) {
                if (fragment.completedLineOverlays.any { (it as? ClickablePolylineOverlay)?.codeId == fragment.selectedPointCodeId } ||
                    fragment.collectedLabeledPoints.any { it.codeId == fragment.selectedPointCodeId }) {
                    advanceLineCodeForNewSegment(sheetBinding)
                }
            }

            sheetBinding.btnCloseCollectPoint.setOnClickListener {
                sheetBinding.clLineMenu.visibility = View.GONE
                attemptToExitCollectionMode(showNav = true)
            }

            // If continuing an existing line, set currentLineCodeId so live tracking line appears
            if (fragment.selectedPointIndicatorType == IndicatorType.LINE &&
                fragment.collectedLabeledPoints.isNotEmpty() &&
                !fragment.hasStartedNewLine
            ) {
                val lastPoint = fragment.collectedLabeledPoints.last()
                // Only set currentLineCodeId if we're continuing the exact same line code
                if (lastPoint.codeId == fragment.selectedPointCodeId && isLineCodeFromCodeId(
                        fragment.selectedPointCodeId
                    )
                ) {
                    fragment.currentLineCodeId = fragment.selectedPointCodeId
                    var startIndex = fragment.collectedLabeledPoints.size
                    for (i in fragment.collectedLabeledPoints.size - 1 downTo 0) {
                        val point = fragment.collectedLabeledPoints[i]
                        if (point.codeId == fragment.selectedPointCodeId && isLineCodeFromCodeId(
                                point.codeId
                            )
                        ) {
                            startIndex = i
                        } else {
                            break
                        }
                    }
                    fragment.lineSegmentStartIndex = startIndex
                    redrawPolyline()
                    updateLiveTrackingLine()
                }
            }

            sheetBinding.etPointId.setText(if (fragment.pointIdPrefix != null) "${fragment.pointIdPrefix}${fragment.pointIdNumericCounter}" else fragment.pointCounter.toString())
            sheetBinding.etPointId.setHint(if (fragment.pointIdPrefix != null) "${fragment.pointIdPrefix}${fragment.pointIdNumericCounter}" else fragment.pointCounter.toString())
            updatePointTypeIndicator(sheetBinding.viewTypeDot, fragment.selectedPointIndicatorType)
            sheetBinding.tvPointType.text = fragment.selectedPointCodeId.ifEmpty { "" }

            val updateCloseShape = {
                val pts =
                    if (fragment.selectedPointIndicatorType == IndicatorType.LINE && fragment.currentLineCodeId != null) getAllPointsInCurrentLineSegment() else emptyList()
                sheetBinding.llCloseShape.visibility =
                    if (pts.size >= 3) View.VISIBLE else View.GONE
                sheetBinding.llFromOtherSide.isEnabled = pts.size >= 2
                sheetBinding.llFromOtherSide.alpha = if (pts.size >= 2) 1f else 0.5f
                updateLineMenuVisibility(
                    sheetBinding.btnLineMenu,
                    fragment.selectedPointIndicatorType
                )
            }
            updateCloseShape()

            val hideMenu = {
                if (sheetBinding.clLineMenu.visibility == View.VISIBLE) {
                    sheetBinding.clLineMenu.visibility = View.GONE
                }
            }

            sheetBinding.root.setOnClickListener {
                // Only hide menu if it's visible, otherwise do nothing
                hideMenu()
            }
            sheetBinding.etPointId.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) hideMenu()
            }
            sheetBinding.etNote?.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) hideMenu()
            }

            // Force keyboard to show when ActionMode is created for EditTexts
            val forceKeyboardActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    // Show keyboard when selection menu appears
                    val focusedView = sheetBinding.root.findFocus() ?: sheetBinding.etPointId
                    focusedView.requestFocus()
                    focusedView.postDelayed({
                        val imm = fragment.requireContext()
                            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(focusedView, 0)
                    }, 100)
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean = false
                override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean =
                    false

                override fun onDestroyActionMode(mode: ActionMode?) {}
            }
            sheetBinding.etPointId.customSelectionActionModeCallback =
                forceKeyboardActionModeCallback
            sheetBinding.etNote?.customSelectionActionModeCallback = forceKeyboardActionModeCallback
            sheetBinding.llButtonContainer?.setOnClickListener { hideMenu() }
            sheetBinding.clLineMenu.setOnClickListener {
                // Consume click to prevent hiding menu when clicking on empty space within the menu
            }

            sheetBinding.llPointTypeSelector.setOnClickListener {
                sheetBinding.clLineMenu.visibility = View.GONE

                val openSelectCodeSheet = {
                    showSelectCodeBottomSheet(sheetBinding) { codeId, type ->
                        if (fragment.selectedPointIndicatorType == IndicatorType.LINE && (type != IndicatorType.LINE || fragment.currentLineCodeId != codeId)) finalizeCurrentLineSegment()
                        fragment.selectedPointCodeId = codeId
                        fragment.selectedPointIndicatorType = type
                        updatePointTypeIndicator(sheetBinding.viewTypeDot, type)
                        sheetBinding.tvPointType.text = codeId
                        if (type == IndicatorType.LINE) trackLineCodeUsage(codeId)
                        updateCloseShape()
                    }
                }

                val linePoints =
                    if (fragment.selectedPointIndicatorType == IndicatorType.LINE && fragment.currentLineCodeId != null) {
                        getAllPointsInCurrentLineSegment()
                    } else {
                        emptyList()
                    }

                if (fragment.selectedPointIndicatorType == IndicatorType.LINE &&
                    fragment.currentLineCodeId != null &&
                    linePoints.size == 1
                ) {
                    showConfirmDialogBottomSheet(
                        onYesClick = {
                            val currentAllPoints = getAllPointsInCurrentLineSegment()
                            if (currentAllPoints.size == 1 && fragment.collectedLabeledPoints.isNotEmpty() &&
                                isLineCodeFromCodeId(fragment.collectedLabeledPoints.last().codeId)
                            ) {
                                val removedPoint = fragment.collectedLabeledPoints.last()
                                viewModel.deletePointById(removedPoint.id)
                                fragment.collectedLabeledPoints.removeAt(fragment.collectedLabeledPoints.size - 1)

                                val markersToRemove =
                                    fragment.markerToPointMap.entries.filter { (_, point) ->
                                        point.codeId == fragment.currentLineCodeId
                                    }
                                markersToRemove.forEach { (marker, _) ->
                                    fragment.binding.mapView.overlays.remove(marker)
                                    fragment.markerToPointMap.remove(marker)
                                    fragment.collectedPointMarkers.remove(marker)
                                }

                                fragment.polylineOverlay?.let {
                                    OsmdroidPolylineHelper.removePolyline(
                                        fragment.binding.mapView,
                                        it
                                    )
                                }
                                fragment.polylineOverlay = null

                                fragment.liveTrackingLineOverlay?.let {
                                    OsmdroidPolylineHelper.removePolyline(
                                        fragment.binding.mapView,
                                        it
                                    )
                                }
                                fragment.liveTrackingLineOverlay = null
                                fragment.lineSegmentStartIndex = fragment.collectedLabeledPoints.size
                                fragment.currentLineCodeId = null

                                refreshNextPointIdForCollectSheet()
                                updateMarkersForZoom()
                                fragment.binding.mapView.invalidate()
                                updateCloseShape()
                            }
                            openSelectCodeSheet()
                        },
                        onNoClick = { }
                    )
                    return@setOnClickListener
                }

                openSelectCodeSheet()
            }
            sheetBinding.btnLineMenu.setOnClickListener {
                sheetBinding.clLineMenu.visibility =
                    if (sheetBinding.clLineMenu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
            sheetBinding.llCloseShape.setOnClickListener {
                if (fragment.selectedPointIndicatorType == IndicatorType.LINE) {
                    finalizeCurrentLineSegment(closeFlag = true)
                    fragment.hasStartedNewLine = true
                    advanceLineCodeForNewSegment(sheetBinding)
                    updateCloseShape()
                }
                sheetBinding.clLineMenu.visibility = View.GONE
            }
            sheetBinding.llFromOtherSide.setOnClickListener {
                sheetBinding.clLineMenu.visibility = View.GONE
                fragment.addFromBeginning = !fragment.addFromBeginning
                sheetBinding.ivFromOtherSide.setImageResource(R.drawable.ic_arrow_right_so)
                updateLiveTrackingLine()
            }
            sheetBinding.llStartNewLine.setOnClickListener {
                if (fragment.selectedPointIndicatorType == IndicatorType.LINE) {
                    val lineCodePoints = getConsecutiveLineCodePoints()
                    if (lineCodePoints.size == 1) {
                        showConfirmDialogBottomSheet(
                            onYesClick = {
                                if (fragment.collectedLabeledPoints.isNotEmpty() &&
                                    isLineCodeFromCodeId(fragment.collectedLabeledPoints.last().codeId)
                                ) {
                                    // Remove last point
                                    val pointToRemove = fragment.collectedLabeledPoints.lastOrNull()
                                    pointToRemove?.let { pt ->
                                        viewModel.deletePointById(pt.id)
                                    }
                                    // fragment.collectedLabeledPoints.removeAt... handled by Flow
                                    val markersToRemove =
                                        fragment.markerToPointMap.entries.filter { (_, point) ->
                                            point.codeId == fragment.currentLineCodeId
                                        }
                                    markersToRemove.forEach { (marker, _) ->
                                        fragment.binding.mapView.overlays.remove(marker)
                                        fragment.collectedPointMarkers.remove(marker)
                                        fragment.markerToPointMap.remove(marker)
                                    }
                                    fragment.polylineOverlay?.let {
                                        OsmdroidPolylineHelper.removePolyline(
                                            fragment.binding.mapView,
                                            it
                                        )
                                        fragment.polylineOverlay = null
                                    }
                                    fragment.liveTrackingLineOverlay?.let {
                                        OsmdroidPolylineHelper.removePolyline(
                                            fragment.binding.mapView,
                                            it
                                        )
                                        fragment.liveTrackingLineOverlay = null
                                    }
                                    fragment.lineSegmentStartIndex =
                                        fragment.collectedLabeledPoints.size
                                    fragment.currentLineCodeId = null
                                    updateMarkersForZoom()
                                    fragment.binding.mapView.invalidate()
                                }
                                finalizeCurrentLineSegment(closeFlag = fragment.isShapeClosed)
                                fragment.isShapeClosed = false
                                fragment.addFromBeginning = false
                                fragment.hasStartedNewLine = true
                                advanceLineCodeForNewSegment(sheetBinding)
                                updateCloseShape()
                                sheetBinding.clLineMenu.visibility = View.GONE
                            },
                            onNoClick = {
                                sheetBinding.clLineMenu.visibility = View.GONE
                            }
                        )
                    } else {
                        finalizeCurrentLineSegment(closeFlag = fragment.isShapeClosed)
                        fragment.isShapeClosed = false
                        fragment.addFromBeginning = false
                        fragment.hasStartedNewLine = true
                        advanceLineCodeForNewSegment(sheetBinding)
                        updateCloseShape()
                        sheetBinding.clLineMenu.visibility = View.GONE
                    }
                }
            }
            sheetBinding.btnSave.setOnClickListener {
                var pid = ""
                if (sheetBinding.etPointId.text.isEmpty()) {
                    pid = sheetBinding.etPointId.hint.toString()
                } else {
                    pid = sheetBinding.etPointId.text.toString()
                }
                val loc = fragment.currentLocation ?: fragment.locationMarker?.position
                if (loc != null && pid.isNotEmpty()) {
                    val success = addPointAtLocation(
                        loc,
                        pid,
                        fragment.selectedPointCodeId,
                        fragment.selectedPointIndicatorType
                    )
                    if (success) {
                        updateCloseShape()
                        updatePointIdAfterSave(pid)
                        sheetBinding.etPointId.setText(if (fragment.pointIdPrefix != null) "${fragment.pointIdPrefix}${fragment.pointIdNumericCounter}" else fragment.pointCounter.toString())
                        sheetBinding.etPointId.setHint(if (fragment.pointIdPrefix != null) "${fragment.pointIdPrefix}${fragment.pointIdNumericCounter}" else fragment.pointCounter.toString())
                        sheetBinding.etNote.text?.clear()
                    }
                }
            }
        }
    }
}

    fun hideCollectPointBottomSheet(
        shouldStartNewSegment: Boolean = false,
        finalizeSegment: Boolean = true,
        showNav: Boolean = true,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_DOWN,
        onHidden: (() -> Unit)? = null
    ) {
        // Hide keyboard when closing bottom sheet with input fields
        hideKeyboard(fragment.binding.bottomSheetCollectPoint.root)

        if (finalizeSegment && fragment.selectedPointIndicatorType == IndicatorType.LINE && fragment.currentLineCodeId != null) {
            finalizeCurrentLineSegment()
            if (shouldStartNewSegment) {
                fragment.hasStartedNewLine =
                    true; advanceLineCodeForNewSegment(fragment.binding.bottomSheetCollectPoint)
            }
        }
        adjustMapsButtonsForBottomSheet(closingView = fragment.binding.bottomSheetCollectPoint.root)

        animateSheetTransition(fragment.binding.bottomSheetCollectPoint.root, null, transition) {
            if (showNav) restoreStateAfterClosingInfoSheet()
            onHidden?.invoke()
        }
    }

    private fun attemptToExitCollectionMode(showNav: Boolean, onExit: (() -> Unit)? = null) {
        val points =
            if (fragment.selectedPointIndicatorType == IndicatorType.LINE && fragment.currentLineCodeId != null) getAllPointsInCurrentLineSegment() else getConsecutiveLineCodePoints()
        if (fragment.selectedPointIndicatorType == IndicatorType.LINE && fragment.currentLineCodeId != null && points.size == 1) {
            showConfirmDialogBottomSheet(
                onYesClick = {
                    val currentAllPoints = getAllPointsInCurrentLineSegment()
                    if (currentAllPoints.size == 1 && fragment.collectedLabeledPoints.isNotEmpty() &&
                        isLineCodeFromCodeId(fragment.collectedLabeledPoints.last().codeId)
                    ) {
                        val removedPoint = fragment.collectedLabeledPoints.last()
                        viewModel.deletePointById(removedPoint.id)
                        fragment.collectedLabeledPoints.removeAt(fragment.collectedLabeledPoints.size - 1)
                        val markersToRemove =
                            fragment.markerToPointMap.entries.filter { (_, point) ->
                                point.codeId == fragment.currentLineCodeId
                            }
                        markersToRemove.forEach { (marker, _) ->
                            fragment.binding.mapView.overlays.remove(marker)
                            fragment.markerToPointMap.remove(marker)
                            fragment.collectedPointMarkers.remove(marker)
                        }
                        fragment.polylineOverlay?.let {
                            OsmdroidPolylineHelper.removePolyline(
                                fragment.binding.mapView,
                                it
                            )
                        }
                        fragment.polylineOverlay = null
                        fragment.liveTrackingLineOverlay?.let {
                            OsmdroidPolylineHelper.removePolyline(
                                fragment.binding.mapView,
                                it
                            )
                        }
                        fragment.liveTrackingLineOverlay = null
                        fragment.lineSegmentStartIndex = fragment.collectedLabeledPoints.size
                        fragment.currentLineCodeId = null

                        refreshNextPointIdForCollectSheet()
                        updateMarkersForZoom()
                        fragment.binding.mapView.invalidate()
                    }
                    hideCollectPointBottomSheet(finalizeSegment = false, showNav = showNav)
                    onExit?.invoke()
                },
                onNoClick = { }
            )
        } else {
            hideCollectPointBottomSheet(finalizeSegment = points.size >= 2, showNav = showNav)
            onExit?.invoke()
        }
    }

    fun showConfirmDialogBottomSheet(
        onYesClick: () -> Unit = {},
        onNoClick: () -> Unit = {}
    ) {
        if (!fragment.isAdded || fragment.context == null) {
            return
        }

        fragment.confirmDialogBottomSheet?.dismiss()
        fragment.confirmDialogBottomSheet = null

        val dialogContext = fragment.activity ?: fragment.context ?: return

        val dialog = BottomSheetDialog(dialogContext, R.style.BottomSheetDialogTheme)
        val sheetBinding = BottomSheetConfirmDialogBinding.inflate(fragment.layoutInflater)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.btnYes.setOnClickListener {
            dialog.dismiss()
            onYesClick()
        }

        sheetBinding.btnNo.setOnClickListener {
            dialog.dismiss()
            onNoClick()
        }

        dialog.setOnDismissListener {
            if (fragment.confirmDialogBottomSheet == dialog) {
                fragment.confirmDialogBottomSheet = null
            }
        }

        fragment.confirmDialogBottomSheet = dialog

        try {
            if (fragment.isAdded) {
                dialog.show()
            }
        } catch (e: Exception) {
            fragment.confirmDialogBottomSheet = null
        }
    }

    fun showSelectCodeBottomSheet(
        collectSheetBinding: BottomSheetCollectPointBinding?,
        onlyPoints: Boolean = false,
        onlyLines: Boolean = false,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP,
        showNavOnCloseOverride: Boolean? = null,
        advanceLineCode: Boolean = true,
        onCodeSelected: (String, IndicatorType) -> Unit
    ) = hideMenu {
        val sheetBinding = fragment.binding.bottomSheetSelectCode

        // Store the current active sheet so we can return to it
        val previousSheet = currentActiveSheet

        showSheet(SheetType.SELECT_CODE, transition) {
        // Avoid right-slide animation when the sheet itself slides up
        sheetBinding.vfCodeManager.inAnimation = null
        sheetBinding.vfCodeManager.outAnimation = null
        sheetBinding.vfCodeManager.displayedChild = 0
        val shouldShowNavOnClose = showNavOnCloseOverride ?: (collectSheetBinding == null)

        hideBottomNavigation {
            sheetBinding.root.elevation = 48f * fragment.resources.displayMetrics.density
            sheetBinding.root.translationZ = 48f * fragment.resources.displayMetrics.density
            sheetBinding.root.bringToFront()

            applyFullScreenConstraints(sheetBinding.root)
            val defaultCodes = listOf(
                CodeItem("", "No code", IndicatorType.POINT),
                CodeItem("P", "Standard Point", IndicatorType.POINT),
                CodeItem("L", "Standard line", IndicatorType.LINE)
            )
            val allCodes =
                (defaultCodes + getCustomCodes()).filter {
                    (!onlyPoints || it.indicatorType == IndicatorType.POINT) &&
                    (!onlyLines || it.indicatorType == IndicatorType.LINE)
                }.toMutableList()

            val adapter = CodeAdapter(allCodes) { code ->
                val finalId = if (code.indicatorType == IndicatorType.LINE && advanceLineCode) {
                    nextLineCode(code.abbreviation)
                } else {
                    code.abbreviation
                }
                onCodeSelected(finalId, code.indicatorType)
                hideSelectCodeBottomSheet(showNav = shouldShowNavOnClose, previousSheet = previousSheet)
            }
            sheetBinding.rvCodes.layoutManager = LinearLayoutManager(fragment.requireContext())
            sheetBinding.rvCodes.adapter = adapter

            selectCodeSearchWatcher?.let { sheetBinding.etSearch.removeTextChangedListener(it) }
            selectCodeSearchWatcher = object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s?.toString()?.trim()?.lowercase() ?: ""
                    val filteredCodes = if (query.isEmpty()) {
                        allCodes
                    } else {
                        allCodes.filter {
                            it.abbreviation.lowercase().contains(query) ||
                                    it.description.lowercase().contains(query)
                        }
                    }
                    adapter.updateList(filteredCodes)
                }
            }
            sheetBinding.etSearch.addTextChangedListener(selectCodeSearchWatcher)
            sheetBinding.btnClose.setOnClickListener { hideSelectCodeBottomSheet(showNav = shouldShowNavOnClose, previousSheet = previousSheet) }

            // Add Code view setup (Child 1 of vfCodeManager)
                sheetBinding.btnAddCode.setOnClickListener {
                    sheetBinding.etCodeName.setText("")
                    sheetBinding.etCodeDesc.setText("")

                val contextWrapper = android.view.ContextThemeWrapper(
                    fragment.requireContext(),
                    com.google.android.material.R.style.Theme_Material3_Light
                )
                val typeAdapter = ArrayAdapter(
                    contextWrapper,
                    android.R.layout.simple_spinner_item,
                    listOf("Point", "Line")
                )
                typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                sheetBinding.spinnerCodeType.adapter = typeAdapter

                sheetBinding.btnAdd.setOnClickListener {
                    val name = sheetBinding.etCodeName.text.toString().trim()
                    if (name.isNotEmpty()) {
                        saveCustomCode(
                            name,
                            sheetBinding.etCodeDesc.text.toString().trim(),
                            sheetBinding.spinnerCodeType.selectedItem.toString()
                        )
                        // Trigger list refresh
                        val updatedCodes =
                            (defaultCodes + getCustomCodes()).filter {
                                (!onlyPoints || it.indicatorType == IndicatorType.POINT) &&
                                (!onlyLines || it.indicatorType == IndicatorType.LINE)
                            }.toMutableList()
                        adapter.updateList(updatedCodes)

                        hideKeyboard(sheetBinding.root)
                        sheetBinding.vfCodeManager.setInAnimation(fragment.requireContext(), R.anim.slide_in_left)
                        sheetBinding.vfCodeManager.setOutAnimation(fragment.requireContext(), R.anim.slide_out_right)
                        sheetBinding.vfCodeManager.showPrevious()
                        sheetBinding.vfCodeManager.setInAnimation(fragment.requireContext(), R.anim.slide_in_right)
                        sheetBinding.vfCodeManager.setOutAnimation(fragment.requireContext(), R.anim.slide_out_left)
                    }
                }

                sheetBinding.btnBackToSelect.setOnClickListener {
                    hideKeyboard(sheetBinding.root)
                    sheetBinding.vfCodeManager.setInAnimation(fragment.requireContext(), R.anim.slide_in_left)
                    sheetBinding.vfCodeManager.setOutAnimation(fragment.requireContext(), R.anim.slide_out_right)
                    sheetBinding.vfCodeManager.showPrevious()
                    sheetBinding.vfCodeManager.setInAnimation(fragment.requireContext(), R.anim.slide_in_right)
                    sheetBinding.vfCodeManager.setOutAnimation(fragment.requireContext(), R.anim.slide_out_left)
                }

                sheetBinding.btnCloseAddCode.setOnClickListener {
                    hideKeyboard(sheetBinding.root)
                    hideSelectCodeBottomSheet(showNav = shouldShowNavOnClose, previousSheet = previousSheet)
                }

                sheetBinding.vfCodeManager.setInAnimation(fragment.requireContext(), R.anim.slide_in_right)
                sheetBinding.vfCodeManager.setOutAnimation(fragment.requireContext(), R.anim.slide_out_left)
                sheetBinding.vfCodeManager.showNext()
            }

            // Keyboard Handling: Keep bottom nav hidden while sheet is open
            sheetBinding.root.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (!sheetBinding.root.isAttachedToWindow) {
                        sheetBinding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        return
                    }
                    if (sheetBinding.root.visibility != View.VISIBLE) return

                    // Ensure bottom nav stays hidden while sheet is visible
                    hideBottomNavigation()
                }
            })

            setupSwipeToDismiss(sheetBinding.root) { hideSelectCodeBottomSheet(showNav = shouldShowNavOnClose, previousSheet = previousSheet) }
            }
        }
    }

    fun hideSelectCodeBottomSheet(
        showNav: Boolean = true,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_DOWN,
        onHidden: (() -> Unit)? = null,
        previousSheet: SheetType? = null
    ) {
        // Re-enable map touch and show map buttons
        fragment.binding.mapView.setMultiTouchControls(true)
        fragment.binding.mapView.setOnTouchListener(null)
        fragment.binding.llMapsButtons.visibility = View.VISIBLE

        // Clear horizontal animations to avoid slide issues during hide
        val vf = fragment.binding.bottomSheetSelectCode.vfCodeManager
        vf.inAnimation = null
        vf.outAnimation = null

        hideKeyboard(fragment.binding.bottomSheetSelectCode.root)

        val afterAnimation: () -> Unit = {
            onHidden?.invoke()
            if (showNav) {
                restoreStateAfterClosingInfoSheet()
            }
        }

        // If we know which sheet was active before, restore it instead of closing all
        if (previousSheet != null && previousSheet != SheetType.NONE) {
            val outgoingView = getBindingRootForType(SheetType.SELECT_CODE)
            val incomingView = getBindingRootForType(previousSheet)
            currentActiveSheet = previousSheet

            // Reset the incoming sheet's layout params if it's COLLECT_POINT
            if (previousSheet == SheetType.COLLECT_POINT && incomingView != null) {
                val params = incomingView.layoutParams as? ConstraintLayout.LayoutParams
                if (params != null) {
                    params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    params.topToTop = ConstraintLayout.LayoutParams.UNSET
                    params.topMargin = 0
                    params.verticalBias = 1.0f
                    params.constrainedHeight = false
                    incomingView.layoutParams = params
                }
                incomingView.requestLayout()
            }

            animateSheetTransition(outgoingView, incomingView, transition, afterAnimation)
        } else {
            popSheet(transition, afterAnimation)
        }
    }



    fun redrawPolyline() {
        fragment.polylineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(
                fragment.binding.mapView,
                it
            )
        }
        fragment.polylineOverlay = null
        fragment.liveTrackingLineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(
                fragment.binding.mapView,
                it
            )
        }
        fragment.liveTrackingLineOverlay = null

        val points = getConsecutiveLineCodePoints()
        if (points.size >= 2) {
            fragment.polylineOverlay = OsmdroidPolylineHelper.createPolyline(
                fragment.binding.mapView,
                points.map { it.geoPoint },
                ContextCompat.getColor(fragment.requireContext(), R.color.slate_gray_light),
                6f,
                closed = fragment.isShapeClosed,
                dashed = false
            )
        }
        fragment.binding.mapView.invalidate()
    }

    fun redrawPolylineAsClosed() {
        fragment.isShapeClosed = true
        redrawPolyline()
    }

    fun clearCollectedPoints() {
        fragment.collectedPointMarkers.forEach { fragment.binding.mapView.overlays.remove(it) }
        fragment.collectedPointMarkers.clear()
        fragment.markerToPointMap.clear()

        // Clear caches
        pointMarkersCache.clear()

        fragment.collectedLabeledPoints.clear()
        fragment.polylineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(
                fragment.binding.mapView,
                it
            )
        }; fragment.polylineOverlay = null
        fragment.liveTrackingLineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(
                fragment.binding.mapView,
                it
            )
        }; fragment.liveTrackingLineOverlay = null
        fragment.completedLineOverlays.forEach {
            OsmdroidPolylineHelper.removePolyline(
                fragment.binding.mapView,
                it
            )
        }
        fragment.completedLineOverlays.clear()
        fragment.highlightedLineOverlay = null
        fragment.isShapeClosed = false
        fragment.lineSegmentStartIndex = 0
        fragment.currentLineCodeId = null
        fragment.binding.mapView.invalidate()
    }

    fun updateMarkersForZoom(forceRefresh: Boolean = false) {
        if (fragment.isInBullseyeMode) return // Do not draw markers in Bullseye mode
        // if (fragment.collectedLabeledPoints.isEmpty()) return // Handled by cleanup

        // 1. Identify active points
        val activePointIds = fragment.collectedLabeledPoints.map { it.id }.toSet()

        // 2. Cleanup (Remove deleted points from cache and map)
        val cacheIterator = pointMarkersCache.iterator()
        while (cacheIterator.hasNext()) {
            val entry = cacheIterator.next()
            if (!activePointIds.contains(entry.key)) {
                val cache = entry.value
                // Remove Point Marker
                while (fragment.binding.mapView.overlays.remove(cache.pointMarker)) { /* Remove all instances */
                }
                fragment.collectedPointMarkers.remove(cache.pointMarker)
                fragment.markerToPointMap.remove(cache.pointMarker)

                // Remove Label Marker
                cache.labelMarker?.let {
                    while (fragment.binding.mapView.overlays.remove(it)) { /* Remove all instances */
                    }
                    fragment.collectedPointMarkers.remove(it)
                    fragment.markerToPointMap.remove(it)
                }

                cacheIterator.remove()
            }
        }

        // 3. Calculate Visibility (Collision Detection)
        val projection = fragment.binding.mapView.projection

        // Pre-calculate selection states for performance and consistency
        val selectedPointId = fragment.selectedPoint?.id
        val selectedLinePointIds =
            fragment.highlightedLineOverlay?.labeledPoints?.map { it.id }?.toSet() ?: emptySet()
        val editLinePointIds =
            fragment.currentEditLineAdapter?.getPoints()?.map { it.id }?.toSet() ?: emptySet()
        val newLinePointIds = fragment.newLinePoints.map { it.id }.toSet()

        // Calculate priority indices for collision detection
        val priorityIndices = mutableSetOf<Int>()
        fragment.collectedLabeledPoints.forEachIndexed { index, labeledPoint ->
            val isCurrentLinePoint =
                fragment.currentLineCodeId != null && labeledPoint.codeId == fragment.currentLineCodeId
            val isSelected =
                labeledPoint.id == selectedPointId ||
                        selectedLinePointIds.contains(labeledPoint.id) ||
                        editLinePointIds.contains(labeledPoint.id) ||
                        newLinePointIds.contains(labeledPoint.id)
            val isStakeoutTarget = fragment.currentStakeoutMode != StakeoutMode.NONE &&
                    fragment.stakeoutSession?.targetPoints?.getOrNull(
                        fragment.stakeoutSession?.currentIndex ?: 0
                    )?.id == labeledPoint.id

            if (isCurrentLinePoint || isSelected || isStakeoutTarget) {
                priorityIndices.add(index)
            }
        }

        // Note: For large datasets, this visibility calculation might still be a bottleneck on zoom
        // but it is necessary for decluttering.
        val visibleLabelIndices =
            getVisibleLabelIndices(fragment.collectedLabeledPoints, projection, priorityIndices)

        // 4. Update/Add Loop (Incremental)
        fragment.collectedLabeledPoints.forEachIndexed { index, labeledPoint ->
            val isSelected =
                labeledPoint.id == selectedPointId ||
                        selectedLinePointIds.contains(labeledPoint.id) ||
                        editLinePointIds.contains(labeledPoint.id) ||
                        newLinePointIds.contains(labeledPoint.id)
            val showLabel = visibleLabelIndices.contains(index)

            var cache = pointMarkersCache[labeledPoint.id]
            val overlays = fragment.binding.mapView.overlays

            if (cache == null) {
                // Create New Point Marker
                val bitmap = createPointOnlyBitmap(isSelected = isSelected)
                val marker = OsmdroidMarkerHelper.createMarker(
                    fragment.binding.mapView,
                    bitmap,
                    labeledPoint.geoPoint,
                    0.5f,
                    0.5f
                )
                setupMarkerClickListener(marker)

                cache = PointMarkerCache(marker, null, isSelected, labeledPoint.codeId)
                pointMarkersCache[labeledPoint.id] = cache

                // Add to tracking lists
                fragment.collectedPointMarkers.add(marker)
                fragment.markerToPointMap[marker] = labeledPoint
            } else {
                // Reuse existing Point Marker
                // Update properties if state changed
                if (cache.lastIsSelected != isSelected || cache.lastCodeId != labeledPoint.codeId || forceRefresh) {
                    val bitmap = createPointOnlyBitmap(isSelected = isSelected)
                    cache.pointMarker.icon = BitmapDrawable(fragment.resources, bitmap)
                }

                // Ensure position is correct
                if (cache.pointMarker.position.latitude != labeledPoint.geoPoint.latitude ||
                    cache.pointMarker.position.longitude != labeledPoint.geoPoint.longitude
                ) {
                    cache.pointMarker.position = labeledPoint.geoPoint
                }

                if (!overlays.contains(cache.pointMarker)) {
                    overlays.add(cache.pointMarker)
                } else if (cache.lastIsSelected != isSelected || cache.lastCodeId != labeledPoint.codeId || forceRefresh) {
                    // Force re-add to ensure redraw and correct z-order
                    overlays.remove(cache.pointMarker)
                    overlays.add(cache.pointMarker)
                }

                fragment.markerToPointMap[cache.pointMarker] = labeledPoint
            }

            // Handle Label Marker
            if (showLabel) {
                // REGENERATION CONDITIONS:
                // 1. Label doesn't exist
                // 2. Selection state changed
                // 3. Code changed
                // 4. Label was previously hidden (FORCE REFRESH)
                val stateChanged =
                    cache.lastIsSelected != isSelected || cache.lastCodeId != labeledPoint.codeId || forceRefresh

                if (cache.labelMarker == null || stateChanged || !cache.isLabelShown) {
                    // Create or Update Label Marker
                    if (cache.labelMarker == null) {
                        val (bitmap, anchorY) = createLabeledPointBitmap(
                            labeledPoint.id,
                            labeledPoint.codeId,
                            isSelected = isSelected,
                            drawPoint = false
                        )
                        val marker = OsmdroidMarkerHelper.createMarker(
                            fragment.binding.mapView,
                            bitmap,
                            labeledPoint.geoPoint,
                            0.5f,
                            anchorY
                        )
                        setupMarkerClickListener(marker)
                        cache.labelMarker = marker

                        fragment.collectedPointMarkers.add(marker)
                        fragment.markerToPointMap[marker] = labeledPoint
                    } else {
                        // Update existing label marker
                        val (bitmap, anchorY) = createLabeledPointBitmap(
                            labeledPoint.id,
                            labeledPoint.codeId,
                            isSelected = isSelected,
                            drawPoint = false
                        )
                        cache.labelMarker!!.icon = BitmapDrawable(fragment.resources, bitmap)
                        cache.labelMarker!!.setAnchor(0.5f, anchorY)
                    }
                }

                // Ensure visibility / Position
                val labelM = cache.labelMarker!!
                if (labelM.position.latitude != labeledPoint.geoPoint.latitude ||
                    labelM.position.longitude != labeledPoint.geoPoint.longitude
                ) {
                    labelM.position = labeledPoint.geoPoint
                }

                if (!overlays.contains(labelM)) {
                    overlays.add(labelM)
                } else if (stateChanged) {
                    // Force re-add to bring to front of its layer and ensure redraw
                    overlays.remove(labelM)
                    overlays.add(labelM)
                }
                fragment.markerToPointMap[labelM] = labeledPoint

                // Update cache state for label if it was shown/processed
                cache.isLabelShown = true
            } else {
                // Hide Label Logic
                cache.labelMarker?.let {
                    while (overlays.remove(it)) { /* Remove all instances */
                    }

                    // Failsafe: If it was orange, make it black so it's not a glaring orange ghost
                    if (cache.lastIsSelected == true || forceRefresh) {
                        val (bitmap, anchorY) = createLabeledPointBitmap(
                            labeledPoint.id,
                            labeledPoint.codeId,
                            isSelected = false,
                            drawPoint = false
                        )
                        it.icon = BitmapDrawable(fragment.resources, bitmap)
                        it.setAnchor(0.5f, anchorY)
                    }
                }
                cache.isLabelShown = false
            }

            // Sync final state back to cache
            cache.lastIsSelected = isSelected
            cache.lastCodeId = labeledPoint.codeId
        }

        fragment.binding.mapView.invalidate()

        // Bring selected point to top
        fragment.selectedPoint?.let { point ->
            pointMarkersCache[point.id]?.let { cache ->
                // Move Point Marker to end
                if (fragment.binding.mapView.overlays.contains(cache.pointMarker)) {
                    while (fragment.binding.mapView.overlays.remove(cache.pointMarker)) { /* Remove all */
                    }
                    fragment.binding.mapView.overlays.add(cache.pointMarker)
                }
                // Move Label Marker to end
                cache.labelMarker?.let { label ->
                    if (fragment.binding.mapView.overlays.contains(label)) {
                        while (fragment.binding.mapView.overlays.remove(label)) { /* Remove all */
                        }
                        fragment.binding.mapView.overlays.add(label)
                    }
                }
            }
        }

        bringLocationMarkerToTop()
        ensurePointClickHandlerAtEnd()
        fragment.binding.mapView.invalidate()
    }

    private fun handlePointClick(point: LabeledPoint): Boolean {
        // Block all map interactions when Select Code or Collect Point sheets are open
        if (fragment.binding.bottomSheetSelectCode.root.visibility == View.VISIBLE ||
            fragment.binding.bottomSheetCollectPoint.root.visibility == View.VISIBLE) {
            return true
        }

        // New Line Creation Mode Check (Robust: Flag OR UI Visibility)
        if (fragment.isCreatingNewLine || fragment.binding.bottomSheetNewLine.root.visibility == View.VISIBLE) {
            addPointToNewLine(point)
            return true
        }

        // Edit Line Mode Check - Block point selection when edit line is open
        if (fragment.isSelectingPointForEditLine || fragment.currentEditLineAdapter != null ||
            fragment.binding.bottomSheetEditLine.root.visibility == View.VISIBLE
        ) {
            addPointToEditLine(point)
            return true
        }

        // If in stakeout mode, hide its UI temporarily
        if (fragment.currentStakeoutMode != StakeoutMode.NONE) {
            fragment.helper.hideStakeoutUI(showNav = false)
        }

        showPointDetailsBottomSheet(point)
        return true
    }

    private fun setupMarkerClickListener(marker: Marker) {
        marker.setOnMarkerClickListener { clickedMarker: Marker, _: org.osmdroid.views.MapView ->
            fragment.markerToPointMap[clickedMarker]?.let { point ->
                handlePointClick(point)
            } ?: true
        }
    }

    fun bringLabelsToTop() {
        val markers = fragment.binding.mapView.overlays.filterIsInstance<Marker>()
        if (markers.isNotEmpty()) {
            val nonMarkers = fragment.binding.mapView.overlays.filter { it !is Marker }
            fragment.binding.mapView.overlays.clear()
            fragment.binding.mapView.overlays.addAll(nonMarkers)
            fragment.binding.mapView.overlays.addAll(markers)
        }
    }

    fun bringLocationMarkerToTop() {
        fragment.locationMarker?.let {
            fragment.binding.mapView.overlays.remove(it); fragment.binding.mapView.overlays.add(
            it
        )
        }
    }

    /**
     * Add a polyline overlay below all markers in the z-order.
     * This ensures lines render below points as per Figma design.
     */
    private fun addPolylineBelowMarkers(overlay: org.osmdroid.views.overlay.Overlay) {
        val overlays = fragment.binding.mapView.overlays
        val firstMarkerIndex = overlays.indexOfFirst { it is Marker }
        if (firstMarkerIndex >= 0) {
            overlays.add(firstMarkerIndex, overlay)
        } else {
            overlays.add(overlay)
        }
    }

    fun getVisibleLabelIndices(
        points: List<LabeledPoint>,
        projection: org.osmdroid.views.Projection,
        priorityIndices: Set<Int> = emptySet()
    ): Set<Int> {
        if (fragment.binding.mapView.zoomLevelDouble >= fragment.binding.mapView.maxZoomLevel - 0.1) return points.indices.toSet()
        val visible = mutableSetOf<Int>()
        val pixelPoints = mutableListOf<Point>()
        val dens = fragment.resources.displayMetrics.density
        val oX = fragment.labelOverlapDistanceX * dens
        val oY = fragment.labelOverlapDistanceY * dens
        val buf = 100 * dens

        // Pass 1: Process Priority Indices
        priorityIndices.forEach { i ->
            if (i in points.indices) {
                val p = Point(); projection.toPixels(points[i].geoPoint, p)
                // Priority points (Selected, etc.) always show their label if they are even remotely near the screen
                // We ignore the actual width/height bounds for priority points to avoid "width=0" issues on first load
                visible.add(i)
                pixelPoints.add(p)
            }
        }

        // Pass 2: Process Remaining Indices
        points.indices.forEach { i ->
            if (priorityIndices.contains(i)) return@forEach // Skip already processed

            val p = Point(); projection.toPixels(points[i].geoPoint, p)
            val w = fragment.binding.mapView.width
            val h = fragment.binding.mapView.height

            // If width/height is 0 (first load), skip bounds check for non-priority too to show something
            if (w > 0 && h > 0) {
                if (p.x < -buf || p.x > w + buf || p.y < -buf || p.y > h + buf) {
                    // Offscreen
                    return@forEach
                }
            }
            if (pixelPoints.none { Math.abs(p.x - it.x) < oX && Math.abs(p.y - it.y) < oY }) {
                visible.add(i); pixelPoints.add(p)
            }
        }
        return visible
    }

    fun createPointOnlyBitmap(isSelected: Boolean = false): Bitmap {
        val dens = fragment.resources.displayMetrics.density
        val r = 6 * dens
        val s = (r * 2).toInt()
        val bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canv = Canvas(bm)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) ContextCompat.getColor(
                fragment.requireContext(),
                R.color.primary
            ) else ContextCompat.getColor(
                fragment.requireContext(),
                R.color.stakeout_connection_line
            ); style = Paint.Style.FILL
        }
        canv.drawCircle(s / 2f, s / 2f, r, p)
        if (isSelected) canv.drawCircle(
            s / 2f,
            s / 2f,
            r / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL })
        return bm
    }

    fun createLabeledPointBitmap(
        id: String,
        code: String,
        isSelected: Boolean = false,
        drawPoint: Boolean = true
    ): Pair<Bitmap, Float> {
        val dens = fragment.resources.displayMetrics.density
        val pad = 8 * dens
        val idSz = 12 * dens
        val cdSz = 10 * dens
        val r = 6 * dens
        val spc = 4 * dens
        val strk = 3 * dens
        val prim = ContextCompat.getColor(fragment.requireContext(), R.color.primary)
        val idP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = idSz; typeface = Typeface.DEFAULT_BOLD; color =
            if (isSelected) prim else Color.BLACK
        }
        val cdP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = cdSz; color = if (isSelected) prim else ContextCompat.getColor(
            fragment.requireContext(),
            R.color.text_secondary
        )
        }
        val idSP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = idSz; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE; style =
            Paint.Style.STROKE; strokeWidth = strk; strokeJoin = Paint.Join.ROUND
        }
        val idRect = Rect(); idP.getTextBounds(id, 0, id.length, idRect)
        val cdRect = Rect(); cdP.getTextBounds(code, 0, code.length, cdRect)
        val w = (maxOf(idRect.width(), cdRect.width()) + pad * 2 + strk * 2).toInt()
        val h = (pad * 2 + idRect.height() + cdRect.height() + r * 2 + spc * 2).toInt()
        val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canv = Canvas(bm)
        val x = w / 2f
        val idY = pad + idRect.height().toFloat(); canv.drawText(
            id,
            (w - idRect.width()) / 2f,
            idY,
            idSP
        ); canv.drawText(id, (w - idRect.width()) / 2f, idY, idP)
        val cdSP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = cdSz; color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = strk
            strokeJoin = Paint.Join.ROUND
        }
        val pY = idY + spc + r
        if (drawPoint) {
            canv.drawCircle(
                x,
                pY,
                r,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (isSelected) prim else ContextCompat.getColor(
                        fragment.requireContext(),
                        R.color.stakeout_connection_line
                    )
                })
            if (isSelected) canv.drawCircle(
                x,
                pY,
                r / 2f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        }
        val cdY = pY + r + spc + cdRect.height()
        canv.drawText(code, (w - cdRect.width()) / 2f, cdY, cdSP)
        canv.drawText(
            code,
            (w - cdRect.width()) / 2f,
            cdY,
            cdP
        )
        return bm to (pY / h)
    }

    fun setupZoomControls() {
        fragment.binding.imgZoomIn.setOnClickListener {
            hideLineSegmentMenuIfVisible()
            animateZoom(
                fragment.binding.mapView.zoomLevelDouble,
                minOf(
                    fragment.binding.mapView.zoomLevelDouble + 1,
                    fragment.binding.mapView.maxZoomLevel
                )
            )
        }
        fragment.binding.imgZoomOut.setOnClickListener {
            hideLineSegmentMenuIfVisible()
            animateZoom(
                fragment.binding.mapView.zoomLevelDouble,
                maxOf(
                    fragment.binding.mapView.zoomLevelDouble - 1,
                    fragment.binding.mapView.minZoomLevel
                )
            )
        }
    }

    fun animateZoom(from: Double, to: Double) {
        fragment.currentMapAnimator?.cancel()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 150; interpolator = DecelerateInterpolator()
            addUpdateListener { fragment.binding.mapView.controller.setZoom(from + (to - from) * (it.animatedValue as Float)) }
            fragment.currentMapAnimator = this; start()
        }
    }

    fun updateCompassRotation() {
        fragment.binding.imgCompass.rotation = fragment.binding.mapView.mapOrientation
    }

    fun setupCompassButton() {
        fragment.binding.imgCompass.setOnClickListener {
            hideLineSegmentMenuIfVisible()
            animateRotationTo(0f)
        }
    }

    fun animateRotationTo(target: Float) {
        val start = fragment.binding.mapView.mapOrientation
        var fStart = start % 360f
        var fEnd = target % 360f
        if (fEnd - fStart > 180) fEnd -= 360 else if (fEnd - fStart < -180) fEnd += 360
        ValueAnimator.ofFloat(fStart, fEnd).apply {
            duration = 500
            addUpdateListener {
                val v =
                    it.animatedValue as Float; fragment.binding.mapView.mapOrientation =
                v; fragment.binding.imgCompass.rotation =
                v
            }
            start(); fragment.rotationGestureOverlay?.resetRotation()
        }
    }

    fun setupCenterButton() {
        fragment.binding.imgCenter.setOnClickListener {
            hideLineSegmentMenuIfVisible()
            if (fragment.currentStakeoutMode != StakeoutMode.NONE) return@setOnClickListener

            (fragment.currentLocation ?: fragment.locationMarker?.position)?.let { loc ->
                if (fragment.collectedLabeledPoints.isEmpty()) {
                    val rot = fragment.binding.mapView.mapOrientation
                    cancelOngoingAnimations()
                    fragment.binding.mapView.zoomToBoundingBox(
                        BoundingBox(
                            loc.latitude + 0.0001,
                            loc.longitude + 0.0001,
                            loc.latitude - 0.0001,
                            loc.longitude - 0.0001
                        ), true, 240, fragment.binding.mapView.maxZoomLevel, 400L
                    )
                    fragment.binding.mapView.postDelayed({
                        fragment.binding.mapView.mapOrientation =
                            rot; fragment.binding.imgCompass.rotation = rot
                    }, 50)
                } else animateToLocationWithZoom(loc, fragment.binding.mapView.zoomLevelDouble)
            }
        }
    }

    private var lastCollectClickTime: Long = 0

    fun setupCollectButton() {
        fragment.binding.btnCollect.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCollectClickTime > 500) {
                lastCollectClickTime = currentTime
                if (fragment.binding.bottomSheetCollectPoint.root.visibility == View.VISIBLE) hideCollectPointBottomSheet()
                else showCollectPointBottomSheet()
            }
        }
        setupSwipeGestureForDataCollectionSettings(
            fragment.binding.bottomSheetCollectPoint.scrollContent,
            fragment.binding.bottomSheetCollectPoint
        )
    }

    fun setupResizeButton() {
        fragment.binding.imgResize.setOnClickListener {
            hideLineSegmentMenuIfVisible()
            fitMapToPoints()
        }
    }

    private fun fitMapToPoints() {
        // if (fragment.isMapFitted) return // isMapFitted logic not fully ported/visible here, skipping check to force fit

        cancelOngoingAnimations()

        val locationPoint = fragment.currentLocation ?: fragment.locationMarker?.position

        if (fragment.collectedLabeledPoints.isEmpty() && locationPoint == null) return

        val currentRotation = fragment.binding.mapView.mapOrientation

        var minLat = Double.MAX_VALUE
        var maxLat = Double.MIN_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = Double.MIN_VALUE

        fragment.collectedLabeledPoints.forEach { point ->
            val lat = point.geoPoint.latitude
            val lon = point.geoPoint.longitude
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
        }

        locationPoint?.let { location ->
            val lat = location.latitude
            val lon = location.longitude
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
        }

        // Handle case where we only have one point essentially
        if (minLat == Double.MAX_VALUE) return // No points
        if (minLat == maxLat && minLon == maxLon) {
            // Single point logic
            animateToLocationWithZoom(
                GeoPoint(minLat, minLon),
                fragment.binding.mapView.maxZoomLevel
            )
            return
        }

        val boundingBox = BoundingBox(maxLat, maxLon, minLat, minLon)
        val padding = 240

        fragment.binding.mapView.post {
            fragment.binding.mapView.zoomToBoundingBox(
                boundingBox,
                true,
                padding,
                fragment.binding.mapView.maxZoomLevel,
                400L
            )
            fragment.binding.mapView.postDelayed({
                fragment.binding.mapView.mapOrientation = currentRotation
                fragment.binding.imgCompass.rotation = currentRotation
            }, 50)
        }
    }

    fun setupMenuButton() {
        fragment.binding.imgMenu.setOnClickListener {
            if (fragment.binding.clMenu.visibility == View.VISIBLE) {
                // Menu is already open, so just hide it
                hideMenu()
            } else {
                // Menu is closed, check for bottom sheets first
                if (isAnyBottomSheetVisible()) {
                    hideAnyVisibleBottomSheet {
                        showMenu()
                    }
                } else {
                    showMenu()
                }
            }
        }


        fragment.binding.llObjectList.setOnClickListener {
            if (fragment.binding.clMenu.visibility == View.VISIBLE) {
                fragment.binding.clMenu.visibility = View.GONE
            }

            if (fragment.binding.bottomSheetLineSegment.root.visibility == View.VISIBLE) {
                fragment.highlightedLineOverlay?.unhighlight()
                fragment.highlightedLineOverlay = null
                fragment.wasCollectingBeforePointDetails = false
                fragment.selectedPoint = null
                updateMarkersForZoom()
                hideLineSegmentDetailsBottomSheet(clearState = false, showNav = false)
                showObjectListBottomSheet()
            } else if (fragment.binding.bottomSheetCollectPoint.root.visibility == View.VISIBLE) {
                attemptToExitCollectionMode(showNav = false) {
                    showObjectListBottomSheet()
                }
            } else {
                showObjectListBottomSheet()
            }
        }

        fragment.binding.llProjectDetails.setOnClickListener {
            if (fragment.binding.clMenu.visibility == View.VISIBLE) {
                fragment.binding.clMenu.visibility = View.GONE
            }
            // Project details logic pending
        }
    }

    fun cancelOngoingAnimations() {
        fragment.currentMapAnimator?.cancel(); fragment.currentMapAnimator = null
        fragment.isAnimatingLocation = false; fragment.locationUpdateHandler.removeCallbacks(
            fragment.locationSmoothingRunnable
        )
        fragment.binding.mapView.controller.stopAnimation(false)
    }

    fun animateToLocationWithZoom(loc: GeoPoint, zoom: Double) {
        cancelOngoingAnimations()
        val sLat = fragment.binding.mapView.mapCenter.latitude
        val sLon = fragment.binding.mapView.mapCenter.longitude
        val sZ = fragment.binding.mapView.zoomLevelDouble
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400; interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = it.animatedValue as Float
                fragment.binding.mapView.controller.setCenter(
                    GeoPoint(
                        sLat + (loc.latitude - sLat) * f,
                        sLon + (loc.longitude - sLon) * f
                    )
                )
                fragment.binding.mapView.controller.setZoom(sZ + (zoom - sZ) * f)
            }
            fragment.currentMapAnimator = this; start()
        }
    }

    private fun zoomToLine(ls: ClickablePolylineOverlay) {
        val points = ls.labeledPoints
        if (points.isEmpty()) return

        var minLat = Double.MAX_VALUE
        var maxLat = Double.MIN_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = Double.MIN_VALUE

        points.forEach { point ->
            val lat = point.geoPoint.latitude
            val lon = point.geoPoint.longitude
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
        }

        if (minLat == Double.MAX_VALUE) return

        if (minLat == maxLat && minLon == maxLon) {
            animateToLocationWithZoom(
                GeoPoint(minLat, minLon),
                fragment.binding.mapView.maxZoomLevel
            )
            return
        }

        val boundingBox = BoundingBox(maxLat, maxLon, minLat, minLon)
        val padding = 240

        fragment.binding.mapView.post {
            fragment.binding.mapView.zoomToBoundingBox(
                boundingBox,
                true,
                padding,
                fragment.binding.mapView.maxZoomLevel,
                400L
            )
        }
    }

    private fun isAnyBottomSheetVisible(): Boolean {
        return listOf(
            fragment.binding.bottomSheetCollectPoint.root,
            fragment.binding.bottomSheetLineSegment.root,
            fragment.binding.bottomSheetEditLine.root,
            fragment.binding.bottomSheetEditPoint.root,
            fragment.binding.bottomSheetNewLine.root,
            fragment.binding.bottomSheetNewPoint.root,
            fragment.binding.bottomSheetSelectCode.root,
            fragment.binding.bottomSheetObjectList.root,
            fragment.binding.stakeoutBottomSheet.root
        ).any { it.visibility == View.VISIBLE }
    }

    private fun hideAnyVisibleBottomSheet(onHidden: (() -> Unit)? = null) {
        val visibleSheets = listOf(
            fragment.binding.bottomSheetCollectPoint.root,
            fragment.binding.bottomSheetLineSegment.root,
            fragment.binding.bottomSheetEditLine.root,
            fragment.binding.bottomSheetEditPoint.root,
            fragment.binding.bottomSheetNewLine.root,
            fragment.binding.bottomSheetNewPoint.root,
            fragment.binding.bottomSheetSelectCode.root,
            fragment.binding.bottomSheetObjectList.root,
            fragment.binding.stakeoutBottomSheet.root
        ).filter { it.visibility == View.VISIBLE }

        if (visibleSheets.isEmpty()) {
            onHidden?.invoke()
            return
        }

        // We only expect one bottom sheet to be visible at a time based on the app's logic
        // But we'll handle all anyway.
        var hiddenCount = 0
        val totalToHide = visibleSheets.size

        fun checkAllHidden() {
            hiddenCount++
            if (hiddenCount == totalToHide) {
                onHidden?.invoke()
            }
        }

        visibleSheets.forEach { sheet: View ->
            when (sheet.id) {
                fragment.binding.bottomSheetCollectPoint.root.id -> hideCollectPointBottomSheet(onHidden = { checkAllHidden() })
                fragment.binding.bottomSheetLineSegment.root.id -> hideLineSegmentDetailsBottomSheet(onHidden = { checkAllHidden() })
                fragment.binding.bottomSheetEditLine.root.id -> hideEditLineBottomSheet(onHidden = { checkAllHidden() })
                fragment.binding.bottomSheetEditPoint.root.id -> hideEditPointBottomSheet(onHidden = { checkAllHidden() })
                fragment.binding.bottomSheetNewLine.root.id -> hideNewLineBottomSheet(onHidden = { checkAllHidden() })
                fragment.binding.bottomSheetNewPoint.root.id -> hideNewPointBottomSheet(onHidden = { checkAllHidden() })
                fragment.binding.bottomSheetSelectCode.root.id -> hideSelectCodeBottomSheet(onHidden = { checkAllHidden() })
                fragment.binding.bottomSheetObjectList.root.id -> hideObjectListBottomSheet(onHidden = { checkAllHidden() })
                fragment.binding.stakeoutBottomSheet.root.id -> fragment.helper.hideStakeoutUI(showNav = true, onHidden = { checkAllHidden() })
                else -> checkAllHidden()
            }
        }
    }

    private fun showMenu() {
        fragment.binding.clMenu.visibility = View.VISIBLE
        fragment.binding.clMenu.alpha = 0f
        fragment.binding.clMenu.animate().alpha(1f).setDuration(200).start()
    }

    fun hideMenu(onHidden: (() -> Unit)? = null) {
        if (fragment.binding.clMenu.visibility != View.VISIBLE) {
            onHidden?.invoke()
            return
        }
        fragment.binding.clMenu.animate().alpha(0f).setDuration(200).withEndAction {
            fragment.binding.clMenu.visibility = View.GONE
            onHidden?.invoke()
        }.start()
    }

    private fun isAnyBottomSheetOpenExcludingLineSegment(): Boolean {
        // A sheet is considered "open" if it is visible, has alpha > 0, AND is not animating out (translationY == 0)
        // Note: clMenu is intentionally NOT included here as it should not block bottom navigation
        return listOf(
            fragment.binding.bottomSheetCollectPoint.root,
            fragment.binding.bottomSheetEditLine.root,
            fragment.binding.bottomSheetNewPoint.root,
            fragment.binding.bottomSheetSelectCode.root,
            fragment.binding.bottomSheetObjectList.root,
            fragment.binding.bottomSheetNewLine.root
        ).any { it.visibility == View.VISIBLE && it.alpha > 0f && it.translationY == 0f }
    }

    private fun isAnyInfoSheetOpen(): Boolean {
        return listOf(
            fragment.binding.bottomSheetLineSegment.root,
            fragment.binding.bottomSheetEditPoint.root,
            fragment.binding.bottomSheetEditLine.root,
            fragment.binding.bottomSheetObjectList.root,
            fragment.binding.bottomSheetNewPoint.root,
            fragment.binding.bottomSheetNewLine.root,
            fragment.binding.bottomSheetCollectPoint.root
        ).any { it.visibility == View.VISIBLE && it.alpha > 0f }
    }

    internal fun restoreStateAfterClosingInfoSheet() {
        // 🛑 CRITICAL FIX FOR HANG STATE: Always re-enable map touch explicitly when returning to base state
        fragment.binding.mapView.setMultiTouchControls(true)
        fragment.binding.mapView.overlays.filterIsInstance<org.osmdroid.views.overlay.gestures.RotationGestureOverlay>()
            .forEach { it.isEnabled = true }
        fragment.binding.mapView.setOnTouchListener(null)
        fragment.binding.llMapsButtons.visibility = View.VISIBLE

        if (fragment.stakeoutSession != null) {
            fragment.helper.showStakeoutUI()
            // Force measurement update to restore visuals (lines, circles, etc.)
            fragment.currentLocation?.let {
                updateStakeoutMeasurements(
                    it.latitude,
                    it.longitude,
                    it.altitude
                )
            }
        } else if (fragment.isSelectingPointForEditLine && fragment.pendingEditLineSegment != null) {
            showEditLineBottomSheet(fragment.pendingEditLineSegment!!)
        } else if (fragment.binding.bottomSheetEditLine.root.visibility == View.VISIBLE || fragment.binding.bottomSheetNewLine.root.visibility == View.VISIBLE) {
            // Don't show bottom nav if Edit Line or New Line sheets are visible
            hideBottomNavigation()
        } else {
            showBottomNavigation()
        }
    }

    fun hideBottomNavigation(onEnd: (() -> Unit)? = null) {
        isNavHidden = true
        // Hide bottom navigation so sheets don't overlap it
        (fragment.activity as? MainActivity)?.binding?.bottomNavigationView?.apply {
            animate().cancel()
            visibility = View.GONE
            alpha = 0f
        }
        // Only handle stakeout UI if needed
        if (fragment.currentStakeoutMode != StakeoutMode.NONE) {
            fragment.helper.hideStakeoutUI(showNav = false)
        }
        onEnd?.invoke()
    }

    fun showBottomNavigation(force: Boolean = false) {
        // Don't show bottom nav if any input sheets are visible
        val isSheetOpen = fragment.binding.bottomSheetEditLine.root.visibility == View.VISIBLE ||
                fragment.binding.bottomSheetNewLine.root.visibility == View.VISIBLE ||
                fragment.binding.bottomSheetCollectPoint.root.visibility == View.VISIBLE ||
                fragment.binding.bottomSheetNewPoint.root.visibility == View.VISIBLE

        if (isSheetOpen && !force) {
            return
        }
        isNavHidden = false
        // Bottom nav is always visible; just reset UI element positions
        (fragment.activity as? MainActivity)?.binding?.bottomNavigationView?.apply {
            animate().cancel()
            visibility = View.VISIBLE
            alpha = 1f
        }

        fragment.binding.btnCollect.animate().cancel()
        fragment.binding.btnCollect.translationY = -bottomNavOffset

        fragment.binding.llMapsButtons.animate().cancel()
        fragment.binding.llMapsButtons.translationY = 0f

        // If Bottom Sheet Line Segment is VISIBLE, ensure it has the margin to float above nav
        if (fragment.binding.bottomSheetLineSegment.root.visibility == View.VISIBLE && !isSheetOpen) {
            fragment.binding.bottomSheetLineSegment.root.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
                bottomMargin = bottomNavOffset.toInt()
            }
        }
    }

    fun setupPointClickHandler() {
        val protected = mutableListOf<View>()
        fun collect(v: View) {
            protected.add(v); if (v is ViewGroup) (0 until v.childCount).forEach {
                collect(
                    v.getChildAt(
                        it
                    )
                )
            }
        }
        listOf(
            fragment.binding.clMenu,
            fragment.binding.llRightPanel,
            fragment.binding.imgBack,
            fragment.binding.imgMenu,
            fragment.binding.btnCollect,
            fragment.binding.bottomSheetLineSegment.root,
            fragment.binding.bottomSheetEditLine.root,
            fragment.binding.bottomSheetCollectPoint.root,
            fragment.binding.bottomSheetNewPoint.root,
            fragment.binding.bottomSheetSelectCode.root,
            fragment.binding.bottomSheetObjectList.root
        ).forEach { it?.let { v -> collect(v) } }
        fragment.pointClickHandlerOverlay =
            PointClickHandlerOverlay(onPointClick = { geoPoint ->
                val nearest = findNearestPoint(geoPoint)
                nearest?.let { handlePointClick(it) } ?: false
            }, protectedViews = protected)
        ensurePointClickHandlerAtEnd()
    }

    fun preventDoubleTapZoomOnNonMapViews() {
        val protected = mutableListOf<View>()
        fun collect(v: View) {
            protected.add(v); if (v is ViewGroup) (0 until v.childCount).forEach {
                collect(
                    v.getChildAt(
                        it
                    )
                )
            }
        }
        listOf(
            fragment.binding.clMenu,
            fragment.binding.llRightPanel,
            fragment.binding.imgBack,
            fragment.binding.imgMenu,
            fragment.binding.btnCollect,
            fragment.binding.bottomSheetLineSegment.root,
            fragment.binding.bottomSheetEditLine.root,
            fragment.binding.bottomSheetCollectPoint.root,
            fragment.binding.bottomSheetNewPoint.root,
            fragment.binding.bottomSheetSelectCode.root,
            fragment.binding.bottomSheetObjectList.root
        ).forEach { it?.let { v -> collect(v) } }

        fragment.doubleTapInterceptorOverlay = DoubleTapInterceptorOverlay(protected)
        fragment.binding.mapView.overlays.add(fragment.doubleTapInterceptorOverlay)
    }

    fun ensurePointClickHandlerAtEnd() {
        fragment.pointClickHandlerOverlay?.let {
            fragment.binding.mapView.overlays.remove(it); fragment.binding.mapView.overlays.add(
            it
        )
        }
    }

    fun findNearestPoint(geo: GeoPoint): LabeledPoint? {
        val proj = fragment.binding.mapView.projection
        val p = Point(); proj.toPixels(geo, p)
        val density = fragment.resources.displayMetrics.density
        val pointTol = 24f * density // 24dp tolerance (48dp touch target) for precision
        var best: LabeledPoint? = null
        var minD = Float.MAX_VALUE

        // Find nearest point
        fragment.collectedLabeledPoints.forEach { pt ->
            // Prevent selection of "ghost" points hidden by zoom clustering
            val isVisible = pointMarkersCache[pt.id]?.pointMarker?.isEnabled ?: true
            if (!isVisible) return@forEach

            val pp = Point(); proj.toPixels(pt.geoPoint, pp)
            val dx = p.x - pp.x
            val dy = p.y - pp.y
            val d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (d < minD) {
                minD = d; best = pt
            }
        }

        if (best != null) {
            var minLineDist = Float.MAX_VALUE
            // Find nearest line
            fragment.completedLineOverlays.forEach { overlay ->
                if (overlay is ClickablePolylineOverlay) {
                    val dist = overlay.distanceToPolyline(
                        fragment.binding.mapView,
                        p.x.toFloat(),
                        p.y.toFloat()
                    )
                    if (dist < minLineDist) {
                        minLineDist = dist
                    }
                }
            }

            // SMART SELECTION LOGIC
            
            // Case 1: Point is a solid/direct hit (within 24dp)
            // We ALWAYS prefer the point, UNLESS the line is substantially closer.
            // Example: tap is 23dp from point, but 2dp from line -> pick line.
            if (minD <= 24f * density) {
                if (minLineDist <= 10f * density && minD > minLineDist + 10f * density) {
                    return null // User definitively hit the line, let line overlay handle it
                }
                return best
            }

            // Case 2: Point is a sloppy hit (between 25dp and 35dp)
            // We pick the point only if the line isn't closer.
            if (minD <= pointTol) {
                if (minLineDist < minD) {
                    return null // Line is closer, let line overlay handle it
                }
                return best
            }
        }

        return null
    }


    fun updateLiveTrackingLine() {
        fragment.liveTrackingLineOverlay?.let {
            OsmdroidPolylineHelper.removePolyline(fragment.binding.mapView, it)
        }
        fragment.liveTrackingLineOverlay = null

        // Fix: Prevent tracking line from appearing during manual new line creation
        // or if no line is actively being collected
        if (fragment.isCreatingNewLine || fragment.currentLineCodeId == null) {
            return
        }

        val lineCodePoints = getConsecutiveLineCodePoints()

        if (lineCodePoints.isNotEmpty()) {
            val referencePoint = if (fragment.addFromBeginning) {
                lineCodePoints.first().geoPoint
            } else {
                lineCodePoints.last().geoPoint
            }
            val currentMarkerPosition =
                fragment.locationMarker?.position ?: fragment.currentLocation

            if (currentMarkerPosition != null && referencePoint != currentMarkerPosition && !fragment.isShapeClosed) {
                val primaryColor =
                    ContextCompat.getColor(fragment.requireContext(), R.color.primary)
                fragment.liveTrackingLineOverlay = OsmdroidPolylineHelper.createPolyline(
                    fragment.binding.mapView,
                    listOf(referencePoint, currentMarkerPosition),
                    primaryColor,
                    6f,
                    closed = false,
                    dashed = true
                )
            }
        }

        bringLabelsToTop()
        bringLocationMarkerToTop()
        ensurePointClickHandlerAtEnd()
    }

    fun initializeMap() {
        fragment.binding.mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); minZoomLevel =
            2.0; maxZoomLevel = 25.0; isHorizontalMapRepetitionEnabled =
            true; isVerticalMapRepetitionEnabled = false; isTilesScaledToDpi =
            true; setBuiltInZoomControls(false)
            setScrollableAreaLimitDouble(BoundingBox(85.0, 180.0, -85.0, -180.0))
            addMapListener(object : MapListener {
                override fun onScroll(e: ScrollEvent?): Boolean {
                    fragment.isMapFitted = false; updateCompassRotation(); return false
                }

                override fun onZoom(e: ZoomEvent?): Boolean {
                    fragment.isMapFitted = false
                    e?.let {
                        if (Math.abs(it.zoomLevel - fragment.lastZoomLevel) > 0.5) {
                            fragment.lastZoomLevel = it.zoomLevel; post { updateMarkersForZoom() }
                        }
                    }
                    updateCompassRotation(); return false
                }
            })
        }
        fragment.mapController =
            fragment.binding.mapView.controller.apply { setZoom(15.0) }; fragment.lastZoomLevel =
            15.0
        fragment.rotationGestureOverlay = RotationGestureOverlay(fragment.binding.mapView).also {
            fragment.binding.mapView.overlays.add(
                0,
                it
            )
        }
        createLocationPin()
    }


    fun setupSwipeToDismiss(view: View, onDismiss: () -> Unit) {
        view.isClickable = true // Ensure root consumes touches to see the full gesture
        var initialY = 0f
        var initialX = 0f
        var isDragging = false
        var startTranslationY = 0f
        var viewHeight = 0f

        val gestureListener = View.OnTouchListener(fun(v: View, event: MotionEvent): Boolean {
            // If the view can scroll up (i.e. we are not at the top), let it handle the event
            if (v.canScrollVertically(-1) && !isDragging) return false

            return when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    initialX = event.rawX
                    startTranslationY = view.translationY
                    viewHeight = view.height.toFloat()
                    isDragging = false
                    // Cancel any ongoing animations
                    view.animate().cancel()
                    false // Allow click propagation initially
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - initialY
                    val deltaX = event.rawX - initialX

                    // Start dragging if vertical movement is detected
                    if (!isDragging && Math.abs(deltaY) > 10f && Math.abs(deltaY) > Math.abs(deltaX)) {
                        isDragging = true
                        // Request parent to not intercept touch events
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    if (isDragging) {
                        // Calculate new translation - follow finger exactly
                        // Allow dragging both up (negative) and down (positive)
                        val newTranslation = startTranslationY + deltaY

                        // Allow dragging down up to 1% of the view height for bounce effect
                        val minTranslation = 0f // No upward overscroll
                        val maxTranslation = viewHeight * 0.01f // Max drag down is 1%

                        view.translationY = newTranslation.coerceIn(minTranslation, maxTranslation)
                        return true // Consume the event while dragging
                    }
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)

                    if (isDragging) {
                        val currentTranslation = view.translationY

                        // If pulled down (positive translation), bounce back
                        if (currentTranslation > 0) {
                            view.animate()
                                .translationY(0f)
                                .setDuration(400)
                                .setInterpolator(OvershootInterpolator(2.0f))
                                .start()
                        } else {
                            // ALWAYS snap back to fully open position, disabling swipe-to-dismiss
                            view.animate()
                                .translationY(0f)
                                .setDuration(150)
                                .setInterpolator(FastOutSlowInInterpolator())
                                .start()
                        }

                        isDragging = false
                        return true
                    }
                    isDragging = false
                    false
                }

                else -> false
            }
        })

        fun attachListenerRecursively(v: View) {
            // Attach to everything to enable swipe everywhere
            // Internal scrolling is handled by the check at the start of onTouch
            if (v is RecyclerView ||
                v is android.widget.ScrollView ||
                v is androidx.core.widget.NestedScrollView
            ) {
                return
            }
            v.setOnTouchListener(gestureListener)
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    attachListenerRecursively(v.getChildAt(i))
                }
            }
        }

        attachListenerRecursively(view)
    }

    fun setupLocationTracking() {
        fragment.fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(fragment.requireActivity())
        fragment.locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setMinUpdateIntervalMillis(500).build()
        fragment.locationCallback = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                res.lastLocation?.let {
                    updateLocationMarker(it.latitude, it.longitude, it.altitude)

                    // Stakeout Update - Handled in smoothMoveToTarget for sync
                    if (fragment.stakeoutSession != null && fragment.isFirstLocationUpdate) {
                        // Fallback for first update or if not animating
                        updateStakeoutMeasurements(it.latitude, it.longitude, it.altitude)
                    }
                    // Mock GNSS Status for now or extract from extras if available

                    if (fragment.isFirstLocationUpdate) {
                        fragment.isFirstLocationUpdate = false
                        animateToLocationWithZoom(
                            GeoPoint(it.latitude, it.longitude, it.altitude),
                            18.0
                        )
                    } else if (fragment.isLockMode) {
                        smoothMoveToTarget()
                    }
                }
            }
        }
        try {
            fragment.fusedLocationClient?.requestLocationUpdates(
                fragment.locationRequest!!,
                fragment.locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
        }
    }

    fun stopLocationUpdates() {
        fragment.fusedLocationClient?.removeLocationUpdates(fragment.locationCallback!!)
    }

    fun updateLocationMarker(lat: Double, lon: Double, alt: Double) {
        val newLocation = GeoPoint(lat, lon, alt)

        if (fragment.locationMarker == null) {
            fragment.currentLocation = newLocation
            fragment.targetLocation = newLocation
            createLocationPin()
            fragment.locationMarker?.position = newLocation

            if (fragment.isFirstLocationUpdate && fragment.mapController != null) {
                fragment.binding.mapView.post { fitMapToPoints() }
                fragment.isFirstLocationUpdate = false
            }
            return
        }

        if (fragment.isFirstLocationUpdate && fragment.mapController != null) {
            fragment.currentLocation = newLocation
            fragment.targetLocation = newLocation
            fragment.locationMarker?.position = newLocation
            fragment.binding.mapView.post { fitMapToPoints() }
            fragment.isFirstLocationUpdate = false
            return
        }

        fragment.targetLocation = newLocation
        // fragment.currentLocation = newLocation // Handled in smoothMoveToTarget for sync

        if (getConsecutiveLineCodePoints().isNotEmpty()) {
            updateLiveTrackingLine()
        }

        bringLocationMarkerToTop()

        if (!fragment.isAnimatingLocation) {
            fragment.isAnimatingLocation = true
            fragment.locationUpdateHandler.post(fragment.locationSmoothingRunnable)
        }
    }

    fun smoothMoveToTarget() {
        val target = fragment.targetLocation ?: return
        val marker = fragment.locationMarker ?: return
        val currentPos = marker.position ?: return

        val smoothingFactor = 0.15

        val newLat = currentPos.latitude + (target.latitude - currentPos.latitude) * smoothingFactor
        val newLon =
            currentPos.longitude + (target.longitude - currentPos.longitude) * smoothingFactor
        val newAlt = currentPos.altitude + (target.altitude - currentPos.altitude) * smoothingFactor

        val smoothedPosition = GeoPoint(newLat, newLon, newAlt)
        marker.position = smoothedPosition
        fragment.currentLocation =
            smoothedPosition // Update currentLocation to match smoothed for distance calcs

        if (fragment.stakeoutSession != null) {
            updateStakeoutMeasurements(newLat, newLon, newAlt)
        }

        updateLiveTrackingLine()

        bringLocationMarkerToTop()
        fragment.binding.mapView.invalidate()

        val distance = smoothedPosition.distanceToAsDouble(target)
        if (distance < 0.01) {
            marker.position = target
            fragment.binding.mapView.invalidate()
        }
    }

    fun createLocationPin(text: String = "M") {
        fragment.currentPinText = text
        updatePinBitmap()
    }

    fun updatePinBitmap() {
        if (!fragment.isAdded || fragment.context == null) return
        val dens = fragment.resources.displayMetrics.density
        val base = 60 * dens
        val pad = base * 0.25f
        val s = (base + pad * 2).toInt()
        val bm = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canv = Canvas(bm); canv.translate(pad, pad)
        CustomLocationPinDrawable(
            fragment.requireContext(),
            fragment.currentPinText,
            fragment.currentHeading
        ).apply { setBounds(0, 0, base.toInt(), base.toInt()); draw(canv) }
        val icon = BitmapDrawable(fragment.resources, bm)
        val aY = (base * 0.70f + pad) / s
        if (fragment.locationMarker == null) {
            fragment.locationMarker = Marker(fragment.binding.mapView).apply {
                this.icon = icon; setAnchor(
                0.5f,
                aY
            ); infoWindow = null; setOnMarkerClickListener { mk, _ ->
                findNearestPoint(mk.position)?.let {
                    showPointDetailsBottomSheet(
                        it
                    )
                }; true
            }
            }
            fragment.binding.mapView.overlays.add(fragment.locationMarker)
        } else {
            fragment.locationMarker?.icon = icon; fragment.locationMarker?.setAnchor(0.5f, aY)
        }
        fragment.binding.mapView.invalidate()
    }

    fun updatePinText(newText: String) {
        fragment.currentPinText = newText
        updatePinBitmap()
    }

    fun updatePinOrientation() {
        updatePinBitmap()
    }

    fun setupCompassOrientation() {
        fragment.sensorManager =
            fragment.requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        fragment.orientationSensor =
            fragment.sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

        fragment.sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (!fragment.isAdded || fragment.context == null) return
                fragment.currentHeading = e.values[0]; updatePinBitmap()
            }

            override fun onAccuracyChanged(s: Sensor, a: Int) {}
        }
    }

    fun setupSwipeGestureForDataCollectionSettings(v: View, b: BottomSheetCollectPointBinding) {
        v.isClickable = true
        var initialY = 0f
        var initialX = 0f
        var isDragging = false
        var originalHeight = 0
        var startHeight = 0
        var maxContentHeight = 0

        val gestureListener = View.OnTouchListener { touchedView, event ->
            // When keyboard is open (full screen mode), disable swipe gestures
            val r = Rect()
            b.root.getWindowVisibleDisplayFrame(r)
            val screenHeight = b.root.rootView.height
            val keypadHeight = screenHeight - r.bottom
            if (keypadHeight > screenHeight * 0.15) {
                return@OnTouchListener false
            }

            if (touchedView.canScrollVertically(-1) && !isDragging) return@OnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    initialX = event.rawX
                    isDragging = false

                    // ALWAYS RE-MEASURE to get clean values for both states (collapsed and expanded)
                    // This fixes the issue where originalHeight was measured incorrectly if already expanded.
                    val prevVis = b.llDataCollectionSettings.visibility

                    // 1. Measure Collapsed (settings GONE)
                    b.llDataCollectionSettings.visibility = View.GONE
                    v.measure(
                        View.MeasureSpec.makeMeasureSpec(
                            (v.parent as View).width,
                            View.MeasureSpec.EXACTLY
                        ),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    originalHeight = v.measuredHeight

                    // 2. Measure Expanded (settings VISIBLE)
                    b.llDataCollectionSettings.visibility = View.VISIBLE
                    v.measure(
                        View.MeasureSpec.makeMeasureSpec(
                            (v.parent as View).width,
                            View.MeasureSpec.EXACTLY
                        ),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    )
                    maxContentHeight = v.measuredHeight

                    // Restore original visibility state for the gesture start
                    b.llDataCollectionSettings.visibility = prevVis

                    startHeight = v.height
                    false // propagate
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialY - event.rawY // Positive = Drag Up
                    val deltaX = initialX - event.rawX

                    if (!isDragging && Math.abs(deltaY) > 20f && Math.abs(deltaY) > Math.abs(deltaX)) {
                        isDragging = true
                        touchedView.parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    if (isDragging) {
                        // Limit height to max content height
                        val limitHeight =
                            if (maxContentHeight > 0) maxContentHeight else ((v.parent as? View)?.height
                                ?: 2000)

                        val newHeight = (startHeight + deltaY).toInt()

                        // Allow dragging down up to 1% below original height for bounce effect
                        val minAllowedHeight = (originalHeight * 0.99).toInt()

                        val constrainedHeight = newHeight.coerceIn(minAllowedHeight, limitHeight)

                        // Update Settings Visibility
                        if (constrainedHeight > originalHeight + 20) {
                            if (b.llDataCollectionSettings.visibility != View.VISIBLE) {
                                b.llDataCollectionSettings.visibility = View.VISIBLE
                                b.llDataCollectionSettings.alpha = 1f
                            }
                        } else if (constrainedHeight <= originalHeight) {
                            if (b.llDataCollectionSettings.visibility == View.VISIBLE) {
                                b.llDataCollectionSettings.visibility = View.GONE
                            }
                        }

                        val layoutParams = v.layoutParams
                        layoutParams.height = constrainedHeight
                        v.layoutParams = layoutParams

                        return@OnTouchListener true
                    }
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchedView.parent?.requestDisallowInterceptTouchEvent(false)
                    if (isDragging) {
                        val currentHeight = v.height

                        // Calculate target expanded height
                        val targetExpandedHeight =
                            if (maxContentHeight > 0) maxContentHeight else ((v.parent as? View)?.height
                                ?: 2000)

                        // Threshold to snap to expanded state (pull up by ~25% of the expandable area)
                        val expandThreshold =
                            originalHeight + (targetExpandedHeight - originalHeight) / 4

                        if (currentHeight > expandThreshold) {
                            // Snap to Expanded
                            val anim = ValueAnimator.ofInt(currentHeight, targetExpandedHeight)
                            anim.addUpdateListener { va ->
                                val h = va.animatedValue as Int
                                val lp = v.layoutParams
                                lp.height = h
                                v.layoutParams = lp
                            }
                            anim.duration = 200
                            anim.interpolator = FastOutSlowInInterpolator()
                            anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: android.animation.Animator) {
                                    // Set to WRAP_CONTENT to avoid white space if content changes
                                    val lp = v.layoutParams
                                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    v.layoutParams = lp
                                    v.requestLayout()
                                    adjustMapsButtonsForBottomSheet()
                                }
                            })
                            anim.start()

                            // Ensure settings are fully visible
                            b.llDataCollectionSettings.visibility = View.VISIBLE
                            b.llDataCollectionSettings.alpha = 1f
                        } else {
                            // Snap back to Collapsed with Over-shoot interpolator if it was dragged below original
                            val anim = ValueAnimator.ofInt(currentHeight, originalHeight)
                            anim.addUpdateListener { va ->
                                val h = va.animatedValue as Int
                                val lp = v.layoutParams
                                lp.height = h
                                v.layoutParams = lp
                            }
                            anim.duration = 400

                            if (currentHeight < originalHeight) {
                                // Bouncy re-open if dragged below
                                anim.interpolator = OvershootInterpolator(2.0f)
                            } else {
                                anim.interpolator = FastOutSlowInInterpolator()
                            }

                            anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: android.animation.Animator) {
                                    b.llDataCollectionSettings.visibility = View.GONE
                                    // RESET TO WRAP_CONTENT to avoid white space and ensure it hugs content
                                    val lp = v.layoutParams
                                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    v.layoutParams = lp
                                    v.requestLayout()
                                    adjustMapsButtonsForBottomSheet()
                                }
                            })
                            anim.start()
                        }

                        isDragging = false
                        return@OnTouchListener true
                    }
                    isDragging = false
                    false
                }

                else -> false
            }
        }

        fun attachListenerRecursively(view: View) {
            view.setOnTouchListener(gestureListener)
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    attachListenerRecursively(view.getChildAt(i))
                }
            }
        }

        attachListenerRecursively(v)
    }

    private fun showDataCollectionSettings(b: BottomSheetCollectPointBinding) {
        if (b.llDataCollectionSettings.visibility != View.VISIBLE) {
            b.llDataCollectionSettings.visibility = View.VISIBLE; b.llDataCollectionSettings.alpha =
                0f
            b.llDataCollectionSettings.animate().alpha(1f).setDuration(200).start()
        }
    }

    private fun hideDataCollectionSettings(b: BottomSheetCollectPointBinding) {
        if (b.llDataCollectionSettings.visibility == View.VISIBLE) {
            b.llDataCollectionSettings.animate().alpha(0f).setDuration(200)
                .withEndAction { b.llDataCollectionSettings.visibility = View.GONE }.start()
        }
    }

    fun setupExpandableObjectListGesture(v: View, b: BottomSheetObjectListBinding) {
        v.isClickable = true
        var initialY = 0f
        var initialX = 0f
        var isDragging = false
        var originalHeight = 0
        var startHeight = 0

        val gestureListener = View.OnTouchListener { touchedView, event ->
            if (touchedView.canScrollVertically(-1) && !isDragging) return@OnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    initialX = event.rawX
                    isDragging = false

                    if (originalHeight == 0 || v.layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
                        v.measure(
                            View.MeasureSpec.makeMeasureSpec(
                                (v.parent as View).width,
                                View.MeasureSpec.EXACTLY
                            ),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                        )
                        originalHeight = v.measuredHeight
                    }
                    startHeight = v.height
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialY - event.rawY // Positive = Drag Up
                    val deltaX = initialX - event.rawX

                    if (!isDragging && Math.abs(deltaY) > 20f && Math.abs(deltaY) > Math.abs(deltaX)) {
                        isDragging = true
                        touchedView.parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    if (isDragging) {
                        val parentHeight = (v.parent as? View)?.height
                            ?: fragment.resources.displayMetrics.heightPixels
                        val margin = 50
                        val fullHeight = parentHeight - margin

                        // Allow dragging up to full height
                        val newHeight = (startHeight + deltaY).toInt()
                        val constrainedHeight = newHeight.coerceIn(originalHeight / 2, fullHeight)

                        val layoutParams = v.layoutParams
                        layoutParams.height = constrainedHeight
                        v.layoutParams = layoutParams

                        // If expanded beyond 600dp (our XML limit), unlock the RecyclerView
                        val rvParams = b.rvObjectList.layoutParams as ConstraintLayout.LayoutParams
                        if (constrainedHeight > fragment.resources.displayMetrics.density * 550) {
                            // Remove/Increase max height constraint
                            rvParams.matchConstraintMaxHeight = 0 // Standard match constraint
                            // We also need to set layout_height="0dp" if it was wrap_content?
                            // In XML it is wrap_content.
                            // Changing to 0dp might be needed.
                            // Let's just set matchConstraintMaxHeight to a huge value
                            rvParams.matchConstraintMaxHeight = 20000
                        } else {
                            // Restore limit
                            rvParams.matchConstraintMaxHeight =
                                (fragment.resources.displayMetrics.density * 600).toInt()
                        }
                        b.rvObjectList.layoutParams = rvParams

                        return@OnTouchListener true
                    }
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    touchedView.parent?.requestDisallowInterceptTouchEvent(false)
                    if (isDragging) {
                        val currentHeight = v.height
                        val parentHeight = (v.parent as? View)?.height
                            ?: fragment.resources.displayMetrics.heightPixels
                        val fullHeight = parentHeight - 50

                        if (currentHeight > originalHeight * 1.5 || currentHeight > fullHeight * 0.8) {
                            // Snap to Full Screen
                            val anim = ValueAnimator.ofInt(currentHeight, fullHeight)
                            anim.addUpdateListener { va ->
                                v.layoutParams.height = va.animatedValue as Int
                                v.requestLayout()
                                // Ensure Recycler Unlocked
                                val rvParams =
                                    b.rvObjectList.layoutParams as ConstraintLayout.LayoutParams
                                rvParams.matchConstraintMaxHeight = 20000
                                b.rvObjectList.layoutParams = rvParams
                            }
                            anim.duration = 200
                            anim.start()
                        } else if (currentHeight < originalHeight * 0.8) {
                            // Dismiss
                            hideObjectListBottomSheet()
                        } else {
                            // Snap back to Original (Wrap)
                            val anim = ValueAnimator.ofInt(currentHeight, originalHeight)
                            anim.addUpdateListener { va ->
                                v.layoutParams.height = va.animatedValue as Int
                                v.requestLayout()
                                // Restore Recycler Lock
                                val rvParams =
                                    b.rvObjectList.layoutParams as ConstraintLayout.LayoutParams
                                rvParams.matchConstraintMaxHeight =
                                    (fragment.resources.displayMetrics.density * 600).toInt()
                                b.rvObjectList.layoutParams = rvParams
                            }
                            anim.duration = 200
                            anim.start()
                        }
                        isDragging = false
                        return@OnTouchListener true
                    }
                    false
                }

                else -> false
            }
        }

        // Attach to specific drag targets
        // Assuming b has viewDragHandle and a header layout
        // Only attach to Handle and Header to allow List scrolling
        // b.root.setOnTouchListener(gestureListener) // NO - blocks list

        // Find header views manually or if binding has IDs
        // b.btnDragHandle? No, XML has view_drag_handle (no ID in binding? XML id is view_drag_handle)
        // Wait, where did I see view_drag_handle??
        // Ah, `bottom_sheet_collect_point.xml` (Step 440) had `@+id/view_drag_handle`.
        // `bottom_sheet_object_list.xml` (Step 520) Line 14: <View ... layout_marginTop...>
        // NO ID!!!!

        // Function `attachListenerRecursively` used previously attached to ROOT.
        // It worked for dismiss because entire background was draggable.
        // But broke list.

        // I need to attach to the header.
        // Header LinearLayout (Line 8). No ID.
        // Child LinearLayout (Line 22). No ID.
        // "Object list" TextView.

        // I MUST ADD IDs to XML to attach listeners properly!
        // Or attach to Root and intercept?
        // If I Attach to Root (exclude Recycler), it means touching Header works.
        // Touching background (if any) works.
        // So `attachListenerRecursively` (with Recycler exclusion) IS THE WAY.

        // So I can use `attachListenerRecursively` logic inside `setupExpandable...`.

        val recursiveAttacher = object : Any() {
            fun attach(view: View) {
                // Attach to everything
                /*if (view is androidx.recyclerview.widget.RecyclerView ||
                    view is android.widget.ScrollView ||
                    view is androidx.core.widget.NestedScrollView) {
                    return
                }*/
                view.setOnTouchListener(gestureListener)
                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        attach(view.getChildAt(i))
                    }
                }
            }
        }
        recursiveAttacher.attach(v)
    }

fun setupSwipeGestureForPointLineSelection(v: View, b: BottomSheetLineSegmentBinding) {
        val swipeThresholdPx = 12f
        var startY = 0f
        var infoVisible = b.nsvInfo.visibility == View.VISIBLE

        fun enforceWrapContentSheetLayout() {
            val rootLp = b.root.layoutParams as? ConstraintLayout.LayoutParams ?: return
            rootLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            rootLp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            rootLp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            rootLp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            rootLp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            rootLp.verticalBias = 1.0f
            rootLp.constrainedHeight = true
            // minimal margins in collapsed state
            rootLp.topMargin = 0
            rootLp.bottomMargin = bottomNavOffset.toInt()
            b.root.layoutParams = rootLp
        }

        fun enforceFullHeightSheetLayout() {
            val rootLp = b.root.layoutParams as? ConstraintLayout.LayoutParams ?: return
            rootLp.height = 0
            rootLp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            rootLp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            rootLp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            rootLp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            rootLp.verticalBias = 0.5f
            rootLp.constrainedHeight = false
            // remove gaps while expanded
            rootLp.topMargin = 0
            rootLp.bottomMargin = 0
            b.root.layoutParams = rootLp
        }

        fun refreshPointLineData() {
            val isLineMode = fragment.highlightedLineOverlay != null
            val isPointMode = fragment.selectedPoint != null
            if (isLineMode) {
                b.llPointLineInfo.visibility = View.VISIBLE
                b.clLineInfo.visibility = View.VISIBLE
                b.clPointInfo.visibility = View.GONE
                fragment.highlightedLineOverlay?.let { line ->
                    b.tvCodeIdInfo.text = line.codeId.ifEmpty { "No Code" }
                    b.txtPointInfo.text = "${line.pointCount}"
                    b.txtDistanceInfo.text = String.format("%.2f M", line.length)
                }
            } else if (isPointMode) {
                b.llPointLineInfo.visibility = View.VISIBLE
                b.clPointInfo.visibility = View.VISIBLE
                b.clLineInfo.visibility = View.GONE
                fragment.selectedPoint?.let { point ->
                    b.txtCollectedPointInfo.text = formatTimestamp(point.ts)
                    b.txtAntennaInfo.text = String.format("%.3f M", point.elevation)
                    updatePointDetailsUI(b, point)
                }
            }
        }

        fun showInfo() {
            if (infoVisible) return
            // Expand just to content height (no forced empty space)
            (b.root.layoutParams as? ConstraintLayout.LayoutParams)?.let { lp ->
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                lp.verticalBias = 1.0f
                lp.constrainedHeight = true
                lp.topMargin = statusBarHeight + fragment.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._10sdp)
                lp.bottomMargin = bottomNavOffset.toInt()
                b.root.layoutParams = lp
            }
            b.nsvInfo.visibility = View.VISIBLE
            b.llPointLineInfo.visibility = View.VISIBLE
            refreshPointLineData()
            infoVisible = true
            b.nsvInfo.requestLayout()
            b.root.requestLayout()
        }

        fun hideInfo() {
            if (!infoVisible) return
            // Keep same wrap_content constraints
            (b.root.layoutParams as? ConstraintLayout.LayoutParams)?.let { lp ->
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                lp.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                lp.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                lp.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                lp.verticalBias = 1.0f
                lp.constrainedHeight = true
                lp.topMargin = statusBarHeight + fragment.resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._10sdp)
                lp.bottomMargin = bottomNavOffset.toInt()
                b.root.layoutParams = lp
            }
            b.clLineMenu.visibility = View.GONE
            b.nsvInfo.visibility = View.GONE
            b.llPointLineInfo.visibility = View.GONE
            infoVisible = false
            b.root.requestLayout()
        }

        val swipeTouch = View.OnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - startY
                    if (kotlin.math.abs(dy) > swipeThresholdPx) {
                        if (dy < 0) showInfo() else hideInfo()
                        startY = event.rawY
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    false
                }
                else -> false
            }
        }

        fun attachEverywhere(view: View) {
            if (view.id == b.nsvInfo.id || view.id == b.btnCloseLineSegment.id) return // allow scroll + close click
            view.setOnTouchListener(swipeTouch)
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) attachEverywhere(view.getChildAt(i))
            }
        }

        attachEverywhere(b.root)
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun View.setSwipeSafeClickListener(onClick: () -> Unit) {
        var startX = 0f
        var startY = 0f
        var isSwipe = false
        setOnTouchListener { v, event ->
            val touchSlop = 15f * v.resources.displayMetrics.density
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    isSwipe = false
                    false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.x - startX)
                    val dy = Math.abs(event.y - startY)
                    if (dx > touchSlop || dy > touchSlop) {
                        isSwipe = true
                    }
                    false
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (isSwipe) {
                        v.isPressed = false
                        true // Consume event, suppress click
                    } else {
                        v.performClick()
                        onClick()
                        true
                    }
                }
                else -> false
            }
        }
    }

    fun setupSwipeGestureForEditLine(v: View, sheetBinding: BottomSheetEditLineBinding) {
        v.isClickable = true
        var initialY = 0f
        var initialX = 0f
        var isDragging = false
        var originalHeight = 0
        var startHeight = 0
        var lastEventTime = 0L

        val gestureListener = View.OnTouchListener { view, event ->
            // Disable swipe if we are aggressively dragging a point
            if (fragment.isDraggingEditLinePoint) return@OnTouchListener false

            // Let internal scroll handle it if at top
            if (view.canScrollVertically(-1) && !isDragging) return@OnTouchListener false

            if (event.eventTime == lastEventTime) return@OnTouchListener isDragging
            lastEventTime = event.eventTime

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    initialX = event.rawX
                    isDragging = false
                    startHeight = sheetBinding.rvPoints.height

                    // Measure base height with exactly 3 items (~170dp)
                    val collapsedListHeight = (170 * fragment.resources.displayMetrics.density).toInt()
                    originalHeight = collapsedListHeight

                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialY - event.rawY
                    val deltaX = initialX - event.rawX

                    val parentHeight =
                        (sheetBinding.root.parent as? View)?.height
                            ?: fragment.resources.displayMetrics.heightPixels

                    // Measure header and footer for accurate list expansion
                    val headerHeight = sheetBinding.clHeaderTop.height
                    val footerHeight = sheetBinding.btnSaveEdit.height
                    val density = fragment.resources.displayMetrics.density
                    val topGap = (25 * density).toInt()      // gap at top (matches collect sheet)
                    val bottomMargin = (40 * density).toInt() // btn_save_edit marginBottom

                    val fullHeight = parentHeight - (statusBarHeight + topGap + headerHeight + footerHeight + bottomMargin)

                    if (!isDragging && Math.abs(deltaY) > 20f && Math.abs(deltaY) > Math.abs(deltaX)) {
                        isDragging = true
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        startHeight = sheetBinding.rvPoints.height
                    }

                    if (isDragging) {
                        val newHeight = (startHeight + deltaY).toInt()
                        val constrainedHeight = newHeight.coerceAtMost(fullHeight)
                        val finalHeight = Math.max(originalHeight, constrainedHeight)

                        val lp = sheetBinding.rvPoints.layoutParams
                        lp.height = finalHeight
                        sheetBinding.rvPoints.layoutParams = lp
                        return@OnTouchListener true
                    }
                    false
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    if (isDragging) {
                        val deltaY = initialY - event.rawY
                        val currentHeight = sheetBinding.rvPoints.height
                        val parentHeight =
                            (sheetBinding.root.parent as? View)?.height
                                ?: fragment.resources.displayMetrics.heightPixels

                        val headerHeight = sheetBinding.clHeaderTop.height
                        val footerHeight = sheetBinding.btnSaveEdit.height
                        val density = fragment.resources.displayMetrics.density
                        val topGap = (25 * density).toInt()
                        val bottomMargin = (40 * density).toInt()
                        val fullHeight = parentHeight - (statusBarHeight + topGap + headerHeight + footerHeight + bottomMargin)

                        val threshold = originalHeight + (fullHeight - originalHeight) * 0.15
                        val isQuickExpand = startHeight < originalHeight + 50 && deltaY > 100
                        val isQuickCollapse = startHeight > fullHeight - 50 && deltaY < -100

                        if ((currentHeight < threshold && !isQuickExpand) || isQuickCollapse) {
                            // Collapse
                            val anim = ValueAnimator.ofInt(currentHeight, originalHeight)
                            anim.addUpdateListener { va ->
                                val lp = sheetBinding.rvPoints.layoutParams
                                lp.height = va.animatedValue as Int
                                sheetBinding.rvPoints.layoutParams = lp
                            }
                            anim.duration = 150
                            anim.interpolator = FastOutSlowInInterpolator()
                            anim.start()

                            fragment.binding.mapView.setMultiTouchControls(true)
                            fragment.binding.mapView.setOnTouchListener(null)
                            fragment.binding.llMapsButtons.visibility = View.VISIBLE
                        } else {
                            // Expand
                            val anim = ValueAnimator.ofInt(currentHeight, fullHeight)
                            anim.addUpdateListener { va ->
                                val lp = sheetBinding.rvPoints.layoutParams
                                lp.height = va.animatedValue as Int
                                sheetBinding.rvPoints.layoutParams = lp
                            }
                            anim.addListener(object : android.animation.AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: android.animation.Animator) {
                                    // Optionally apply full screen constraints to root if desired,
                                    // but user asked for ONLY rv_points height to change.
                                }
                            })
                            anim.duration = 150
                            anim.interpolator = FastOutSlowInInterpolator()
                            anim.start()

                            // Disable map buttons only if it's very tall
                            if (fullHeight > parentHeight * 0.5) {
                                fragment.binding.mapView.setMultiTouchControls(false)
                                fragment.binding.mapView.setOnTouchListener { _, _ -> true }
                                fragment.binding.llMapsButtons.visibility = View.GONE
                            }
                        }
                        isDragging = false
                        return@OnTouchListener true
                    }
                    isDragging = false
                    false
                }

                else -> false
            }
        }

        fun attachListenerRecursively(view: View) {
            // Don't attach gesture listener to RecyclerView - let it handle its own scrolling
            if (view !is RecyclerView && view !is android.widget.ScrollView && view !is androidx.core.widget.NestedScrollView) {
                view.setOnTouchListener(gestureListener)
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    attachListenerRecursively(view.getChildAt(i))
                }
            }
        }
        attachListenerRecursively(v)
    }

    private fun showPointLineSelection(b: BottomSheetLineSegmentBinding) {
        b.clLineMenu.animate().cancel()
        b.clLineMenu.visibility = View.VISIBLE
        if (b.clLineMenu.alpha < 1f) {
            b.clLineMenu.alpha = 0f
            b.clLineMenu.animate().alpha(1f).setDuration(200).start()
        } else {
            b.clLineMenu.alpha = 1f
        }
    }

    private fun hidePointLineSelection(b: BottomSheetLineSegmentBinding) {
        b.clLineMenu.animate().cancel()
        if (b.clLineMenu.visibility == View.VISIBLE) {
            b.clLineMenu.animate().alpha(0f).setDuration(200)
                .withEndAction {
                    b.clLineMenu.visibility = View.GONE
                    b.clLineMenu.alpha = 1f
                }.start()
        }
    }

    private fun hideLineSegmentMenuIfVisible() {
        val lineSheet = fragment.binding.bottomSheetLineSegment
        if (lineSheet.root.visibility == View.VISIBLE && lineSheet.clLineMenu.visibility == View.VISIBLE) {
            hidePointLineSelection(lineSheet)
        }
    }

    private fun hideLineSegmentMenuThen(action: () -> Unit) {
        val lineSheet = fragment.binding.bottomSheetLineSegment
        if (lineSheet.root.visibility == View.VISIBLE && lineSheet.clLineMenu.visibility == View.VISIBLE) {
            hidePointLineSelection(lineSheet)
            lineSheet.root.postDelayed({ action() }, 200)
        } else {
            action()
        }
    }

    private fun setMapTouchForLineSegment(blockMap: Boolean) {
        val lineSheet = fragment.binding.bottomSheetLineSegment
        fragment.binding.mapView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && lineSheet.clLineMenu.visibility == View.VISIBLE) {
                hidePointLineSelection(lineSheet)
            }
            blockMap
        }
    }

    fun setupBottomSheetClickToHideMenu(v: View, b: Any) {
        if (b is BottomSheetLineSegmentBinding) {
            val hide =
                { if (b.clLineMenu.visibility == View.VISIBLE) b.clLineMenu.visibility = View.GONE }
            b.llMainContent.setOnClickListener { hide() }
            b.llCodeIdContainer.setOnClickListener { hide() }
            b.txtPointId.setOnClickListener { hide() }
            b.tvCodeId.setOnClickListener { hide() }
            b.tvSegmentInfo.setOnClickListener { hide() }
            b.tvSegmentInfo.setOnClickListener { hide() }
            b.viewTypeDot.setOnClickListener { hide() }

            b.llEdit.setOnClickListener {
                val point = fragment.selectedPoint
                if (point != null) {
                    hide() // Hide menu
                    // Keep line segment sheet visible; show edit point sheet above it
                    showEditPointBottomSheet(point)
                }
            }
        }
    }

    fun advanceLineCodeForNewSegment(b: BottomSheetCollectPointBinding? = null) {
        if (isLineCodeFromCodeId(fragment.selectedPointCodeId)) {
            fragment.selectedPointCodeId = nextLineCode(fragment.selectedPointCodeId)
            b?.tvPointType?.text = fragment.selectedPointCodeId
        }
    }

    fun showEditLineBottomSheet(
        ls: ClickablePolylineOverlay,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP,
        isRestoring: Boolean = false
    ) = hideMenu {
        if (!isRestoring) {
            fragment.isSelectingPointForEditLine = false // Ensure we are not in selection mode
        }
        android.util.Log.d(
            "MappingLogic",
            "showEditLineBottomSheet called for codeId: ${ls.codeId}"
        )

        fragment.pendingEditLineSegment = ls
        // Remove original line while editing so only the edit preview is visible.
        if (fragment.binding.mapView.overlays.contains(ls)) {
            fragment.binding.mapView.overlays.remove(ls)
        }
        // Also remove any stale overlays with the same code to avoid double-rendering while reordering.
        val staleSameCodeOverlays =
            fragment.binding.mapView.overlays.filterIsInstance<ClickablePolylineOverlay>()
                .filter { it.codeId == ls.codeId }
                .toList()
        staleSameCodeOverlays.forEach { fragment.binding.mapView.overlays.remove(it) }
        fragment.binding.mapView.invalidate()

        // Hide bottom navigation first before hiding collect point sheet
        hideBottomNavigation()

        hideCollectPointBottomSheet(finalizeSegment = false, showNav = false)
        if (!isRestoring) {
            fragment.highlightedLineOverlay?.unhighlight()
            fragment.highlightedLineOverlay = null
        }
        updateMarkersForZoom(forceRefresh = true)

        // Execute Show Logic IMMEDIATELY
        val sheetBinding = fragment.binding.bottomSheetEditLine
        sheetBinding.root.elevation = 24f * fragment.resources.displayMetrics.density
        sheetBinding.root.translationZ = 24f * fragment.resources.displayMetrics.density

        sheetBinding.root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        sheetBinding.root.requestLayout()
        fragment.currentEditLineBinding = sheetBinding

        // Capture original state for revert-on-cancel ONLY if starting a fresh session
        if (!isRestoring && originalEditLineState == null) {
            originalEditLineState = ls.labeledPoints.toList()
            originalEditLineCodeId = ls.codeId
            originalEditLineFeatureCode = ls.featureCode
        }
        if (!isRestoring) isEditLineSaved = false

        val points = ls.labeledPoints.toMutableList()
        sheetBinding.tvCodeDescription.text =
            getCodeDescription(ls.codeId).ifEmpty { "No code" }
        sheetBinding.tvCodeId.text = ls.codeId.ifEmpty { "" }
        sheetBinding.cbClosedLine.isChecked = ls.isClosed
        val canClose = points.size >= 3
        sheetBinding.cbClosedLine.isEnabled = canClose
        val colorRes = if (canClose) R.color.text_primary else R.color.neutral_dark
        sheetBinding.tvClosedLineLabel.setTextColor(
            ContextCompat.getColor(
                fragment.requireContext(),
                colorRes
            )
        )

        if (!canClose) sheetBinding.cbClosedLine.isChecked = false

        sheetBinding.cbClosedLine.setOnCheckedChangeListener { _, _ ->
            updateEditLineOverlay()
        }

        sheetBinding.tvPointsCount.text =
            "${points.size} ${if (points.size == 1) "Point" else "Points"}"

        sheetBinding.rvPoints.layoutManager = LinearLayoutManager(fragment.requireContext())
        val itemTouchHelper = ItemTouchHelper(object :
            ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    fragment.isDraggingEditLinePoint = true
                    viewHolder?.itemView?.let { v ->
                        v.animate().cancel()
                        v.elevation = 24f * fragment.resources.displayMetrics.density
                        v.translationZ = 24f * fragment.resources.displayMetrics.density
                        v.setBackgroundResource(R.drawable.bg_edit_point_item)
                    }
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                fragment.isDraggingEditLinePoint = false
                viewHolder.itemView.let { v ->
                    v.animate().cancel()
                    v.elevation = 0f
                    v.translationZ = 0f

                    // Finalize overlay updates only after the drag session finishes.
                    updateEditLineOverlay()
                    updateMarkersForZoom(forceRefresh = true)
                }
            }

            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                tgt: RecyclerView.ViewHolder
            ): Boolean {
                val adapter = rv.adapter as? EditPointAdapter ?: return false
                adapter.moveItem(vh.adapterPosition, tgt.adapterPosition)
                // PREVENT flickering during drag by NOT calling updateEditLineOverlay here.
                // It will be called in clearView once reordering settles.
                return true
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && isCurrentlyActive) {
                    val itemView = viewHolder.itemView
                    val recyclerView = recyclerView
                    val itemHeight = itemView.height

                    val recyclerTop = recyclerView.top
                    val recyclerBottom = recyclerView.bottom
                    val itemTop = itemView.top + dY.toInt()
                    val itemBottom = itemView.bottom + dY.toInt()

                    val scrollThreshold = itemHeight
                    val maxScrollSpeed = 50

                    when {
                        itemTop <= recyclerTop + scrollThreshold -> {
                            val distanceFromTop = (itemTop - recyclerTop).toFloat()
                            val scrollSpeed = if (distanceFromTop > 0) {
                                (maxScrollSpeed * (1 - (distanceFromTop / scrollThreshold))).toInt()
                            } else {
                                maxScrollSpeed
                            }
                            recyclerView.scrollBy(0, -scrollSpeed)
                        }
                        itemBottom >= recyclerBottom - scrollThreshold -> {
                            val distanceFromBottom = (recyclerBottom - itemBottom).toFloat()
                            val scrollSpeed = if (distanceFromBottom > 0) {
                                (maxScrollSpeed * (1 - (distanceFromBottom / scrollThreshold))).toInt()
                            } else {
                                maxScrollSpeed
                            }
                            recyclerView.scrollBy(0, scrollSpeed)
                        }
                    }
                }
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, d: Int) {}
            override fun isLongPressDragEnabled() = false
        })

        val adapter = EditPointAdapter(points, onRemoveClick = { pos ->
            val cur = sheetBinding.rvPoints.adapter as? EditPointAdapter
            if (cur != null && cur.itemCount > 2) {
                val ptToRemove = cur.getPoints()[pos]
                cur.removePoint(pos)
                val idx =
                    fragment.collectedLabeledPoints.indexOfFirst { it.id == ptToRemove.id }
                if (idx >= 0) fragment.collectedLabeledPoints[idx] =
                    fragment.collectedLabeledPoints[idx].copy(codeId = "")
                updateEditLineOverlay() // Update the line on map first
                updateMarkersForZoom(forceRefresh = true) // Then refresh marker selection
                sheetBinding.tvPointsCount.text =
                    "${cur.itemCount} ${if (cur.itemCount == 1) "Point" else "Points"}"

                val canClose = cur.itemCount >= 3
                sheetBinding.cbClosedLine.isEnabled = canClose
                val colorRes = if (canClose) R.color.text_primary else R.color.neutral_dark
                sheetBinding.tvClosedLineLabel.setTextColor(
                    ContextCompat.getColor(
                        fragment.requireContext(),
                        colorRes
                    )
                )

                if (!canClose && sheetBinding.cbClosedLine.isChecked) {
                    sheetBinding.cbClosedLine.isChecked = false
                }

                updateEditLineOverlay()
            } else Toast.makeText(
                fragment.requireContext(),
                "A line must have at least 2 points",
                Toast.LENGTH_SHORT
            ).show()
        }, onDragStart = { holder -> itemTouchHelper.startDrag(holder) })

        sheetBinding.rvPoints.adapter = adapter
        itemTouchHelper.attachToRecyclerView(sheetBinding.rvPoints)
        fragment.currentEditLineAdapter = adapter
        updateEditLineOverlay()

        sheetBinding.tvAddPoint.setOnClickListener {
            fragment.isSelectingPointForEditLine = true
            fragment.pendingEditLineSegment = ls
            // Keep edit line sheet visible underneath; slide object list above it
            showObjectListBottomSheetInternalForEditLine(BottomSheetTransition.SLIDE_UP)
        }

        sheetBinding.llCodeValue.setOnClickListener {
            // Keep edit line visible underneath; show select code above it
            showSelectCodeBottomSheet(
                null,
                onlyPoints = false,
                onlyLines = true,
                transition = BottomSheetTransition.SLIDE_UP,
                showNavOnCloseOverride = false,
                advanceLineCode = true
            ) { codeId, indicatorType ->
                if (indicatorType != IndicatorType.LINE) {
                    // Only line codes are valid here
                    return@showSelectCodeBottomSheet
                }
                // Update the overlay's code ID
                val oldCodeId = ls.codeId
                ls.codeId = codeId
                ls.featureCode = codeId.filter { it.isLetter() }.ifEmpty { "L" }

                // Update all associated points in local list
                fragment.collectedLabeledPoints.forEachIndexed { index, point ->
                    if (point.codeId == oldCodeId) {
                        fragment.collectedLabeledPoints[index] = point.copy(codeId = codeId)
                    }
                }

                // Update edit line UI if visible
                fragment.currentEditLineBinding?.let { b ->
                    b.tvCodeId.text = ls.codeId.ifEmpty { "" }
                    b.tvCodeDescription.text = getCodeDescription(ls.codeId).ifEmpty { "No code" }
                }
                updateEditLineOverlay()
            }
        }

        sheetBinding.root.visibility = View.VISIBLE
        applyFullScreenConstraints(sheetBinding.root)
        animateSheetTransition(null, sheetBinding.root, transition)
        setupSwipeToDismiss(sheetBinding.root) { hideEditLineBottomSheet() }

        sheetBinding.btnSaveEdit.setOnClickListener {
            val cur = sheetBinding.rvPoints.adapter as? EditPointAdapter
            if (cur != null && cur.itemCount >= 2) {
                isEditLineSaved = true // Mark as saved to prevent revert
                val reordered = cur.getPoints()
                val isClosed = sheetBinding.cbClosedLine.isChecked
                val gps = reordered.map { it.geoPoint }
                var dist = 0.0
                for (i in 0 until gps.size - 1) dist += gps[i].distanceToAsDouble(gps[i + 1])
                if (isClosed) dist += gps.last().distanceToAsDouble(gps.first())

                val wasHigh = fragment.highlightedLineOverlay == ls
                val ptsCorrected =
                    reordered.map { if (it.codeId != ls.codeId) it.copy(codeId = ls.codeId) else it }
                val toReorder =
                    fragment.collectedLabeledPoints.filter { pt -> ptsCorrected.any { it.id == pt.id } }
                if (toReorder.isNotEmpty()) {
                    val firstIdx =
                        fragment.collectedLabeledPoints.indexOfFirst { pt -> toReorder.any { it.id == pt.id } }
                    fragment.collectedLabeledPoints.removeAll(toReorder.toSet())
                    fragment.collectedLabeledPoints.addAll(firstIdx, ptsCorrected)
                }

                // Save to Database
                val projectId = fragment.viewModel.currentProjectId.value
                if (projectId == null) {
                    Toast.makeText(
                        fragment.requireContext(),
                        "Error: No active project",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                val featureCode = ls.featureCode
                val lineEntity = LineEntity(
                    projectId = projectId,
                    id = ls.codeId,
                    code = featureCode,
                    isClosed = isClosed,
                    length = dist
                )
                val pointEntities = ptsCorrected.map { it.toPointEntity(projectId) }
                fragment.viewModel.saveLine(lineEntity, pointEntities)

                // Handle removed points: explicitly save them with empty code AND update local state
                val originalPoints = originalEditLineState ?: emptyList()
//                val removedPoints =
//                    originalPoints.filter { original -> ptsCorrected.none { it.id == original.id } }
                val removedPoints: List<LabeledPoint> =
                    originalPoints.filter { original: LabeledPoint -> ptsCorrected.none { corrected: LabeledPoint -> corrected.id == original.id } }
                removedPoints.forEach { removed ->
                    val updatedPoint = removed.copy(codeId = "").toPointEntity(projectId)
                    fragment.viewModel.savePoint(updatedPoint)

                    // Update local state so map refreshes correctly
                    val idx = fragment.collectedLabeledPoints.indexOfFirst { it.id == removed.id }
                    if (idx >= 0) {
                        fragment.collectedLabeledPoints[idx] =
                            fragment.collectedLabeledPoints[idx].copy(codeId = "")
                    }
                }

                // Update local collectedLines state to prevent reconstructPolylines from using stale data
                val updatedLineWithPoints = LineWithPoints(
                    line = lineEntity,
                    points = pointEntities
                )
                collectedLines = collectedLines.map {
                    if (it.line.id == ls.codeId) updatedLineWithPoints else it
                }

                Toast.makeText(
                    fragment.requireContext(),
                    "Line saved successfully",
                    Toast.LENGTH_SHORT
                ).show()

                val staleSavedOverlays =
                    fragment.completedLineOverlays.filterIsInstance<ClickablePolylineOverlay>()
                        .filter { it.codeId == ls.codeId }
                        .toList()
                staleSavedOverlays.forEach {
                    OsmdroidPolylineHelper.removePolyline(fragment.binding.mapView, it)
                }
                fragment.completedLineOverlays.removeAll(staleSavedOverlays.toSet())

                // Also remove the temporary edit overlay if it exists
                if (fragment.highlightedLineOverlay != null && fragment.highlightedLineOverlay != ls) {
                    fragment.binding.mapView.overlays.remove(fragment.highlightedLineOverlay)
                }
                fragment.highlightedLineOverlay = null
                fragment.closingSegmentOverlay?.let {
                    fragment.binding.mapView.overlays.remove(it)
                }
                fragment.closingSegmentOverlay = null

                val updated = ClickablePolylineOverlay(
                    gps,
                    ContextCompat.getColor(fragment.requireContext(), R.color.slate_gray_light),
                    6f,
                    closed = isClosed
                ).apply {
                    this.codeId = ls.codeId; this.featureCode = featureCode; pointCount =
                    ptsCorrected.size; length =
                    dist; labeledPoints = ptsCorrected; this.isClosed = isClosed
                    setOnClickListener { handleLineSegmentClick(this) }
                }
                addPolylineBelowMarkers(updated)
                fragment.completedLineOverlays.add(updated)
                ensurePointClickHandlerAtEnd(); updateMarkersForZoom(); bringLocationMarkerToTop()
                if (wasHigh) {
                    updated.highlight(
                        ContextCompat.getColor(
                            fragment.requireContext(),
                            R.color.primary
                        )
                    ); fragment.highlightedLineOverlay = updated
                }
                fragment.binding.mapView.invalidate()
                fragment.binding.mapView.invalidate()

                // Reset tracking state to prevent live tracking line from connecting to these points
                fragment.lineSegmentStartIndex = fragment.collectedLabeledPoints.size
                fragment.liveTrackingLineOverlay?.let {
                    OsmdroidPolylineHelper.removePolyline(fragment.binding.mapView, it)
                }
                fragment.liveTrackingLineOverlay = null

            } else {
                Toast.makeText(
                    fragment.requireContext(),
                    "A line must have at least 2 points",
                    Toast.LENGTH_SHORT
                ).show(); return@setOnClickListener
            }
            hideEditLineBottomSheet()
        }

        sheetBinding.btnCloseEditLine.setOnClickListener {
            // Explicit close should exit selection mode to avoid reopening
            fragment.isSelectingPointForEditLine = false
            isEditLineSaved = false

            // Close edit line explicitly (do not reopen via back stack)
            clearBackStack()
            hideEditLineBottomSheet()
        }
        sheetBinding.root.visibility = View.VISIBLE
        sheetBinding.root.bringToFront() // Ensure it's on top

        // Calculate initial collapsed height (3 items = 170dp)
        val nsvParams = sheetBinding.rvPoints.layoutParams
        val collapsedListHeight = (170 * fragment.resources.displayMetrics.density).toInt()
        nsvParams.height = collapsedListHeight
        sheetBinding.rvPoints.layoutParams = nsvParams

        sheetBinding.root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        sheetBinding.root.requestLayout()

        sheetBinding.root.alpha = 0f
        // Estimate height for animation start
        sheetBinding.root.translationY = (350 * fragment.resources.displayMetrics.density)

        sheetBinding.root.animate().alpha(1f).translationY(0f)
            .setInterpolator(FastOutSlowInInterpolator()).setDuration(200)
            .start()

        setupSwipeGestureForEditLine(sheetBinding.root, sheetBinding)

        // Keyboard Handling: Expand to full screen when keyboard shows (like collect point)
        sheetBinding.root.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            private var wasOpened = false
            private var savedHeight = 0

            override fun onGlobalLayout() {
                if (!sheetBinding.root.isAttachedToWindow) {
                    sheetBinding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    return
                }

                val r = Rect()
                sheetBinding.root.getWindowVisibleDisplayFrame(r)
                val screenHeight = sheetBinding.root.rootView.height
                val keypadHeight = screenHeight - r.bottom

                if (keypadHeight > screenHeight * 0.15) { // Keyboard is open
                    if (!wasOpened) {
                        // Expand only the list height with smooth animation
                        val parentHeight = (sheetBinding.root.parent as? View)?.height ?: screenHeight
                        val baseMargin = (25 * fragment.resources.displayMetrics.density).toInt()
                        val fullHeight = parentHeight - (statusBarHeight + baseMargin + 200) // approx room for footer/header

                        val lp = sheetBinding.rvPoints.layoutParams
                        if (savedHeight == 0) savedHeight = lp.height
                        val currentHeight = lp.height

                        val anim = ValueAnimator.ofInt(currentHeight, fullHeight)
                        anim.addUpdateListener { va ->
                            val params = sheetBinding.rvPoints.layoutParams
                            params.height = va.animatedValue as Int
                            sheetBinding.rvPoints.layoutParams = params
                        }
                        anim.duration = 100
                        anim.interpolator = FastOutSlowInInterpolator()
                        anim.start()

                        wasOpened = true
                        // ... map touch logic ...
                        fragment.binding.mapView.setMultiTouchControls(false)
                        fragment.binding.mapView.overlays.filterIsInstance<org.osmdroid.views.overlay.gestures.RotationGestureOverlay>()
                            .forEach { it.isEnabled = false }
                        fragment.binding.mapView.setOnTouchListener { _, _ -> true }
                        fragment.binding.llMapsButtons.visibility = View.GONE
                    }
                } else { // Keyboard is closed
                    if (wasOpened) {
                        val lp = sheetBinding.rvPoints.layoutParams
                        val collapsedHeight = if (savedHeight > 0) savedHeight else (170 * fragment.resources.displayMetrics.density).toInt()
                        val currentHeight = lp.height

                        val anim = ValueAnimator.ofInt(currentHeight, collapsedHeight)
                        anim.addUpdateListener { va ->
                            val params = sheetBinding.rvPoints.layoutParams
                            params.height = va.animatedValue as Int
                            sheetBinding.rvPoints.layoutParams = params
                        }
                        anim.duration = 100
                        anim.interpolator = FastOutSlowInInterpolator()
                        anim.start()

                        wasOpened = false
                        // ... re-enable map logic ...
                        fragment.binding.mapView.setMultiTouchControls(true)
                        fragment.binding.mapView.overlays.filterIsInstance<org.osmdroid.views.overlay.gestures.RotationGestureOverlay>()
                            .forEach { it.isEnabled = true }
                        fragment.binding.mapView.setOnTouchListener(null)
                        fragment.binding.llMapsButtons.visibility = View.VISIBLE
                    }
                    // Ensure bottom nav stays hidden while sheet is visible (keyboard closed)
                    hideBottomNavigation()
                }
            }
        })

        // Do NOT adjust sidebar for edit line sheet - sidebar should stay in place
    }

    fun hideEditLineBottomSheet(
        showNav: Boolean = true,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_DOWN,
        onHidden: (() -> Unit)? = null
    ) {
        // Revert logic if not saved AND not just hiding to select a point
        if (!fragment.isSelectingPointForEditLine) {
            if (!isEditLineSaved && originalEditLineState != null) {
                val originalPoints = originalEditLineState!!
                val lineForRevert = fragment.pendingEditLineSegment ?: fragment.highlightedLineOverlay
                val lineCodeForRevert = originalEditLineCodeId ?: lineForRevert?.codeId ?: ""
                val originalIds = originalPoints.map { it.id }.toSet()
                val dirtyPoints =
                    fragment.currentEditLineAdapter?.getPoints()
                        ?: fragment.highlightedLineOverlay?.labeledPoints
                        ?: lineForRevert?.labeledPoints
                        ?: emptyList()

                // 1. Detach points that were added (in dirty but not in original)
                // These points were given the line code, so we must strip it
                dirtyPoints.forEach { pt ->
                    if (pt.id !in originalIds) {
                        val idx = fragment.collectedLabeledPoints.indexOfFirst { it.id == pt.id }
                        if (idx >= 0) {
                            fragment.collectedLabeledPoints[idx] =
                                fragment.collectedLabeledPoints[idx].copy(codeId = "")
                        }
                    }
                }

                // 2. Re-attach points that were removed (in original but maybe not in dirty)
                // These points lost the line code, so we must restore it
                originalPoints.forEach { pt ->
                    val idx = fragment.collectedLabeledPoints.indexOfFirst { it.id == pt.id }
                    if (idx >= 0) {
                        // Restore codeId to line's code
                        fragment.collectedLabeledPoints[idx] =
                            fragment.collectedLabeledPoints[idx].copy(codeId = lineCodeForRevert)
                    }
                }

                if (lineForRevert != null) {
                    lineForRevert.codeId = lineCodeForRevert
                    lineForRevert.featureCode =
                        originalEditLineFeatureCode
                            ?: lineCodeForRevert.filter { it.isLetter() }.ifEmpty { "L" }
                    updateLineGeometry(lineForRevert, originalPoints)
                }
            }
            originalEditLineState = null // Clear state since session ended
            originalEditLineCodeId = null
            originalEditLineFeatureCode = null
        }

        val root = fragment.binding.bottomSheetEditLine.root
        animateSheetTransition(root, null, transition) {
            if (!fragment.isSelectingPointForEditLine) {
                val originalLine = fragment.pendingEditLineSegment
                if (!isEditLineSaved && originalLine != null) {
                    originalLine.unhighlight()
                    if (!fragment.binding.mapView.overlays.contains(originalLine)) {
                        addPolylineBelowMarkers(originalLine)
                    }
                }
                fragment.pendingEditLineSegment = null

                fragment.highlightedLineOverlay?.let { fragment.binding.mapView.overlays.remove(it) }
                fragment.highlightedLineOverlay = null
                fragment.closingSegmentOverlay?.let { fragment.binding.mapView.overlays.remove(it) }
                fragment.closingSegmentOverlay = null
            }
            fragment.currentEditLineAdapter = null
            fragment.currentEditLineBinding = null
            fragment.selectedPoint = null
            isEditLineSaved = false
            updateMarkersForZoom(forceRefresh = true)
            fragment.binding.mapView.invalidate()
            if (fragment.binding.bottomSheetCollectPoint.root.visibility == View.GONE && fragment.currentLineCodeId != null && fragment.selectedPointIndicatorType == IndicatorType.LINE) {
                showCollectPointBottomSheet()
            } else if (showNav) {
                restoreStateAfterClosingInfoSheet()
            }
            onHidden?.invoke()
        }
    }

    fun showNewPointBottomSheet(
        lineSegment: ClickablePolylineOverlay?,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP,
        animate: Boolean = true
    ) = hideMenu {
        val sheetBinding = fragment.binding.bottomSheetNewPoint
        hideBottomNavigation {
            // Ensure New Point sheet is above any other visible sheet
            sheetBinding.root.elevation = 32f * fragment.resources.displayMetrics.density
            sheetBinding.root.translationZ = 32f * fragment.resources.displayMetrics.density
            sheetBinding.root.bringToFront()

            applyFullScreenConstraints(sheetBinding.root)

            sheetBinding.etNote.setText("")
            sheetBinding.etElevation.setText("")

            val nextId =
                if (fragment.pointIdPrefix != null) "${fragment.pointIdPrefix}${fragment.pointIdNumericCounter}" else fragment.pointCounter.toString()
            sheetBinding.etPointId.setText(nextId); sheetBinding.etPointId.hint = nextId
            val defCode =
                lineSegment?.codeId?.ifEmpty { "P" } ?: fragment.selectedPointCodeId.ifEmpty { "P" }
            sheetBinding.tvPointType.text = defCode
            updatePointTypeIndicator(
                sheetBinding.viewTypeDot,
                if (isLineCodeFromCodeId(defCode)) IndicatorType.LINE else IndicatorType.POINT
            )
            (fragment.currentLocation ?: fragment.locationMarker?.position)?.let {
                sheetBinding.etLongitude.setText(it.longitude.toString()); sheetBinding.etLatitude.setText(
                it.latitude.toString()
            )
            }

            sheetBinding.llPointTypeSelector.setOnClickListener {
                showSelectCodeBottomSheet(null, onlyPoints = true, showNavOnCloseOverride = false) { code, type ->
                    fragment.selectedPointCodeId = code
                    fragment.selectedPointIndicatorType = type
                    sheetBinding.tvPointType.text = code
                    updatePointTypeIndicator(sheetBinding.viewTypeDot, type)
                }
            }
            sheetBinding.btnCloseNewPoint.setOnClickListener { hideNewPointBottomSheet() }
            sheetBinding.btnSaveNewPoint.setOnClickListener {
                val pid = sheetBinding.etPointId.text.toString()
                val lon = sheetBinding.etLongitude.text.toString().toDoubleOrNull()
                val lat = sheetBinding.etLatitude.text.toString().toDoubleOrNull()
                if (pid.isEmpty() || lon == null || lat == null) {
                    Toast.makeText(
                        fragment.requireContext(),
                        "Basic data required",
                        Toast.LENGTH_SHORT
                    )
                        .show(); return@setOnClickListener
                }
                val geo = GeoPoint(lat, lon)
                val ts = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())
                val actualPid = if (pid.length == 1 && pid[0].isLetter()) "${pid}1" else pid
                val newPt = LabeledPoint(
                    id = actualPid,
                    codeId = sheetBinding.tvPointType.text.toString(),
                    coords = listOf(lon, lat),
                    elevation = sheetBinding.etElevation.text.toString().toDoubleOrNull() ?: 0.0,
                    ts = ts
                )

                if (lineSegment != null) addExistingPointToLineSegment(newPt, lineSegment)
                else {
                    val type =
                        if (isLineCodeFromCodeId(newPt.codeId)) IndicatorType.LINE else IndicatorType.POINT
                    fragment.selectedPointCodeId =
                        newPt.codeId; fragment.selectedPointIndicatorType =
                        type
                    val success = addPointAtLocation(geo, actualPid, newPt.codeId, type)
                    if (!success) return@setOnClickListener // Don't hide sheet or update ID if duplicate
                }
                updatePointIdAfterSave(actualPid); hideNewPointBottomSheet()
            }

            sheetBinding.root.visibility = View.VISIBLE

            // Keyboard Handling: Keep bottom nav hidden while sheet is open
            sheetBinding.root.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (!sheetBinding.root.isAttachedToWindow) {
                        sheetBinding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        return
                    }
                    if (sheetBinding.root.visibility != View.VISIBLE) return

                    // Ensure bottom nav stays hidden while sheet is visible
                    hideBottomNavigation()
                }
            })

            // Disable map touch and hide map buttons when new point sheet is open
            fragment.binding.mapView.setMultiTouchControls(false)
            fragment.binding.mapView.setOnTouchListener { _, _ -> true }
            fragment.binding.llMapsButtons.visibility = View.GONE

            if (animate) {
                animateSheetTransition(null, sheetBinding.root, transition)
            }
            adjustMapsButtonsForBottomSheet(overrideHeight = sheetBinding.root.height)
            setupSwipeToDismiss(sheetBinding.root) { hideNewPointBottomSheet() }
        }
    }

    fun hideNewPointBottomSheet(
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_DOWN,
        onHidden: (() -> Unit)? = null
    ) {
        // Re-enable map touch and show map buttons
        fragment.binding.mapView.setMultiTouchControls(true)
        fragment.binding.mapView.setOnTouchListener(null)
        fragment.binding.llMapsButtons.visibility = View.VISIBLE

        val root = fragment.binding.bottomSheetNewPoint.root
        adjustMapsButtonsForBottomSheet(closingView = root)

        animateSheetTransition(root, null, transition) {
            restoreStateAfterClosingInfoSheet()
            updateObjectListIfVisible()
            onHidden?.invoke()
        }
    }

    private fun addExistingPointToLineSegment(newPt: LabeledPoint, ls: ClickablePolylineOverlay) {
        fragment.isSelectingPointForEditLine = false

        // Check 0: Prevent duplicate point in current line
        if (ls.labeledPoints.any { it.id == newPt.id }) {
            Toast.makeText(
                fragment.requireContext(),
                "Point is already in this line",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Prevent adding points that are already part of an existing line (that is NOT this line)
        // Check 1: Visual Overlays
        val isVisuallyConnected = fragment.completedLineOverlays.any { overlay ->
            (overlay is ClickablePolylineOverlay) && overlay != ls && overlay.labeledPoints.any { it.id == newPt.id }
        }

        // Check 2: Database State
        val isDbConnected = collectedLines.any { line ->
            line.line.id != ls.codeId && line.points.any { it.id == newPt.id }
        }

        if (isVisuallyConnected || isDbConnected) {
            Toast.makeText(
                fragment.requireContext(),
                "Point is already part of another line",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val corrected = if (newPt.codeId != ls.codeId) newPt.copy(codeId = ls.codeId) else newPt
        val updated = ls.labeledPoints.toMutableList(); updated.add(corrected)

        updateLineGeometry(ls, updated)

        val idx = fragment.collectedLabeledPoints.indexOfFirst { it.id == newPt.id }
        if (idx >= 0) fragment.collectedLabeledPoints[idx] = corrected
        fragment.currentEditLineAdapter?.updatePoints(updated)
        // updateMarkersForZoom() and invalidate() are handled in updateLineGeometry
        fragment.currentEditLineBinding?.let {
            it.tvPointsCount.text =
                "${updated.size} ${if (updated.size == 1) "Point" else "Points"}"
        }
        showEditLineBottomSheet(ls) // Ensure sheet is visible
    }

    private fun updatePointIdAfterSave(pid: String) {
        val regex = Regex("^([a-zA-Z][a-zA-Z ]*)(\\d+)$")
        val numRegex = Regex("^\\d+$")
        when {
            regex.matches(pid) -> {
                val m = regex.find(pid)
                val prefix = m?.groupValues?.get(1) ?: ""
                fragment.pointIdPrefix = prefix
            }

            // Pure alphabet (e.g., "A", "B") -> start prefix numbering from 1
            Regex("^[a-zA-Z][a-zA-Z ]*$").matches(pid) -> {
                val prefix = pid
                fragment.pointIdPrefix = prefix
            }

            numRegex.matches(pid) -> {
                fragment.pointIdPrefix = null
            }

            else -> {
                fragment.pointIdPrefix = null
            }
        }
        refreshNextPointIdForCollectSheet()
    }

    private fun computeNextNumericId(): Int {
        val used = fragment.collectedLabeledPoints.mapNotNull {
            if (it.id.all { ch -> ch.isDigit() }) it.id.toIntOrNull() else null
        }.toSet()
        var next = 1
        while (next in used) next++
        return next
    }

    private fun computeNextPrefixNum(prefix: String): Int {
        val regex = Regex("^([a-zA-Z][a-zA-Z ]*)(\\d+)$")
        val used = fragment.collectedLabeledPoints.mapNotNull {
            val m = regex.find(it.id)
            if (m != null && m.groupValues[1] == prefix) m.groupValues[2].toIntOrNull() else null
        }.toSet()
        var next = 1
        while (next in used) next++
        return next
    }

    private fun refreshNextPointIdForCollectSheet() {
        if (fragment.pointIdPrefix != null) {
            fragment.pointIdNumericCounter = computeNextPrefixNum(fragment.pointIdPrefix!!)
        } else {
            fragment.pointCounter = computeNextNumericId()
        }
        val sheet = fragment.binding.bottomSheetCollectPoint
        if (sheet.root.visibility == View.VISIBLE) {
            val nextId =
                if (fragment.pointIdPrefix != null) "${fragment.pointIdPrefix}${fragment.pointIdNumericCounter}" else fragment.pointCounter.toString()
            sheet.etPointId.setText(nextId)
            sheet.etPointId.setHint(nextId)
        }
    }

    fun onBackPressed(): Boolean {
        if (fragment.binding.clMenu.visibility == View.VISIBLE) {
            fragment.binding.clMenu.visibility = View.GONE
            return true
        }
        if (fragment.binding.bottomSheetLineSegment.root.visibility == View.VISIBLE) {
            hideLineSegmentDetailsBottomSheet()
            return true
        }
        if (fragment.binding.bottomSheetCollectPoint.root.visibility == View.VISIBLE) {
            hideCollectPointBottomSheet()
            return true
        }
        if (fragment.binding.bottomSheetObjectList.root.visibility == View.VISIBLE) {
            hideObjectListBottomSheet()
            return true
        }
        if (fragment.binding.bottomSheetEditLine.root.visibility == View.VISIBLE) {
            hideEditLineBottomSheet()
            return true
        }
        if (fragment.binding.bottomSheetNewPoint.root.visibility == View.VISIBLE) {
            hideNewPointBottomSheet()
            return true
        }
        if (fragment.binding.bottomSheetSelectCode.root.visibility == View.VISIBLE) {
            val vf = fragment.binding.bottomSheetSelectCode.vfCodeManager
            if (vf.displayedChild == 1) {
                // Add Code view is showing — slide back to Select Code view
                hideKeyboard(fragment.binding.bottomSheetSelectCode.root)
                vf.setInAnimation(fragment.requireContext(), R.anim.slide_in_left)
                vf.setOutAnimation(fragment.requireContext(), R.anim.slide_out_right)
                vf.showPrevious()
                vf.setInAnimation(fragment.requireContext(), R.anim.slide_in_right)
                vf.setOutAnimation(fragment.requireContext(), R.anim.slide_out_left)
            } else {
                hideSelectCodeBottomSheet()
            }
            return true
        }

        if (fragment.binding.bottomSheetEditPoint.root.visibility == View.VISIBLE) {
            hideEditPointBottomSheet()
            return true
        }
        if (fragment.binding.bottomSheetNewLine.root.visibility == View.VISIBLE) {
            hideNewLineBottomSheet()
            return true
        }
        if (fragment.binding.stakeoutBottomSheet.root.visibility == View.VISIBLE) {
            fragment.helper.hideStakeoutUI()
            return true
        }
        return false
    }

    // ---------------------------------------------------------------------
    // Stakeout Logic
    // ---------------------------------------------------------------------

    private var autoAdvanceRunnable: Runnable? = null

    fun setupStakeoutMode() {
        // Initialize handler for auto-follow
        fragment.autoFollowHandler = android.os.Handler(Looper.getMainLooper())
    }

    fun startStakeoutSession(points: List<StakeoutPoint>, startIndex: Int = 0) {
        if (points.isEmpty()) return

        fragment.stakeoutSession = StakeoutSession(
            id = java.util.UUID.randomUUID().toString(),
            targetPoints = points,
            currentIndex = startIndex
        )
        // Transition to MAP_NAVIGATION initially
        fragment.currentStakeoutMode = StakeoutMode.MAP_NAVIGATION

        // Setup UI for stakeout - this will also draw the initial connection line
        fragment.helper.showStakeoutUI()

        // Trigger immediate update if location is already available
        fragment.currentLocation?.let { loc ->
            updateStakeoutMeasurements(loc.latitude, loc.longitude, loc.altitude)
        }
    }

    fun stopStakeoutSession() {
        fragment.helper.clearStakeoutMarkers()
        fragment.helper.hideBullseyeView()

        fragment.stakeoutSession = null
        fragment.currentStakeoutMode = StakeoutMode.NONE
        fragment.isInBullseyeMode = false
        fragment.connectionLineOverlay = null
        fragment.isLockMode = false

        fragment.helper.hideStakeoutUI()

        fragment.binding.mapView.invalidate()
    }

    fun updateStakeoutMeasurements(lat: Double, lon: Double, alt: Double) {
        val session = fragment.stakeoutSession ?: return
        val currentTarget = session.targetPoints.getOrNull(session.currentIndex) ?: return

        // Calculate measurements
        val currentGeo = GeoPoint(lat, lon, alt)
        val targetGeo =
            GeoPoint(currentTarget.latitude, currentTarget.longitude, currentTarget.elevation)

        // Use CoordinateUtils
        val dist = CoordinateUtils.calculateDistance(
            lat,
            lon,
            currentTarget.latitude,
            currentTarget.longitude
        )
        val bearing = CoordinateUtils.calculateBearing(
            lat,
            lon,
            currentTarget.latitude,
            currentTarget.longitude
        )
        val offsets = CoordinateUtils.calculateOffsets(
            lat,
            lon,
            currentTarget.latitude,
            currentTarget.longitude
        )
        val north = offsets.first
        val east = offsets.second
        val vertDist = CoordinateUtils.calculateVerticalDistance(
            alt,
            currentTarget.elevation,
            session.poleHeight
        )

        val measurement = StakeoutMeasurement(
            targetPointId = currentTarget.id,
            horizontalDistance = dist,
            verticalDistance = vertDist,
            northOffset = north,
            eastOffset = east,
            bearing = bearing,
            inTolerance = checkInTolerance(dist, vertDist, session.toleranceThreshold)
        )
        fragment.currentMeasurement = measurement

        // Check for mode transition (Map <-> Bullseye)
        checkStakeoutModeTransition(dist)

        // Handle auto-follow logic
        handleAutoFollow(measurement)

        // Update UI
        fragment.helper.updateStakeoutBottomSheet(measurement)

        if (isAnyInfoSheetOpen()) {
            fragment.helper.clearStakeoutMarkers()
            if (fragment.isInBullseyeMode) {
                fragment.helper.hideBullseyeView()
                // Do NOT set isInBullseyeMode to false, we want to know we were in it
            }
            return
        }

        fragment.helper.updateConnectionLine(currentGeo, targetGeo)
        fragment.helper.updateProximityCircle(targetGeo, dist < 10.0)

        // If in bullseye mode, update the overlay
        if (fragment.isInBullseyeMode) {
            // Restore bullseye view if it was hidden (e.g. after closing info sheet)
            if (fragment.bullseyeOverlay == null) {
                fragment.helper.showBullseyeView()
            }
            // Scale for precision view: e.g., 1000 pixels per meter (Zoomed out to fit 0.5m range)
            // 0.5m * 1000 = 500 pixels offset (fits on screen)
            val scale = 1000f
            fragment.bullseyeOverlay?.updateRoverPosition(
                (-east * scale).toFloat(),
                (north * scale).toFloat()
            )
            fragment.bullseyeOverlay?.verticalDistance = measurement.verticalDistance
            fragment.bullseyeOverlay?.isInTolerance = measurement.inTolerance
            fragment.binding.mapView.invalidate()
        }

    }

    fun checkInTolerance(hDist: Double, vDist: Double, tolerance: Double): Boolean {
        // Simple horizontal tolerance check
        return hDist < tolerance
    }

    fun checkStakeoutModeTransition(distanceToTarget: Double) {
        if (distanceToTarget < StakeoutConstants.BULLSEYE_TRANSITION_DISTANCE && !fragment.isInBullseyeMode) {
            // Enter Bullseye Mode
            fragment.isInBullseyeMode = true
            fragment.currentStakeoutMode = StakeoutMode.BULLSEYE_PRECISION
            fragment.helper.showBullseyeView()
        } else if (distanceToTarget > StakeoutConstants.BULLSEYE_EXIT_DISTANCE && fragment.isInBullseyeMode) {
            // Exit Bullseye Mode
            fragment.isInBullseyeMode = false
            fragment.currentStakeoutMode = StakeoutMode.MAP_NAVIGATION
            fragment.helper.hideBullseyeView()
        }
    }

    fun handleAutoFollow(measurement: StakeoutMeasurement) {
        val session = fragment.stakeoutSession ?: return
        if (!session.autoFollowEnabled) return

        if (measurement.inTolerance) {
            if (autoAdvanceRunnable == null) {
                autoAdvanceRunnable = Runnable {
                    advanceToNextStakeoutPoint()
                    autoAdvanceRunnable = null
                }
                fragment.autoFollowHandler?.postDelayed(autoAdvanceRunnable!!, 2000)
            }
        } else {
            autoAdvanceRunnable?.let {
                fragment.autoFollowHandler?.removeCallbacks(it)
                autoAdvanceRunnable = null
            }
        }
    }

    fun advanceToNextStakeoutPoint() {
        val session = fragment.stakeoutSession ?: return
        if (session.currentIndex < session.targetPoints.size - 1) {
            val nextIndex = session.currentIndex + 1
            fragment.stakeoutSession = session.copy(currentIndex = nextIndex)
            fragment.currentMeasurement = null
            // Reset auto advance runnable just in case
            autoAdvanceRunnable?.let { fragment.autoFollowHandler?.removeCallbacks(it) }
            autoAdvanceRunnable = null

            // Trigger UI update for new point (will be refined in next location update)
            fragment.currentLocation?.let { loc ->
                updateStakeoutMeasurements(loc.latitude, loc.longitude, loc.altitude)
            }
        }
    }

    fun goToPreviousStakeoutPoint() {
        val session = fragment.stakeoutSession ?: return
        if (session.currentIndex > 0) {
            val prevIndex = session.currentIndex - 1
            fragment.stakeoutSession = session.copy(currentIndex = prevIndex)
            fragment.currentMeasurement = null
            autoAdvanceRunnable?.let { fragment.autoFollowHandler?.removeCallbacks(it) }
            autoAdvanceRunnable = null

            // Trigger UI update
            fragment.currentLocation?.let { loc ->
                updateStakeoutMeasurements(loc.latitude, loc.longitude, loc.altitude)
            }
        }
    }

    private fun setupFilterDropdown(b: BottomSheetLineSegmentBinding, point: LabeledPoint) {
        b.llFilterSystem.setOnClickListener {
            // Toggle visibility
            b.cvFilterOptions.visibility =
                if (b.cvFilterOptions.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            // Rotate arrow based on state? Optional polish
            b.ivArrow.rotation = if (b.cvFilterOptions.visibility == View.VISIBLE) 270f else 90f
        }

        b.tvOptionGlobal.setOnClickListener {
            fragment.currentCoordinateSystem = CoordinateSystem.GLOBAL
            updatePointDetailsUI(b, point)
            b.cvFilterOptions.visibility = View.GONE
            b.ivArrow.rotation = 90f
        }

        b.tvOptionLocal.setOnClickListener {
            fragment.currentCoordinateSystem = CoordinateSystem.LOCAL
            updatePointDetailsUI(b, point)
            b.cvFilterOptions.visibility = View.GONE
            b.ivArrow.rotation = 90f
        }
    }

    private fun updatePointDetailsUI(b: BottomSheetLineSegmentBinding, point: LabeledPoint) {
        if (fragment.currentCoordinateSystem == CoordinateSystem.GLOBAL) {
            b.txtCs.text = "Global coordinate system"
            b.txtLocal.text = "Global"

            // Show Lat/Lon in Decimal Degrees
            b.txtLatLongE.text = String.format("%.8f", point.geoPoint.latitude)
            b.txtLatLongN.text = String.format("%.8f", point.geoPoint.longitude)
            b.txtLatLongU.text = String.format("%.3f m", point.elevation)

        } else {
            b.txtCs.text = "Local coordinate system"
            b.txtLocal.text = "Local"

            // Ideally this would be a projection conversion
            b.txtLatLongE.text =
                String.format("%.2f m E", point.geoPoint.longitude * 111320.0) // Mock conversion
            b.txtLatLongN.text =
                String.format("%.2f m N", point.geoPoint.latitude * 110574.0)  // Mock conversion
            b.txtLatLongU.text = String.format("%.2f m", point.elevation)
        }
    }


    fun showEditPointBottomSheet(
        point: LabeledPoint,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP
    ) = hideMenu {
        val sheetBinding = fragment.binding.bottomSheetEditPoint
        sheetBinding.root.elevation = 24f * fragment.resources.displayMetrics.density
        sheetBinding.root.translationZ = 24f * fragment.resources.displayMetrics.density
        sheetBinding.root.bringToFront()
        sheetBinding.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
            bottomMargin = 0
            height = 0
        }

        // Match Select Code behavior: full-height constrained sheet.
        applyFullScreenConstraints(sheetBinding.root)
        sheetBinding.root.requestLayout()

            var selectedCodeId = point.codeId

            val updateEditPointCodeUi = {
                val indicatorType =
                    if (isLineCodeFromCodeId(selectedCodeId)) IndicatorType.LINE else IndicatorType.POINT

                sheetBinding.tvCodeDescription.text =
                    getCodeDescription(selectedCodeId).ifEmpty { "No code" }
                sheetBinding.tvCodeId.text = selectedCodeId.ifEmpty { "" }
                updatePointTypeIndicator(sheetBinding.viewCodeTypeDot, indicatorType)
            }

            sheetBinding.etPointId.setText(point.id)
            sheetBinding.etPointId.setHint(point.id)
            sheetBinding.etContent.setText("")
            updateEditPointCodeUi()

            sheetBinding.llCodeValue.setOnClickListener {
                showSelectCodeBottomSheet(
                    null,
                    onlyPoints = true,
                    showNavOnCloseOverride = false
                ) { codeId, indicatorType ->
                    if (indicatorType != IndicatorType.POINT) return@showSelectCodeBottomSheet
                    selectedCodeId = codeId
                    updateEditPointCodeUi()
                }
            }

            sheetBinding.btnCloseEditPoint.setOnClickListener { hideEditPointBottomSheet() }

            sheetBinding.btnSave.setOnClickListener {
                val newId = sheetBinding.etPointId.text.toString()

                if (newId.isBlank()) {
                    Toast.makeText(
                        fragment.requireContext(),
                        "Point ID cannot be empty",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                // Update Logic
                val updatedPoint = point.copy(id = newId, codeId = selectedCodeId)
                val currentProjectId = viewModel.currentProjectId.value ?: 1L

                if (fragment.collectedLabeledPoints.any { it.id == newId && it.id != point.id }) {
                    Toast.makeText(
                        fragment.requireContext(),
                        "Point is already collected with same point id",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                // Renaming Logic: If ID changed, delete the old one first
                if (newId != point.id) {
                    viewModel.deletePointById(point.id)
                }

                viewModel.savePoint(updatedPoint.toPointEntity(currentProjectId))

                // Update in collected list (Restore Manual Logic)
                val index = fragment.collectedLabeledPoints.indexOfFirst { it.id == point.id }
                if (index != -1) {
                    fragment.collectedLabeledPoints[index] = updatedPoint
                }

                // Update selected point reference if it matches
                if (fragment.selectedPoint?.id == point.id) {
                    fragment.selectedPoint = null
                }


                // Update in marker map (re-draw)
                updateMarkersForZoom()

                hideEditPointBottomSheet()
            }

        sheetBinding.root.visibility = View.VISIBLE
        animateSheetTransition(null, sheetBinding.root, transition)
        adjustMapsButtonsForBottomSheet(overrideHeight = sheetBinding.root.height)

            // Keyboard Handling: Expand to full screen when keyboard shows (same as collect point)
            sheetBinding.root.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                private var wasOpened = false
                private var initialHeight = 0

                override fun onGlobalLayout() {
                    if (!sheetBinding.root.isAttachedToWindow) {
                        sheetBinding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        return
                    }

                    val r = Rect()
                    sheetBinding.root.getWindowVisibleDisplayFrame(r)
                    val screenHeight = sheetBinding.root.rootView.height
                    val keypadHeight = screenHeight - r.bottom

                    if (keypadHeight > screenHeight * 0.15) { // Keyboard is open
                        if (!wasOpened) {
                            if (initialHeight == 0) initialHeight = sheetBinding.root.height
                            applyFullScreenConstraints(sheetBinding.root)
                            sheetBinding.root.requestLayout()
                            wasOpened = true

                            // Disable map touch when keyboard is open
                            fragment.binding.mapView.setMultiTouchControls(false)
                            fragment.binding.mapView.overlays.filterIsInstance<org.osmdroid.views.overlay.gestures.RotationGestureOverlay>().forEach { it.isEnabled = false }
                            fragment.binding.mapView.setOnTouchListener { _, _ -> true }
                            // Hide map buttons when keyboard is open
                            fragment.binding.llMapsButtons.visibility = View.GONE
                        }
                    } else { // Keyboard is closed
                        if (wasOpened) {
                            val currentParams =
                                sheetBinding.root.layoutParams as ConstraintLayout.LayoutParams
                            if (currentParams.height == 0 && currentParams.topToTop == ConstraintLayout.LayoutParams.PARENT_ID) {
                                currentParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                currentParams.topToTop = ConstraintLayout.LayoutParams.UNSET
                                currentParams.topMargin = 0
                                sheetBinding.root.layoutParams = currentParams
                                sheetBinding.root.requestLayout()

                                // Clear focus to dismiss floating text selection menus
                                sheetBinding.root.clearFocus()
                            }
                            wasOpened = false

                            // Re-enable map touch when keyboard is closed
                            fragment.binding.mapView.setMultiTouchControls(true)
                            fragment.binding.mapView.overlays.filterIsInstance<org.osmdroid.views.overlay.gestures.RotationGestureOverlay>().forEach { it.isEnabled = true }
                            fragment.binding.mapView.setOnTouchListener(null)
                            // Show map buttons when keyboard is closed
                            fragment.binding.llMapsButtons.visibility = View.VISIBLE
                        }
                        // Ensure bottom nav stays hidden while sheet is visible (keyboard closed)
                        hideBottomNavigation()
                    }
                }
            })

        setupSwipeToDismiss(sheetBinding.root) { hideEditPointBottomSheet() }
    }

    fun hideEditPointBottomSheet(
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_DOWN,
        onHidden: (() -> Unit)? = null
    ) {
        val root = fragment.binding.bottomSheetEditPoint.root
        hideKeyboard(root)

        // Show bottom navigation immediately (no animation) before sliding down
        (fragment.activity as? MainActivity)?.binding?.bottomNavigationView?.apply {
            animate().cancel()
            visibility = View.VISIBLE
            alpha = 1f
            translationY = 0f
        }

        animateSheetTransition(root, null, transition) {
            onHidden?.invoke()
            // Re-enable map touch and show map buttons
            fragment.binding.mapView.setMultiTouchControls(true)
            fragment.binding.mapView.overlays.filterIsInstance<org.osmdroid.views.overlay.gestures.RotationGestureOverlay>()
                .forEach { it.isEnabled = true }
            fragment.binding.mapView.setOnTouchListener(null)
            fragment.binding.llMapsButtons.visibility = View.VISIBLE

            adjustMapsButtonsForBottomSheet(closingView = root)
            if (fragment.binding.bottomSheetLineSegment.root.visibility != View.VISIBLE) {
                fragment.selectedPoint = null
            }
            updateMarkersForZoom(forceRefresh = true)
            fragment.binding.mapView.invalidate()
            restoreStateAfterClosingInfoSheet()
        }
    }

    private fun hideKeyboard(view: View) {

        val imm = fragment.requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
