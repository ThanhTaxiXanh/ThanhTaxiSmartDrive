package com.thanhtaxi.smartdrive

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import com.thanhtaxi.smartdrive.navigation.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Android OS instrumentation run on GitHub Actions emulator.
 * Fictitious graph and synthetic location fixes; NO public-road accuracy claims.
 */
@RunWith(AndroidJUnit4::class)
class NavigationEmulatorTest {
    private val graph = OfflineRouteGraph(
        listOf(
            RouteNode("A",21.0,106.0,"Điểm thử A"),
            RouteNode("B",21.0,106.001,"Điểm thử B"),
            RouteNode("C",21.0,106.002,"Điểm thử C")
        ),
        listOf(
            RouteEdge("ab","A","B","test-ab",105.0,true),
            RouteEdge("bc","B","C","test-bc",105.0,true)
        ),
        listOf(AllowedTurn("ab","B","bc",true))
    )

    @Test fun emulatorRunsActiveNavigationLifecycleOnAndroidRuntime() {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.thanhtaxi.smartdrive",target.packageName)
        val nav=ActiveNavigationEngine(graph)
        val route=graph.route("A","C")!!
        assertEquals(NavigationPhase.GPS_UNCERTAIN,nav.start(route).phase)
        val state=nav.observe(NavigationFix(21.0,106.0003,3.0,1000,90.0,4.0),1000)
        assertEquals(NavigationPhase.ACTIVE,state.phase)
        assertTrue(state.remainingMeters!!>0)
        assertEquals(NavigationPhase.GPS_UNCERTAIN,nav.suspendForUncertainGps().phase)
        assertEquals(NavigationPhase.IDLE,nav.stop().phase)
    }

    @Test fun testEnvironmentHasNoDependenceOnGrantedLocationPermission() {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val declared=target.packageManager.getPackageInfo(
            target.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions?.toList().orEmpty()
        assertTrue(declared.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        // Pure engine never asks Android for permission and is deterministic even
        // on a clean emulator without GNSS hardware/location permissions.
        val nav=ActiveNavigationEngine(graph)
        nav.start(graph.route("A","C")!!)
        assertEquals(NavigationPhase.GPS_UNCERTAIN,
            nav.observe(NavigationFix(21.0,106.0003,100.0,1000),1000).phase)
    }

    @Test fun etaAndVoicePromptPolicyRunInRealAndroidJvmWithSyntheticFixes() {
        val nav=ActiveNavigationEngine(graph)
        nav.start(graph.route("A","C")!!)
        val a=nav.observe(NavigationFix(21.0,106.0002,3.0,1000,90.0,7.0),1000)
        val b=nav.observe(NavigationFix(21.0,106.00045,3.0,2000,90.0,7.0),2000)
        assertNull(a.etaSeconds)
        assertNotNull(b.etaSeconds)
        val policy=GuidancePromptPolicy()
        assertNotNull(policy.next(b,"emulator-fixture"))
        assertNull(policy.next(b,"emulator-fixture"))
        assertEquals(NavigationPhase.GPS_UNCERTAIN,
            nav.observe(NavigationFix(21.0,106.00045,3.0,3000,90.0,7.0,true),3000).phase)
    }

    @Test fun offlineVietnameseTtsAdapterToleratesMissingLanguagePack() {
        // Doesn't assume a Vietnamese voice is installed on the emulator.
        // A non-network voice is required and absence must be safe.
        val speaker=LocalVietnameseGuidance(
            InstrumentationRegistry.getInstrumentation().targetContext)
        speaker.stop()
        speaker.speak("Thử nghiệm")
        speaker.close()
        assertFalse(speaker.available)
    }

    @Test fun importerEnforcesTaxiVehicleAndTimedSurveyClosuresOnEmulator() {
        // Fully synthetic import; no file persistence or device GPS.
        val json="""{
          "schema":1,"series":"emulator-fake","version":1,
          "title":"Fictitious survey","source":"Emulator test only","license":"TEST",
          "segments":[
            {"id":"r1","from":[21.0,106.0],"to":[21.0,106.001],
             "roadKind":"TWO_WAY_OR_SINGLE_LANE_ONEWAY","populationZone":"UNKNOWN",
             "reviewed":true,"sourceRef":"fake-survey-a"},
            {"id":"r2","from":[21.0,106.001],"to":[21.0,106.002],
             "roadKind":"TWO_WAY_OR_SINGLE_LANE_ONEWAY","populationZone":"UNKNOWN",
             "reviewed":true,"sourceRef":"fake-survey-b"}
          ],
          "routing":{
            "nodes":[{"id":"A","lat":21.0,"lon":106.0},
                     {"id":"B","lat":21.0,"lon":106.001},
                     {"id":"C","lat":21.0,"lon":106.002}],
            "edges":[
              {"id":"ab","segmentId":"r1","fromNode":"A","toNode":"B",
               "direction":"forward","distanceMeters":104.0,"reviewed":true,
               "sourceRef":"fake-edge-a"},
              {"id":"bc","segmentId":"r2","fromNode":"B","toNode":"C",
               "direction":"forward","distanceMeters":104.0,"reviewed":true,
               "sourceRef":"fake-edge-b","restrictions":{
                 "allowedVehicles":["TAXI"],
                 "blockedWindows":[{"days":[1,2,3,4,5],
                  "startMinute":420,"endMinute":540}]
               }}
            ],
            "allowedTurns":[{"fromEdge":"ab","viaNode":"B","toEdge":"bc",
                             "reviewed":true,"sourceRef":"fake-turn-b"}]
          }
        }"""
        val app=InstrumentationRegistry.getInstrumentation().targetContext
        val graph=com.thanhtaxi.smartdrive.data.RoadPackRepository(app)
            .parse(json).routeGraph!!
        assertNull(graph.route("A","C"))
        assertNull(graph.route("A","C",
            RouteAccessContext(RoutingVehicle.CAR,7,600)))
        assertNull(graph.route("A","C",
            RouteAccessContext(RoutingVehicle.TAXI,1,480)))
        val plan=graph.route("A","C",
            RouteAccessContext(RoutingVehicle.TAXI,1,540))
        assertEquals(listOf("ab","bc"),plan?.edgeIds)
        assertEquals(1,RoutePreviewInstructions.generate(graph,plan!!).size)
        try {
            com.thanhtaxi.smartdrive.data.RoadPackRepository(app).parse(
                json.replace("\"allowedVehicles\":[\"TAXI\"]",
                    "\"allowedVehicles\":[\"ILLEGAL_VALUE\"]"))
            fail("reject unsupported vehicle")
        } catch (_: IllegalArgumentException) {}
    }

    @Test fun mainDashboardCreatesWithoutRealGpsHardware() {
        // Validates the Android Activity, map widget and dashboard lifecycle on
        // the emulator; offline tiles and GPS measurement are tested separately.
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertNotNull(activity.findViewById<android.view.View>(
                    android.R.id.content))
            }
        }
    }
}
