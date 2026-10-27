package com.thanhtaxi.smartdrive.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedComplianceTest {
    private val c = SpeedCompliance()
    private val verified = LimitResult(60, LimitStatus.VERIFIED, "sign", "test")
    @Test fun warningLevels() {
        assertEquals(SpeedSeverity.NORMAL, c.grade(SpeedObservation(48.0,true), verified))
        assertEquals(SpeedSeverity.APPROACHING, c.grade(SpeedObservation(58.0,true), verified))
        assertEquals(SpeedSeverity.OVER, c.grade(SpeedObservation(65.0,true), verified))
        assertEquals(SpeedSeverity.SERIOUS, c.grade(SpeedObservation(80.0,true), verified))
    }
    @Test fun unknownSourcesNeverTriggerLegalOverspeedAssertion() {
        assertEquals(SpeedSeverity.UNKNOWN, c.grade(SpeedObservation(120.0,true), LimitResult(null, LimitStatus.UNKNOWN,"none",""), 3))
        assertEquals(SpeedSeverity.UNKNOWN, c.grade(SpeedObservation(120.0,false), verified, 3))
    }
}
