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
| Survey node/name/coordinate lookup | Imported short displayName, unique node ID, WGS84 ≤75m; duplicate names/nearby ties rejected | ALPHA PARKED-ONLY; **not national geocoding** |
| Map search/POI | No licensed Vietnamese address or POI geocoder | NOT IMPLEMENTED |
| Route deviation and alternative preview | Three distinct synthetic fixes; new **opt-in foreground real GPS parked diagnostics** with strict age/speed/accuracy gating; surveyed candidate only | ALPHA parked-only; NOT navigation or moving-vehicle rerouting |
| Production turn-by-turn, alternatives, ETA, rerouting, voice | No live turn instructions, licensed address search or rerouting in motion | NOT IMPLEMENTED |
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

## Parked route selection and deviation *simulation*

Open **Settings → Tính thử tuyến trên dữ liệu road pack đã nhập** after
parking. A road pack with `routing.nodes`, `routing.edges` and
`routing.allowedTurns` is required, with user-asserted `reviewed`
edge and turn flags (not legal validation).

1. Enter start and finish **existing node IDs** or coordinates like
   `21.12345,106.54321`. Coordinates are accepted only if one unique
   node exists within 75 m. Near ties within 20 m or unknown/distant points
   are refused. This is **not** address/POI search for Vietnam.
2. Tap **Tính tuyến thử** and inspect surveyed directed edges.
3. Enter a hypothetical GPS coordinate into the simulation field and tap
   **Mô phỏng 3 GPS**. The program creates three synthetic timestamped
   observations explicitly for offline algorithm demonstration. It does
   **not** use the device's live GPS or assert actual travel.
4. For a simulated deviation (cross-track over 30 m or 2×accuracy),
   the app proposes a new route only if a unique surveyed junction is
   within 100 m and Dijkstra can traverse surveyed **directed**
   edges/approved turns to the original destination.
5. A proposed alternative is **never automatically applied**; tap
   **Xem tuyến khác** (while parked) to inspect it.

Do not use this feature to issue instructions to a moving driver. No
live ETA/voice/location following/rerouting is implemented.

## Latest Phase 2 pilot — 27 Oct 2026

- [Real GPS read-only, foreground parked diagnostic](REAL-GPS-DIAGNOSTIC.md):
  opt-in exact location, no tracking history; stops when GPS speed ≥0.8 m/s
  or Activity pauses. Without reported speed it stays UNKNOWN.
- [Local point names from surveyed licensed package](LOCAL-SEARCH-SCHEMA.md):
  optional `routing.nodes[].displayName` search with strict ambiguity checks.
  This is **not** a licensed nationwide Vietnamese address/POI geocoder.
- Self-reported `reviewed` route graph remains an alpha input, not evidence of
  road legality, turn rules, signal priority or route safety.
