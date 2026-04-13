# Edit Line Sheet - Detailed Process Flow

## Overview

When user taps "Edit Line" on an existing line, a bottom sheet opens where user can:
- View line code (read-only: "L1")
- Toggle "Closed Line" checkbox (affects geometry - closed vs open polyline)
- See all points currently assigned to the line
- Reorder points (UI present, logic ready for drag implementation)
- Save changes or cancel

---

## Step-by-Step Flow

### 1. User Interaction Trigger

**Location:** `LineSegmentSheet.kt` → Edit button click

```kotlin
Button(onClick = { state.onEdit() }, modifier = Modifier.fillMaxWidth()) {
    Icon(Icons.Default.Edit, "Edit")
    Spacer(modifier = Modifier.width(4.dp))
    Text("Edit Line")
}
```

When user taps this "Edit Line" button:
- Calls `state.onEdit()` callback
- This callback is passed from `MappingSheetState.LineSegment.onEdit`

---

### 2. State Transition - LineSegment to EditLine

**Location:** Fragment/Logic code (not shown in current scope, but happens here)

The fragment's logic handles the callback:
```kotlin
// Pseudocode - what should happen in MappingFragmentLogic
LineSegmentSheet.onEdit() → {
    val selectedLine = currentSelectedLine  // the line being edited
    viewModel.setSheetState(
        MappingSheetState.EditLine(
            lineOverlay = selectedLine,
            lineCode = selectedLine.code,     // e.g., "L1"
            isClosedLine = selectedLine.isClosed,
            points = selectedLine.points,      // List<LabeledPoint>
            onSave = { isClosed, newPoints ->
                viewModel.saveLine(
                    LineEntity(...isClosed = isClosed...),
                    newPoints
                )
                viewModel.dismissSheet()
            },
            onClose = { viewModel.dismissSheet() }
        )
    )
}
```

**Key Transition:**
- Old sheet (LineSegmentSheet) dismisses automatically
- New state (EditLine) flows through StateFlow
- MappingSheetsHost watches sheetState and re-renders

---

### 3. ModalBottomSheet Animation

**Location:** Compose runtime

```
Previous state: LineSegmentSheet visible
                ↓
viewModel.setSheetState(EditLine(...))
                ↓
StateFlow emits new MappingSheetState.EditLine
                ↓
MappingFragment collects via collectAsStateWithLifecycle()
                ↓
MappingSheetsHost recomposes
                ↓
when (state) branch matches: is MappingSheetState.EditLine
                ↓
EditLineSheet(...) composable rendered
                ↓
ModalBottomSheet shows with 280ms slide-up animation (native Material3)
```

**No manual animation needed** — Compose ModalBottomSheet handles all transitions.

---

### 4. EditLineSheet Composition & Layout

**File:** `EditLineSheet.kt`

#### 4.1 Header Section

```
┌─────────────────────────────────────────┐
│   [spacing]  Edit Line  [spacing]  [×]  │  ← Row with title & close button
├─────────────────────────────────────────┤
│ ─────────────────────────────────────── │  ← SheetDivider (1dp line)
└─────────────────────────────────────────┘
```

**Code:**
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {
    Spacer(modifier = Modifier.weight(1f))
    Text("Edit Line", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.weight(1f))
    IconButton(onClick = { state.onClose(); onDismiss() }) {
        Icon(Icons.Default.Close, "Close")
    }
}
```

Close button calls:
1. `state.onClose()` → ViewModel dismisses sheet
2. `onDismiss()` → from MappingSheetsHost → sets state to None

---

#### 4.2 Code Display (Read-Only)

```kotlin
Text("Code: L1", style = MaterialTheme.typography.bodyMedium)
```

- Hardcoded "L1" in current implementation
- Should be `state.lineCode` for dynamic lines (L1, L2, etc.)
- **Read-only** - user cannot change line code here
- Code change (if needed) would happen in SelectCodeSheet first

---

#### 4.3 Closed Line Toggle

```
┌─────────────────────────────────────────┐
│  Closed Line              [checkbox]  ☑  │
└─────────────────────────────────────────┘
```

**Code:**
```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {
    Text("Closed Line", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.weight(1f))
    Checkbox(
        checked = isClosedLine.value,
        onCheckedChange = { isClosedLine.value = it }
    )
}
```

**State Management:**
- `isClosedLine` is a **local Compose state** (not from ViewModel initially)
- Initialized from `state.isClosedLine` at composition time
- `remember` ensures it survives recompositions while sheet is visible
- When user toggles: `isClosedLine.value = it` updates local state
- **When saving:** passes this value to `onSave(isClosedLine.value, points)`

**Meaning:**
- Checked = closed polygon (last point connects to first point)
- Unchecked = open polyline (endpoints do not connect)

---

#### 4.4 Points List

```
Points (3)
├─ ≡  P1       ← Point item with drag handle
│    Line1
├─ ≡  P2
│    Line1
└─ ≡  P3
     Line1
