package com.thanhtaxi.smartdrive.domain

import kotlin.math.max

/** End-of-restriction is a logical pack event; do not assume an official sign code. */
enum class SignKind { R_420, R_421, P_127, END_SPEED }
data class LocatedRoadSign(
    val id: String, val segmentId: String, val kind: SignKind,
    val latitude: Double, val longitude: Double, val progressMeters: Double,
    val kph: Int? = null,
    val direction: String? = null, val lane: Int? = null,
    val vehicle: VehicleClass? = null,
    val validFromMs: Long = Long.MIN_VALUE, val validUntilMs: Long = Long.MAX_VALUE,
    val confidence: Double = 1.0, val reviewed: Boolean = false
)
data class UpcomingSign(val kind: SignKind, val metres: Int, val kph: Int?)
data class SignDecision(
    val zone: PopulationZone, val speedEvidence: SpeedEvidence?,
    val upcoming: List<UpcomingSign>, val conflict: Boolean
)

/**
 * Applies only signs already passed on the confidently matched directional
 * segment. Nearby signs on other roads are ignored.
 * Zone/limit scope deliberately ends with the current surveyed segment.
 */
class TrafficSignEngine {
    fun evaluate(context: RoadContext, progressMeters: Double, accuracyMeters: Double,
                 signs: List<LocatedRoadSign>, nowMs: Long): SignDecision {
        if (!context.gpsReliable || !context.matchedConfidently || context.segmentId == null ||
            !progressMeters.isFinite() || !accuracyMeters.isFinite() || accuracyMeters > 20) {
            return SignDecision(context.zone, null, emptyList(), false)
        }
        val valid = signs.filter {
            it.segmentId == context.segmentId && it.reviewed && it.confidence >= 0.9 &&
            (it.direction == null || it.direction == context.direction) &&
            (it.vehicle == null || it.vehicle == context.vehicle) &&
            (it.lane == null || it.lane == context.lane) &&
            nowMs >= it.validFromMs && nowMs <= it.validUntilMs
        }.sortedWith(compareBy<LocatedRoadSign> { it.progressMeters }.thenBy { it.id })
        val clear = max(4.0, accuracyMeters)
        val passed = valid.filter { it.progressMeters <= progressMeters - clear }
        var zone = context.zone
        var current: LocatedRoadSign? = null
        var conflict = false
        passed.forEachIndexed { idx, sign ->
            // Contradictory signs in the same 5m area require human review.
            if (idx > 0) {
                val last = passed[idx - 1]
                if (sign.progressMeters - last.progressMeters <= 5.0 &&
                    ((sign.kind == SignKind.P_127 && last.kind == SignKind.P_127 && sign.kph != last.kph) ||
                     (setOf(sign.kind, last.kind) == setOf(SignKind.R_420, SignKind.R_421)))) conflict = true
            }
            if (sign.kind == SignKind.P_127 && sign.kph !in 5..130) conflict = true
            when (sign.kind) {
                SignKind.R_420 -> zone = PopulationZone.INSIDE
                SignKind.R_421 -> zone = PopulationZone.OUTSIDE
                SignKind.P_127 -> current = sign
                SignKind.END_SPEED -> current = null
            }
        }
        val upcoming = valid.filter { it.progressMeters > progressMeters + clear &&
            it.progressMeters - progressMeters <= 800.0 }
            .take(3).map { UpcomingSign(it.kind, (it.progressMeters - progressMeters).toInt(), it.kph) }
        val speed = if (conflict || current == null) null else SpeedEvidence(
            kph = current!!.kph ?: 0,
            segmentId = context.segmentId,
            source = EvidenceSource.VERIFIED_SIGN,
            direction = current!!.direction, lane = current!!.lane, vehicle = current!!.vehicle,
            confidence = current!!.confidence, reviewed = true,
            validFromEpochMs = current!!.validFromMs, validUntilEpochMs = current!!.validUntilMs
        )
        return SignDecision(zone, speed, upcoming, conflict)
    }
}
