# Product requirements and six-phase delivery plan

## Goal
Safe, offline-capable taxi navigation support in Vietnam; Android Phone Companion + compliant Android Auto
+ separately deployed full-custom Android head-unit dashboard.

## Phase matrix (actual status as of first commit set)
| Phase | Scope | State |
|---|---|---|
| 1 | Feasibility, architecture, product requirements, CI structure | implemented documentation |
| 2 | GPS, map, speed, signs, routing, Free Drive | partial: GPS/map/free drive + conservative sign matching; new offline graph route planner & parked preview, but no field-verified routing coverage |
| 3 | 4-panel UI, settings, lunar calendar, QR, media, layout presets | partial: standard landscape layout, local settings/image/media; editor/events missing |
| 4 | AI signs, validated offline road packs, downloadable offline tiles | partial: MapLibre offline region pilot + PMTiles v3 SAF importer and raster preview + SHA-256/version rollback + surveyed CSV QA pipeline; no AI or signed authoritative data |
| 5 | Compliant Android Auto CarAppService and tests | not implemented |
| 6 | QA, road surveys in Bắc Giang–Bắc Ninh, signed release | not implemented |

## Feature feasibility
- Official Android Auto: navigation / POI templates subject to category and UX policy, media APIs with permissions.
- Android head unit: custom four-panel Dashboard Activity.
- Requires technical investigation: user-specific vehicle connectivity; tile/map pack distribution, on-device AI, independent legal verification.
- Cannot claim today: Android Auto four freely arranged panels, full traffic-sign coverage, certified legal speed limit, signed release APK.

## Completion gates
1. Unit test success and debug APK from Actions;
2. GPS and map on actual Android hardware;
3. Verified source pack and sign/segment/lane consistency trials;
4. Offline tile load + navigation and route recalculation;
5. Android Auto DHU acceptance under official policy;
6. Security, location retention, licensing and Vietnamese road tests.

The application is not a substitute for visual observation of signs and road conditions.

## Development update (2026-10-27)
- [x] Directed graph Dijkstra with explicit allowlisted turns and unit tests
- [x] Parked-only route preview over MapLibre and routing JSON importer
- [x] Deterministic source-ref CSV pack builder + Python test gate in CI
- [x] Structural sign QA CLI for missing provenance and nearby conflicts (not legal verification)
- [ ] Source verification signatures, national coverage, routing with restrictions
- [x] PMTiles v3 importer and raster preview source; [ ] device-proven offline rendering and vector/MBTiles adapters
- [ ] Android Auto Navigation template and live device driving safety QA (not discoverable with current APK)

- [x] Bounded MVT vector metadata extraction and MapLibre source-layer selection, offline line style preview
- [ ] Offline validated HDX Roads OSM archive in field; signed provenance and navigation semantics

- [x] Follow/recenter camera control and PMTiles vector declared source zoom
- [x] Root directory range/varint checks for PMTiles with none/GZIP compression
- [ ] Real device archive validation, complete glyphs/labels and map source schema QA

- [x] Read per-layer PMTiles field names; on-tap parked road name/ref inspection (not overlaid font labels)
- [x] StableRoadMatcher three distinct consecutive GPS fixes for reviewed segment recognition
- [ ] Device-tested offline glyph/labels & PMTiles street style, actual field road segments and legal sign validation

- [x] Road-name Canvas overlay (system font, zero network glyphs), bounded 20 sample points, 9 labels
- [x] Strict segment transition gate from reviewed routing allowedTurns (when graph exists)
- [ ] Complete offline vector label placement from geometry, automatic hierarchy/typography, actual road packs, on-device QA
