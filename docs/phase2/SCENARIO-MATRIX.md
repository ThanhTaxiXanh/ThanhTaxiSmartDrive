# Deterministic speed/sign scenario matrix

The test files below use fictitious geometry and locally constructed `SpeedEvidence`;
they **do not authorize legal speed limits**.

| Scenario | Test | Expected state |
|---|---|---|
| Taxi inside zone, divided road 60 | SpeedLimitEngineTest | ESTIMATED |
| Taxi outside zone, two-way road 80 | SpeedLimitEngineTest | ESTIMATED |
| R.420 passed / R.421 passed | TrafficSignEngineTest | Conditional zone transition, not before the sign |
| Survey P.127 passed and reviewed | Phase2SyntheticReplayTest | SURVEY_REPORTED, amber, no audio |
| Explicit end of surveyed speed restriction | Phase2SyntheticReplayTest | Falls back to conditional default, still no audio |
| P.127 different lane or opposite direction | TrafficSignEngineTest | Not applicable |
| Expired/early sign evidence | TrafficSignEngineTest | Not applicable |
| Contradictory P.127 | TrafficSignEngineTest | CONFLICT or empty speed evidence |
| Missing sourceRef on sign | TrafficSignEngineTest | Ignore, no elevated trust |
| Parallel road geometries | Phase2SyntheticReplayTest / GeoMatchingTest | Match UNKNOWN |
| 3 unique GPS fixes / stale gap | StableRoadMatcherTest | Stabilize or revoke |
| Unsupported transition between surveyed directed edges | RoadTopologyGateTest | Reject switch |
| Trusted vs self-reviewed publisher | SpeedLimitEngineTest | VERIFIED only through non-JSON authenticated path |
| Visual and audible PIP policy | SpeedPipPolicyTest | Audio only for authenticated excess |
| Repeated audible alerts | SpeedAlertDebouncerTest | Suppress sound bursts; reset on unknown |

All tests must continue to pass on GitHub Actions. A passing test only verifies its assumptions; the missing coverage in the field protocol remains mandatory.

## Synthetic destination/deviation cases (new)

| Scenario | Test | Expected |
|---|---|---|
| Exact node ID or near coordinate | SurveyNavigationPreviewTest | Resolve the specific surveyed node only |
| Unknown street name / faraway coordinate | SurveyNavigationPreviewTest | NOT_FOUND, never invoke external geocoder or invent route |
| Two nearby junctions at comparable distances | SurveyNavigationPreviewTest | AMBIGUOUS; no automatic snap |
| One off-route GPS sample | SurveyNavigationPreviewTest | SUSPECTED, no immediate reroute |
| 3 distinct consecutive off-route samples | SurveyNavigationPreviewTest | OFF_ROUTE as *simulation only* |
| Duplicate timestamp / unreliable GPS / long gap | SurveyNavigationPreviewTest | No false confidence |
| Alternate route from reviewed directed graph | SurveyNavigationPreviewTest | Only traversable approved edges and turns |
| Route against direction | SurveyNavigationPreviewTest | No alternative |

## New active engine state tests

| Scenario | Test | Expected |
|---|---|---|
| Fresh GPS near routed edge | ActiveNavigationEngineTest | ACTIVE with remaining metres and geometry text |
| Inaccurate/mocked/stale fix | ActiveNavigationEngineTest | GPS_UNCERTAIN |
| GPS bearing strongly opposite edge | ActiveNavigationEngineTest | GPS_UNCERTAIN; never reroute on heading mismatch |
| Parallel ambiguous candidates | ActiveNavigationEngineTest | Do not invent edge position |
| Three fresh off-route fixes | ActiveNavigationEngineTest | OFF_ROUTE and **candidate** only |
| User attempts candidate while moving | ActiveNavigationEngineTest | Not accepted |
| User accepts when parked | ActiveNavigationEngineTest | Reacquire fresh GPS on new route |
| Near destination 3 fixes | ActiveNavigationEngineTest | ARRIVED tentative |
| Approved directed turn missing | ActiveNavigationEngineTest | Route rejected |
| MainActivity on Android emulator | NavigationEmulatorTest | Activity launches; permission and pure engine smoke checks |

## New ETA/voice scripted replay cases

| Scenario | Test | Expected |
|---|---|---|
| Two reliable moving GPS samples | EtaGuidanceTest / ActiveNavigationEngineTest | ETA becomes available |
| One sample, stopped, too fast, missing or stale speed | EtaGuidanceTest | No ETA or voice |
| Multiple ETA samples with one speed outlier | EtaGuidanceTest | Robust median instead of last-value jump |
| Cross 150m and 40m upcoming geometry change | EtaGuidanceTest / ActiveNavigationReplayTest | One message per threshold, no duplicate |
| Mock GPS, stale GPS or off-route | ActiveNavigationReplayTest | Guidance / ETA cleared |
| Full scripted fictional A→B→C, near C three times | ActiveNavigationReplayTest | Tentative arrival and silent prompt |
| TTS unavailable on Android emulator | NavigationEmulatorTest | Adapter safe shutdown, silence |
