package com.thanhtaxi.smartdrive.domain

import org.junit.Assert.*
import org.junit.Test

class TrafficSignEngineTest {
    private val context = RoadContext("road", "north", null, PopulationZone.UNKNOWN,
        RoadKind.TWO_WAY_OR_SINGLE_LANE_ONEWAY, matchedConfidently = true, gpsReliable = true)
    private fun sign(id: String, kind: SignKind, metre: Double, limit: Int? = null) =
        LocatedRoadSign(id, "road", kind, 21.0, 106.0, metre, kph = limit,
            direction = "north", reviewed = true,sourceRef="test-field-survey")
    private val engine = TrafficSignEngine()
    @Test fun r420R421OnlyAfterPassedNotNearby() {
        val signs = listOf(sign("in",SignKind.R_420,100.0), sign("out",SignKind.R_421,300.0))
        assertEquals(PopulationZone.UNKNOWN,engine.evaluate(context,80.0,5.0,signs,1000).zone)
        assertEquals(PopulationZone.INSIDE,engine.evaluate(context,150.0,5.0,signs,1000).zone)
        assertEquals(PopulationZone.OUTSIDE,engine.evaluate(context,350.0,5.0,signs,1000).zone)
    }
    @Test fun p127AndExplicitEndOnlyForCurrentSegment() {
        val signs = listOf(sign("limit",SignKind.P_127,100.0,40),sign("end",SignKind.END_SPEED,300.0))
        assertNull(engine.evaluate(context,90.0,4.0,signs,1000).speedEvidence)
        assertEquals(40,engine.evaluate(context,140.0,4.0,signs,1000).speedEvidence?.kph)
        assertNull(engine.evaluate(context,340.0,4.0,signs,1000).speedEvidence)
        assertNull(engine.evaluate(context.copy(segmentId="another"),140.0,4.0,signs,1000).speedEvidence)
    }
    @Test fun unreviewedAndWrongLaneAreExcluded() {
        val candidate = sign("limit", SignKind.P_127,100.0,40)
        assertNull(engine.evaluate(context,200.0,4.0,listOf(candidate.copy(reviewed=false)),1000).speedEvidence)
        assertNull(engine.evaluate(context,200.0,4.0,listOf(candidate.copy(lane=2)),1000).speedEvidence)
    }
    @Test fun conflictingAtSameLocationRejectsLimit() {
        val signs = listOf(sign("A", SignKind.P_127,100.0,40), sign("B", SignKind.P_127,102.0,50))
        val result = engine.evaluate(context,200.0,4.0,signs,1000)
        assertTrue(result.conflict)
        assertNull(result.speedEvidence)
    }
    @Test fun unreferencedAndCorruptSignCoordinatesAreNotTrusted() {
        val sample=sign("P",SignKind.P_127,100.0,40)
        assertNull(engine.evaluate(context,200.0,4.0,
            listOf(sample.copy(sourceRef=null)),1000).speedEvidence)
        assertNull(engine.evaluate(context,200.0,4.0,
            listOf(sample.copy(progressMeters=Double.NaN)),1000).speedEvidence)
        assertNull(engine.evaluate(context,200.0,4.0,
            listOf(sample.copy(progressMeters=-30.0)),1000).speedEvidence)
    }
    @Test fun accuracyAndLaneAmbiguityMustFailClosed() {
        val sample=sign("P",SignKind.P_127,100.0,40)
        assertNull(engine.evaluate(context,200.0,Double.NaN,listOf(sample),1000).speedEvidence)
        assertNull(engine.evaluate(context,200.0,-1.0,listOf(sample),1000).speedEvidence)
        assertNull(engine.evaluate(context.copy(gpsReliable=false),200.0,4.0,listOf(sample),1000).speedEvidence)
    }
    @Test fun directionVehicleAndTimeApplicability() {
        val sample=sign("P",SignKind.P_127,100.0,40)
            .copy(vehicle=VehicleClass.TAXI,validFromMs=1000,validUntilMs=1500)
        assertEquals(40,engine.evaluate(context,200.0,4.0,listOf(sample),1200).speedEvidence?.kph)
        assertNull(engine.evaluate(context.copy(direction="south"),200.0,4.0,listOf(sample),1200).speedEvidence)
        assertNull(engine.evaluate(context.copy(vehicle=VehicleClass.LARGE_BUS),200.0,4.0,listOf(sample),1200).speedEvidence)
        assertNull(engine.evaluate(context,200.0,4.0,listOf(sample),1800).speedEvidence)
    }
    @Test fun upcomingOnlyTrustedAhead() {
        val signs = listOf(sign("A",SignKind.P_127,250.0,50), sign("B",SignKind.R_421,1200.0))
        val result = engine.evaluate(context,100.0,4.0,signs,1000)
        assertEquals(1,result.upcoming.size)
        assertEquals(150,result.upcoming[0].metres)
    }
}
