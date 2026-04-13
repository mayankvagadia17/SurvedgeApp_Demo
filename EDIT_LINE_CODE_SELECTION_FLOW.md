# Edit Line Code Selection Flow

## Updated EditLineSheet.kt Implementation

### What Changed

The **Code** section in EditLineSheet is now **fully interactive**:

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { state.onSelectCode() }  // ← Now clickable!
        .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Column(modifier = Modifier.weight(1f)) {
        Text("Code", style = MaterialTheme.typography.labelMedium)
        Text(
            state.lineCode.ifEmpty { "Select Code" },  // ← Dynamic code display
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Icon(
        Icons.AutoMirrored.Filled.ArrowForward,  // ← Indicator for interaction
        contentDescription = "Select Code",
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

### Key Features

| Feature | Details |
|---------|---------|
| **Clickable Row** | User can tap anywhere in the Code row to select a new code |
| **Dynamic Display** | Shows `state.lineCode` (updates when code changes), fallback "Select Code" |
| **Arrow Icon** | Visual indicator that the element is interactive |
| **Full Width** | Entire row is tappable, not just the text |
| **Padding** | 12.dp padding for comfortable touch target |

---

## Complete Flow: User Edits Line Code

### 1. EditLineSheet Displays Current Code

```
User opens EditLineSheet for line "L1"
    ↓
State passed: EditLine(lineCode = "L1", onSelectCode = {...}, ...)
    ↓
UI renders:
┌───────────────────────────────┐
│ Code                    →     │  ← Clickable row with arrow
│ L1                            │
└───────────────────────────────┘
```

---

### 2. User Taps Code Row

```
User taps the Code row
    ↓
onClick = { state.onSelectCode() }  triggered
    ↓
onSelectCode callback is invoked
    ↓
Fragment logic receives callback:
    Fragment.onSelectCode() {
        // Create SelectCodeSheet state
        val selectCodeState = MappingSheetState.SelectCode(
            onCodeSelect = { selectedCode ->
                // User picked a code from library
                // e.g., "PV" (Point Vertex) or "BL" (Boundary Line)
                handleCodeSelected(selectedCode)
            }
        )
        // Show SelectCodeSheet
        viewModel.setSheetState(selectCodeState)
    }
```

---

### 3. SelectCodeSheet Opens (Sheet Stacking)

```
EditLineSheet (current, visible behind)
    ↓
New ModalBottomSheet appears on top:
SelectCodeSheet (now visible)
    ↓
User sees code list:
┌────────────────────────────┐
│ Select Code         [×]    │
├────────────────────────────┤
│ [Search box]               │
├────────────────────────────┤
│ PV   Point Vertex  (point) │
│ BL   Boundary Line (line)  │
│ CL   Center Line   (line)  │
│ ST   Structure     (point) │
└────────────────────────────┘
```

**Current Implementation Note:**
- SelectCodeSheet is **not yet wired** to actually switch from EditLineSheet
- In full implementation, the fragment's logic will:
  1. Show SelectCodeSheet with code selection callback
  2. When user selects a code, get the CodeItem (e.g., "BL", "Boundary Line")
  3. Update the EditLineSheet state with new code
  4. Show EditLineSheet again with updated lineCode value

---

### 4. User Selects a Code

```
User taps "BL   Boundary Line" code
    ↓
SelectCodeSheet.onCodeSelect callback triggered with:
    CodeItem("BL", "Boundary Line", IndicatorType.LINE)
    ↓
Fragment logic receives the selected code:
    Fragment.handleCodeSelected(codeItem) {
        // Now we have the new code: "BL"
        val currentEditLineState = viewModel.sheetState.value as? EditLine
        if (currentEditLineState != null) {
            // Create updated EditLineSheet state with new code
            val updatedState = currentEditLineState.copy(
                lineCode = codeItem.abbreviation  // "BL"
            )
            // Show updated EditLineSheet
            viewModel.setSheetState(updatedState)
        }
    }
```

---

### 5. EditLineSheet Updates with New Code

```
SelectCodeSheet dismisses automatically (or explicitly)
    ↓
viewModel.setSheetState(updatedEditLineState)
    ↓
StateFlow emits new state
    ↓
MappingSheetsHost recomposes
    ↓
when (state) matches EditLineSheet
    ↓
EditLineSheet recomposes with:
    lineCode = "BL"  (was "L1")
    ↓
UI updates:
┌───────────────────────────────┐
│ Code                    →     │
│ BL   (updated!)               │  ← New code displayed
└───────────────────────────────┘
```

---

### 6. User Saves Line with New Code

```
User modifies other properties (e.g., toggle "Closed Line")
    ↓
User taps "Save Line" button
    ↓
onClick = { state.onSave(isClosedLine.value, state.points) }
    ↓
Fragment logic calls:
    viewModel.saveLine(
        lineEntity = LineEntity(
            id = "BL",          // ← New code becomes line ID
            isClosed = true,
            ...
        ),
        points = [P1, P2, P3]
    )
    ↓
ViewModel.saveLine() runs in IO coroutine:
    1. Upsert LineEntity with id="BL", isClosed=true
    2. Upsert all PointEntities (unchanged)
    3. Clear old LinePointCrossRef entries
    4. Insert new entries with orderIndex
    5. Update project lastModified
    ↓
Database now has:
    LineEntity(id="BL", isClosed=true, ...)  ← Code updated
    ↓
Sheet dismisses
    ↓
Map overlay updates visually:
    - Line geometry updates if closed/open changed
    - Line code/label shown as "BL" instead of "L1"
```

---

## Point Code Propagation (Advanced Feature)

### Current State
When a line code changes from "L1" to "BL", the UI **should also update** the codes displayed on associated points.

### How It Should Work

```
Line "L1" has points: P1, P2, P3
Each point displays: "L1" (their line association)
    ↓
User changes line code to "BL"
    ↓
In saveLine() callback:
    // Option 1: Update point codes directly
    points.forEach { point ->
        viewModel.savePoint(point.copy(code = "BL"))
    }
    
    // Option 2: Just save the line code, let rendering layer show it
    // (Points keep their own codes, line code is separate)
```

### Current Implementation Status
- ✅ Code row is now clickable and triggers `onSelectCode()`
- ✅ EditLineSheet displays dynamic `state.lineCode`
- ⚠️ **Point code propagation not yet implemented** — needs fragment logic wiring
- ⚠️ **Sheet stacking from EditLine → SelectCode → EditLine** not yet wired

### What Needs to Be Done in Fragment Logic

In `MappingFragmentLogic.kt` (or equivalent), implement:

```kotlin
// When user taps Code in EditLineSheet
private fun editLineOnSelectCode(currentState: MappingSheetState.EditLine) {
    val selectCodeState = MappingSheetState.SelectCode(
        initialSearch = "",
        codes = availableCodes,  // Load from database/local
        onCodeSelect = { selectedCode ->
            // 1. Update line code in current state
            val updatedEditLineState = currentState.copy(
                lineCode = selectedCode.abbreviation
            )
            // 2. Show updated EditLineSheet
            viewModel.setSheetState(updatedEditLineState)
        }
    )
    viewModel.setSheetState(selectCodeState)
}

// When user saves the edited line with new code
private fun editLineSave(isClosed: Boolean, points: List<LabeledPoint>) {
    val currentState = viewModel.sheetState.value as? MappingSheetState.EditLine
    currentState?.let {
        val lineEntity = LineEntity(
            id = it.lineCode,           // Use new code as ID
            projectId = currentProjectId,
            isClosed = isClosed,
            ...
        )
        
        // Option: Also update point codes if desired
        // points.forEach { point ->
        //     val updatedPoint = point.copy(code = it.lineCode)
        //     viewModel.savePoint(updatedPoint)
        // }
        
        viewModel.saveLine(lineEntity, pointsToSave)
        viewModel.dismissSheet()
    }
}
```

---

## UI Behavior Summary

### Before Update
```
┌─────────────────────────────┐
│ Code:                       │
│ L1                          │  ← Static text, not interactive
└─────────────────────────────┘
```

### After Update
```
┌─────────────────────────────┐
│ Code                    →   │  ← Clickable row with indicator
│ L1                          │
└─────────────────────────────┘
     ↑↑ Tap to select new code
```

When code is changed to "BL":
```
┌─────────────────────────────┐
│ Code                    →   │
│ BL                          │  ← Dynamic, reflects selection
└─────────────────────────────┘
```

---

## State Inheritance

**MappingSheetState.EditLine** already supports this:
```kotlin
data class EditLine(
    val lineCode: String = "",                    // ← Dynamic code
    val onSelectCode: () -> Unit = {},            // ← Callback to open SelectCode
    val onSave: (Boolean, List<LabeledPoint>) -> Unit = { _, _ -> },  // ← Persist
    ...
)
```

**No state structure changes needed** — EditLineSheet and MappingViewModel are ready. Only fragment logic integration needed.

---

## Compilation Status
✅ **EditLineSheet.kt compiles cleanly** with new interactive Code section.

---

## Testing Checklist

- [ ] Open existing line → EditLineSheet shows current code
- [ ] Tap Code row → interactive feedback (ripple/highlight)
- [ ] Tap Code row → SelectCodeSheet opens (when fragment logic wired)
- [ ] Select new code from library → EditLineSheet shows new code
- [ ] Toggle Closed Line + change code + Save → Database updated with both
- [ ] Verify points update labels if code propagation is implemented
- [ ] Back press → dismisses to previous sheet correctly
