# Project Management

[Index](../../index.md) > [UI](../readme.md) > Project Management

This section documents the project management lifecycle, from listing existing projects to creating new ones and managing data exchange.

## Overview

The project management module allow users to:
- **List Projects**: View all previously created/imported projects.
- **Create Projects**: Start new survey projects with specific configurations (CRS, Vertical Datum, Units).
- **Import/Export**: Exchange data via JSON (Import) and CSV (Export) on a per-project basis.
- **Project Selection**: Select a project to enter the mapping interface.

## Components

### Fragments

| Class | Role |
| :--- | :--- |
| `ProjectsFragment` | The primary list view. Displays project cards using `ProjectsAdapter`. Handles navigation to existing projects and triggers the creation flow. |
| `CreateProjectFragment` | A detailed creation screen. Collects project name, author, and coordinate settings. |
| `NewProjectBottomSheet` | A lightweight alternative for quick project creation (requires name and operator). |

### Adapter

| Class | Role |
| :--- | :--- |
| `ProjectsAdapter` | A `ListAdapter` for `ProjectEntity`. Displays the project name and the last modified timestamp. |

### ViewModel

| Class | Role |
| :--- | :--- |
| `ProjectsViewModel` | Manages project-related business logic. Bridges the UI to the `ProjectDao`, `CSVExporter`, and `JSONImporter`. |

## Key Flows

### Project Creation
1. User clicks the "New Project" FAB or button in `ProjectsFragment`.
2. Navigation leads to `CreateProjectFragment`.
3. User enters project details (Name is required).
4. `ProjectsViewModel.createProject` inserts a new `ProjectEntity` with default "WGS84" CRS and "Meters" unit.
5. Upon success, the user is navigated into the newly created project.

### Data Exchange
Accessed via long-press on a project item in `ProjectsFragment`:
- **Export**: Generates a CSV file of all points in the project and saves it to the device's **Downloads** folder.
- **Import**: Allows the user to select a JSON file to append points to an existing project.

## Technical Details

- **Database Source**: `ProjectEntity` (Room DB).
- **State Management**: `ProjectsViewModel` exposes `allProjects` as a `Flow<List<ProjectEntity>>` collected by the Fragment.
- **Edge-to-Edge**: Both `ProjectsFragment` and `CreateProjectFragment` implement manual window inset handling for status/navigation bars and keyboard visibility.
