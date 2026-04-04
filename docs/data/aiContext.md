[docs](../readme.md) / [data](readme.md) / aiContext

# aiContext — data

Key terms for the data layer.

| Term | Definition |
|---|---|
| **Entity** | Kotlin data class annotated with `@Entity`. Each entity maps to one database table. |
| **DAO** | Data Access Object. Interface annotated with `@Dao` containing SQL query methods. Room generates the implementation at compile time. |
| **RoomDatabase** | Abstract base class that Room uses to create and manage the SQLite database. `AppDatabase` extends this. |
| **TypeConverter** | Method pair annotated with `@TypeConverter` that converts a non-primitive type to a type Room can store (e.g. `List<Double>` to `String`) and back. |
| **Cross-ref table** | A join table represented as a Room entity with composite primary key, used to model many-to-many relationships. `LinePointCrossRef` links lines to points. |
| **Relation** | `@Relation` annotation used in a POJO (not an entity) to load related rows eagerly. `LineWithPoints` uses this to load a line with all its associated points. |
| **Singleton** | `AppDatabase.getDatabase()` always returns the same instance. Constructing multiple instances would cause conflicts. |
| **fallbackToDestructiveMigration** | Room migration strategy that drops and recreates the database when the schema version changes. Safe only during development. |
| **exportSchema** | When `false`, Room does not write a schema JSON file to disk. Set to `true` in production to enable schema migration tracking. |
