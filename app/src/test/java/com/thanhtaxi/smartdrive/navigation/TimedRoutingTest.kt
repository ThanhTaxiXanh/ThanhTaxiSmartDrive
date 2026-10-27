package com.thanhtaxi.smartdrive.navigation

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TimedRoutingTest {
    private val nodes=listOf(RouteNode("A",21.0,106.0),RouteNode("B",21.0,106.001),
        RouteNode("C",21.0,106.002),RouteNode("D",21.001,106.001))
    private val ban=SurveyAccessRule(closures=listOf(
        SurveyClosure(setOf(2),7*60,9*60)),sourceRef="synthetic-ban")
    private val edges=listOf(
        RouteEdge("ab","A","B","ab",100.0,true),
        RouteEdge("bc","B","C","bc",100.0,true,ban),
        RouteEdge("ad","A","D","ad",140.0,true),
        RouteEdge("dc","D","C","dc",140.0,true))
    private val turns=listOf(AllowedTurn("ab","B","bc",true),
        AllowedTurn("ad","D","dc",true))
    private val g=OfflineRouteGraph(nodes,edges,turns)
    private fun time(h:Int,m:Int,s:Int=0):Long =
        LocalDateTime.of(2026,10,27,h,m,s).atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
            .toInstant().toEpochMilli()

    @Test fun entryToSecondEdgeOccursAfterRestrictionBeginsSoDetourIsUsed() {
        val trip=g.routeTimed("A","C",RoutingVehicle.TAXI,time(6,59,50))!!
        assertEquals(listOf("ad","dc"),trip.edgeIds)
        assertEquals(2,trip.schedule.size)
        assertTrue(g.isScheduledPlanValid(trip))
    }

    @Test fun enteringSecondEdgeAfterBanExpiresPermitsShorterTrip() {
        val departure=time(8,59,50)
        val trip=g.routeTimed("A","C",RoutingVehicle.TAXI,departure)!!
        assertEquals(listOf("ab","bc"),trip.edgeIds)
        assertEquals(departure,trip.schedule[0].enterEpochMs)
        assertEquals(trip.schedule[0].exitEpochMs,trip.schedule[1].enterEpochMs)
        assertTrue(trip.schedule[1].enterEpochMs>=time(9,0,0))
        assertTrue(g.isScheduledPlanValid(trip))
        assertEquals(1,RoutePreviewInstructions.generate(g,trip).size)
    }

    @Test fun cannotCrossAClosureStartingMidEdgeEvenIfEnteredBeforeBan() {
        val g2=OfflineRouteGraph(nodes,edges.map { if(it.id=="ab")
            it.copy(access=ban,metres=1000.0,assumedSpeedKph=10.0) else it },turns)
        assertFalse(g2.isEdgeAvailableDuring("ab",RoutingVehicle.TAXI,null,
            time(6,59),time(7,5)))
        assertEquals(listOf("ad","dc"),
            g2.routeTimed("A","C",RoutingVehicle.TAXI,time(6,59))!!.edgeIds)
    }

    @Test fun laneUnknownAndExcludedVehiclesCauseAlternativeOrNoRoute() {
        val restrictions=SurveyAccessRule(
            allowedLanes=setOf(2),blockedVehicles=setOf(RoutingVehicle.TRUCK),
            sourceRef="synthetic-lane")
        val lg=OfflineRouteGraph(nodes,edges.map { if(it.id=="bc")
            it.copy(access=restrictions) else it },turns)
        val depart=time(10,0)
        assertEquals(listOf("ad","dc"),
            lg.routeTimed("A","C",RoutingVehicle.TAXI,depart)!!.edgeIds)
        assertEquals(listOf("ad","dc"),
            lg.routeTimed("A","C",RoutingVehicle.TRUCK,depart,2)!!.edgeIds)
        assertEquals(listOf("ab","bc"),
            lg.routeTimed("A","C",RoutingVehicle.TAXI,depart,2)!!.edgeIds)
        assertEquals(listOf("ad","dc"),
            lg.routeTimed("A","C",RoutingVehicle.TAXI,depart,1)!!.edgeIds)
        val preview=lg.routeTimed("A","C",RoutingVehicle.TAXI,depart,2)!!
        assertTrue(lg.hasLaneScopedRules(preview))
        assertEquals(NavigationPhase.GPS_UNCERTAIN,
            ActiveNavigationEngine(lg).also { it.start(preview) }.observe(
                NavigationFix(21.0,106.0003,3.0,1000,90.0,4.0),1000,depart).phase)
    }

    @Test fun modeledSpeedChangesEntryTimeAndCanChangeSelectedRoute() {
        val speedG=OfflineRouteGraph(nodes,edges.map { if(it.id=="ab")
            it.copy(assumedSpeedKph=20.0) else it },turns)
        // At 08:59:50, the second edge opens at 09:00; a slow first edge
        // delays arrival sufficiently to make short route available.
        val route=speedG.routeTimed("A","C",RoutingVehicle.TAXI,time(8,59,50))!!
        assertEquals(listOf("ab","bc"),route.edgeIds)
        assertEquals(20.0,route.schedule.first().modelSpeedKph,0.01)
        assertTrue(route.schedule[0].exitEpochMs>time(9,0,0))
    }

    @Test fun malformedSpeedOrMissingContextFailsClosed() {
        assertNull(g.routeTimed("A","C",RoutingVehicle.TAXI,-1))
        assertNull(g.routeTimed("A","C",RoutingVehicle.TAXI,time(10,0),
            defaultModelSpeedKph=0.0))
        val timed=g.routeTimed("A","C",RoutingVehicle.TAXI,time(10,0))!!
        assertFalse(g.isScheduledPlanValid(timed.copy(schedule=timed.schedule.dropLast(1))))
        assertFalse(g.isScheduledPlanValid(timed.copy(
            schedule=timed.schedule.map { it.copy(enterEpochMs=it.enterEpochMs+60_000) })))
    }
}
