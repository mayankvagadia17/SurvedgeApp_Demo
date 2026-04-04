# Stakeout AI Context

## Domain Language

- **Stakeout:** The process of locating a point on the ground whose coordinates are known. Also called "Setting Out".
- **Bullseye View:** A zoomed-in precision UI mode that activates when the user is within 0.5 meters of the target.
- **Tolerance:** The acceptable error margin (usually 0.05m or 5cm) for a point to be considered "staked".
- **Cut/Fill:** Vertical guidance terms often used in stakeout. **Cut** means the target is lower than the current ground elevation (requiring a "cut" into the ground); **Fill** means it is higher (requiring ground to be "filled").
- **Pole Height:** The height of the carbon fiber pole used to hold the GNSS receiver. It is subtracted from the antenna elevation to determine the current ground elevation.
- **North/East Offsets:** Guidance instructions telling the user how many meters to move along the North/South and East/West axes to reach the target exactly.
- **Bearing:** The required compass heading (0-360°) to direct the user toward the target from their current position.
- **Haversine Formula:** Used in `CoordinateUtils` to calculate horizontal distance between two points on the Earth's surface.
- **Precision UI Mode:** Refers to the "Bullseye" target visualization used for sub-meter positioning.
