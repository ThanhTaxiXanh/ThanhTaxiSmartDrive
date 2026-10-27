package com.thanhtaxi.smartdrive.navigation

import org.junit.Assert.*
import org.junit.Test

/**
 * Replay: deterministic fictitious Hanoi-region points. No real car or map
 * data; synthetic time & speed supplied directly to the pure domain engine.
 */
class ActiveNavigationReplayTest {
    private val nodes=listOf(
        RouteNode("A",21.0,106.0),
        RouteNode("B",21.0,106.001),
        RouteNode("C",21.0,106.002),
        RouteNode("D",21.001,106.001),
        RouteNode("E",21.001,106.002))
    private val edges=listOf(
        RouteEdge("ab","A","B","s-a",105.0,true),
        RouteEdge("bc","B","C","s-b",105.0,true),
        RouteEdge("de","D","E","s-c",105.0,true),
        RouteEdge("ec","E","C","s-d",111.0,true))
    private val graph=OfflineRouteGraph(nodes,edges,listOf(
        AllowedTurn("ab","B","bc",true),AllowedTurn("de","E","ec",true)))
    private val route=graph.route("A","C")!!
    private fun fix(t:Long,lon:Double,lat:Double=21.0,bearing:Double=90.0,
                    speed:Double?=7.0,acc:Double=3.0):NavigationFix =
        NavigationFix(lat,lon,acc,t,bearing,speed)

    @Test fun replayFromRouteStartToArrivalThenStop() {
        val engine=ActiveNavigationEngine(graph)
        val speaker=GuidancePromptPolicy()
        engine.start(route)
        val first=engine.observe(fix(1000,106.00020),1000)
        assertEquals(NavigationPhase.ACTIVE,first.phase)
        assertNull(first.etaSeconds)
        assertNull(speaker.next(first,"abc"))
        val second=engine.observe(fix(2000,106.00045),2000)
        assertEquals(NavigationPhase.ACTIVE,second.phase)
        assertNotNull(second.etaSeconds)
        assertEquals(0,second.nextManeuverIndex)
        assertEquals(1,second.totalManeuvers)
        assertTrue(second.progressMeters!! > first.progressMeters!!)
        assertNotNull(speaker.next(second,"abc"))
        assertNull(speaker.next(second,"abc"))
        val third=engine.observe(fix(3000,106.00076),3000)
        assertTrue(third.nextManeuverMeters!!<40.0)
        assertNotNull(speaker.next(third,"abc"))
        assertNull(speaker.next(third,"abc"))

        // Further synthetic samples approach the end of the second edge.
        engine.observe(fix(4000,106.00155),4000)
        engine.observe(fix(5000,106.00193),5000)
        engine.observe(fix(6000,106.00196),6000)
        val end=engine.observe(fix(7000,106.00197),7000)
        assertEquals(NavigationPhase.ARRIVED,end.phase)
        assertEquals(0.0,end.remainingMeters!!,0.01)
        assertNull(speaker.next(end,"abc"))
        assertEquals(NavigationPhase.IDLE,engine.stop().phase)
    }

    @Test fun replayStaleGpsStopsVoiceAndClearsEstimatedEta() {
        val engine=ActiveNavigationEngine(graph)
        val speaker=GuidancePromptPolicy()
        engine.start(route)
        engine.observe(fix(1000,106.00025),1000)
        val active=engine.observe(fix(2000,106.00045),2000)
        assertNotNull(active.etaSeconds)
        assertNotNull(speaker.next(active,"abc"))
        val bad=engine.observe(fix(3000,106.00055).copy(isMock=true),3000)
        assertEquals(NavigationPhase.GPS_UNCERTAIN,bad.phase)
        assertNull(bad.etaSeconds)
        assertNull(speaker.next(bad,"abc"))
        assertEquals(NavigationPhase.GPS_UNCERTAIN,engine.current.phase)
    }

    @Test fun userMustExplicitlyAcceptAlternativeWhileStationary() {
        val engine=ActiveNavigationEngine(graph)
        engine.start(route)
        engine.observe(fix(1000,106.00025),1000)
        val far=fix(2000,106.001,21.001)
        assertEquals(NavigationPhase.POSSIBLY_OFF_ROUTE,engine.observe(far,2000).phase)
        engine.observe(far.copy(elapsedMs=3000),3000)
        val deviation=engine.observe(far.copy(elapsedMs=4000),4000)
        assertEquals(NavigationPhase.OFF_ROUTE,deviation.phase)
        assertEquals(listOf("de","ec"),deviation.alternative?.edgeIds)
        assertEquals(listOf("ab","bc"),engine.activeRoute?.edgeIds)
        engine.acceptProposedRoute(parkedConfirmed=false)
        assertEquals(listOf("ab","bc"),engine.activeRoute?.edgeIds)
        engine.acceptProposedRoute(parkedConfirmed=true)
        assertEquals(listOf("de","ec"),engine.activeRoute?.edgeIds)
        assertEquals(NavigationPhase.GPS_UNCERTAIN,engine.current.phase)
    }
}
