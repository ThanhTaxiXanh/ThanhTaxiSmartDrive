# Feasibility study — Android Auto, custom head units and reference code

**Verified 2026-10-27 from official Android documentation and public repositories.**

## Two car environments must not be conflated

**A. Android Auto projection (phone app rendered by car host).** It is not an arbitrary fullscreen Android Activity. Car App Library requires a declared `CarAppService` with a supported app category, compliant templates and testing. For a *true navigation app*, the `NAVIGATION` category and navigation-template permission are relevant; simply showing a map does not establish navigation category eligibility. `NavigationTemplate` and `MapWithContentTemplate` are available to navigation apps, but they do **not** provide an unconstrained 70/30 calendar/QR/media grid. Recent official docs recommend `MapWithContentTemplate` for API level 7+; earlier navigation templates have deprecations. **Do not put QR payment/drag-and-drop editor on a driving navigation template.**

**B. Independent Android head unit (Android OS accepts application Activities).** A native Activity may draw the exact 70/30 four-region layout, offer local media controls and QR image while parked, with responsive layouts. This is a technical feasibility judgment; **the user's particular head unit, including VF e34, has not been validated**. Hardware may be projection-only, restrict APK installation or forbid custom apps. Nothing in an APK can override vehicle firmware limitations.

**C. Phone Companion.** Settings, layout editing, package management, private QR/calendar and diagnostics; present build is one Kotlin Android app with multiple Activities. Future product may split phone/car APKs or use shared Kotlin modules; not required merely to document Phase 1.

**D. Android Automotive OS.** Separate OS installed directly in some cars, with distinct app eligibility and distribution. Not interchangeable with generic head unit or Android Auto projection.

## Feasibility matrix

| Feature | Android Activity / independent head unit | Android Auto | Evidence status |
|---|---|---|---|
| Four widgets simultaneously 70/30 | Feasible using responsive native layout | Not an arbitrary supported template | Native Activity exists; real display trial pending |
| Free Drive + route preview | Feasible, current prototype only | Navigation category requires actual navigation product | CarAppService absent; not host-tested |
| GPS/speed overlay on map | Native control, watch distraction | Within supported navigation UX; may not redraw unrestricted widgets | Device/GPS validation pending |
| Lunar calendar / QR payment custom widget | Feasible in native app when parked | Not promised as an arbitrary car navigation surface | Phone prototype exists |
| Media control | Scoped to exposed Android MediaSessions | Separate eligible media integration, avoid arbitrary cross-app control | Only local listener prototype |
| AI forward camera | Depends on hardware/permissions/thermal/data rights | Car host does not automatically grant access to arbitrary cameras | Feasibility spike, not implemented |
| Offline PMTiles vector/raster | MapLibre Native 11.7+ supports local sources | Requires compliant navigation map surface and archive tests | Parser/code exists, field acceptance pending |
| Full Vietnam speed-sign database | Needs licensed surveys and legal review | Same requirement; host cannot provide authoritative data | Not available |

## Reference repository assessment

### Fermata — https://github.com/AndreyPavlenko/Fermata

- Public repository supports media playback with an Android Auto mode; educational reference for media sessions, Android services, activity lifecycle and build strategy.
- Repository is **GPL-3.0**, which has obligations for derivative distribution. **Do not copy source or noncompliant workarounds into this proprietary-capable app** without license review.
- Android Auto usage patterns in a third-party project are **not platform policy authority**; the Master Prompt statement about copying bypasses is explicitly rejected under its own mandatory safety/compliance principles.

### MapLibre Navigation Android — https://github.com/maplibre/maplibre-navigation-android

- Upstream MIT-licensed navigation SDK; public release **5.0.0 (2026-08-26)**, with breaking changes in request/location APIs and Kotlin Multiplatform changes. Integration is not automatic.
- MapLibre Navigation supplies navigation logic, **not by itself a guaranteed complete offline Vietnam routing database**. Map tiles and directions/graph data are separate concerns. Evaluate bridge to existing native MapLibre renderer, lifecycle, offline storage and route graph coverage in Phase 2.
- Current project uses a smaller custom Dijkstra road graph for **parked preview**; do not claim it is equivalent to production turn-by-turn.

### SKILL archive — user-provided `car-taxi-driver-skill.zip`

Files reviewed: `SKILL.md`, `references/architecture.md`, `references/speed_limits_2026.md`, Dart starter map/speed/sign/UI/test sources and CI YAML.
- Strong ideas to retain: explicit domain/data/UI separation, location → sign → speed pipeline, offline packs, TFLite candidate and CI.
- **Correct** its absolute precedence claim that every camera-detected P.127 overrides all other evidence: user Master Prompt VII.3 explicitly requires applicability assessment and conflict handling.
- Flutter and `flutter_car_app` are presented as starter options, **not verified official car APIs**. Prefer official Kotlin Android for Cars integration for future Phase 5.
- Avoid copying Flutter starter code into Kotlin without porting/testing. Sample speed tables are design inputs, not legal certification.
- The skill's broad background camera/car speed permissions must not be copied blindly; request only permissions justified by implemented functionality.

## Verified official sources

- https://developer.android.com/training/cars/apps/library
- https://developer.android.com/training/cars/apps/library/set-up-project
- https://developer.android.com/training/cars/apps/navigation
- https://developer.android.com/docs/quality-guidelines/car-app-quality
- https://developer.android.com/training/cars/apps/testing
- https://github.com/AndreyPavlenko/Fermata
- https://github.com/maplibre/maplibre-navigation-android/releases

**Phase 5 go/no-go:** true turn-by-turn + policy category fit + `CarAppService` and templates + DHU/vehicle test + compliant distribution; do not create a fake navigation category just to appear in launcher.
