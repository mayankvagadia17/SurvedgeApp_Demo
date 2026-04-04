[docs](../readme.md) / data

# data

Room database layer for Survedge. Manages all persistent storage for projects, survey points, and lines.

## Source file

`app/src/main/java/com/nexova/survedge/data/db/AppDatabase.kt`

## Database

| Property | Value |
|---|---|
| Class | `AppDatabase` |
| Database name | `survedge_database` |
| Version | 1 |
| Export schema | false |

## Registered entities

| Entity class | Table |
|---|---|
| `ProjectEntity` | projects |
| `PointEntity` | points |
| `LineEntity` | lines |
| `LinePointCrossRef` | line_point_cross_ref |

## Registered DAOs

| DAO | Purpose |
|---|---|
| `ProjectDao` | CRUD for projects |
| `PointDao` | CRUD for survey points |
| `LineDao` | CRUD for lines and line-point associations |

## Type converters

`Converters` class is registered at the database level and applies to all entities and DAOs. See [converter](db/converter/readme.md).

## Migration strategy

`fallbackToDestructiveMigration()` is active. On a version bump the database is dropped and rebuilt. This is intentional during development — remove before production release.

## Singleton pattern

`AppDatabase` is a thread-safe singleton instantiated via `getDatabase(context)`. The instance is held in a `@Volatile` companion object field and guarded with `synchronized`.

## Subsections

| Section | Description |
|---|---|
| [entities](db/entities/readme.md) | All Room entity classes and their relationships |
| [dao](db/dao/readme.md) | All DAO interfaces and their query methods |
| [converter](db/converter/readme.md) | TypeConverters registered with Room |
| [model](model/readme.md) | Import/export data models |
