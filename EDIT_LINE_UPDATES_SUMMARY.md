# EditLineSheet Updates Summary

## Changes Made

### File: `EditLineSheet.kt`

#### 1. Added Import
```kotlin
import androidx.compose.material.icons.automirrored.filled.ArrowForward
```

#### 2. Code Display → Interactive Row

**Before:**
```kotlin
Text("Code: L1", style = MaterialTheme.typography.bodyMedium)
```

**After:**
```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { state.onSelectCode() }
        .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Column(modifier = Modifier.weight(1f)) {
        Text("Code", style = MaterialTheme.typography.labelMedium)
        Text(
            state.lineCode.ifEmpty { "Select Code" },
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Icon(
        Icons.AutoMirrored.Filled.ArrowForward,
        contentDescription = "Select Code",
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
```

### What This Achieves

✅ **Interactive Code Display**
- Code section is now fully clickable (entire row is a tap target)
- User can tap to select a different code

✅ **Dynamic Code Binding**
- Uses `state.lineCode` instead of hardcoded "L1"
- When line code changes, UI updates automatically
- Shows "Select Code" if no code is assigned

✅ **Visual Affordance**
- Arrow icon indicates the element is interactive
- Proper Material3 styling with label/value layout
- Matches design language of other interactive sheet elements

✅ **Callback Trigger**
- Tapping invokes `state.onSelectCode()` callback
- Fragment can handle by opening SelectCodeSheet
- Callback already exists in MappingSheetState.EditLine

---

## State Management (No Changes Needed)

MappingSheetState.EditLine already supports this:

```kotlin
data class EditLine(
    val lineCode: String = "",                // ← Dynamic display
    val isClosedLine: Boolean = false,
    val points: List<LabeledPoint> = emptyList(),
    val onSave: (Boolean, List<LabeledPoint>) -> Unit = { _, _ -> },
    val onSelectCode: () -> Unit = {},        // ← Already here
    val onAddPoint: () -> Unit = {},
    val onClose: () -> Unit = {}
)
```

**Ready to use** — no modifications needed.

---

## How It Works

```
User taps Code row in EditLineSheet
         ↓
onClick = { state.onSelectCode() }
         ↓
Fragment logic receives callback
         ↓
Fragment decides to show SelectCodeSheet (implementation detail)
         ↓
User selects new code (e.g., "BL")
         ↓
Fragment updates EditLineSheet state:
   lineCode = "BL"  (was "L1")
         ↓
EditLineSheet recomposes
         ↓
Code display shows "BL" instead of "L1"
         ↓
User saves line with new code
         ↓
viewModel.saveLine() persists to database
```

---

## Compilation Status

✅ **BUILD SUCCESSFUL**
- No errors
- No warnings (fixed deprecation: ArrowForward → AutoMirrored.ArrowForward)
- Ready for testing

---

## Fragment Integration Required

The following still needs to be implemented in fragment/logic:

### 1. Handle onSelectCode Callback
```kotlin
// In MappingFragmentLogic or MappingFragment
private fun handleEditLineSelectCode(currentEditLineState: MappingSheetState.EditLine) {
    // Open SelectCodeSheet
    val selectCodeState = MappingSheetState.SelectCode(
        onCodeSelect = { codeItem ->
            // User selected a code
            handleCodeSelected(codeItem, currentEditLineState)
        }
    )
    viewModel.setSheetState(selectCodeState)
}

private fun handleCodeSelected(codeItem: CodeItem, editLineState: MappingSheetState.EditLine) {
    // Update EditLineSheet with new code
    val updatedEditLineState = editLineState.copy(
        lineCode = codeItem.abbreviation  // "BL", "PV", etc.
    )
    viewModel.setSheetState(updatedEditLineState)
}
```

### 2. Update onSave to Use Dynamic Code
```kotlin
// When EditLineSheet.onSave is called with new code
val lineEntity = LineEntity(
    id = state.lineCode,  // Use current code value
    isClosed = isClosedLine,
    ...
)
viewModel.saveLine(lineEntity, points)
```

### 3. (Optional) Propagate Code to Points
```kotlin
// In saveLine callback, optionally update point codes too
points.forEach { point ->
    val updatedPoint = point.copy(code = lineEntity.id)
    viewModel.savePoint(updatedPoint)
}
```

---

## Key Points

| Aspect | Status | Notes |
|--------|--------|-------|
| **Code display interactive** | ✅ Done | Row is clickable, triggers callback |
| **Dynamic lineCode** | ✅ Done | Uses `state.lineCode`, not hardcoded |
| **Arrow indicator** | ✅ Done | Shows element is interactive |
| **Callback exists** | ✅ Done | `onSelectCode()` ready in state |
| **Sheet stacking** | ⚠️ Pending | Fragment logic needed to show SelectCodeSheet |
| **Code persistence** | ✅ Done | ViewModel.saveLine() handles it |
| **Point code update** | ⚠️ Optional | Can be added if desired |

---

## Testing Instructions

1. **Build & Run**
   ```bash
   ./gradlew assembleDebug
   # Install on emulator/device
   ```

2. **Open existing line for editing**
   - Navigate to mapping screen
   - Tap on a line to open LineSegmentSheet
   - Tap "Edit Line" button

3. **Verify Code Section**
   - Code section displays current line code
   - Code row is highlighted/ripple when tapped
   - Arrow icon is visible on the right

4. **Tap Code Row**
   - Should trigger `onSelectCode()` callback
   - Once fragment logic is wired, SelectCodeSheet should appear

5. **Select New Code (Once Wired)**
   - SelectCodeSheet should display code options
   - Selecting a code should update EditLineSheet display

6. **Save Changes**
   - Save button saves line with new code to database
   - Verify in database or map overlay

---

## Files Modified

- `EditLineSheet.kt` — Added interactive Code row with ArrowForward icon

## Files Unchanged

- `MappingSheetState.kt` — Already supports the flow
- `MappingViewModel.kt` — Already saves codes correctly
- `SelectCodeSheet.kt` — Ready to be called when needed

---

## Next Steps

1. ✅ **EditLineSheet interactive** — Complete
2. ⏳ **Wire fragment logic** — Hook up onSelectCode to show SelectCodeSheet
3. ⏳ **Test on device** — Verify UI and interactions
4. ⏳ **(Optional) Point code propagation** — Update point codes when line code changes

The UI foundation is ready. Fragment integration is next.
