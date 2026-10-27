package com.thanhtaxi.smartdrive.navigation

/** Privacy-preserving validation of one foreground GPS fix for a PARKED diagnostic.
 * No speed-limit/legal assurance or permission to give live directions is implied.
 */
data class ParkedGpsSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double?,
    val speedMetersPerSecond: Double?,
    val elapsedRealtimeMs: Long,
    val isMock: Boolean
)

enum class ParkedGpsDecision {
    VALID_STATIONARY, MOVING_STOP, NO_SPEED, POOR_ACCURACY,
    STALE, MOCK, BAD_FIX
}

object ParkedGpsQuality {
    const val MAX_SPEED_MPS = 0.8 // just under 3 km/h; safety, not proof of parking
    const val MAX_ACCURACY_METERS = 15.0
    const val MAX_AGE_MS = 5_000L

    fun check(fix: ParkedGpsSample, nowElapsedMs: Long): ParkedGpsDecision {
        if (!fix.latitude.isFinite() || !fix.longitude.isFinite() ||
            fix.latitude !in -90.0..90.0 || fix.longitude !in -180.0..180.0 ||
            fix.elapsedRealtimeMs < 0 || nowElapsedMs < 0)
            return ParkedGpsDecision.BAD_FIX
        if (fix.isMock) return ParkedGpsDecision.MOCK
        val age = nowElapsedMs-fix.elapsedRealtimeMs
        if (age < 0 || age > MAX_AGE_MS) return ParkedGpsDecision.STALE
        val speed=fix.speedMetersPerSecond
        if (speed==null || !speed.isFinite() || speed<0) return ParkedGpsDecision.NO_SPEED
        // Always stop the parked observer on clear movement, even with low GPS accuracy.
        if (speed>=MAX_SPEED_MPS) return ParkedGpsDecision.MOVING_STOP
        val accuracy=fix.accuracyMeters
        if (accuracy==null || !accuracy.isFinite() ||
            accuracy !in 0.0..MAX_ACCURACY_METERS)
            return ParkedGpsDecision.POOR_ACCURACY
        return ParkedGpsDecision.VALID_STATIONARY
    }
}
