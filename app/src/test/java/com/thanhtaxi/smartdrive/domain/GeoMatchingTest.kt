package com.thanhtaxi.smartdrive.domain

import org.junit.Assert.*
import org.junit.Test

class GeoMatchingTest {
    private val main = RoadLine("road", 21.0000, 106.0000, 21.0010, 106.0000, true)
    @Test fun matchingGoodGps() {
        val result = GeoMatcher().match(GeoFix(21.0005, 106.00002, 5.0, 0.0, 10.0), listOf(main))
        assertEquals("road", result?.segmentId)
        assertTrue(result!!.progressMeters in 50.0..62.0)
    }
    @Test fun rejectOppositeDirection() {
        assertNull(GeoMatcher().match(GeoFix(21.0005, 106.00001, 5.0, 180.0, 12.0), listOf(main)))
    }
    @Test fun rejectParallelRoadAmbiguity() {
        val other = main.copy(id = "parallel", aLon = 106.00005, bLon = 106.00005)
        assertNull(GeoMatcher().match(GeoFix(21.0005, 106.00002, 6.0, 0.0, 10.0), listOf(main, other)))
    }
    @Test fun rejectBadOrMissingSurveyEvidence() {
        assertNull(GeoMatcher().match(GeoFix(21.0005, 106.0, 24.0), listOf(main)))
        assertNull(GeoMatcher().match(GeoFix(21.0005, 106.0, 5.0), listOf(main.copy(reviewed = false))))
    }
}
