# Importing source-attributed road packs

The app intentionally ships **without nationwide speed signs**. No road limit is legally verified by the application.

## Example schema (illustrative, NOT validated road data)
```json
{
  "schema": 1,
  "title": "Sample only — do not use while driving",
  "source": "USER-SUPPLIED-SURVEY",
  "license": "owner permission required",
  "segments": [
    {
      "id": "example-only",
      "from": [21.0, 106.0],
      "to": [21.0002, 106.0002],
      "roadKind": "TWO_WAY_OR_SINGLE_LANE_ONEWAY",
      "populationZone": "UNKNOWN",
      "direction": "forward",
      "reviewed": false
    }
  ],
  "speedSigns": []
}
```
A segment must have provenance, reviewed=true, a reliable heading match and accurate GPS before rule evaluation.
An imported flag alone is **not a government verification**. The app has no way to independently validate an imported pack. Use reviewed values only for engineering/field trials, and cross-check signs in person.

Accepted roadKind: DIVIDED_OR_MULTI_LANE_ONEWAY, TWO_WAY_OR_SINGLE_LANE_ONEWAY, MOTORWAY, UNKNOWN.
Population: INSIDE, OUTSIDE, UNKNOWN. `speedSigns` entries contain `segmentId`, `kph`,
`source` (VERIFIED_SIGN, VALIDATED_CAMERA, VERIFIED_MAP, UNVERIFIED_MAP), `reviewed`,
and optional `direction`, `lane`, `validFrom`, `validUntil` (Unix milliseconds), `confidence`.

A proper production pipeline must spatially join survey data, deduplicate signs, validate lane/direction/effective periods and cryptographically sign packs.
