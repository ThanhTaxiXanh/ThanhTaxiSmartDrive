package com.thanhtaxi.smartdrive.navigation

import org.junit.Assert.*
import org.junit.Test

class ActiveNavigationEngineTest {
    private val nodes=listOf(
        RouteNode("A",21.0,106.0),
        RouteNode("B",21.0,106.001),
        RouteNode("C",21.0,106.002),
        RouteNode("D",21.001,106.001),
        RouteNode("E",21.001,106.002))
    private val edges=listOf(
        RouteEdge("ab","A","B","ab",105.0,true),
        RouteEdge("bc","B","C","bc",105.0,true),
        RouteEdge("de","D","E","de",105.0,true),
        RouteEdge("ec","E","C","ec",111.0,true))
    private val turns=listOf(
        AllowedTurn("ab","B","bc",true),
        AllowedTurn("de","E","ec",true))
    private val graph=OfflineRouteGraph(nodes,edges,turns)
    private val plan=graph.route("A","C")!!
    private fun gps(lon:Double=106.0003,lat:Double=21.0,t:Long=1000L,
                    bearing:Double?=90.0,acc:Double=3.0,mock:Boolean=false)=
        NavigationFix(lat,lon,acc,t,bearing,4.0,mock)

    @Test fun startedRouteHasNoInstructionsUntilFreshGps() {
        val n=ActiveNavigationEngine(graph)
        assertEquals(NavigationPhase.IDLE,n.current.phase)
        assertEquals(NavigationPhase.GPS_UNCERTAIN,n.start(plan).phase)
        assertEquals(NavigationPhase.ACTIVE,n.observe(gps(),1000).phase)
        assertTrue(n.current.remainingMeters!! in 150.0..210.0)
    }

    @Test fun progressionIsMonotonicAndShowsUpcomingGeometry() {
        val n=ActiveNavigationEngine(graph)
        n.start(plan)
        val first=n.observe(gps(),1000)
        val second=n.observe(gps(lon=106.0007,t=2000),2000)
        assertEquals(NavigationPhase.ACTIVE,second.phase)
        assertTrue(second.remainingMeters!! < first.remainingMeters!!)
        assertNotNull(second.nextManeuver)
        assertTrue(second.nextManeuverMeters!! >= 0.0)
    }

    @Test fun etaAppearsOnlyAfterTwoMovingProgressFixesAndDisappearsOnUnknown() {
        val n=ActiveNavigationEngine(graph)
        n.start(plan)
        val first=n.observe(gps(),1000)
        assertNull(first.etaSeconds)
        val second=n.observe(gps(lon=106.00065,t=2000),2000)
        assertEquals(NavigationPhase.ACTIVE,second.phase)
        assertNotNull(second.etaSeconds)
        assertTrue(second.etaSeconds!! in 1..200)
        assertEquals(0,second.nextManeuverIndex)
        assertEquals(1,second.totalManeuvers)
        assertNotNull(second.progressMeters)
        val uncertain=n.observe(gps(t=3000,acc=30.0),3000)
        assertNull(uncertain.etaSeconds)
        assertNull(uncertain.nextManeuver)
    }

    @Test fun staleInaccurateMockAndWrongHeadingFailClosed() {
        val n=ActiveNavigationEngine(graph)
        n.start(plan)
        assertEquals(NavigationPhase.GPS_UNCERTAIN,
            n.observe(gps(acc=34.0),1000).phase)
        assertEquals(NavigationPhase.GPS_UNCERTAIN,
            n.observe(gps(t=2000,mock=true),2000).phase)
        assertEquals(NavigationPhase.GPS_UNCERTAIN,
            n.observe(gps(t=3000,bearing=270.0),3000).phase)
        assertEquals(NavigationPhase.GPS_UNCERTAIN,
            n.observe(gps(t=4000),10_000).phase)
    }

    @Test fun threeDistinctOffRouteFixesBeforeRerouteProposal() {
        val n=ActiveNavigationEngine(graph)
        n.start(plan)
        n.observe(gps(),1000)
        // D lies far from the existing A->B->C route; a route from D->E->C exists.
        val far=gps(lon=106.001,lat=21.001,t=2000)
        assertEquals(NavigationPhase.POSSIBLY_OFF_ROUTE,n.observe(far,2000).phase)
        assertEquals(NavigationPhase.POSSIBLY_OFF_ROUTE,n.observe(far,2000).phase)
        assertEquals(NavigationPhase.POSSIBLY_OFF_ROUTE,
            n.observe(far.copy(elapsedMs=3000),3000).phase)
        val result=n.observe(far.copy(elapsedMs=4000),4000)
        assertEquals(NavigationPhase.OFF_ROUTE,result.phase)
        assertEquals(listOf("de","ec"),result.alternative?.edgeIds)
        assertEquals(NavigationPhase.OFF_ROUTE,n.acceptProposedRoute(false).phase)
        assertEquals(NavigationPhase.GPS_UNCERTAIN,n.acceptProposedRoute(true).phase)
        assertEquals(listOf("de","ec"),n.activeRoute?.edgeIds)
    }

    @Test fun offRouteWithoutDirectedAlternativeDoesNotInventARoute() {
        val graph=OfflineRouteGraph(nodes,edges,emptyList())
        val n=ActiveNavigationEngine(graph)
        n.start(RoutePlan(listOf("A","B"),listOf("ab"),105.0))
        n.observe(gps(),1000)
        n.observe(gps(lat=21.001,lon=106.002,t=2000),2000)
        n.observe(gps(lat=21.001,lon=106.002,t=3000),3000)
        val result=n.observe(gps(lat=21.001,lon=106.002,t=4000),4000)
        assertEquals(NavigationPhase.OFF_ROUTE,result.phase)
        assertNull(result.alternative)
    }

    @Test fun threeFreshNearDestinationFixesRequiredToArrive() {
        val n=ActiveNavigationEngine(graph)
        n.start(plan)
        n.observe(gps(lon=106.0009),1000)
        assertEquals(NavigationPhase.ACTIVE,n.observe(gps(lon=106.0017,t=2000),2000).phase)
        val near=gps(lon=106.00196,t=3000)
        assertEquals(NavigationPhase.ACTIVE,n.observe(near,3000).phase)
        assertEquals(NavigationPhase.ACTIVE,n.observe(near.copy(elapsedMs=4000),4000).phase)
        assertEquals(NavigationPhase.ARRIVED,n.observe(near.copy(elapsedMs=5000),5000).phase)
        assertEquals(NavigationPhase.ARRIVED,n.observe(near.copy(elapsedMs=6000),6000).phase)
    }

    @Test fun duplicateTimestampNotCountedAndPauseRevokesConfidence() {
        val n=ActiveNavigationEngine(graph)
        n.start(plan)
        val first=n.observe(gps(),1000)
        assertEquals(first,n.observe(gps(lon=106.0008),1000))
        assertEquals(NavigationPhase.GPS_UNCERTAIN,n.suspendForUncertainGps().phase)
        assertEquals(NavigationPhase.ACTIVE,n.observe(gps(t=2000),2000).phase)
        assertEquals(NavigationPhase.IDLE,n.stop().phase)
    }

    @Test fun unreviewedRouteCannotStart() {
        val invalid=OfflineRouteGraph(nodes,edges,emptyList())
        val n=ActiveNavigationEngine(invalid)
        try {
            n.start(RoutePlan(listOf("A","B","C"),listOf("ab","bc"),210.0))
            fail("missing reviewed turn")
        } catch (_:IllegalArgumentException) {}
    }
}
