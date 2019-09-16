package groundWar.fogOfWar

import groundWar.*

/*
Map for each node and arc in a world....in fact , one does not need to keep track or arc visibility,
as this is defined by visibility to either of the terminal nodes!
 */
data class HistoricVisibility(val nodes: Map<Int, Pair<Int, Double>>) : Map<Int, Pair<Int, Double>> by nodes {

    fun lastVisible(node: Int): Int {
        return nodes.getOrDefault(node, Pair(0, 0.0)).first
    }

    fun lastKnownForce(node: Int): Double {
        return nodes.getOrDefault(node, Pair(0, 0.0)).second
    }

    fun updateNode(node: Int, time: Int, size: Double): HistoricVisibility {
        return HistoricVisibility(nodes + Pair(node, Pair(time, size)))
    }
}