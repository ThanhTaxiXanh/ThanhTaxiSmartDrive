package com.thanhtaxi.smartdrive.domain

import com.thanhtaxi.smartdrive.navigation.*
import org.junit.Assert.*
import org.junit.Test

class RoadTopologyGateTest {
    private val nodes=listOf(RouteNode("A",21.0,106.0),
        RouteNode("B",21.0,106.001),
        RouteNode("C",21.0,106.002),
        RouteNode("D",21.001,106.001))
    private val edges=listOf(
        RouteEdge("ab","A","B","segment-a",100.0,true),
        RouteEdge("bc","B","C","segment-b",100.0,true),
        RouteEdge("db","D","B","segment-d",100.0,true))
    private fun m(id:String)=RoadMatch(id,30.0,2.0,100.0)
    @Test fun validDirectedReviewedTurnAllowsSwitch() {
        val graph=OfflineRouteGraph(nodes,edges,listOf(AllowedTurn("ab","B","bc",true)))
        val gate=RoadTopologyGate(graph)
        assertEquals("segment-a",gate.accept(m("segment-a"))?.segmentId)
        assertEquals("segment-b",gate.accept(m("segment-b"))?.segmentId)
        assertFalse(gate.rejectedTransition)
    }
    @Test fun missingTurnOrOppositeDirectionRefused() {
        val graph=OfflineRouteGraph(nodes,edges,listOf(AllowedTurn("ab","B","bc",true)))
        val gate=RoadTopologyGate(graph)
        gate.accept(m("segment-d"))
        assertNull(gate.accept(m("segment-b")))
        assertTrue(gate.rejectedTransition)
        assertEquals("segment-d",gate.accept(m("segment-d"))?.segmentId)
        gate.reset(graph)
        gate.accept(m("segment-b"))
        assertNull(gate.accept(m("segment-a")))
    }
    @Test fun unreviewedTurnCannotEnableTransition() {
        val graph=OfflineRouteGraph(nodes,edges,listOf(AllowedTurn("ab","B","bc",false)))
        val gate=RoadTopologyGate(graph)
        gate.accept(m("segment-a"))
        assertNull(gate.accept(m("segment-b")))
    }
    @Test fun graphAbsentDoesNotPretendToValidateTopology() {
        val gate=RoadTopologyGate()
        assertNotNull(gate.accept(m("segment-a")))
        assertNotNull(gate.accept(m("segment-b")))
        assertFalse(gate.rejectedTransition)
    }
    @Test fun resetAllowsReacquisitionAfterRestart() {
        val graph=OfflineRouteGraph(nodes,edges,emptyList())
        val gate=RoadTopologyGate(graph)
        gate.accept(m("segment-a"))
        assertNull(gate.accept(m("segment-b")))
        gate.reset(graph)
        assertNotNull(gate.accept(m("segment-b")))
    }
}
