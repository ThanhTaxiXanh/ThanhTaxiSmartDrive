package com.thanhtaxi.smartdrive.offline

import org.junit.Assert.*
import org.junit.Test

class DashboardMapSelectionTest {
    @Test fun respectsPilotGeographicExtent() {
        val area=PmtilesHeader(2,10,15,21.20,106.1,21.30,106.3,21.25,106.2,12)
        assertTrue(DashboardMapSelection.inside(area,21.25,106.2))
        assertFalse(DashboardMapSelection.inside(area,21.25,105.8))
        assertFalse(DashboardMapSelection.inside(area,21.6,106.2))
    }
    @Test fun clampsGpsZoomToArchiveRange() {
        val area=PmtilesHeader(2,10,15,21.2,106.1,21.3,106.3,21.25,106.2,12)
        assertEquals(15.0,DashboardMapSelection.zoom(area,17.0),0.0001)
        assertEquals(10.0,DashboardMapSelection.zoom(area,8.0),0.0001)
    }
}
