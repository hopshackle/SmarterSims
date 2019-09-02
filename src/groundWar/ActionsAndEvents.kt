package groundWar

import ggi.Action
import ggi.ActionAbstractGameState
import groundWar.fatigue.*
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
            val actualTransit = if (city.pop.size < transit.force.size) {
                transit.copy(force = transit.force.copy(city.pop.size))
            } else {
                transit
            }
            city.pop = city.pop.copy(size = city.pop.size - actualTransit.force.size)
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
            val playerRef = playerIDToNumber(playerId)
            val transit = getTransit(state)
            if (transit != null) {
                val arrivingForce = state.fatigueModels[playerRef].move(state.nTicks(), transit.force)
                CityInflux(playerId, arrivingForce, toCity, fromCity).apply(state)
                state.world.removeTransit(transit)
            }
        }
    }

    override fun visibleTo(player: Int, state: ActionAbstractGameState): Boolean {
        if (state is LandCombatGame) {
            return state.world.checkVisible(Transit(Force(0.0), fromCity, toCity, playerId, 0, endTime), numberToPlayerID(player))
        }
        return true
    }


    private fun getTransit(state: LandCombatGame): Transit? {
        return state.world.currentTransits.firstOrNull {
            it.endTime == endTime && it.playerId == playerId && it.fromCity == fromCity && it.toCity == toCity
        }
    }
}

data class CityInflux(val playerId: PlayerId, val pop: Force, val destination: Int, val origin: Int = -1) : Action {
    override val player = playerIDToNumber(playerId)

    override fun apply(state: ActionAbstractGameState) {
        if (state is LandCombatGame) {
            val world = state.world
            val city = world.cities[destination]
            city.pop = state.fatigueModels[player].rest(state.nTicks(), city.pop)
            if (city.owner == playerId) {
                city.pop = city.pop + pop
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
                    city.pop = pop.copy(size = result)
                } else {
                    // defenders win
                    city.pop = city.pop.copy(size = -result)
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
                return checkVisible(Transit(Force(0.0), origin, destination, this@CityInflux.playerId, 0, 0), playerId)
                // If we could see a Transit by another player on that route, then we can see the CityInflux
            }
        return true
    }
}

data class Battle(val transit1: Transit, val transit2: Transit) : Action {
    override val player = -1

    fun combatForce(side: Int, state: LandCombatGame): Force {
        val baseTransit = when (side) {
            1 -> transit1
            2 -> transit2
            else -> throw AssertionError("Only 2 sides supported")
        }
        val fatigueModel = state.fatigueModels[playerIDToNumber(baseTransit.playerId)]
        return fatigueModel.move(state.nTicks(), baseTransit.force)
    }
    override fun apply(state: ActionAbstractGameState) {
        if (state is LandCombatGame) {
            val p = state.world.params
            val playerRef = playerIDToNumber(transit1.playerId)
            // we first need to apply fatigue effects of travelling up to this point before we calculate the results of the battle
            val result = lanchesterClosedFormBattle(
                    combatForce(1, state),
                    combatForce(2, state),
                    p.lanchesterCoeff[playerRef],
                    p.lanchesterExp[playerRef],
                    p.lanchesterCoeff[1 - playerRef],
                    p.lanchesterExp[1 - playerRef]
            )
            val winningSide = if (result > 0.0) 1 else 2
            val (winningTransit, losingTransit) = if (result > 0.0) Pair(transit1, transit2) else Pair(transit2, transit1)

            state.world.removeTransit(losingTransit)
            state.world.removeTransit(winningTransit)
            if (abs(result).toInt() == 0) {
                // do nothing
            } else {
                val successorTransit = winningTransit.copy(force = Force(abs(result), combatForce(winningSide, state).fatigue, state.nTicks()))
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
                val sourceCityPop = cities[origin].pop.size
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
                val startTime = state.nTicks() + delayTime
                val travelTime = (distance / world.params.speed[player]).toInt()
                val arrivalTime = startTime + travelTime
                val forcesSent = forcesSent(state)
                val currentGarrison = world.cities[origin].pop
                if (currentGarrison.fatigue > 0.0) { // update garrison fatigue first
                    world.cities[origin].pop = state.fatigueModels[player].rest(
                            state.nTicks(),
                            currentGarrison)
                }
                // when a Transit is created we only need the starting Fatigue - the end fatigue will be calculated on arrival
                val transit = Transit(Force(forcesSent, world.cities[origin].pop.fatigue, startTime), origin, destination, playerId, startTime, arrivalTime)
                // we execute the troop departure immediately (with delay)
                state.eventQueue.add(Event(startTime, TransitStart(transit)))
                // and put their arrival in the queue for the game state
                state.eventQueue.add(Event(arrivalTime, TransitEnd(transit.playerId, transit.fromCity, transit.toCity, transit.endTime)))
            }
        }
    }

    override fun nextDecisionPoint(player: Int, state: ActionAbstractGameState): Pair<Int, Int> {
        val minTime = state.nTicks() + wait
        return Pair(minTime, minTime)
    }

    fun isValid(state: LandCombatGame): Boolean {
        return state.world.cities[origin].owner == playerId &&
                state.world.cities[origin].pop.size > 0 &&
                origin != destination &&
                state.world.allRoutesFromCity.getOrDefault(origin, emptyList()).any { r -> r.toCity == destination } &&
                meetsMinStrengthCriterion(state)
    }

    fun meetsMinStrengthCriterion(state: LandCombatGame): Boolean {
        val forceStrength = forcesSent(state)

        with(state.world) {
            if (cities[destination].owner == playerId) return true
            if (cities[destination].pop.size > forceStrength / params.minAssaultFactor[player])
                return false
            val forcesOnArc = currentTransits
                    .filter { it.fromCity == destination && it.toCity == origin && it.playerId != playerId }
                    .sumByDouble { it.force.size }
            if (forcesOnArc > forceStrength / params.minAssaultFactor[player])
                return false
        }
        return true
    }

    // only visible to planning player
    override fun visibleTo(player: Int, state: ActionAbstractGameState) = player == playerIDToNumber(playerId)
}
