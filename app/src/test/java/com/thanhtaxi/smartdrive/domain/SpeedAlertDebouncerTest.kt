package com.thanhtaxi.smartdrive.domain

import org.junit.Assert.*
import org.junit.Test

class SpeedAlertDebouncerTest {
    @Test fun emitsOnEntryAndEscalationWithoutRepeatingEveryGpsTick() {
        val d = SpeedAlertDebouncer(30_000)
        assertFalse(d.shouldAlert(SpeedSeverity.NORMAL, 1000))
        assertTrue(d.shouldAlert(SpeedSeverity.OVER, 2000))
        assertFalse(d.shouldAlert(SpeedSeverity.OVER, 3000))
        assertTrue(d.shouldAlert(SpeedSeverity.SERIOUS, 4000))
        assertFalse(d.shouldAlert(SpeedSeverity.SERIOUS, 5000))
        assertTrue(d.shouldAlert(SpeedSeverity.SERIOUS, 35000))
        assertFalse(d.shouldAlert(SpeedSeverity.UNKNOWN, 36000))
        assertTrue(d.shouldAlert(SpeedSeverity.OVER, 37000))
    }
}
