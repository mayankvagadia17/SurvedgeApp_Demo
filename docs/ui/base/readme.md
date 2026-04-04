# Base UI Layer

## Overview
The `base` package contains foundational classes that provide shared functionality to all activities and view models in the application. Its primary responsibility is managing global requirements such as location permissions and service status.

## Components

### BaseActivity
`BaseActivity` is an abstract class extending `AppCompatActivity`. Every activity in the application should inherit from this class to ensure consistent handling of location requirements.

**Responsibilities:**
- **Location Permission Management:** Automatically checks and requests `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` permissions.
- **Location Service Enforcement:** Monitors whether GPS or Network location services are enabled. If disabled, it prompts the user to enable them via an `AlertDialog`.
- **Reactive UI:** Provides lifecycle-aware monitoring (every 2 seconds) to ensure location remains enabled while the activity is in the foreground.

**Extensibility Hooks:**
- `onLocationPermissionDenied()`: Called when the user refuses location permissions.
- `onLocationServiceEnabled()`: Called when location services are confirmed to be active.
- Customizable dialog strings: `getLocationPermissionRationaleMessage()`, `getLocationDialogTitle()`, `getLocationDialogMessage()`, etc.

**Source Path:** `app/src/main/java/com/nexova/survedge/ui/base/activity/BaseActivity.kt`

---

### LocationViewModel
`LocationViewModel` is a lightweight `AndroidViewModel` used by `BaseActivity` (and potentially other components) to interact with system location services.

**Key Methods:**
- `checkLocationEnabled()`: Returns `true` if either the GPS or Network location provider is active.
- `checkLocationPermission(context)`: Returns `true` if the app has been granted either fine or coarse location permissions.

**Source Path:** `app/src/main/java/com/nexova/survedge/ui/base/viewmodel/LocationViewModel.kt`

## Logic Flow (Location Enforcement)
1. **onCreate**: `checkLocationPermission()` is called.
2. **Permission Request**: If permissions are missing, a rationale is shown or permissions are requested directly using the `ActivityResult` API.
3. **onResume**: If permissions are granted, `startLocationMonitoring()` begins.
4. **Monitoring**: A coroutine checks every 2 seconds if location services are still enabled.
5. **Enforcement**: If services are disabled, a non-cancelable `AlertDialog` forces the user to the Settings screen to toggle location ON.
