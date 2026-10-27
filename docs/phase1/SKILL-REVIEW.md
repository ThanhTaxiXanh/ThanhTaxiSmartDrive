# SKILL source audit — car-taxi-driver-skill.zip

**Source:** bundled SKILL supplied with Master Prompt (the source is a starter kit, not binding official API/legal evidence). Reviewed 2026-10-27; no original Dart source has been copied into the Kotlin app.

| Input | What it provides | Decision or incompatibility |
|---|---|---|
| `SKILL.md` | Flutter Android Auto concept, P.127/R.420/R.421, camera, UI/CI checklist | Retain requirements; reject unconditional camera override and unsupported UI bypass claims |
| `references/architecture.md` | Phone vs car UI separation and domain/data/services, sign capture pipeline | Retain logical boundaries; use Kotlin Android native now, AndroidX Car App Library later |
| `references/speed_limits_2026.md` | Example taxi class and population-zone speed table | Cross-check with legislation effective at drive time; P.127 applicability is not an unconditional override |
| `assets/map_layer_engine.dart` | `ZoneType`, `RoadType`, `VehicleClass`, `TrafficSign`, `SpeedLimitResult`, `MapLayerEngine` | Domain ideas kept; generic zone lookup is insufficient for road-specific, lane/time/bridge cases |
| `assets/speed_compliance_service.dart` | Soft and hard thresholds, location/speed stream, events | Separate *warning threshold* from *legal limit*; avoid conflating confidence and enforcement |
| `assets/sign_recognition_service.dart` | Proposed TFLite detection/classification type model | Candidate only, no reliable model weights/accuracy or camera permission validation supplied |
| `assets/ui_templates_car.dart` | Flutter Material `CarSpeedDashboard` and alert widgets | Flutter widgets are not Android Auto host navigation templates; suitable only as design inspiration for independent Android UI |
| `assets/test/map_layer_engine_test.dart` | Zone transitions, sign override and vehicle test ideas | Must extend to legal applicability, ambiguity, directed context and conflicting sources |
| `assets/test/speed_compliance_service_test.dart` | Alert tier behavior examples | Ported concept into Kotlin unit tests, not source copied verbatim |
| `assets/.github/workflows/build-apk.yml` | Flutter analyze/test + release APK build | Current app is Kotlin; maintain existing Gradle CI; signing and static analysis still Phase 6 work |
| `assets/skill_manifest.yaml` | Desired permissions/platform/data environment | Do not import unused CAMERA, BACKGROUND_LOCATION, foreground services or token example placeholders into production manifest |

## Resolved contradictory guidance

- Master Prompt VII.3: **camera detection cannot automatically override all sources**. A camera reading must be time/direction/lane/vehicle validated and may conflict with verified signage. SKILL's camera-first unconditional rule is not adopted.
- Master Prompt III: four customizable panels must not be promised on the official Android Auto template host. `flutter_car_app` is an option to assess, not evidence of unrestricted host UI.
- Master Prompt XXII: no licensing circumvention, no unverified legal claims; therefore analyzing Fermata is allowed but copying bypass methods or GPL-3.0 code without approval is not in scope.
- The user-supplied speed table and legal reference PDF are inputs for later compliance review, *not evidence this app maintains the complete current law*.

## Source preservation

Archive is retained as user-provided reference; no wholesale vendoring into this public GitHub repo. The project contains an independently structured Kotlin app and its own test suite. Any future reuse must document code provenance and compatible license.
