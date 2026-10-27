package com.thanhtaxi.smartdrive.navigation

import org.junit.Assert.*
import org.junit.Test

class EtaGuidanceTest {
    @Test fun requiresTwoValidMovingFixesAndNeverUsesUnknownOrExtremeSpeed() {
        val e=SimpleEtaEstimator()
        assertNull(e.estimate(1200.0,8.0))
        assertEquals(150L,e.estimate(1200.0,8.0))
        assertNull(e.estimate(1000.0,null))
        assertNull(e.estimate(1000.0,0.0))
        assertNull(e.estimate(1000.0,60.0))
        assertNull(e.estimate(Double.NaN,9.0))
        assertNull(e.estimate(-1.0,9.0))
        assertNull(e.estimate(1000.0,9.0)) // reset requires two samples
    }

    @Test fun usesMedianSpeedToDampenSingleSuddenSpike() {
        val e=SimpleEtaEstimator()
        e.estimate(1000.0,10.0)
        e.estimate(1000.0,10.0)
        e.estimate(1000.0,30.0)
        val result=e.estimate(1000.0,10.0)
        assertEquals(100L,result)
    }

    @Test fun voiceIsOffForUncertainMissingEtaOrRepeatedThreshold() {
        val p=GuidancePromptPolicy()
        val snapshot=NavigationUpdate(
            phase=NavigationPhase.ACTIVE,message="test",
            remainingMeters=500.0,nextManeuver="Hình học đổi hướng sang trái",
            nextManeuverMeters=120.0,etaSeconds=80,
            nextManeuverIndex=0,totalManeuvers=2
        )
        assertNotNull(p.next(snapshot,"abc"))
        assertNull(p.next(snapshot,"abc"))
        assertNotNull(p.next(snapshot.copy(nextManeuverMeters=25.0),"abc"))
        assertNull(p.next(snapshot.copy(nextManeuverMeters=25.0),"abc"))
        assertNull(p.next(snapshot.copy(phase=NavigationPhase.GPS_UNCERTAIN),"abc"))
        assertNull(p.next(snapshot.copy(etaSeconds=null,nextManeuverIndex=1),"abc"))
        assertNotNull(p.next(snapshot.copy(nextManeuverIndex=1),"abc"))
        assertNotNull(p.next(snapshot,"different-route"))
    }

    @Test fun noVoiceForDistantGeometryOrNoActualManeuver() {
        val p=GuidancePromptPolicy()
        val s=NavigationUpdate(NavigationPhase.ACTIVE,"",
            nextManeuver="Hình học cong",nextManeuverMeters=200.0,
            etaSeconds=45,nextManeuverIndex=0)
        assertNull(p.next(s,"route"))
        assertNull(p.next(s.copy(nextManeuverMeters=80.0,nextManeuverIndex=null),"route"))
    }
}
