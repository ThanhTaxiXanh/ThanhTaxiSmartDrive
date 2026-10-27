package com.thanhtaxi.smartdrive.domain

import kotlin.math.*

data class GeoFix(val latitude: Double, val longitude: Double, val accuracyMeters: Double,
                  val bearingDegrees: Double? = null, val speedMetersPerSecond: Double? = null)
data class RoadLine(val id: String, val aLat: Double, val aLon: Double, val bLat: Double, val bLon: Double,
                    val reviewed: Boolean = false)
data class RoadProjection(val progressMeters: Double, val crossTrackMeters: Double, val lengthMeters: Double,
                          val rawFraction: Double)
data class RoadMatch(val segmentId: String, val progressMeters: Double, val distanceMeters: Double,
                     val lengthMeters: Double)

/** Short segment local tangent projection, not a navigation grade 3D map matcher. */
object GeoMath {
    fun project(lat: Double, lon: Double, road: RoadLine): RoadProjection {
        val latRad = Math.toRadians((road.aLat + road.bLat + lat) / 3.0)
        val kx = 111_320.0 * cos(latRad)
        val ay = (road.aLat - lat) * 111_195.0
        val ax = (road.aLon - lon) * kx
        val by = (road.bLat - lat) * 111_195.0
        val bx = (road.bLon - lon) * kx
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 < 0.001) return RoadProjection(0.0, hypot(ax, ay), 0.0, 0.0)
        val t = -(ax * dx + ay * dy) / len2
        val clipped = t.coerceIn(0.0, 1.0)
        return RoadProjection(clipped * sqrt(len2), hypot(ax + clipped * dx, ay + clipped * dy), sqrt(len2), t)
    }
    fun bearing(road: RoadLine): Double {
        val a = Math.toRadians(road.aLat); val b = Math.toRadians(road.bLat)
        val d = Math.toRadians(road.bLon - road.aLon)
        return (Math.toDegrees(atan2(sin(d) * cos(b),
            cos(a) * sin(b) - sin(a) * cos(b) * cos(d))) + 360.0) % 360.0
    }
    fun differenceDegrees(a: Double, b: Double): Double = abs((a - b + 540.0) % 360.0 - 180.0)
}

/**
 * Rejects parallel-road / crossing-road ties rather than guessing. A road pack
 * must contain directional, topologically split, surveyed segments.
 */
/** Immutable grid index for short validated road segments; avoids scanning nationwide packs every GPS tick. */
class RoadSpatialIndex(roads: List<RoadLine>, private val cellSizeDegrees: Double = 0.002) {
    private val grid: Map<Pair<Int,Int>, List<RoadLine>>
    init {
        require(cellSizeDegrees > 0 && cellSizeDegrees <= 0.01)
        val cells = mutableMapOf<Pair<Int,Int>, MutableList<RoadLine>>()
        for (r in roads) {
            if (!r.reviewed) continue
            val yMin = cell(min(r.aLat,r.bLat)); val yMax = cell(max(r.aLat,r.bLat))
            val xMin = cell(min(r.aLon,r.bLon)); val xMax = cell(max(r.aLon,r.bLon))
            // Refuse pathological or unvalidated country-spanning polylines.
            if ((yMax-yMin+1)*(xMax-xMin+1) > 500) continue
            for (y in yMin..yMax) for (x in xMin..xMax) {
                cells.getOrPut(y to x) { mutableListOf() }.add(r)
            }
        }
        grid = cells
    }
    private fun cell(degrees: Double): Int = floor(degrees / cellSizeDegrees).toInt()
    fun query(lat: Double, lon: Double): List<RoadLine> {
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || !lat.isFinite() || !lon.isFinite()) return emptyList()
        val y=cell(lat);val x=cell(lon)
        return (-1..1).asSequence().flatMap { dy ->
            (-1..1).asSequence().flatMap { dx ->
                grid[y+dy to x+dx].orEmpty().asSequence()
            }
        }.distinctBy { it.id }.toList()
    }
}

class GeoMatcher {
    fun match(fix: GeoFix, index: RoadSpatialIndex): RoadMatch? =
        match(fix, index.query(fix.latitude, fix.longitude))

    fun match(fix: GeoFix, segments: List<RoadLine>): RoadMatch? {
        if (!fix.latitude.isFinite() || !fix.longitude.isFinite() ||
            fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0 ||
            !fix.accuracyMeters.isFinite() || fix.accuracyMeters !in 0.0..20.0) return null
        if ((fix.bearingDegrees != null && !fix.bearingDegrees.isFinite()) ||
            (fix.speedMetersPerSecond != null && (!fix.speedMetersPerSecond.isFinite() || fix.speedMetersPerSecond < 0)))
            return null
        val maxDistance = min(17.0, fix.accuracyMeters + 7.0)
        val good = segments.asSequence().filter { it.reviewed &&
            minOf(kotlin.math.abs(it.aLat - fix.latitude), kotlin.math.abs(it.bLat - fix.latitude)) <= 0.04 &&
            minOf(kotlin.math.abs(it.aLon - fix.longitude), kotlin.math.abs(it.bLon - fix.longitude)) <= 0.04
        }.mapNotNull { road ->
            val p = GeoMath.project(fix.latitude, fix.longitude, road)
            if (p.lengthMeters !in 3.0..2500.0 || p.rawFraction < -0.03 || p.rawFraction > 1.03 ||
                p.crossTrackMeters > maxDistance) return@mapNotNull null
            if (fix.bearingDegrees != null && fix.speedMetersPerSecond != null &&
                fix.speedMetersPerSecond >= 2.0 &&
                GeoMath.differenceDegrees(fix.bearingDegrees, GeoMath.bearing(road)) > 55.0) return@mapNotNull null
            RoadMatch(road.id, p.progressMeters, p.crossTrackMeters, p.lengthMeters)
        }.sortedBy { it.distanceMeters }.take(2).toList()
        if (good.isEmpty()) return null
        if (good.size > 1 && good[1].distanceMeters - good[0].distanceMeters < 9.0) return null
        return good[0]
    }
}
