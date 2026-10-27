# Product Requirements Document (PRD) — Thanh Taxi Smart Drive

**Revision:** Phase 1 baseline, 2026-10-27. **Status:** design baseline, not production acceptance.

## 1. Problem, audience and primary journeys

Vietnamese taxi drivers need quickly readable GPS speed, *applicable* segment-level speed-limit evidence and navigation while driving, and a configurable calendar/payment/media dashboard while parked. A phone is the preferred configuration/data-management surface. Full four-region custom UI requires a compatible **standalone Android head unit**; **Android Auto** is a distinct and restricted template-based product surface.

Primary users:
- Taxi driver (driving/Free Drive): map, GPS speed, verified/current limits, uncertainty states, glanceable alerts.
- Taxi driver (parked): offline map/sign packages, QR selection, layout editor, configuration, calendar events, diagnostic checks.
- Data survey/review operator: maintain geometry/sign applicability, provenance, approvals and version history.
- QA/release operator: test real-device compatibility, offline behavior, policy, privacy and GPS/data safety.

### User journeys

**J-01 Free Drive:** start without destination → permission state → map + GPS → confidence-checked matched road → verified-speed status or "—" → nonintrusive warning if evidence supports it. If GPS stale, ambiguous, contradictory or data absent: no invented limit.

**J-02 Parked setup:** stop car → confirm parked state → select licensed map style/PMTiles → validate bounds/checksum/license → preview online/offline → assign dashboard map source → return. Never imply successful tile rendering just from archive import.

**J-03 Payment:** while parked, select local QR image → show to passenger → restore dashboard; displaying a QR never means payment confirmed.

**J-04 Custom head-unit dashboard:** present four regions at supported aspect ratio, with a persistent priority speed/map area. Phone edits layouts while parked; monitor and phone share settings only in supported architecture.

**J-05 Official Android Auto:** only when navigation category criteria/functional turn-by-turn and Android for Cars templates are met; no arbitrary four-panel overlay, QR/editor/video interaction while driving.

**J-06 Road-data lifecycle:** legally permitted source → per-record provenance → geometry/topology validation → human review → digitally signed package (future) → local version/rollback → conflicting or unknown evidence remains unknown.

## 2. Requirements baseline

Complete machine-readable trace: [REQUIREMENTS.csv](REQUIREMENTS.csv). The IDs below are testable acceptance targets and **not claims of current completion**.

### Functional
- **MAP-01–MAP-06:** GPS display, 2D heading/free drive, map renderer, offline selectable source, confident map matching, route search/alternatives/recalculation/voice.
- **SPD-01–SPD-06:** GPS speed/PIP limit, source applicability with vehicle/direction/lane/time/zone, speed warning tiers, stale/unknown/conflict, R.420/R.421/P.127 and termination rules, verified per-segment data.
- **SIG-01–SIG-04:** sign imagery/geolocation and future AI classification; per-record proof, directional/lane validity and error reports.
- **DASH-01–DASH-04:** head-unit 70% map + right calendar 25% / QR 55% / Media 20% of right height; 16:9/16:10/21:9 support; editor/presets; driving vs parked interaction limits.
- **CAL/QR/MEDIA:** offline Vietnam lunar calendar and user events; local payment images and profiles; permission-scoped MediaSessions.
- **AUTO-01–AUTO-03:** official Android Auto eligible navigation surface; separate template layout; host compatibility and testing.
- **OPS-01–OPS-05:** offline/AI packages, provenance+rollback, privacy, build/test/debug artifacts and release signing.

### Nonfunctional
- Privacy: location processed locally by default; no network transfer of GPS or payments without explicit purpose and consent. Minimize permissions; documented deletion.
- Safety: data without trustworthy applicability is not a legally authoritative speed limit. Driver distraction constraints override custom interaction.
- Offline: specify which subsystems operate without network (lunar, GPS reading, imported tile styles/data). Routing and all relevant raster/vector/glyph assets need **separate** evidence.
- Reliability: handle no GPS, GPS jumps, weak bearing, contradictory signs, network loss, PMTiles corruption, app restart and low memory.
- Performance: define measurable frame/CPU/RAM/battery targets *after* device benchmarking; never report assumed FPS/latency as observed.
- Maintainability: modular domain interfaces; provenance schema; unit/integration/UI/road tests, CI test artifacts, secrets outside repo.

## 3. Platform classification

Legend: **SUPPORTED** by official template where category policy permits; **CUSTOM** only in independent Android Activity; **SPIKE** requires device/license test; **BLOCKED** not an allowable promise in current Android Auto.

| Capability | Phone/standalone Android head unit | Official Android Auto |
|---|---|---|
| Map + permitted navigation UI | SUPPORTED in Activity; real nav requires engine | SUPPORTED via navigation templates only when eligible |
| 4 freely resizable panels simultaneously | CUSTOM head unit only | BLOCKED as an arbitrary template layout |
| Drag-drop layout editor and QR full screen | SUPPORTED when parked on phone/head unit | Not general driving-template feature |
| Generic media overlay / existing media | Android MediaSession capabilities by permission | Independent compliant media apps/services; no arbitrary widget compositing |
| Background camera/AI | SPIKE hardware, privacy/permissions, background policy | No blanket entitlement from host |
| Offline PMTiles | SPIKE rendering on actual device/licensed dataset | Separate rendering and template/surface test |

## 4. Acceptance and boundaries

Phase 1 success = verified feasibility, reference/source checks, coherent architecture + data model + UI wireframes, traceability and CI documentation guard. **It does not equal product acceptance in Master Prompt section XXIII.**

Later success requires proof on supported real hardware, quality maps/road-sign evidence and signed data, Android Auto DHU tests, full product use cases and review.

## 5. External dependencies and non-goals now

No certified nationwide Vietnam sign inventory, no verified VF e34 screen build, no official Android Auto service or navigation voice in current APK, no independent payment verification, no sanctioned fallback that bypasses Android Auto policy. Camera inference, full 3D, connected user calendars, commercial map coverage and signed release are future work.

Source sections: Master Prompt I, III–XIX, XX–XXIII, plus [FEASIBILITY](FEASIBILITY.md).
