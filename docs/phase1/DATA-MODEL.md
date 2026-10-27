# Data model baseline — Phase 1

This document separates **current stored shapes** from **target contracts**. Do not infer a field is already implemented merely because it is documented.

## 1. Common evidence envelope (target contract)

```text
EvidenceRecord {
  recordId: string; schemaVersion: int; datasetSeries: string;
  publisherId: string?; sourceRef: string; sourceKind: AUTHORITY|LICENSED|
    FIELD_SURVEY|OSM|USER_REPORT|CAMERA|DERIVED;
  collectedAtUtc: instant?; reviewedAtUtc: instant?;
  effectiveFromUtc: instant?; effectiveUntilUtc: instant?;
  reviewerId: string?; verifiedStatus: UNVERIFIED|SURVEY_REVIEWED|
    AUTHORITY_CHECKED|REVOKED;
  confidence: float?; signatureRef: string?;
  licenseId: string?; attribution: string?;
}
```

SourceRef in the existing JSON is a provenance *pointer*, not a signature. The current `reviewed` Boolean is user-controlled and **must not stand alone as legal certification**.

## 2. Location and road matching

```text
LocationFix {
  latitude, longitude: WGS84 double;
  elapsedRealtimeNanos: long; accuracyMeters: float?;
  gpsSpeedMetersPerSecond: float?; headingDegrees: float?;
  timestampUtc: instant?; source: GPS|FUSED; permissionState;
}
RoadSegment {
  segmentId; directedGeometry[]; roadClass; populationZone;
  laneCount?; direction; laneApplicability?;
  vehicleApplicability?; signRefs[];
  bridgeTunnelLevel?; accessRestrictions[];
  validFrom?; validUntil?; evidence: EvidenceRecord;
}
RoadMatch {
  segmentId?; progressMeters?; perpendicularDistanceMeters?;
  headingConsistency?; confidenceState:
    UNKNOWN|CANDIDATE|STABLE|AMBIGUOUS|REJECTED;
}
```

**Current code:** `data/RoadPack.kt`, `domain/GeoMatching.kt`, `StableRoadMatcher.kt`, `RoadTopologyGate.kt`. GPS reads currently use Android `LocationManager`; semantic road restrictions and multilevel matching are not fully resolved.

## 3. Signs, legal applicability and speed output

```text
TrafficSign {
  signId; signCode: P127|R420|R421|...; geometry;
  direction; segmentId?; laneIds?; vehicleClasses?;
  effectiveInterval?; auxiliaryPlateConditions?;
  restrictions?; state: ACTIVE|ENDED|UNKNOWN;
  evidence: EvidenceRecord;
}
SpeedEvidence {
  segmentId; kph?; source; applicabilityConditions;
  effectiveInterval?; conflictRefs[]; verification;
}
SpeedLimitResult {
  kph?: int; status: VERIFIED|ESTIMATED|CONFLICT|UNKNOWN;
  basis[]; reason; resolvedAtUtc;
}
SpeedCompliance {
  gpsKph?; limitResult; alert: NORMAL|NEAR|OVER|SERIOUS|UNKNOWN;
  userWarningToleranceKph;
}
```

Legal use-case must *also* consider governing rules currently in force, road class/median, vehicle type, lane, time, termination signs and sign plates. A UI setting can adjust **alert thresholds**, not the legal limit. A camera's inference is candidate evidence, not absolute precedence. If conditions cannot be resolved, return UNKNOWN.

**Current status:** Android prototype resolves conservative subsets; complete camera, lane/time and authoritative source audits pending.

## 4. Directed topology and route

**Currently imported JSON structure (schema 1):**

```json
{
  "schema": 1,
  "series": "survey-series-id",
  "version": 1,
  "title": "Licensed surveyed dataset",
  "source": "Publisher + provenance manifest",
  "license": "License identifier",
  "segments": [],
  "speedSigns": [],
  "trafficSigns": [],
  "routing": {
    "nodes": [
      {"id": "A", "lat": 21.00000, "lon": 106.00000}
    ],
    "edges": [
      {
        "id": "AB",
        "fromNode": "A", "toNode": "B",
        "segmentId": "segment-ab",
        "direction": "forward",
        "distanceMeters": 111.2,
        "reviewed": false,
        "sourceRef": "field-survey-photo-index"
      }
    ],
    "allowedTurns": [
      {
        "fromEdge": "AB", "viaNode": "B", "toEdge": "BC",
        "reviewed": false,
        "sourceRef": "junction-survey-id"
      }
    ]
  }
}
```

Example is deliberately **not a complete valid graph**: the sample references nodes and edges not listed, so it cannot be used as a working road package. See `docs/ROUTING.md` for validation rules and executable unit tests.

- RoadNodes are explicit geographic junctions; crossing lines do **not** imply a junction.
- RouteEdges are directed and reference an existing surveyed segment.
- AllowedTurns are edge-pair, direction-specific and independently surveyed.
- A parked route preview is not a live navigation engine.

## 5. Offline map asset model

```text
OfflineMapPackage {
  archiveId = SHA256(binary);
  fileUri = internal app-private path;
  fileBytes; pmtilesVersion; tileType; minZoom; maxZoom;
  boundsWGS84; centerWGS84; vectorSourceLayers[];
  sourcePublisher; userDeclaredLicense; attribution;
  integrity = VERIFIED_SHA|CORRUPT;
  publisherAuthenticity = UNKNOWN until signature verification;
  renderTrial = NOT_TESTED|USER_REPORTED|DEVICE_VERIFIED;
}
```

Current parser accepts PMTiles v3 imports, verifies bounds and SHA, extracts vector layers with compression constraints, and lets Android render with MapLibre. A valid SHA compared with local metadata **does not independently authenticate origin**. PMTiles tiles and routing graph remain independent data products.

## 6. Phone settings, dashboard, QR and calendar

Target `DashboardLayout`: `presetId`, `screenProfile` 16:9/16:10/21:9/custom, `mapWidthRatio=0.70`, `rightWidgetsHeightRatio=[0.25,0.55,0.20]`, `visibility`, `locked`, `theme`, `minimumTapDp`, `updatedAt`, `revision`.

Target `PaymentProfile`: `profileId`, `displayName`, `bankLabel?`, `imageUri`, `isDefault`, `verifiedPaid=false`. No banking credentials or automatic payment claims.

Target `CalendarEvent`: `eventId`, `title`, `calendarSystem=SOLAR|LUNAR`, `date`, `leapMonthFlag?`, `timezone=Asia/Ho_Chi_Minh`, `recurrence`, `reminder`. Show legal public holidays separately from user events.

Target `MediaNowPlaying`: `appPackage`, `sessionId`, `title`, `duration`, `playbackState`, `actionsAllowed`. Reflect only authorized MediaSessions.

Target `SettingsRevision`: `revision`, `deviceId?`, `updatedAt`, `sourceDevice`, `conflictPolicy`. Remote phone/head-unit sync remains a future dependency.

## 7. Lifecycle and state boundaries

- Private local data: `Context.filesDir`; QR selected through Android Storage Access Framework.
- No cloud GPS tracking, image upload, telemetry or licensing bypass by default.
- Planned secure data pack: publisher public key pinned to app → digital signature verifies signed manifest/content → version/revocation → QA metadata; provenance must survive offline use.
- Road surveys may contain identifiable imagery; keep source references and controlled access distinct from public downloadable packs.

See `docs/ROAD-PACK.md`, `docs/ROUTING.md`, `docs/PMTILES-DESIGN.md`, `docs/SURVEY-PIPELINE.md`.
