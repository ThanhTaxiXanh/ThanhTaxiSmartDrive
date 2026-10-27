package com.thanhtaxi.smartdrive.offline

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/** PMTiles v3 fixed 127-byte header validation. It does not validate every tile/directory. */
data class PmtilesHeader(
    val tileType: Int,
    val minZoom: Int,
    val maxZoom: Int,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    val centerLat: Double,
    val centerLon: Double,
    val centerZoom: Int
) {
    val isRaster: Boolean get() = tileType in setOf(2, 3, 4)
    val formatName: String get() = when (tileType) {
        1 -> "MVT vector"; 2 -> "PNG raster"; 3 -> "JPEG raster"; 4 -> "WebP raster"
        5 -> "AVIF raster (no preview)"; 6 -> "MLT vector (no preview)"
        else -> "Unknown"
    }
}
object PmtilesV3 {
    const val HEADER_SIZE = 127
    const val MIN_FILE_SIZE = 128L
    const val MAX_FILE_SIZE = 1_073_741_824L

    fun parse(header: ByteArray, fileSize: Long): PmtilesHeader {
        require(header.size >= HEADER_SIZE && fileSize >= MIN_FILE_SIZE && fileSize <= MAX_FILE_SIZE) {
            "PMTiles file size/header is invalid or exceeds 1 GiB"
        }
        require(String(header,0,7,Charsets.US_ASCII) == "PMTiles" && (header[7].toInt() and 255) == 3) {
            "Only PMTiles v3 is supported"
        }
        val data = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        fun validSection(offset: Int, length: Int) {
            val start = data.getLong(offset)
            val count = data.getLong(length)
            require(start >= HEADER_SIZE && count > 0 && start <= fileSize &&
                count <= fileSize - start) { "Archive section is outside the file" }
        }
        validSection(8,16) // root directory
        validSection(24,32) // metadata JSON (possibly compressed)
        validSection(56,64) // tile data
        val rootStart = data.getLong(8)
        val rootLength = data.getLong(16)
        require(rootStart + rootLength <= 16_384L) { "Root directory exceeds 16 KiB" }
        val leafOffset = data.getLong(40)
        val leafLength = data.getLong(48)
        require(leafOffset >= 0 && leafLength >= 0 &&
            ((leafLength == 0L && leafOffset <= fileSize) ||
                (leafOffset >= HEADER_SIZE && leafOffset <= fileSize && leafLength <= fileSize - leafOffset))) {
            "Leaf directory section invalid"
        }
        require((header[97].toInt() and 255) in 1..4 &&
            (header[98].toInt() and 255) in 1..4) { "Unsupported PMTiles compression marker" }
        val tileType = header[99].toInt() and 255
        require(tileType in 1..6) { "Unknown tile type" }
        val minZ = header[100].toInt() and 255
        val maxZ = header[101].toInt() and 255
        require(minZ <= maxZ && maxZ <= 24) { "Invalid zoom levels" }
        fun degree(offset: Int): Double = data.getInt(offset).toDouble()/10_000_000.0
        val minLon = degree(102)
        val minLat = degree(106)
        val maxLon = degree(110)
        val maxLat = degree(114)
        val cz = header[118].toInt() and 255
        val cl = degree(119)
        val ct = degree(123)
        require(minLat in -90.0..90.0 && maxLat in -90.0..90.0 &&
            minLon in -180.0..180.0 && maxLon in -180.0..180.0 &&
            minLat <= maxLat && minLon <= maxLon &&
            cl in -180.0..180.0 && ct in -90.0..90.0 && cz <= 24) {
            "Invalid PMTiles geographic bounds"
        }
        return PmtilesHeader(tileType,minZ,maxZ,minLat,minLon,maxLat,maxLon,ct,cl,cz)
    }
}
