package com.thanhtaxi.smartdrive.domain

import com.thanhtaxi.smartdrive.navigation.OfflineRouteGraph

/**
 * Conservative gate for switching among previously reviewed GPS road segments.
 * When a reviewed routing graph exists, any cross-segment transition needs an
 * explicit reviewed directed turn. Without graph data, GPS stabilization still
 * applies but topology validation is not claimed.
 */
class RoadTopologyGate(private var graph: OfflineRouteGraph? = null) {
    private var lastAcceptedId: String? = null
    var rejectedTransition: Boolean = false
        private set
    fun reset(updatedGraph: OfflineRouteGraph? = graph) {
        graph=updatedGraph
        lastAcceptedId=null
        rejectedTransition=false
    }
    fun accept(stableMatch: RoadMatch?): RoadMatch? {
        if(stableMatch==null) return null
        val prior=lastAcceptedId
        if(prior!=null && prior != stableMatch.segmentId &&
            graph != null && !graph!!.isValidatedSegmentTransition(prior,stableMatch.segmentId)) {
            rejectedTransition=true
            return null
        }
        lastAcceptedId=stableMatch.segmentId
        rejectedTransition=false
        return stableMatch
    }
}
