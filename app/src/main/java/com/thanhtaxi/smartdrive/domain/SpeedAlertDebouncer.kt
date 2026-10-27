package com.thanhtaxi.smartdrive.domain

/** Rate-limits optional warning sounds; the visual warning always remains visible. */
class SpeedAlertDebouncer(private val repeatMs: Long = 45_000L) {
    private var previous = SpeedSeverity.UNKNOWN
    private var lastAt: Long? = null
    fun shouldAlert(severity: SpeedSeverity, monotonicMs: Long): Boolean {
        val alertable = severity == SpeedSeverity.OVER || severity == SpeedSeverity.SERIOUS
        if (!alertable) {
            previous = severity
            lastAt = null
            return false
        }
        val entering = previous != SpeedSeverity.OVER && previous != SpeedSeverity.SERIOUS
        val escalating = severity == SpeedSeverity.SERIOUS && previous != SpeedSeverity.SERIOUS
        val last = lastAt
        val due = last == null || (monotonicMs >= last && monotonicMs - last >= repeatMs)
        previous = severity
        if (entering || escalating || due) {
            lastAt = monotonicMs
            return true
        }
        return false
    }
}
