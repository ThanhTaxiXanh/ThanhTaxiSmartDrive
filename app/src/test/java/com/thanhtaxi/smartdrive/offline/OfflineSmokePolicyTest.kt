package com.thanhtaxi.smartdrive.offline
import org.junit.Assert.*
import org.junit.Test

class OfflineSmokePolicyTest {
    private val complete=OfflineSmokeObservations(
        archiveChecksumKnown=true,mapStyleLoaded=true,noActiveNetwork=true,
        userObservedMapTiles=true,userTriedMultipleZoomLevels=true,userRestartedApplication=true)
    @Test fun allObservationsOnlyMakeHumanReviewReady() {
        assertEquals(OfflineSmokeStatus.READY_FOR_HUMAN_REVIEW,OfflineSmokePolicy.assess(complete))
    }
    @Test fun onlineOrWithoutRebootIsIncomplete() {
        assertEquals(OfflineSmokeStatus.INCOMPLETE,OfflineSmokePolicy.assess(complete.copy(noActiveNetwork=false)))
        assertEquals(OfflineSmokeStatus.INCOMPLETE,OfflineSmokePolicy.assess(complete.copy(userRestartedApplication=false)))
    }
    @Test fun styleLoadingNotEquivalentToVisibleRasterTiles() {
        assertEquals(OfflineSmokeStatus.INCOMPLETE,OfflineSmokePolicy.assess(complete.copy(userObservedMapTiles=false)))
    }
}
