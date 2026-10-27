# Product requirements and six-phase delivery plan

## Goal
Safe, offline-capable taxi navigation support in Vietnam; Android Phone Companion + compliant Android Auto
+ separately deployed full-custom Android head-unit dashboard.

## Phase matrix — 2026-10-27 after architecture review

**Phase 1 architecture/documentation gate: complete**, conditional on successful GitHub Actions
`python3 tools/phase1_check.py`. See **[Phase 1 acceptance package](phase1/README.md)**
(`docs/phase1/README.md`). This **does not** mean the whole app is complete or has
passed road trials, hardware tests, legal certification or Android Auto integration.

| Phase | Master Prompt scope | Current evidence / state |
|---|---|---|
| **1** | Analyze SKILL and upstream references; Android Auto SDK; architecture; licenses; four-region feasibility | **COMPLETE: architecture baseline** — PRD, feasibility review, SDK and licenses, data model, head-unit/Android Auto wireframes, 44 traced requirements, ADRs, risk gates and CI documentation guard |
| **2** | GPS, map matching, Free Drive, signs, speed/PIP/alerts and routing | **IN PROGRESS** — implemented trust-labeled Speed PIP, sign/GPS matching and alert policy; no field-verified road data, production routing/search or actual device acceptance. See [Phase 2 matrix](phase2/README.md) |
| **3** | 4-panel Dashboard, layout editor, themes, calendar, QR, Media and settings | **PARTIAL PROTOTYPE** — custom Activity, lunar calendar/QR/Media; editable layouts, events/themes and responsive device QA missing |
| **4** | Camera AI, offline map/sign packs, validation and updates | **PARTIAL PROTOTYPE** — PMTiles and QA; AI, signed legal data, full region coverage and device-proven offline render missing |
| **5** | Android Auto CarAppService, navigation templates and host tests | **NOT IMPLEMENTED** — current APK will not show as Android Auto app |
| **6** | Integration/device/road tests, performance, CI and signed release | **PARTIAL SUPPORT** — CI and Debug APK exist; field/device tests, full security QA and signed release missing |

## Feature feasibility
- Official Android Auto: navigation / POI templates subject to category and UX policy, media APIs with permissions.
- Android head unit: custom four-panel Dashboard Activity.
- Requires technical investigation: user-specific vehicle connectivity; tile/map pack distribution, on-device AI, independent legal verification.
- Cannot claim today: Android Auto four freely arranged panels, full traffic-sign coverage, certified legal speed limit, signed release APK.

## Completion gates
0. **Phase 1**: source/SDK review, PRD, architectural design, wireframes, license audit and
   the `tools/phase1_check.py` CI guard documented at [Phase 1](phase1/README.md).
   This is an architecture gate, not Android Auto or commercial driving certification.
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

- [x] Parked-only, offline graph geometry change preview with cumulative distances and audited directed turns
- [x] Source/edge/turn topology structural QA gate in Python CI
- [x] Route preview can use the active local raster/vector PMTiles basemap after SHA-256 check
- [ ] Device trial with validated regional road packs; road restrictions, turns by vehicle/time and Android Auto navigation

### Phase 2 follow-up (2026-10-27)

- [x] Surveyed-node ID / WGS84-coordinate lookup with ambiguity rejection
- [x] Route-deviation detector requiring repeated fresh fixes with quality checks
- [x] Parked-only **synthetic** GPS simulation and explicit optional alternate preview
- [ ] Vietnamese address/POI search from properly licensed index or geocoder
- [ ] Navigation-grade GPS-to-route matcher, live deviation + safe reroute/ETA/voice
- [ ] Real vehicle and field-survey acceptance of signage and legal constraints

See [Phase 2 report](phase2/README.md). These checkmarks indicate
**code-level capabilities, not field acceptance or completion of Phase 2**.
