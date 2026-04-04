# Room Entities

**Breadcrumb:** [docs/](../../) > [data/](../) > [db/](../) > entities

This section documents all 5 Room entity classes and the junction table that connects lines to points.

---

## Entity Hierarchy

The application stores geolocation survey data in a three-level hierarchy:

```
Project
  └── Line (multiple per project)
        └── Point (multiple per line, via junction table)
```

---

## ProjectEntity

**File:** `app/src/main/java/com/nexova/survedge/data/db/entity/ProjectEntity.kt`  
**Table:** `projects`

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| `id` | Long | No | Primary key, auto-generated |
| `name` | String | No | Project name |
| `description` | String | Yes | Optional project description |
| `createdDate` | Long | No | Timestamp when project was created |
| `lastModified` | Long | No | Timestamp of last modification |
| `crs` | String | Yes | Coordinate Reference System (default: "WGS84") |
| `operatorName` | String | Yes | Name of the surveyor/operator |
| `clientName` | String | Yes | Name of the client |
| `verticalDatum` | String | Yes | Vertical datum spec (default: "Ellipsoidal height") |
| `distanceUnit` | String | Yes | Unit for measurements (default: "Meters") |

---

## LineEntity

**File:** `app/src/main/java/com/nexova/survedge/data/db/entity/LineEntity.kt`  
**Table:** `lines`

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| `pk` | Long | No | Primary key, auto-generated |
| `id` | String | No | User-facing line ID (e.g., "L1") |
| `projectId` | Long | No | Foreign key to ProjectEntity.id; cascade on delete |
| `code` | String | No | Feature code (e.g., "ROAD") |
| `name` | String | Yes | Optional line name |
| `description` | String | Yes | Optional line description |
| `length` | Double | Yes | Computed/stored line length (default: 0.0) |
| `isClosed` | Boolean | No | Whether line is closed (default: false) |
| `createdDate` | Long | No | Timestamp when line was created |

**Indices:** `(projectId)`, `(projectId, id)`

---

## PointEntity

**File:** `app/src/main/java/com/nexova/survedge/data/db/entity/PointEntity.kt`  
**Table:** `points`

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| `pk` | Long | No | Primary key, auto-generated |
| `id` | String | No | User-facing point ID (e.g., "P1") |
| `projectId` | Long | No | Foreign key to ProjectEntity.id; cascade on delete |
| `code` | String | No | Feature code |
| `description` | String | Yes | Optional point description |
| **Coordinates (Geographic)** | | | |
| `latitude` | Double | No | WGS84 latitude |
| `longitude` | Double | No | WGS84 longitude |
| `elevation` | Double | No | Elevation value |
| `ellipsoidalHeight` | Double | Yes | Ellipsoidal height |
| **Coordinates (Grid)** | | | |
| `easting` | Double | Yes | UTM or grid easting |
| `northing` | Double | Yes | UTM or grid northing |
| `zone` | String | Yes | Grid zone identifier |
| **GNSS Metadata** | | | |
| `hRMS` | Double | Yes | Horizontal RMS value |
| `vRMS` | Double | Yes | Vertical RMS value |
| `pdop` | Double | Yes | Positional Dilution of Precision |
| `gdop` | Double | Yes | Geometric Dilution of Precision |
| `satellitesCount` | Int | Yes | Total satellite count used |
| `specificSatellites` | String | Yes | Per-constellation satellite counts (JSON or delimited) |
| `solutionStatus` | String | Yes | Solution status (e.g., "FIXED", "FLOAT", "SINGLE") |
| `correctionType` | String | Yes | Type of correction applied |
| **Antenna Info** | | | |
| `antennaHeight` | Double | Yes | Height of antenna above ground |
| `antennaHeightUnits` | String | Yes | Units for antenna height (default: "m") |
| **Base Station Info** | | | |
| `baseEasting` | Double | Yes | Base station easting coordinate |
| `baseNorthing` | Double | Yes | Base station northing coordinate |
| `baseElevation` | Double | Yes | Base station elevation |
| `baseLatitude` | Double | Yes | Base station latitude |
| `baseLongitude` | Double | Yes | Base station longitude |
| `baseEllipsoidalHeight` | Double | Yes | Base station ellipsoidal height |
| `baselineLength` | Double | Yes | Distance from base to rover |
| `mountPoint` | String | Yes | Base station mount point identifier |
| **Time** | | | |
| `ts` | String | No | ISO 8601 timestamp |
| `averagingStart` | String | Yes | Start time of averaging window |
| `averagingEnd` | String | Yes | End time of averaging window |
| `samples` | Int | Yes | Number of samples in averaging |
| **Device Info** | | | |
| `deviceType` | String | Yes | Type of GNSS receiver |
| `deviceSerialNumber` | String | Yes | Serial number of device |

**Indices:** `(projectId)`, `(code)`, `(projectId, id)`

---

## LinePointCrossRef

**File:** `app/src/main/java/com/nexova/survedge/data/db/entity/LinePointCrossRef.kt`  
**Table:** `line_points` (junction table)

| Field | Type | Nullable | Notes |
|-------|------|----------|-------|
| `linePk` | Long | No | FK to LineEntity.pk; cascade on delete; part of composite primary key |
| `pointPk` | Long | No | FK to PointEntity.pk; cascade on delete; part of composite primary key |
| `orderIndex` | Int | No | Sequence order of point within line; part of composite primary key |

**Primary Key:** `(linePk, pointPk, orderIndex)` — composite, uniquely identifies a point's position in a line

**Indices:** `(linePk)`, `(pointPk)`

---

## LineWithPoints

**File:** `app/src/main/java/com/nexova/survedge/data/db/entity/LineWithPoints.kt`  
**Type:** Data class (not a Room entity table)

This is a **relation class** used by Room to load a line together with all its points in a single query:

```kotlin
data class LineWithPoints(
    @Embedded val line: LineEntity,
    @Relation(
        parentColumn = "pk",       // line.pk
        entityColumn = "pk",       // point.pk
        associateBy = Junction(
            value = LinePointCrossRef::class,
            parentColumn = "linePk",
            entityColumn = "pointPk"
        )
    )
    val points: List<PointEntity>
)
```

**Usage:** Returned by DAO queries to fetch a complete line with all its ordered points in a single result object.

---

## Relationship Diagram

```
┌─────────────────┐
│  ProjectEntity  │
│  (projects)     │
│  ┌─────────┐    │
│  │ id (PK) │    │
│  └─────────┘    │
└────────┬────────┘
         │ 1:N (projectId FK)
         │
    ┌────┴────┐
    │          │
┌───▼──────────────┐         ┌──────────────────┐
│  LineEntity      │ 1:N ────┤ LinePointCrossRef│
│  (lines)         │ (M:N)   │ (line_points)    │
│  ┌──────────┐    │         │  ┌─────────────┐ │
│  │ pk (PK)  │    │         │  │ linePk (FK) │ │
│  │ id       │    │         │  │ pointPk (FK)│ │
│  │ code     │    │         │  │ orderIndex  │ │
│  └──────────┘    │         │  └─────────────┘ │
└──────────────────┘         └────────┬─────────┘
                                      │
                             ┌────────▼────────┐
                             │  PointEntity    │
                             │  (points)       │
                             │  ┌──────────┐   │
                             │  │ pk (PK)  │   │
                             │  │ id       │   │
                             │  │ code     │   │
                             │  │ lat/lng  │   │
                             │  └──────────┘   │
                             └─────────────────┘
```
