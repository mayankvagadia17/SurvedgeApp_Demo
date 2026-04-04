# Current State

**Plan:** Modularize MappingFragmentLogic.kt
**File:** 01-current-state.md
**Created:** 2026-04-04

---

## File Overview

**Location:** `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt`

**Current Statistics:**
- 7,029 lines of code
- 128 functions organized in a single monolithic class
- Enums: `SheetType` (12 variants), `BottomSheetTransition` (6 variants)
- Private state variables: ~40 fields (navigation stack, caches, UI state)
- No separate test files

---

## Class Structure (Current)

### Constructor
```kotlin
class MappingFragmentLogic(
    private val fragment: MappingFragment,
    private val viewModel: MappingViewModel
)
```

### Enums & Constants (Lines 116-130, 302-303)
- `SheetType` — NONE, COLLECT_POINT, LINE_SEGMENT, EDIT_LINE, EDIT_POINT, NEW_LINE, NEW_POINT, SELECT_CODE, OBJECT_LIST, SELECT_POINT, STAKEOUT, CONFIRM_DIALOG
- `BottomSheetTransition` — SLIDE_UP, SLIDE_DOWN, SLIDE_IN_RIGHT, SLIDE_OUT_LEFT, SLIDE_IN_LEFT, SLIDE_OUT_RIGHT
- `PREFS_NAME = "survedge_prefs"`
- `KEY_CUSTOM_CODES = "custom_codes"`

### Shared State Variables
- `originalEditLineState`, `originalEditLineCodeId`, `originalEditLineFeatureCode`, `isEditLineSaved`, `selectCodeSearchWatcher`
- `sheetNavigationStack`, `currentActiveSheet`
- `pointMarkersCache` (optimization cache)
- `collectedLines` (DB state tracking)
- Fragment binding references and view state

---

## Function Categories (128 total)

### Sheet Management (7 functions)
- `animateSheetTransition()` — handles animated transitions between bottom sheets
- `showSheet()` — centralized sheet display logic
- `pushBackStack()`, `popSheet()`, `clearBackStack()` — navigation stack
- `hideAllSheets()` — dismisses all sheets
- `getBindingRootForType()` (private) — maps SheetType to View binding

### Data Updates (2 functions)
- `updateLinesFromDatabase()` — refreshes line overlays
- `updatePointsFromDatabase()` — refreshes point markers and UI

### Rendering (13 functions)
- `reconstructPolylines()`, `redrawPolyline()`, `redrawPolylineAsClosed()`
- `updateMarkersForZoom()`, `bringLabelsToTop()`, `bringLocationMarkerToTop()`
- `getVisibleLabelIndices()`, `createPointOnlyBitmap()`, `createLabeledPointBitmap()`
- `updateCollectSheetLineMenuUI()`, `updatePointTypeIndicator()`, `updateLineMenuVisibility()`

### Line Operations (14 functions)
- `toggleNewLineMode()`, `showNewLineBottomSheet()`, `hideNewLineBottomSheet()`
- `showSelectPointBottomSheet()`, `hideSelectPointBottomSheet()`
- `transferPointToCurrentLine()`, `addPointToNewLine()`, `removePointFromNewLine()`
- `updateNewLineUI()`, `updateNewLineOverlay()`, `saveNewLine()`
- `advanceLineCodeForNewSegment()`, `showEditLineBottomSheet()`, `hideEditLineBottomSheet()`
- `showNewPointBottomSheet()`, `hideNewPointBottomSheet()`

### Point Operations (9 functions)
- `addPointAtLocation()`, `deletePoint()`, `deleteLineSegment()`
- `handleLineSegmentClick()`, `finalizeCurrentLineSegment()`
- `getAllPointsInCurrentLineSegment()`, `getConsecutiveLineCodePoints()`, `findNearestPoint()`

### Code Management (7 functions)
- `saveCustomCode()`, `getCustomCodes()`, `getCodeDescription()`
- `isLineCodeFromCodeId()`, `normalizeLineCode()`, `trackLineCodeUsage()`

### Sheet UI Management (13 functions)
- `showLineSegmentDetailsBottomSheet()`, `hideLineSegmentDetailsBottomSheet()`
- `showPointDetailsBottomSheet()`, `showObjectListBottomSheet()`, `hideObjectListBottomSheet()`
- `processCollectedPointsForObjectList()`
- `showCollectPointBottomSheet()`, `hideCollectPointBottomSheet()`
- `showConfirmDialogBottomSheet()`, `showSelectCodeBottomSheet()`, `hideSelectCodeBottomSheet()`
- `enforceWrapContentSheetLayout()`, `enforceFullHeightSheetLayout()`

