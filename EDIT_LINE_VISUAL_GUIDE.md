# EditLineSheet Visual Guide & Code Structure

## Before & After Comparison

### BEFORE (Static Code Display)
```
┌─────────────────────────────────────┐
│ Edit Line                        [×] │
├─────────────────────────────────────┤
│ Code: L1                            │  ← Static text, not clickable
├─────────────────────────────────────┤
│ ☑ Closed Line                       │
├─────────────────────────────────────┤
│ Points (3)                          │
│ ≡ P1                                │
│   Line1                             │
│ ≡ P2                                │
│   Line1                             │
│ ≡ P3                                │
│   Line1                             │
├─────────────────────────────────────┤
│      Save Line                      │
└─────────────────────────────────────┘
```

### AFTER (Interactive Code Selection)
```
┌─────────────────────────────────────┐
│ Edit Line                        [×] │
├─────────────────────────────────────┤
│ Code                            → │ ← Clickable!
│ L1                                │   Dynamic display
├─────────────────────────────────────┤
│ ☑ Closed Line                       │
├─────────────────────────────────────┤
│ Points (3)                          │
│ ≡ P1                                │
│   Line1                             │
│ ≡ P2                                │
│   Line1                             │
│ ≡ P3                                │
│   Line1                             │
├─────────────────────────────────────┤
│      Save Line                      │
└─────────────────────────────────────┘
```

### User Taps Code Row
```
┌─────────────────────────────────────┐
│ Code                            → │ ← Ripple animation
│ L1                                │   Feedback on tap
└─────────────────────────────────────┘
   ↓ onSelectCode() triggered
   ↓ Fragment opens SelectCodeSheet
```

### After Code Selected
```
┌─────────────────────────────────────┐
│ Code                            → │
│ BL  (updated!)                    │ ← Reflects new selection
├─────────────────────────────────────┤
│ ☑ Closed Line                       │
└─────────────────────────────────────┘
```

---

## Code Structure Breakdown

### Interactive Row (Lines 64-83)

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()           // Full width tappable area
        .clickable {              // Makes entire row clickable
            state.onSelectCode()  // Triggers callback
        }
        .padding(12.dp),          // Comfortable touch target
    verticalAlignment = Alignment.CenterVertically
) {
    Column(modifier = Modifier.weight(1f)) {  // Left side: label & value
        Text(
            "Code",
            style = MaterialTheme.typography.labelMedium  // Small label
        )
        Text(
            state.lineCode.ifEmpty { "Select Code" },     // Dynamic display
            style = MaterialTheme.typography.bodyMedium    // Larger value
        )
    }
    Icon(
        Icons.AutoMirrored.Filled.ArrowForward,           // Right side: indicator
        contentDescription = "Select Code",
        tint = MaterialTheme.colorScheme.onSurfaceVariant // Subtle color
    )
}
```

### Key Properties

| Property | Purpose | Value |
|----------|---------|-------|
| `fillMaxWidth()` | Ensures row spans full sheet width | Modifier |
| `clickable { }` | Makes entire area tappable | Modifier.clickable |
| `onSelectCode()` | Invokes callback on tap | () → Unit |
| `padding(12.dp)` | Touch target minimum size | 12.dp |
| `state.lineCode` | Dynamic code display from state | String |
| `ifEmpty { }` | Fallback when no code selected | "Select Code" |
| `Icons.AutoMirrored.Filled.ArrowForward` | Visual affordance for interaction | ImageVector |

---

## State Flow Integration

### EditLineSheet Composable Parameters
```kotlin
@Composable
fun EditLineSheet(
    state: MappingSheetState.EditLine,  // ← Receives this
    onDismiss: () -> Unit
)
```

### MappingSheetState.EditLine Structure
```kotlin
data class EditLine(
    val lineOverlay: ClickablePolylineOverlay? = null,
    val lineCode: String = "",                    // ← This gets displayed
    val isClosedLine: Boolean = false,
    val points: List<LabeledPoint> = emptyList(),
    val onSave: (Boolean, List<LabeledPoint>) -> Unit = { _, _ -> },
    val onSelectCode: () -> Unit = {},            // ← This gets called on tap
    val onAddPoint: () -> Unit = {},
    val onClose: () -> Unit = {}
)
```

### Data Flow

```
Fragment creates EditLineSheet state:
    ↓
    val editLineState = MappingSheetState.EditLine(
        lineCode = "L1",              // Current code
        onSelectCode = { ... }        // Callback to handle selection
    )
    ↓
viewModel.setSheetState(editLineState)
    ↓
StateFlow emits → MappingSheetsHost renders EditLineSheet
    ↓
EditLineSheet receives state parameter
    ↓
Code row displays: state.lineCode = "L1"
    ↓
User taps Code row
    ↓
Row's clickable modifier triggers: state.onSelectCode()
    ↓
Fragment's callback logic executes
    ↓
(Optional) Show SelectCodeSheet for code selection
    ↓
(Optional) Update state with new code
    ↓
EditLineSheet recomposes
    ↓
Code row displays: state.lineCode = "BL" (new value)
```

---

## Layout Hierarchy

```
EditLineSheet (Composable)
    ↓
ModalBottomSheet
    ↓
