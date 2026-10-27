package com.thanhtaxi.smartdrive.offline

data class OfflineArea(val id: String, val name: String, val south: Double, val west: Double,
                       val north: Double, val east: Double) {
    init {
        require(south in -90.0..90.0 && north in -90.0..90.0 && west in -180.0..180.0 && east in -180.0..180.0)
        require(south < north && west < east && north - south <= 0.10 && east - west <= 0.10)
    }
}
/** Pilot bounding boxes around town centres: NOT all of either province. */
object OfflineAreaCatalog {
    val PILOT = listOf(
        OfflineArea("bac-giang-center", "Trung tâm Bắc Giang (vùng thử nghiệm)", 21.246667, 106.18, 21.286667, 106.22),
        OfflineArea("bac-ninh-center", "Trung tâm Bắc Ninh (vùng thử nghiệm)", 21.163333, 106.03, 21.203333, 106.07)
    )
    const val MIN_ZOOM = 11.0
    const val MAX_ZOOM = 15.0
    fun validStyleUri(uri: String): Boolean =
        uri.startsWith("https://") && !uri.contains(' ') && !uri.contains('@') && uri.length in 12..1024
}
