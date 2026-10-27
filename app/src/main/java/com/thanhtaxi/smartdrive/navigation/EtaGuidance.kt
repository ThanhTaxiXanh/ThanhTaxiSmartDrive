package com.thanhtaxi.smartdrive.navigation

import kotlin.math.roundToLong

/**
 * ETA from a short moving-speed window; NOT a traffic, congestion, road-rule
 * or arrival-time forecast. Zero / missing / implausible GPS speed => unknown.
 */
class SimpleEtaEstimator {
    private val recent=ArrayDeque<Double>()

    fun reset() { recent.clear() }

    fun estimate(remainingMeters: Double, speedMetersPerSecond: Double?): Long? {
        if (!remainingMeters.isFinite() || remainingMeters<0.0 ||
            speedMetersPerSecond==null || !speedMetersPerSecond.isFinite() ||
            speedMetersPerSecond !in 2.0..33.0) {
            reset()
            return null
        }
        recent.addLast(speedMetersPerSecond)
        while (recent.size>4) recent.removeFirst()
        if (recent.size<2) return null
        val sorted=recent.sorted()
        val speed=(sorted[(sorted.size-1)/2]+sorted[sorted.size/2])/2.0
        val seconds=(remainingMeters/speed)
        if (!seconds.isFinite() || seconds>12*60*60) return null
        return seconds.roundToLong().coerceAtLeast(0)
    }
}

/**
 * Pure once-per-threshold speech scheduler. All messages explicitly describe
 * geometric survey changes, never authoritative or legal instructions.
 */
class GuidancePromptPolicy {
    private var routeKey: String?=null
    private val announced=HashSet<String>()

    fun reset() { routeKey=null;announced.clear() }

    fun next(update: NavigationUpdate, routeId: String): String? {
        if(update.phase!=NavigationPhase.ACTIVE || update.etaSeconds==null ||
            update.nextManeuver==null || update.nextManeuverIndex==null ||
            update.nextManeuverMeters==null ||
            !update.nextManeuverMeters.isFinite()) return null
        if(routeId!=routeKey) { routeKey=routeId; announced.clear() }
        val distance=update.nextManeuverMeters
        if(distance<0.0 || distance>150.0) return null
        val threshold=if(distance<=40.0) "near" else "far"
        val key="${update.nextManeuverIndex}:$threshold"
        if(!announced.add(key)) return null
        val rounded=if(distance>=10.0)
            ((distance/10.0).toInt()*10).coerceAtLeast(10)
            else distance.toInt().coerceAtLeast(0)
        val statement=update.nextManeuver
            .replace(Regex("[\\p{Cntrl}]")," ")
            .take(130)
        return "Tuyến khảo sát thử. $statement, sau khoảng $rounded mét. " +
            "Vui lòng ưu tiên biển báo và đường thực tế."
    }
}
