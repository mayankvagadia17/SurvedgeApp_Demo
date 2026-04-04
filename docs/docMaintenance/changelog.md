---
breadcrumb: [docs](../index.md) > [docMaintenance](.) > changelog
---

# Changelog

Entries are prepended — newest first.

| session | date | focus | files | summary |
|---------|------|-------|-------|---------|
| 21 | 2026-04-04 | feat: bottom sheets always overlap nav tabs | 5 files | Redesigned bottom navigation behavior — nav always visible, sheets overlap with proper elevation. Removed nav hide/show state machine; simplified inset listener. All sheets now have elevation > nav (16dp) for correct z-order. |
| n/a | 2026-04-04 | fix: bottom sheet safe area padding | 2 files | Fixed bottom sheets extending into system nav bar safe area by adding proper bottom margin. |
| 20 | 2026-04-04 | fix: bottom sheet nav visibility timing | 4 files | Fixed race condition where bottom nav stayed hidden after closing sheets — moved nav restore into animation callback. |
| n/a | 2026-04-04 | fix: line sheet overlapping | 2 files | Fixed bottom sheet overlapping issues by controlling title visibility and managing state during sheet transitions |
| 19 | 2026-04-04 | index-drift + changelog: Final sync | 4 files | Final documentation sync — all 19 sessions complete |
| 18 | 2026-04-04 | docs/utils | 2 files | Documented CSVExporter and JSONImporter with file format specs |
| 17 | 2026-04-04 | docs/ui/stakeout | 2 files | Documented stakeout models, utility functions, and domain terminology |
| 16 | 2026-04-04 | docs/ui/mapping/maplibre + mapper + drawable | 3 files | Documented marker/polyline helpers, point mappers, and custom pin drawable |
| 15 | 2026-04-04 | docs/ui/mapping/adapter | 1 file | Documented CodeAdapter, EditPointAdapter, and ObjectListAdapter |
| 14 | 2026-04-04 | docs/ui/mapping/overlay | 1 file | Documented all 7 OSMDroid custom overlay classes |
| 13 | 2026-04-04 | docs/ui/mapping/viewmodel | 1 file | Documented MappingViewModel.kt state and operations |
| 12 | 2026-04-04 | docs/ui/mapping/fragment | 1 file | Documented Fragment/Helper/Logic split for Mapping screen |
| 11 | 2026-04-04 | docs/ui/mapping (overview) | 2 files | Created Mapping section overview and domain terminology |
| 10 | 2026-04-04 | docs/ui/project | 2 files | Documented full project management feature set |
| 9 | 2026-04-04 | docs/ui/main + splash + device + custom | 4 files | Documented MainActivity, SplashActivity, DeviceFragment, and SnappingHorizontalScrollView |
