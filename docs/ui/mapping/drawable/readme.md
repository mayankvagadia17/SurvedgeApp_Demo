# Custom Drawables

[Index](../../index.md) > [Mapping](../readme.md) > Drawables

This directory contains custom graphics used for map interaction and location display.

## Files
- [CustomLocationPinDrawable.kt](../../../app/src/main/java/com/nexova/survedge/ui/mapping/drawable/CustomLocationPinDrawable.kt)

## CustomLocationPinDrawable
A complex custom `Drawable` used to represent the user's current location on the map.

### Visual Components
- **Outer Ring:** A stroke-based circle using `stakeout_connection_line` color.
- **Inner Circle:** A solid circle using the `primary` color.
- **Triangle Pointer:** A rotating triangle (slate gray) that indicates the user's current bearing or heading.
- **Center Text:** Displays a single character (e.g., "R" for Rover or "B" for Base) in the center of the pin.

### Key Properties
| Property | Type | Purpose |
| :--- | :--- | :--- |
| `text` | `String` | The character displayed in the center circle. |
| `triangleRotation` | `Float` | The rotation angle in degrees for the bearing indicator. |

### Rendering Details
The drawable uses a `Path` for the triangle pointer and applies a `Matrix` rotation around the center of the pin. It also renders subtle shadows to provide depth against the map background.
