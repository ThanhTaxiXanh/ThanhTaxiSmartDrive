# Phase 2 — Map, Speed Engine and Free Drive acceptance packet

**Snapshot:** 2026-10-27. **Status:** IN PROGRESS — algorithm/UI implemented in debug alpha; **field and device acceptance NOT achieved**.

Master Prompt Phase 2 requires map, GPS/Free Drive, road-aware speed limits, PIP, R.420/R.421/P.127 and speed alerts, map matching, and usable routing/navigation. Coding a route-preview algorithm or building an APK is *not* enough to close the phase.

## Engineering implementation matrix

| Requirement | Current implementation | Status |
|---|---|---|
| Live GPS speed, age and accuracy | `LocationManager`, explicit timeout/accuracy guard | Code + unit-level logic; physical tests pending |
| Free Drive and camera | MapLibre `MainActivity` + user pan/follow toggle | Code; head-unit frame rate and sunlight trial pending |
| Display speed limit as PIP | Circular badge + **trust label**: Authenticated / Survey / Estimate / Unknown / Conflict | Code; readability on device pending |
| Warning levels | `SpeedCompliance` NORMAL/APPROACHING/OVER/SERIOUS; user threshold separate from statutory limit | Code; audible warnings limited to independently authenticated source |
| Sign awareness | `TrafficSignEngine` R.420/R.421/P.127/explicit survey end marker, directional segment matching, 800m upcoming preview | Code; legal applicability and current national coverage pending |
| GPS road matching | Spatial index, heading/accuracy rejection, parallel-road ambiguity, 3 unique samples, reviewed turn topology | Code; bridges and intersections need field trial |
| Source verification | User `reviewed=true` + `sourceRef` is **SURVEY_REPORTED**, not VERIFIED | Code; signed authoritative pack verifier not implemented |
| Map search/POI | No licensed Vietnamese geocoder/POI index | NOT IMPLEMENTED |
| Production turn-by-turn, alternatives, ETA, rerouting, voice | Parked-only Dijkstra preview and geometry step description | PARTIAL PROTOTYPE, NOT navigation |
| Field/regulatory evidence | No independently inspected regional sign pack or road-trial record | NOT DONE |

## Speed PIP trust contract

| PIP border / label | Meaning | Can display number? | Audible over-speed? |
|---|---|---|---|
| Red ring / AUTHENTICATED | Future publisher-authenticated applicable evidence | Yes | Only if opt-in |
| Amber ring / SURVEY (unverified) | Self-reviewed with `sourceRef`; no independent authority authentication | Yes, advisory only | **No** |
| Amber ring / ESTIMATED | Conditional default from reviewed road class/zone, may not reflect actual sign | Yes, advisory only | **No** |
| Grey / UNKNOWN | No reliable GPS/match/applicability | `--` | **No** |
| Red-orange / CONFLICT | Contradictory surveyed signs/sources | `--` | **No** |

`SpeedEvidence.publisherAuthenticated` is **NOT parsed from JSON** and defaults false; current source import cannot elevate survey data into verified/authenticated limits. No public trust key/signature verification has shipped. **Therefore the current build intentionally cannot play a legal-limit audio alarm based solely on user-imported road packs.** The "Beep" setting is a future preference for authenticated evidence and must not be described as active for unverifiable surveys.

Unit integration replay is synthetic, not an actual approved road or sign dataset.

## Architecture links and caveats

- Speed evidence: `domain/SpeedLimitEngine.kt`
- Sign succession: `domain/TrafficSignEngine.kt`
- Visual trust policy: `domain/SpeedPipPolicy.kt`
- Alerts + debouncing: `domain/SpeedCompliance.kt` and `domain/SpeedAlertDebouncer.kt`
- GPS quality: `domain/GeoMatching.kt`, `StableRoadMatcher.kt`, `RoadTopologyGate.kt`
- Road pack parser: `data/RoadPack.kt`
- Route preview only: `navigation/OfflineRouteGraph.kt`, `RoutePreviewInstructions.kt`
- Master Prompt requirements: [Phase 1 traceability](../phase1/REQUIREMENTS.csv)

See [field trial protocol](FIELD-TRIAL-PROTOCOL.md) before any Phase 2 closeout.
