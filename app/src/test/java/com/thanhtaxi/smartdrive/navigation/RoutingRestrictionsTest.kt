package com.thanhtaxi.smartdrive.navigation

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RoutingRestrictionsTest {
    private val a=RouteNode("A",21.0,106.0)
    private val b=RouteNode("B",21.0,106.001)
    private val c=RouteNode("C",21.0,106.002)
    private val d=RouteNode("D",21.001,106.001)
    private val all=listOf(a,b,c,d)
    private val edges=listOf(
        RouteEdge("ab","A","B","one",100.0,true),
        RouteEdge("bc","B","C","two",100.0,true),
        RouteEdge("ad","A","D","three",140.0,true),
        RouteEdge("dc","D","C","four",140.0,true))
    private val defaultTurns=listOf(
        AllowedTurn("ab","B","bc",true),
        AllowedTurn("ad","D","dc",true))
    private fun ctx(vehicle:RoutingVehicle=RoutingVehicle.TAXI,day:Int=1,
                    minute:Int=600)=RouteAccessContext(vehicle,day,minute)

    @Test fun taxiIsFilteredFromRestrictedEdgeAndCannotBypassByOmittingContext() {
        val restricted=edges.map { if(it.id=="bc")
            it.copy(access=SurveyAccessRule(setOf(RoutingVehicle.CAR),
                sourceRef="user-survey-01")) else it }
        val g=OfflineRouteGraph(all,restricted,defaultTurns)
        assertEquals(listOf("ad","dc"),g.route("A","C",ctx())?.edgeIds)
        assertEquals(listOf("ab","bc"),g.route("A","C",ctx(RoutingVehicle.CAR))?.edgeIds)
        assertEquals(listOf("ad","dc"),g.route("A","C")?.edgeIds)
        assertNull(g.route("B","C")) // no vehicle context => fail closed
        assertFalse(g.isEdgeAllowed("bc"))
    }

    @Test fun surveyedTurnBanByTimeIsAppliedOnlyAtReviewedJunction() {
        val rule=SurveyAccessRule(closures=listOf(SurveyClosure(
            setOf(1,2,3,4,5),420,540)),sourceRef="field-survey-morning")
        val turns=defaultTurns.map { if(it.fromEdge=="ab")it.copy(access=rule)else it }
        val g=OfflineRouteGraph(all,edges,turns)
        assertEquals(listOf("ad","dc"),g.route("A","C",ctx(minute=480))?.edgeIds)
        assertEquals(listOf("ab","bc"),g.route("A","C",ctx(minute=540))?.edgeIds)
        assertEquals(listOf("ab","bc"),g.route("A","C",ctx(day=7,minute=480))?.edgeIds)
        assertFalse(g.hasReviewedTurn("ab","bc"))
        assertFalse(g.isValidatedSegmentTransition("one","two"))
        assertEquals(listOf("ad","dc"),g.route("A","C")?.edgeIds)
        val forged=RoutePlan(listOf("A","B","C"),listOf("ab","bc"),200.0)
        try { RoutePreviewInstructions.generate(g,forged);fail("must reject missing context") }
        catch (_:IllegalArgumentException) {}
        val permitted=RoutePlan(forged.nodeIds,forged.edgeIds,forged.metres,ctx(minute=540))
        assertEquals(1,RoutePreviewInstructions.generate(g,permitted).size)
    }

    @Test fun overnightWindowsIncludePreviousDayAndMondayToTuesday() {
        val w=SurveyClosure(setOf(1),22*60,2*60)
        assertFalse(w.blocks(ctx(day=1,minute=21*60+59)))
        assertTrue(w.blocks(ctx(day=1,minute=22*60)))
        assertTrue(w.blocks(ctx(day=2,minute=60)))
        assertFalse(w.blocks(ctx(day=2,minute=120)))
        assertFalse(w.blocks(ctx(day=3,minute=60)))
        val sunday=SurveyClosure(setOf(7),22*60,60)
        assertTrue(sunday.blocks(ctx(day=1,minute=30)))
    }

    @Test fun invalidRestrictionsAndDuplicateTurnsRejected() {
        try { SurveyClosure(setOf(1),600,600);fail("bad window") }
        catch (_:IllegalArgumentException) {}
        try { SurveyClosure(setOf(8),600,700);fail("bad weekday") }
        catch (_:IllegalArgumentException) {}
        try { SurveyAccessRule(setOf(RoutingVehicle.TAXI));fail("no provenance") }
        catch (_:IllegalArgumentException) {}
        try { RouteAccessContext(RoutingVehicle.TAXI,1,1500);fail("bad minute") }
        catch (_:IllegalArgumentException) {}
        try { OfflineRouteGraph(all,edges,defaultTurns+defaultTurns.first());fail("duplicate turns") }
        catch (_:IllegalArgumentException) {}
        assertNull(OfflineRouteGraph(all,edges,defaultTurns).route("C","A",ctx()))
    }

    @Test fun timestampUsesVietnamTimezoneWithoutHostLocalTimezone() {
        val millis=LocalDateTime.of(2026,10,27,8,30).atZone(
            ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toEpochMilli()
        val context=RouteAccessContext.atVietnamTime(RoutingVehicle.TAXI,millis)
        assertEquals(2,context.dayOfWeek)
        assertEquals(510,context.minuteOfDay)
    }
}
