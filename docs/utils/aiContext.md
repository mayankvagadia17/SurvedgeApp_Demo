---
breadcrumb: [docs](../index.md) > [utils](readme.md) > aiContext
---

# AI Context: Utils

This document defines key terms and schemas for the utilities used in data persistence and interoperability.

## Key Terms

- **JSON Import**: The process of converting a structured JSON file into internal `PointEntity` objects.
- **CSV Export**: The process of generating a comma-separated value file following surveying standards (including RMS values, solution status, and antenna heights) for use in office software.
- **PointImportRoot**: The top-level container for the JSON import schema.
- **RMS (Root Mean Square)**: A measure of precision for coordinates (Easting, Northing, Elevation, Lateral).
- **Solution Status**: The fix status of the GNSS measurement (e.g., FIX, FLOAT).
- **Antenna Height**: The offset from the ground point to the GNSS receiver's Phase Center.

## Formats & Schemas

### JSON Schema (PointImportItem)
- `id`: Unique identifier for the point (String).
- `codeId`: Feature code (e.g., "POLE", "EDGE").
- `coords`: Array containing `[longitude, latitude]`.
- `elevation`: Vertical height.
- `ts`: ISO-8601 timestamp.

### CSV Columns (Priority Fields)
- `Name`: Point ID.
- `Code`: Measurement code.
- `Easting/Northing`: Local projected coordinates.
- `Longitude/Latitude`: WGS84 geographic coordinates.
- `Elevation/Ellipsoidal Height`: Vertical measurements.
- `RMS Values`: Quality indicators for the measurement (Precision).
