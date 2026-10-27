package com.thanhtaxi.smartdrive.offline

import android.content.Context
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.security.MessageDigest

/** User-imported local POI database; coordinates and review flags are not legally authoritative. */
data class OfflinePoi(val id: String, val name: String, val address: String,
    val province: String, val category: String, val lat: Double, val lon: Double,
    val source: String, val verifiedBySupplier: Boolean)

class OfflinePoiRepository(private val context: Context) {
    private val installed get() = File(context.filesDir, "offline_pois_v1.db")
    private val staged get() = File(context.filesDir, "offline_pois_v1.pending")
    private val backup get() = File(context.filesDir, "offline_pois_v1.backup")
    fun available(): Boolean = installed.isFile

    /** Execute off the UI thread. SHA-256 comes from a separately supplied pack manifest. */
    @Synchronized fun importDb(uri: Uri, expectedSha: String): Int {
        require(Regex("[a-fA-F0-9]{64}").matches(expectedSha.trim())) {
            "Cần SHA-256 gồm 64 ký tự hex từ manifest của gói."
        }
        require(!staged.exists() && !backup.exists()) {
            "Có giao dịch cũ chưa được xử lý; không tự ghi đè gói dữ liệu."
        }
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Không mở được tệp đã chọn")
            input.use { source ->
                staged.outputStream().buffered().use { dest ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        total += count
                        require(total <= 250L * 1024 * 1024) { "Gói vượt 250 MiB" }
                        digest.update(buffer, 0, count)
                        dest.write(buffer, 0, count)
                    }
                    require(total > 0) { "Tệp rỗng" }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            require(hash.equals(expectedSha.trim(), true)) { "SHA-256 không khớp manifest" }
            val count = validateDb(staged)
            val hadPrevious = installed.exists()
            if (hadPrevious) check(installed.renameTo(backup)) { "Không thể sao lưu gói cũ" }
            if (!staged.renameTo(installed)) {
                if (hadPrevious) check(backup.renameTo(installed)) { "Không thể khôi phục bản cũ" }
                error("Không thể kích hoạt gói mới")
            }
            backup.delete()
            return count
        } finally {
            staged.delete()
            if (!installed.exists() && backup.exists()) backup.renameTo(installed)
        }
    }

    private fun validateDb(file: File): Int {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery("PRAGMA user_version", null).use {
                require(it.moveToFirst() && it.getInt(0) == 1) { "Schema POI không hỗ trợ" }
            }
            db.rawQuery("PRAGMA integrity_check", null).use {
                require(it.moveToFirst() && it.getString(0) == "ok") { "Cơ sở dữ liệu bị lỗi" }
            }
            db.rawQuery("SELECT count(*) FROM pois", null).use {
                require(it.moveToFirst())
                val count = it.getInt(0)
                require(count in 1..1_000_000) { "Số địa điểm không hợp lệ" }
                db.rawQuery("SELECT rowid FROM pois_fts WHERE pois_fts MATCH ? LIMIT 1",
                    arrayOf("\"test\"*")).use { /* Check on-device FTS5 availability. */ }
                db.rawQuery("SELECT count(*) FROM poi_geo", null).use { geo ->
                    require(geo.moveToFirst() && geo.getInt(0) == count) { "RTree không đồng bộ" }
                }
                return count
            }
        } finally { db.close() }
    }

    /** Search is entirely local; callers must never equate POI presence with route legality. */
    fun search(query: String, limit: Int = 25): List<OfflinePoi> {
        require(limit in 1..50)
        val expression = OfflinePoiQueries.ftsExpression(query) ?: return emptyList()
        if (!installed.exists()) return emptyList()
        val db = SQLiteDatabase.openDatabase(installed.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            val results = mutableListOf<OfflinePoi>()
            db.rawQuery("""SELECT p.id,p.name,p.address,p.province,p.category,p.lat,p.lon,
                p.source,p.verified FROM pois p JOIN pois_fts ON pois_fts.rowid=p.rowid
                WHERE pois_fts MATCH ? ORDER BY bm25(pois_fts) LIMIT ?""",
                arrayOf(expression, limit.toString())).use { cursor ->
                while (cursor.moveToNext()) results += OfflinePoi(
                    cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    cursor.getString(3), cursor.getString(4), cursor.getDouble(5),
                    cursor.getDouble(6), cursor.getString(7), cursor.getInt(8) == 1)
            }
            results
        } finally { db.close() }
    }
}
