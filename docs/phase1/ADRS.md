# Architecture Decision Records (Phase 1)

### ADR-001 — Separate full-custom head-unit Activity from official Android Auto presentation
**Accepted.** One domain/data layer; standalone Android Activity may implement four widgets, whereas Android Auto uses host-controlled car-app templates and its own category constraints. Reject the shortcut of registering `NAVIGATION` solely to gain launcher visibility, or copying unofficial media/bypass methods.

### ADR-002 — Kotlin-first baseline, optional Flutter phone companion
**Accepted for existing alpha.** The source already compiles in Kotlin with MapLibre Native; retain that validated vertical slice to reduce bridge complexity and CI churn. Flutter is explicitly a *preference* in the Master Prompt, not a mandate requiring a rebuild mid-alpha. Future evaluation of a separate Flutter phone UI must assess application size, native integration, state sync and shared domain cost. Prefer plain domain contracts and JSON/binary packages to tight UI coupling.

### ADR-003 — MapLibre Native, licensed OSM, offline PMTiles with separate routing graph
**Accepted.** MapLibre's rendering and local PMTiles API are viable. An offline map is not an offline routing database. Preserve imported source-layer names and tile-license details; use OpenFreeMap for permitted online trials without assuming universal offline quotas. Do not embed high-volume third-party tile requests beyond provider terms.

### ADR-004 — Fail-closed legal speed evidence
**Accepted.** GPS alone is not road identity; use directional/accuracy/stable-fix/topology checks, legal applicability and dated provenance. Priority isn't blindly "camera first". Uncertain or conflicting sources yield `UNKNOWN`/`CONFLICT` rather than invented legal limits. User-set alert tolerance never changes the legal value.

### ADR-005 — Publisher authentication separate from SHA-256
**Accepted.** SHA validates on-device content integrity against locally stored expected bytes, but cannot prove publisher identity, approval, accuracy or legal effect. Phase 4 needs verified provenance and public-key signatures, revocation and rollback policy before national distribution.

### ADR-006 — Local-first, explicit permissions and no default telemetry
**Accepted.** GPS and calendar should work without backend; QR resides in private Storage Access Framework selection; Media control only on authorized MediaSessions; camera location/audio permissions added only when their functionality exists. Complex configuration requires parked state; app should avoid accumulating location history by default.

### ADR-007 — Phase gates based on evidence, not feature names or successful compilation
**Accepted.** Unit tests + Debug APK in CI are build evidence only. Phase 2 needs road signs and GPS in actual traffic; Phase 3 needs layout editor and device UI tests; Phase 4 needs true offline rendering/AI/data signatures; Phase 5 needs car host template/quality acceptance; Phase 6 needs road test, performance and signed release. Architecture documents can complete Phase 1 while later-phase runtime deliverables remain open.

### Decision supersession

These ADRs supersede unqualified claims in the SKILL about "camera highest priority" and a universal Flutter→Android Auto bridge, while retaining the original intent. Changes require explicit ADR revision plus affected requirement IDs/tests.
