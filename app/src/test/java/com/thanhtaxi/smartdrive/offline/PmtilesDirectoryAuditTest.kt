package com.thanhtaxi.smartdrive.offline

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class PmtilesDirectoryAuditTest {
    private val good=byteArrayOf(1,0,1,4,1)
    @Test fun oneTileDirectoryValid() {
        val report=PmtilesDirectoryAudit.inspectDirectory(good,1,4,0)
        assertTrue(report.checked)
        assertEquals(1,report.entries)
    }
    @Test fun gzippedRootDirectoryValid() {
        val output=ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(good) }
        assertTrue(PmtilesDirectoryAudit.inspectDirectory(output.toByteArray(),2,4,0).checked)
    }
    @Test fun rejectTileBeyondDataSection() {
        try {
            PmtilesDirectoryAudit.inspectDirectory(good,1,3,0)
            fail("Expected invalid tile range")
        } catch (_:IllegalArgumentException) {}
    }
    @Test fun rejectTruncatedIndexAndInvalidVarint() {
        for(bad in listOf(byteArrayOf(1,0,1,4),byteArrayOf(0),byteArrayOf(0x80.toByte()))) {
            try {
                PmtilesDirectoryAudit.inspectDirectory(bad,1,4,0)
                fail("Expected corrupt PMTiles root")
            } catch (_:IllegalArgumentException) {}
        }
    }
    @Test fun brotliOrZstdIsNotCalledVerified() {
        assertFalse(PmtilesDirectoryAudit.inspectDirectory(good,3,4,0).checked)
        assertFalse(PmtilesDirectoryAudit.inspectDirectory(good,4,4,0).checked)
    }
}
