# Product requirements and six-phase delivery plan

## Goal
Safe, offline-capable taxi navigation support in Vietnam; Android Phone Companion + compliant Android Auto
+ separately deployed full-custom Android head-unit dashboard.

## Phase matrix (actual status as of first commit set)
| Phase | Scope | State |
|---|---|---|
| 1 | Feasibility, architecture, product requirements, CI structure | implemented documentation |
| 2 | GPS, map, speed, signs, routing, Free Drive | partial: GPS/map/free drive, conservative directional matching and geocoded signs; routing & verified nationwide signs missing |
| 3 | 4-panel UI, settings, lunar calendar, QR, media, layout presets | partial: standard landscape layout, local settings/image/media; editor/events missing |
| 4 | AI signs, validated offline road packs, downloadable offline tiles | not implemented |
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
