# Problems & Caveats

**Plan:** Modularize MappingFragmentLogic.kt
**File:** 02-problems-and-caveats.md
**Created:** 2026-04-04

---

## Identified Risks

### 1. State Mutation Patterns

**Problem:** Functions directly mutate `fragment.*` properties without clear ownership.

**Examples:**
- `updatePointsFromDatabase()` clears and refills `fragment.collectedLabeledPoints`
- `saveNewLine()` sets `fragment.pendingNewLineSegment` and modifies line code sequences
- `handleLineSegmentClick()` sets `fragment.selectedLine` and potentially `fragment.pendingEditLineSegment`

**Caveat:** When splitting into delegates, **every delegate must receive the fragment reference** and any state mutations must go through the same reference — no duplication of state.

**Resolution:** All delegates receive `fragment` and `viewModel` in constructor. No caching of state within delegates. This maintains the existing mutation pattern while enabling modular organization.

---

### 2. Circular Function Dependencies

**Problem:** Functions in different logical domains call each other:
- `updatePointsFromDatabase()` (data domain) → `reconstructPolylines()` (rendering) → `updateMarkersForZoom()` (rendering) → `redrawPolyline()` (rendering)
- `showSheet()` (sheets) → `hideBottomNavigation()` (navigation) → `showBottomNavigation()` (navigation)
- `handleLineSegmentClick()` (point ops) → `showLineSegmentDetailsBottomSheet()` (sheet UI) → `updateCollectSheetLineMenuUI()` (rendering)

**Caveat:** Delegates cannot be purely hierarchical. A "PointOperations" delegate cannot directly call "SheetUIManagement" without creating a circular import if SheetUIManagement also calls PointOperations.

**Resolution:** 
- Keep `MappingFragmentLogic` as the orchestrator/hub
- Delegates call back to main class for cross-domain operations (e.g., `parentLogic.hideBottomNavigation()`)
- Main class never calls another delegate directly; instead, it coordinates the sequence
- All top-level entry points (`updatePointsFromDatabase`, `showSheet`, etc.) stay in main class

---

### 3. Gesture Handler Nested Objects

**Problem:** Many gesture setup functions create anonymous inner objects (LocationCallback, TextWatcher, ViewTreeObserver.OnGlobalLayoutListener) that capture local state and fragment references.

**Examples:**
- `setupLocationTracking()` creates a `LocationCallback` object that captures `fragment` and local variables
- `setupSwipeGestureForEditLine()` creates a `ViewTreeObserver.OnGlobalLayoutListener` with nested state (`wasOpened`, `savedHeight`)

**Caveat:** When extracting these functions into delegates, the nested objects must remain inline — they cannot be extracted further without deep refactoring. The delegate must instantiate and own the callback object.

**Resolution:** Gesture setup functions stay intact; no breaking apart of nested object creation. The delegate simply wraps the entire function body.

---

### 4. Fragment Binding Access (60+ binding properties)

**Problem:** Functions reference ~60 different binding properties across the fragment's various bottom sheets, map view, buttons, etc.
- `fragment.binding.bottomSheetCollectPoint.root`
- `fragment.binding.mapView.setMultiTouchControls()`
- `fragment.binding.llMapsButtons.visibility`
- etc.

**Caveat:** Every delegate **must** have direct access to `fragment` to maintain the existing binding access patterns. Removing access would require creating wrapper methods on fragment, which breaks the goal of backward compatibility.

**Resolution:** All delegates receive `private val fragment: MappingFragment` in the constructor. Binding access remains unchanged.

---

### 5. PointMarkerCache Optimization

**Problem:** `pointMarkersCache` is a shared mutable map used across rendering and zoom functions to avoid recreating marker objects on every update.

**Structure:**
```kotlin
private data class PointMarkerCache(
    val pointMarker: Marker,
    var labelMarker: Marker? = null,
    var lastIsSelected: Boolean? = null,
    // ... other fields
)
```

