# Mapping AI Context

[Index](../../index.md) > [UI](../readme.md) > Mapping > AI Context

This document defines key terms and concepts used within the mapping module to ensure consistent terminology for AI agents and developers.

## Domain Terms
- **Overlay**: A visual layer drawn on top of the map (e.g., markers, paths).
- **Polyline**: A sequence of connected segments representing a line or boundary.
- **Stakeout**: The process of navigating to a specific coordinate on the ground.
- **Collect**: The action of recording a point's coordinates and metadata into the database.
- **Bullseye**: A high-precision circular UI used during the final stage of a stakeout.
- **Marker**: A visual pin on the map representing a single `PointEntity`.
- **Code**: A classification label assigned to a point (e.g., "M" for Monument, "EP" for EP).
- **Segment**: A single line between two points in a polyline.
- **Object List**: A searchable list of all points and lines currently in the mapping session.
- **Lock Mode**: A state where the map automatically centers/rotates based on the user's GPS position.

## Technical Keywords
- `OSMDroid`: The underlying map library used for rendering.
- `GeoPoint`: The standard latitude/longitude/altitude data structure used by the map.
- `IMapController`: Interface used to programmatically move or zoom the map.
- `FusedLocationProviderClient`: API used for high-accuracy location tracking.
- `LabeledPoint`: A data class combining coordinates with display metadata like IDs and labels.
