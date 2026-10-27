# Solution architecture, trust boundaries and failure handling

**Decision:** Kotlin-first Android vertical slice with pure Kotlin domain logic; future official car surface uses AndroidX Car App Library and shares domain/data modules. Flutter remains an optional phone UI technology, not a prerequisite.

## System context

```text
 [Phone user / parked driver]            [Driver / standalone Android head unit]
             |                                           |
             v                                           v
  Phone settings/data UI                      Custom Dashboard Activity
             \                                          /
              \----[local settings + road/map packs]---/
                            |
                    [pure domain services]
            GPS/map matching/sign evaluation/routing/calendar
                            |
                      [MapLibre renderer]
                            |
                 local PMTiles / licensed tile API

 Future Phase 5:
 Android Auto host -> CarAppService -> template Session -> shared domain + permitted map surface
 (separate presentation; NOT the custom four-region Activity)
```

## Logical module contracts (target modular boundaries)

| Module contract | Owns | Implementation in current repository | Future separation |
|---|---|---|---|
| `phone_app` | settings, offline import, permission/QR management | `SettingsActivity`, `offline/*` | UI shell |
| `car_dashboard` | custom 4 panels on standalone OS | `MainActivity` | head-unit Activity |
| `auto_templates` | compliant Android Auto session/templates | **NOT IMPLEMENTED** | Phase 5 CarAppService |
| `location_service` | GPS, validity, bearing/speed | Android LocationManager in `MainActivity` | platform adapter |
| `map_engine` | render/camera, local/remote styles | MapLibre `MapView`, offline modules | MapRepository/MapRenderer interfaces |
| `map_matching` | candidate/ambiguity/stability/topology | `GeoMatching`, `StableRoadMatcher`, `RoadTopologyGate` | pure domain |
| `speed_limit_engine` | vehicle/road/sign rules, unknown/conflict | `domain/SpeedLimitEngine.kt` | pure domain, signed evidence |
| `traffic_sign_engine` | sign applicability and provenance | `TrafficSignEngine.kt`, `RoadPack.kt` | domain + sign repository |
| `navigation_engine` | graph, approved turn traversals | `navigation/OfflineRouteGraph.kt` parked preview | production routing, ETA/voice/recalc |
| `offline_data_manager` | import, SHA/integrity, versions | `offline/*`, `RoadPackRepository` | signed distribution and rollback |
| `calendar_engine` | Vietnamese lunar conversion | `calendar/LunarCalendar.kt` | local core module |
| `media_controller` | authorized MediaSession access | `media/MediaAccessService.kt` | opt-in scoped adapter |
| `sign_recognition_engine` | camera→classify→validate | **NOT IMPLEMENTED** | optional on-device adapter |
| `dashboard_layout_engine` | edit/presets/response | static Activity arrangement only | Phase 3 |
| `sync_service` | phone-head-unit changes, optional backend | **NOT IMPLEMENTED** | opt-in and transport dependent |

No mandatory backend for speed defaults, position processing, lunar calendar, local settings and offline content.

## Real-time pipeline

```text
 LocationManager callback
   -> timestamp/accuracy/speed/bearing sanity
   -> GeoMatcher + spatial index (road context)
   -> StableRoadMatcher (3 DISTINCT GPS fixes)
   -> RoadTopologyGate if reviewed routing graph present
   -> RoadContext (vehicle, lane, direction, timestamp)
   -> verified sign/reviewed data with sourceRef and applicability
   -> SpeedLimitEngine (VERIFIED / ESTIMATED / CONFLICT / UNKNOWN)
   -> SpeedCompliance (warning; never edits legal limit)
   -> Dashboard label (UNKNOWN -> "—")
```

**Critical boundary:** A self-declared `reviewed=true` in a user-imported road pack is **not equivalent to independently authoritative legal verification**. Root SHA-256 checks data corruption but do not authenticate publisher identity. Future trust gateway: audited per-segment provenance and digital signature anchored to a known key.

Navigation and speed signs must use separate, independently validated data, not casually turn visual OSM `maxspeed` or Road MVT geometry into applicable speed regulations.

## Offline/data flows

```text
 Licensed file picked by user (SAF)
    -> private import temp file, cap size
    -> PMTiles v3 metadata + index range checks
    -> SHA-256 integrity
    -> local content-addressed archive and provenance metadata
    -> MapLibre style source ("pmtiles://file://...")
    -> map render (must inspect tile on hardware)
```

`OfflineManager` network tile cache and PMTiles file-backed local tiles are **different** mechanisms. Offline tiles do **not** automatically include a turn-by-turn routing graph.

Road pack JSON: schema/version/series/segments/signs/routing → structural QA → local repository/backup → road/sign domain. Production signed pack and incremental updates are future work.

## Android surfaces + lifecycle

- Phone Settings screens: complex configuration only while parked (driver acknowledgement is not automotive speed telemetry).
- Head-unit Dashboard Activity: 70% map, right 30%; warning speed remains glanceable. On lifecycle stop, drop stale fix state and release listeners.
- Android Auto `CarAppService`: separate navigation-category surface only when product truly meets official criteria; the official host owns templated controls and distraction constraints.
- MediaSession access requires appropriate permission/active supported sessions; not a promise of universal player control.

## Failure modes and required behavior

| Failure | Response |
|---|---|
| GPS missing, stale, low accuracy, jump/ambiguity | Show uncertainty; do not invent speed limit; do not make confident navigation claims |
| Adjacent/overpass road mismatch | Reject ambiguous map matching and transitions; legal limit unknown |
| Conflicting P.127/camera/user report | Check effective lane/time/direction/source; require review; never blindly overwrite |
| Network absent | Keep offline cached map if actually complete; explicit blank-map status otherwise; local calendar/pack works |
| PMTiles wrong layer, missing glyphs, corrupted file | Report reason, allow selecting a valid layer, no silent online fallback |
| Memory pressure / background | Stop expensive UI streams; persist only required settings |
| Unknown Android Auto host capabilities | Render only supported templates; no feature bypass |
| QR/media permission denied | Show disabled state; map/speed remain available |
| Missing signed legal package | Conservative status: NOT VERIFIED / UNKNOWN |

## Architecture quality gates

Pure domain code should not depend on Android UI; platform adapters should not impose speed-limit policy. Add unit tests for invalid GPS, sign scope, unknown/conflict, route directions, lunar leap dates. Phase 2+ must add Android instrumentation/device, CI static analysis and performance profiles.

## Reference

See [ADRS](ADRS.md), [DATA-MODEL](DATA-MODEL.md), [RISK-AND-TEST-GATES](RISK-AND-TEST-GATES.md) and Master Prompt sections III/V–XIX.