**Functions that access it:**
- `updateMarkersForZoom()` — reads and updates cache
- `createLabeledPointBitmap()` — creates cache entries
- Marker click handlers — check cache for matching marker

**Caveat:** Cache must remain in `MappingFragmentLogic` (not in rendering delegate) to be a true singleton across all access patterns. If cache is moved to RenderingAndUI delegate, other delegates cannot access it without back-reference to RenderingAndUI, creating circular dependencies.

**Resolution:** Keep cache in main class. Pass cache reference to delegates that need it, or keep cache update logic in main class and expose cache as a property to delegates that need read-only access.

---

### 6. EditLine State Reversion (originalEditLineState, etc.)

**Problem:** Three variables track the "original" state of a line being edited so it can be reverted if user cancels:
- `originalEditLineState: List<LabeledPoint>?`
- `originalEditLineCodeId: String?`
- `originalEditLineFeatureCode: String?`

**Usage:**
- Captured in `showEditLineBottomSheet()`
- Reverted in `hideEditLineBottomSheet()` if not saved
- Also used in keyboard handling logic within the sheet setup

**Caveat:** These three variables are tightly coupled to edit line operations. They could go into LineOperations delegate, but the reversion logic is called from `hideEditLineBottomSheet()` which is in SheetUIManagement. This creates a data ownership ambiguity.

**Resolution:** Keep these three variables in main class (MappingFragmentLogic). LineOperations delegate updates them, SheetUIManagement delegate reads them. This avoids cross-delegate data access.

---

### 7. Sheet Navigation Stack Consistency

**Problem:** `sheetNavigationStack` and `currentActiveSheet` track the navigation history and current visible sheet. They must stay synchronized with actual UI state.

**Risk Scenario:**
1. User shows Sheet A, sheet stack = [A], current = A
2. User shows Sheet B without pushing A to stack, sheet stack = [A], current = B ← mismatch!
3. User presses back, tries to pop from stack, gets A instead of B ← incorrect behavior

**Caveat:** If multiple delegates call `showSheet()` without coordinating through main class, the stack and current state can diverge. The code has some protection (checking `if (currentActiveSheet == type)` to avoid duplicates), but it's not bulletproof.

**Resolution:** All `showSheet()` calls go through main class, never directly from delegates. Delegates call back to main class (e.g., `parentLogic.showSheet(SheetType.COLLECT_POINT)`).

---

### 8. Code Search TextWatcher Registration

**Problem:** `selectCodeSearchWatcher` is a TextWatcher that's registered on the code search EditText in the SelectCode sheet. The watcher is created once and stored as a field so it can be unregistered later.

**Current Pattern:**
```kotlin
selectCodeSearchWatcher = object : TextWatcher { ... }
fragment.binding.bottomSheetSelectCode.etSearchCode.addTextChangedListener(selectCodeSearchWatcher)
```

**Caveat:** The TextWatcher reference must persist across multiple show/hide cycles of the SelectCode sheet. If the delegate is recreated or the watcher is lost, the listener registration leaks or breaks.

**Resolution:** Keep `selectCodeSearchWatcher` in main class. CodeManagement delegate calls back to main to update/register it, or delegates register listeners during setup and clean up during hide.

---

### 9. ViewModel and Fragment Initialization Timing

**Problem:** Both `fragment` and `viewModel` are passed to constructor and used immediately in setup functions. If initialization order is wrong (e.g., calling `initializeMap()` before viewModel is ready), crashes occur.

**Risk Scenario:**
- `initializeMap()` is called from fragment lifecycle
- Inside `initializeMap()`, calls to `viewModel.getLineWithPoints()` or `viewModel.savePoint()`
- If viewModel data isn't loaded yet, calls fail

**Caveat:** Delegates cannot validate that fragment and viewModel are initialized. They just assume they are. If any delegate is instantiated before fragment/viewModel setup, it will fail.

