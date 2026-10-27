package com.thanhtaxi.smartdrive.offline

/** An on-device field-trial form, never an automated attestation of valid map tiles. */
data class OfflineSmokeObservations(
    val archiveChecksumKnown: Boolean,
    val mapStyleLoaded: Boolean,
    val noActiveNetwork: Boolean,
    val userObservedMapTiles: Boolean,
    val userTriedMultipleZoomLevels: Boolean,
    val userRestartedApplication: Boolean
)
enum class OfflineSmokeStatus { INCOMPLETE, READY_FOR_HUMAN_REVIEW }
object OfflineSmokePolicy {
    fun assess(input: OfflineSmokeObservations): OfflineSmokeStatus =
        if (input.archiveChecksumKnown && input.mapStyleLoaded && input.noActiveNetwork &&
            input.userObservedMapTiles && input.userTriedMultipleZoomLevels &&
            input.userRestartedApplication)
            OfflineSmokeStatus.READY_FOR_HUMAN_REVIEW
        else OfflineSmokeStatus.INCOMPLETE
}
