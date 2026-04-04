# Mapping Helpers (OSMdroid)

[Index](../../index.md) > [Mapping](../readme.md) > Helpers

This directory contains utility classes for interacting with the OSMdroid MapView, specifically for creating and managing markers and polylines.

## Files
- [OsmdroidMarkerHelper.kt](../../../app/src/main/java/com/nexova/survedge/ui/mapping/maplibre/OsmdroidMarkerHelper.kt)
- [OsmdroidPolylineHelper.kt](../../../app/src/main/java/com/nexova/survedge/ui/mapping/maplibre/OsmdroidPolylineHelper.kt)

## OsmdroidMarkerHelper
A singleton object providing standard methods to add and remove markers.

| Method | Role | Key Behaviors |
| :--- | :--- | :--- |
| `createMarker` | Adds a bitmap-based marker to the map | Disables info windows and dragging; sets anchor to center by default. |
| `removeMarker` | Removes a marker from the map | Calls `invalidate()` to refresh the view immediately. |

## OsmdroidPolylineHelper
A singleton object that handles the creation of both solid and dashed lines.

| Method | Role | Key Behaviors |
| :--- | :--- | :--- |
| `createPolyline` | Adds a line overlay to the map | Handles `closed` loops; supports `dashed` mode via `DashedPolylineOverlay`. |
| `removePolyline` | Removes a line overlay | Compatible with both standard `Polyline` and `DashedPolylineOverlay`. |

### Z-Ordering Logic
The helper carefully manages Z-order by inserting polylines *before* any existing markers in the overlay list. This ensures that lines always render **below** points/markers for better visibility.
