[docs](../readme.md) / [app](readme.md) / aiContext

# AI context — app

## Terms

**Application class**
The Android `Application` subclass. Instantiated once per process lifetime, before any Activity or Service. Used for process-scoped initialization only.

**Singleton scope**
Any state or object initialized in `Application.onCreate()` lives for the entire process lifetime. There is no teardown until the OS kills the process.

**OSMDroid**
The open-source Android map library used in this project. Requires explicit initialization via `Configuration.getInstance()` before any map view is displayed.

**SharedPreferences (`"osmdroid"`)**
OSMDroid persists its configuration to a SharedPreferences file named `"osmdroid"`. This is passed to `Configuration.getInstance().load()` at startup.

**Tile cache**
OSMDroid downloads map tiles from a tile server and caches them to disk. The cache path, size cap, and thread counts are all set in `SurvedgeApplication`.

**DI-free**
This project does not use a dependency injection framework. There is no Hilt, Koin, or Dagger module graph. Dependencies are passed manually or accessed via singletons.

**Scoped storage (Android 10+)**
From Android 10 onward, apps must use `getExternalFilesDir()` rather than the legacy `Environment.getExternalStorageDirectory()` for writable external paths. `SurvedgeApplication` branches on `Build.VERSION.SDK_INT` to handle both cases.
