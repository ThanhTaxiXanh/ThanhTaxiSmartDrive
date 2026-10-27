package com.thanhtaxi.smartdrive.domain
import org.junit.Assert.*
import org.junit.Test

class StableRoadMatcherTest {
    private fun m(id:String="A",position:Double=30.0) = RoadMatch(id,position,2.0,200.0)
    @Test fun mustObserveThreeUniqueGpsFixes() {
        val stable=StableRoadMatcher()
        assertNull(stable.observe(m(),1000))
        assertTrue(stable.isConfirming)
        assertNull(stable.observe(m(),1000)) // UI duplicate cannot advance confidence
        assertNull(stable.observe(m(position=35.0),2000))
        assertEquals("A",stable.observe(m(position=40.0),3000)?.segmentId)
    }
    @Test fun parallelRoadSwitchIsUnknownUntilThreeSamples() {
        val s=StableRoadMatcher()
        s.observe(m(),1000);s.observe(m(),2000);assertNotNull(s.observe(m(),3000))
        assertNull(s.observe(m("parallel"),4000))
        assertNull(s.observe(m("parallel"),5000))
        assertEquals("parallel",s.observe(m("parallel"),6000)?.segmentId)
    }
    @Test fun lossOrLongGapRevokesEvidence() {
        val s=StableRoadMatcher()
        s.observe(m(),1000);s.observe(m(),2000);assertNotNull(s.observe(m(),3000))
        assertNull(s.observe(null,4000))
        assertNull(s.observe(m(),5000))
        assertNull(s.observe(m(),6000))
        assertNotNull(s.observe(m(),7000))
        assertNull(s.observe(m(),20000))
    }
    @Test fun teleportRevokesEvidence() {
        val s=StableRoadMatcher()
        s.observe(m(),1000);s.observe(m(),2000);assertNotNull(s.observe(m(),3000))
        assertNull(s.observe(m(position=190.0),4000))
        assertTrue(s.isConfirming)
    }
    @Test fun malformedFixRejected() {
        val s=StableRoadMatcher()
        assertNull(s.observe(m(position=Double.NaN),1000))
        assertNull(s.observe(m(position=300.0),2000))
    }
}
