package com.thanhtaxi.smartdrive.offline

import org.junit.Assert.*
import org.junit.Test

class RoadLabelPlacementTest {
    @Test fun suppressesDuplicatesAndCrowding() {
        val src=listOf(
            RoadLabelCandidate("Trần Phú",150f,80f),
            RoadLabelCandidate("Trần Phú",310f,80f),
            RoadLabelCandidate("Lê Lợi",160f,83f),
            RoadLabelCandidate("Nguyễn Văn Cừ",370f,190f)
        )
        val out=RoadLabelPlacement.choose(src,500,350)
        assertEquals(listOf("Trần Phú","Nguyễn Văn Cừ"),out.map { it.text })
    }
    @Test fun respectsBoundsAndMaxNumber() {
        val src=(0..20).map { RoadLabelCandidate("Road$it",100f+it*140f,200f) }
        assertEquals(3,RoadLabelPlacement.choose(src,600,350,3).size)
        assertTrue(RoadLabelPlacement.choose(src,0,350).isEmpty())
    }
    @Test fun rejectsInvalidCoordinatesAndLongTitles() {
        val long="Tên đường rất dài ".repeat(10)
        val c=RoadLabelPlacement.choose(listOf(
            RoadLabelCandidate("ignored",Float.NaN,50f),
            RoadLabelCandidate(long,200f,80f)),500,250)
        assertEquals(1,c.size)
        assertTrue(c[0].text.length<=28)
    }
}
