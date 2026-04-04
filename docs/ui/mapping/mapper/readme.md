# Point Mappers

[Index](../../index.md) > [Mapping](../readme.md) > Mappers

This directory contains data transformation logic between the Database layer and the UI Overlay layer.

## Files
- [PointMappers.kt](../../../app/src/main/java/com/nexova/survedge/ui/mapping/mapper/PointMappers.kt)

## Overview
The mappers provide extension functions to bridge `PointEntity` (used for Room persistence) and `LabeledPoint` (used by custom map overlays).

| Function | From | To | Purpose |
| :--- | :--- | :--- | :--- |
| `toLabeledPoint` | `PointEntity` | `LabeledPoint` | Prepares database records for map rendering. |
| `toPointEntity` | `LabeledPoint` | `PointEntity` | Converts UI-created points back to database format for saving. |

### Field Mapping
- **Coordinates:** Maps `latitude`/`longitude` to a `listOf(lon, lat)` (X/Y) coordinate list.
- **Identity:** Preserves `id` and `code` (mapping it to `codeId`).
- **Metadata:** Preserves `elevation` and `ts` (timestamp).
