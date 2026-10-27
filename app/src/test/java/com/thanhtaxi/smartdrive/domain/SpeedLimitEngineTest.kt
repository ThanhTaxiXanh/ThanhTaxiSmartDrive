package com.thanhtaxi.smartdrive.domain

import org.junit.Assert.*
import org.junit.Test

class SpeedLimitEngineTest {
    private val engine = SpeedLimitEngine()
    private fun road(zone: PopulationZone = PopulationZone.INSIDE, kind: RoadKind = RoadKind.DIVIDED_OR_MULTI_LANE_ONEWAY) =
        RoadContext("seg-10", "north", 1, zone, kind, matchedConfidently = true, gpsReliable = true)
    @Test fun insidePopulationDefaultsFromVerifiedRoadClassification() {
        assertEquals(60, engine.resolve(road(), emptyList(), 1000).kph)
        assertEquals(50, engine.resolve(road(kind = RoadKind.TWO_WAY_OR_SINGLE_LANE_ONEWAY), emptyList(), 1000).kph)
    }
    @Test fun outsidePopulationDefaults() {
        assertEquals(90, engine.resolve(road(zone = PopulationZone.OUTSIDE), emptyList(), 1000).kph)
        assertEquals(80, engine.resolve(road(PopulationZone.OUTSIDE, RoadKind.TWO_WAY_OR_SINGLE_LANE_ONEWAY), emptyList(), 1000).kph)
    }
    @Test fun verifiedSignAppliesToSegmentAndDirectionOnly() {
        val sign = SpeedEvidence(40, "seg-10", EvidenceSource.VERIFIED_SIGN, direction = "north", reviewed = true)
        assertEquals(40, engine.resolve(road(), listOf(sign), 1000).kph)
        assertEquals(60, engine.resolve(road().copy(direction="south"), listOf(sign), 1000).kph)
        assertEquals(60, engine.resolve(road().copy(segmentId="seg-11"), listOf(sign), 1000).kph)
    }
    @Test fun limitWithLaneAndTimeConstraints() {
        val sign = SpeedEvidence(30, "seg-10", EvidenceSource.VERIFIED_SIGN, lane = 2, validFromEpochMs = 500, validUntilEpochMs = 1500, reviewed = true)
        assertEquals(60, engine.resolve(road(), listOf(sign), 1000).kph)
        assertEquals(30, engine.resolve(road().copy(lane = 2), listOf(sign), 1000).kph)
        assertEquals(60, engine.resolve(road().copy(lane = 2), listOf(sign), 2000).kph)
    }
    @Test fun unknownWhenNoGpsOrPopulationZoneOrMotorway() {
        assertNull(engine.resolve(road().copy(gpsReliable=false), emptyList(), 1000).kph)
        assertNull(engine.resolve(road(PopulationZone.UNKNOWN), emptyList(), 1000).kph)
        assertNull(engine.resolve(road(kind=RoadKind.MOTORWAY), emptyList(), 1000).kph)
    }
    @Test fun conflictingSignsDoNotGuess() {
        val signs = listOf(40, 50).map { SpeedEvidence(it, "seg-10", EvidenceSource.VERIFIED_SIGN, reviewed = true) }
        assertEquals(LimitStatus.CONFLICT, engine.resolve(road(), signs, 1000).status)
        assertNull(engine.resolve(road(), signs, 1000).kph)
    }
    @Test fun unreviewedCameraCannotOverrideVerifiedInformation() {
        val sign = SpeedEvidence(40, "seg-10", EvidenceSource.VALIDATED_CAMERA, confidence = 0.99, reviewed = false)
        assertEquals(60, engine.resolve(road(), listOf(sign), 1000).kph)
    }
}
