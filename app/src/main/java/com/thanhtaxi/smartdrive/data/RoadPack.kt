package com.thanhtaxi.smartdrive.data

import android.content.Context
import com.thanhtaxi.smartdrive.domain.*
import org.json.JSONObject
import java.io.File
import kotlin.math.*

data class Segment(
    val id: String, val startLat: Double, val startLon: Double, val endLat: Double, val endLon: Double,
    val kind: RoadKind, val zone: PopulationZone, val direction: String, val reviewed: Boolean
)
data class RoadPack(
    val title: String, val source: String, val license: String,
    val segments: List<Segment>, val signs: List<SpeedEvidence>,
    val trafficSigns: List<LocatedRoadSign> = emptyList()
)

/**
 * On-device JSON pack, never bundled with invented road limits.
 * A self-reported review field does not constitute official legal verification.
 */
class RoadPackRepository(private val context: Context) {
    private val file get() = File(context.filesDir, "road_pack_v1.json")
    fun load(): RoadPack? = runCatching { if (file.exists()) parse(file.readText()) else null }.getOrNull()
    fun import(json: String): RoadPack {
        val pack = parse(json)
        val temp = File(context.filesDir, "road_pack_v1.tmp")
        temp.writeText(json)
        check(temp.renameTo(file)) { "Không thể thay thế gói dữ liệu cũ" }
        return pack
    }
    fun parse(json: String): RoadPack {
        val root = JSONObject(json)
        require(root.getInt("schema") == 1) { "Phiên bản gói không hỗ trợ" }
        val title = root.getString("title").trim()
        val source = root.getString("source").trim()
        val license = root.getString("license").trim()
        require(title.isNotBlank() && source.isNotBlank() && license.isNotBlank())
        val array = root.getJSONArray("segments")
        require(array.length() <= 100000) { "Gói quá lớn" }
        val segments = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val a = o.getJSONArray("from"); val b = o.getJSONArray("to")
            fun lat(v: Double) = v.also { require(it.isFinite() && it in -90.0..90.0) }
            fun lon(v: Double) = v.also { require(it.isFinite() && it in -180.0..180.0) }
            Segment(o.getString("id"), lat(a.getDouble(0)), lon(a.getDouble(1)),
                lat(b.getDouble(0)), lon(b.getDouble(1)),
                RoadKind.valueOf(o.getString("roadKind")), PopulationZone.valueOf(o.getString("populationZone")),
                o.optString("direction", "forward"), o.optBoolean("reviewed", false))
        }
        require(segments.distinctBy { it.id }.size == segments.size) { "Trùng segment ID" }
        require(segments.all { it.id.isNotBlank() &&
            GeoMath.project(it.startLat, it.startLon,
                RoadLine(it.id,it.startLat,it.startLon,it.endLat,it.endLon,it.reviewed))
                .lengthMeters in 3.0..2500.0 }) { "Đoạn đường không hợp lệ hoặc dài quá 2,5km" }
        val ids = segments.map { it.id }.toSet()
        val arr = root.optJSONArray("speedSigns")
        val signs = if (arr == null) emptyList() else (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("segmentId")
            require(id in ids)
            SpeedEvidence(kph = o.getInt("kph"), segmentId = id,
                source = EvidenceSource.valueOf(o.getString("source")),
                direction = o.optString("direction").takeIf { it.isNotBlank() },
                lane = if (o.has("lane")) o.getInt("lane") else null,
                validFromEpochMs = o.optLong("validFrom", Long.MIN_VALUE),
                validUntilEpochMs = o.optLong("validUntil", Long.MAX_VALUE),
                confidence = o.optDouble("confidence", 1.0), reviewed = o.optBoolean("reviewed", false))
        }
        val roads = segments.associateBy { it.id }
        val trafficArray = root.optJSONArray("trafficSigns")
        require((trafficArray?.length() ?: 0) <= 25000) { "Gói có quá nhiều biển báo" }
        val located = if (trafficArray == null) emptyList() else (0 until trafficArray.length()).map { i ->
            val o = trafficArray.getJSONObject(i)
            val segmentId = o.getString("segmentId")
            val road = requireNotNull(roads[segmentId]) { "Biển phải liên kết với đoạn đường" }
            val signId = o.getString("id").trim()
            require(signId.isNotBlank())
            val code = SignKind.valueOf(o.getString("kind"))
            val lat = o.getDouble("lat")
            val lon = o.getDouble("lon")
            require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0)
            val line = RoadLine(road.id, road.startLat, road.startLon, road.endLat, road.endLon, road.reviewed)
            val projection = GeoMath.project(lat, lon, line)
            require(projection.crossTrackMeters <= 12.0 && projection.rawFraction in 0.0..1.0) {
                "Biển báo nằm ngoài phạm vi đoạn đường"
            }
            val kph = if (code == SignKind.P_127) o.getInt("kph") else null
            require(kph == null || kph in 5..130)
            val lane = if (o.has("lane")) o.getInt("lane").also { require(it > 0) } else null
            val from = o.optLong("validFrom", Long.MIN_VALUE)
            val until = o.optLong("validUntil", Long.MAX_VALUE)
            require(from <= until)
            val confidence = o.optDouble("confidence", 0.0)
            require(confidence.isFinite() && confidence in 0.0..1.0)
            LocatedRoadSign(
                id = signId, segmentId = segmentId, kind = code,
                latitude = lat, longitude = lon, progressMeters = projection.progressMeters,
                kph = kph, direction = o.optString("direction").takeIf { it.isNotBlank() },
                lane = lane,
                vehicle = o.optString("vehicle").takeIf { it.isNotBlank() }?.let { VehicleClass.valueOf(it) },
                validFromMs = from, validUntilMs = until, confidence = confidence,
                reviewed = o.optBoolean("reviewed", false) && road.reviewed
            )
        }
        require(located.distinctBy { it.id }.size == located.size) { "Trùng mã ID biển báo" }
        return RoadPack(title, source, license, segments, signs, located)
    }
}
