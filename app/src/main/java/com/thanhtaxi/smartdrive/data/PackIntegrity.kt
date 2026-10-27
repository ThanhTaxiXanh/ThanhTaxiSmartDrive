package com.thanhtaxi.smartdrive.data

import java.security.MessageDigest
import java.util.Locale

/** On-device corruption detection only. A checksum does not authenticate a data source. */
object PackIntegrity {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { String.format(Locale.ROOT,"%02x",it.toInt() and 0xff) }

    fun consistent(bytes: ByteArray, expected: String?): Boolean =
        expected != null && expected.length == 64 &&
        MessageDigest.isEqual(sha256(bytes).toByteArray(Charsets.US_ASCII),
            expected.lowercase(Locale.ROOT).toByteArray(Charsets.US_ASCII))
}

/** Refuse rollback in the same data series unless explicitly restored from local backup. */
object PackVersions {
    fun upgradeAllowed(oldSeries: String?, oldVersion: Long, incomingSeries: String, incomingVersion: Long): Boolean =
        incomingSeries.isNotBlank() && incomingVersion in 1..1_000_000_000 &&
            (oldSeries != incomingSeries || incomingVersion > oldVersion)
}
