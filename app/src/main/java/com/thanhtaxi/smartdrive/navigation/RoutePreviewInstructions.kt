package com.thanhtaxi.smartdrive.navigation

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class GeometryChange { STRAIGHT, LEFT, RIGHT, SHARP_LEFT, SHARP_RIGHT, REVERSE }

/**
 * An explanation of the surveyed route graph for a PARKED preview.
 * It is not a driving command, and contains no legal turn-authorisation claim.
 */
data class RoutePreviewStep(
    val atNodeId: String,
    val metresFromStart: Double,
    val change: GeometryChange,
    val text: String
)

object RoutePreviewInstructions {
    fun geometryChange(previous: RouteNode, junction: RouteNode, following: RouteNode): GeometryChange {
        val entering = heading(previous, junction)
        val leaving = heading(junction, following)
        var delta = (leaving-entering+540.0)%360.0-180.0
        if (delta == -180.0) delta=180.0
        return when {
            kotlin.math.abs(delta)<30.0 -> GeometryChange.STRAIGHT
            kotlin.math.abs(delta)>=150.0 -> GeometryChange.REVERSE
            delta >= 75.0 -> GeometryChange.RIGHT
            delta <= -75.0 -> GeometryChange.LEFT
            delta > 0 -> GeometryChange.SHARP_RIGHT // gentle right; text avoids a turn command
            else -> GeometryChange.SHARP_LEFT
        }
    }

    fun generate(graph: OfflineRouteGraph, plan: RoutePlan): List<RoutePreviewStep> {
        require(plan.nodeIds.size == plan.edgeIds.size+1) { "Route node/edge lengths differ" }
        require(plan.metres.isFinite() && plan.metres>=0.0)
        require(plan.nodeIds.all { it in graph.nodes })
        if(plan.edgeIds.isEmpty()) return emptyList()
        val edges=plan.edgeIds.map { id -> requireNotNull(graph.edge(id)) { "Unknown route edge" } }
        for (i in edges.indices) {
            val e=edges[i]
            require(graph.isEdgeAllowed(e.id,plan.accessContext) &&
                e.from==plan.nodeIds[i] && e.to==plan.nodeIds[i+1]) {
                "Route edge is not reviewed or does not connect the claimed nodes"
            }
            if (i>0) require(graph.hasReviewedTurn(
                edges[i-1].id,e.id,plan.accessContext)) {
                "No reviewed connection between surveyed segments"
            }
        }
        val steps=ArrayList<RoutePreviewStep>()
        var distance=0.0
        for (i in 1 until plan.nodeIds.lastIndex) {
            distance+=edges[i-1].metres
            val a=graph.nodes.getValue(plan.nodeIds[i-1])
            val b=graph.nodes.getValue(plan.nodeIds[i])
            val c=graph.nodes.getValue(plan.nodeIds[i+1])
            val geometry=geometryChange(a,b,c)
            val description=when(geometry) {
                GeometryChange.STRAIGHT -> "Hình học gần như đi thẳng tại nút khảo sát"
                GeometryChange.LEFT -> "Hình học đổi hướng sang trái tại nút khảo sát"
                GeometryChange.RIGHT -> "Hình học đổi hướng sang phải tại nút khảo sát"
                GeometryChange.SHARP_LEFT -> "Hình học chếch trái tại nút khảo sát"
                GeometryChange.SHARP_RIGHT -> "Hình học chếch phải tại nút khảo sát"
                GeometryChange.REVERSE -> "Hình học đổi hướng gần 180° — cần kiểm tra thực địa"
            }
            steps.add(RoutePreviewStep(b.id,distance,geometry,description))
        }
        return steps
    }

    private fun heading(a: RouteNode,b: RouteNode): Double {
        val latA=Math.toRadians(a.lat)
        val latB=Math.toRadians(b.lat)
        val lon=Math.toRadians(b.lon-a.lon)
        return (Math.toDegrees(atan2(sin(lon)*cos(latB),
            cos(latA)*sin(latB)-sin(latA)*cos(latB)*cos(lon)))+360.0)%360.0
    }

    fun asParkedText(graph: OfflineRouteGraph, plan: RoutePlan): String {
        val steps=generate(graph,plan)
        val items=ArrayList<String>()
        items.add("XEM TRƯỚC HÌNH HỌC — KHÔNG PHẢI CHỈ DẪN LÁI XE")
        items.add("Tổng chiều dài trong dữ liệu: %.2f km".format(java.util.Locale.ROOT,plan.metres/1000.0))
        if (steps.isEmpty()) items.add("Không có lượt chuyển hướng trung gian.")
        steps.forEachIndexed { i,step ->
            items.add("${i+1}. Nút ${step.atNodeId} (sau %.0f m): ${step.text}"
                .format(java.util.Locale.ROOT,step.metresFromStart))
        }
        items.add("Lượt rẽ chỉ là dữ liệu do người nhập đánh dấu reviewed. " +
            "Chưa có biển cấm, làn, cầu vượt, giờ cấm hoặc kiểm chứng pháp lý.")
        return items.joinToString("\n\n")
    }
}
