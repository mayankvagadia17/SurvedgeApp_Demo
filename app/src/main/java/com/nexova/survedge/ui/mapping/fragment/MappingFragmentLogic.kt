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
import com.nexova.survedge.databinding.BottomSheetNewLineBinding
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
import com.nexova.survedge.ui.mapping.maplibre.MapLibreMarkerHelper
import com.nexova.survedge.ui.mapping.maplibre.MapLibrePolylineHelper
import com.nexova.survedge.ui.mapping.overlay.LabeledPoint
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.camera.CameraUpdateFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.nexova.survedge.ui.stakeout.model.*
import com.nexova.survedge.ui.stakeout.util.*
import com.nexova.survedge.ui.mapping.viewmodel.MappingViewModel
import com.nexova.survedge.ui.mapping.mapper.toPointEntity
import com.nexova.survedge.data.db.entity.LineWithPoints
import org.maplibre.android.geometry.LatLngBounds
import com.nexova.survedge.data.db.entity.PointEntity
import com.nexova.survedge.data.db.entity.LineEntity

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
    private var editLineLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

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
            // Check if this is an input sheet that should hide nav
            when (incoming) {
                fragment.binding.bottomSheetEditPoint.root,
                fragment.binding.bottomSheetCollectPoint.root,
                fragment.binding.bottomSheetNewLine.root,
                fragment.binding.bottomSheetNewPoint.root,
                fragment.binding.bottomSheetEditLine.root,
                fragment.binding.bottomSheetSelectCode.root,
                fragment.binding.bottomSheetObjectList.root -> {
                    // Hide nav for input sheets
                    (fragment.activity as? MainActivity)?.binding?.bottomNavigationView?.apply {
                        animate().cancel()
                        visibility = View.GONE
                        alpha = 1f
                        translationY = 0f
                    }
                }
                fragment.binding.bottomSheetLineSegment.root -> {
                    // Show nav for LINE_SEGMENT (info-only sheet)
                    isNavHidden = false
                    (fragment.activity as? MainActivity)?.binding?.bottomNavigationView?.apply {
                        animate().cancel()
                        visibility = View.VISIBLE
                        alpha = 1f
                        translationY = 0f
                    }
                }
                else -> {
                    // Keep nav visible by default
                    isNavHidden = false
                    (fragment.activity as? MainActivity)?.binding?.bottomNavigationView?.apply {
                        animate().cancel()
                        visibility = View.VISIBLE
                        alpha = 1f
                        translationY = 0f
                    }
                }
            }
        } else if (outgoing != null) {
            // Show navigation when sheet closes
            isNavHidden = false
            (fragment.activity as? MainActivity)?.binding?.bottomNavigationView?.apply {
                animate().cancel()
                visibility = View.VISIBLE
                alpha = 1f
                translationY = 0f
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
        else -> null
    }


    private val PREFS_NAME = "survedge_prefs"
    private val KEY_CUSTOM_CODES = "custom_codes"

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
        val map = fragment.mapLibreMap ?: return
        
        // Build a lookup map from point ID to LabeledPoint for quick resolution
        val pointLookup = fragment.collectedLabeledPoints.associateBy { it.id }

        val linesToRender = mutableListOf<com.nexova.survedge.ui.mapping.maplibre.LineData>()

        // 1. Recreate polylines from database lines
        collectedLines.forEach { lineWithPoints ->
            val codeId = lineWithPoints.line.id
            if (codeId.isBlank() || codeId == fragment.pendingEditLineSegment) return@forEach

            // Resolve PointEntity -> LabeledPoint using the lookup, preserving DB order
            val orderedPoints = lineWithPoints.points.mapNotNull { pointEntity ->
                pointLookup[pointEntity.id]
            }

            if (orderedPoints.size >= 2) {
                linesToRender.add(
                    com.nexova.survedge.ui.mapping.maplibre.LineData(
                        id = codeId,
                        points = orderedPoints.map { it.latLng },
                        color = ContextCompat.getColor(fragment.requireContext(), R.color.slate_gray_light),
                        width = 6f,
                        isClosed = lineWithPoints.line.isClosed
                    )
                )
            }
        }
        
        // 2. MapLibre bulk update
        MapLibrePolylineHelper.updatePolylines(map, linesToRender)
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
        if (collectedLines.any { it.line.id == codeId }) return true
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
                fragment.mapLibreMap?.let { m -> MapLibrePolylineHelper.removePolyline(m, temporary) }
            }

            fragment.pendingEditLineSegment = null
            fragment.isSelectingPointForEditLine = false
            fragment.highlightedLineOverlay = null
            reconstructPolylines()
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
                    updatePointIdFromSelectedCode(codeId)
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
                        it.latLng,
                        (fragment.mapLibreMap?.cameraPosition?.zoom ?: 15.0).coerceAtLeast(18.0)
                    )
                }
                Unit
            }, onDragStart = { vh -> currentItemTouchHelper?.startDrag(vh) })

            sheetBinding.rvPoints.layoutManager = LinearLayoutManager(fragment.requireContext())
            sheetBinding.rvPoints.adapter = adapter
            // Keep dragged rows clipped within the list area so they do not appear under the Save button.
            sheetBinding.rvPoints.clipToPadding = true
            sheetBinding.rvPoints.clipChildren = true

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
                }

                override fun interpolateOutOfBoundsScroll(
                    recyclerView: RecyclerView,
                    viewSize: Int,
                    viewSizeOutOfBounds: Int,
                    totalSize: Int,
                    msSinceStartScroll: Long
                ): Int {
                    val direction = if (viewSizeOutOfBounds > 0) 1 else -1
                    val absOutOfBounds = kotlin.math.abs(viewSizeOutOfBounds)
                    val outOfBoundsRatio =
                        (absOutOfBounds.toFloat() / viewSize.toFloat()).coerceIn(0f, 1f)
                    val timeRatio = (msSinceStartScroll.toFloat() / 1000f).coerceIn(0f, 1f)
                    // More aggressive response near/beyond edges so dragging downward scrolls promptly.
                    val minScroll = 16
                    val maxScroll = 110
                    val speed =
                        (minScroll + (maxScroll - minScroll) * (0.35f + 0.65f * outOfBoundsRatio) * (0.55f + 0.45f * timeRatio)).toInt()

                    return direction * speed.coerceAtMost(maxScroll)
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
                override fun isLongPressDragEnabled() = false
            })
            currentItemTouchHelper.attachToRecyclerView(sheetBinding.rvPoints)

            // Match edit-line collapsed list height so swipe expansion/collapse behaves consistently.
            val collapsedListHeight = (170 * fragment.resources.displayMetrics.density).toInt()
            val rvParams = sheetBinding.rvPoints.layoutParams
            rvParams.height = collapsedListHeight
            sheetBinding.rvPoints.layoutParams = rvParams

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
            setupSwipeGestureForNewLine(sheetBinding.root, sheetBinding)
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
            val lineId = fragment.pendingEditLineSegment ?: return@pushBackStack
            val lineWithPoints = viewModel.currentLines.value.find { it.line.id == lineId } ?: return@pushBackStack
            showEditLineBottomSheet(lineWithPoints, BottomSheetTransition.SLIDE_DOWN, isRestoring = true)
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

            val map = fragment.mapLibreMap
            fragment.newLinePoints.clear()
            fragment.newLineOverlay?.let { layerId ->
                map?.let { m -> MapLibrePolylineHelper.removePolyline(m, layerId as String) }
            }
            fragment.newLineOverlay = null

            fragment.closingSegmentOverlay?.let { layerId ->
                map?.let { m -> MapLibrePolylineHelper.removePolyline(m, layerId as String) }
            }
            fragment.closingSegmentOverlay = null

            // Force refresh markers to show reverted state
            updateMarkersForZoom(forceRefresh = true)

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
        var inheritedCodeId: String? = null
        val projectId = viewModel.currentProjectId.value ?: return null
        
        // Find the line that contains this point
        val oldLineWithPoints = collectedLines.find { line ->
            line.line.id != fragment.pendingEditLineSegment && line.points.any { it.id == point.id }
        }

        if (oldLineWithPoints != null) {
            val remainingPoints = oldLineWithPoints.points.filter { it.id != point.id }
            
            if (remainingPoints.size < 2) {
                // Line dissolves
                inheritedCodeId = oldLineWithPoints.line.id
                
                // Clear codes for remaining orphan points in DB
                remainingPoints.forEach { orphanEntity ->
                    val updatedEntity = orphanEntity.copy(code = "")
                    viewModel.savePoint(updatedEntity)
                    
                    // Also update local cache
                    val index = fragment.collectedLabeledPoints.indexOfFirst { it.id == orphanEntity.id }
                    if (index >= 0) {
                        fragment.collectedLabeledPoints[index] = fragment.collectedLabeledPoints[index].copy(codeId = "")
                    }
                }
                
                // Delete the dissolved line
                viewModel.deleteLine(oldLineWithPoints.line)
            } else {
                // Line persists but point is removed from it
                // Logic: Since points hold the code, removing point from line means clearing its code
                // which is handled by the caller or in this function
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
        val isPointInLine = collectedLines.any { line -> line.points.any { it.id == point.id } }

        if (isPointInLine) {
            Toast.makeText(
                fragment.requireContext(),
                "Point is already part of another line",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Ensure point is not "selected" in the UI sense (orange highlight)
        if (fragment.selectedPoint == point) {
            fragment.selectedPoint = null
            // Force redraw to remove the orange highlight immediately
            updateMarkersForZoom()
        }

        // Check if point is already added to the line
        if (fragment.newLinePoints.any { it.id == point.id }) {
            // Point already exists in the line, don't add it again
            return
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
        val latLngs = fragment.newLinePoints.map { it.latLng }
        if (latLngs.size >= 2) {
            for (i in 0 until latLngs.size - 1) {
                length += latLngs[i].distanceTo(latLngs[i + 1])
            }
            if (fragment.isNewLineClosed) {
                length += latLngs.last().distanceTo(
                    latLngs.first()
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
        val map = fragment.mapLibreMap ?: return
        
        fragment.newLineOverlay?.let { layerId ->
            MapLibrePolylineHelper.removePolyline(map, layerId as String)
        }
        fragment.newLineOverlay = null
        
        fragment.closingSegmentOverlay?.let { layerId ->
            MapLibrePolylineHelper.removePolyline(map, layerId as String)
        }
        fragment.closingSegmentOverlay = null

        if (fragment.newLinePoints.size >= 2) {
            val latLngs = fragment.newLinePoints.map { it.latLng }
            val mainColor = ContextCompat.getColor(fragment.requireContext(), R.color.primary)
            
            // 1. Regular Polyline
            val layerId = "layer_line_new_preview"
            MapLibrePolylineHelper.addPolyline(
                map,
                layerId,
                latLngs,
                mainColor,
                6f,
                isClosed = false
            )
            fragment.newLineOverlay = layerId

            // 2. Closing Segment (Only if closed) - Dashed
            if (fragment.isNewLineClosed && latLngs.size >= 3) {
                val closingLayerId = "layer_line_new_closing"
                MapLibrePolylineHelper.addPolyline(
                    map,
                    closingLayerId,
                    listOf(latLngs.last(), latLngs.first()),
                    mainColor,
                    6f,
                    isClosed = false,
                    isDashed = true
                )
                fragment.closingSegmentOverlay = closingLayerId
            }
        }
    }

    fun addPointToEditLine(point: LabeledPoint) {
        if (fragment.isSelectingPointForEditLine) {
            fragment.isSelectingPointForEditLine = false
            // hideObjectListBottomSheetInternalForNewLine() // Not needed if we just update adapter
        }

        val adapter = fragment.currentEditLineAdapter
        if (adapter != null) {
            val currentPoints = adapter.getPoints()
            // Prevent duplicate points (already in the line)
            if (currentPoints.any { it.id == point.id }) return

            // Prevent adding points that are already part of another line (consistent with Add Line)
            val currentLineId = fragment.pendingEditLineSegment
            val isPointInAnotherLine = collectedLines.any { line ->
                line.line.id != currentLineId && line.points.any { it.id == point.id }
            }

            if (isPointInAnotherLine) {
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
                    fragment.pendingEditLineSegment = inheritedCode

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
        val map = fragment.mapLibreMap ?: return
        val adapter = fragment.currentEditLineAdapter ?: return
        val points = adapter.getPoints()
        val latLngs = points.map { it.latLng }
        val isClosed = fragment.currentEditLineBinding?.cbClosedLine?.isChecked == true
        val editingLineId = fragment.pendingEditLineSegment ?: ""

        // Cleanup existing preview overlays
        fragment.highlightedLineOverlay?.let { layerId ->
            MapLibrePolylineHelper.removePolyline(map, layerId as String)
        }
        fragment.highlightedLineOverlay = null
        
        fragment.closingSegmentOverlay?.let { layerId ->
            MapLibrePolylineHelper.removePolyline(map, layerId as String)
        }
        fragment.closingSegmentOverlay = null

        if (latLngs.size >= 2) {
            val mainColor = ContextCompat.getColor(fragment.requireContext(), R.color.primary)
            
            // 1. Main Segment (Solid)
            val layerId = "layer_line_edit_preview"
            MapLibrePolylineHelper.addPolyline(
                map,
                layerId,
                latLngs,
                mainColor,
                6f,
                isClosed = false
            )
            fragment.highlightedLineOverlay = layerId

            // 2. Closing Segment (Dashed)
            if (isClosed && latLngs.size >= 3) {
                val closingLayerId = "layer_line_edit_closing"
                MapLibrePolylineHelper.addPolyline(
                    map,
                    closingLayerId,
                    listOf(latLngs.last(), latLngs.first()),
                    mainColor,
                    6f,
                    isClosed = false,
                    isDashed = true
                )
                fragment.closingSegmentOverlay = closingLayerId
            }
        }
        updateMarkersForZoom(forceRefresh = true)
    }

    fun saveNewLine() {
        if (fragment.newLinePoints.size < 2) return

        // Update points to have the new Line Code
        val newCode = fragment.newLineCodeId
        val updatedLinePoints = ArrayList<LabeledPoint>()
        val projectId = viewModel.currentProjectId.value ?: 1L

        fragment.newLinePoints.forEach { point ->
            val index = fragment.collectedLabeledPoints.indexOfFirst { it.id == point.id }
            if (index != -1) {
                val oldPoint = fragment.collectedLabeledPoints[index]
                val newPoint = oldPoint.copy(codeId = newCode)
                fragment.collectedLabeledPoints[index] = newPoint
                updatedLinePoints.add(newPoint)
            } else {
                updatedLinePoints.add(point)
            }
        }

        // Save to Database
        val lineEntity = LineEntity(
            id = fragment.newLineCodeId,
            projectId = projectId,
            code = fragment.newLineCodeId.filter { it.isLetter() }.ifEmpty { "L" },
            isClosed = fragment.isNewLineClosed,
            length = 0.0 // Will be calculated by logic if needed, or by reconstruct if we store it
        )
        // Recalculate length for DB if needed
        val latLngs = updatedLinePoints.map { it.latLng }
        var length = 0.0
        for (i in 0 until latLngs.size - 1) length += latLngs[i].distanceTo(latLngs[i + 1])
        if (fragment.isNewLineClosed && latLngs.size >= 3) length += latLngs.last().distanceTo(latLngs.first())
        
        val lineToSave = lineEntity.copy(length = length)
        val pointEntities = updatedLinePoints.map { it.toPointEntity(projectId) }
        viewModel.saveLine(lineToSave, pointEntities)

        // Clear local preview overlays
        val map = fragment.mapLibreMap
        fragment.newLineOverlay?.let { overlayId -> 
            map?.let { m -> MapLibrePolylineHelper.removePolyline(m, overlayId) } 
        }
        fragment.newLineOverlay = null
        
        fragment.closingSegmentOverlay?.let { overlayId -> 
            map?.let { m -> MapLibrePolylineHelper.removePolyline(m, overlayId) } 
        }
        fragment.closingSegmentOverlay = null
        
        fragment.liveTrackingLineOverlay?.let { overlayId -> 
            map?.let { m -> MapLibrePolylineHelper.removePolyline(m, overlayId) } 
        }
        fragment.liveTrackingLineOverlay = null

        // Mark as saved
        fragment.isNewLineSaved = true

        // Reset tracking state
        fragment.lineSegmentStartIndex = fragment.collectedLabeledPoints.size

        // Hide sheet
        hideNewLineBottomSheet()
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
        location: LatLng,
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

        fragment.lastZoomLevel = fragment.mapLibreMap?.cameraPosition?.zoom ?: 15.0
        updateMarkersForZoom()
        return true
    }


    fun showDeleteLineOptionsDialog(lineWithPoints: com.nexova.survedge.data.db.entity.LineWithPoints) {
        val dialog = BottomSheetDialog(fragment.requireContext())
        dialog.setContentView(R.layout.bottom_sheet_delete_line_options)

        val btnKeepPoints = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_keep_points)
        val btnDeleteAll = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_delete_all)
        val btnCancel = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)

        btnKeepPoints?.setOnClickListener {
            deleteLineOnlyKeepPoints(lineWithPoints)
            dialog.dismiss()
        }

        btnDeleteAll?.setOnClickListener {
            deleteLineAndPoints(lineWithPoints)
            dialog.dismiss()
        }

        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    fun deleteLineOnlyKeepPoints(lineWithPoints: com.nexova.survedge.data.db.entity.LineWithPoints) {
        // 1. Remove line entity from DB
        viewModel.deleteLine(lineWithPoints.line)

        // 2. Convert line points to individual points (remove line code)
        lineWithPoints.points.forEach { pointEntity ->
            val updatedEntity = pointEntity.copy(code = "")
            viewModel.savePoint(updatedEntity)
        }

        // 3. Update local state
        val lineCode = lineWithPoints.line.id
        val pointIds = lineWithPoints.points.map { it.id }.toSet()
        fragment.collectedLabeledPoints.forEachIndexed { index, point ->
            if (point.id in pointIds && point.codeId == lineCode) {
                fragment.collectedLabeledPoints[index] = point.copy(codeId = "")
            }
        }

        // 4. Clear highlighting if needed
        val layerId = "layer_line_${lineWithPoints.line.id}"
        if (fragment.highlightedLineOverlay == layerId) {
            fragment.highlightedLineOverlay = null
        }
        
        // UI removal and marker updates are handled by observers
        hideLineSegmentDetailsBottomSheet()
        Toast.makeText(fragment.requireContext(), "Line deleted (Points kept)", Toast.LENGTH_SHORT).show()
    }

    fun deleteLineAndPoints(lineWithPoints: com.nexova.survedge.data.db.entity.LineWithPoints) {
        // 1. Remove line entity from DB
        viewModel.deleteLine(lineWithPoints.line)

        // 2. Remove points associated with this line via ViewModel
        lineWithPoints.points.forEach { pointEntity ->
            viewModel.deletePointById(pointEntity.id)
        }

        // 3. Clear state
        val layerId = "layer_line_${lineWithPoints.line.id}"
        if (fragment.highlightedLineOverlay == layerId) {
            fragment.highlightedLineOverlay = null
        }
        fragment.selectedPoint = null
        
        // UI removal is handled by reconstructPolylines and updateMarkers (triggered by DB changes)
        hideLineSegmentDetailsBottomSheet()
        Toast.makeText(fragment.requireContext(), "Line and points deleted", Toast.LENGTH_SHORT).show()
    }

    fun deleteLineSegment(lineWithPoints: com.nexova.survedge.data.db.entity.LineWithPoints) {
        deleteLineAndPoints(lineWithPoints)
    }

    fun deletePoint(point: LabeledPoint) {
        // 1. Remove from DB using ID. Local list and map updates are handled by the database observer.
        viewModel.deletePointById(point.id)

        // 2. Clear selected point if it was the one deleted
        if (fragment.selectedPoint?.id == point.id) {
            fragment.selectedPoint = null
        }

        hideLineSegmentDetailsBottomSheet()
        refreshNextPointIdForCollectSheet()
        Toast.makeText(fragment.requireContext(), "Point deleted", Toast.LENGTH_SHORT).show()
    }


    fun finalizeCurrentLineSegment(closeFlag: Boolean = fragment.isShapeClosed) {
        val lineCodePoints =
            if (fragment.currentLineCodeId != null) getAllPointsInCurrentLineSegment() else getConsecutiveLineCodePoints()

        val map = fragment.mapLibreMap
        fragment.polylineOverlay?.let { layerId ->
            map?.let { MapLibrePolylineHelper.removePolyline(it, layerId) }
        }
        fragment.polylineOverlay = null
        fragment.liveTrackingLineOverlay?.let { layerId ->
            map?.let { MapLibrePolylineHelper.removePolyline(it, layerId) }
        }
        fragment.liveTrackingLineOverlay = null

        if (lineCodePoints.size >= 2) {
            val geoPoints = lineCodePoints.map { it.latLng }
            var length = 0.0
            for (i in 0 until geoPoints.size - 1) {
                length += geoPoints[i].distanceTo(geoPoints[i + 1])
            }
            if (closeFlag) {
                length += geoPoints.last().distanceTo(geoPoints.first())
            }

            val codeId = lineCodePoints.firstOrNull()?.codeId ?: ""
            val projectId = viewModel.currentProjectId.value ?: 1L
            val existingLine = collectedLines.find { it.line.id == codeId }?.line
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
            
            // Note: UI update is handled by the database observer triggering reconstructPolylines
        }

        fragment.lineSegmentStartIndex = fragment.collectedLabeledPoints.size

        fragment.lineSegmentStartIndex = fragment.collectedLabeledPoints.size
        fragment.isShapeClosed = false
        fragment.addFromBeginning = false
        fragment.currentLineCodeId = null
        fragment.hasStartedNewLine = true
        updateMarkersForZoom()
    }

    fun handleLineSegmentClick(layerId: String) {
        // Block all map interactions when Select Code, Collect Point, or New Line sheets are open
        if (fragment.binding.bottomSheetSelectCode.root.visibility == View.VISIBLE ||
            fragment.binding.bottomSheetCollectPoint.root.visibility == View.VISIBLE ||
            fragment.binding.bottomSheetNewLine.root.visibility == View.VISIBLE) {
            return
        }

        val map = fragment.mapLibreMap ?: return
        val lineId = layerId.replace("layer_line_", "")
        val lineWithPoints = collectedLines.find { it.line.id == lineId } ?: return

        hideLineSegmentMenuThen {
            // Hide stakeout bottom sheet if it's currently visible (prevents overlap)
            if (fragment.currentStakeoutMode != StakeoutMode.NONE) {
                fragment.helper.hideStakeoutUI(showNav = false)
            }

            val previousId = fragment.highlightedLineOverlay
            if (previousId != null) {
                MapLibrePolylineHelper.unhighlightPolyline(map, previousId, ContextCompat.getColor(fragment.requireContext(), R.color.slate_gray_light))
            }

            MapLibrePolylineHelper.highlightPolyline(map, layerId, ContextCompat.getColor(fragment.requireContext(), R.color.primary))
            fragment.highlightedLineOverlay = layerId
            showLineSegmentDetailsBottomSheet(lineWithPoints)
            updateMarkersForZoom()
        }
    }

    fun showLineSegmentDetailsBottomSheet(
        lineWithPoints: com.nexova.survedge.data.db.entity.LineWithPoints,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP,
        showNav: Boolean = true
    ) = hideMenu {
        showSheet(SheetType.LINE_SEGMENT, transition) {
            fragment.selectedPoint = null
            
            val layerId = "layer_line_${lineWithPoints.line.id}"
            fragment.mapLibreMap?.let {
                MapLibrePolylineHelper.highlightPolyline(it, layerId, ContextCompat.getColor(fragment.requireContext(), R.color.primary))
            }
            fragment.highlightedLineOverlay = layerId
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
            sheetBinding.tvCodeId.text = lineWithPoints.line.id.ifEmpty { "No code" }
            sheetBinding.llCodeIdContainer.visibility = View.VISIBLE
            sheetBinding.viewTypeDot.visibility = View.GONE
            sheetBinding.txtPointId.visibility = View.GONE
            
            val pointCount = lineWithPoints.points.size
            val length = lineWithPoints.line.length
            
            sheetBinding.tvSegmentInfo.text =
                "$pointCount Points | ${String.format("%.1f M", length)}"
            sheetBinding.txtPointInfo.text = "$pointCount"
            sheetBinding.txtDistanceInfo.text = String.format("%.1f M", length)
            sheetBinding.txtSlopeDistanceInfo.text = String.format("%.1f M", length)

            sheetBinding.llContinueCollect.setOnClickListener {
                sheetBinding.clLineMenu.visibility = View.GONE
                val lastPoint = lineWithPoints.points.lastOrNull() ?: return@setOnClickListener
                fragment.selectedPointCodeId = lineWithPoints.line.id
                fragment.selectedPointIndicatorType = IndicatorType.LINE
                fragment.currentLineCodeId = lineWithPoints.line.id
                // Find index of first point with this code in the overall collected list
                fragment.lineSegmentStartIndex =
                    fragment.collectedLabeledPoints.indexOfFirst { it.codeId == lineWithPoints.line.id }
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
                    if (lineWithPoints.line.isClosed) View.GONE else View.VISIBLE
            }

            sheetBinding.llContinueCollect.visibility =
                if (lineWithPoints.line.isClosed) View.GONE else View.VISIBLE
            
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
                hideLineSegmentDetailsBottomSheet(clearState = false, showNav = false)
                showEditLineBottomSheet(lineWithPoints)
            }

            // Populate Points List
            val items = lineWithPoints.points.map { point ->
                ObjectListItem(
                    id = point.id,
                    codeId = point.code,
                    dateTime = point.ts,
                    indicatorType = IndicatorType.POINT
                )
            }
            val adapter = ObjectListAdapter(items.toMutableList(), onItemClick = { item ->
                // Handle item click if needed, e.g. center on point
                fragment.collectedLabeledPoints.find { it.id == item.id }
                    ?.let {
                        animateToLocationWithZoom(
                            it.latLng,
                            fragment.mapLibreMap?.cameraPosition?.zoom?.coerceAtLeast(18.0) ?: 18.0
                        )
                        showPointDetailsBottomSheet(it)
                    }
                Unit
            })

            sheetBinding.llDeleteLineButton.setOnClickListener {
                showDeleteLineOptionsDialog(lineWithPoints)
            }

            sheetBinding.btnStakeout.setSwipeSafeClickListener {
                sheetBinding.clLineMenu.visibility = View.GONE
                val points = lineWithPoints.points.map {
                    StakeoutPoint(
                        id = it.id,
                        name = it.code,
                        latitude = it.latitude,
                        longitude = it.longitude,
                        elevation = it.elevation,
                        isLine = true
                    )
                }

                // Manually clear selection state without triggering bottom nav show
                fragment.mapLibreMap?.let { map ->
                    fragment.highlightedLineOverlay?.let { layerId ->
                        MapLibrePolylineHelper.unhighlightPolyline(map, layerId as String)
                    }
                }
                fragment.highlightedLineOverlay = null
                fragment.selectedPoint = null
                updateMarkersForZoom()

                fragment.restoreLineSegmentAfterStakeout = lineWithPoints.line.id
                hideLineSegmentDetailsBottomSheet(clearState = false, showNav = false)
                startStakeoutSession(points)
            }

            sheetBinding.root.visibility = View.VISIBLE
            adjustMapsButtonsForBottomSheet(overrideHeight = sheetBinding.root.height)
        }
    }

    fun hidePointDetailsBottomSheet() {
        hideLineSegmentDetailsBottomSheet()
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

        val afterAnimation: () -> Unit = {
            onHidden?.invoke()
            resetLineSegmentSheetToDefaultState()
            sheetBinding.llPointLineInfo.visibility = View.GONE

            // RESTORE map interaction and buttons when closed
            fragment.binding.llMapsButtons.visibility = View.VISIBLE

            if (clearState) {
                fragment.highlightedLineOverlay?.let { layerId ->
                    fragment.mapLibreMap?.let { map ->
                        MapLibrePolylineHelper.unhighlightPolyline(map, layerId, ContextCompat.getColor(fragment.requireContext(), R.color.slate_gray_light))
                    }
                }
                val wasCollectingStr = fragment.wasCollectingBeforePointDetails
                fragment.highlightedLineOverlay = null
                fragment.wasCollectingBeforePointDetails = false
                fragment.selectedPoint = null
                updateMarkersForZoom()
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
                fragment.highlightedLineOverlay?.let { layerId ->
                    fragment.mapLibreMap?.let { map ->
                        MapLibrePolylineHelper.unhighlightPolyline(map, layerId)
                    }
                }
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
                        pushBackStack(SheetType.LINE_SEGMENT) {
                            currentActiveSheet = SheetType.LINE_SEGMENT
                        }
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
                        pushBackStack(SheetType.LINE_SEGMENT) {
                            currentActiveSheet = SheetType.LINE_SEGMENT
                        }
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
                        latitude = point.latLng.latitude,
                        longitude = point.latLng.longitude,
                        elevation = point.elevation,
                        isLine = isLineCodeFromCodeId(point.codeId)
                    )

                    // Manually clear selection state without triggering bottom nav show
                    fragment.highlightedLineOverlay?.let { layerId ->
                        fragment.mapLibreMap?.let { map ->
                            MapLibrePolylineHelper.unhighlightPolyline(map, layerId)
                        }
                    }
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
                        val lineId = fragment.pendingEditLineSegment!!
                        val lineWithPoints = viewModel.currentLines.value.find { it.line.id == lineId }
                        if (lineWithPoints != null) {
                            hideObjectListBottomSheet(showNav = false, transition = BottomSheetTransition.SLIDE_OUT_RIGHT)
                            addExistingPointToLineSegment(point, lineWithPoints)
                        }
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
                                        it.latLng,
                                        (fragment.mapLibreMap?.cameraPosition?.zoom ?: 15.0).coerceAtLeast(18.0)
                                    )
                                    showPointDetailsBottomSheet(it)
                                }
                        } else {
                            fragment.completedLineOverlays.find { it == item.codeId }
                                ?.let { lineId ->
                                    val lineWithPoints = collectedLines.find { it.line.id == lineId }
                                    lineWithPoints?.let { lp ->
                                        zoomToLine(lp.points.map { LatLng(it.latitude, it.longitude) })
                                        handleLineSegmentClick("layer_line_$lineId")
                                    }
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
                    if (sheetBinding.root.visibility != View.VISIBLE) return
                    val query = s?.toString()?.trim()?.lowercase() ?: ""
                    val filtered = if (query.isEmpty()) {
                        items
                    } else {
                        items.filter {
                            it.id.lowercase().contains(query) ||
                                it.codeId.lowercase().contains(query)
                        }
                    }
                    adapter.updateItems(filtered)
                }
            })

            }
        }
    }

    fun hideObjectListBottomSheet(
        showNav: Boolean = true,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_DOWN,
        onHidden: (() -> Unit)? = null
    ) {
        // Re-enable map touch and show map buttons
        fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(true)
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
                    // (Logic removed as ls is now an ID string, state is managed in the adapter)

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

        // 1. Process all points, grouping by line code if they belong to a line
        fragment.collectedLabeledPoints.reversed().forEach { point ->
            if (isLineCodeFromCodeId(point.codeId)) {
                if (!processedLineCodes.contains(point.codeId)) {
                    val lineWithPoints = collectedLines.find { it.line.id == point.codeId }
                    val allLinePoints = lineWithPoints?.points?.map { it.toLabeledPoint() } ?: fragment.collectedLabeledPoints.filter { it.codeId == point.codeId }
                    
                    var dist = 0.0
                    if (allLinePoints.size >= 2) {
                        for (i in 0 until allLinePoints.size - 1) {
                            dist += allLinePoints[i].latLng.distanceTo(allLinePoints[i + 1].latLng)
                        }
                    }
                    val isClosed = lineWithPoints?.line?.isClosed ?: false
                    if (isClosed && allLinePoints.size >= 2) {
                        dist += allLinePoints.last().latLng.distanceTo(allLinePoints.first().latLng)
                    }

                    val nestedItems = allLinePoints.map { lp ->
                        ObjectListItem(lp.id, lp.codeId, formatTimestamp(lp.ts), IndicatorType.POINT)
                    }
                    items.add(
                        ObjectListItem(
                            point.codeId,
                            point.codeId,
                            "${allLinePoints.size} Points | ${String.format("%.1f M", dist)}",
                            IndicatorType.LINE,
                            allLinePoints.size,
                            dist ?: 0.0,
                            nestedPoints = nestedItems
                        )
                    )
                    processedLineCodes.add(point.codeId)
                }
            } else {
                items.add(
                    ObjectListItem(point.id, point.codeId, formatTimestamp(point.ts), IndicatorType.POINT)
                )
            }
        }

        // 2. Add any lines that don't have points in the current collected list (unlikely but safe)
        collectedLines.forEach { lineWithPoints ->
            if (!processedLineCodes.contains(lineWithPoints.line.id)) {
                val allLinePoints = lineWithPoints.points.map { it.toLabeledPoint() }
                if (allLinePoints.isEmpty()) return@forEach
                
                var dist = lineWithPoints.line.length ?: 0.0
                val nestedItems = allLinePoints.map { lp ->
                    ObjectListItem(lp.id, lp.codeId, formatTimestamp(lp.ts), IndicatorType.POINT)
                }
                items.add(
                    ObjectListItem(
                        lineWithPoints.line.id,
                        lineWithPoints.line.id,
                        "${allLinePoints.size} Points | ${String.format("%.1f M", dist)}",
                        IndicatorType.LINE,
                        allLinePoints.size,
                        dist,
                        nestedPoints = nestedItems
                    )
                )
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
                if (fragment.completedLineOverlays.any { it == fragment.selectedPointCodeId } ||
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

            sheetBinding.tvPointIdLabel.visibility = View.VISIBLE
            sheetBinding.llPointIdContainer.visibility = View.VISIBLE
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
                        updatePointIdFromSelectedCode(codeId)
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

                                val map = fragment.mapLibreMap
                                fragment.polylineOverlay?.let { layerId ->
                                    map?.let { m -> MapLibrePolylineHelper.removePolyline(m, layerId as String) }
                                }
                                fragment.polylineOverlay = null

                                fragment.liveTrackingLineOverlay?.let { layerId ->
                                    map?.let { m -> MapLibrePolylineHelper.removePolyline(m, layerId as String) }
                                }
                                fragment.liveTrackingLineOverlay = null
                                fragment.lineSegmentStartIndex = fragment.collectedLabeledPoints.size
                                fragment.currentLineCodeId = null

                                refreshNextPointIdForCollectSheet()
                                updateMarkersForZoom(forceRefresh = true)
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
                                    val map = fragment.mapLibreMap
                                    fragment.polylineOverlay?.let { layerId ->
                                        map?.let { m -> MapLibrePolylineHelper.removePolyline(m, layerId as String) }
                                    }
                                    fragment.polylineOverlay = null
                                    fragment.liveTrackingLineOverlay?.let { layerId ->
                                        map?.let { m -> MapLibrePolylineHelper.removePolyline(m, layerId as String) }
                                    }
                                    fragment.liveTrackingLineOverlay = null
                                    fragment.lineSegmentStartIndex =
                                        fragment.collectedLabeledPoints.size
                                    fragment.currentLineCodeId = null
                                    updateMarkersForZoom(forceRefresh = true)
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
                val loc = fragment.currentLocation
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

        collectSheetLayoutListener?.let {
            fragment.binding.bottomSheetCollectPoint.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        collectSheetLayoutListener = null
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
                        val map = fragment.mapLibreMap
                        fragment.polylineOverlay?.let { layerId ->
                            map?.let { m -> MapLibrePolylineHelper.removePolyline(m, layerId as String) }
                        }
                        fragment.polylineOverlay = null
                        fragment.liveTrackingLineOverlay?.let { layerId ->
                            map?.let { m -> MapLibrePolylineHelper.removePolyline(m, layerId as String) }
                        }
                        fragment.liveTrackingLineOverlay = null
                        fragment.lineSegmentStartIndex = fragment.collectedLabeledPoints.size
                        fragment.currentLineCodeId = null

                        refreshNextPointIdForCollectSheet()
                        updateMarkersForZoom(forceRefresh = true)
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
        fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(true)
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
        val map = fragment.mapLibreMap ?: return
        
        fragment.polylineOverlay?.let { layerId ->
            MapLibrePolylineHelper.removePolyline(map, layerId as String)
        }
        fragment.polylineOverlay = null
        fragment.liveTrackingLineOverlay?.let { layerId ->
            MapLibrePolylineHelper.removePolyline(map, layerId as String)
        }
        fragment.liveTrackingLineOverlay = null

        val points = getConsecutiveLineCodePoints()
        if (points.size >= 2) {
            val layerId = "layer_line_current"
            MapLibrePolylineHelper.addPolyline(
                map,
                layerId,
                points.map { it.latLng },
                ContextCompat.getColor(fragment.requireContext(), R.color.slate_gray_light),
                6f,
                isClosed = fragment.isShapeClosed
            )
            fragment.polylineOverlay = layerId
        }
    }

    fun redrawPolylineAsClosed() {
        fragment.isShapeClosed = true
        redrawPolyline()
    }

    fun clearCollectedPoints() {
        val map = fragment.mapLibreMap ?: return
        
        // 1. Clear markers via helper
        MapLibreMarkerHelper.clearMarkers(map)
        // Legacy caches cleared

        // 2. Clear logical points
        fragment.collectedLabeledPoints.clear()

        // 3. Remove polylines
        fragment.polylineOverlay?.let { layerId ->
            MapLibrePolylineHelper.removePolyline(map, layerId as String)
        }
        fragment.polylineOverlay = null
        fragment.liveTrackingLineOverlay?.let { layerId ->
            MapLibrePolylineHelper.removePolyline(map, layerId as String)
        }
        fragment.liveTrackingLineOverlay = null

        // All permanent lines from DB will be cleared only if DB is cleared,
        // but here we just want to clear the "Current session" overlays if any.
        // If we want to clear ALL lines:
        MapLibrePolylineHelper.clearPolylines(map)

        fragment.highlightedLineOverlay = null
        fragment.isShapeClosed = false
        fragment.lineSegmentStartIndex = 0
        fragment.currentLineCodeId = null
    }

    fun refreshMapData() {
        updateMarkersForZoom(forceRefresh = true)
        reconstructPolylines()
    }

    fun updateMarkersForZoom(forceRefresh: Boolean = false) {
        if (fragment.isInBullseyeMode) return // Do not draw markers in Bullseye mode
        val map = fragment.mapLibreMap ?: return
        
        // Ensure all points have their icons added to the style
        fragment.collectedLabeledPoints.forEach { point ->
            val iconId = "icon_${point.id}"
            val isSelected = point.id == fragment.selectedPoint?.id
            val (bitmap, _) = createLabeledPointBitmap(point.id, point.codeId, isSelected)
            MapLibreMarkerHelper.addOrUpdateIcon(map, iconId, bitmap)
        }
        
        MapLibreMarkerHelper.updateMarkers(map, fragment.collectedLabeledPoints, fragment.selectedPoint?.id)
    }

    fun handlePointClick(point: LabeledPoint): Boolean {
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
        
        fragment.selectedPoint = point
        updateMarkersForZoom(forceRefresh = true)

        // If in stakeout mode, hide its UI temporarily
        if (fragment.currentStakeoutMode != StakeoutMode.NONE) {
            fragment.helper.hideStakeoutUI(showNav = false)
        }

        showPointDetailsBottomSheet(point)
        return true
    }


    fun bringLabelsToTop() {
        val style = fragment.mapLibreMap?.style ?: return
        style.getLayer("points_layer")?.let { layer ->
            style.removeLayer(layer)
            style.addLayer(layer)
        }
    }

    fun bringLocationMarkerToTop() {
        val style = fragment.mapLibreMap?.style ?: return
        style.getLayer("user_location_layer")?.let { layer ->
            style.removeLayer(layer)
            style.addLayer(layer)
        }
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
            val currentZoom = fragment.mapLibreMap?.cameraPosition?.zoom ?: 15.0
            val targetZoom = (currentZoom + 1.0).coerceAtMost(fragment.mapLibreMap?.maxZoomLevel ?: 22.0)
            animateZoom(currentZoom, targetZoom)
        }
        fragment.binding.imgZoomOut.setOnClickListener {
            hideLineSegmentMenuIfVisible()
            val currentZoom = fragment.mapLibreMap?.cameraPosition?.zoom ?: 15.0
            val targetZoom = (currentZoom - 1.0).coerceAtLeast(fragment.mapLibreMap?.minZoomLevel ?: 0.0)
            animateZoom(currentZoom, targetZoom)
        }
    }

    fun animateZoom(from: Double, to: Double) {
        fragment.currentMapAnimator?.cancel()
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 150; interpolator = DecelerateInterpolator()
            addUpdateListener { 
                val zoom = (from + (to - from) * (it.animatedValue as Float)).toDouble()
                fragment.mapLibreMap?.moveCamera(CameraUpdateFactory.zoomTo(zoom)) 
            }
            fragment.currentMapAnimator = this; start()
        }
    }

    fun updateCompassRotation() {
        val bearing = fragment.mapLibreMap?.cameraPosition?.bearing ?: 0.0
        fragment.binding.imgCompass.rotation = -bearing.toFloat()
    }

    fun animateRotationTo(target: Float) {
        val map = fragment.mapLibreMap ?: return
        map.animateCamera(
            org.maplibre.android.camera.CameraUpdateFactory.bearingTo(target.toDouble()),
            500,
            object : org.maplibre.android.maps.MapLibreMap.CancelableCallback {
                override fun onCancel() {}
                override fun onFinish() {
                    updateCompassRotation()
                }
            }
        )
    }

    fun setupCompassButton() {
        fragment.binding.imgCompass.setOnClickListener {
            hideLineSegmentMenuIfVisible()
            animateRotationTo(0f)
        }
    }

    fun setupCenterButton() {
        fragment.binding.imgCenter.setOnClickListener {
            hideLineSegmentMenuIfVisible()
            if (fragment.currentStakeoutMode != StakeoutMode.NONE) return@setOnClickListener

            val currentPos = fragment.mapLibreMap?.cameraPosition?.target
            val targetPos = fragment.lastLocation?.let { org.maplibre.android.geometry.LatLng(it.latitude, it.longitude) } ?: currentPos

            targetPos?.let { pos ->
                val cameraUpdate = org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(pos, 18.0)
                fragment.mapLibreMap?.animateCamera(cameraUpdate, 1000)
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
        val map = fragment.mapLibreMap ?: return
        cancelOngoingAnimations()

        val locationPoint = fragment.lastLocation?.let { org.maplibre.android.geometry.LatLng(it.latitude, it.longitude) }
        val points = fragment.collectedLabeledPoints

        if (points.isEmpty() && locationPoint == null) return

        val builder = org.maplibre.android.geometry.LatLngBounds.Builder()
        locationPoint?.let { builder.include(it) }
        points.forEach { builder.include(it.latLng) }

        try {
            val bounds = builder.build()
            val cameraUpdate = org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(bounds, 100)
            map.animateCamera(cameraUpdate, 1000)
        } catch (e: Exception) {
            // If bounds couldn't be built (e.g. no points included)
            locationPoint?.let {
                map.animateCamera(org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(it, 18.0), 1000)
            }
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
                fragment.highlightedLineOverlay?.let { layerId ->
                    fragment.mapLibreMap?.let { map ->
                        MapLibrePolylineHelper.unhighlightPolyline(map, layerId)
                    }
                }
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
        fragment.mapLibreMap?.cameraPosition // Reading position usually stops standard camera animations
    }

    fun animateToLocationWithZoom(loc: LatLng, zoom: Double) {
        val map = fragment.mapLibreMap ?: return
        val cameraPosition = org.maplibre.android.camera.CameraPosition.Builder()
            .target(loc)
            .zoom(zoom)
            .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 400)
    }

    private fun zoomToLine(points: List<LatLng>) {
        if (points.isEmpty()) return

        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(it) }
        
        try {
            val bounds = builder.build()
            fragment.mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
        } catch (e: Exception) {
            // handle empty points
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
        fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(true)
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
            val lineId = fragment.pendingEditLineSegment!!
            val lineWithPoints = viewModel.currentLines.value.find { it.line.id == lineId }
            if (lineWithPoints != null) {
                showEditLineBottomSheet(lineWithPoints)
            }
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
        // Don't show bottom nav if any INPUT sheets are visible
        // LineSegment is info-only, not an input sheet, so nav can show while it's open
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
        // Handled by MapLibre click listener in MappingFragmentHelper
    }
    
    fun preventDoubleTapZoomOnNonMapViews() {
        // Handled natively
    }

    fun ensurePointClickHandlerAtEnd() {
        // Handled by MapLibre
    }

    fun findNearestPoint(geo: LatLng): LabeledPoint? {
        val map = fragment.mapLibreMap ?: return null
        val proj = map.projection
        val p = proj.toScreenLocation(geo)
        val density = fragment.resources.displayMetrics.density
        var best: LabeledPoint? = null
        var minD = Float.MAX_VALUE

        // Find nearest point
        fragment.collectedLabeledPoints.forEach { pt ->
            val pp = proj.toScreenLocation(pt.latLng)
            val dx = p.x - pp.x
            val dy = p.y - pp.y
            val d = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (d < minD) {
                minD = d; best = pt
            }
        }

        if (best != null) {
            // Case 1: Point is a solid/direct hit (within 24dp)
            if (minD <= 24f * density) {
                return best
            }
        }

        return null
    }


    fun updateLiveTrackingLine() {
        val map = fragment.mapLibreMap ?: return
        fragment.liveTrackingLineOverlay?.let { layerId ->
            MapLibrePolylineHelper.removePolyline(map, layerId as String)
        }
        fragment.liveTrackingLineOverlay = null

        // Fix: Prevent tracking line from appearing during manual new line creation
        // or if no line is actively being collected
        if (fragment.isCreatingNewLine || fragment.currentLineCodeId == null) {
            return
        }

        val lineCodePoints = getConsecutiveLineCodePoints()

        if (lineCodePoints.isNotEmpty()) {
            val referenceLatLng = if (fragment.addFromBeginning) {
                lineCodePoints.first().latLng
            } else {
                lineCodePoints.last().latLng
            }
            val currentLatLng = fragment.currentLocation

            if (currentLatLng != null && referenceLatLng != currentLatLng && !fragment.isShapeClosed) {
                val primaryColor = ContextCompat.getColor(fragment.requireContext(), R.color.primary)
                val layerId = "layer_live_tracking"
                MapLibrePolylineHelper.addPolyline(
                    map,
                    layerId,
                    listOf(referenceLatLng, currentLatLng),
                    primaryColor,
                    6f,
                    isClosed = false,
                    isDashed = true
                )
                fragment.liveTrackingLineOverlay = layerId
            }
        }

        bringLabelsToTop()
        bringLocationMarkerToTop()
        ensurePointClickHandlerAtEnd()
    }



    fun setupSwipeToDismiss(view: View, onDismiss: () -> Unit) {
        view.isClickable = true // Ensure root consumes touches to see the full gesture
        var initialY = 0f
        var initialX = 0f
        var isDragging = false
        var startTranslationY = 0f
        var viewHeight = 0f

        val gestureListener = View.OnTouchListener { v, event ->
            // If the view can scroll up (i.e. we are not at the top), let it handle the event
            if (v.canScrollVertically(-1) && !isDragging) return@OnTouchListener false

            return@OnTouchListener when (event.action) {
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
                        true // Consume the event while dragging
                    } else {
                        false
                    }
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
                        true
                    } else {
                        isDragging = false
                        false
                    }
                }

                else -> false
            }
        }

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
                    fragment.lastLocation = it
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
                            LatLng(it.latitude, it.longitude, it.altitude),
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
        val newLocation = LatLng(lat, lon, alt)

        if (fragment.currentLocation == null) {
            fragment.currentLocation = newLocation
            fragment.targetLocation = newLocation
            createLocationPin()

            if (fragment.isFirstLocationUpdate) {
                fragment.binding.mapView.post { fitMapToPoints() }
                fragment.isFirstLocationUpdate = false
            }
            return
        }

        if (fragment.isFirstLocationUpdate) {
            fragment.currentLocation = newLocation
            fragment.targetLocation = newLocation
            fragment.mapLibreMap?.let { MapLibreMarkerHelper.updateLocationMarker(it, newLocation, "location_pin_icon") }
            fragment.binding.mapView.post { fitMapToPoints() }
            fragment.isFirstLocationUpdate = false
            return
        }

        fragment.targetLocation = newLocation

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
        val currentPos = fragment.currentLocation ?: target

        val smoothingFactor = 0.15

        val newLat = currentPos.latitude + (target.latitude - currentPos.latitude) * smoothingFactor
        val newLon =
            currentPos.longitude + (target.longitude - currentPos.longitude) * smoothingFactor
        val newAlt = currentPos.altitude + (target.altitude - currentPos.altitude) * smoothingFactor

        val smoothedPosition = LatLng(newLat, newLon, newAlt)
        fragment.currentLocation = smoothedPosition
        
        fragment.mapLibreMap?.let { map ->
            MapLibreMarkerHelper.updateLocationMarker(map, smoothedPosition, "location_pin_icon")
        }

        if (fragment.stakeoutSession != null) {
            updateStakeoutMeasurements(newLat, newLon, newAlt)
        }

        updateLiveTrackingLine()

        bringLocationMarkerToTop()

        val distance = smoothedPosition.distanceTo(target)
        if (distance < 0.01) {
            fragment.currentLocation = target
            fragment.mapLibreMap?.let { map ->
                MapLibreMarkerHelper.updateLocationMarker(map, target, "location_pin_icon")
            }
        }
    }

    fun createLocationPin(text: String = "M") {
        fragment.currentPinText = text
        updatePinBitmap()
    }

    fun updatePinBitmap() {
        if (!fragment.isAdded || fragment.context == null) return
        val map = fragment.mapLibreMap ?: return
        
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
        
        // We add the icon to the map style
        map.getStyle { style ->
            style.addImage("location_pin_icon", bm)
            
            // If location exists, update or add the marker
            fragment.lastLocation?.let { location ->
                val latLng = org.maplibre.android.geometry.LatLng(location.latitude, location.longitude)
                MapLibreMarkerHelper.updateLocationMarker(map, latLng, "location_pin_icon")
            }
        }
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

    private fun setupSwipeGestureForPointLineSelection(v: View, b: BottomSheetLineSegmentBinding) {
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
                fragment.highlightedLineOverlay?.let { layerId ->
                    val lineId = layerId.removePrefix("layer_line_")
                    val lineWithPoints = viewModel.currentLines.value.find { it.line.id == lineId }
                    lineWithPoints?.let { l ->
                        b.tvCodeIdInfo.text = l.line.id.ifEmpty { "No Code" }
                        b.txtPointInfo.text = "${l.points.size}"
                        var length = 0.0
                        if (l.points.size >= 2) {
                            for (i in 0 until l.points.size - 1) {
                                length += LatLng(l.points[i].latitude, l.points[i].longitude)
                                    .distanceTo(LatLng(l.points[i+1].latitude, l.points[i+1].longitude))
                            }
                        }
                        b.txtDistanceInfo.text = String.format("%.2f M", length)
                    }
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

                            fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(true)
                            
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
                                fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(false)
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
            if (view is RecyclerView || view is android.widget.ScrollView || view is androidx.core.widget.NestedScrollView) {
                return
            }
            view.setOnTouchListener(gestureListener)
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    attachListenerRecursively(view.getChildAt(i))
                }
            }
        }
        attachListenerRecursively(v)
    }

    fun setupSwipeGestureForNewLine(v: View, sheetBinding: BottomSheetNewLineBinding) {
        v.isClickable = true
        var initialY = 0f
        var initialX = 0f
        var isDragging = false
        var originalHeight = 0
        var startHeight = 0
        var lastEventTime = 0L

        val gestureListener = View.OnTouchListener { view, event ->
            if (view.canScrollVertically(-1) && !isDragging) return@OnTouchListener false

            if (event.eventTime == lastEventTime) return@OnTouchListener isDragging
            lastEventTime = event.eventTime

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    initialX = event.rawX
                    isDragging = false
                    startHeight = sheetBinding.rvPoints.height

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

                    val headerHeight = sheetBinding.rvPoints.top
                    val footerHeight = sheetBinding.btnSaveLine.height
                    val density = fragment.resources.displayMetrics.density
                    val topGap = (25 * density).toInt()
                    val bottomMargin = (40 * density).toInt()

                    val rawFullHeight =
                        parentHeight - (statusBarHeight + topGap + headerHeight + footerHeight + bottomMargin)
                    val fullHeight = Math.max(originalHeight, rawFullHeight)

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

                        val headerHeight = sheetBinding.rvPoints.top
                        val footerHeight = sheetBinding.btnSaveLine.height
                        val density = fragment.resources.displayMetrics.density
                        val topGap = (25 * density).toInt()
                        val bottomMargin = (40 * density).toInt()
                        val rawFullHeight =
                            parentHeight - (statusBarHeight + topGap + headerHeight + footerHeight + bottomMargin)
                        val fullHeight = Math.max(originalHeight, rawFullHeight)

                        val threshold = originalHeight + (fullHeight - originalHeight) * 0.15
                        val isQuickExpand = startHeight < originalHeight + 50 && deltaY > 100
                        val isQuickCollapse = startHeight > fullHeight - 50 && deltaY < -100

                        if ((currentHeight < threshold && !isQuickExpand) || isQuickCollapse) {
                            val anim = ValueAnimator.ofInt(currentHeight, originalHeight)
                            anim.addUpdateListener { va ->
                                val lp = sheetBinding.rvPoints.layoutParams
                                lp.height = va.animatedValue as Int
                                sheetBinding.rvPoints.layoutParams = lp
                            }
                            anim.duration = 150
                            anim.interpolator = FastOutSlowInInterpolator()
                            anim.start()

                            fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(true)
                            
                            fragment.binding.llMapsButtons.visibility = View.VISIBLE
                        } else {
                            val anim = ValueAnimator.ofInt(currentHeight, fullHeight)
                            anim.addUpdateListener { va ->
                                val lp = sheetBinding.rvPoints.layoutParams
                                lp.height = va.animatedValue as Int
                                sheetBinding.rvPoints.layoutParams = lp
                            }
                            anim.duration = 150
                            anim.interpolator = FastOutSlowInInterpolator()
                            anim.start()

                            if (fullHeight > parentHeight * 0.5) {
                                fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(false)
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
            if (view is RecyclerView || view is android.widget.ScrollView || view is androidx.core.widget.NestedScrollView) {
                return
            }
            view.setOnTouchListener(gestureListener)
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
        fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(!blockMap)
        // Note: MapLibre doesn't easily support a "dismiss on any touch" while blocked, 
        // but we can use the click listener if gestures are enabled.
        if (!blockMap) {
            fragment.mapLibreMap?.addOnMapClickListener {
                if (lineSheet.clLineMenu.visibility == View.VISIBLE) {
                    hidePointLineSelection(lineSheet)
                }
                true
            }
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
        lineWithPoints: com.nexova.survedge.data.db.entity.LineWithPoints,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP,
        isRestoring: Boolean = false
    ) = hideMenu {
        if (!isRestoring) {
            fragment.isSelectingPointForEditLine = false // Ensure we are not in selection mode
        }
        android.util.Log.d(
            "MappingLogic",
            "showEditLineBottomSheet called for codeId: ${lineWithPoints.line.id}"
        )

        fragment.pendingEditLineSegment = lineWithPoints.line.id
        reconstructPolylines() // Refresh to hide the line being edited
        // Also remove any stale overlays with the same code to avoid double-rendering while reordering.
        // Remove existing highlight
        fragment.highlightedLineOverlay?.let { layerId ->
            fragment.mapLibreMap?.let { m -> MapLibrePolylineHelper.unhighlightPolyline(m, layerId as String) }
        }
        fragment.highlightedLineOverlay = null

        hideCollectPointBottomSheet(finalizeSegment = false, showNav = false)
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
            originalEditLineState = lineWithPoints.points.map { it.toLabeledPoint() }
            originalEditLineCodeId = lineWithPoints.line.id
            originalEditLineFeatureCode = lineWithPoints.line.code
        }
        if (!isRestoring) isEditLineSaved = false

        // Resolve PointEntity -> LabeledPoint using the main list
        val pointLookup = fragment.collectedLabeledPoints.associateBy { it.id }
        val points = lineWithPoints.points.mapNotNull { pointEntity ->
            pointLookup[pointEntity.id]
        }.toMutableList()

        sheetBinding.tvCodeDescription.text =
            getCodeDescription(lineWithPoints.line.id).ifEmpty { "No code" }
        sheetBinding.tvCodeId.text = lineWithPoints.line.id.ifEmpty { "" }
        sheetBinding.cbClosedLine.isChecked = lineWithPoints.line.isClosed
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
        sheetBinding.rvPoints.clipToPadding = true
        sheetBinding.rvPoints.clipChildren = true
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
            }

            override fun interpolateOutOfBoundsScroll(
                recyclerView: RecyclerView,
                viewSize: Int,
                viewSizeOutOfBounds: Int,
                totalSize: Int,
                msSinceStartScroll: Long
            ): Int {
                val direction = if (viewSizeOutOfBounds > 0) 1 else -1
                val absOutOfBounds = kotlin.math.abs(viewSizeOutOfBounds)
                val outOfBoundsRatio =
                    (absOutOfBounds.toFloat() / viewSize.toFloat()).coerceIn(0f, 1f)
                val timeRatio = (msSinceStartScroll.toFloat() / 1000f).coerceIn(0f, 1f)

                val minScroll = 16
                val maxScroll = 110
                val speed =
                    (minScroll + (maxScroll - minScroll) * (0.35f + 0.65f * outOfBoundsRatio) * (0.55f + 0.45f * timeRatio)).toInt()

                return direction * speed.coerceAtMost(maxScroll)
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, d: Int) {}
            override fun isLongPressDragEnabled() = false
        })

        fragment.pendingEditLineSegment = lineWithPoints.line.id
        
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
            fragment.pendingEditLineSegment = lineWithPoints.line.id
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
                val oldCodeId = fragment.pendingEditLineSegment ?: ""
                fragment.pendingEditLineSegment = codeId
                updatePointIdFromSelectedCode(codeId)

                // Update all associated points in local list
                fragment.collectedLabeledPoints.forEachIndexed { index, point ->
                    if (point.codeId == oldCodeId) {
                        fragment.collectedLabeledPoints[index] = point.copy(codeId = codeId)
                    }
                }

                // Update edit line UI if visible
                fragment.currentEditLineBinding?.let { b ->
                    b.tvCodeId.text = codeId.ifEmpty { "" }
                    b.tvCodeDescription.text = getCodeDescription(codeId).ifEmpty { "No code" }
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
                val latLngs = reordered.map { it.latLng }
                var dist = 0.0
                for (i in 0 until latLngs.size - 1) dist += latLngs[i].distanceTo(latLngs[i + 1])
                if (isClosed && latLngs.size >= 3) dist += latLngs.last().distanceTo(latLngs.first())

                val currentLineId = fragment.pendingEditLineSegment ?: ""
                val ptsCorrected = reordered.map { if (it.codeId != currentLineId) it.copy(codeId = currentLineId) else it }
                
                // Update local list
                val toReorder = fragment.collectedLabeledPoints.filter { pt -> ptsCorrected.any { it.id == pt.id } }
                if (toReorder.isNotEmpty()) {
                    val firstIdx = fragment.collectedLabeledPoints.indexOfFirst { pt -> toReorder.any { it.id == pt.id } }
                    fragment.collectedLabeledPoints.removeAll(toReorder.toSet())
                    fragment.collectedLabeledPoints.addAll(firstIdx, ptsCorrected)
                }

                // Save to Database
                val projectId = viewModel.currentProjectId.value ?: 1L
                val featureCode = currentLineId.filter { it.isLetter() }.ifEmpty { "L" }
                val lineEntity = LineEntity(
                    projectId = projectId,
                    id = currentLineId,
                    code = featureCode,
                    isClosed = isClosed,
                    length = dist
                )
                val pointEntities = ptsCorrected.map { it.toPointEntity(projectId) }
                viewModel.saveLine(lineEntity, pointEntities)

                // Handle removed points
                val originalPoints = originalEditLineState ?: emptyList()
                val removedPoints = originalPoints.filter { original -> ptsCorrected.none { it.id == original.id } }
                removedPoints.forEach { removed ->
                    val updatedPoint = removed.copy(codeId = "").toPointEntity(projectId)
                    viewModel.savePoint(updatedPoint)

                    // Update local state
                    val idx = fragment.collectedLabeledPoints.indexOfFirst { it.id == removed.id }
                    if (idx >= 0) {
                        fragment.collectedLabeledPoints[idx] =
                            fragment.collectedLabeledPoints[idx].copy(codeId = "")
                    }
                }

                Toast.makeText(
                    fragment.requireContext(),
                    "Line saved successfully",
                    Toast.LENGTH_SHORT
                ).show()

                // Cleanup preview overlays
                val map = fragment.mapLibreMap
                fragment.highlightedLineOverlay?.let { map?.let { m -> MapLibrePolylineHelper.removePolyline(m, it as String) } }
                fragment.highlightedLineOverlay = null
                fragment.closingSegmentOverlay?.let { map?.let { m -> MapLibrePolylineHelper.removePolyline(m, it as String) } }
                fragment.closingSegmentOverlay = null

                updateMarkersForZoom(forceRefresh = true)
                hideEditLineBottomSheet()
                
                // Refresh map to show the saved line (it was hidden during edit)
                fragment.pendingEditLineSegment = null
                reconstructPolylines()
            }
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
        editLineLayoutListener?.let {
            sheetBinding.root.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
            private var wasOpened = false
            private var savedHeight = 0

            override fun onGlobalLayout() {
                if (!sheetBinding.root.isAttachedToWindow) {
                    sheetBinding.root.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    editLineLayoutListener = null
                    return
                }

                val r = Rect()
                sheetBinding.root.getWindowVisibleDisplayFrame(r)
                val screenHeight = sheetBinding.root.rootView.height
                val keypadHeight = screenHeight - r.bottom

                if (keypadHeight > screenHeight * 0.15) { // Keyboard open
                    if (!wasOpened) {
                        wasOpened = true
                        savedHeight = sheetBinding.root.height
                        sheetBinding.root.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                        sheetBinding.root.requestLayout()
                    }
                } else { // Keyboard closed
                    if (wasOpened) {
                        wasOpened = false
                        sheetBinding.root.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                        sheetBinding.root.requestLayout()
                    }
                }
            }
        }
        sheetBinding.root.viewTreeObserver.addOnGlobalLayoutListener(listener)
        editLineLayoutListener = listener
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
                val lineIdForRevert = fragment.pendingEditLineSegment ?: ""
                val lineCodeForRevert = originalEditLineCodeId ?: lineIdForRevert
                val originalIds = originalPoints.map { it.id }.toSet()
                val dirtyPoints = fragment.currentEditLineAdapter?.getPoints() ?: emptyList()

                // 1. Detach points that were added (in dirty but not in original)
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
                originalPoints.forEach { pt ->
                    val idx = fragment.collectedLabeledPoints.indexOfFirst { it.id == pt.id }
                    if (idx >= 0) {
                        fragment.collectedLabeledPoints[idx] =
                            fragment.collectedLabeledPoints[idx].copy(codeId = lineCodeForRevert)
                    }
                }
            }
            originalEditLineState = null // Clear state since session ended
            originalEditLineCodeId = null
            originalEditLineFeatureCode = null
        }

        val root = fragment.binding.bottomSheetEditLine.root
        editLineLayoutListener?.let {
            root.viewTreeObserver.removeOnGlobalLayoutListener(it)
        }
        editLineLayoutListener = null
        animateSheetTransition(root, null, transition) {
            if (!fragment.isSelectingPointForEditLine) {
                val map = fragment.mapLibreMap
                
                // Cleanup preview overlays
                fragment.highlightedLineOverlay?.let { layerId ->
                    map?.let { m -> MapLibrePolylineHelper.removePolyline(m, layerId as String) }
                }
                fragment.highlightedLineOverlay = null
                
                fragment.closingSegmentOverlay?.let { layerId ->
                    map?.let { m -> MapLibrePolylineHelper.removePolyline(m, layerId as String) }
                }
                fragment.closingSegmentOverlay = null
                
                fragment.pendingEditLineSegment = null
                reconstructPolylines() // Show the original line again
            }
            fragment.currentEditLineAdapter = null
            fragment.currentEditLineBinding = null
            fragment.selectedPoint = null
            isEditLineSaved = false
            updateMarkersForZoom(forceRefresh = true)
            if (fragment.binding.bottomSheetCollectPoint.root.visibility == View.GONE && fragment.currentLineCodeId != null && fragment.selectedPointIndicatorType == IndicatorType.LINE) {
                showCollectPointBottomSheet()
            } else if (showNav) {
                isNavHidden = false
                restoreStateAfterClosingInfoSheet()
            }
            onHidden?.invoke()
        }
    }

    fun showNewPointBottomSheet(
        layerId: String? = null,
        transition: BottomSheetTransition = BottomSheetTransition.SLIDE_UP,
        animate: Boolean = true
    ) = hideMenu {
        val lineSegment = layerId?.let { lid -> collectedLines.find { it.line.id == lid } }
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
            
            var defCode = fragment.selectedPointCodeId.ifEmpty { "P" }
            layerId?.let { lid ->
                collectedLines.find { it.line.id == lid }?.let {
                    defCode = it.line.id // Or however we get the code from the line
                }
            }
            
            sheetBinding.tvPointType.text = defCode
            updatePointTypeIndicator(
                sheetBinding.viewTypeDot,
                if (isLineCodeFromCodeId(defCode)) IndicatorType.LINE else IndicatorType.POINT
            )
            fragment.currentLocation?.let {
                sheetBinding.etLongitude.setText(it.longitude.toString())
                sheetBinding.etLatitude.setText(it.latitude.toString())
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
                val geo = LatLng(lat, lon)
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
            fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(false)
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
        fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(true)
        fragment.binding.llMapsButtons.visibility = View.VISIBLE

        val root = fragment.binding.bottomSheetNewPoint.root
        adjustMapsButtonsForBottomSheet(closingView = root)

        animateSheetTransition(root, null, transition) {
            restoreStateAfterClosingInfoSheet()
            updateObjectListIfVisible()
            onHidden?.invoke()
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

        val query = sheetBinding.etSearchObject.text?.toString()?.trim()?.lowercase() ?: ""
        val filtered = if (query.isEmpty()) {
            items
        } else {
            items.filter {
                it.id.lowercase().contains(query) || it.codeId.lowercase().contains(query)
            }
        }

        val adapter = sheetBinding.rvObjectList.adapter as? ObjectListAdapter
        adapter?.updateItems(filtered)
    }

    private fun addExistingPointToLineSegment(newPt: LabeledPoint, lineWithPoints: com.nexova.survedge.data.db.entity.LineWithPoints) {
        fragment.isSelectingPointForEditLine = false
        val lineId = lineWithPoints.line.id

        // Check 0: Prevent duplicate point in current line
        if (lineWithPoints.points.any { it.id == newPt.id }) {
            Toast.makeText(
                fragment.requireContext(),
                "Point is already in this line",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Check 1: Database State (Check if point is already part of another line)
        val isDbConnected = collectedLines.any { line ->
            line.line.id != lineId && line.points.any { it.id == newPt.id }
        }

        if (isDbConnected) {
            Toast.makeText(
                fragment.requireContext(),
                "Point is already part of another line",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val corrected = if (newPt.codeId != lineId) newPt.copy(codeId = lineId) else newPt
        
        // Update database: Save the point with its new code
        val projectId = viewModel.currentProjectId.value ?: 1L
        viewModel.savePoint(corrected.toPointEntity(projectId))

        // Note: UI update is handled by the database observer triggering reconstructPolylines
        
        // Update local cache and UI sheet
        val idx = fragment.collectedLabeledPoints.indexOfFirst { it.id == newPt.id }
        if (idx >= 0) fragment.collectedLabeledPoints[idx] = corrected
        
        val updatedPoints = lineWithPoints.points.toMutableList().apply { 
            // We need to convert corrected (LabeledPoint) to PointEntity or just let the observer refresh
        }
        
        // Refresh the sheet
        showEditLineBottomSheet(lineWithPoints) 
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

    private fun updatePointIdFromSelectedCode(codeId: String) {
        val regex = Regex("^([a-zA-Z][a-zA-Z ]*)(\\d+)$")
        val numRegex = Regex("^\\d+$")
        val alphaOnlyRegex = Regex("^[a-zA-Z][a-zA-Z ]*$")

        when {
            regex.matches(codeId) -> {
                val m = regex.find(codeId)
                val prefix = m?.groupValues?.get(1) ?: ""
                val number = m?.groupValues?.get(2)?.toIntOrNull() ?: 1
                fragment.pointIdPrefix = prefix
                fragment.pointIdNumericCounter = number
            }
            alphaOnlyRegex.matches(codeId) -> {
                fragment.pointIdPrefix = codeId
                fragment.pointIdNumericCounter = 1
            }
            numRegex.matches(codeId) -> {
                fragment.pointIdPrefix = null
                fragment.pointCounter = codeId.toIntOrNull() ?: 1
            }
            else -> {
                fragment.pointIdPrefix = null
                fragment.pointCounter = 1
            }
        }
        updateCollectSheetPointId()
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

    private fun updateCollectSheetPointId() {
        val sheet = fragment.binding.bottomSheetCollectPoint
        if (sheet.root.visibility == View.VISIBLE) {
            val pointId = if (fragment.pointIdPrefix != null) "${fragment.pointIdPrefix}${fragment.pointIdNumericCounter}" else fragment.pointCounter.toString()
            sheet.etPointId.setText(pointId)
            sheet.etPointId.setHint(pointId)
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

        updateMarkersForZoom()
    }

    fun updateStakeoutMeasurements(lat: Double, lon: Double, alt: Double) {
        val session = fragment.stakeoutSession ?: return
        val currentTarget = session.targetPoints.getOrNull(session.currentIndex) ?: return

        // Calculate measurements
        val currentGeo = LatLng(lat, lon, alt)
        val targetGeo =
            LatLng(currentTarget.latitude, currentTarget.longitude, currentTarget.elevation)

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
                fragment.bullseyeOverlay = fragment.binding.bullseyeView
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
            b.txtLatLongE.text = String.format("%.8f", point.latLng.latitude)
            b.txtLatLongN.text = String.format("%.8f", point.latLng.longitude)
            b.txtLatLongU.text = String.format("%.3f m", point.elevation)

        } else {
            b.txtCs.text = "Local coordinate system"
            b.txtLocal.text = "Local"

            // Ideally this would be a projection conversion
            b.txtLatLongE.text =
                String.format("%.2f m E", point.latLng.longitude * 111320.0) // Mock conversion
            b.txtLatLongN.text =
                String.format("%.2f m N", point.latLng.latitude * 110574.0)  // Mock conversion
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
                            fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(false)
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
                            fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(true)
                            // Show map buttons when keyboard is closed
                            fragment.binding.llMapsButtons.visibility = View.VISIBLE
                        }
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

        animateSheetTransition(root, null, transition) {
            onHidden?.invoke()
            // Re-enable map touch and show map buttons
            fragment.mapLibreMap?.uiSettings?.setAllGesturesEnabled(true)
            fragment.binding.llMapsButtons.visibility = View.VISIBLE

            adjustMapsButtonsForBottomSheet(closingView = root)
            if (fragment.binding.bottomSheetLineSegment.root.visibility != View.VISIBLE) {
                fragment.selectedPoint = null
            }
            updateMarkersForZoom(forceRefresh = true)
            restoreStateAfterClosingInfoSheet()
        }
    }

    private fun hideKeyboard(view: View) {

        val imm = fragment.requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun PointEntity.toLabeledPoint() = LabeledPoint(
        id = id,
        codeId = code,
        coords = listOf(longitude, latitude),
        elevation = elevation,
        ts = ts
    )
}
