package com.thanhtaxi.smartdrive.offline

import org.json.JSONArray
import org.json.JSONObject

/** Minimal offline line rendering from one metadata-declared MVT source layer. */
object PmtilesVectorStyle {
    fun make(tileUri: String, layerId: String, attribution: String): String {
        require(tileUri.startsWith("pmtiles://file:///")) { "Only local app-private PMTiles sources supported" }
        require(Regex("^[A-Za-z0-9_.:-]{1,120}$").matches(layerId)) {
            "Source-layer must be selected from PMTiles metadata"
        }
        val roadSource = JSONObject().put("type","vector")
            .put("url",tileUri)
            .put("attribution",attribution)
        val background = JSONObject().put("id","background")
            .put("type","background")
            .put("paint",JSONObject().put("background-color","#f5f2eb"))
        val underlay = JSONObject().put("id","road-casing")
            .put("type","line").put("source","localRoads")
            .put("source-layer",layerId)
            .put("paint",JSONObject()
                .put("line-color","#f8f8f8")
                .put("line-width",JSONObject().put("base",1.4)
                    .put("stops",JSONArray().put(JSONArray().put(7).put(1.4))
                        .put(JSONArray().put(14).put(6.0))
                        .put(JSONArray().put(18).put(12.0))))
                .put("line-opacity",0.98))
        val road = JSONObject().put("id","road-lines")
            .put("type","line").put("source","localRoads")
            .put("source-layer",layerId)
            .put("paint",JSONObject()
                .put("line-color","#425d74")
                .put("line-width",JSONObject().put("base",1.4)
                    .put("stops",JSONArray().put(JSONArray().put(7).put(0.8))
                        .put(JSONArray().put(14).put(3.4))
                        .put(JSONArray().put(18).put(7.0))))
                .put("line-opacity",0.9))
        return JSONObject().put("version",8)
            .put("name","Thanh Taxi – offline roads preview (no routing)")
            .put("sources",JSONObject().put("localRoads",roadSource))
            .put("layers",JSONArray().put(background).put(underlay).put(road))
            .toString()
    }
}
