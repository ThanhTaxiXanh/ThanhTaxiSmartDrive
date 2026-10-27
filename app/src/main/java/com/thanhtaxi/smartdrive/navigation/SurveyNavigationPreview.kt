package com.thanhtaxi.smartdrive.navigation

import com.thanhtaxi.smartdrive.domain.GeoMath
import com.thanhtaxi.smartdrive.domain.RoadLine
import kotlin.math.max
import java.text.Normalizer
import java.util.Locale

/** Only IDs and surveyed coordinates, never an internet address/POI search. */
enum class SurveyLookupStatus { EXACT_ID, NEAREST_NODE, AMBIGUOUS, NOT_FOUND, BAD_QUERY }
data class SurveyLookup(val nodeId: String?, val status: SurveyLookupStatus, val detail: String)

/** Deliberately refuses to snap to a distant or ambiguous surveyed node. */
object SurveyPointSearch {
    /** Accent-insensitive matching for user-provided local names, not geocoding. */
    internal fun fold(input: String): String =
        Normalizer.normalize(input.lowercase(Locale.ROOT)
            .replace('đ','d'),Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"),"").trim()

    fun suggestions(graph: OfflineRouteGraph, query: String, maxResults: Int = 8): List<String> {
        if (maxResults !in 1..20) return emptyList()
        val q=fold(query)
        if(q.isBlank()) return emptyList()
        return graph.nodes.values.asSequence()
            .filter { fold(it.id).contains(q) ||
                it.displayName?.let { name -> fold(name).contains(q) }==true }
            .sortedWith(compareBy<RouteNode> { !fold(it.id).startsWith(q) &&
                it.displayName?.let { name -> fold(name).startsWith(q) }!=true }
                .thenBy { it.id.length }.thenBy { it.id })
            .map { node ->
                if(node.displayName.isNullOrBlank()) node.id
                else "${node.displayName} (${node.id})"
            }
            .take(maxResults).toList()
    }

    fun resolve(graph: OfflineRouteGraph, query: String, radiusMeters: Double=75.0): SurveyLookup {
        val q=query.trim()
        if(q.isBlank()) return SurveyLookup(null,SurveyLookupStatus.BAD_QUERY,"Chưa nhập nút khảo sát.")
        if(q in graph.nodes) return SurveyLookup(q,SurveyLookupStatus.EXACT_ID,"Đúng mã nút khảo sát.")
        // Names are read only from the imported, user-licensed survey package.
        // Never interpret arbitrary address text as a national geocoder result.
        val idFromLabel=graph.nodes.values.firstOrNull {
            !it.displayName.isNullOrBlank() &&
                q.equals("${it.displayName} (${it.id})",ignoreCase=true)
        }
        if(idFromLabel!=null) return SurveyLookup(idFromLabel.id,
            SurveyLookupStatus.EXACT_ID,"Tên điểm từ gói khảo sát đã nhập; chưa xác thực địa chỉ.")
        val folded=fold(q)
        val named=graph.nodes.values.filter {
            !it.displayName.isNullOrBlank() && folded==fold(it.displayName)
        }
        if(named.size==1) return SurveyLookup(named[0].id,
            SurveyLookupStatus.EXACT_ID,"Tên điểm từ gói khảo sát đã nhập; chưa xác thực địa chỉ.")
        if(named.size>1) return SurveyLookup(null,SurveyLookupStatus.AMBIGUOUS,
            "Trùng tên điểm trong gói khảo sát; chọn ID cụ thể.")
        val pair=q.split(",")
        if(pair.size!=2) return SurveyLookup(null,SurveyLookupStatus.NOT_FOUND,
            "Chỉ hỗ trợ ID nút đã nhập hoặc tọa độ WGS84 'vĩ độ,kinh độ'; chưa có tìm kiếm địa chỉ.")
        val lat=pair[0].trim().toDoubleOrNull()
        val lon=pair[1].trim().toDoubleOrNull()
        if(lat==null || lon==null || !lat.isFinite() || !lon.isFinite() ||
            lat !in -90.0..90.0 || lon !in -180.0..180.0 ||
            !radiusMeters.isFinite() || radiusMeters !in 1.0..150.0)
            return SurveyLookup(null,SurveyLookupStatus.BAD_QUERY,"Tọa độ WGS84 không hợp lệ.")
        val nearest=graph.nodes.values.asSequence()
            .map { it.id to OfflineRouteGraph.haversine(lat,lon,it.lat,it.lon) }
            .filter { it.second <= radiusMeters }
            .sortedWith(compareBy<Pair<String,Double>> { it.second }.thenBy { it.first })
            .take(2).toList()
        if(nearest.isEmpty()) return SurveyLookup(null,SurveyLookupStatus.NOT_FOUND,
            "Không có nút khảo sát trong bán kính ${radiusMeters.toInt()}m; không tự tạo đường.")
        if(nearest.size>1 && nearest[1].second-nearest[0].second < 20.0)
            return SurveyLookup(null,SurveyLookupStatus.AMBIGUOUS,
                "Gần nhiều nút khảo sát; hãy chọn chính xác ID, không tự đoán.")
        val found=nearest.first()
        return SurveyLookup(found.first,SurveyLookupStatus.NEAREST_NODE,
            "Gần nút ${found.first}, cách %.1fm; CHỈ là ghép tọa độ thử."
                .format(java.util.Locale.ROOT,found.second))
    }
}

enum class RouteDeviationState { UNKNOWN, ON_ROUTE, SUSPECTED, OFF_ROUTE }
data class RouteDeviationResult(val state: RouteDeviationState, val distanceMeters: Double?)

/**
 * Validates THREE distinct monotonically increasing synthetic/real fix timestamps.
 * This pure component does not start location services, reroute, speak or instruct a driver.
 */
class RouteDeviationMonitor(
    private val requiredOffRouteFixes: Int = 3,
    private val maxGapMs: Long = 7_000
) {
    init { require(requiredOffRouteFixes in 2..8 && maxGapMs in 1000..30_000) }
    private var strikes=0
    private var previousTimestamp: Long?=null
    private var last=RouteDeviationResult(RouteDeviationState.UNKNOWN,null)

    fun reset() { strikes=0; previousTimestamp=null; last=RouteDeviationResult(RouteDeviationState.UNKNOWN,null) }

    fun observe(graph: OfflineRouteGraph, route: RoutePlan, lat: Double, lon: Double,
                accuracyMeters: Double, fixTimeMs: Long): RouteDeviationResult {
        if(!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 ||
            lon !in -180.0..180.0 || !accuracyMeters.isFinite() ||
            accuracyMeters !in 0.0..20.0 || fixTimeMs<0 || route.edgeIds.isEmpty() ||
            route.nodeIds.size!=route.edgeIds.size+1) {
            reset()
            return last
        }
        val prev=previousTimestamp
        if(prev!=null && prev==fixTimeMs) return last
        if(prev!=null && (fixTimeMs<prev || fixTimeMs-prev>maxGapMs)) reset()
        previousTimestamp=fixTimeMs
        var best=Double.POSITIVE_INFINITY
        for (i in route.edgeIds.indices) {
            val edge=graph.edge(route.edgeIds[i]) ?:run { reset();return last }
            val laneCtx=if(route.schedule.isNotEmpty() && route.accessContext!=null)
                RouteAccessContext.atVietnamTime(route.accessContext.vehicle,
                    route.schedule[i].enterEpochMs,route.accessContext.lane)
                else route.accessContext
            if(!graph.isEdgeAllowed(edge.id,laneCtx) || edge.from!=route.nodeIds[i] ||
                edge.to!=route.nodeIds[i+1] ||
                (i>0 && !graph.hasReviewedTurn(route.edgeIds[i-1],edge.id,laneCtx))) {
                reset()
                return last
            }
            val a=graph.nodes[edge.from] ?:run { reset();return last }
            val b=graph.nodes[edge.to] ?:run { reset();return last }
            val projection=GeoMath.project(lat,lon,
                RoadLine(edge.id,a.lat,a.lon,b.lat,b.lon,reviewed=true))
            if(projection.lengthMeters <= 0 || !projection.crossTrackMeters.isFinite()) {
                reset();return last
            }
            best=minOf(best,projection.crossTrackMeters)
        }
        val off=best > max(30.0,2*accuracyMeters)
        strikes=if(off) (strikes+1).coerceAtMost(requiredOffRouteFixes) else 0
        val state=when {
            !off -> RouteDeviationState.ON_ROUTE
            strikes>=requiredOffRouteFixes -> RouteDeviationState.OFF_ROUTE
            else -> RouteDeviationState.SUSPECTED
        }
        last=RouteDeviationResult(state,best)
        return last
    }
}

/**
 * Explicit parked-only reroute *candidate*, from a uniquely nearby surveyed
 * junction and only using reviewed directed edges/turns. No automatic guidance.
 */
object SurveyReroutePreview {
    fun candidate(graph: OfflineRouteGraph, lat: Double,lon:Double,destinationId:String,
                  radiusMeters:Double=100.0,
                  context:RouteAccessContext?=null): Pair<SurveyLookup, RoutePlan?> {
        val q="${lat},${lon}"
        val located=SurveyPointSearch.resolve(graph,q,radiusMeters)
        val id=located.nodeId ?:return located to null
        if(id==destinationId) return located to graph.route(id,id,context)
        return located to graph.route(id,destinationId,context)
    }
}