### Location Tracking (9 functions)
- `initializeMap()`, `setupLocationTracking()`, `stopLocationUpdates()`
- `updateLocationMarker()`, `smoothMoveToTarget()`
- `createLocationPin()`, `updatePinBitmap()`, `updatePinText()`, `updatePinOrientation()`

### Gesture Handlers (7 functions)
- `setupSwipeToDismiss()`, `setupSwipeGestureForDataCollectionSettings()`
- `setupExpandableObjectListGesture()`, `setupSwipeGestureForPointLineSelection()`
- `setupSwipeGestureForEditLine()`, `setupBottomSheetClickToHideMenu()`
- `preventDoubleTapZoomOnNonMapViews()`

### Zoom & Controls (7 functions)
- `setupZoomControls()`, `animateZoom()`, `updateCompassRotation()`
- `setupCompassButton()`, `animateRotationTo()`, `setupCenterButton()`, `setupResizeButton()`

### Button Handlers (6 functions)
- `setupMenuButton()`, `cancelOngoingAnimations()`, `animateToLocationWithZoom()`
- `checkAllHidden()`, `hideMenu()`

### Navigation & UI Helpers (10 functions)
- `hideBottomNavigation()`, `showBottomNavigation()`
- `setupPointClickHandler()`, `ensurePointClickHandlerAtEnd()`, `collect()` (nested)
- `preventDoubleTapZoomOnNonMapViews()`, `adjustMapsButtonsForBottomSheet()`
- `setupEdgeToEdgeInsets()`, `findViewAt()`, `triggerButtonContainerRipple()`

### Stakeout Mode (10 functions)
- `onBackPressed()` — special lifecycle handling
- `setupStakeoutMode()`, `startStakeoutSession()`, `stopStakeoutSession()`
- `updateStakeoutMeasurements()`, `checkInTolerance()`, `checkStakeoutModeTransition()`
- `handleAutoFollow()`, `advanceToNextStakeoutPoint()`, `goToPreviousStakeoutPoint()`

### Utilities (6 functions)
- `formatTimestamp()`, `refreshPointLineData()`, `showInfo()`, `hideInfo()`
- `clearCollectedPoints()`, `updateLiveTrackingLine()`
- `hideKeyboard()` (private)

---

## Key Dependencies

**Fragment References:**
- All functions access `fragment.binding.*` for view access
- State stored in `fragment.*` properties (UI state, point collections, line data)

**ViewModel References:**
- `viewModel.savePoint()`, `viewModel.deletePointById()`, etc.
- Database operations and state persistence

**Android/OSMDroid Libraries:**
- Location services, sensor management, map overlays
- Bottom sheet animations and transitions
- Material Design components

**Internal Cross-Calls:**
- `updatePointsFromDatabase()` → calls 5+ functions (reconstructPolylines, updateCollectSheetLineMenuUI, etc.)
- Sheet transitions trigger navigation hide/show sequence
- Marker updates depend on zoom level and visibility calculations

---

## Current Issues

1. **Monolithic Size** — 7,000+ lines in single file complicates:
   - Finding related functions (manual scrolling/searching required)
   - Managing state mutations (unclear which functions affect which state)
   - Locating brace balance issues during edits (recent compilation errors: line 6202, 7032)

2. **Brace Balancing Fragility** — Recent compilation errors required manual brace fixing:
   - No structural boundaries to enforce correct nesting
   - Easy to accidentally add/remove braces during refactoring

3. **State Clarity** — ~40 private variables with no clear grouping:
   - Hard to understand which functions depend on which state
   - No enforced separation between UI state, cache state, and data state

4. **Testing Difficulty** — No way to unit test functions independently:
   - All functions access `fragment` and `viewModel` directly
   - No dependency injection pattern

5. **Initialization** — Single constructor call in MappingFragment:
   ```kotlin
   // MappingFragment.kt, line ~126
   logic = MappingFragmentLogic(this, viewModel)
   ```

---

## Compilation Status (Before Modularization)

**Fixed Errors:**
- Line 6202: Extra closing braces (removed)
- Line 7032: Missing closing brace logic (corrected)

**Current:** File should compile successfully after fixes.
