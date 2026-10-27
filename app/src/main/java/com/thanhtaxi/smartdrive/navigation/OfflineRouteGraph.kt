package com.thanhtaxi.smartdrive.navigation

import java.util.PriorityQueue
import kotlin.math.*

/** Route graph is user-supplied and survey-labelled; reviewed is NOT government certification. */
data class RouteNode(val id: String, val lat: Double, val lon: Double)
data class RouteEdge(
    val id: String, val from: String, val to: String,
    val segmentId: String, val metres: Double, val reviewed: Boolean
)
data class AllowedTurn(val fromEdge: String, val viaNode: String, val toEdge: String, val reviewed: Boolean)
data class RoutePlan(val nodeIds: List<String>, val edgeIds: List<String>, val metres: Double)

/** No geometric intersection is treated as a junction unless it is explicitly in the graph. */
class OfflineRouteGraph(
    nodes: List<RouteNode>,
    edges: List<RouteEdge>,
    turns: List<AllowedTurn>
) {
    val nodes: Map<String, RouteNode>
    private val outgoing: Map<String, List<RouteEdge>>
    private val edgesById: Map<String, RouteEdge>
    private val allowed: Set<Pair<String, String>>
    private val validatedSegmentTransitions: Set<Pair<String,String>>

    init {
        require(nodes.size <= 20_000 && edges.size <= 40_000 && turns.size <= 80_000)
        require(nodes.all { it.id.isNotBlank() && it.lat.isFinite() && it.lon.isFinite() &&
            it.lat in -90.0..90.0 && it.lon in -180.0..180.0 })
        require(nodes.distinctBy { it.id }.size == nodes.size) { "Duplicate graph node" }
        require(edges.distinctBy { it.id }.size == edges.size) { "Duplicate graph edge" }
        this.nodes = nodes.associateBy { it.id }
        require(edges.all { it.id.isNotBlank() && it.segmentId.isNotBlank() &&
            it.from in this.nodes && it.to in this.nodes && it.from != it.to &&
            it.metres.isFinite() && it.metres in 2.0..3000.0 })
        val edgeById = edges.associateBy { it.id }
        edgesById = edgeById
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
        validatedSegmentTransitions = allowed.mapNotNull { (from, to) ->
            val prior=edgeById[from] ?: return@mapNotNull null
            val next=edgeById[to] ?: return@mapNotNull null
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
    fun hasReviewedTurn(previousEdge: String, nextEdge: String): Boolean =
        (previousEdge to nextEdge) in allowed

    /**
     * Dijkstra over (node, preceding directed edge). Every intermediate turn
     * needs an explicit allowlist entry. No optimistic turn assumptions.
     */
    fun route(start: String, finish: String): RoutePlan? {
        if (start !in nodes || finish !in nodes) return null
        if (start == finish) return RoutePlan(listOf(start), emptyList(), 0.0)
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
                return RoutePlan(reversedNodes.asReversed(), reversedEdges.asReversed(), cost)
            }
            for (edge in outgoing[state.node].orEmpty()) {
                if (state.incoming != null && (state.incoming to edge.id) !in allowed) continue
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