**Resolution:** Main class instantiation stays in `MappingFragment.onCreate()` or similar lifecycle method, after both fragment and viewModel are guaranteed ready. Delegates are instantiated as instance variables in main class constructor, not lazily.

---

### 10. Stakeout Mode State Machine

**Problem:** Stakeout mode has complex state:
- Active/inactive toggle
- Current point index
- Measurement calculations
- Auto-advance runnable
- Back button handling to exit

**Multiple Functions Involved:**
- `onBackPressed()` — checks stakeout state and handles back button differently
- `setupStakeoutMode()`, `startStakeoutSession()`, `stopStakeoutSession()` — state transitions
- `updateStakeoutMeasurements()`, `checkInTolerance()` — calculations
- Location tracking integration — stakeout updates when location changes

**Caveat:** Stakeout functions are scattered across multiple callbacks and lifecycle points. Extracting them to a delegate might make it look self-contained, but it's deeply integrated with location tracking and back button handling. The delegate cannot truly be self-contained without duplicating callback registrations.

**Resolution:** Create StakeoutMode delegate for stakeout-specific logic, but keep `onBackPressed()` in main class because it coordinates multiple domains (stakeout check, sheet dismissal, navigation). The delegate provides helper methods that main class calls.

---

### 11. Backward Compatibility Guarantee

**Problem:** MappingFragment currently instantiates MappingFragmentLogic in one line:
```kotlin
// MappingFragment.kt, line ~126
logic = MappingFragmentLogic(this, viewModel)
```

**Requirement:** No changes to this line.

**Caveat:** All internal delegate instantiation and coordination must happen inside MappingFragmentLogic constructor. Cannot pass additional parameters or change the initialization contract.

**Resolution:** Main class constructor creates all delegate instances. No changes to MappingFragment needed.

---

## Execution Guardrails

### Guardrail 1: No Cross-Delegate Direct Calls
- Delegates never call methods on other delegates
- Only call back to main class (parentLogic) for cross-domain operations
- Verify by: Grep for `this.` calls within delegate files — should only be `this.fragment`, `this.viewModel`, `this.parentLogic`

### Guardrail 2: State Mutations Go Through Fragment
- No state duplication in delegates
- All persistent state lives in `fragment.*` properties or main class fields (cache, stakeout state)
- Verify by: No private mutable properties in delegates that represent UI or data state

### Guardrail 3: All Top-Level Entry Points in Main Class
- `updatePointsFromDatabase()`, `updateLinesFromDatabase()`, `showSheet()`, `popSheet()`, `onBackPressed()` — these orchestrate operations
- They call delegate methods but remain in main class
- Verify by: These functions are NOT moved to delegates

### Guardrail 4: Fragment Reference Always Passed Through
- Every delegate receives `fragment` and `viewModel` in constructor
- No lazy initialization of fragment reference
- Verify by: All delegate constructors have the same signature: `(fragment: MappingFragment, viewModel: MappingViewModel, parentLogic: MappingFragmentLogic)`

### Guardrail 5: No Compilation Warnings on Extract
- After moving a function from main class to delegate, main class must still compile
- All imports must resolve in both files
- Verify by: Run `./gradlew build` after each session

---

## Dependencies & Imports Summary

**Core Android/Jetpack:**
- `android.animation.*`, `android.view.*`, `android.graphics.*`
- `androidx.constraintlayout.*`, `androidx.recyclerview.*`, `androidx.core.*`

**Google Play Services:**
- `com.google.android.gms.location.*`

**Material Design:**
- `com.google.android.material.bottomsheet.*`

**OSMDroid Mapping:**
- `org.osmdroid.*`, `org.osmdroid.views.overlay.*`

**Local Packages:**
- `com.nexova.survedge.ui.mapping.*` (adapters, overlays, drawables, viewmodel, mappers)
- `com.nexova.survedge.data.db.entity.*`
- `com.nexova.survedge.ui.stakeout.*`

**All delegates will inherit these imports.** No new external dependencies should be added.
