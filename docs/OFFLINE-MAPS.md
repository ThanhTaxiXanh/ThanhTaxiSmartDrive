# MapLibre offline pilots — scope and risks

Initial implementation: MapLibre Native Android OfflineManager with small bounding boxes and zoom range 11–15.
A region is only treated as complete when SDK OfflineRegionStatus.isComplete returns true, not when the
download starts or an initial metadata record exists. The app can list, pause, resume, delete the regions.

Pilot boxes (WGS84, not administrative boundaries):
- Former Bắc Giang city centre: SW (21.246667, 106.18), NE (21.286667, 106.22)
- Bắc Ninh city centre: SW (21.163333, 106.03), NE (21.203333, 106.07)

These are **not** full provincial map packages and **not** route graph databases.

Source for approximate place centres: UN Geographical Names dashboard
- https://ungegn.un.org/dashboard/cities/details?id=1201
- https://ungegn.un.org/dashboard/cities/details?id=1202

MapLibre offline region API:
https://maplibre.org/maplibre-native/android/api/-map-libre%20-native%20-android/org.maplibre.android.offline/-offline-manager/index.html

For downloaded tiles to render later, the app must use the exact same style URL and tile resource URLs
supported by the cache, and cached glyphs/sprites must remain available.
The user must confirm their tile provider allows offline storage; the bundled demo style does **not**
assert any commercial or offline caching license.

MapLibre's cache is different from downloadable PMTiles files. PMTiles (supported natively since Android
SDK 11.7) will be a separate future importer if the user can supply properly licensed regional archives.

Still to validate on devices:
- actual offline map rendering in airplane mode, app restart and device reboot
- download interruptions, storage limits, deletes, style changes and low storage
- network metering changes mid-download (paused/resumed state), battery/thermal usage
- legal tile usage, credits/attributions and symbol fonts

Never treat offline tiles as navigation routing data or verified speed regulations.
