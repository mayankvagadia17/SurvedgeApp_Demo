[Index](../../index.md) > [UI](../base/readme.md)

# Main Activity

## Overview
`MainActivity` is the primary container for the application's main functional areas (Device and Mapping/Projects). It manages the bottom navigation and top-level fragment transitions.

## Role
- **Host Role:** Serves as the `FragmentActivity` responsible for hosting the app's multi-tab experience.
- **Navigation:** Implements `BottomNavigationView` with two main destinations:
  - **Device (`R.id.device`):** Displays `DeviceFragment` for hardware connectivity.
  - **Vector (`R.id.vector`):** Displays `ProjectsFragment` (to select a project) or `MappingFragment` (if a project is active).
- **Interface:** Implements `ProjectNavigationListener` to handle transitions from project selection to active mapping.

## Key Features
- **Fragment Management:** Dynamically hides/shows fragments (`DeviceFragment`, `ProjectsFragment`, `MappingFragment`) to preserve state while switching tabs.
- **Navigation Logic:** 
  - `onProjectCreated(projectId)`: Launches `MappingFragment` with a specific `project_id`.
  - `onStartCreateProject()`: Navigates to a transient `CreateProjectFragment` for new project entry.
- **UI Management:** Includes logic to dynamically hide the bottom navigation bar when requested by fragments (e.g., when a bottom sheet is open) or when the soft keyboard (IME) is visible.

## Source Reference
- `com.nexova.survedge.ui.main.activity.MainActivity`
- `layout/activity_main.xml`
