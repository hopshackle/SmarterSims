package groundWar

import ggi.Action
import ggi.ActionAbstractGameState
import kotlin.math.min

data class TransitStart(val transit: Transit) : Action {
    override fun apply(state: ActionAbstractGameState): Int {
        if (state is LandCombatGame) {
            val world = state.world
            val city = world.cities[transit.fromCity]
            if (city.owner == transit.playerId) {
                if (city.pop < transit.nPeople)
                    throw AssertionError("Invalid Transit - maximum force move is limited to city population")
                city.pop -= transit.nPeople
            } else {
                throw AssertionError("Invalid Transit - must be from city owned by playerId")
            }
            val enemyCollision = world.nextCollidingTransit(transit, state.nTicks())
            world.addTransit(transit)
            if (enemyCollision != null) {
                state.eventQueue.add(transit.collisionEvent(enemyCollision, world, state.nTicks()))
            }
        }
        return -1
    }

    override fun visibleTo(player: Int, state: ActionAbstractGameState) = if (state is LandCombatGame) state.world.checkVisible(transit, numberToPlayerID(player)) else true
}

data class TransitEnd(val player: PlayerId, val fromCity: Int, val toCity: Int, val endTime: Int) : Action {
    override fun apply(state: ActionAbstractGameState): Int {
        if (state is LandCombatGame) {
            val transit = getTransit(state)
            if (transit != null) {
                CityInflux(player, transit.nPeople, toCity, fromCity).apply(state)
                state.world.removeTransit(transit)
            }
        }
        return -1
    }

    override fun visibleTo(player: Int, state: ActionAbstractGameState): Boolean {
        if (state is LandCombatGame) {
            val transit = getTransit(state)
            return transit != null && state.world.checkVisible(transit, numberToPlayerID(player))
        }
        return true
    }

    private fun getTransit(state: LandCombatGame): Transit? {
        return state.world.currentTransits.filter {
            it.endTime == endTime && it.playerId == player && it.fromCity == fromCity && it.toCity == toCity
        }.firstOrNull()
    }
}

data class CityInflux(val player: PlayerId, val pop: Double, val destination: Int, val origin: Int = -1) : Action {
    override fun apply(state: ActionAbstractGameState): Int {
        if (state is LandCombatGame) {
            val world = state.world
            val city = world.cities[destination]
            if (city.owner == player) {
                city.pop += pop
            } else {
                val p = world.params
                val playerRef = playerIDToNumber(player)
                val result = lanchesterClosedFormBattle(pop, city.pop,
                        p.lanchesterCoeff[playerRef] / if (city.fort) p.fortAttackerDivisor else 1.0,
                        p.lanchesterExp[playerRef],
                        p.lanchesterCoeff[1 - playerRef],
                        p.lanchesterExp[1 - playerRef] + if (city.fort) p.fortDefenderExpBonus else 0.0
                )
                if (result > 0.0) {
                    // attackers win
                    city.owner = player
                    city.pop = result
                } else {
                    // defenders win
                    city.pop = -result
                }
            }
        }
        return -1
    }

    override fun visibleTo(player: Int, state: ActionAbstractGameState): Boolean {
        // we can't just check the destination city...as what matters is where the force is coming *from*
        // i.e. which Route they are travelling on
        val playerId = numberToPlayerID(player)
        if (state is LandCombatGame)
            with(state.world) {
                if (origin == -1) return this@CityInflux.player == playerId || checkVisible(destination, playerId)
                return checkVisible(Transit(0.0, origin, destination, this@CityInflux.player, 0, 0), playerId)
                // If we could see a Transit by another player on that route, then we can see the CityInflux
            }
        return true
    }
}

data class Battle(val transit1: Transit, val transit2: Transit) : Action {
    override fun apply(state: ActionAbstractGameState): Int {
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
            if (Math.abs(result).toInt() == 0) {
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
        return -1
    }

    override fun visibleTo(player: Int, state: ActionAbstractGameState): Boolean {
        if (state is LandCombatGame) {
            val playerId = numberToPlayerID(player)
            return state.world.checkVisible(transit1, playerId) && state.world.checkVisible(transit2, playerId)
        }
        return true
    }
}

data class LaunchExpedition(val player: PlayerId, val from: Int, val toCode: Int, val proportion: Double, val wait: Int) : Action {

    fun forcesSent(state: ActionAbstractGameState): Double {
        if (state is LandCombatGame) {
            with(state.world) {
                val sourceCityPop = cities[from].pop
                var forcesSent = proportion * sourceCityPop
                if (forcesSent < 1.0) forcesSent = min(1.0, sourceCityPop)
                return forcesSent
            }
        }
        return -1.0
    }

    override fun apply(state: ActionAbstractGameState): Int {
        if (state is LandCombatGame) {
            val world = state.world
            val to = destinationCity(state)
            if (isValid(state)) {
                val distance = world.cities[from].location.distanceTo(world.cities[to].location)
                val arrivalTime = state.nTicks() + (distance / world.params.speed[playerIDToNumber(player)]).toInt()
                val forcesSent = forcesSent(state)
                val transit = Transit(forcesSent, from, to, player, state.nTicks(), arrivalTime)
                // we execute the troop departure immediately
                TransitStart(transit).apply(state)
                // and put their arrival in the queue for the game state
                state.eventQueue.add(Event(arrivalTime, TransitEnd(transit.playerId, transit.fromCity, transit.toCity, transit.endTime)))
            }
        }
        return state.nTicks() + wait
    }

    fun destinationCity(state: LandCombatGame): Int {
        val routes = state.world.allRoutesFromCity[from] ?: emptyList()
        if (routes.isEmpty())
            throw AssertionError("Should not be empty")
        return routes[toCode % routes.size].toCity
    }

    fun isValid(state: LandCombatGame): Boolean {
        val to = destinationCity(state)
        return state.world.cities[from].owner == player &&
                state.world.cities[from].pop > 0 &&
                from != to &&
                meetsMinStrengthCriterion(state)
    }

    fun meetsMinStrengthCriterion(state: LandCombatGame): Boolean {
        val targetCity = destinationCity(state)
        val forceStrength = forcesSent(state)

        with(state.world) {
            if (cities[targetCity].owner == player) return true
            if (cities[targetCity].pop > forceStrength / params.minAssaultFactor[playerIDToNumber(player)])
                return false
            val forcesOnArc = currentTransits
                    .filter { it.fromCity == targetCity && it.toCity == from && it.playerId != player }
                    .sumByDouble(Transit::nPeople)
            if (forcesOnArc > forceStrength / params.minAssaultFactor[playerIDToNumber(player)])
                return false
        }
        return true
    }


    // only visible to planning player
    override fun visibleTo(player: Int, state: ActionAbstractGameState) = player == playerIDToNumber(this.player)
}
