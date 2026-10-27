package com.thanhtaxi.smartdrive.navigation

import org.junit.Assert.*
import org.junit.Test

class ParkedGpsQualityTest {
    private fun good()=ParkedGpsSample(21.0,106.0,4.0,0.1,10_000L,false)

    @Test fun stationaryFreshAccurateRealFixAccepted() {
        assertEquals(ParkedGpsDecision.VALID_STATIONARY,
            ParkedGpsQuality.check(good(),10_700L))
    }
    @Test fun movingGpsStopsDiagnosticRegardlessOfAccuracy() {
        assertEquals(ParkedGpsDecision.MOVING_STOP,
            ParkedGpsQuality.check(good().copy(accuracyMeters=100.0,
                speedMetersPerSecond=1.0),10_100))
        assertEquals(ParkedGpsDecision.MOVING_STOP,
            ParkedGpsQuality.check(good().copy(speedMetersPerSecond=0.8),10_000))
    }
    @Test fun noSpeedMeansNoProofOfParking() {
        assertEquals(ParkedGpsDecision.NO_SPEED,
            ParkedGpsQuality.check(good().copy(speedMetersPerSecond=null),10_000))
        assertEquals(ParkedGpsDecision.NO_SPEED,
            ParkedGpsQuality.check(good().copy(speedMetersPerSecond=Double.NaN),10_000))
    }
    @Test fun rejectStaleFutureMockInaccurateAndBadLocation() {
        assertEquals(ParkedGpsDecision.STALE,ParkedGpsQuality.check(good(),16_000))
        assertEquals(ParkedGpsDecision.STALE,ParkedGpsQuality.check(good(),9_900))
        assertEquals(ParkedGpsDecision.MOCK,
            ParkedGpsQuality.check(good().copy(isMock=true),10_100))
        assertEquals(ParkedGpsDecision.POOR_ACCURACY,
            ParkedGpsQuality.check(good().copy(accuracyMeters=20.0),10_100))
        assertEquals(ParkedGpsDecision.BAD_FIX,
            ParkedGpsQuality.check(good().copy(latitude=120.0),10_100))
    }
}
