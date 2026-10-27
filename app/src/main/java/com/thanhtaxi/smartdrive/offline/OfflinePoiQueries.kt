package com.thanhtaxi.smartdrive.offline

import java.text.Normalizer
import java.util.Locale

/** No network/geocoder dependency. FTS terms are strictly normalized before binding. */
object OfflinePoiQueries {
    fun normalize(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ROOT).replace('đ', 'd'), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex(" +"), " ")

    fun ftsExpression(query: String): String? {
        val words = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        // Prefix search on each token; quote all terms and bind as a single SQL argument.
        return words.take(12).joinToString(" AND ") { "\"$it\"*" }
    }
}
