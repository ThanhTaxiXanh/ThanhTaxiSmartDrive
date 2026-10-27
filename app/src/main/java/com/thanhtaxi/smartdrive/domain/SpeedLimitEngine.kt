package com.thanhtaxi.smartdrive.domain

/** A numeric limit is never inferred from a GPS coordinate alone. */
enum class PopulationZone { INSIDE, OUTSIDE, UNKNOWN }
enum class RoadKind { DIVIDED_OR_MULTI_LANE_ONEWAY, TWO_WAY_OR_SINGLE_LANE_ONEWAY, MOTORWAY, UNKNOWN }
enum class VehicleClass { TAXI, LARGE_BUS, BUS_OR_MOTORCYCLE, TOWING_OR_TANKER, SPECIAL_MACHINERY }
enum class EvidenceSource { VERIFIED_SIGN, VALIDATED_CAMERA, VERIFIED_MAP, UNVERIFIED_MAP }
enum class LimitStatus { VERIFIED, SURVEY_REPORTED, ESTIMATED_FROM_LAW, UNKNOWN, CONFLICT }
data class RoadContext(
    val segmentId: String?,
    val direction: String?,
    val lane: Int?,
    val zone: PopulationZone,
    val kind: RoadKind,
    val vehicle: VehicleClass = VehicleClass.TAXI,
    val matchedConfidently: Boolean = false,
    val gpsReliable: Boolean = false
)
data class SpeedEvidence(
    val kph: Int,
    val segmentId: String,
    val source: EvidenceSource,
    val signCode: String = "P.127",
    val direction: String? = null,
    val lane: Int? = null,
    val vehicle: VehicleClass? = null,
    val validFromEpochMs: Long = Long.MIN_VALUE,
    val validUntilEpochMs: Long = Long.MAX_VALUE,
    val confidence: Double = 1.0,
    val reviewed: Boolean = false,
    val sourceRef: String? = null,
    /** Can only be set by future trusted signed-pack verifier; never by imported JSON. */
    val publisherAuthenticated: Boolean = false
)
data class LimitResult(
    val kph: Int?,
    val status: LimitStatus,
    val source: String,
    val note: String
)

/** Conservative phase-1 rule resolution. This is not a certified legal determination. */
class SpeedLimitEngine {
    fun resolve(context: RoadContext, evidence: List<SpeedEvidence>, nowEpochMs: Long): LimitResult {
        if (!context.gpsReliable || !context.matchedConfidently || context.segmentId.isNullOrBlank()) {
            return unknown("GPS hoặc đối sánh đoạn đường chưa đáng tin cậy")
        }
        val applicable = evidence.filter {
            it.kph in 5..130 &&
            it.segmentId == context.segmentId &&
            (it.direction == null || it.direction == context.direction) &&
            (it.lane == null || it.lane == context.lane) &&
            (it.vehicle == null || it.vehicle == context.vehicle) &&
            nowEpochMs in it.validFromEpochMs..it.validUntilEpochMs &&
            it.confidence >= (if (it.source == EvidenceSource.VALIDATED_CAMERA) 0.93 else 0.8) &&
            it.reviewed && !it.sourceRef.isNullOrBlank() &&
            it.source != EvidenceSource.UNVERIFIED_MAP
        }
        for (source in listOf(EvidenceSource.VERIFIED_SIGN, EvidenceSource.VALIDATED_CAMERA, EvidenceSource.VERIFIED_MAP)) {
            val items = applicable.filter { it.source == source && (source != EvidenceSource.VERIFIED_SIGN || it.reviewed) }
            val limits = items.map { it.kph }.distinct()
            if (limits.size > 1) return LimitResult(null, LimitStatus.CONFLICT, source.name, "Dữ liệu giới hạn tốc độ mâu thuẫn")
            if (limits.size == 1) {
                val disagreement = applicable.any { it.kph != limits[0] }
                if (disagreement) return LimitResult(null, LimitStatus.CONFLICT,
                    "conflicting sources", "Các nguồn dữ liệu tốc độ chưa thống nhất")
                // Imported reviewed=true and a sourceRef are user-controlled. They
                // do NOT imply authenticated publisher or legal certification.
                val authenticated = items.all { it.publisherAuthenticated } &&
                    applicable.all { it.publisherAuthenticated }
                return LimitResult(limits[0],
                    if (authenticated) LimitStatus.VERIFIED else LimitStatus.SURVEY_REPORTED,
                    source.name,
                    if(authenticated) "Nguồn được xác thực; vẫn phải quan sát biển thực tế"
                    else "Tham khảo gói khảo sát người dùng; CHƯA xác thực pháp lý")
            }
        }
        if (context.kind == RoadKind.MOTORWAY) return unknown("Cao tốc cần giới hạn theo biển và tổ chức giao thông")
        val n = defaultSpeed(context.vehicle, context.zone, context.kind)
            ?: return unknown("Thiếu phân loại đường, khu dân cư hoặc phương tiện")
        return LimitResult(n, LimitStatus.ESTIMATED_FROM_LAW, "TT 38/2024/TT-BGTVT (cần đối chiếu hiệu lực)", "Giới hạn mặc định có điều kiện; ưu tiên biển báo thực tế")
    }
    private fun defaultSpeed(vehicle: VehicleClass, zone: PopulationZone, kind: RoadKind): Int? {
        if (zone == PopulationZone.UNKNOWN || kind == RoadKind.UNKNOWN) return null
        if (vehicle == VehicleClass.SPECIAL_MACHINERY) return 40
        val high = kind == RoadKind.DIVIDED_OR_MULTI_LANE_ONEWAY
        if (zone == PopulationZone.INSIDE) return if (high) 60 else 50
        return when (vehicle) {
            VehicleClass.TAXI -> if (high) 90 else 80
            VehicleClass.LARGE_BUS -> if (high) 80 else 70
            VehicleClass.BUS_OR_MOTORCYCLE -> if (high) 70 else 60
            VehicleClass.TOWING_OR_TANKER -> if (high) 60 else 50
            VehicleClass.SPECIAL_MACHINERY -> 40
        }
    }
    private fun unknown(reason: String) = LimitResult(null, LimitStatus.UNKNOWN, "none", reason)
}
