# Phase gates, risk register and verification plan

Date: 2026-10-27. Definitions: **DESIGN-VERIFIED** (source documents/code inspected); **BUILD-VERIFIED** (CI tests/APK); **DEVICE-VERIFIED** (observed on real hardware); **FIELD-VERIFIED** (cross-checked against actual road/sign with appropriate review); **POLICY-VERIFIED** (official host review). These are not interchangeable.

## Phase 1 acceptance

| Gate | Evidence | Result |
|---|---|---|
| P1-G1 Full source inventory | Master Prompt I–XXIV; SKILL, architecture and speed notes examined | PASS (documented) |
| P1-G2 Two reference repositories | Fermata GPL-3.0 and MapLibre Navigation MIT/5.0.0 assessed | PASS (documented) |
| P1-G3 Official Android Auto feasibility | Car App Library, service/categories, navigation templates and DHU restrictions recorded | PASS (documentation); service NOT implemented |
| P1-G4 Architecture and data contracts | Shared domain and distinct phone/custom headunit/official car app surfaces documented | PASS (design) |
| P1-G5 SDK and license matrix | Gradle/SDK versions, PMTiles, OSM, Fermata, tile provider limits recorded | PASS (audit inventory); redistribution rights unresolved |
| P1-G6 Four-panel feasibility | 70/30 wireframe, right 25/55/20 split, responsive device matrix and Android Auto exclusion | PASS (design); real hardware trial unresolved |
| P1-G7 PRD and traceability | Requirements IDs, named later-phase gates and mechanical guard | PASS after CI guard |
| P1-G8 Minimal technical evidence | Existing Kotlin/MapLibre app, domain unit tests and Debug APK in Actions | BUILD-VERIFIED; not driving acceptance |

**Phase 1 disposition:** architecture gate complete after documentation CI succeeds; never substitute it for product acceptance in Master Prompt XXIII.

## Risk register (severity, consequence, controls, owner gate)

| ID | Severity | Risk | Architecture control and future verification | Phase |
|---|---|---|---|---|
| R-01 | Critical | Incorrect claimed legal speed limit | Fail closed; per-segment/direction/lane/time, signed source, legal review | 2/4/6 |
| R-02 | Critical | Map matching on parallel/overpass route | GPS accuracy, stable fixes, reviewed topology, road trials | 2/6 |
| R-03 | High | Car host refuses app | Separate template-based CarAppService, real navigation, DHU tests | 5 |
| R-04 | High | Four-panel layout requested through projection-only screen | Capability detection; distinct generic head-unit target | 3/5 |
| R-05 | High | Map tile license/noncompliant download volumes | Provenance manifests, attribution, throttle/limit or own licensed server | 4/6 |
| R-06 | High | Offline PMTiles shows blank image | Device no-network pan/zoom/reboot test and tile/glyph review | 4/6 |
| R-07 | Critical | User-imported dataset self-labels reviewed | Source review, signed release keys, publisher trust and revocation | 4/6 |
| R-08 | High | Driver distraction from QR/settings | Parked-only interaction, glanceable overlays, Android Auto templates | 3/5/6 |
| R-09 | High | GPS/location privacy leakage | Local-first, permission minimization, explicit retention/deletion and audit | 6 |
| R-10 | Medium | TFLite/AI performance/false detection | Optional capture, confidence/applicability gate, on-device benchmarks | 4/6 |
| R-11 | High | Navigation reference SDK API break | Abstract adapter, evaluate 5.0.0 changes in isolated spike | 2 |
| R-12 | Medium | Wide layouts unreadable on actual dashboard | VF e34 capability audit, physical DPI/sunlight tests, min touch targets | 3/6 |
| R-13 | High | GPL incompatibility from Fermata code reuse | Analysis only, no source transplant, legal license review | 1/6 |
| R-14 | Medium | Release signing/secrets exposed or incompatible | signing via GitHub Secrets, reproducible process and SBOM | 6 |

## Future acceptance gates

**Phase 2 Map & Speed:** real GPS log tests with consent; multiple field segments (R.420, R.421, P.127), lane/time applicability; unknown/conflict; visual speed PIP; alerts measured/verified; free-drive in foreground. Routing preview alone does not pass.

**Phase 3 Dashboard & Settings:** layout editing/locking/named presets, responsive 16:9/16:10/21:9 in real devices, lunar leap and event CRUD tests, QR persistence, media permission/session integration.

**Phase 4 AI & Offline:** imported licensed archive render in airplane mode after reboot, PMTiles vector labels, sign-source provenance and digital signatures, on-device AI false-positive tests, incremental offline update/rollback and provider-limit compliance.

**Phase 5 Android Auto:** category-eligible navigation engine, separate CarAppService + approved templates, DHU/vehicle tests, policy quality checklist and compliant distribution.

**Phase 6 QA & Release:** instrumentation tests, route/road survey evidence, thermal/battery/RAM/device logs, localization/privacy/permission audit, legal source signoff, release signing and SBOM.

## CI and device acceptance differentiation

`python3 tools/phase1_check.py` validates **documentation completeness and requirement trace**, not legal correctness.
`python3 -m unittest discover -s tools/tests -v` validates Python tooling.
`gradle :app:testDebugUnitTest :app:assembleDebug` builds debug APK and runs Kotlin unit tests.
These alone do not prove Android Auto host acceptance, real-world GPS matching or tile rendering. File issues and attach redacted device evidence to the repository before changing any FIELD-VERIFIED/DEVICE-VERIFIED status.

Hardware test prerequisites: model/year/head-unit OS/API resolution and aspect ratio, ability to install third-party APK or Android Auto-only projection, Android phone model/version, legally permitted offline archive, test routes with independently observed signs and a parked safety procedure.

## Privacy and legal safety

Never record precise personal/route history in public CI artifacts; remove identifying GPS/QR/person details from example uploads. Traffic law/standards require contemporaneous legal review (Master Prompt references TT38/2024, QCVN41/2024, Road Traffic Safety law) and later amendments—not one hardcoded table or SKILL assert. No legal certification is claimed here.