```

**Code:**
```kotlin
Text("Points (${state.points.size})", style = MaterialTheme.typography.labelMedium)

LazyColumn(modifier = Modifier.fillMaxWidth()) {
    itemsIndexed(state.points) { _, point ->
        PointDragItem(point)
    }
}

@Composable
private fun PointDragItem(point: LabeledPoint) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("≡", ...)  // Drag handle visual indicator
        Column {
            Text(point.id, ...)       // e.g., "P1"
            Text("Line1", ...)        // Hardcoded line association
        }
    }
}
```

**Current State:**
- ✅ LazyColumn renders all points efficiently
- ✅ Drag handle "≡" symbol visible
- ✅ Point ID displayed
- ⚠️ Reordering logic **not yet wired** - UI present but tap/drag has no effect
- ⚠️ Line association shown as hardcoded "Line1"

**What Should Happen for Drag Reorder:**
1. User long-presses or drags a PointDragItem
2. Item becomes highlighted/semi-transparent
3. As user drags up/down, items shift position
4. On drop: order saved to local `points` list
5. On Save: reordered list passed to `onSave(isClosedLine, newPoints)`

---

#### 4.5 Save Button

```kotlin
Button(
    onClick = { state.onSave(isClosedLine.value, state.points) },
    modifier = Modifier.fillMaxWidth()
) {
    Text("Save Line")
}
```

**On Click:**
1. Calls `state.onSave(isClosed: Boolean, points: List<LabeledPoint>)`
2. Fragment receives callback with:
   - `isClosed` = current checkbox state
   - `points` = point list (order matters if reordered)
3. ViewModel.saveLine() is called:
   ```kotlin
   fun saveLine(line: LineEntity, points: List<PointEntity>) {
       viewModelScope.launch(Dispatchers.IO) {
           // 1. Upsert line with updated isClosed flag
           val lineToSave = line.copy(
               pk = existingLine.pk,
               isClosed = isClosed
           )
           val linePk = lineDao.insertLine(lineToSave)
           
           // 2. Rebuild cross-references with new point order
           val crossRefs = points.mapIndexed { index, point ->
               LinePointCrossRef(
                   linePk = linePk,
                   pointPk = pointPk,
                   orderIndex = index  // Preserves order
               )
           }
           
           // 3. Clear old, insert new
           lineDao.clearLinePoints(linePk)
           lineDao.insertLinePointCrossRefs(crossRefs)
           
           updateProjectTimestamp()
       }
   )
   }
   ```
4. Sheet dismisses: `onDismiss()` → `viewModel.dismissSheet()`
5. State returns to None
6. ModalBottomSheet slides down with 280ms animation

---

## Data Structures

### MappingSheetState.EditLine

```kotlin
data class EditLine(
    val lineOverlay: ClickablePolylineOverlay? = null,      // Visual overlay on map
    val lineCode: String = "",                               // e.g., "L1", "L2"
    val isClosedLine: Boolean = false,                       // Closed or open shape
    val points: List<LabeledPoint> = emptyList(),            // All points in line
    val onSave: (Boolean, List<LabeledPoint>) -> Unit = ..., // Save callback
    val onSelectCode: () -> Unit = {},                       // Open SelectCodeSheet (unused)
    val onAddPoint: () -> Unit = {},                         // Open ObjectListSheet (unused)
    val onClose: () -> Unit = {}                             // Close sheet callback
) : MappingSheetState()
```

### LabeledPoint

```kotlin
data class LabeledPoint(
    val id: String,          // e.g., "P1", "P2"
    val lat: Double,
    val lon: Double,
    val elevation: Double,
    val code: String = ""    // e.g., "PV" (point vertex), "BL" (boundary line)
)
```

### Database Entities

**LineEntity:**
```kotlin
@Entity
data class LineEntity(
    @PrimaryKey(autoGenerate = true) val pk: Long = 0,
    val projectId: Long,
    val id: String,          // User-visible code: "L1"
    val isClosed: Boolean,   // ← What EditLine toggles
    val createdDate: Long,
    val lastModified: Long
)
```

**LinePointCrossRef:**
```kotlin
@Entity(primaryKeys = ["linePk", "pointPk"])
data class LinePointCrossRef(
    val linePk: Long,
    val pointPk: Long,
    val orderIndex: Int      // ← Preserves user's reorder
)
```

---

## State Flow Diagram

```
User taps "Edit Line" button on existing line
         ↓
LineSegmentSheet.onEdit() callback triggered
         ↓
