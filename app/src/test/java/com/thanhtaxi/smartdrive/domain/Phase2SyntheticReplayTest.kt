package com.thanhtaxi.smartdrive.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * Deterministic, synthetic Phase 2 integration traces. NOT field/device proof.
 * Coordinates are made-up for testing and must never be shipped as road evidence.
 */
class Phase2SyntheticReplayTest {
    private val line=RoadLine("synthetic-east",21.0,106.0,21.0,106.002,reviewed=true)
    private val matcher=GeoMatcher()
    private val index=RoadSpatialIndex(listOf(line))
    private val signs=listOf(
        LocatedRoadSign("entry","synthetic-east",SignKind.R_420,
            21.0,106.00048,50.0,direction="east",reviewed=true,sourceRef="fixture-entry"),
        LocatedRoadSign("p127","synthetic-east",SignKind.P_127,
            21.0,106.00072,75.0,kph=40,direction="east",
            reviewed=true,sourceRef="fixture-P127"),
        LocatedRoadSign("end","synthetic-east",SignKind.END_SPEED,
            21.0,106.00172,180.0,direction="east",reviewed=true,sourceRef="fixture-end")
    )
    private fun matchAt(lon:Double): RoadMatch? = matcher.match(
        GeoFix(21.0,lon,3.0,bearingDegrees=90.0,speedMetersPerSecond=8.0),index)
    private fun context(confirmed: RoadMatch?): RoadContext =
        RoadContext(confirmed?.segmentId,"east",null,PopulationZone.UNKNOWN,
            RoadKind.TWO_WAY_OR_SINGLE_LANE_ONEWAY,
            matchedConfidently=confirmed!=null,gpsReliable=true)

    @Test fun threeStableGpsFixesProduceSurveyAdvisoryNotLegalAlarm() {
        val stabilizer=StableRoadMatcher()
        assertNull(stabilizer.observe(matchAt(106.0009),1000))
        assertNull(stabilizer.observe(matchAt(106.00091),2000))
        val stable=stabilizer.observe(matchAt(106.00092),3000)
        assertNotNull(stable)
        val observed=TrafficSignEngine().evaluate(
            context(stable),stable!!.progressMeters,3.0,signs,10000)
        assertEquals(PopulationZone.INSIDE,observed.zone)
        assertEquals(40,observed.speedEvidence?.kph)
        val result=SpeedLimitEngine().resolve(context(stable).copy(zone=observed.zone),
            listOfNotNull(observed.speedEvidence),10000)
        assertEquals(LimitStatus.SURVEY_REPORTED,result.status)
        val severity=SpeedCompliance().grade(SpeedObservation(65.0,true),result)
        val pip=SpeedPipPolicy.present(result,severity)
        assertEquals(SpeedPipTrust.SURVEY_ONLY,pip.trust)
        assertEquals("40",pip.value)
        assertFalse(pip.soundEligible)
    }

    @Test fun explicitEndOfSpeedZoneLeavesOnlyConditionalEstimate() {
        val stable=matchAt(106.00190)!!
        val context=context(stable)
        val observed=TrafficSignEngine().evaluate(
            context,stable.progressMeters,3.0,signs,10000)
        assertEquals(PopulationZone.INSIDE,observed.zone)
        assertNull(observed.speedEvidence)
        val result=SpeedLimitEngine().resolve(
            context.copy(zone=observed.zone),emptyList(),10000)
        assertEquals(LimitStatus.ESTIMATED_FROM_LAW,result.status)
        assertEquals(50,result.kph)
        assertFalse(SpeedPipPolicy.present(result,SpeedSeverity.SERIOUS).soundEligible)
    }

    @Test fun nearbyParallelStreetMustRemainUnknown() {
        val adjacent=RoadLine("parallel",21.00005,106.0,21.00005,106.002,reviewed=true)
        val ambiguity=GeoMatcher().match(GeoFix(21.000025,106.0009,3.0,90.0,6.0),
            RoadSpatialIndex(listOf(line,adjacent)))
        assertNull(ambiguity)
        val result=SpeedLimitEngine().resolve(context(null),emptyList(),10000)
        assertEquals("--",SpeedPipPolicy.present(result,SpeedSeverity.OVER).value)
    }
}
