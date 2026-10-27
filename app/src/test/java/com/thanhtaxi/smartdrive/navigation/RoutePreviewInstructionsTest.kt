package com.thanhtaxi.smartdrive.navigation

import org.junit.Assert.*
import org.junit.Test

class RoutePreviewInstructionsTest {
    private val nodes=listOf(
        RouteNode("A",21.0,106.0),RouteNode("B",21.001,106.0),
        RouteNode("R",21.001,106.001),RouteNode("L",21.001,105.999))
    private val edges=listOf(
        RouteEdge("ab","A","B","ab",111.0,true),
        RouteEdge("br","B","R","br",104.0,true),
        RouteEdge("bl","B","L","bl",104.0,true))
    private val graph=OfflineRouteGraph(nodes,edges,listOf(
        AllowedTurn("ab","B","br",true),
        AllowedTurn("ab","B","bl",true)))
    @Test fun rightTurnGeometryAndDistance() {
        val p=graph.route("A","R")!!
        val result=RoutePreviewInstructions.generate(graph,p)
        assertEquals(1,result.size)
        assertEquals(GeometryChange.RIGHT,result.first().change)
        assertEquals(111.0,result.first().metresFromStart,0.001)
        assertTrue(RoutePreviewInstructions.asParkedText(graph,p).contains("KHÔNG PHẢI CHỈ DẪN"))
    }
    @Test fun leftTurnGeometry() {
        val p=graph.route("A","L")!!
        assertEquals(GeometryChange.LEFT,RoutePreviewInstructions.generate(graph,p).first().change)
    }
    @Test fun directLegHasNoTurnSteps() {
        assertTrue(RoutePreviewInstructions.generate(graph,graph.route("A","B")!!).isEmpty())
    }
    @Test fun forgedUnreviewedConnectionIsRefused() {
        val without=OfflineRouteGraph(nodes,edges,emptyList())
        val forged=RoutePlan(listOf("A","B","R"),listOf("ab","br"),215.0)
        try { RoutePreviewInstructions.generate(without,forged);fail("should reject unreviewed turn") }
        catch (_:IllegalArgumentException) {}
    }
    @Test fun forgedTurnCannotPassEvenWhenBothEdgesAreSameSegment() {
        val sameSegment=OfflineRouteGraph(nodes,listOf(
            RouteEdge("ab","A","B","same",111.0,true),
            RouteEdge("br","B","R","same",104.0,true)),emptyList())
        val forged=RoutePlan(listOf("A","B","R"),listOf("ab","br"),215.0)
        try {
            RoutePreviewInstructions.generate(sameSegment,forged)
            fail("Explicit edge-to-edge turn must exist")
        } catch (_: IllegalArgumentException) {}
    }
    @Test fun malformedPlanIsRefused() {
        val plan=RoutePlan(listOf("R","B","A"),listOf("br","ab"),215.0)
        try { RoutePreviewInstructions.generate(graph,plan);fail("wrong edge direction") }
        catch (_:IllegalArgumentException) {}
    }
}
