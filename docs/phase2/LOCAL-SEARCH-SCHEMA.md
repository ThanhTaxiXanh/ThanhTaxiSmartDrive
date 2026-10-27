# Local surveyed point lookup — not a Vietnam address geocoder

There is currently **no licensed complete Vietnamese POI/address dataset** in this repository. The user can opt to import a permission-cleared **road pack** that already has `routing.nodes[]`, and each node may optionally contain a short human-readable `displayName`:

```json
{
  "id": "survey-node-001",
  "lat": 21.0001,
  "lon": 106.0002,
  "displayName": "Điểm khảo sát thử nghiệm"
}
```

**The coordinate/name above is fictitious**, not a real station or official road. Place it inside a complete schema-v1 road pack with source/license, linked surveyed segments, reviewed directed edges/turns and sourceRef where required. It is not independently verified as address or business data.

The Android importer and Python `survey_routing_qa.py` limit displayName length (≤100 characters) and reject control characters. Search matches an exact node ID; exact unique displayName; selected `Tên (node ID)` suggestion; or latitude,longitude close to a unique node (default ≤75 m). Duplicated names, nearly tied nodes, invalid/out-of-bounds WGS84 and distant points yield an explicit failure. Names do not influence speed rules.

All input labels and geographical data remain self-declared, subject to the road-pack license. No remote geocoding call, hidden fallback or provider-key sharing is performed.

## Future licensed address search gate

Phase 2 requires a separate provider/license review for **index scope, query API, storage/redistribution, data update frequency, attribution, API quotas and privacy terms**. Candidate sources should be evaluated in an isolated spike before connecting network searches or shipping national POI/house-number data. No provider has been integrated or approved here.

Only map-search implementation with actual coverage, attribution, accuracy testing and stable destination snapping can count toward full Phase 2 acceptance.
