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
