# Phase 2 device & legally scoped road trial protocol (not yet executed)

## Preconditions

- Vehicle stopped at safe location while installing/importing/testing settings. No phone interaction, debugging or note-taking by the driver during active driving; use a separate observer if necessary.
- Do not use alpha speed PIP/route preview as the sole source for safe driving or legal speed limits.
- Confirm exact head-unit model, Android OS/API/resolution and **whether it runs an independent Android OS or merely Android Auto projection**. The latter will **not** launch current Activity.
- Only use survey data with permission. Do not upload precise home/personal routes, bystander photos, payment QR images or vehicle registration to a public repository.
- Review current official Vietnam legal text / QCVN and on-road signage at test time, including date, vehicle/road class, lane and time conditions. User supplied TT38 PDF is a reference, **not legal certification**.

## Required test cases and expected outcomes

| ID | Real-world input | Acceptance observations (must record) |
|---|---|---|
| P2-F01 | GPS outside, open sky, stationary and at varied speeds | GPS time age/accuracy, displayed speed, heading; no stale reading retained as current |
| P2-F02 | Losing GPS under roof/tunnel | Show `--` / uncertainty, cancel trusted-speed alert; no invented legal speed |
| P2-F03 | Parallel roads, stacked roads, nearby junction | No forced snap to conflicting roads; 3 distinct reliable fixes and recorded transitions |
| P2-F04 | R.420 entry | Only segment/direction and after passed with accuracy margin; displayed zone/limit conditional |
| P2-F05 | R.421 exit | Applicable new default after actual sign, not on adjacent road |
| P2-F06 | P.127 and surveyed end of speed restriction | Apply only to correct lane/direction/vehicle/effective time and after sign; withdraw after verified termination |
| P2-F07 | Two contradictory signs or sources | `CONFLICT`, PIP `--`, no audio |
| P2-F08 | Sign missing, unverified, expired, unknown lane | `UNKNOWN` or clearly marked `ESTIMATED`, no verified legal alarm |
| P2-F09 | MapLibre Free Drive on independent Android OS | Car position, follow toggle, pan/zoom, responsive map; no camera jump after manual drag |
| P2-F10 | Offline map/network loss | Verify tile geometry and any local glyphs with Airplane Mode after app restart, not just style callback |
| P2-F11 | Visual alert readability | Measure legibility, contrast, distraction and warning timing on device |
| P2-F12 | OSM road matching vs trusted signage | Show OSM geometry as map background only; never label OSM `maxspeed` as authenticated speed |
| P2-F13 | Route preview | At parked state only; no turn-by-turn live instructions, ETA, rerouting or false completion claims |
| P2-F14 | Release GPS and sound permissions | Verify denied/revoked states and local-only processing |

## Report format

Create a **private** review record with: test ID, date and legal rule revision used; device model/version; APK commit/SHA; anonymized track fixture ID; consent and test scenario; expected vs observed PIP source/status; actual observation of sign lane/direction/time; screenshot (redacted); pass/fail; regression issue URL. Never rely solely on one photo of a sign without verifying route segment context.

Suggested structured report (example, **no real data**):

```json
{
  "testId": "P2-F06",
  "commit": "REPLACE_WITH_TESTED_COMMIT",
  "date": "REPLACE",
  "fixtureId": "REDACTED-PRIVATE-TRACK",
  "sourceReview": "PENDING",
  "gpsQuality": "RECORDED_IN_PRIVATE_LOG",
  "expected": "survey only until signed/authenticated",
  "observed": "NOT_RUN",
  "pass": null
}
```

## Blockers to marking Phase 2 complete

1. Trustworthy legal sign/road dataset with independently authenticated source and applicable scope.
2. Real-device GPS and map performance on actual supported phone/head unit.
3. Routing beyond parked prototype: address search/POI, turn-by-turn directions, ETA, deviation and safe rerouting, production restrictions.
4. Verified alert timing/readability and consent-based audio policy.
5. Explicit release/device matrix and QA evidence for all P2-F01–F14.

No "100% Phase 2 done" may be claimed without satisfying these field and functional gates.

## Additions for stationary live GPS pilot

| ID | Device scenario | Required observation |
|---|---|---|
| P2-G01 | User opts in after parking; precise GPS granted | Foreground only; one active provider listener and explicit status |
| P2-G02 | No `Location.hasSpeed` or accuracy exceeds 15m | UNKNOWN; no false on-route assertion |
| P2-G03 | Fix stale by >5s / timestamp duplicated / mock provider | Reject and avoid false route confidence |
| P2-G04 | GPS speed ≥0.8m/s | Stop read-only observer, clear last cached position |
| P2-G05 | Activity paused, permission denied or provider disabled | Listener removed, no background tracking and no crash |
| P2-G06 | Press Điểm đi = GPS at recent stationary fix | Select only unique local surveyed node ≤75m; no automatic route |
| P2-G07 | Imported duplicate point names or ambiguous coordinate | Require explicit node ID; no fabricated address |
| P2-G08 | Airplane mode, locally licensed road pack | Pure local read-only diagnostics; no implied tile/router availability |

All these tests are **NOT YET EXECUTED** on a real device. Kotlin unit
tests and Python QA verify only isolated logic, not platform lifecycle.
