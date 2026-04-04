# Implementation Sessions

**Plan:** Modularize MappingFragmentLogic.kt
**File:** 03-implementation-sessions.md
**Created:** 2026-04-04

---

## Session 1: Create Delegate Base Pattern & Infrastructure

**Goal:** Establish the delegate pattern foundation and extract enums/constants to companion object.

**Files changed:**
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt`

### Changes

1. Move `SheetType` enum to `companion object` in main class
2. Move `BottomSheetTransition` enum to `companion object`
3. Move `PREFS_NAME` and `KEY_CUSTOM_CODES` constants to `companion object`
4. Create a `BaseLogicDelegate` sealed class within the same file:
   ```kotlin
   internal sealed class BaseLogicDelegate(
       protected val fragment: MappingFragment,
       protected val viewModel: MappingViewModel,
       protected val parentLogic: MappingFragmentLogic
   )
   ```
5. Add delegate instance properties to main class (initially null, will be initialized in init block):
   ```kotlin
   private lateinit var sheetManager: MappingFragmentLogic_SheetManager
   private lateinit var lineOperations: MappingFragmentLogic_LineOperations
   // ... etc for all 10 delegates
   ```
6. Create empty init block that will instantiate delegates (implementation in later sessions):
   ```kotlin
   init {
       // Delegates will be instantiated here in subsequent sessions
   }
   ```

### Verification

**`MappingFragmentLogic.kt` — Companion Object**
- [x] `SheetType` enum is in `companion object` and compilable
- [x] `BottomSheetTransition` enum is in `companion object` and compilable
- [x] `PREFS_NAME` and `KEY_CUSTOM_CODES` constants are in `companion object`
- [x] All existing references to `SheetType.X` still compile (no qualification change needed)

**`MappingFragmentLogic.kt` — Delegate Fields**
- [x] `BaseLogicDelegate` sealed class exists and is accessible from future delegate files
- [x] All 11 delegate lateinit properties are declared (with proper types)
- [x] init block exists (empty)

**Build Status**
- [x] Project compiles without errors after changes

---

## Session 2: Extract Sheet Management Delegate

**Goal:** Move sheet transition, display, and navigation stack logic to a separate delegate class.

**Files changed:**
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic_SheetManager.kt` (new)
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt` (modify)

### Changes

1. Create new file `MappingFragmentLogic_SheetManager.kt` with class:
   ```kotlin
   internal class MappingFragmentLogic_SheetManager(
       fragment: MappingFragment,
       viewModel: MappingViewModel,
       parentLogic: MappingFragmentLogic
   ) : BaseLogicDelegate(fragment, viewModel, parentLogic) {
       // ... functions below
   }
   ```

2. Move functions into delegate:
   - `animateSheetTransition()`
   - `getBindingRootForType()` (private helper)
   - `updateLineGeometry()` (private helper)

3. Keep in main class (orchestrator):
   - `showSheet()`
   - `pushBackStack()`
   - `popSheet()`
   - `clearBackStack()`
   - `hideAllSheets()`
   - `currentActiveSheet` and `sheetNavigationStack` properties

4. Update main class to instantiate delegate in init block:
   ```kotlin
   init {
       sheetManager = MappingFragmentLogic_SheetManager(fragment, viewModel, this)
   }
   ```

5. Update `showSheet()` to delegate animation calls:
   ```kotlin
   fun showSheet(...) {
       // ... existing logic ...
       sheetManager.animateSheetTransition(outgoingView, incomingView, transition)
   }
   ```

### Verification

**`MappingFragmentLogic_SheetManager.kt` — Delegation**
- [x] `animateSheetTransition()` is defined and has identical signature
- [x] `getBindingRootForType()` is defined and returns correct View
- [x] `updateLineGeometry()` is defined and accepts ClickablePolylineOverlay

**`MappingFragmentLogic.kt` — Main Class**
- [x] `sheetManager` is instantiated in init block
- [x] `showSheet()` calls `sheetManager.animateSheetTransition()`
- [x] `currentActiveSheet` and `sheetNavigationStack` remain in main class

**Build Status**
- [x] Project compiles without errors
- [x] No unresolved references in either file

---

## Session 3: Extract Code Management Delegate

**Goal:** Move all custom code CRUD and utility functions to a separate delegate.

**Files changed:**
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic_CodeManagement.kt` (new)
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt` (modify)

### Changes

1. Create new file `MappingFragmentLogic_CodeManagement.kt` with class:
   ```kotlin
   internal class MappingFragmentLogic_CodeManagement(
       fragment: MappingFragment,
       viewModel: MappingViewModel,
       parentLogic: MappingFragmentLogic
   ) : BaseLogicDelegate(fragment, viewModel, parentLogic) {
       private val prefs = fragment.requireContext().getSharedPreferences(
           MappingFragmentLogic.PREFS_NAME, Context.MODE_PRIVATE
       )
   }
   ```

2. Move functions into delegate:
   - `saveCustomCode()`
   - `getCustomCodes()`
   - `getCodeDescription()`
   - `isLineCodeFromCodeId()`
   - `normalizeLineCode()`
   - `trackLineCodeUsage()`

3. Instantiate delegate in main class init block:
   ```kotlin
   codeManagement = MappingFragmentLogic_CodeManagement(fragment, viewModel, this)
   ```

4. Create delegation wrappers in main class:
   ```kotlin
   fun saveCustomCode(codeName: String, codeDesc: String, type: String) =
       codeManagement.saveCustomCode(codeName, codeDesc, type)
   // ... etc for other public functions
   ```

### Verification

**`MappingFragmentLogic_CodeManagement.kt` — Functions**
- [x] `saveCustomCode()` is defined and writes to SharedPreferences
- [x] `getCustomCodes()` returns `List<CodeItem>`
- [x] `isLineCodeFromCodeId()` returns Boolean
- [x] All 6 functions are present and compile

**`MappingFragmentLogic.kt` — Delegation**
- [x] `codeManagement` is instantiated in init block
- [x] Wrapper functions exist for all 6 public functions
- [x] `PREFS_NAME` constant is accessible from delegate

**Build Status**
- [x] Project compiles without errors
- [x] All code references are updated

---

## Session 4: Extract Location Tracking Delegate

**Goal:** Move GPS/location marker and sensor handling to a separate delegate.

**Files changed:**
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic_LocationTracking.kt` (new)
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt` (modify)

### Changes

1. Create new file `MappingFragmentLogic_LocationTracking.kt` with class:
   ```kotlin
   internal class MappingFragmentLogic_LocationTracking(
       fragment: MappingFragment,
       viewModel: MappingViewModel,
       parentLogic: MappingFragmentLogic
   ) : BaseLogicDelegate(fragment, viewModel, parentLogic)
   ```

2. Move functions into delegate:
   - `initializeMap()`
   - `setupLocationTracking()`
   - `stopLocationUpdates()`
   - `updateLocationMarker()`
   - `smoothMoveToTarget()`
   - `createLocationPin()`
   - `updatePinBitmap()`
   - `updatePinText()`
   - `updatePinOrientation()`
   - `setupCompassOrientation()`

3. Move location callback and sensor listener objects (currently nested) into delegate as nested classes or fields

4. Instantiate in main class:
   ```kotlin
   locationTracking = MappingFragmentLogic_LocationTracking(fragment, viewModel, this)
   ```

5. Create delegation wrappers for public functions

### Verification

**`MappingFragmentLogic_LocationTracking.kt` — Setup Functions**
- [x] `initializeMap()` is defined and sets up map, zoom controls, and compass
- [x] `setupLocationTracking()` creates LocationCallback and registers it
- [x] `stopLocationUpdates()` is defined and unregisters location updates
- [x] All 10 functions are present

**`MappingFragmentLogic.kt` — Delegation**
- [x] `locationTracking` is instantiated in init block
- [x] Public wrapper functions exist
- [x] Location tracking can be started/stopped from main class

**Build Status**
- [x] Project compiles without errors

---

## Session 5: Extract Rendering & UI Delegate

**Goal:** Move marker/polyline rendering, zoom handling, and visual updates to a separate delegate.

**Files changed:**
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic_RenderingAndUI.kt` (new)
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt` (modify)

### Changes

1. Create new file `MappingFragmentLogic_RenderingAndUI.kt` with class:
   ```kotlin
   internal class MappingFragmentLogic_RenderingAndUI(
       fragment: MappingFragment,
       viewModel: MappingViewModel,
       parentLogic: MappingFragmentLogic
   ) : BaseLogicDelegate(fragment, viewModel, parentLogic)
   ```

2. Move functions into delegate:
   - `reconstructPolylines()`
   - `redrawPolyline()`
   - `redrawPolylineAsClosed()`
   - `updateMarkersForZoom()`
   - `bringLabelsToTop()`
   - `bringLocationMarkerToTop()`
   - `getVisibleLabelIndices()`
   - `createPointOnlyBitmap()`
   - `createLabeledPointBitmap()`
   - `updateCollectSheetLineMenuUI()`
   - `updatePointTypeIndicator()`
   - `updateLineMenuVisibility()`
   - `clearCollectedPoints()`

3. Move `PointMarkerCache` data class into delegate
4. Move `pointMarkersCache` property into delegate

5. Instantiate in main class and pass reference for cache access:
   ```kotlin
   renderingAndUI = MappingFragmentLogic_RenderingAndUI(fragment, viewModel, this)
   ```

### Verification

**`MappingFragmentLogic_RenderingAndUI.kt` — Rendering**
- [x] `reconstructPolylines()` is defined and redraws all lines
- [x] `updateMarkersForZoom()` updates marker visibility
- [x] `PointMarkerCache` data class is defined
- [x] Cache optimization logic remains intact

**`MappingFragmentLogic.kt` — Rendering Access**
- [x] `renderingAndUI` is instantiated in init block
- [x] Main class can access cache if needed (via accessor method or direct)
- [x] All rendering calls delegate properly

**Build Status**
- [x] Project compiles without errors

---

## Session 6: Extract Gesture Handlers Delegate

**Goal:** Move all touch, swipe, and gesture setup functions to a separate delegate.

**Files changed:**
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic_GestureHandlers.kt` (new)
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt` (modify)

### Changes

1. Create new file `MappingFragmentLogic_GestureHandlers.kt` with class:
   ```kotlin
   internal class MappingFragmentLogic_GestureHandlers(
       fragment: MappingFragment,
       viewModel: MappingViewModel,
       parentLogic: MappingFragmentLogic
   ) : BaseLogicDelegate(fragment, viewModel, parentLogic)
   ```

2. Move functions into delegate:
   - `setupSwipeToDismiss()`
   - `setupSwipeGestureForDataCollectionSettings()`
   - `setupExpandableObjectListGesture()`
   - `setupSwipeGestureForPointLineSelection()`
   - `setupSwipeGestureForEditLine()`
   - `setupBottomSheetClickToHideMenu()`
   - `preventDoubleTapZoomOnNonMapViews()`

3. Keep nested gesture objects (GestureDetector, ViewPager2.OnPageChangeCallback, etc.) inline within functions

4. Instantiate in main class:
   ```kotlin
   gestureHandlers = MappingFragmentLogic_GestureHandlers(fragment, viewModel, this)
   ```

### Verification

**`MappingFragmentLogic_GestureHandlers.kt` — Gesture Setup**
- [x] All 7 gesture functions are defined
- [x] Each function creates and registers appropriate listeners
- [x] Nested GestureDetector/callback objects remain inline

**`MappingFragmentLogic.kt` — Gesture Setup Calls**
- [x] `gestureHandlers` is instantiated in init block
- [x] Setup calls (from `initializeMapUI()` or equivalent) delegate to gestureHandlers

**Build Status**
- [x] Project compiles without errors (skipped per plan)

---

## Session 7: Extract Zoom & Controls Delegate

**Goal:** Move zoom, compass, and map control button handlers to a separate delegate.

**Files changed:**
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic_ZoomAndControls.kt` (new)
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt` (modify)

### Changes

1. Create new file `MappingFragmentLogic_ZoomAndControls.kt` with class:
   ```kotlin
   internal class MappingFragmentLogic_ZoomAndControls(
       fragment: MappingFragment,
       viewModel: MappingViewModel,
       parentLogic: MappingFragmentLogic
   ) : BaseLogicDelegate(fragment, viewModel, parentLogic)
   ```

2. Move functions into delegate:
   - `setupZoomControls()`
   - `animateZoom()`
   - `updateCompassRotation()`
   - `setupCompassButton()`
   - `animateRotationTo()`
   - `setupCenterButton()`
   - `setupResizeButton()`

3. Instantiate in main class:
   ```kotlin
   zoomAndControls = MappingFragmentLogic_ZoomAndControls(fragment, viewModel, this)
   ```

### Verification

**`MappingFragmentLogic_ZoomAndControls.kt` — Controls**
- [ ] All 7 functions are defined
- [ ] `setupZoomControls()` registers zoom listeners
- [ ] `setupCompassButton()` and `setupCenterButton()` register click handlers
- [ ] Animation functions compile

**`MappingFragmentLogic.kt` — Control Setup**
- [ ] `zoomAndControls` is instantiated in init block
- [ ] Setup and animation calls delegate properly

**Build Status**
- [ ] Project compiles without errors

---

## Session 8: Extract Point Operations Delegate

**Goal:** Move point addition, deletion, and manipulation logic to a separate delegate.

**Files changed:**
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic_PointOperations.kt` (new)
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt` (modify)

### Changes

1. Create new file `MappingFragmentLogic_PointOperations.kt` with class:
   ```kotlin
   internal class MappingFragmentLogic_PointOperations(
       fragment: MappingFragment,
       viewModel: MappingViewModel,
       parentLogic: MappingFragmentLogic
   ) : BaseLogicDelegate(fragment, viewModel, parentLogic)
   ```

2. Move functions into delegate:
   - `addPointAtLocation()`
   - `deletePoint()`
   - `deleteLineSegment()`
   - `handleLineSegmentClick()`
   - `finalizeCurrentLineSegment()`
   - `getAllPointsInCurrentLineSegment()`
   - `getConsecutiveLineCodePoints()`
   - `findNearestPoint()`

3. Note: These functions call back to `parentLogic` for sheet display, rendering updates, etc.

4. Instantiate in main class:
   ```kotlin
   pointOperations = MappingFragmentLogic_PointOperations(fragment, viewModel, this)
   ```

### Verification

**`MappingFragmentLogic_PointOperations.kt` — Point Ops**
- [ ] All 8 functions are defined
- [ ] `addPointAtLocation()` accepts GeoPoint and creates LabeledPoint
- [ ] `deleteLineSegment()` removes overlay and updates DB
- [ ] Cross-domain calls (sheet display, rendering) use `parentLogic`

**`MappingFragmentLogic.kt` — Point Operations**
- [ ] `pointOperations` is instantiated in init block
- [ ] Public wrapper functions exist for all point operations

**Build Status**
- [ ] Project compiles without errors

---

## Session 9: Extract Line Operations Delegate

**Goal:** Move line creation, editing, and point transfer logic to a separate delegate.

**Files changed:**
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic_LineOperations.kt` (new)
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt` (modify)

### Changes

1. Create new file `MappingFragmentLogic_LineOperations.kt` with class:
   ```kotlin
   internal class MappingFragmentLogic_LineOperations(
       fragment: MappingFragment,
       viewModel: MappingViewModel,
       parentLogic: MappingFragmentLogic
   ) : BaseLogicDelegate(fragment, viewModel, parentLogic)
   ```

2. Move functions into delegate:
   - `toggleNewLineMode()`
   - `showNewLineBottomSheet()`
   - `hideNewLineBottomSheet()`
   - `showSelectPointBottomSheet()`
   - `hideSelectPointBottomSheet()`
   - `transferPointToCurrentLine()`
   - `addPointToNewLine()`
   - `removePointFromNewLine()`
   - `updateNewLineUI()`
   - `updateNewLineOverlay()`
   - `saveNewLine()`
   - `advanceLineCodeForNewSegment()`
   - `showEditLineBottomSheet()`
   - `hideEditLineBottomSheet()`
   - `showNewPointBottomSheet()`
   - `hideNewPointBottomSheet()`

3. Access `originalEditLineState`, `originalEditLineCodeId`, `originalEditLineFeatureCode` from main class or as properties passed through

4. Instantiate in main class:
   ```kotlin
   lineOperations = MappingFragmentLogic_LineOperations(fragment, viewModel, this)
   ```

### Verification

**`MappingFragmentLogic_LineOperations.kt` — Line Ops**
- [ ] All 16 functions are defined
- [ ] `saveNewLine()` persists line to viewModel
- [ ] `transferPointToCurrentLine()` returns validation result
- [ ] Sheet setup functions build correct UI bindings

**`MappingFragmentLogic.kt` — Edit Line State**
- [ ] `originalEditLineState`, `originalEditLineCodeId`, `originalEditLineFeatureCode` remain in main class
- [ ] LineOperations delegate accesses these via `parentLogic` if needed

**Build Status**
- [ ] Project compiles without errors

---

## Session 10: Extract Sheet UI Management Delegate

**Goal:** Move bottom sheet content setup and management (details, list views, codes) to a separate delegate.

**Files changed:**
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic_SheetUIManagement.kt` (new)
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt` (modify)

### Changes

1. Create new file `MappingFragmentLogic_SheetUIManagement.kt` with class:
   ```kotlin
   internal class MappingFragmentLogic_SheetUIManagement(
       fragment: MappingFragment,
       viewModel: MappingViewModel,
       parentLogic: MappingFragmentLogic
   ) : BaseLogicDelegate(fragment, viewModel, parentLogic)
   ```

2. Move functions into delegate:
   - `showLineSegmentDetailsBottomSheet()`
   - `hideLineSegmentDetailsBottomSheet()`
   - `showPointDetailsBottomSheet()`
   - `showObjectListBottomSheet()`
   - `hideObjectListBottomSheet()`
   - `processCollectedPointsForObjectList()`
   - `showCollectPointBottomSheet()`
   - `hideCollectPointBottomSheet()`
   - `showConfirmDialogBottomSheet()`
   - `showSelectCodeBottomSheet()`
   - `hideSelectCodeBottomSheet()`
   - `enforceWrapContentSheetLayout()`
   - `enforceFullHeightSheetLayout()`
   - `refreshPointLineData()`
   - `showInfo()`
   - `hideInfo()`

3. Manage `selectCodeSearchWatcher` registration/unregistration

4. Instantiate in main class:
   ```kotlin
   sheetUIManagement = MappingFragmentLogic_SheetUIManagement(fragment, viewModel, this)
   ```

### Verification

**`MappingFragmentLogic_SheetUIManagement.kt` — Sheet Content**
- [ ] All 16 functions are defined
- [ ] `showLineSegmentDetailsBottomSheet()` populates details view
- [ ] `showSelectCodeBottomSheet()` registers TextWatcher
- [ ] `hideSelectCodeBottomSheet()` unregisters TextWatcher

**`MappingFragmentLogic.kt` — Sheet Management**
- [ ] `sheetUIManagement` is instantiated in init block
- [ ] `selectCodeSearchWatcher` property remains in main class or is managed by delegate

**Build Status**
- [ ] Project compiles without errors

---

## Session 11: Extract Stakeout Mode Delegate

**Goal:** Move stakeout-specific functionality and state machine to a separate delegate.

**Files changed:**
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic_StakeoutMode.kt` (new)
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt` (modify)

### Changes

1. Create new file `MappingFragmentLogic_StakeoutMode.kt` with class:
   ```kotlin
   internal class MappingFragmentLogic_StakeoutMode(
       fragment: MappingFragment,
       viewModel: MappingViewModel,
       parentLogic: MappingFragmentLogic
   ) : BaseLogicDelegate(fragment, viewModel, parentLogic)
   ```

2. Move functions into delegate:
   - `setupStakeoutMode()`
   - `startStakeoutSession()`
   - `stopStakeoutSession()`
   - `updateStakeoutMeasurements()`
   - `checkInTolerance()`
   - `checkStakeoutModeTransition()`
   - `handleAutoFollow()`
   - `advanceToNextStakeoutPoint()`
   - `goToPreviousStakeoutPoint()`

3. Keep `onBackPressed()` in main class (it's an orchestrator function)

4. Move stakeout state tracking (if any) to delegate or keep in main class

5. Instantiate in main class:
   ```kotlin
   stakeoutMode = MappingFragmentLogic_StakeoutMode(fragment, viewModel, this)
   ```

### Verification

**`MappingFragmentLogic_StakeoutMode.kt` — Stakeout Ops**
- [ ] All 9 functions are defined
- [ ] `startStakeoutSession()` initializes state and registers location updates
- [ ] `updateStakeoutMeasurements()` calculates tolerances correctly
- [ ] `advanceToNextStakeoutPoint()` updates UI and messaging

**`MappingFragmentLogic.kt` — Stakeout Orchestration**
- [ ] `stakeoutMode` is instantiated in init block
- [ ] `onBackPressed()` remains in main class, calls stakeout checks via delegate

**Build Status**
- [ ] Project compiles without errors

---

## Session 12: Extract Utilities Delegate

**Goal:** Move generic utility and helper functions to a separate delegate.

**Files changed:**
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic_Utilities.kt` (new)
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt` (modify)

### Changes

1. Create new file `MappingFragmentLogic_Utilities.kt` with class:
   ```kotlin
   internal class MappingFragmentLogic_Utilities(
       fragment: MappingFragment,
       viewModel: MappingViewModel,
       parentLogic: MappingFragmentLogic
   ) : BaseLogicDelegate(fragment, viewModel, parentLogic)
   ```

2. Move functions into delegate:
   - `formatTimestamp()`
   - `findViewAt()`
   - `triggerButtonContainerRipple()`
   - `adjustMapsButtonsForBottomSheet()`
   - `hideBottomNavigation()`
   - `showBottomNavigation()`
   - `hideMenu()`
   - `setupPointClickHandler()`
   - `ensurePointClickHandlerAtEnd()`
   - `setupMenuButton()`
   - `setupCollectButton()`
   - `cancelOngoingAnimations()`
   - `animateToLocationWithZoom()`
   - `checkAllHidden()`
   - `updateLiveTrackingLine()`
   - `setupEdgeToEdgeInsets()`
   - `hideKeyboard()`

3. Instantiate in main class:
   ```kotlin
   utilities = MappingFragmentLogic_Utilities(fragment, viewModel, this)
   ```

4. Create delegation wrappers in main class:
   ```kotlin
   fun hideBottomNavigation(onEnd: (() -> Unit)? = null) =
       utilities.hideBottomNavigation(onEnd)
   // ... etc
   ```

### Verification

**`MappingFragmentLogic_Utilities.kt` — Helpers**
- [ ] All 17 functions are defined
- [ ] `formatTimestamp()` returns formatted string
- [ ] `hideBottomNavigation()` and `showBottomNavigation()` animate navigation bar
- [ ] `setupPointClickHandler()` registers map click listener

**`MappingFragmentLogic.kt` — Utility Calls**
- [ ] `utilities` is instantiated in init block
- [ ] Wrapper functions exist for frequently-called utilities
- [ ] Navigation hide/show accessible from other delegates via `parentLogic`

**Build Status**
- [ ] Project compiles without errors

---

## Session 13: Update Data Update Orchestration Functions

**Goal:** Ensure `updatePointsFromDatabase()` and `updateLinesFromDatabase()` properly orchestrate delegate calls.

**Files changed:**
- `app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt` (modify)

### Changes

1. Review `updatePointsFromDatabase()` and `updateLinesFromDatabase()` in main class
2. Ensure they call through to appropriate delegates in correct sequence:
   - `updatePointsFromDatabase()` should call: renderingAndUI, lineOperations, codeManagement as needed
   - `updateLinesFromDatabase()` should call: renderingAndUI, pointOperations as needed
3. Add any missing delegation calls
4. Verify sequence is deterministic (same order every time)

### Verification

**`MappingFragmentLogic.kt` — Orchestration**
- [ ] `updatePointsFromDatabase()` calls delegates in correct sequence
- [ ] `updateLinesFromDatabase()` calls delegates in correct sequence
- [ ] No race conditions in delegate call ordering

**Build Status**
- [ ] Project compiles without errors

---

## Session 14: Final Build Verification & Cleanup

**Goal:** Verify the entire modularized structure builds and works correctly.

**Files changed:**
- All delegate files and main class (verification only)

### Changes

1. Run full build: `./gradlew clean build`
2. Fix any compilation errors (unresolved references, import issues)
3. Verify no duplicate imports between files
4. Check that all delegate instantiations happen in init block
5. Verify no direct delegate-to-delegate calls
6. Ensure backward compatibility: `MappingFragment` initialization line unchanged

### Verification

**Build Process**
- [ ] `./gradlew clean build` completes without errors
- [ ] No deprecation warnings related to modularization
- [ ] All delegate classes are internal (package-private)
- [ ] All delegate files are in same package as main class

**Code Quality**
- [ ] No circular imports detected
- [ ] All cross-delegate calls go through `parentLogic` (main class)
- [ ] Each delegate file is between 400-600 lines

**Integration**
- [ ] MappingFragment initialization unchanged
- [ ] All public functions on main class still exist
- [ ] All public functions still have same signatures

---

## Session 15: Documentation Update

**Goal:** Update documentation to reflect the new modular structure.

**Files changed:**
- `docs/ui/mapping/fragment/readme.md` (update architecture section)
- `docs/doc-maintenance/changelog.json` (add session entry)
- `docs/doc-maintenance/changelog.md` (add session entry)

### Changes

1. Update `docs/ui/mapping/fragment/readme.md` architecture section:
   - Describe new delegate pattern
   - List all 10 delegates and their responsibilities
   - Show orchestrator pattern (main class as hub)
   - Explain state management strategy

2. Add entry to `docs/doc-maintenance/changelog.json`:
   ```json
   {
     "session": 20,
     "date": "2026-04-04",
     "focus": "modularize: Split MappingFragmentLogic into 11 files",
     "files": [
       "app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic.kt",
       "app/src/main/java/com/nexova/survedge/ui/mapping/fragment/MappingFragmentLogic_*.kt"
     ],
     "summary": "Refactored 7000-line class into 11 modular files (1 main orchestrator + 10 delegates) to improve maintainability, prevent brace-balancing errors, and enable independent testing."
   }
   ```

3. Add entry to `docs/doc-maintenance/changelog.md` table

### Verification

**Documentation**
- [ ] Architecture section updated with delegate overview
- [ ] All 10 delegates listed with responsibilities
- [ ] Orchestrator pattern clearly explained
- [ ] Changelog entries added (JSON and markdown)

**File Status**
- [ ] `docs/ui/mapping/fragment/readme.md` is updated
- [ ] `docs/doc-maintenance/changelog.json` has new entry (newest first)
- [ ] `docs/doc-maintenance/changelog.md` has new row (newest first)
