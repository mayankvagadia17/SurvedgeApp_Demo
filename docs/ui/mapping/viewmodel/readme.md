# Mapping ViewModel

[index](../../../index.md) > [ui](../../readme.md) > [mapping](../readme.md) > **viewmodel**

The `MappingViewModel` serves as the central state hub for the mapping screen. It manages the active project context, coordinates data loading from the Room database, and handles all point/line persistence operations.

## Responsibility
- **State Selection**: Manages which project is currently being viewed/edited.
- **Data Streaming**: Exposes reactive `StateFlow` streams for points and lines belonging to the active project.
- **Ordered Data**: Ensures points within a line are presented in their correct sequence (by `orderIndex`).
- **Data Lifecycle**: Handles the creation, update, and deletion of geospatial data (Points and Lines).

## State Properties

| Property | Type | Description |
| :--- | :--- | :--- |
| `currentProjectId` | `StateFlow<Long?>` | The primary key of the active project. Defaults to ID `1` if none is explicitly set. |
| `currentProject` | `StateFlow<ProjectEntity?>` | The full metadata for the currently active project. |
| `currentPoints` | `StateFlow<List<PointEntity>>` | All points associated with the current project ID. Updates automatically when the DB changes. |
| `currentLines` | `StateFlow<List<LineWithPoints>>` | All lines (with their nested points) for the current project, re-ordered by `orderIndex`. |

## Key Operations

### Project Context
- **`setProjectId(id: Long)`**: Switches the active project ID, triggering a reload of all associated points and lines.
- **`init { ... }`**: Automatically ensures a "Default Project" exists in the database on first run and sets it as active.

### Point Management
- **`savePoint(point: PointEntity)`**: Performs an upsert (Insert or Update if code exists) for a single point.
- **`deletePoint(point: PointEntity)`**: Deletes the specified point entity from the database.
- **`deletePointById(pointId: String)`**: Searches for and deletes a point by its code within the current project context.

### Line Management
- **`saveLine(line: LineEntity, points: List<PointEntity>)`**: A complex operation that saves line metadata, ensures all nested points are upserted, and rebuilds the `LinePointCrossRef` table to maintain strict point order.
- **`saveLineMetadata(line: LineEntity)`**: Updates only the line entity itself (e.g., changing a line name/code) without altering its constituent points.
- **`deleteLine(line: LineEntity)`**: Deletes a line and its associations from the database.

### Internal Logic
- **`updateProjectTimestamp()`**: Automatically updates the `lastModified` field of the parent project whenever any of its points or lines are modified.

## Implementation Notes
- **Reactive Streams**: Uses `flatMapLatest` to ensure that when the `currentProjectId` changes, all data streams (points/lines) switch focus to the new project immediately.
- **Point Ordering**: Room relations do not inherently guarantee order. This ViewModel manually re-orders points for each line based on the `orderIndex` found in the cross-reference table to ensure survey paths are rendered correctly.
