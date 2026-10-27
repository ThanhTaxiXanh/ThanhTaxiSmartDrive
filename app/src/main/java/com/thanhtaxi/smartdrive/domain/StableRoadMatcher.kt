package com.thanhtaxi.smartdrive.domain
import kotlin.math.abs
import kotlin.math.max

/**
 * Only accepts a directional road after distinct, consistent GPS location fixes.
 * Must be called once per fresh GPS fix, never from the periodic UI refresh.
 */
class StableRoadMatcher(private val requiredFixes: Int = 3, private val maxGapMs: Long = 6000L) {
    init { require(requiredFixes in 2..8 && maxGapMs in 1000..30000) }
    private var pending: RoadMatch? = null
    private var streak = 0
    private var lastFixMs: Long? = null
    private var confirmed: RoadMatch? = null
    val isConfirming: Boolean get() = pending != null && confirmed == null
    fun reset() { pending=null; streak=0; lastFixMs=null; confirmed=null }

    fun observe(match: RoadMatch?, timeMs: Long): RoadMatch? {
        if (match==null || timeMs<0 || match.segmentId.isBlank() ||
            !match.progressMeters.isFinite() || !match.distanceMeters.isFinite() ||
            !match.lengthMeters.isFinite() || match.lengthMeters <= 0 ||
            match.progressMeters !in 0.0..match.lengthMeters ||
            match.distanceMeters !in 0.0..25.0) {
            reset()
            return null
        }
        val last=lastFixMs
        if(last!=null && last==timeMs) return confirmed
        if(last!=null && (timeMs<last || timeMs-last>maxGapMs)) reset()
        val prev=pending
        val dt=if(last!=null && timeMs>=last) timeMs-last else 0L
        val maxProgressJump=max(45.0,dt/1000.0*55.0+25.0)
        val continuing=prev!=null && prev.segmentId==match.segmentId &&
            abs(prev.progressMeters-match.progressMeters)<=maxProgressJump
        if(continuing) {
            streak=(streak+1).coerceAtMost(requiredFixes)
            if(streak==requiredFixes) confirmed=match
        } else {
            streak=1
            confirmed=null
        }
        pending=match
        lastFixMs=timeMs
        return confirmed
    }
}