Column (main container)
    ↓
    ├─ Row (header)
    │  ├─ Spacer(weight=1f)
    │  ├─ Text("Edit Line")
    │  ├─ Spacer(weight=1f)
    │  └─ IconButton(Close)
    │
    ├─ SheetDivider()
    ├─ Spacer(height=12.dp)
    │
    ├─ Row (CODE SECTION) ← NEW INTERACTIVE ROW
    │  ├─ Column(weight=1f)
    │  │  ├─ Text("Code")
    │  │  └─ Text(state.lineCode)
    │  └─ Icon(ArrowForward)
    │
    ├─ Spacer(height=12.dp)
    │
    ├─ Row (Closed Line Checkbox)
    │  ├─ Text("Closed Line")
    │  ├─ Spacer(weight=1f)
    │  └─ Checkbox
    │
    ├─ Spacer(height=12.dp)
    ├─ Text("Points (N)")
    │
    ├─ LazyColumn
    │  └─ itemsIndexed(points)
    │     └─ PointDragItem(point)
    │
    ├─ Spacer(height=16.dp)
    ├─ Button("Save Line")
    └─ Spacer(height=16.dp)
```

---

## Interaction Flow Diagram

```
User on Map Screen
    ↓
Tap Line on map
    ↓
LineSegmentSheet opens (shows line details)
    ↓
Tap "Edit Line" button
    ↓
EditLineSheet opens
    ┌─────────────────────────────────┐
    │ Edit Line Sheet                 │
    │                                 │
    │ Code            →               │ ← User's focus here
    │ L1                              │
    │                                 │
    │ ☑ Closed Line                   │
    │ Points (3)                      │
    │ [points list]                   │
    │ [Save button]                   │
    └─────────────────────────────────┘
    ↓
User taps Code row
    ↓
onClick = { state.onSelectCode() }
    ↓
Fragment logic triggered:
    editLineOnSelectCode()
    ↓
(Fragment decides next step)
    ├─ Option 1: Show SelectCodeSheet
    │  └─ User selects code
    │     └─ Update lineCode in state
    │        └─ Return to EditLineSheet with new code
    │
    └─ Option 2: Navigate to code picker elsewhere

Later...
    ↓
User sees updated code in EditLineSheet
    ↓
User toggles "Closed Line" checkbox
    ↓
User taps "Save Line"
    ↓
viewModel.saveLine(
    LineEntity(id = state.lineCode, isClosed = ...),
    points
)
    ↓
Database updated
    ↓
Sheet dismisses
    ↓
Map overlay updates with new line code + geometry
```

---

## Material3 Design Alignment

The interactive Code row follows Material3 guidelines:

### Row Structure
- **Label** (small): "Code" (typography.labelMedium)
- **Value** (larger): "L1" or "BL" (typography.bodyMedium)
- **Icon** (trailing): ArrowForward indicator

### Interactive State Feedback
- ✅ Ripple effect on tap (automatic with `.clickable`)
- ✅ Color feedback (icon uses onSurfaceVariant for accessibility)
- ✅ Clear affordance (arrow indicates interaction)

### Spacing & Touch Target
- ✅ 12.dp padding ensures 48dp+ touch target (Material spec minimum)
- ✅ Full-width row maximizes tappable area
- ✅ Proper vertical alignment for content

---

## Code Modifications Summary

| Section | Change | Impact |
|---------|--------|--------|
| Import | Added ArrowForward icon | Visual indicator |
| Row wrapper | Added `.clickable` | Makes area tappable |
| Code display | Changed to `state.lineCode` | Dynamic from state |
| Column layout | Separated label/value | Better visual hierarchy |
| Icon | Added ArrowForward | Shows interactive affordance |

---

## Implementation Checklist

- [x] EditLineSheet.kt updated with interactive Code row
- [x] Uses `state.lineCode` (not hardcoded "L1")
- [x] Shows "Select Code" fallback
- [x] ArrowForward icon added
- [x] Calls `state.onSelectCode()` on tap
- [x] Compiles cleanly
- [ ] Fragment logic wired to handle onSelectCode
- [ ] Test on emulator/device
- [ ] Verify ripple feedback
- [ ] Verify code selection flow (once wired)

---

## Expected Behavior (After Fragment Integration)

```
1. User opens existing line "L1"
   → EditLineSheet shows: Code: L1

2. User taps Code row
   → Ripple feedback, visual confirmation

3. SelectCodeSheet appears (fragment logic)
   → Shows code library: PV, BL, CL, ST, etc.

4. User selects "BL"
   → Callback triggered, state updated

5. EditLineSheet reappears with updated code
   → Code: BL (changed from L1)

6. User saves
   → Database: LineEntity with id="BL"
   → Map overlay: Shows line labeled as "BL"
```

---

## Notes for Implementation

- `state.lineCode` is a String parameter passed to EditLineSheet
- `onSelectCode()` is a callback (lambda) — fragment provides the logic
- The Row is clickable via `.clickable` modifier (standard Compose approach)
- No custom click listeners needed
- Works seamlessly with ModalBottomSheet dismiss logic
- Recomposes automatically when state updates

**Status: ✅ Ready for use.** UI is complete. Awaiting fragment integration to wire the callback.
