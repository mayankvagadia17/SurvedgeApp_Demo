# AI Context — Project Management

[Index](../../index.md) > [UI](../readme.md) > [Project Management](./readme.md) > AI Context

## Terminology

| Term | Context |
| :--- | :--- |
| **Project** | The top-level container for all survey data (Points, Lines). Defined by `ProjectEntity`. |
| **CRS** | Coordinate Reference System. Currently defaults to "WGS84". |
| **Vertical Datum** | The vertical reference point for elevation. Currently defaults to "Ellipsoidal". |
| **Distance Unit** | The measurement unit used for distance (Meters, Feet). |
| **Export (CSV)** | The process of converting project points into a comma-separated text file. |
| **Import (JSON)** | The process of parsing an external JSON file to create `PointEntity` records within a project. |

## Knowledge Triggers

- **Empty State**: When no projects exist, `ProjectsFragment` shows a prompt (`clEmptyState`) to create the first one.
- **Modification Date**: Projects are sorted/displayed by `lastModified` timestamp, which is updated whenever points are imported or the project is created.
- **Permissions**: Exporting requires access to the `Downloads` directory via MediaStore API.
