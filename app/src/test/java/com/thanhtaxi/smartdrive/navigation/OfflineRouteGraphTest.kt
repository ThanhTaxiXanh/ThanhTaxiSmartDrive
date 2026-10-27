package com.thanhtaxi.smartdrive.navigation

import org.junit.Assert.*
import org.junit.Test

class OfflineRouteGraphTest {
    private val nodes = listOf(
        RouteNode("A",21.0,106.0), RouteNode("B",21.0,106.001),
        RouteNode("C",21.0,106.002), RouteNode("D",21.001,106.001)
    )
    private val edges = listOf(
        RouteEdge("ab","A","B","s1",100.0,true),
        RouteEdge("bc","B","C","s2",100.0,true),
        RouteEdge("ad","A","D","s3",140.0,true),
        RouteEdge("dc","D","C","s4",140.0,true)
    )
    @Test fun choosesShortestSurveyedRoute() {
        val graph = OfflineRouteGraph(nodes,edges,listOf(
            AllowedTurn("ab","B","bc",true),
            AllowedTurn("ad","D","dc",true)))
        assertEquals(listOf("ab","bc"),graph.route("A","C")?.edgeIds)
        assertEquals(200.0,graph.route("A","C")!!.metres,0.01)
    }
    @Test fun doesNotInventAllowedTurns() {
        val graph = OfflineRouteGraph(nodes,edges,listOf(AllowedTurn("ad","D","dc",true)))
        assertEquals(listOf("ad","dc"),graph.route("A","C")?.edgeIds)
    }
    @Test fun rejectsUnreviewedTurnsAndMissingRoads() {
        val graph = OfflineRouteGraph(nodes,edges.take(2),listOf(AllowedTurn("ab","B","bc",false)))
        assertNull(graph.route("A","C"))
        assertNull(graph.route("unlisted","C"))
        assertEquals(0.0,graph.route("A","A")!!.metres,0.01)
    }
    @Test fun edgesStayDirected() {
        val graph = OfflineRouteGraph(nodes,edges,emptyList())
        assertNull(graph.route("B","A"))
    }
    @Test fun turnMustJoinAtRealNode() {
        try {
            OfflineRouteGraph(nodes,edges,listOf(AllowedTurn("ab","C","bc",true)))
            fail("wrong via must fail")
        } catch (_: IllegalArgumentException) {}
    }
    @Test fun graphDoesNotAssumeCrossingsFromCoordinates() {
        val a=RouteNode("west",21.0,106.0)
        val b=RouteNode("east",21.0,106.002)
        val c=RouteNode("south",20.999,106.001)
        val d=RouteNode("north",21.001,106.001)
        val graph=OfflineRouteGraph(listOf(a,b,c,d),listOf(
            RouteEdge("h","west","east","h",200.0,true),
            RouteEdge("v","south","north","v",200.0,true)),emptyList())
        assertNull(graph.route("west","north"))
    }
    @Test fun surveyedButNotReviewedEdgeIsNotTraversable() {
        val graph=OfflineRouteGraph(nodes,edges.map {
            if(it.id=="ab") it.copy(reviewed=false) else it
        },listOf(AllowedTurn("ab","B","bc",true)))
        assertNull(graph.route("A","B"))
    }
}
