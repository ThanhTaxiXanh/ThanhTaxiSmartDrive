package com.thanhtaxi.smartdrive.offline

/** Camera may follow GPS only within the imported PMTiles geographic bounds. */
object DashboardMapSelection {
    fun inside(header: PmtilesHeader,latitude: Double,longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in header.minLat..header.maxLat && longitude in header.minLon..header.maxLon
    fun zoom(header: PmtilesHeader,preferred: Double): Double =
        preferred.coerceIn(header.minZoom.toDouble(),header.maxZoom.toDouble())
}
