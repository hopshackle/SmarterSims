package groundWar

import ggi.Action
import ggi.ActionAbstractGameState
import groundWar.fatigue.*
import groundWar.fogOfWar.HistoricVisibility
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun getCurrentTransit(transit: Transit, state: LandCombatGame): Transit? {
    return state.world.currentTransits.firstOrNull {
        it.endTime == transit.endTime && it.playerId == transit.playerId && it.fromCity == transit.fromCity && it.toCity == transit.toCity
    }
}

data class TransitStart(val transit: Transit) : Action {
    override val player = playerIDToNumber(transit.playerId)

    override fun apply(state: ActionAbstractGameState) {
        if (state is LandCombatGame) {
            val world = state.world
            val city = world.cities[transit.fromCity]
            if (city.owner != transit.playerId)
                return // do nothing
            val actualTransit = when {
                city.pop.size < transit.force.size -> transit.copy(force = transit.force.copy(city.pop.size))
                else -> transit
            }
            if (actualTransit.force.size <= 0.00) return
            city.pop = city.pop.copy(size = city.pop.size - actualTransit.force.size)
            val enemyCollision = world.nextCollidingTransit(actualTransit, state.nTicks())
            world.addTransit(actualTransit)
            if (enemyCollision != null) {
                state.eventQueue.add(actualTransit.collisionEvent(enemyCollision, world, state.nTicks()))
            }
        }
    }

    override fun visibleTo(player: Int, state: ActionAbstractGameState): Boolean {
        return if (state is LandCombatGame) state.world.checkVisible(transit, numberToPlayerID(player),
                (state.visibilityModels.get(numberToPlayerID(player)) ?: noVisibility)) else true
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
            val perspectiveId = numberToPlayerID(player)
            return when {
                !state.world.params.fogOfWar -> true    // no fog of war, so all visible
                playerId == perspectiveId -> true // one of ours
                state.world.currentTransits.any { it.fromCity == fromCity && it.toCity == toCity && it.playerId == playerId && it.endTime == endTime } -> true
                // we have a visible transit that matches this
                else -> state.world.checkVisible(Transit(Force(0.0), fromCity, toCity, playerId, state.nTicks(), endTime), perspectiveId,
                        (state.visibilityModels.get(perspectiveId) ?: noVisibility))
                // this last condition may be relevant if the TransitStart has not yet been activated (due to order delays for example)
            }
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
                        p.lanchesterExp[1 - player] + if (city.fort) p.fortDefenderExpBonus else 0.0,
                        city.limit
                )
                if (result > 0.0) {
                    // attackers win
                    val opponent = city.owner
                    city.owner = playerId
                    city.pop = pop.copy(size = result)
                    if (opponent !in listOf(PlayerId.Neutral, PlayerId.Fog))
                        state.updateVisibilityOfNeighbours(destination, opponent)
                } else {
                    // defenders win
                    city.pop = city.pop.copy(size = -result)
                    state.updateVisibility(destination, playerId)
                    if (origin != -1) state.updateVisibility(origin, playerId)
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
                return checkVisible(Transit(Force(0.0), origin, destination, this@CityInflux.playerId, 0, 0), playerId,
                        (state.visibilityModels.get(playerId)) ?: HistoricVisibility(emptyMap()))
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
            val actualTransit1 = getCurrentTransit(transit1, state)
            val actualTransit2 = getCurrentTransit(transit2, state)
            if (actualTransit1 == null || actualTransit2 == null)
                return      // no Battle takes place

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
            val (winningTransit, losingTransit) = if (result > 0.0) Pair(actualTransit1, actualTransit2) else Pair(actualTransit2, actualTransit1)

            state.world.removeTransit(losingTransit)
            state.world.removeTransit(winningTransit)
            state.updateVisibility(losingTransit.fromCity, losingTransit.playerId)
            state.updateVisibility(losingTransit.toCity, losingTransit.playerId)
            if (abs(result).toInt() == 0) {
                state.updateVisibility(winningTransit.fromCity, winningTransit.playerId)
                state.updateVisibility(winningTransit.toCity, winningTransit.playerId)
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
        /*   val playerId = numberToPlayerID(player)
           val visibility = state.visibilityModels.get(playerId) ?: HistoricVisibility(emptyMap())
           return state.world.checkVisible(transit1, playerId, visibility)
                   && state.world.checkVisible(transit2, playerId, visibility)
                   */
        // since a battle must involve two players...and since we only have two active players, it will always be visible!
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
                val routeLimit = allRoutesFromCity[origin]?.find { it.toCity == destination }?.limit ?: 0.0
                if (routeLimit > 0.0 && routeLimit < forcesSent) forcesSent = routeLimit
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
                if (forcesSent <= 0.001) return // not enough to mount an expedition
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
        val minTime = state.nTicks() + max(wait, 1)
        return Pair(minTime, minTime)
    }

    fun isValid(state: LandCombatGame): Boolean {
        return state.world.cities[origin].owner == playerId &&
                state.world.cities[origin].pop.size > 0 &&
                origin != destination &&
                state.world.allRoutesFromCity.getOrDefault(origin, emptyList()).any { r -> r.toCity == destination } &&
                meetsMinStrengthCriterion(state) &&
                doesNotExceedControlLimit(state)
    }

    private fun meetsMinStrengthCriterion(state: LandCombatGame): Boolean {
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

    private fun doesNotExceedControlLimit(state: LandCombatGame): Boolean {
        val limit = state.world.params.controlLimit[player]
        if (limit == 0) return true
        val currentTransits = state.world.currentTransits.filter { it.playerId == playerId }.size
        val currentPlannedTransits = state.eventQueue.filter {
            (it.action is LaunchExpedition && it.action.playerId == playerId)
                    || (it.action is TransitStart && it.action.transit.playerId == playerId)
        }.size
        return (currentTransits + currentPlannedTransits < limit)
    }

    // only visible to planning player
    override fun visibleTo(player: Int, state: ActionAbstractGameState) = player == playerIDToNumber(playerId)
}
