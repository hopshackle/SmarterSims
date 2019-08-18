package groundWar

import ggi.Action
import ggi.ActionAbstractGameState
import kotlin.math.abs
import kotlin.math.min


data class TransitStart(val transit: Transit) : Action {
    override val player = playerIDToNumber(transit.playerId)

    override fun apply(state: ActionAbstractGameState) {
        if (state is LandCombatGame) {
            val world = state.world
            val city = world.cities[transit.fromCity]
            if (city.owner != transit.playerId)
                return // do nothing
            val actualTransit = if (city.pop < transit.nPeople) {
                transit.copy(city.pop)
            } else {
                transit
            }
            city.pop -= actualTransit.nPeople
            val enemyCollision = world.nextCollidingTransit(actualTransit, state.nTicks())
            world.addTransit(actualTransit)
            if (enemyCollision != null) {
                state.eventQueue.add(actualTransit.collisionEvent(enemyCollision, world, state.nTicks()))
            }
        }
    }

    override fun visibleTo(player: Int, state: ActionAbstractGameState): Boolean {
        return if (state is LandCombatGame) state.world.checkVisible(transit, numberToPlayerID(player)) else true
    }
}

data class TransitEnd(val playerId: PlayerId, val fromCity: Int, val toCity: Int, val endTime: Int) : Action {
    override val player = playerIDToNumber(playerId)

    override fun apply(state: ActionAbstractGameState) {
        if (state is LandCombatGame) {
            val transit = getTransit(state)
            if (transit != null) {
                CityInflux(playerId, transit.nPeople, toCity, fromCity).apply(state)
                state.world.removeTransit(transit)
            }
        }
    }

    override fun visibleTo(player: Int, state: ActionAbstractGameState): Boolean {
        if (state is LandCombatGame) {
            return state.world.checkVisible(Transit(0.0, fromCity, toCity, playerId, 0, endTime), numberToPlayerID(player))
        }
        return true
    }


    private fun getTransit(state: LandCombatGame): Transit? {
        return state.world.currentTransits.firstOrNull {
            it.endTime == endTime && it.playerId == playerId && it.fromCity == fromCity && it.toCity == toCity
        }
    }
}

data class CityInflux(val playerId: PlayerId, val pop: Double, val destination: Int, val origin: Int = -1) : Action {
    override val player = playerIDToNumber(playerId)

    override fun apply(state: ActionAbstractGameState) {
        if (state is LandCombatGame) {
            val world = state.world
            val city = world.cities[destination]
            if (city.owner == playerId) {
                city.pop += pop
            } else {
                val p = world.params
                val result = lanchesterClosedFormBattle(pop, city.pop,
                        p.lanchesterCoeff[player] / if (city.fort) p.fortAttackerDivisor else 1.0,
                        p.lanchesterExp[player],
                        p.lanchesterCoeff[1 - player],
                        p.lanchesterExp[1 - player] + if (city.fort) p.fortDefenderExpBonus else 0.0
                )
                if (result > 0.0) {
                    // attackers win
                    city.owner = playerId
                    city.pop = result
                } else {
                    // defenders win
                    city.pop = -result
                }
            }
        }
    }

    override fun visibleTo(player: Int, state: ActionAbstractGameState): Boolean {
        // we can't just check the destination city...as what matters is where the force is coming *from*
        // i.e. which Route they are travelling on
        val playerId = numberToPlayerID(player)
        if (state is LandCombatGame)
            with(state.world) {
                if (origin == -1) return this@CityInflux.playerId == playerId || checkVisible(destination, playerId)
                return checkVisible(Transit(0.0, origin, destination, this@CityInflux.playerId, 0, 0), playerId)
                // If we could see a Transit by another player on that route, then we can see the CityInflux
            }
        return true
    }
}

