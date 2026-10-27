package com.thanhtaxi.smartdrive.domain
import org.junit.Assert.*
import org.junit.Test

class SpeedPipPolicyTest {
    @Test fun userReviewedIsExplicitlyReferenceOnlyAndSilent() {
        val result=SpeedPipPolicy.present(
            LimitResult(40,LimitStatus.SURVEY_REPORTED,"sign","survey"),
            SpeedSeverity.SERIOUS)
        assertEquals("40",result.value)
        assertEquals(SpeedPipTrust.SURVEY_ONLY,result.trust)
        assertTrue(result.badge.contains("CHƯA XÁC THỰC"))
        assertFalse(result.soundEligible)
    }
    @Test fun legalDefaultEstimateNeverTriggersAuthoritativeAudio() {
        val result=SpeedPipPolicy.present(
            LimitResult(60,LimitStatus.ESTIMATED_FROM_LAW,"rules","conditional"),
            SpeedSeverity.OVER)
        assertEquals(SpeedPipTrust.ESTIMATED,result.trust)
        assertFalse(result.soundEligible)
    }
    @Test fun unknownAndConflictingLimitsDoNotDisplayNumberOrAlerts() {
        for (status in listOf(LimitStatus.UNKNOWN,LimitStatus.CONFLICT)) {
            val state=SpeedPipPolicy.present(LimitResult(null,status,"none","unknown"),
                SpeedSeverity.SERIOUS)
            assertEquals("--",state.value)
            assertEquals(SpeedSeverity.UNKNOWN,state.severity)
            assertFalse(state.soundEligible)
        }
    }
    @Test fun independentlyAuthenticatedWouldBeAudibleOnlyWhenOverspeed() {
        val limit=LimitResult(50,LimitStatus.VERIFIED,"signed","verified")
        assertFalse(SpeedPipPolicy.present(limit,SpeedSeverity.APPROACHING).soundEligible)
        assertTrue(SpeedPipPolicy.present(limit,SpeedSeverity.OVER).soundEligible)
        assertTrue(SpeedPipPolicy.present(limit,SpeedSeverity.SERIOUS).soundEligible)
    }
    @Test fun invalidNumericValueRefusedEvenIfStatusClaimsAuthenticated() {
        val result=SpeedPipPolicy.present(
            LimitResult(190,LimitStatus.VERIFIED,"signed","invalid"),SpeedSeverity.SERIOUS)
        assertEquals("--",result.value)
        assertFalse(result.soundEligible)
    }
}
