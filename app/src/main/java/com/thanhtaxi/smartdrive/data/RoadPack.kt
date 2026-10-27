package com.thanhtaxi.smartdrive.data

import android.content.Context
import com.thanhtaxi.smartdrive.domain.*
import com.thanhtaxi.smartdrive.navigation.*
import org.json.JSONObject
import java.io.File
import kotlin.math.*

data class Segment(
    val id: String, val startLat: Double, val startLon: Double, val endLat: Double, val endLon: Double,
    val kind: RoadKind, val zone: PopulationZone, val direction: String, val reviewed: Boolean,
    val sourceRef: String? = null
)
data class RoadPack(
    val title: String, val source: String, val license: String,
    val series: String, val version: Long,
    val segments: List<Segment>, val signs: List<SpeedEvidence>,
    val trafficSigns: List<LocatedRoadSign> = emptyList(),
    val routeGraph: OfflineRouteGraph? = null
)

/**
 * On-device JSON pack, never bundled with invented road limits.
 * A self-reported review field does not constitute official legal verification.
 */
class RoadPackRepository(private val context: Context) {
    private val file get() = File(context.filesDir, "road_pack_v1.json")
    private val previous get() = File(context.filesDir, "road_pack_previous.json")
    private val meta get() = context.getSharedPreferences("road_pack_meta", Context.MODE_PRIVATE)
    fun load(): RoadPack? = runCatching {
        if (!file.exists()) return null
        val data = file.readBytes()
        val recorded = meta.getString("sha256",null)
        if (recorded != null && !PackIntegrity.consistent(data,recorded)) return null
        val pack = parse(String(data,Charsets.UTF_8))
        // Migrate legacy stored files to integrity tracking (not source authentication).
        if (recorded == null) meta.edit().putString("sha256",PackIntegrity.sha256(data))
            .putString("series",pack.series).putLong("version",pack.version).apply()
        pack
    }.getOrNull()
    @Synchronized fun import(json: String): RoadPack {
        require(json.toByteArray(Charsets.UTF_8).size <= 10_000_000)
        val pack = parse(json)
        val oldSeries = meta.getString("series",null)
        val oldVersion = meta.getLong("version",0L)
        require(PackVersions.upgradeAllowed(oldSeries,oldVersion,pack.series,pack.version)) {
            "Phiên bản phải lớn hơn phiên bản đã nhập trong cùng bộ dữ liệu"
        }
        val data = json.toByteArray(Charsets.UTF_8)
        val temp = File(context.filesDir,"road_pack_new.tmp")
        try {
            temp.writeBytes(data)
            check(PackIntegrity.consistent(temp.readBytes(),PackIntegrity.sha256(data))) { "Sai checksum" }
            if (file.exists()) {
                val current = file.readBytes()
                val known = meta.getString("sha256",null)
                if (known == null || PackIntegrity.consistent(current,known)) {
                    previous.writeBytes(current)
                    meta.edit().putString("backupSha256",PackIntegrity.sha256(current)).apply()
                }
            }
            check(temp.renameTo(file)) { "Không thể cập nhật dữ liệu mới" }
            meta.edit().putString("sha256",PackIntegrity.sha256(data))
                .putString("series",pack.series).putLong("version",pack.version).apply()
            return pack
        } finally { temp.delete() }
    }
    @Synchronized fun restoreBackup(): RoadPack {
        require(previous.exists()) { "Chưa có bản sao lưu" }
        val bytes = previous.readBytes()
        require(PackIntegrity.consistent(bytes,meta.getString("backupSha256",null))) {
            "Checksum bản sao lưu không hợp lệ"
        }
        val restored = parse(String(bytes,Charsets.UTF_8))
        val temp = File(context.filesDir,"road_pack_restore.tmp")
        try {
            temp.writeBytes(bytes)
            check(temp.renameTo(file)) { "Không thể khôi phục" }
            meta.edit().putString("sha256",PackIntegrity.sha256(bytes))
                .putString("series",restored.series).putLong("version",restored.version).apply()
            return restored
        } finally { temp.delete() }
    }
    fun parse(json: String): RoadPack {
        val root = JSONObject(json)
        require(root.getInt("schema") == 1) { "Phiên bản gói không hỗ trợ" }
        val series = root.optString("series","legacy-v1").trim()
        val version = root.optLong("version",1L)
        require(series.isNotBlank() && version in 1..1_000_000_000)
        val title = root.getString("title").trim()
        val source = root.getString("source").trim()
        val license = root.getString("license").trim()
        require(title.isNotBlank() && source.isNotBlank() && license.isNotBlank())
        val array = root.getJSONArray("segments")
        require(array.length() <= 100000) { "Gói quá lớn" }
        val segments = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val a = o.getJSONArray("from"); val b = o.getJSONArray("to")
            fun lat(v: Double) = v.also { require(it.isFinite() && it in -90.0..90.0) }
            fun lon(v: Double) = v.also { require(it.isFinite() && it in -180.0..180.0) }
            val sourceRef = o.optString("sourceRef").trim().takeIf { it.isNotBlank() && it.length <= 256 }
            Segment(o.getString("id"), lat(a.getDouble(0)), lon(a.getDouble(1)),
                lat(b.getDouble(0)), lon(b.getDouble(1)),
                RoadKind.valueOf(o.getString("roadKind")), PopulationZone.valueOf(o.getString("populationZone")),
                o.optString("direction", "forward"),
                o.optBoolean("reviewed", false) && sourceRef != null, sourceRef)
        }
        require(segments.distinctBy { it.id }.size == segments.size) { "Trùng segment ID" }
        require(segments.all { it.id.isNotBlank() &&
            GeoMath.project(it.startLat, it.startLon,
                RoadLine(it.id,it.startLat,it.startLon,it.endLat,it.endLon,it.reviewed))
                .lengthMeters in 3.0..2500.0 }) { "Đoạn đường không hợp lệ hoặc dài quá 2,5km" }
        val ids = segments.map { it.id }.toSet()
        val eligibleRoads = segments.filter { it.reviewed }.map { it.id }.toSet()
        val arr = root.optJSONArray("speedSigns")
        require((arr?.length() ?: 0) <= 25000)
        val signs = if (arr == null) emptyList() else (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val id = o.getString("segmentId")
            require(id in ids)
            val src = EvidenceSource.valueOf(o.getString("source"))
            require(src == EvidenceSource.VERIFIED_MAP || src == EvidenceSource.UNVERIFIED_MAP) {
                "speedSigns chỉ nhận dữ liệu thuộc đoạn đường; biển P.127 phải dùng trafficSigns có tọa độ"
            }
            require(o.getInt("kph") in 5..130)
            val sourceRef = o.optString("sourceRef").trim().takeIf { it.isNotBlank() && it.length <= 256 }
            SpeedEvidence(kph = o.getInt("kph"), segmentId = id,
                source = src,
                direction = o.optString("direction").takeIf { it.isNotBlank() },
                lane = if (o.has("lane")) o.getInt("lane") else null,
                validFromEpochMs = o.optLong("validFrom", Long.MIN_VALUE),
                validUntilEpochMs = o.optLong("validUntil", Long.MAX_VALUE),
                confidence = o.optDouble("confidence", 1.0),
                reviewed = o.optBoolean("reviewed", false) && sourceRef != null && id in eligibleRoads,
                sourceRef = sourceRef)
        }
        val roads = segments.associateBy { it.id }
        val trafficArray = root.optJSONArray("trafficSigns")
        require((trafficArray?.length() ?: 0) <= 25000) { "Gói có quá nhiều biển báo" }
        val located = if (trafficArray == null) emptyList() else (0 until trafficArray.length()).map { i ->
            val o = trafficArray.getJSONObject(i)
            val segmentId = o.getString("segmentId")
            val road = requireNotNull(roads[segmentId]) { "Biển phải liên kết với đoạn đường" }
            val signId = o.getString("id").trim()
            require(signId.isNotBlank())
            val code = SignKind.valueOf(o.getString("kind"))
            val lat = o.getDouble("lat")
            val lon = o.getDouble("lon")
            require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0)
            val line = RoadLine(road.id, road.startLat, road.startLon, road.endLat, road.endLon, road.reviewed)
            val projection = GeoMath.project(lat, lon, line)
            require(projection.crossTrackMeters <= 12.0 && projection.rawFraction in 0.0..1.0) {
                "Biển báo nằm ngoài phạm vi đoạn đường"
            }
            val kph = if (code == SignKind.P_127) o.getInt("kph") else null
            require(kph == null || kph in 5..130)
            val lane = if (o.has("lane")) o.getInt("lane").also { require(it > 0) } else null
            val from = o.optLong("validFrom", Long.MIN_VALUE)
            val until = o.optLong("validUntil", Long.MAX_VALUE)
            require(from <= until)
            val confidence = o.optDouble("confidence", 0.0)
            require(confidence.isFinite() && confidence in 0.0..1.0)
            val sourceRef = o.optString("sourceRef").trim().takeIf { it.isNotBlank() && it.length <= 256 }
            LocatedRoadSign(
                id = signId, segmentId = segmentId, kind = code,
                latitude = lat, longitude = lon, progressMeters = projection.progressMeters,
                kph = kph, direction = o.optString("direction").takeIf { it.isNotBlank() },
                lane = lane,
                vehicle = o.optString("vehicle").takeIf { it.isNotBlank() }?.let { VehicleClass.valueOf(it) },
                validFromMs = from, validUntilMs = until, confidence = confidence,
                reviewed = o.optBoolean("reviewed", false) && road.reviewed && sourceRef != null,
                sourceRef = sourceRef
            )
        }
        require(located.distinctBy { it.id }.size == located.size) { "Trùng mã ID biển báo" }
        val routing = root.optJSONObject("routing")
        val graph = if (routing == null) null else {
            val points = routing.getJSONArray("nodes")
            val links = routing.getJSONArray("edges")
            val approvedTurns = routing.getJSONArray("allowedTurns")
            require(points.length() <= 20000 && links.length() <= 40000 &&
                approvedTurns.length() <= 80000) { "Gói định tuyến vượt giới hạn" }
            fun parseAccess(item: JSONObject): SurveyAccessRule {
                if(!item.has("restrictions")) return SurveyAccessRule()
                val rule=item.optJSONObject("restrictions")
                    ?:throw IllegalArgumentException("restrictions phải là object")
                require(item.optBoolean("reviewed",false) &&
                    item.optString("sourceRef","").isNotBlank()) {
                    "Quy định hạn chế cần dấu reviewed và sourceRef khảo sát"
                }
                val allowed=rule.optJSONArray("allowedVehicles")
                val vehicles=if(allowed==null) emptySet() else {
                    require(allowed.length() in 1..RoutingVehicle.entries.size)
                    (0 until allowed.length()).map { ix ->
                        RoutingVehicle.valueOf(allowed.getString(ix))
                    }.toSet().also { require(it.isNotEmpty()) }
                }
                if(rule.has("allowedVehicles")) require(allowed!=null)
                val blocked=rule.optJSONArray("blockedWindows")
                if(rule.has("blockedWindows")) require(blocked!=null)
                val windows=if(blocked==null) emptyList() else {
                    require(blocked.length() in 1..32)
                    (0 until blocked.length()).map { ix ->
                        val w=blocked.getJSONObject(ix)
                        val days=w.getJSONArray("days")
                        require(days.length() in 1..7)
                        val values=(0 until days.length()).map { days.getInt(it) }.toSet()
                        SurveyClosure(values,w.getInt("startMinute"),w.getInt("endMinute"))
                    }
                }
                require(vehicles.isNotEmpty() || windows.isNotEmpty()) {
                    "Hạn chế giao thông phải có điều kiện hợp lệ"
                }
                return SurveyAccessRule(vehicles,windows,item.optString("sourceRef").trim())
            }
            val graphNodes = (0 until points.length()).map { i ->
                val item = points.getJSONObject(i)
                val label=item.optString("displayName","").trim()
                require(label.length<=100 && label.none { it.isISOControl() }) {
                    "Tên nút khảo sát không hợp lệ"
                }
                RouteNode(item.getString("id").trim(), item.getDouble("lat"),
                    item.getDouble("lon"),label.takeIf { it.isNotBlank() })
            }
            val nodeMap = graphNodes.associateBy { it.id }
            val graphEdges = (0 until links.length()).map { i ->
                val item = links.getJSONObject(i)
                val road = requireNotNull(roads[item.getString("segmentId")]) { "Đoạn đường định tuyến không tồn tại" }
                val start = requireNotNull(nodeMap[item.getString("fromNode")]) { "Thiếu điểm đầu" }
                val end = requireNotNull(nodeMap[item.getString("toNode")]) { "Thiếu điểm cuối" }
                val reversed = when(item.getString("direction")) {
                    "forward" -> false
                    "reverse" -> true
                    else -> throw IllegalArgumentException("Hướng routing edge phải là forward hoặc reverse")
                }
                val firstLat = if(reversed) road.endLat else road.startLat
                val firstLon = if(reversed) road.endLon else road.startLon
                val lastLat = if(reversed) road.startLat else road.endLat
                val lastLon = if(reversed) road.startLon else road.endLon
                require(OfflineRouteGraph.haversine(start.lat,start.lon,firstLat,firstLon) <= 8.0 &&
                    OfflineRouteGraph.haversine(end.lat,end.lon,lastLat,lastLon) <= 8.0) {
                    "Đỉnh routing phải trùng các đầu mút đoạn đường"
                }
                val metres = item.getDouble("distanceMeters")
                val direct = OfflineRouteGraph.haversine(start.lat,start.lon,end.lat,end.lon)
                require(direct > 2.0 && metres.isFinite() && metres >= direct*0.9 &&
                    metres <= direct*1.5) { "Chiều dài định tuyến bất thường" }
                val accepted = item.optBoolean("reviewed",false)
                if (accepted) require(item.optString("sourceRef").isNotBlank()) {
                    "Đoạn routing đã duyệt cần sourceRef khảo sát"
                }
                RouteEdge(item.getString("id").trim(),start.id,end.id,road.id,metres,
                    accepted && road.reviewed,parseAccess(item))
            }
            val turns = (0 until approvedTurns.length()).map { i ->
                val item = approvedTurns.getJSONObject(i)
                val accepted = item.optBoolean("reviewed",false)
                if (accepted) require(item.optString("sourceRef").isNotBlank()) {
                    "Chuyển hướng đã duyệt cần sourceRef khảo sát"
                }
                AllowedTurn(item.getString("fromEdge"),item.getString("viaNode"),
                    item.getString("toEdge"),accepted,parseAccess(item))
            }
            OfflineRouteGraph(graphNodes,graphEdges,turns)
        }
        return RoadPack(title, source, license, series, version, segments, signs, located, graph)
    }
}
