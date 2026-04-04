# Stakeout Module

The Stakeout module provides the logic and data structures for guiding a user to a precise geographic location. It handles distance calculations, bearing alignment, and precision thresholds for the "Bullseye" guidance mode.

## Core Models

### [StakeoutPoint.kt](file:///Users/amanvagadiya/Documents/GitHub/Android/SurvedgeApp_Demo/app/src/main/java/com/nexova/survedge/ui/stakeout/model/StakeoutPoint.kt)
Represents a single target destination.
| Field | Type | Description |
| :--- | :--- | :--- |
| `id` | String | Unique identifier for the point. |
| `name` | String | User-visible name (e.g., "P-101"). |
| `latitude`/`longitude` | Double | Target WGS84 coordinates. |
| `elevation` | Double | Target elevation (Z-axis). |
| `isLine` | Boolean | Flag indicating if this point is part of a line stakeout sequence. |
| `order` | Int | Sequence order for multi-point stakeout. |

### [StakeoutSession.kt](file:///Users/amanvagadiya/Documents/GitHub/Android/SurvedgeApp_Demo/app/src/main/java/com/nexova/survedge/ui/stakeout/model/StakeoutSession.kt)
Manages the state of an active stakeout operation.
- **`targetPoints`**: List of points to be staked.
- **`currentIndex`**: Tracks which point in the list is currently being guided.
- **`poleHeight`**: User-defined antenna height (default 1.80m).
- **`toleranceThreshold`**: The distance at which a point is considered "staked" (default 0.05m).
- **`autoFollowEnabled`**: Whether to automatically advance to the next point upon reaching tolerance.

### [StakeoutMeasurement.kt](file:///Users/amanvagadiya/Documents/GitHub/Android/SurvedgeApp_Demo/app/src/main/java/com/nexova/survedge/ui/stakeout/model/StakeoutMeasurement.kt)
The output of the guidance engine, ideally calculated on every position update. 
| Field | Type | Description |
| :--- | :--- | :--- |
| `horizontalDistance` | Double | Direct horizontal distance to the target in meters. |
| `verticalDistance` | Double | Elevation difference (Target - (User + Pole)). |
| `northOffset` | Double | Displacement required along the North/South axis. |
| `eastOffset` | Double | Displacement required along the East/West axis. |
| `bearing` | Double | The required compass heading (0-360°) to reach the target. |
| `inTolerance` | Boolean | True if horizontal distance is $\le$ session tolerance. |

## Utilities & Constants

### [CoordinateUtils.kt](file:///Users/amanvagadiya/Documents/GitHub/Android/SurvedgeApp_Demo/app/src/main/java/com/nexova/survedge/ui/stakeout/util/CoordinateUtils.kt)
The geometric engine for the module:
- **`calculateDistance`**: Uses the Haversine formula to find distance over the Earth's surface ($R=6371km$).
- **`calculateBearing`**: Computes the initial bearing from current position to target.
- **`calculateOffsets`**: Determines North/East deltas in meters.
- **`calculateVerticalDistance`**: Adjusts for pole height to find the ground-to-target vertical gap.

### [StakeoutConstants.kt](file:///Users/amanvagadiya/Documents/GitHub/Android/SurvedgeApp_Demo/app/src/main/java/com/nexova/survedge/ui/stakeout/util/StakeoutConstants.kt)
- **`BULLSEYE_TRANSITION_DISTANCE`**: 0.5m. Trigger for precision UI mode.
- **`BULLSEYE_EXIT_DISTANCE`**: 0.6m. Threshold to return to map view.
- **`DEFAULT_TOLERANCE`**: 0.05m (5cm).
- **`DEFAULT_POLE_HEIGHT`**: 1.80m.
- **`BULLSEYE_RING_COUNT`**: 4. Visual rings in the precision UI.
