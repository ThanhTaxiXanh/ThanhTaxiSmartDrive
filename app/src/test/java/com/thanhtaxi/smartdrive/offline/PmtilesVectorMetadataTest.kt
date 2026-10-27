package com.thanhtaxi.smartdrive.offline

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import org.json.JSONObject

class PmtilesVectorMetadataTest {
    private fun archive(json: String, compressed: Boolean): java.io.File {
        val raw = json.toByteArray(Charsets.UTF_8)
        val data = if (!compressed) raw else java.io.ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(raw) }
        }.toByteArray()
        val file = Files.createTempFile("pmtiles-metadata-", ".pmtiles").toFile()
        file.deleteOnExit()
        val header = ByteArray(127)
        "PMTiles".toByteArray(Charsets.US_ASCII).copyInto(header)
        header[7] = 3
        header[97] = (if(compressed) 2 else 1).toByte()
        header[99] = 1
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(24,127)
            putLong(32,data.size.toLong())
        }
        file.outputStream().use { it.write(header); it.write(data) }
        return file
    }
    @Test fun readsDeclaredLayerWithoutGuessing() {
        val f=archive("""{"vector_layers":[{"id":"hotosm_roads"}]}""",false)
        assertEquals(listOf("hotosm_roads"),PmtilesVectorMetadata.read(f).layers)
    }
    @Test fun preservesAdvertisedRoadNameAndRefFieldNames() {
        val f=archive("""{"vector_layers":[{"id":"roads","fields":{"name":"String","name:vi":"String","ref":"String","speed":"Number"}}]}""",true)
        val info=PmtilesVectorMetadata.read(f)
        assertEquals(listOf("name","name:vi","ref","speed"),info.fieldsByLayer["roads"])
        assertFalse(info.fieldsByLayer["roads"]!!.contains("maxspeed"))
    }
    @Test fun readsGzippedTileJson() {
        val f=archive("""{"vector_layers":[{"id":"transportation"},{"id":"roads"}]}""",true)
        assertEquals(2,PmtilesVectorMetadata.read(f).layers.size)
    }
    @Test fun missingLayersCannotPreview() {
        assertFalse(PmtilesVectorMetadata.read(archive("""{"name":"unknown"}""",false)).previewable)
    }
    @Test fun acceptsOnlyValidLocalStyleAndSelectedLayer() {
        val style=JSONObject(PmtilesVectorStyle.make(
            "pmtiles://file:///data/user/0/app/files/roads.pmtiles","roads","© OSM"))
        assertEquals("roads",style.getJSONArray("layers").getJSONObject(1).getString("source-layer"))
        assertEquals("vector",style.getJSONObject("sources").getJSONObject("localRoads").getString("type"))
        val limited=JSONObject(PmtilesVectorStyle.make(
            "pmtiles://file:///data/user/0/app/files/roads.pmtiles",
            "roads","© OSM",minZoom=6,maxZoom=12))
            .getJSONObject("sources").getJSONObject("localRoads")
        assertEquals(6,limited.getInt("minzoom"))
        assertEquals(12,limited.getInt("maxzoom"))
        try {
            PmtilesVectorStyle.make("https://example.org/a.pmtiles","roads","© OSM")
            fail("should not allow remote sources in offline style")
        } catch (_:IllegalArgumentException) { }
    }
}
