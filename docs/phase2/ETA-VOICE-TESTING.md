# Phase 2 — ETA and offline Vietnamese geometry commentary

**Implementation:** 2026-10-27, **alpha**. No real-road certification, no legally verified turn instruction, no automotive host integration.

## ETA

`SimpleEtaEstimator` (pure Kotlin) estimates time from the **graph distance remaining / recent usable GPS speed**. It requires at least 2 distinct, confidently matched position samples with finite speed in [2,33] m/s and takes the median of the last 4 usable moving-speed samples. Invalid, stationary, missing, very high speed, negative distance, GPS uncertainty or route deviation clears the estimate. Estimates above 12 h are discarded. ETA is a *time-remaining approximation*, not a clock-arrival prediction and does **not** model congestion, intersections, speed signs, road restrictions, traffic lights, stops or weather. It displays `—` until trustworthy enough for this prototype.

`NavigationUpdate` now provides `progressMeters`, `etaSeconds`, `nextManeuverIndex` and `totalManeuvers` together with remaining graph distance and the next *geometry-change description*. The Dashboard shows current step count and ETA only while GPS is trusted. On stale GPS and app lifecycle pauses, this disappears.

## Opt-in local speech

In parked route preview, the user can check **Phát mô tả điểm đổi hướng bằng tiếng Việt offline**; it defaults to **OFF**. The setting applies only to the started session and is not persisted. After starting, the Dashboard tests for an *installed Vietnamese TTS voice that declares no network dependency*. No compatible voice → remains silent, not a network synthesis fallback. The user must obtain any voice pack on the Android device separately if needed (subject to the pack's distribution terms). It does not copy fonts or voice assets into the repository.

The voice text always identifies the route as a **survey prototype** and describes a geometric change such as `Hình học đổi hướng sang trái`, rather than giving a legally authoritative imperative `Rẽ trái ngay`. It reminds the driver to prioritize on-road signs. `GuidancePromptPolicy` schedules at most one **far** (≤150 m) and one **near** (≤40 m) prompt per maneuver, under reliable `ACTIVE` with a non-null ETA, and resets on new route / lifecycle. Off-route, unknown GPS, stopped or arrived → no spoken maneuver. `LocalVietnameseGuidance` handles `TextToSpeech` asynchronous initialization, offline voice selection, audio attributes, stop and shutdown. No new location/recording permissions or cloud TTS request.

Android 11+ `<queries>` for `android.intent.action.TTS_SERVICE` is declared, per official docs:
https://developer.android.com/training/package-visibility/use-cases

TextToSpeech API lifecycle/offline voice:
https://developer.android.com/reference/android/speech/tts/TextToSpeech
https://developer.android.com/reference/android/speech/tts/Voice

**Caution:** offline TTS availability is device-specific; Android Emulator instrumentation validates the adapter's safe initialization/shutdown but cannot certify audible Vietnamese synthesis without a local voice pack.

## Deterministic test matrix

- `EtaGuidanceTest`: speed validity, median window, unknown ETA, prompt de-duplication at two thresholds, no speech if unknown.
- `ActiveNavigationEngineTest`: ETA only after 2 usable samples; progress and maneuver indices exposed; reset after uncertainty.
- `ActiveNavigationReplayTest`: fictitious GPS start→next maneuver→near destination→ARRIVED; mock/rejected GPS; 3-fix off-route and parked-only alternate approval.
- `NavigationEmulatorTest`: emulator Android runtime ETA/prompt smoke and local TTS adapter close without assuming an installed language pack.
- Standard Gradle CI + emulator runner API 35 via `.github/workflows/android.yml` and `.github/workflows/android-emulator.yml`.

**Explicitly deferred:** measured accuracy on phone/head unit, map completeness, legal turn controls, ETA benchmarking, Vietnamese TTS subjective quality and automotive audio ducking tests. Unit and emulator results are engineering tests, *not* product/road acceptance.
