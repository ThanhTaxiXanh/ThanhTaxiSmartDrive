package com.thanhtaxi.smartdrive.navigation

import com.thanhtaxi.smartdrive.domain.GeoMath
import com.thanhtaxi.smartdrive.domain.RoadLine
import kotlin.math.*

/**
 * Prototype active guidance engine. The routing graph is user-reviewed, not
 * independently authenticated for legal access, lanes, restrictions or safety.
 * Pure Kotlin: no Android services, UI, network, storage, TTS or permissions.
 */
enum class NavigationPhase {
    IDLE, ACTIVE, GPS_UNCERTAIN, POSSIBLY_OFF_ROUTE, OFF_ROUTE, ARRIVED
}

data class NavigationFix(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Double,
    val elapsedMs: Long,
    val bearingDegrees: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val isMock: Boolean = false
)

data class NavigationUpdate(
    val phase: NavigationPhase,
    val message: String,
    val remainingMeters: Double? = null,
    val nextManeuver: String? = null,
    val nextManeuverMeters: Double? = null,
    val crossTrackMeters: Double? = null,
    val alternative: RoutePlan? = null,
    val progressMeters: Double? = null,
    val etaSeconds: Long? = null,
    val nextManeuverIndex: Int? = null,
    val totalManeuvers: Int = 0
)

/**
 * Sessions start by explicit user action while parked. Incoming fixes must be
 * fresh, timestamp-distinct and plausible. Guide is suspended on uncertainty,
 * and an alternate route is a *proposal* until explicitly accepted while parked.
 */
class ActiveNavigationEngine(private val graph: OfflineRouteGraph) {
    private var route: RoutePlan? = null
    private var instructions: List<RoutePreviewStep> = emptyList()
    private var lastFixTime: Long? = null
    private var lastProgressMeters: Double? = null
    private var lastEdgeIndex: Int? = null
    private var offRouteStreak = 0
    private var arrivalStreak = 0
    private var proposal: RoutePlan? = null
    private val eta=SimpleEtaEstimator()
    private var last = NavigationUpdate(NavigationPhase.IDLE, "Chưa chọn tuyến thử.")

    val current: NavigationUpdate get() = last
    val activeRoute: RoutePlan? get() = route

    fun start(plan: RoutePlan): NavigationUpdate {
        require(plan.edgeIds.isNotEmpty()) { "Navigation requires a directed edge" }
        instructions = RoutePreviewInstructions.generate(graph, plan)
        route = plan
        lastFixTime = null
        lastProgressMeters = null
        lastEdgeIndex = null
        offRouteStreak = 0
        arrivalStreak = 0
        proposal = null
        eta.reset()
        last = NavigationUpdate(NavigationPhase.GPS_UNCERTAIN,
            "Đã chọn tuyến thử • Đang đợi vị trí GPS đủ tin cậy.")
        return last
    }

    fun stop(): NavigationUpdate {
        route = null
        instructions = emptyList()
        lastFixTime = null
        lastProgressMeters = null
        lastEdgeIndex = null
        offRouteStreak = 0
        arrivalStreak = 0
        proposal = null
        eta.reset()
        last = NavigationUpdate(NavigationPhase.IDLE, "Đã dừng hành trình thử.")
        return last
    }

    /** Only a parked UI action may approve the pending candidate route. */
    fun acceptProposedRoute(parkedConfirmed: Boolean): NavigationUpdate {
        val candidate = proposal
        if(!parkedConfirmed || candidate == null)
            return last
        return start(candidate)
    }

    /** Called when Activity loses foreground, location permission or GPS is stale. */
    fun suspendForUncertainGps(): NavigationUpdate {
        if(route == null) return last
        lastFixTime = null
        offRouteStreak = 0
        arrivalStreak = 0
        proposal = null
        eta.reset()
        last = NavigationUpdate(NavigationPhase.GPS_UNCERTAIN,
            "GPS không đủ tin cậy • Tạm ẩn hướng dẫn tuyến thử.")
        return last
    }

