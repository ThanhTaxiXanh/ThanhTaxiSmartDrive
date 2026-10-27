# Phase 2 — Real GPS diagnostic in the parked route preview (2026-10-27)

**Scope: foreground, opt-in, stationary ONLY. This is NOT live navigation and does not enable driver interaction while moving.**

## What has been implemented

1. Parked user opens **Settings → Tính thử tuyến trên dữ liệu road pack đã nhập**.
2. Selects the start and destination by surveyed node ID, **imported point displayName**, or geographic WGS84 coordinates close to a unique node (≤75 m).
3. Computes a reviewed directed route using the imported routing graph.
4. Chooses **Bật GPS thật (khi đỗ)**. Android prompts for `ACCESS_FINE_LOCATION` if necessary. Only `LocationManager.GPS_PROVIDER` is registered on this *foreground* screen; **no background location** and **no telemetry or position log**.
5. Each callback must pass the isolated `ParkedGpsQuality` validation: coordinate bounds; GPS-fix age at most 5 s; accuracy at most 15 m; a reported finite nonnegative GPS speed **below 0.8 m/s**; no mock provider. Missing speed or poor accuracy yields unknown.
6. `RouteDeviationMonitor` reports ON_ROUTE / SUSPECTED / OFF_ROUTE after distinct successive fix timestamps, with reviewed directed turns. The result is labeled **diagnostic**, not a command to drive.
7. When speed reaches ≥0.8 m/s (approximately 2.9 km/h), observer stops. It also stops on `onPause`, app close or GPS provider disabled. The user must explicitly start a new session after returning.
8. **Điểm đi = GPS** uses only a *currently valid fresh stationary fix* to propose a unique nearby surveyed node. The driver must confirm before computing; no automatic route activation.
9. The separate **Mô phỏng 3 GPS** button remains available only when the real GPS observer is off. Synthetic timestamps are never mixed with actual GPS observations.

The device's measured low speed alone does not prove the vehicle is parked. It is a safety gate in addition to the driver's affirmative stationary check. Do not interact with the UI while in motion.

## Current limitations

- No background location, actual turn-by-turn, ETA, voice, automatic deviation-based rerouting while moving or Android Auto NavigationTemplate.
- The monitor computes nearest distance to node-to-node segments of a **self-declared surveyed graph**, not navigation-grade road geometry or lane-level topology.
- Android's `Location.hasSpeed()` can be unavailable when stationary. In that case, the observer shows unknown and refuses to infer parking; use the separate synthetic simulation for testing.
- The current code does not include a field-tested/legally reviewed road pack. No guarantee any surveyed node lies within 75 m of the phone.
- `LocationManager` callbacks and Android permission behavior require instrumented tests on the actual handset/head unit. CI unit tests only exercise the pure Kotlin quality gate.
- The activity does not export location history to files or servers. Manual screenshots or logs captured by a human still require privacy review.

## Device trial before acceptance

1. With vehicle securely parked outdoors, choose a test node near an imported surveyed location; validate Android precise location granted.
2. With GPS speed unavailable, confirm NO_SPEED → unknown and no route acceptance.
3. With accurate, fresh location, confirm read-only diagnostics and no history persistence after leaving.
4. Check airplane mode with locally imported road pack + PMTiles: GPS should remain available if satellite fix exists; online tile style cannot be assumed.
5. Revoke location permission or disable provider: diagnostic stops with explanatory state; no crash.
6. Drive only with a **separate qualified passenger observer**, and without touching the device: confirm app does not facilitate the route-preview interface in motion. Future QA must assess the ≥0.8 m/s cutoff and the limitations of unreliable speed estimates.
7. Inspect device logs after redaction; never post raw traces with homes, registration plates or payment identifiers to the public repository.

See [FIELD-TRIAL-PROTOCOL](FIELD-TRIAL-PROTOCOL.md) P2-F01/02/03/13/14. This is an **engineering milestone**, not Phase 2 closure.
