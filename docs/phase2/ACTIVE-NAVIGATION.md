# Phase 2 — Active GPS navigation **prototype**, 2026-10-27

**Status:** code-level implementation; *not validated for real traffic, legal turns, vehicle restrictions or production navigation*. Android Activity only; no Android Auto CarAppService.

## Scope of the active path

- Start only from a manually confirmed *parked* **RoutePreviewActivity** after calculating an existing route on an imported `road pack`.
- The route uses a `routing.nodes` / `routing.edges` / `routing.allowedTurns` **directed** topology. `reviewed=true` is self-declared; imported sourceRef is not publisher authentication.
- The chosen edge/node list and total metres are passed to the Dashboard; it validates *every edge*, sequence, reviewed status, reviewed directed turns and total metres before building `ActiveNavigationEngine`.
- Uses the Dashboard's existing GPS provider (no new background location permission). The pure Kotlin engine follows fresh, non-mock, accurate fixes, analyzes heading against edge bearing, detects geometric progress and upcoming geometry changes. The route polyline and a conspicuous **ĐIỀU HƯỚNG THỬ** label are rendered on the independent Android Dashboard.
- Displays estimated **distance remaining on the imported graph** and next geometry-change information; no ETA or voice.
- Rejects ambiguous parallel edges, stale fixes, improbable match-direction changes and jumps forward more than one edge. Three consecutive off-route GPS observations are required to make an **alternative route proposal**.
- **No automatic reroute in traffic**. If a candidate exists, no turn instruction is emitted until the operator manually accepts it *while parked* using **Chọn tuyến khác (khi đỗ)**.
- Three fresh fixes near the last surveyed node produce a tentative arrival status. Arrival is not proof of the passenger's actual destination.
- Press **Dừng tuyến** to terminate the session. GPS stale or Activity paused → `GPS_UNCERTAIN`, with no assertive guidance. No background tracking or recording.

## Start/test procedure on emulator

1. Import a valid **fictitious or licensed** road pack (see `docs/ROAD-PACK.md`) that includes connected `routing` nodes/edges/allowedTurns. PMTiles **alone** cannot produce that routing graph.
2. With the virtual device stationary, use **Settings → Tính thử tuyến trên dữ liệu road pack đã nhập**.
3. Pick named surveyed nodes, IDs or WGS84 coordinates; inspect the route preview and press **Bắt đầu theo tuyến thử (GPS thật)**.
4. Confirm parked status and read the disclaimer. The Dashboard opens with route highlighted and navigation status.
5. Use emulator's **Extended controls → Location → Routes / GPX** to provide test position sequences. Be aware Android marks injected locations as **mock**, and the production engine intentionally **rejects mock fixes**. The separate JVM/instrumentation tests provide synthetic `NavigationFix(isMock=false)` only inside the test process; they do not bypass the application's real GPS safeguards.
6. Stop the route. Android instrumentation runs Activity launch and engine lifecycle on API 35 without requiring physical car GPS.

## Automated tests

**JVM unit:** `ActiveNavigationEngineTest` checks startup, route progress, future maneuver, GPS staleness, heading errors, untrusted mock fixes, duplicate timestamps, 3-fix deviations, approved proposal, directed graph refusal, tentative arrival and lifecycle stop. Other suites test `OfflineRouteGraph`, `RoutePreviewInstructions`, `RouteDeviationMonitor` and signs/speed.

**Emulator instrumentation:** `app/src/androidTest/java/com/thanhtaxi/smartdrive/NavigationEmulatorTest.kt` verifies engine state on Android runtime, explicit permission declaration and basic dashboard Activity startup. Workflow `.github/workflows/android-emulator.yml` uses Android API 35 Google APIs emulator and publishes its report. This **does not validate real GPS accuracy, map tiles, road safety or Android Auto**.

**Standard APK CI** remains in `.github/workflows/android.yml`. Physical car/phone road tests are explicitly postponed by user direction and remain separate, not silently marked passed.

## Known blockers / non-goals

- Current route geometry is constructed from node-to-node line segments, not a full navigation-grade lane map or snapped OSM routing network.
- No way to infer road restrictions, legal turning bans, time-of-day/vehicle rules, road grade/overpass, traffic, construction closures or official speed rules from user-reported `reviewed` tags.
- No signed, independently verified road package; no licensed national geocoding or POI search, no ETA, alternatives optimization, voice/automated guidance, route saving or replay logging.
- The currently displayed geometry-change text is explicitly **not a legally authoritative driving instruction**.
- Android Auto uses a separate approved navigation App Library template/service and cannot be substituted with the Dashboard Activity.

**Completion gate for Phase 2** still includes true road-data validation and supported navigation tests. Passing emulator tests is *engineering evidence*, not field acceptance.
