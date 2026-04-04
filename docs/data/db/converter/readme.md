[← Data Layer](../../readme.md)

# Type Converters

**Source:** [Converters.kt](../../../../../app/src/main/java/com/nexova/survedge/data/db/converter/Converters.kt)

Room requires custom type converters to store non-primitive types in the SQLite database. This file contains the converters used by the app.

## Converters

### Date ↔ Long (Timestamp)

| Method | Input | Output | Purpose |
|--------|-------|--------|---------|
| `fromTimestamp(value: Long?)` | Long timestamp (milliseconds) | `Date?` | Converts stored timestamp to Date object |
| `dateToTimestamp(date: Date?)` | `Date?` object | Long timestamp (milliseconds) | Converts Date object to timestamp for storage |

Both methods handle null values safely using Kotlin's `let` operator.

## Registration

These converters are registered with Room via the `@TypeConverter` annotation. Room automatically applies them when reading/writing `Date` fields in entities.
