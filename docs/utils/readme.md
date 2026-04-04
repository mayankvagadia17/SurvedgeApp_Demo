---
breadcrumb: [docs](../index.md) > utils
---

# Utils

The utilities package provides helper objects for data import and export operations, specifically handling JSON imports and CSV exports for surveying points.

## JSONImporter

`JSONImporter` is a singleton object responsible for parsing JSON strings into a list of `PointEntity` objects.

### Methods

| Method | Parameters | Return Type | Description |
|--------|------------|-------------|-------------|
| `parseJSON` | `jsonContent: String`, `projectId: Long` | `List<PointEntity>` | Parses the provided JSON content and maps each item to a `PointEntity` associated with the given project. |

### JSON Format
The expected JSON structure follows the `PointImportRoot` model:

```json
{
  "points": [
    {
      "id": "PT001",
      "codeId": "LANDMARK",
      "coords": [-122.4194, 37.7749],
      "elevation": 15.5,
      "ts": "2024-04-04T12:00:00Z"
    }
  ]
}
```

*   **coords**: A list of doubles, typically `[longitude, latitude]`.
*   **codeId**: Optional string (defaults to "NO-CODE" if missing).
*   **ts**: Timestamp string.

### Error Handling
If parsing fails (e.g., malformed JSON), the method catches the exception, prints the stack trace, and returns an `emptyList()`.

---

## CSVExporter

`CSVExporter` is a singleton object that generates a standardized CSV string from a list of `PointEntity` objects.

### Methods

| Method | Parameters | Return Type | Description |
|--------|------------|-------------|-------------|
| `generateCSV` | `points: List<PointEntity>` | `String` | Generates a full CSV string with headers for the provided list of points. |

### CSV Format (Survey Standard)
The CSV output includes a comprehensive header followed by data rows. It uses `Locale.US` for double formatting (3 or 8 decimal places depending on the field) to ensure consistent decimal separators.

**Headers:**
`Name,Code,Code description,Easting,Northing,Elevation,Description,Longitude,Latitude,Ellipsoidal height,Origin,Easting RMS,Northing RMS,Elevation RMS,Lateral RMS,Antenna height,Antenna height units,Solution status,Correction type,Averaging start,Averaging end,Samples`

*   **Coordinates**: Both projected (Easting/Northing) and geographic (Lat/Long) coordinates are exported if available.
*   **Precision**: Lat/Long are formatted to 8 decimal places; heights and RMS values to 3 decimal places.
*   **Origin**: Hardcoded to "Global".

---

## Source Files
- [JSONImporter.kt](../../app/src/main/java/com/nexova/survedge/utils/JSONImporter.kt)
- [CSVExporter.kt](../../app/src/main/java/com/nexova/survedge/utils/CSVExporter.kt)
- [ImportModels.kt](../../app/src/main/java/com/nexova/survedge/data/model/ImportModels.kt)
