package com.thanhtaxi.smartdrive.domain

/** Pure presentation policy. This never establishes a statutory speed limit. */
enum class SpeedPipTrust { AUTHENTICATED, SURVEY_ONLY, ESTIMATED, UNKNOWN, CONFLICT }

data class SpeedPipState(
    val value: String,
    val trust: SpeedPipTrust,
    val badge: String,
    val detail: String,
    val severity: SpeedSeverity,
    val soundEligible: Boolean
)

object SpeedPipPolicy {
    fun present(limit: LimitResult, severity: SpeedSeverity): SpeedPipState {
        val trust = when(limit.status) {
            LimitStatus.VERIFIED -> SpeedPipTrust.AUTHENTICATED
            LimitStatus.SURVEY_REPORTED -> SpeedPipTrust.SURVEY_ONLY
            LimitStatus.ESTIMATED_FROM_LAW -> SpeedPipTrust.ESTIMATED
            LimitStatus.UNKNOWN -> SpeedPipTrust.UNKNOWN
            LimitStatus.CONFLICT -> SpeedPipTrust.CONFLICT
        }
        val usable = limit.kph in 5..130 && trust !in setOf(
            SpeedPipTrust.UNKNOWN,SpeedPipTrust.CONFLICT)
        val warning= if(!usable) SpeedSeverity.UNKNOWN else severity
        val badge=when(trust) {
            SpeedPipTrust.AUTHENTICATED -> "NGUỒN ĐÃ XÁC THỰC"
            SpeedPipTrust.SURVEY_ONLY -> "KHẢO SÁT · CHƯA XÁC THỰC"
            SpeedPipTrust.ESTIMATED -> "ƯỚC TÍNH · KHÔNG PHẢI BIỂN"
            SpeedPipTrust.UNKNOWN -> "KHÔNG ĐỦ DỮ LIỆU"
            SpeedPipTrust.CONFLICT -> "DỮ LIỆU MÂU THUẪN"
        }
        val detail=when {
            !usable -> limit.note
            trust==SpeedPipTrust.AUTHENTICATED -> "Theo nguồn đã xác thực; quan sát biển báo trên đường."
            trust==SpeedPipTrust.SURVEY_ONLY -> "Chỉ tham khảo; hồ sơ khảo sát chưa được xác minh độc lập."
            else -> "Ước tính có điều kiện theo phân loại đường; phải quan sát biển thật."
        }
        val soundEligible=trust==SpeedPipTrust.AUTHENTICATED &&
            (warning==SpeedSeverity.OVER || warning==SpeedSeverity.SERIOUS)
        return SpeedPipState(
            if(usable) limit.kph.toString() else "--",
            trust,badge,detail,warning,soundEligible)
    }
}
