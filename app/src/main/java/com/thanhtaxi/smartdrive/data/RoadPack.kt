package com.thanhtaxi.smartdrive.data

import android.content.Context
import android.location.Location
import com.thanhtaxi.smartdrive.domain.*
import org.json.JSONObject
import java.io.File
import kotlin.math.*

data class Segment(
    val id: String, val startLat: Double, val startLon: Double, val endLat: Double, val endLon: Double,
    val kind: RoadKind, val zone: PopulationZone, val direction: String, val reviewed: Boolean
)
data class RoadPack(val title: String, val source: String, val license: String, val segments: List<Segment>, val signs: List<SpeedEvidence>)

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
        return RoadPack(title, source, license, segments, signs)
    }
}

/** Geometry + course selection with ambiguity rejection; not a full routing/map-matching engine. */
class RoadMatcher {
    fun match(location: Location, segments: List<Segment>): Segment? {
        if (!location.hasAccuracy() || location.accuracy > 20f) return null
        val candidates = segments.asSequence().filter { it.reviewed }.mapNotNull { seg ->
            val bearing = initialBearing(seg.startLat, seg.startLon, seg.endLat, seg.endLon)
            if (location.hasBearing() && angleDelta(location.bearing.toDouble(), bearing) > 55.0) return@mapNotNull null
            val metres = pointToSegmentMetres(location.latitude, location.longitude, seg)
            if (metres > min(18.0, location.accuracy + 9.0)) null else seg to metres
        }.sortedBy { it.second }.take(2).toList()
        if (candidates.isEmpty()) return null
        if (candidates.size > 1 && candidates[1].second - candidates[0].second < 10.0) return null
        return candidates[0].first
    }
    private fun angleDelta(a: Double, b: Double) = abs((a - b + 540) % 360 - 180)
    private fun initialBearing(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val lat1 = Math.toRadians(aLat); val lat2 = Math.toRadians(bLat)
        val dLon = Math.toRadians(bLon - aLon)
        return (Math.toDegrees(atan2(sin(dLon) * cos(lat2),
            cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon))) + 360) % 360
    }
    private fun pointToSegmentMetres(lat: Double, lon: Double, s: Segment): Double {
        val scale = cos(Math.toRadians(lat))
        val ax = (s.startLon - lon) * 111320.0 * scale
        val ay = (s.startLat - lat) * 111320.0
        val bx = (s.endLon - lon) * 111320.0 * scale
        val by = (s.endLat - lat) * 111320.0
        val dx = bx - ax; val dy = by - ay
        val t = if (dx * dx + dy * dy < 0.001) 0.0 else (-ax * dx - ay * dy) / (dx * dx + dy * dy)
        val u = t.coerceIn(0.0, 1.0)
        return hypot(ax + u * dx, ay + u * dy)
    }
}