    fun observe(fix: NavigationFix, nowElapsedMs: Long): NavigationUpdate {
        val plan=route ?: return last
        if(last.phase==NavigationPhase.ARRIVED) return last
        val age=nowElapsedMs-fix.elapsedMs
        if(fix.isMock || !fix.lat.isFinite() || !fix.lon.isFinite() ||
            fix.lat !in -90.0..90.0 || fix.lon !in -180.0..180.0 ||
            !fix.accuracyMeters.isFinite() || fix.accuracyMeters !in 0.0..20.0 ||
            fix.elapsedMs<0 || nowElapsedMs<0 || age !in 0..5000 ||
            (fix.bearingDegrees!=null &&
                (!fix.bearingDegrees.isFinite() || fix.bearingDegrees !in 0.0..360.0)) ||
            (fix.speedMetersPerSecond!=null &&
                (!fix.speedMetersPerSecond.isFinite() || fix.speedMetersPerSecond<0))) {
            return suspendForUncertainGps()
        }
        val previous=lastFixTime
        if(previous!=null && fix.elapsedMs<=previous) {
            if(fix.elapsedMs==previous && age<=5000) return last
            return suspendForUncertainGps()
        }
        if(previous!=null && fix.elapsedMs-previous>7000)
            suspendForUncertainGps()
        lastFixTime=fix.elapsedMs

        data class Candidate(val index:Int, val cross:Double, val progress:Double,
                             val roadBearing:Double)
        val candidates=ArrayList<Candidate>()
        var nearest=Double.POSITIVE_INFINITY
        var headingRejected=false
        for(i in plan.edgeIds.indices) {
            val edge=graph.edge(plan.edgeIds[i]) ?:return suspendForUncertainGps()
            val a=graph.nodes[edge.from] ?:return suspendForUncertainGps()
            val b=graph.nodes[edge.to] ?:return suspendForUncertainGps()
            val line=RoadLine(edge.id,a.lat,a.lon,b.lat,b.lon,true)
            val position=GeoMath.project(fix.lat,fix.lon,line)
            if(!position.crossTrackMeters.isFinite() ||
                position.lengthMeters<=0.0) return suspendForUncertainGps()
            nearest=min(nearest,position.crossTrackMeters)
            if(position.rawFraction < -0.04 || position.rawFraction > 1.04 ||
                position.crossTrackMeters>min(24.0,fix.accuracyMeters+9.0)) continue
            // Bearing is meaningful for motion; reject confidently wrong-way
            // candidates rather than snapping to the opposite carriageway.
            val roadBearing=GeoMath.bearing(line)
            if(fix.speedMetersPerSecond!=null && fix.speedMetersPerSecond>=2.5 &&
                fix.bearingDegrees!=null &&
                GeoMath.differenceDegrees(fix.bearingDegrees,roadBearing)>70.0) {
                headingRejected=true
                continue
            }
            candidates.add(Candidate(i,position.crossTrackMeters,
                position.progressMeters / position.lengthMeters * edge.metres,roadBearing))
        }
        val sorted=candidates.sortedWith(compareBy<Candidate> { it.cross }.thenBy { it.index })
        // Before an edge has been established, two indistinguishable parallel
        // edges are AMBIGUOUS, not an invitation to guess.
        val prior=lastEdgeIndex
        val chosen=when {
            sorted.isEmpty() -> null
            prior!=null -> {
                val sequential=sorted.filter { it.index in prior..(prior+1) }
                val near=sequential.minByOrNull { it.cross }
                if(near!=null && near.cross<=sorted[0].cross+8.0) near
                else if(sorted.size>1 && sorted[1].cross-sorted[0].cross<8.0) null
                else sorted[0]
            }
            sorted.size>1 && sorted[1].cross-sorted[0].cross<8.0 -> null
            else -> sorted[0]
        }
        if(chosen==null && (sorted.isNotEmpty() || headingRejected))
            return suspendForUncertainGps()
        if(chosen==null) {
            arrivalStreak=0
            offRouteStreak++
            proposal=null
            eta.reset()
            if(offRouteStreak<3) {
                last=NavigationUpdate(NavigationPhase.POSSIBLY_OFF_ROUTE,
                    "GPS xa tuyến thử (${offRouteStreak}/3); chưa đề xuất đường.",crossTrackMeters=nearest)
                return last
            }
            val destination=plan.nodeIds.last()
            val (lookup, candidate)=SurveyReroutePreview.candidate(
                graph,fix.lat,fix.lon,destination,context=plan.accessContext)
            // Never auto-switch to a candidate or emit a turn instruction.
            proposal=candidate?.takeIf { it.edgeIds.isNotEmpty() && it.edgeIds!=plan.edgeIds }
            last=NavigationUpdate(NavigationPhase.OFF_ROUTE,
                if(proposal!=null)
                    "Lệch tuyến thử; có tuyến đề xuất từ nút ${lookup.nodeId}. Chỉ xác nhận khi xe đỗ."
                else "Lệch tuyến thử; không có tuyến thay thế đủ cơ sở.",crossTrackMeters=nearest,
                alternative=proposal)
            return last
        }
        // A suddenly far-forward or backward match is not proof of traversal.
        if(prior!=null && chosen.index>prior+1) return suspendForUncertainGps()
        var travelled=chosen.progress
        for(i in 0 until chosen.index)
            travelled+=graph.edge(plan.edgeIds[i])!!.metres
        val oldProgress=lastProgressMeters
        if(oldProgress!=null && travelled<oldProgress-35.0)
            return suspendForUncertainGps()
        val progress=max(oldProgress?:0.0,travelled).coerceAtMost(plan.metres)
        lastProgressMeters=progress
        lastEdgeIndex=chosen.index
        offRouteStreak=0
        proposal=null
        val destination=graph.nodes.getValue(plan.nodeIds.last())
        val endpointDistance=OfflineRouteGraph.haversine(
            fix.lat,fix.lon,destination.lat,destination.lon)
        val nearArrival=endpointDistance<=max(14.0,fix.accuracyMeters+8.0) &&
            progress>=plan.metres-35.0
        arrivalStreak=if(nearArrival) arrivalStreak+1 else 0
        if(arrivalStreak>=3) {
            last=NavigationUpdate(NavigationPhase.ARRIVED,
                "Đã tới gần điểm cuối trong dữ liệu thử. Hãy tự xác nhận điểm đến.",
                remainingMeters=0.0,crossTrackMeters=chosen.cross)
            return last
        }

        val maneuverIndex=instructions.indexOfFirst { it.metresFromStart>progress+3.0 }
        val maneuver=instructions.getOrNull(maneuverIndex)
        val remaining=(plan.metres-progress).coerceAtLeast(0.0)
        val seconds=eta.estimate(remaining,fix.speedMetersPerSecond)
        last=NavigationUpdate(NavigationPhase.ACTIVE,
            "Đang theo tuyến khảo sát thử • KHÔNG xác thực giao thông",
            remainingMeters=remaining,
            nextManeuver=maneuver?.text ?: "Theo hình học tuyến đến điểm cuối thử",
            nextManeuverMeters=maneuver?.let { (it.metresFromStart-progress).coerceAtLeast(0.0) },
            crossTrackMeters=chosen.cross,progressMeters=progress,
            etaSeconds=seconds,
            nextManeuverIndex=maneuverIndex.takeIf { it>=0 },
            totalManeuvers=instructions.size)
        return last
    }
}
