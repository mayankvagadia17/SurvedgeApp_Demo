[docs](../readme.md) / app

# app

## Source file

`app/src/main/java/com/nexova/survedge/SurvedgeApplication.kt`

## Role

`SurvedgeApplication` is the Android `Application` subclass. It is the first class instantiated when the process starts. Its sole responsibility is initializing the OSMDroid map library before any Activity or Fragment runs.

There is no dependency injection framework in this project. No Hilt, Koin, or Dagger. Manual singletons are used where global state is needed.

## Initialization flow

1. `onCreate()` is called by the Android runtime at process start.
2. `initializeOsmdroid()` is called immediately.
3. OSMDroid configuration is loaded from `SharedPreferences` (`"osmdroid"` key).
4. The user agent is set to the application package name.
5. The tile cache base path is resolved — see platform difference below.
6. Cache directories (`osmdroid/` and `osmdroid/tiles/`) are created if missing.
7. Cache size limits and download thread counts are applied.

## OSMDroid tile cache — platform difference

| Android version | Base path |
|---|---|
| Android 10+ (API 29+) | `getExternalFilesDir(null)/osmdroid` |
| Below Android 10 | `Environment.getExternalStorageDirectory()/osmdroid` |

This is a known platform difference. No unification is planned at this time.

## Cache configuration

| Setting | Value | Notes |
|---|---|---|
| `cacheMapTileCount` | 2000 tiles | |
| `cacheMapTileOvershoot` | 200 tiles | |
| `tileFileSystemCacheMaxBytes` | 1 GB | Placeholder — not tuned for a specific device |
| `tileFileSystemCacheTrimBytes` | 800 MB | Placeholder — trim target when cache exceeds max |
| `tileDownloadThreads` | 4 | |
| `tileDownloadMaxQueueSize` | 200 | |
| `tileFileSystemMaxQueueSize` | 200 | |

## Known gaps

- `initializeOsmdroid()` catches all exceptions silently. If OSMDroid initialization fails (e.g. external storage unavailable), no error is logged and the failure is invisible. At minimum, a warning log should be added to the catch block.
