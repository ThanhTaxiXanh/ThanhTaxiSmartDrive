package com.thanhtaxi.smartdrive.domain

enum class SpeedSeverity { UNKNOWN, NORMAL, APPROACHING, OVER, SERIOUS }
data class SpeedObservation(val gpsKph: Double?, val accurate: Boolean)
class SpeedCompliance {
    fun grade(observation: SpeedObservation, limit: LimitResult, warningToleranceKph: Int = 3): SpeedSeverity {
        if (!observation.accurate || observation.gpsKph == null || !observation.gpsKph.isFinite()
            || limit.kph == null || limit.status == LimitStatus.CONFLICT) return SpeedSeverity.UNKNOWN
        val excess = observation.gpsKph - limit.kph
        return when {
            excess >= warningToleranceKph + 15 -> SpeedSeverity.SERIOUS
            excess >= warningToleranceKph -> SpeedSeverity.OVER
            excess >= -5 -> SpeedSeverity.APPROACHING
            else -> SpeedSeverity.NORMAL
        }
    }
}
