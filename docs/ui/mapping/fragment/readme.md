# Mapping Fragment

[Index](../../../index.md) > [UI](../../../ui/readme.md) > [Mapping](../readme.md) > Fragment

## Overview

The mapping screen is the most complex component in the Survedge App. To maintain readability and manage a logic set that spans thousands of lines, the implementation is split into three primary classes: `MappingFragment`, `MappingFragmentLogic`, and `MappingFragmentHelper`.

## Responsibility Split

| File | Primary Role | Key Responsibilities |
| :--- | :--- | :--- |
| **`MappingFragment.kt`** | **Lifecycle & State Container** | Owns the primary state (member variables), manages Android lifecycle events (`onCreateView`, `onPause`, `onDestroyView`), and initializes the `Logic` and `Helper` instances. |
| **`MappingFragmentLogic.kt`** | **Controller & Business Logic** | Orchestrates the custom "Sheet Navigation" system, handles database-to-UI synchronization, manages point/line code sequences, and processes edge-to-edge layout constraints. |
| **`MappingFragmentHelper.kt`** | **UI & Map Specialized Utilities** | Focuses on low-level Osmdroid `MapView` configuration, specialized "Stakeout" UI card updates, and transitions for the precision "Bullseye" view. |

## Key Classes and Methods

### `MappingFragment`
The entry-point class that delegates actual operations to its logic and helper components while maintaining the core reference to the `MappingViewModel`.

*   `initializeMap()`: Delegates to `helper.initializeMap()` to set up the Osmdroid view.
*   `onViewCreated()`: Sets up the back-pressed dispatcher and initiates database observation via the ViewModel.
*   `Member Variables`: Stores all live state, including `collectedLabeledPoints`, `stakeoutSession`, and active overlay references.

### `MappingFragmentLogic`
The "brain" of the UI, containing the bulk of the interactive behavior. It operates directly on the fragment's internal fields to update the shared state.

*   `showSheet(type: SheetType, ...)`: A centralized system for managing the various bottom sheets (Collect Point, Edit Line, Object List) without using standard `BottomSheetDialogFragment`.
*   `updatePointsFromDatabase(points: List<LabeledPoint>)`: Handles high-performance marker updates and prefix/sequence numbering when data syncs.
*   `reconstructPolylines()`: Re-renders all line overlays to ensure "closed" status and point order are visually accurate.
*   `animateSheetTransition(...)`: Manages smooth custom animations (Slide Up, Slide Down, Slide Left/Right) for the sheet system.

### `MappingFragmentHelper`
A utility class specialized in configuring the map environment and handling formatting for complex UI components like Stakeout and Bullseye.

*   `initializeMap()`: Configures `TileSourceFactory`, multi-touch controls, and zoom limits for the `MapView`.
*   `setupStakeoutUI()`: Initializes the complex horizontal scroll cards used for stakeout navigation.
*   `showBullseyeView()`: Transitions the UI into "Precision Mode," locking the map orientation to North and hiding all markers except the immediate target.
*   `updateStakeoutBottomSheet(m: StakeoutMeasurement)`: Formats real-time metrics (North/East offsets, Bearing, Cut/Fill) for display.

## Interaction Flow

1.  **State Change**: The `MappingViewModel` emits new data from the Room database.
2.  **Observation**: `MappingFragment` receives the update and calls `logic.updatePointsFromDatabase()`.
3.  **Processing**: `MappingFragmentLogic` updates the shared point list and calculates any changes to prefixes or sequence counters.
4.  **UI Refresh**: `Logic` (for data-driven overlays) or `Helper` (for map-driven state) updates the map and invalidates the view to trigger a redraw.

## Bottom Sheet Navigation & Layering

The mapping section uses a custom "Sheet Navigation" system to manage multiple overlays (bottom sheets) with proper z-order and animation sequencing. The bottom navigation tabs remain **always visible** — bottom sheets smoothly slide up and overlap the navigation bar, creating a modern layered UI.

### Sheet Opening Behavior

When opening a bottom sheet:

1. **Menu Hiding**: The `hideMenu {}` callback hides any open context menus.
2. **Sheet Display**: The `showSheet(SheetType.XXX) {}` callback displays the new bottom sheet immediately (no nav hiding needed).
3. **Elevation Control**: All sheets have elevation higher than the bottom navigation view (16dp), ensuring they render on top:
   - Standard sheets: 24dp elevation
   - Full-screen sheets (Object List, Select Code): 32dp elevation
   - High-priority sheets (Select Code): 48dp elevation

### Sheet Visibility & Overlapping

- **Always-Visible Navigation**: The bottom navigation bar is never hidden during sheet operations. Sheets seamlessly overlap it while the navigation items remain functional.
- **Elevation Hierarchy**: `MappingFragmentLogic` sets sheet elevation dynamically (e.g., `sheetBinding.root.elevation = 24f * density`), and the layout XML defines baseline elevation values (20dp). This ensures proper z-order stacking.
- **Object List Sheet**: The `showObjectListBottomSheet()` function supports an optional `showTitle` parameter (default `true`) to hide the title when displayed alongside other sheets, reducing visual clutter.
- **State Restoration**: After closing sheets, `restoreStateAfterClosingInfoSheet()` re-enables map touch and restores UI element positions (e.g., collect button above nav bar). Navigation visibility is unchanged since the nav is always visible.
- **Line Segment Sheet Special Case**: The `bottomSheetLineSegment` sheet has `bottomMargin = bottomNavOffset`, causing it to float above the navigation bar rather than overlap it. This is intentional for this small info sheet.
