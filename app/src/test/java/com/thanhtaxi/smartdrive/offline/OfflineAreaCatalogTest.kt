package com.thanhtaxi.smartdrive.offline
import org.junit.Assert.*
import org.junit.Test

class OfflineAreaCatalogTest {
    @Test fun pilotsAreSmall() {
        assertEquals(2,OfflineAreaCatalog.PILOT.size)
        assertTrue(OfflineAreaCatalog.PILOT.all { it.north-it.south < 0.05 && it.east-it.west < 0.05 })
    }
    @Test fun rejectInvertedBounds() {
        try { OfflineArea("bad","bad",21.0,106.0,20.0,106.5); fail() }
        catch (_: IllegalArgumentException) {}
    }
    @Test fun httpsStyleRequired() {
        assertTrue(OfflineAreaCatalog.validStyleUri("https://example.org/style.json"))
        assertFalse(OfflineAreaCatalog.validStyleUri("http://example.org/style.json"))
        assertFalse(OfflineAreaCatalog.validStyleUri("https://user@example.org/style.json"))
    }
}
