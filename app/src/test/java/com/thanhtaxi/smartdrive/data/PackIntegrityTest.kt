package com.thanhtaxi.smartdrive.data
import org.junit.Assert.*
import org.junit.Test

class PackIntegrityTest {
    @Test fun detectsCorruption() {
        val bytes = "test".toByteArray()
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",PackIntegrity.sha256(bytes))
        assertTrue(PackIntegrity.consistent(bytes,PackIntegrity.sha256(bytes)))
        assertFalse(PackIntegrity.consistent("changed".toByteArray(),PackIntegrity.sha256(bytes)))
        assertFalse(PackIntegrity.consistent(bytes,null))
    }
    @Test fun noRollbackWithoutExplicitRestore() {
        assertTrue(PackVersions.upgradeAllowed("survey",5,"survey",6))
        assertFalse(PackVersions.upgradeAllowed("survey",5,"survey",5))
        assertFalse(PackVersions.upgradeAllowed("survey",5,"survey",4))
        assertTrue(PackVersions.upgradeAllowed("survey",5,"new-dataset",1))
    }
}
