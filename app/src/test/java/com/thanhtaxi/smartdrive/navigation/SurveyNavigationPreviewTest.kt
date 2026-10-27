package com.thanhtaxi.smartdrive.navigation

import org.junit.Assert.*
import org.junit.Test

class SurveyNavigationPreviewTest {
    private val nodes=listOf(RouteNode("A",21.0,106.0),
        RouteNode("B",21.0,106.001),
        RouteNode("C",21.0,106.002),
        RouteNode("D",21.001,106.001))
    private val edges=listOf(RouteEdge("ab","A","B","s1",105.0,true),
        RouteEdge("bc","B","C","s2",105.0,true),
        RouteEdge("dc","D","C","s3",145.0,true))
    private val turns=listOf(AllowedTurn("ab","B","bc",true))
    private val graph=OfflineRouteGraph(nodes,edges,turns)
    private val plan=graph.route("A","C")!!

    @Test fun idAndNearbyCoordinateLookupAndDeterministicSuggestions() {
        assertEquals("B",SurveyPointSearch.resolve(graph,"B").nodeId)
        val nearby=SurveyPointSearch.resolve(graph,"21.0,106.001",75.0)
        assertEquals(SurveyLookupStatus.NEAREST_NODE,nearby.status)
        assertEquals("B",nearby.nodeId)
        assertEquals(listOf("A"),SurveyPointSearch.suggestions(graph,"a"))
    }

    @Test fun licensedPackNodeNamesCanBeSearchedButDuplicatesRefused() {
        val named=OfflineRouteGraph(listOf(
            RouteNode("A",21.0,106.0,"Bến xe Bắc Ninh"),
            RouteNode("B",21.0,106.001,"Ga giả lập")),emptyList(),emptyList())
        assertEquals("A",SurveyPointSearch.resolve(named,"Bến xe Bắc Ninh").nodeId)
        assertEquals(listOf("Bến xe Bắc Ninh (A)"),
            SurveyPointSearch.suggestions(named,"Bến xe"))
        assertEquals("A",SurveyPointSearch.resolve(named,"Bến xe Bắc Ninh (A)").nodeId)
        val dup=OfflineRouteGraph(listOf(RouteNode("A",21.0,106.0,"Bến xe"),
            RouteNode("B",21.0,106.01,"Bến xe")),emptyList(),emptyList())
        assertEquals(SurveyLookupStatus.AMBIGUOUS,
            SurveyPointSearch.resolve(dup,"Bến xe").status)
    }
    @Test fun rejectsAmbiguousDistantAndInvalidCoordinates() {
        val close=OfflineRouteGraph(listOf(RouteNode("W",21.0,106.0),
            RouteNode("E",21.0,106.00009)),emptyList(),emptyList())
        assertEquals(SurveyLookupStatus.AMBIGUOUS,
            SurveyPointSearch.resolve(close,"21.0,106.000045").status)
        assertNull(SurveyPointSearch.resolve(graph,"21.5,106.5").nodeId)
        assertEquals(SurveyLookupStatus.BAD_QUERY,
            SurveyPointSearch.resolve(graph,"NaN,NaN").status)
        assertEquals(SurveyLookupStatus.NOT_FOUND,
            SurveyPointSearch.resolve(graph,"Nguyen Van Cu").status)
    }

    @Test fun offRouteRequiresThreeNewConsistentFixesAndResets() {
        val monitor=RouteDeviationMonitor()
        assertEquals(RouteDeviationState.ON_ROUTE,
            monitor.observe(graph,plan,21.0,106.0005,3.0,1000).state)
        val lat=21.0008
        assertEquals(RouteDeviationState.SUSPECTED,
            monitor.observe(graph,plan,lat,106.0005,3.0,2000).state)
        assertEquals(RouteDeviationState.SUSPECTED,
            monitor.observe(graph,plan,lat,106.0005,3.0,2000).state)
        assertEquals(RouteDeviationState.SUSPECTED,
            monitor.observe(graph,plan,lat,106.0005,3.0,3000).state)
        assertEquals(RouteDeviationState.OFF_ROUTE,
            monitor.observe(graph,plan,lat,106.0005,3.0,4000).state)
        assertEquals(RouteDeviationState.ON_ROUTE,
            monitor.observe(graph,plan,21.0,106.0005,3.0,5000).state)
    }

    @Test fun badGpsAndLongGapFailClosed() {
        val monitor=RouteDeviationMonitor()
        val lat=21.0008
        monitor.observe(graph,plan,lat,106.0005,3.0,1000)
        monitor.observe(graph,plan,lat,106.0005,3.0,2000)
        assertEquals(RouteDeviationState.UNKNOWN,
            monitor.observe(graph,plan,lat,106.0005,50.0,3000).state)
        assertEquals(RouteDeviationState.SUSPECTED,
            monitor.observe(graph,plan,lat,106.0005,3.0,4000).state)
        assertEquals(RouteDeviationState.SUSPECTED,
            monitor.observe(graph,plan,lat,106.0005,3.0,20000).state)
    }

    @Test fun candidateRerouteCannotInventDirectedTurn() {
        val (point, reroute)=SurveyReroutePreview.candidate(graph,21.001,106.001,"C")
        assertEquals("D",point.nodeId)
        assertEquals(listOf("dc"),reroute?.edgeIds)
        val wrong=SurveyReroutePreview.candidate(graph,21.0,106.002,"A")
        assertEquals("C",wrong.first.nodeId)
        assertNull(wrong.second) // graph contains no reverse edges
    }
    @Test fun rerouteRequiresNearbyUniqueSurveyedNode() {
        val result=SurveyReroutePreview.candidate(graph,22.0,107.0,"C")
        assertNull(result.first.nodeId)
        assertNull(result.second)
    }
}
