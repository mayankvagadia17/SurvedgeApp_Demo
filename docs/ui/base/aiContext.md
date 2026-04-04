# AI Context: Base UI

## Key Terms

- **BaseActivity**: The fundamental parent class for UI screens in Survedge. It centralizes "Location First" logic.
- **Location Enforcement**: The process of ensuring both permissions are granted AND hardware location services are toggled ON before the app proceeds.
- **Activity Result API**: The mechanism used for requesting permissions (`locationPermissionLauncher`) and handling settings results (`locationSettingsLauncher`).
- **Location Provider**: Refers to `GPS_PROVIDER` (satellite) or `NETWORK_PROVIDER` (cell/Wi-Fi) checked via `LocationManager`.
- **Permission Rationale**: A dialog shown explaining *why* the app needs location before the system prompt appears, as per Android best-practices.

## Design Patterns
- **Template Method Pattern**: `BaseActivity` provides the structure for location checking while allowing child classes to override specific hooks (`onLocationServiceEnabled`, etc.).
- **Foreground Monitoring**: Passive polling (2s interval) ensures the app reacts if the user toggles location OFF via the notification shade while using the app.
