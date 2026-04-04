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

## Bottom Sheet Navigation & Sequencing

The mapping section uses a custom "Sheet Navigation" system to manage multiple overlays (bottom sheets) with proper lifecycle handling. When opening a bottom sheet:

1. **Menu Hiding**: The `hideMenu {}` callback hides any open context menus.
2. **Navigation Hiding**: The `hideBottomNavigation {}` callback slides the bottom navigation bar out of view **first**, waiting for the animation to complete.
3. **Sheet Display**: Only after the navigation is fully hidden does the `showSheet(SheetType.XXX) {}` callback display the new bottom sheet.

This sequential ordering (hideMenu → hideBottomNavigation → showSheet) prevents visual overlap between the bottom sheet and the navigation tab bar. All six primary bottom sheet functions (`showNewLineBottomSheet`, `showSelectPointBottomSheet`, `showLineSegmentDetailsBottomSheet`, `showObjectListBottomSheet`, `showCollectPointBottomSheet`, `showEditLineBottomSheet`) follow this pattern.
