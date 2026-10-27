package com.thanhtaxi.smartdrive.navigation

import java.util.PriorityQueue
import kotlin.math.*

/** Route graph is user-supplied and survey-labelled; reviewed is NOT government certification. */
data class RouteNode(val id: String, val lat: Double, val lon: Double,
                     val displayName: String? = null,
                     val aliases: List<String> = emptyList())
/** Independently labeled local point mapped to a surveyed node, never a national POI. */
enum class LocalPlaceCategory { PICKUP, DROPOFF, PARKING, REST, OTHER }
data class SurveyDestination(val id:String, val title:String, val nodeId:String,
                             val category:LocalPlaceCategory,
                             val aliases:List<String> = emptyList(),
                             val sourceRef:String)

data class RouteEdge(
    val id: String, val from: String, val to: String,
    val segmentId: String, val metres: Double, val reviewed: Boolean,
    val access: SurveyAccessRule = SurveyAccessRule(),
    /** User-supplied trip-model speed; NEVER a sign or legal speed. */
    val assumedSpeedKph: Double? = null
)
data class AllowedTurn(val fromEdge: String, val viaNode: String, val toEdge: String,
                       val reviewed: Boolean, val access: SurveyAccessRule = SurveyAccessRule())
data class RoutePlan(val nodeIds: List<String>, val edgeIds: List<String>, val metres: Double,
                     val accessContext: RouteAccessContext? = null,
                     val schedule: List<SegmentEta> = emptyList())

data class SegmentEta(val edgeId: String, val enterEpochMs: Long,
                      val exitEpochMs: Long, val modelSpeedKph: Double)

