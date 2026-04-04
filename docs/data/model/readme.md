[← Data Layer](../../readme.md)

# Import Models

**Source:** [ImportModels.kt](../../../../../app/src/main/java/com/nexova/survedge/data/model/ImportModels.kt)

Data classes that define the structure of imported data. These models map to external data sources (JSON/CSV) and are used to parse and validate incoming data before storing in the database.

## Data Classes

### PointImportRoot

Wrapper class for the root structure of imported point data.

| Field | Type | Nullable | Purpose |
|-------|------|----------|---------|
| `points` | `List<PointImportItem>` | No | Collection of individual point records |

### PointImportItem

Represents a single point record from an import source.

| Field | Type | Nullable | Purpose |
|-------|------|----------|---------|
| `id` | `String` | No | Unique identifier for the point |
| `codeId` | `String?` | Yes | Associated code identifier (if any) |
| `coords` | `List<Double>` | No | Coordinate values (typically [latitude, longitude]) |
| `elevation` | `Double?` | Yes | Elevation value (if available) |
| `ts` | `String` | No | Timestamp as string |

## Usage

These data classes are used as intermediary models when importing point data from external sources. The parsed data from `PointImportRoot` is then mapped to entity objects (`PointEntity`, `ProjectEntity`, etc.) for storage in the Room database.
