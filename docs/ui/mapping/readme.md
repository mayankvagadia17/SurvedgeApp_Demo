# Mapping Section

[Index](../../index.md) > [UI](../readme.md) > Mapping

The mapping section is the core of the Survedge application, providing a real-time geographic interface for surveying tasks. It integrates GPS location tracking with an interactive map to allow users to collect points, create lines, and perform stakeout operations.

## Purpose
To provide a spatial environment where users can visualize existing survey data and record new data (points and lines) using the device's location and orientation sensors.

## Architecture
The mapping section follows a Fragment-ViewModel-Helper pattern to manage its complexity:
- **MappingFragment**: Coordinates UI events, lifecycle, and map initialization.
- **MappingViewModel**: Manages persistent state, database interactions, and reactive data flows.
- **MappingFragmentLogic**: Contains the business logic for map interactions and tool flows.
- **MappingFragmentHelper**: Handles UI-specific setup and view animations.

## User Flow
1. **Setup**: Upon entering, the map centers on the user's GPS location. Existing data for the active project is loaded from the database.
2. **Data Collection**:
   - **Point Collection**: Users record the current GPS position with metadata (Code, ID).
   - **Line Collection**: Users can start a line, adding points sequentially to form a path or closed shape.
3. **Data Interaction**: Tapping on map objects (markers or polylines) opens detail views for inspection, editing, or deletion.
4. **Stakeout**: A specialized workflow where the user selects a target (point or line) and the application provides distance and bearing guidance to reach that target.

## Subsections
| Subsection | Description |
| :--- | :--- |
| [Fragment](./fragment/readme.md) | Detailed documentation on the three-file split (`Fragment`, `Helper`, `Logic`). |
| [ViewModel](./viewmodel/readme.md) | State management and database operations for mapping. |
| [Overlay](./overlay/readme.md) | Custom OSMDroid drawing layers for markers, polylines, and UI elements. |
| [Adapter](./adapter/readme.md) | List adapters for codes, point editing, and object lists. |
| [MapLibre Support](./maplibre/readme.md) | Helpers for creating and managing map markers and polylines. |
| [Mapper](./mapper/readme.md) | Logic for transforming database entities to map coordinates. |
| [Drawable](./drawable/readme.md) | Custom rendering for location pins and markers. |

## Key Source Files
- `MappingFragment.kt`
- `MappingViewModel.kt`
- `MappingFragmentLogic.kt`
- `MappingFragmentHelper.kt`
