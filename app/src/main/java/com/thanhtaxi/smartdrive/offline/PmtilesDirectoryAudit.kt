package com.thanhtaxi.smartdrive.offline

import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded first-level PMTiles v3 directory audit: not a full archive/tile validator. */
data class PmtilesRootAudit(val checked: Boolean, val entries: Int, val warning: String?) {
    val summary: String get() = when {
        checked -> "Đã kiểm tra $entries mục chỉ mục tile gốc"
        else -> warning ?: "Chưa thể giải mã chỉ mục tile gốc"
    }
}

object PmtilesDirectoryAudit {
    private const val MAX_ENTRIES = 65_535

    fun inspect(file: File): PmtilesRootAudit =
        RandomAccessFile(file, "r").use { input ->
            require(input.length() >= 127) { "PMTiles file truncated" }
            val bytes = ByteArray(127)
            input.readFully(bytes)
            require(String(bytes,0,7,Charsets.US_ASCII) == "PMTiles" && bytes[7].toInt()==3) {
                "PMTiles archive header not supported"
            }
            val h = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val start = h.getLong(8)
            val length = h.getLong(16)
            val leafSize = h.getLong(48)
            val tileSize = h.getLong(64)
            require(start >= 127 && length > 0 && start <= input.length() &&
                length <= input.length()-start && start+length <= 16_384 &&
                length <= 16_257) { "PMTiles root directory range invalid" }
            require(leafSize >= 0 && tileSize > 0) { "Invalid tile/leaf data range" }
            val raw = ByteArray(length.toInt())
            input.seek(start)
            input.readFully(raw)
            inspectDirectory(raw, bytes[97].toInt() and 255, tileSize,leafSize)
        }

    fun inspectDirectory(compressed: ByteArray, compression: Int,
                         tileDataLength: Long, leafDirectoryLength: Long): PmtilesRootAudit {
        if (compression == 3 || compression == 4) {
            return PmtilesRootAudit(false,0,
                "Chỉ mục nén Brotli/Zstd: chưa kiểm định được chỉ mục bằng Kotlin; MapLibre sẽ thử đọc")
        }
        val bytes = PmtilesVectorMetadata.decode(compressed,compression)
        class Varints {
            var index=0
            fun next(): Long {
                var answer=0L
                for (step in 0..8) {
                    require(index < bytes.size) { "Truncated PMTiles varint" }
                    val x=bytes[index++].toInt() and 255
                    answer=answer or ((x and 127).toLong() shl (7*step))
                    if (x and 128 == 0) return answer
                }
                throw IllegalArgumentException("Oversized PMTiles varint")
            }
        }
        val reader=Varints()
        val count=reader.next()
        require(count in 1..MAX_ENTRIES.toLong()) { "PMTiles root directory entry count invalid" }
        val n=count.toInt()
        var tileId=0L
        repeat(n) {
            val delta=reader.next()
            require(tileId <= Long.MAX_VALUE-delta) { "PMTiles tile ID overflow" }
            tileId+=delta
        }
        val runs=LongArray(n){reader.next()}
        val lengths=LongArray(n){reader.next()}
        var offset=0L
        for(i in 0 until n) {
            val encoded=reader.next()
            offset=when {
                encoded==0L && i>0 -> {
                    val len=lengths[i-1]
                    require(offset<=Long.MAX_VALUE-len) { "PMTiles offset overflow" }
                    offset+len
                }
                encoded>0 -> encoded-1
                else -> throw IllegalArgumentException("Invalid initial PMTiles offset")
            }
            val sectionSize=if(runs[i]==0L) leafDirectoryLength else tileDataLength
            require(lengths[i]>0 && sectionSize>0 && offset<=sectionSize &&
                lengths[i]<=sectionSize-offset) {
                "PMTiles root entry points outside tile/leaf section"
            }
        }
        require(reader.index==bytes.size) { "PMTiles root directory has trailing bytes" }
        return PmtilesRootAudit(true,n,null)
    }
}