Fragment logic creates MappingSheetState.EditLine with:
  - lineCode, isClosedLine, points list
  - onSave callback pointing to viewModel.saveLine()
  - onClose callback pointing to viewModel.dismissSheet()
         ↓
viewModel.setSheetState(editLineState)
         ↓
_sheetState: StateFlow emits new value
         ↓
MappingFragment.collectAsStateWithLifecycle() wakes up
         ↓
MappingSheetsHost recomposes, matches: is MappingSheetState.EditLine
         ↓
EditLineSheet(@Composable) renders inside ModalBottomSheet
         ↓
User sees bottom sheet with:
  - Line code (read-only)
  - Closed line toggle (local state)
  - Points list with drag handles
  - Save/Close buttons
         ↓
User modifies:
  1. Toggles "Closed Line" checkbox
     → isClosedLine.value = true/false
  2. Reorders points (when implemented)
     → points list reordered
  3. Taps Save button
         ↓
onSave(isClosedLine.value, points) called
         ↓
Fragment logic calls:
  viewModel.saveLine(lineEntity, pointList)
         ↓
ViewModel launches IO coroutine:
  1. Upsert LineEntity with isClosed flag
  2. Upsert all PointEntities
  3. Clear old LinePointCrossRef entries
  4. Insert new entries with orderIndex matching user's order
  5. Update project lastModified timestamp
         ↓
onDismiss() called → viewModel.dismissSheet()
         ↓
_sheetState = MappingSheetState.None
         ↓
MappingSheetsHost renders None (nothing)
         ↓
ModalBottomSheet slides down (280ms)
         ↓
Map overlay updates with new line geometry:
  - Line is now closed (if toggled) or open
  - Points in new order
  - Points marked as edited in database
```

---

## Key Design Patterns

### 1. Callback Pattern

Sheet doesn't know about ViewModel directly. It receives callbacks:
```kotlin
state.onSave = { isClosedLine, points ->
    // Fragment provides this lambda pointing to viewModel.saveLine()
}
```

**Benefit:** Sheet is purely UI/Compose. Fragment owns the business logic integration.

### 2. Local Compose State + ViewModel State

```kotlin
// EditLineSheet has local state for toggling within the sheet
val isClosedLine = remember { mutableStateOf(state.isClosedLine) }

// When save: converts local state to callback parameter
state.onSave(isClosedLine.value, state.points)
```

**Benefit:** Checkbox toggle is instant UI feedback. Only persisted on Save.

### 3. StateFlow-driven Navigation

No imperative `.show()` / `.hide()` methods. Just set state:
```kotlin
viewModel.setSheetState(MappingSheetState.EditLine(...))  // Show
viewModel.dismissSheet()  // Hide (sets to None)
```

**Benefit:** Single source of truth. No race conditions from overlapping animations.

### 4. Sealed Class State Machine

```kotlin
sealed class MappingSheetState {
    object None : MappingSheetState()
    data class EditLine(...) : MappingSheetState()
    data class NewLine(...) : MappingSheetState()
    // ... 10 more variants
}
```

Only one state active at a time. Compose `when` expression handles branching.

---

## Current Limitations & Future TODOs

| Limitation | Reason | Fix |
|---|---|---|
| No drag reorder | UI laid out but interaction not wired | Add PointerInput modifier + state change on drag |
| Line code hardcoded "L1" | Should be `state.lineCode` | Replace string literal with parameter |
| Point association hardcoded "Line1" | Should show actual code | Pass full line info to PointDragItem |
| No add/remove points | Not in scope for "edit" | Would need separate "Add Point to Line" flow |
| isClosedLine is local state | Not synced back to ViewModel until Save | That's by design - instant feedback, persist on Save |

---

## Testing Checklist

- [ ] Open existing line → EditLineSheet appears with correct code
- [ ] Toggle "Closed Line" checkbox → value updates visually
- [ ] See all points in list → count correct
- [ ] Tap Save without changes → line saved, sheet dismisses
- [ ] Tap Save after toggling closed → database updated with new isClosed flag
- [ ] Tap Close button → sheet dismisses without saving
- [ ] Tap back button (system/device) → sheet dismisses
- [ ] Map overlay updates → shows line is now closed/open
- [ ] (Future) Drag point up/down → reorders in list
- [ ] (Future) Save reordered list → points saved in new order

---

## Summary

**EditLineSheet is a controlled modal that lets users modify line metadata (closed/open) and see its points.** It uses:
- ModalBottomSheet for native animation
- Local Compose state for instant checkbox feedback
- Callbacks to integrate with ViewModel
- Database updates via saveLine() with order preservation
- No counters, no race conditions, native Compose lifecycle management

When user saves, the line in the database is updated with the new `isClosed` flag and (once implemented) any point reordering is persisted via `LinePointCrossRef.orderIndex`.
