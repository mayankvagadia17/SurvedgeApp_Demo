# Data Access Objects (DAOs)

**Breadcrumb:** [Home](../../../index.md) > [Data](../../readme.md) > [Database](../readme.md) > DAOs

## Overview

Three DAO interfaces manage all database queries and mutations for the three entity types: Projects, Lines, and Points. Each DAO follows Room's suspend/Flow patterns for coroutine-safe access.

---

## ProjectDao

**File:** `app/src/main/java/com/nexova/survedge/data/db/dao/ProjectDao.kt`

**Purpose:** Manages Project entity CRUD operations.

| Method | Parameters | Return Type | Notes |
|--------|-----------|------------|-------|
| `getAllProjects()` | none | `Flow<List<ProjectEntity>>` | Emits all projects ordered by most recently modified first |
| `getProjectById()` | `projectId: Long` | `suspend → ProjectEntity?` | Returns single project or null |
| `insertProject()` | `project: ProjectEntity` | `suspend → Long` | Inserts/replaces; returns primary key |
| `updateProject()` | `project: ProjectEntity` | `suspend → Unit` | Updates existing project |
| `deleteProject()` | `project: ProjectEntity` | `suspend → Unit` | Deletes single project |

---

## LineDao

**File:** `app/src/main/java/com/nexova/survedge/data/db/dao/LineDao.kt`

**Purpose:** Manages Line entity and Line-Point cross-reference operations.

| Method | Parameters | Return Type | Notes |
|--------|-----------|------------|-------|
| `insertLine()` | `line: LineEntity` | `suspend → Long` | Inserts/replaces line; returns primary key |
| `insertLinePointCrossRef()` | `crossRef: LinePointCrossRef` | `suspend → Unit` | Inserts single line-point association |
| `insertLinePointCrossRefs()` | `crossRefs: List<LinePointCrossRef>` | `suspend → Unit` | Inserts multiple line-point associations |
| `getLineByCode()` | `projectId: Long, code: String` | `suspend → LineEntity?` | Fetches line by project and code identifier; returns null if not found |
| `getLinePointCrossRefsByLinePk()` | `linePk: Long` | `suspend → List<LinePointCrossRef>` | Returns all point cross-refs for a line, ordered by index |
| `getLinesByProject()` | `projectId: Long` | `Flow<List<LineEntity>>` | Emits all lines for a project as they change |
| `getLinesWithPoints()` | `projectId: Long` | `Flow<List<LineWithPoints>>` | Emits all lines with their associated points, ordered by project; transactional query |
| `clearLinePoints()` | `linePk: Long` | `suspend → Unit` | Deletes all point associations for a line |
| `updateLineWithPoints()` | `line: LineEntity, crossRefs: List<LinePointCrossRef>` | `suspend → Unit` | Transactional: inserts line, clears old point associations, inserts new cross-refs |
| `deleteLine()` | `line: LineEntity` | `suspend → Unit` | Deletes single line |

---

## PointDao

**File:** `app/src/main/java/com/nexova/survedge/data/db/dao/PointDao.kt`

**Purpose:** Manages Point entity CRUD operations.

| Method | Parameters | Return Type | Notes |
|--------|-----------|------------|-------|
| `getPointsByProject()` | `projectId: Long` | `Flow<List<PointEntity>>` | Emits all points for a project, ordered by timestamp ascending |
| `getPointById()` | `pointId: String` | `suspend → PointEntity?` | Fetches point by string identifier; returns null if not found |
| `getPointByPk()` | `pk: Long` | `suspend → PointEntity?` | Fetches point by primary key; returns null if not found |
| `getPointsByPks()` | `pks: List<Long>` | `suspend → List<PointEntity>` | Fetches multiple points by primary keys |
| `getPointByCode()` | `projectId: Long, id: String` | `suspend → PointEntity?` | Fetches point by project and code identifier; returns null if not found |
| `insertPoint()` | `point: PointEntity` | `suspend → Long` | Inserts/replaces point; returns primary key |
| `insertPoints()` | `points: List<PointEntity>` | `suspend → Unit` | Inserts/replaces multiple points |
| `updatePoint()` | `point: PointEntity` | `suspend → Unit` | Updates existing point |
| `deletePoint()` | `point: PointEntity` | `suspend → Unit` | Deletes single point |
| `deletePointsByProject()` | `projectId: Long` | `suspend → Unit` | Deletes all points for a project |

---

## Query Patterns

- **Suspend functions** (`suspend → T`) block until complete; safe to call from coroutines
- **Flow** emits updated data whenever the underlying table changes; suitable for reactive UI binding
- **OnConflictStrategy.REPLACE** on inserts allows upsert behavior (insert or replace if PK already exists)
- **Transactional methods** (marked `@Transaction`) ensure all operations succeed or all fail, preventing partial state