data class Battle(val transit1: Transit, val transit2: Transit) : Action {
    override val player = -1
    override fun apply(state: ActionAbstractGameState) {
        if (state is LandCombatGame) {
            val p = state.world.params
            val playerRef = playerIDToNumber(transit1.playerId)
            val result = lanchesterClosedFormBattle(transit1.nPeople, transit2.nPeople,
                    p.lanchesterCoeff[playerRef],
                    p.lanchesterExp[playerRef],
                    p.lanchesterCoeff[1 - playerRef],
                    p.lanchesterExp[1 - playerRef]
            )
            val winningTransit = if (result > 0.0) transit1 else transit2
            val losingTransit = if (result > 0.0) transit2 else transit1

            state.world.removeTransit(losingTransit)
            state.world.removeTransit(winningTransit)
            if (abs(result).toInt() == 0) {
                // do nothing
            } else {
                val successorTransit = winningTransit.copy(nPeople = Math.abs(result));
                state.world.addTransit(successorTransit)
                val nextCollidingTransit = state.world.nextCollidingTransit(successorTransit, state.nTicks())
                if (nextCollidingTransit != null) {
                    state.eventQueue.add(successorTransit.collisionEvent(nextCollidingTransit, state.world, state.nTicks()))
                }
            }
        }
    }

    override fun visibleTo(player: Int, state: ActionAbstractGameState): Boolean {
        if (state is LandCombatGame) {
            val playerId = numberToPlayerID(player)
            return state.world.checkVisible(transit1, playerId) && state.world.checkVisible(transit2, playerId)
        }
        return true
    }
}

data class LaunchExpedition(val playerId: PlayerId, val origin: Int, val destination: Int, val proportion: Double, val wait: Int) : Action {
    override val player = playerIDToNumber(playerId)

    fun forcesSent(state: ActionAbstractGameState): Double {
        if (state is LandCombatGame) {
            with(state.world) {
                val sourceCityPop = cities[origin].pop
                var forcesSent = proportion * sourceCityPop
                if (forcesSent < 1.0) forcesSent = min(1.0, sourceCityPop)
                return forcesSent
            }
        }
        return -1.0
    }

    override fun apply(state: ActionAbstractGameState) {
        if (state is LandCombatGame) {
            val world = state.world
            if (isValid(state)) {
                val distance = world.cities[origin].location.distanceTo(world.cities[destination].location)
                val delayTime = state.world.params.orderDelay[player]
                val arrivalTime = state.nTicks() + (distance / world.params.speed[player]).toInt() + delayTime
                val startTime = state.nTicks() + delayTime
                val forcesSent = forcesSent(state)
                val transit = Transit(forcesSent, origin, destination, playerId, startTime, arrivalTime)
                // we execute the troop departure immediately (with delay)
                state.eventQueue.add(Event(startTime, TransitStart(transit)))
                // and put their arrival in the queue for the game state
                state.eventQueue.add(Event(arrivalTime, TransitEnd(transit.playerId, transit.fromCity, transit.toCity, transit.endTime)))
            }
        }
    }

    override fun nextDecisionPoint(player: Int, state: ActionAbstractGameState): Pair<Int, Int> {
        val minTime = state.nTicks() + wait
        return return Pair(minTime, minTime)
    }

    fun isValid(state: LandCombatGame): Boolean {
        return state.world.cities[origin].owner == playerId &&
                state.world.cities[origin].pop > 0 &&
                origin != destination &&
                state.world.allRoutesFromCity.getOrDefault(origin, emptyList()).any { r -> r.toCity == destination } &&
                meetsMinStrengthCriterion(state)
    }

    fun meetsMinStrengthCriterion(state: LandCombatGame): Boolean {
        val forceStrength = forcesSent(state)

        with(state.world) {
            if (cities[destination].owner == playerId) return true
            if (cities[destination].pop > forceStrength / params.minAssaultFactor[player])
                return false
            val forcesOnArc = currentTransits
                    .filter { it.fromCity == destination && it.toCity == origin && it.playerId != playerId }
                    .sumByDouble(Transit::nPeople)
            if (forcesOnArc > forceStrength / params.minAssaultFactor[player])
                return false
        }
        return true
    }

    // only visible to planning player
    override fun visibleTo(player: Int, state: ActionAbstractGameState) = player == playerIDToNumber(playerId)
}
