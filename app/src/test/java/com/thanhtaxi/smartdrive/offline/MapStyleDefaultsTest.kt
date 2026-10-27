package com.thanhtaxi.smartdrive.offline

import org.junit.Assert.*
import org.junit.Test

class MapStyleDefaultsTest {
    @Test fun legacyDemoAutomaticallyMigratesToStreetMap() {
        assertEquals(MapStyleDefaults.STREET_STYLE, MapStyleDefaults.effective(null))
        assertEquals(MapStyleDefaults.STREET_STYLE,
            MapStyleDefaults.effective("https://demotiles.maplibre.org/style.json"))
    }
    @Test fun userSelectedProviderIsPreserved() {
        val url="https://tiles.example.org/styles/custom.json"
        assertEquals(url, MapStyleDefaults.effective(url))
    }
}
