# SDK, repository and licensing audit

**As inspected 2026-10-27.** Source code, map tiles, imported geographical data, trademarks, sign photos and ML model weights have **different** licensing requirements.

## 1. Existing build dependencies (from tracked Gradle)

| Component | Current version | Assessment |
|---|---|---|
| Kotlin Android | 2.1.20 | Existing baseline; compiler changes require CI |
| Android Gradle Plugin | 8.7.3 | Existing baseline |
| Gradle | 8.9, provided in GitHub Actions | Existing baseline; wrapper not committed; local developer needs compatible Gradle |
| Java | 17 | Existing CI |
| Android API | compile/target 35, min 26 | Android phone/head-unit floor, NOT proof of car host support |
| MapLibre Native Android | `org.maplibre.gl:android-sdk:11.11.0` | BSD-2-Clause; renderer; supports local PMTiles 11.7+ |
| JUnit | 4.13.2 | Unit tests |
| `org.json` | 20240303 test dependency | JVM tests; Android includes framework JSON |
| Flutter/TFLite/AndroidX Car App | not linked | Planned/optional, not Phase 1 production dependencies |

Release APK signing and pinned full dependency lock/transitive-license SBOM are **not complete**.

## 2. MapLibre stack and navigation tradeoff

- MapLibre Native Android is open-source **BSD 2-Clause**, allows integration subject to preserving notices. `MapLibre Navigation Android` uses **MIT** according to its upstream repository. Do not conflate MapLibre tiles/rendering with direction/routing service or road graph.
- `MapLibre Navigation Android 5.0.0` published 2026-08-26. It contains breaking request/location/Kotlin Multiplatform changes. Existing custom graph code does not automatically implement that SDK; adopt only behind a `NavigationEngine` interface after Android/gradle compatibility and offline routing tests.
- `PMTiles` file-backed sources via `pmtiles://file://` are supported in MapLibre Native Android 11.7+; PMTiles is NOT the same as MapLibre OfflineManager network region downloads and doesn't automatically include route graph, glyphs or map source-layer styling.
- `Fermata` is **GPL-3.0**, so COPYING code into a differently licensed app entails derivative-license obligations; **analysis only, no copied implementation or proprietary bypass**. Support for Android Auto claimed by the repository is not evidence an unrelated app can bypass official UI restrictions.

## 3. Geographic data and tile licenses

- OSM data is **ODbL**; attribution to OpenStreetMap is required for relevant publicly used map products. Share-alike database provisions may apply depending on how derived datasets are produced; obtain legal review for commercial data redistribution.
- HOT/HDX, OSM region archives: check the **specific resource metadata/license/attribution**. An archive filename or being free-to-download does not prove permission to redistribute/offline cache commercially.
- OpenFreeMap public online tiles: Terms as of assessment permit specific bounded offline downloads **at most 3,000 tiles OR 50 MB per download, max four parallel requests and a unique app User-Agent**. The current generic MapLibre OfflineManager pilot does **not independently enforce all provider-specific conditions**; do NOT certify or deploy it against OpenFreeMap commercial/public infrastructure before implementing and auditing limits.
- PMTiles demo raster archives are test fixtures with their own image/style licenses; sample geography may be outside Vietnam. Never label demo tiles "Bắc Giang fully offline."
- OSM traffic signs and road geometry are neither complete nor authoritative legal evidence. Government signage/traffic regulations and any changes must be verified by qualified sources; a self-tagged `reviewed` Boolean or checksum is not authorization.

## 4. Compliance inventory and blockers

| Concern | Phase | Audit status |
|---|---|---|
| BSD and MIT notices + SBOM for shipping binary | 6 | Needed before release |
| Fermata GPL / embedded media video features | 1 | Studied; no code copying |
| License and attribution for each map tile/PMTiles | 4 | Must inspect incoming package |
| Provider offline cache quotas and request concurrency | 4 | Enforcement missing for generic downloader |
| Publisher-authenticated road/sign dataset signatures | 4 | Not implemented |
| Camera/GPS/Background location privacy and consent | 4/6 | Detailed implementation test pending |
| Play Android Auto category and UX review | 5 | Not eligible/currently missing |
| Release signing keys in GitHub Secrets, not repo | 6 | Not configured |

## 5. Verified references

- https://github.com/maplibre/maplibre-native/blob/main/platform/android/LICENSE.md
- https://maplibre.org/maplibre-native/android/examples/data/PMTiles/
- https://github.com/maplibre/maplibre-navigation-android/releases
- https://github.com/AndreyPavlenko/Fermata
- https://osmfoundation.org/wiki/Attribution_Guidelines
- https://openfreemap.org/tos/
- https://developer.android.com/training/cars/apps/library/set-up-project
- https://developer.android.com/training/cars/apps/navigation

**Gate result:** version/license *feasibility audit* complete; actual imported providers, transitive dependencies and production redistribution require separate per-release compliance verification.
