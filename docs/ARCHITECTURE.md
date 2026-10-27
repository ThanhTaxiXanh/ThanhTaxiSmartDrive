# Architecture index — current baseline

**Updated 2026-10-27.** The earlier Phase 1 design sketch was superseded by the
formal [Phase 1 architectural baseline](phase1/ARCHITECTURE.md) and acceptance package
at [docs/phase1/README.md](phase1/README.md).

This index remains here for backward links. The authoritative design set is:

- [Phase 1 PRD](phase1/PRD.md)
- [Phone / head-unit / Android Auto feasibility](phase1/FEASIBILITY.md)
- [Logical module and data-flow architecture](phase1/ARCHITECTURE.md)
- [Data contracts](phase1/DATA-MODEL.md)
- [Dashboard wireframes](phase1/WIREFRAMES.md)
- [SDK and licensing audit](phase1/SDK-AND-LICENSES.md)
- [Architecture Decision Records](phase1/ADRS.md)
- [Risk and phase acceptance gates](phase1/RISK-AND-TEST-GATES.md)

**Current implementation:** one Kotlin Android app with MapLibre Native, a full-custom
`MainActivity` in standalone Activity, a Settings Activity and optional offline screens,
with pure domain speed and routing preview code. Debug APK unit-tested in CI.

**Not currently implemented:** official Android Auto `CarAppService` and Navigation
Templates; legal/source-authenticated nationwide sign packs; real-world validated
routing and speed applicability; true device-tested commercial offline navigation.

Successful compilation is not an Android Auto or road-safety acceptance test.
