package com.thanhtaxi.smartdrive

/**
 * Manual map exploration must not be overwritten by each incoming GPS fix.
 * GPS marker may continue updating while camera stays under user control.
 */
class FreeDriveCameraState {
    var following: Boolean = true
        private set

    fun userPanned() { following = false }
    fun resume() { following = true }

    fun shouldCenter(gpsReliable: Boolean, insideMap: Boolean): Boolean =
        following && gpsReliable && insideMap
}
