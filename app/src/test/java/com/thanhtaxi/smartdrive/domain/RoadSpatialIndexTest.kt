package com.thanhtaxi.smartdrive.domain

import org.junit.Assert.*
import org.junit.Test

class RoadSpatialIndexTest {
    private val roads = listOf(
        RoadLine("north",21.0000,106.0000,21.0100,106.0000,true),
        RoadLine("far",21.5000,106.5000,21.5100,106.5000,true),
        RoadLine("unreviewed",21.0000,106.0001,21.0100,106.0001,false)
    )
    @Test fun returnsLocalSurveyedRoadNotOtherSideOfProvince() {
        val index = RoadSpatialIndex(roads)
        assertEquals(listOf("north"),index.query(21.004,106.00002).map { it.id })
    }
    @Test fun nearestMatchAgreesWithFullScanAcrossCellBoundary() {
        val fix = GeoFix(21.00399,106.00002,4.0,0.0,8.0)
        assertEquals(GeoMatcher().match(fix,roads),GeoMatcher().match(fix,RoadSpatialIndex(roads)))
    }
    @Test fun invalidCoordinateAndUnreviewedSegmentsRejected() {
        val index = RoadSpatialIndex(roads)
        assertTrue(index.query(99.0,106.0).isEmpty())
        assertFalse(index.query(21.004,106.0).any { it.id=="unreviewed" })
    }
}
