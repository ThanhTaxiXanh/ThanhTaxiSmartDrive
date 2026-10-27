package com.thanhtaxi.smartdrive.offline

import android.content.Context
import android.net.Uri
import android.content.ContentResolver
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

data class PmtilesArchive(
    val file: File,
    val header: PmtilesHeader,
    val sha256: String,
    val sourceName: String,
    val license: String,
    val attribution: String
) {
    val tileUri: String get() = "pmtiles://file://" + file.absolutePath
}

/**
 * User-picked SAF stream -> content-addressed private file -> verified metadata.
 * Must run off the UI thread. The archive is not authenticated or legally approved.
 */
class PmtilesArchiveStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("pmtiles_pilot", Context.MODE_PRIVATE)
    private val folder get() = File(context.filesDir, "pmtiles").apply { mkdirs() }
    private val hashPattern = Regex("^[a-f0-9]{64}$")
    private fun digestOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buf = ByteArray(64*1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun parse(file: File): PmtilesHeader {
        require(file.length() in PmtilesV3.MIN_FILE_SIZE..PmtilesV3.MAX_FILE_SIZE)
        val head = ByteArray(PmtilesV3.HEADER_SIZE)
        file.inputStream().buffered().use { input ->
            var total = 0
            while (total < head.size) {
                val n = input.read(head,total,head.size-total)
                require(n > 0) { "Archive has a truncated PMTiles header" }
                total += n
            }
        }
        return PmtilesV3.parse(head,file.length())
    }

    @Synchronized fun importArchive(
        resolver: ContentResolver, uri: Uri, sourceName: String,
        license: String, attribution: String, expectedSha256: String?
    ): PmtilesArchive {
        val source = sourceName.trim()
        val terms = license.trim()
        val credit = attribution.trim()
        require(source.length in 5..200 && terms.length in 5..300 && credit.length in 2..300) {
            "Cần tên nhà cung cấp, giấy phép và attribution có nội dung"
        }
        val expected = expectedSha256?.trim()?.lowercase().orEmpty()
        require(expected.isEmpty() || hashPattern.matches(expected)) {
            "Expected SHA-256 must be exactly 64 hex characters"
        }
        val temp = File.createTempFile("import_",".tmp",folder)
        try {
            val input: InputStream = requireNotNull(resolver.openInputStream(uri)) {
                "Unable to read selected document"
            }
            var count = 0L
            input.buffered().use { stream ->
                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(64*1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw InterruptedException("Import cancelled")
                        val n = stream.read(buffer)
                        if (n < 0) break
                        require(count + n <= PmtilesV3.MAX_FILE_SIZE) { "Archive exceeds 1 GiB" }
                        require(temp.usableSpace >= n + 8L * 1024L * 1024L) { "Insufficient device storage" }
                        output.write(buffer,0,n)
                        count += n
                    }
                }
            }
            val header = parse(temp)
            val hash = digestOf(temp)
            require(expected.isEmpty() || hash == expected) { "SHA-256 mismatches source checksum" }
            val target = File(folder,"$hash.pmtiles")
            if (target.exists()) {
                require(target.length() == temp.length() && digestOf(target) == hash) {
                    "Existing archive file is corrupted"
                }
            } else {
                check(temp.renameTo(target)) { "Could not store PMTiles archive" }
            }
            val manifest = JSONObject()
                .put("sha256",hash)
                .put("bytes",target.length())
                .put("source",source)
                .put("license",terms)
                .put("attribution",credit)
                .toString()
            check(prefs.edit().putString("active",manifest).commit()) { "Could not save archive metadata" }
            // Replacing an archive never silently deletes the previous file.
            return PmtilesArchive(target,header,hash,source,terms,credit)
        } finally {
            temp.delete()
        }
    }

    /** Rehashes all bytes; do not call on main thread for large archives. */
    fun loadVerified(): PmtilesArchive? = runCatching {
        val manifest = JSONObject(prefs.getString("active",null) ?: return null)
        val hash = manifest.getString("sha256")
        if (!hashPattern.matches(hash)) return null
        val file = File(folder,"$hash.pmtiles")
        if (!file.isFile || file.length() != manifest.getLong("bytes")) return null
        if (digestOf(file) != hash) return null
        PmtilesArchive(file,parse(file),hash,
            manifest.getString("source"),manifest.getString("license"),
            manifest.getString("attribution"))
    }.getOrNull()

    @Synchronized fun deleteActiveArchive(): Boolean {
        val raw = prefs.getString("active", null) ?: return false
        val hash = JSONObject(raw).getString("sha256")
        require(hashPattern.matches(hash)) { "Invalid archive identifier" }
        val archive = File(folder,"$hash.pmtiles")
        check(prefs.edit().remove("active").commit()) { "Unable to clear active archive" }
        return !archive.exists() || archive.delete()
    }
    @Synchronized fun clearActive() {
        check(prefs.edit().remove("active").commit())
    }
}

/** Local raster preview only. Vector archives require audited style/layer definitions. */
object PmtilesRasterStyle {
    fun make(archive: PmtilesArchive): String {
        require(archive.header.isRaster) { "Preview supports PNG/JPEG/WebP raster only" }
        val src = JSONObject()
            .put("type","raster")
            .put("url",archive.tileUri)
            .put("tileSize",256)
            .put("minzoom",archive.header.minZoom)
            .put("maxzoom",archive.header.maxZoom)
            .put("attribution",archive.attribution)
        val style = JSONObject().put("version",8)
            .put("name","Offline raster user-supplied PMTiles (pilot)")
            .put("sources",JSONObject().put("offlineRaster",src))
            .put("layers",org.json.JSONArray().put(JSONObject()
                .put("id","offlineRasterLayer").put("type","raster").put("source","offlineRaster")))
        return style.toString()
    }
}
