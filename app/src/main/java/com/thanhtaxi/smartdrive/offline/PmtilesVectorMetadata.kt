package com.thanhtaxi.smartdrive.offline

import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream

data class PmtilesVectorInfo(val layers: List<String>, val warning: String?) {
    val previewable: Boolean get() = layers.isNotEmpty() && warning == null
}

/**
 * Reads PMTiles v3 TileJSON metadata, with size and decompression limits.
 * Vector layer IDs are retrieved from the file; never guess "roads"/"transportation".
 */
object PmtilesVectorMetadata {
    private const val MAX_COMPRESSED = 256 * 1024
    private const val MAX_DECODED = 1024 * 1024
    private val SAFE_ID = Regex("^[A-Za-z0-9_.:-]{1,120}$")

    fun read(file: File): PmtilesVectorInfo {
        val header = ByteArray(127)
        RandomAccessFile(file, "r").use { raf ->
            raf.readFully(header)
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require(String(header,0,7,Charsets.US_ASCII) == "PMTiles" &&
                (header[7].toInt() and 255) == 3) { "Invalid PMTiles v3 header" }
            require((header[99].toInt() and 255) == 1) { "Metadata only supports MVT vector" }
            val position = buffer.getLong(24)
            val compressedLength = buffer.getLong(32)
            require(compressedLength in 1..MAX_COMPRESSED.toLong() &&
                position >= 127 && position <= raf.length() &&
                compressedLength <= raf.length()-position) { "Invalid or oversized metadata" }
            val raw = ByteArray(compressedLength.toInt())
            raf.seek(position)
            raf.readFully(raw)
            val bytes = decode(raw, header[97].toInt() and 255)
            val info = JSONObject(String(bytes,Charsets.UTF_8))
            val vectorLayers = info.optJSONArray("vector_layers")
                ?: return PmtilesVectorInfo(emptyList(),"Archive does not declare vector_layers")
            val ids = mutableListOf<String>()
            for (i in 0 until vectorLayers.length()) {
                val id = vectorLayers.optJSONObject(i)?.optString("id") ?: continue
                if (!SAFE_ID.matches(id)) {
                    return PmtilesVectorInfo(emptyList(),"Invalid source-layer identifier in archive metadata")
                }
                if (id !in ids) ids.add(id)
                require(ids.size <= 128) { "Too many source layers" }
            }
            return if(ids.isEmpty())
                PmtilesVectorInfo(emptyList(),"No usable source-layer was advertised in metadata")
            else PmtilesVectorInfo(ids,null)
        }
    }

    internal fun decode(bytes: ByteArray, compression: Int): ByteArray {
        require(compression == 1 || compression == 2) {
            "Metadata compression Brotli/Zstd unsupported; cannot preview this vector archive"
        }
        val stream = if (compression == 1) ByteArrayInputStream(bytes) else
            GZIPInputStream(ByteArrayInputStream(bytes))
        stream.use { source ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = source.read(buffer)
                if (n < 0) break
                require(out.size()+n <= MAX_DECODED) { "Decoded metadata exceeds 1 MiB" }
                out.write(buffer,0,n)
            }
            return out.toByteArray()
        }
    }
}
