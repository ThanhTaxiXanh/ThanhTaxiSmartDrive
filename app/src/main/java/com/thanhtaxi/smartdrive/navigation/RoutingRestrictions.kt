package com.thanhtaxi.smartdrive.navigation

import java.time.Instant
import java.time.ZoneId

/** Survey metadata, NOT proof that a restriction is legally in force. */
enum class RoutingVehicle { TAXI, CAR, MOTORBIKE, BUS, TRUCK, OTHER }

/** ISO weekday (Monday=1), time of day in the declared Vietnam timezone. */
data class RouteAccessContext(
    val vehicle: RoutingVehicle,
    val dayOfWeek: Int,
    val minuteOfDay: Int,
    val lane: Int? = null
) {
    init {
        require(dayOfWeek in 1..7 && minuteOfDay in 0..1439 &&
            (lane==null || lane in 1..12)) { "Invalid day/time" }
    }
    companion object {
        fun atVietnamTime(vehicle: RoutingVehicle, epochMillis: Long,
                              lane: Int? = null): RouteAccessContext {
            require(epochMillis >= 0)
            val t=Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
            return RouteAccessContext(vehicle,t.dayOfWeek.value,t.hour*60+t.minute,lane)
        }
    }
}

/** Blocking window starts inclusive and ends exclusive, including overnight. */
data class SurveyClosure(
    val days: Set<Int>,
    val startMinute: Int,
    val endMinute: Int
) {
    init {
        require(days.isNotEmpty() && days.all { it in 1..7 })
        require(startMinute in 0..1439 && endMinute in 0..1439 &&
            startMinute!=endMinute) { "Invalid closed time window" }
    }
    fun blocks(ctx: RouteAccessContext): Boolean {
        if(startMinute<endMinute)
            return ctx.dayOfWeek in days &&
                ctx.minuteOfDay>=startMinute && ctx.minuteOfDay<endMinute
        val yesterday=if(ctx.dayOfWeek==1) 7 else ctx.dayOfWeek-1
        return (ctx.dayOfWeek in days && ctx.minuteOfDay>=startMinute) ||
            (yesterday in days && ctx.minuteOfDay<endMinute)
    }
}

/**
 * Optional restrictions on ONE directed edge or ONE reviewed edge-to-edge turn.
 * Absence means this synthetic survey graph has no *recorded* restriction,
 * never that driving is legally permitted. Conditional rules without context
 * fail closed, and sourceRef is a user-supplied reference, not certification.
 */
data class SurveyAccessRule(
    val allowedVehicles: Set<RoutingVehicle> = emptySet(),
    val closures: List<SurveyClosure> = emptyList(),
    val sourceRef: String? = null,
    val allowedLanes: Set<Int> = emptySet(),
    val blockedVehicles: Set<RoutingVehicle> = emptySet()
) {
    val laneScoped: Boolean get() = allowedLanes.isNotEmpty()
    val conditional: Boolean get() = allowedVehicles.isNotEmpty() || blockedVehicles.isNotEmpty() ||
        closures.isNotEmpty() || laneScoped
    init {
        require(closures.size<=32)
        require(allowedLanes.size<=12 && allowedLanes.all { it in 1..12 })
        require(allowedVehicles.intersect(blockedVehicles).isEmpty()) {
            "Conflicting allowed/blocked vehicle rule"
        }
        require(!conditional || !sourceRef.isNullOrBlank()) {
            "Conditional survey rule requires sourceRef"
        }
        require(sourceRef==null || sourceRef.length<=256)
    }
    fun permits(context: RouteAccessContext?): Boolean {
        if(!conditional) return true
        if(context==null) return false
        if(allowedVehicles.isNotEmpty() && context.vehicle !in allowedVehicles) return false
        if(context.vehicle in blockedVehicles) return false
        if(laneScoped && (context.lane==null || context.lane !in allowedLanes)) return false
        return closures.none { it.blocks(context) }
    }
}
