package com.thanhtaxi.smartdrive.offline

import org.junit.Assert.*
import org.junit.Test

class RoadFeatureSummaryTest {
    @Test fun prefersVietnameseNameWhenAvailable() {
        assertEquals("Tên đường trong dữ liệu OSM: Nguyễn Văn Cừ",
            RoadFeatureSummary.bestDisplay(mapOf("name" to "Road A",
                "name:vi" to "Nguyễn Văn Cừ","ref" to "Q1")))
    }
    @Test fun usesReferenceOnlyAsReference() {
        assertEquals("Mã tuyến trong dữ liệu OSM: QL1",
            RoadFeatureSummary.bestDisplay(mapOf("ref" to "QL1")))
    }
    @Test fun neverShowsOsmMaxspeedAsLegalLimit() {
        assertEquals("Đoạn đường không có thuộc tính tên/mã tuyến trong tile.",
            RoadFeatureSummary.bestDisplay(mapOf("maxspeed" to "80")))
    }
    @Test fun trimsAndLimitsUntrustedText() {
        val result=RoadFeatureSummary.bestDisplay(mapOf("name" to "AB\nCD"))
        assertFalse(result.contains("\n"))
        assertTrue(RoadFeatureSummary.bestDisplay(mapOf("name" to "x".repeat(200))).length < 130)
    }
}
