package com.thanhtaxi.smartdrive.offline

/**
 * Road names are display-only attributes observed in rendered OSM features.
 * Never treat "speed", "maxspeed", names, road refs or routing tags as legal proof.
 */
object RoadFeatureSummary {
    private val priority = listOf("name:vi","name","official_name","ref","int_ref","name_en")
    fun bestDisplay(properties: Map<String,String?>): String {
        for(key in priority) {
            val v = properties[key]?.replace(Regex("[\\p{Cntrl}]")," ")?.trim()
            if(!v.isNullOrBlank()) {
                val short = if(v.length>90) v.take(90)+"…" else v
                return when(key) {
                    "name:vi", "name", "official_name", "name_en" -> "Tên đường trong dữ liệu OSM: $short"
                    else -> "Mã tuyến trong dữ liệu OSM: $short"
                }
            }
        }
        return "Đoạn đường không có thuộc tính tên/mã tuyến trong tile."
    }
}