/** No geometric intersection is treated as a junction unless it is explicitly in the graph. */
class OfflineRouteGraph(
    nodes: List<RouteNode>,
    edges: List<RouteEdge>,
    turns: List<AllowedTurn>,
    val destinations: List<SurveyDestination> = emptyList()
) {
    val nodes: Map<String, RouteNode>
    private val outgoing: Map<String, List<RouteEdge>>
    private val edgesById: Map<String, RouteEdge>
    private val allowed: Set<Pair<String, String>>
    private val rulesByTurn: Map<Pair<String, String>, SurveyAccessRule>
    private val validatedSegmentTransitions: Set<Pair<String,String>>

    init {
        require(nodes.size <= 20_000 && edges.size <= 40_000 && turns.size <= 80_000)
        require(nodes.all { it.id.isNotBlank() && (it.displayName==null ||
            (it.displayName.length in 1..100 &&
                it.displayName.none { c -> c.isISOControl() })) &&
            it.lat.isFinite() && it.lon.isFinite() &&
            it.lat in -90.0..90.0 && it.lon in -180.0..180.0 })
        require(nodes.all { it.aliases.size<=8 &&
            it.aliases.all { a -> a.length in 1..100 && a.none { c -> c.isISOControl() } } })
        require(nodes.distinctBy { it.id }.size == nodes.size) { "Duplicate graph node" }
        require(edges.distinctBy { it.id }.size == edges.size) { "Duplicate graph edge" }
        this.nodes = nodes.associateBy { it.id }
        require(destinations.size<=20_000 && destinations.distinctBy { it.id }.size==destinations.size)
        require(destinations.all { d ->
            d.id.isNotBlank() && d.id.length<=96 && d.nodeId in this.nodes &&
            d.title.length in 1..100 && d.title.none { it.isISOControl() } &&
            d.sourceRef.isNotBlank() && d.sourceRef.length<=256 &&
            d.aliases.size<=8 && d.aliases.all { a ->
                a.length in 1..100 && a.none { it.isISOControl() }
            }
        }) { "Offline destinations need valid local node and provenance" }
        require(edges.all { it.id.isNotBlank() && it.segmentId.isNotBlank() &&
            it.from in this.nodes && it.to in this.nodes && it.from != it.to &&
            it.metres.isFinite() && it.metres in 2.0..3000.0 &&
            (it.assumedSpeedKph==null || it.assumedSpeedKph.isFinite() &&
                it.assumedSpeedKph in 5.0..110.0) })
        val edgeById = edges.associateBy { it.id }
        edgesById = edgeById
        require(turns.distinctBy { it.fromEdge to it.toEdge }.size==turns.size) {
            "Duplicate directed turn"
        }
        require(turns.all {
            val before = edgeById[it.fromEdge]
            val after = edgeById[it.toEdge]
            it.viaNode in this.nodes && before != null && after != null &&
                before.to == it.viaNode && after.from == it.viaNode
        }) { "Turn must join two directed edges at a surveyed junction" }
        outgoing = edges.filter { it.reviewed }.groupBy { it.from }
        allowed = turns.asSequence().filter {
            it.reviewed && edgeById[it.fromEdge]?.reviewed == true &&
                edgeById[it.toEdge]?.reviewed == true
        }.map { it.fromEdge to it.toEdge }.toSet()
        rulesByTurn=turns.asSequence().filter {
            (it.fromEdge to it.toEdge) in allowed
        }.associate { (it.fromEdge to it.toEdge) to it.access }
        // A context-free road-matching gate must not assert a conditional turn.
        validatedSegmentTransitions = allowed.mapNotNull { (from, to) ->
            val prior=edgeById[from] ?: return@mapNotNull null
            val next=edgeById[to] ?: return@mapNotNull null
            if(prior.access.conditional || next.access.conditional ||
                rulesByTurn[from to to]?.conditional==true) return@mapNotNull null
            prior.segmentId to next.segmentId
        }.toSet()
    }

    /**
     * Only a recorded, reviewed edge-to-edge junction allows switching the
     * matched segment. A visual crossing of two roads is never sufficient.
     */
    fun isValidatedSegmentTransition(fromSegmentId: String, toSegmentId: String): Boolean =
        fromSegmentId == toSegmentId ||
            (fromSegmentId to toSegmentId) in validatedSegmentTransitions

    /** Returns the surveyed directed edge for route-preview generation only. */
    fun edge(id: String): RouteEdge? = edgesById[id]
    fun hasReviewedTurn(previousEdge: String, nextEdge: String,
                        context: RouteAccessContext? = null): Boolean =
        (previousEdge to nextEdge) in allowed &&
            rulesByTurn[previousEdge to nextEdge]?.permits(context)==true

    fun isEdgeAllowed(id: String, context: RouteAccessContext? = null): Boolean =
        edgesById[id]?.let { it.reviewed && it.access.permits(context) }==true

    fun hasLaneScopedRules(plan: RoutePlan): Boolean =
        plan.edgeIds.any { edgesById[it]?.access?.laneScoped==true } ||
        plan.edgeIds.zipWithNext().any { (a,b) -> rulesByTurn[a to b]?.laneScoped==true }

    fun hasConditionalRules(plan: RoutePlan): Boolean {
        if(plan.edgeIds.any { edgesById[it]?.access?.conditional==true }) return true
        return plan.edgeIds.zipWithNext().any { (a,b) ->
            rulesByTurn[a to b]?.conditional==true
        }
    }

    /** Confident graph check only. It never establishes permission on real roads. */
    fun isRouteEligible(plan: RoutePlan, context: RouteAccessContext?): Boolean {
        if(plan.nodeIds.size!=plan.edgeIds.size+1 ||
            plan.nodeIds.any { it !in nodes }) return false
        for(i in plan.edgeIds.indices) {
            val e=edgesById[plan.edgeIds[i]] ?:return false
            if(e.from!=plan.nodeIds[i] || e.to!=plan.nodeIds[i+1] ||
                !isEdgeAllowed(e.id,context))return false
            if(i>0 && !hasReviewedTurn(plan.edgeIds[i-1],e.id,context))return false
        }
        return true
    }

    /**
     * Dijkstra over (node, preceding directed edge). Every intermediate turn
     * needs an explicit allowlist entry. No optimistic turn assumptions.
     */
    fun route(start: String, finish: String,
              context: RouteAccessContext? = null): RoutePlan? {
        if (start !in nodes || finish !in nodes) return null
        if (start == finish) return RoutePlan(listOf(start), emptyList(), 0.0,context)
        data class State(val node: String, val incoming: String?)
        data class Entry(val cost: Double, val state: State)
        val first = State(start, null)
        val queue = PriorityQueue(compareBy<Entry> { it.cost }
            .thenBy { it.state.node }.thenBy { it.state.incoming ?: "" })
        val best = mutableMapOf(first to 0.0)
        val predecessor = mutableMapOf<State, State>()
        val enteredBy = mutableMapOf<State, String>()
        queue.add(Entry(0.0,first))
        var visits = 0
        while (queue.isNotEmpty() && visits < 120_000) {
            val (cost,state) = queue.poll()
            if (cost > (best[state] ?: Double.POSITIVE_INFINITY) + 1e-7) continue
            visits++
            if (state.node == finish) {
                val reversedNodes = mutableListOf(state.node)
                val reversedEdges = mutableListOf<String>()
                var cursor = state
                while (cursor != first) {
                    reversedEdges.add(enteredBy[cursor] ?: return null)
                    cursor = predecessor[cursor] ?: return null
                    reversedNodes.add(cursor.node)
                }
                return RoutePlan(reversedNodes.asReversed(), reversedEdges.asReversed(),
                    cost,context)
            }
            for (edge in outgoing[state.node].orEmpty()) {
                if(!edge.access.permits(context)) continue
                if(state.incoming!=null && !hasReviewedTurn(state.incoming,edge.id,context))
                    continue
                val next = State(edge.to, edge.id)
                val newCost = cost + edge.metres
                if (newCost + 1e-7 < (best[next] ?: Double.POSITIVE_INFINITY)) {
                    best[next] = newCost
                    predecessor[next] = state
                    enteredBy[next] = edge.id
                    queue.add(Entry(newCost,next))
                }
            }
        }
        return null
    }

    /**
     * Minute-aware modeled arrivals for each directed edge. Uses survey travel
     * speeds or a clearly labeled DEFAULT MODEL speed, NOT a speed limit.
     * No waiting in a blocked lane/road is assumed. Cap labels/horizon to
     * prevent untrusted packs from consuming unlimited CPU.
     *
     * Bucketed time-dependent Dijkstra retains alternate arrivals in distinct
     * minute buckets, rather than incorrectly discarding paths that reach a
     * junction later just after a temporary restriction ends.
     */
    fun routeTimed(start: String, finish: String, vehicle: RoutingVehicle,
                   departureEpochMs: Long, lane: Int? = null,
                   defaultModelSpeedKph: Double = 30.0): RoutePlan? {
        if(start !in nodes || finish !in nodes || departureEpochMs<0 ||
            !defaultModelSpeedKph.isFinite() || defaultModelSpeedKph !in 5.0..110.0 ||
            (lane!=null && lane !in 1..12)) return null
        val original=RouteAccessContext.atVietnamTime(vehicle,departureEpochMs,lane)
        if(start==finish) return RoutePlan(listOf(start),emptyList(),0.0,original)
        data class State(val node:String,val incoming:String?,val minuteBucket:Long)
        data class Entry(val state:State,val epoch:Long)
        data class Prev(val state:State,val edgeId:String,val enter:Long,
                        val exit:Long,val speed:Double)
        val root=State(start,null,departureEpochMs/60_000L)
        val queue=PriorityQueue(compareBy<Entry> { it.epoch }
            .thenBy { it.state.node }.thenBy { it.state.incoming?:"" })
        val best=mutableMapOf(root to departureEpochMs)
        val previous=HashMap<State,Prev>()
        queue.add(Entry(root,departureEpochMs))
        val horizon=12L*60*60*1000
        var visits=0
        while(queue.isNotEmpty() && visits<120_000) {
            val (state,epoch)=queue.poll()
            if(epoch!=best[state]) continue
            visits++
            if(state.node==finish) {
                val reversedNodes=mutableListOf(state.node)
                val reversedEdges=mutableListOf<String>()
                val reversedSegments=mutableListOf<SegmentEta>()
                var cursor=state
                while(cursor!=root) {
                    val p=previous[cursor]?:return null
                    reversedEdges.add(p.edgeId)
                    reversedSegments.add(SegmentEta(p.edgeId,p.enter,p.exit,p.speed))
                    cursor=p.state
                    reversedNodes.add(cursor.node)
                }
                val edges=reversedEdges.asReversed()
                val total=edges.sumOf { edgesById.getValue(it).metres }
                return RoutePlan(reversedNodes.asReversed(),edges,total,original,
                    reversedSegments.asReversed())
            }
            for(edge in outgoing[state.node].orEmpty()) {
                val speed=edge.assumedSpeedKph?:defaultModelSpeedKph
                val travelMs=kotlin.math.ceil(edge.metres*3_600_000.0/
                    (speed*1000.0)).toLong().coerceAtLeast(1)
                if(travelMs>horizon || epoch>Long.MAX_VALUE-travelMs) continue
                val exit=epoch+travelMs
                if(exit<departureEpochMs || exit-departureEpochMs>horizon) continue
                if(state.incoming!=null &&
                    !hasReviewedTurn(state.incoming,edge.id,
                        RouteAccessContext.atVietnamTime(vehicle,epoch,lane))) continue
                if(!isEdgeAvailableDuring(edge.id,vehicle,lane,epoch,exit)) continue
                val next=State(edge.to,edge.id,exit/60_000L)
                if(exit<(best[next]?:Long.MAX_VALUE)) {
                    best[next]=exit
                    previous[next]=Prev(state,edge.id,epoch,exit,speed)
                    queue.add(Entry(next,exit))
                }
            }
        }
        return null
    }

    /** Includes every crossed local-minute boundary, not just edge endpoints. */
    fun isEdgeAvailableDuring(edgeId:String,vehicle:RoutingVehicle,lane:Int?,
                              enterMs:Long,exitMs:Long):Boolean {
        if(enterMs<0 || exitMs<=enterMs || exitMs-enterMs>3_600_000L) return false
        val edge=edgesById[edgeId]?:return false
        if(!edge.reviewed)return false
        val entryCtx=RouteAccessContext.atVietnamTime(vehicle,enterMs,lane)
        if(!edge.access.permits(entryCtx))return false
        var boundary=(enterMs/60_000L+1L)*60_000L
        while(boundary<exitMs) {
            if(!edge.access.permits(RouteAccessContext.atVietnamTime(vehicle,boundary,lane)))
                return false
            boundary+=60_000L
        }
        return true
    }

    /** Validate every modeled arrival before route-preview geometry or voice. */
    fun isScheduledPlanValid(plan:RoutePlan):Boolean {
        val context=plan.accessContext?:return false
        if(plan.schedule.size!=plan.edgeIds.size || plan.edgeIds.isEmpty() ||
            !isRouteEligibleGeometry(plan))return false
        var previousExit:Long?=null
        var total=0.0
        for((i,s) in plan.schedule.withIndex()) {
            if(s.edgeId!=plan.edgeIds[i] || !s.modelSpeedKph.isFinite() ||
                s.modelSpeedKph !in 5.0..110.0 ||
                (previousExit!=null && previousExit!=s.enterEpochMs))return false
            if(i>0 && !hasReviewedTurn(plan.edgeIds[i-1],s.edgeId,
                    RouteAccessContext.atVietnamTime(context.vehicle,s.enterEpochMs,context.lane)))
                return false
            if(!isEdgeAvailableDuring(s.edgeId,context.vehicle,context.lane,
                    s.enterEpochMs,s.exitEpochMs))return false
            val e=edgesById[s.edgeId]?:return false
            val expected= kotlin.math.ceil(e.metres*3600.0/
                s.modelSpeedKph*1000.0).toLong().coerceAtLeast(1L)
            if(s.exitEpochMs-s.enterEpochMs!=expected ||
                (e.assumedSpeedKph!=null &&
                    kotlin.math.abs(e.assumedSpeedKph-s.modelSpeedKph)>0.001))
                return false
            total+=e.metres
            previousExit=s.exitEpochMs
        }
        return kotlin.math.abs(total-plan.metres)<0.25
    }

    private fun isRouteEligibleGeometry(plan:RoutePlan):Boolean {
        if(plan.nodeIds.size!=plan.edgeIds.size+1)return false
        return plan.edgeIds.indices.all { i ->
            val edge=edgesById[plan.edgeIds[i]]
            edge!=null && edge.reviewed && edge.from==plan.nodeIds[i] &&
                edge.to==plan.nodeIds[i+1] &&
                (i==0 || (plan.edgeIds[i-1] to edge.id) in allowed)
        }
    }

    companion object {
        fun haversine(aLat: Double,aLon: Double,bLat: Double,bLon: Double): Double {
            val r = 6371008.8
            val x = Math.toRadians(bLat-aLat)
            val y = Math.toRadians(bLon-aLon)
            val v = sin(x/2).pow(2) +
                cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(y/2).pow(2)
            return 2*r*asin(sqrt(v.coerceIn(0.0,1.0)))
        }
    }
}
