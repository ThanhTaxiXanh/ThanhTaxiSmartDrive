# Technical architecture — Phase 1 (2026-10-27)

## Implemented boundary

One Kotlin Android app, running as a standard Android Activity:
- `MainActivity`: MapLibre map; real GPS speed; responsive head-unit dashboard (map ~70%, right widgets ~30% in landscape)
- `SettingsActivity`: map style URL; warning tolerance; persisted user image; user-imported JSON road pack; Media access
- `RoadPackRepository`: local validated JSON parsing and atomic replacement; provenance metadata
- `RoadMatcher`: candidate selection with heading, spatial distance and ambiguity rejection
- `SpeedLimitEngine`: pure Kotlin, conservative source/segment/vehicle/lane/time resolution; unknown when evidence insufficient
- `SpeedCompliance`: thresholded alerts while preserving the original legal limit
- `LunarCalendar`: on-device UTC+7 algorithm (user-provided source adapted to package)
- `MediaAccessService`: opt-in notification listener capability for active MediaSession control

## Data flow
`LocationManager(GPS)` → accuracy/freshness checks → `RoadMatcher` →
`RoadContext` + `SpeedEvidence` → `SpeedLimitEngine` → `SpeedCompliance` → UI.

The app DOES NOT yet provide routing, robust multilevel/parallel-road matching, lane localization,
live traffic-sign camera recognition, comprehensive offline maps, a signed nationwide dataset or official Android Auto support.

## Android Auto feasibility
Android Auto uses the Android for Cars App Library with controlled templates. A normal Android Activity's four freely
resized panels cannot be used as an Android Auto template. Navigation category implies actual navigation functionality,
turn-by-turn instructions and full driver-distraction validation. Therefore no `CarAppService` is registered yet.
A compliant template-based navigation surface is a separate Phase 5 deliverable.
Full custom 4-panel layout is intended only for generic Android head units / environments that permit Activities.

## Technical decisions
- Map: MapLibre Native Android 11.11.0; online demo style is a development default, NOT suitable for commercial tile traffic.
- Offline: lunar calendar + local settings + imported road/sign metadata operate without network; **map tiles do not yet work offline**.
- UI: native Kotlin avoids a Flutter/Android Auto bridge dependency for the feasibility version.
- Security: location processed on device, no backend, no background tracking, only user-selected persistable QR URI.
- Performance: currently no device metrics; graphics + thermals require hardware tests.
- Licenses: OSM ODbL attribution and map tile usage constraints must be honored by any deployed tile provider.

## Verification
`gradle :app:testDebugUnitTest :app:assembleDebug` on GitHub Actions.
No successful APK build or real-road validation should be asserted until a successful run is inspected.

Sources: Android for Cars documentation (developer.android.com/training/cars/apps/library);
MapLibre Android Quickstart (maplibre.org/maplibre-native/android/examples/getting-started/);
TT 38/2024/TT-BGTVT user PDF (effective 2025-01-01; amendments must be rechecked).
