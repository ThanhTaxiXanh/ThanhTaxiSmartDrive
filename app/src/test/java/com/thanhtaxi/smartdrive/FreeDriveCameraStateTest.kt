package com.thanhtaxi.smartdrive

import org.junit.Assert.*
import org.junit.Test

class FreeDriveCameraStateTest {
    @Test fun panningDisablesFollowingWithoutDisablingGps() {
        val state=FreeDriveCameraState()
        assertTrue(state.shouldCenter(true,true))
        state.userPanned()
        assertFalse(state.shouldCenter(true,true))
        state.resume()
        assertTrue(state.shouldCenter(true,true))
    }
    @Test fun inaccurateOrOutOfCoverageMustNotRecenter() {
        val state=FreeDriveCameraState()
        assertFalse(state.shouldCenter(false,true))
        assertFalse(state.shouldCenter(true,false))
    }
}
