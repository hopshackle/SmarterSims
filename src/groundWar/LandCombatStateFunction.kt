package groundWar

import ggi.ActionAbstractGameState
import ggi.StateSummarizer
import kotlin.math.roundToInt

object LandCombatStateFunction : StateSummarizer {
    override fun invoke(state: ActionAbstractGameState): String {
        if (state is LandCombatGame) {
            // features to use...
            // for each city: ownership, population
            // for each route/player: number of transits, total population of transits
            return with(StringBuilder()) {
                append(state.nTicks() / 50)
                append("|")
                state.world.cities.forEach {
                    append(playerIDToNumber(it.owner))
                    append(",")
                    append(it.pop.size.roundToInt())
                    append("|")
                }
                state.world.routes.forEach { arc ->
                    val transitsOnArcByPlayer = state.world.currentTransits.filter { t -> t.fromCity == arc.fromCity && t.toCity == arc.toCity }.groupBy { it.playerId }
                    append(transitsOnArcByPlayer[PlayerId.Blue]?.count() ?: 0)
                    append(",")
                    append(transitsOnArcByPlayer[PlayerId.Red]?.count() ?: 0)
                    append(",")
                    append(transitsOnArcByPlayer[PlayerId.Blue]?.sumByDouble{it.force.effectiveSize}?.roundToInt() ?: 0)
                    append(",")
                    append(transitsOnArcByPlayer[PlayerId.Red]?.sumByDouble{it.force.effectiveSize}?.roundToInt() ?: 0)
                    append("|")
                }
                this
            }.toString()
        }
        return ""
    }
}