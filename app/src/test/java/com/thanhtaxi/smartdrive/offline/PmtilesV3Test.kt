package com.thanhtaxi.smartdrive.offline

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PmtilesV3Test {
    private fun header(type: Int = 2): ByteArray {
        val b=ByteArray(127)
        "PMTiles".toByteArray(Charsets.US_ASCII).copyInto(b)
        b[7]=3
        val v=ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        v.putLong(8,127);v.putLong(16,4)
        v.putLong(24,131);v.putLong(32,4)
        v.putLong(40,0);v.putLong(48,0)
        v.putLong(56,135);v.putLong(64,65)
        b[97]=2;b[98]=1;b[99]=type.toByte()
        b[100]=10;b[101]=14
        v.putInt(102,(106.0 * 10_000_000).toInt())
        v.putInt(106,(21.0 * 10_000_000).toInt())
        v.putInt(110,(106.1 * 10_000_000).toInt())
        v.putInt(114,(21.1 * 10_000_000).toInt())
        b[118]=12
        v.putInt(119,(106.05 * 10_000_000).toInt())
        v.putInt(123,(21.05 * 10_000_000).toInt())
        return b
    }
    @Test fun validRasterHeader() {
        val value=PmtilesV3.parse(header(),200)
        assertTrue(value.isRaster)
        assertEquals(12,value.centerZoom)
        assertEquals(21.05,value.centerLat,0.00001)
    }
    @Test fun validVectorImportedButNotRasterPreview() {
        assertFalse(PmtilesV3.parse(header(1),200).isRaster)
    }
    @Test fun rejectsWrongMagicAndVersion() {
        val b=header(); b[0]=0
        assertFails(b,200)
        val newer=header();newer[7]=4
        assertFails(newer,200)
    }
    @Test fun detectsTruncationOrOutOfBoundsSections() {
        assertFails(header(),140)
        val data=header()
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putLong(8,99999)
        assertFails(data,200)
    }
    @Test fun badZoomAndBoundingBox() {
        val data=header(); data[101]=9
        assertFails(data,200)
        val coord=header()
        ByteBuffer.wrap(coord).order(ByteOrder.LITTLE_ENDIAN).putInt(106,(90.1*10_000_000).toInt())
        assertFails(coord,200)
    }
    @Test fun rejectsOversize() {
        assertFails(header(),PmtilesV3.MAX_FILE_SIZE+1)
    }
    private fun assertFails(value:ByteArray,size:Long) {
        try { PmtilesV3.parse(value,size); fail("Expected invalid archive") }
        catch (_: IllegalArgumentException) {}
    }
}
