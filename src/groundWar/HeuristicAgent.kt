package groundWar

import ggi.*
import kotlin.math.max
import kotlin.random.Random

enum class HeuristicOptions {
    ATTACK, WITHDRAW
}

class HeuristicAgent(val attackRatio: Double, val defenseRatio: Double, val policy: List<HeuristicOptions> = listOf(HeuristicOptions.ATTACK), val seed: Int = 1) : SimpleActionPlayerInterface {

    val rnd: Random = Random(seed)

    override fun getAgentType(): String = "HeuristicAgent"

    override fun toString(): String {
        return policy.joinToString("|") + String.format("|Attack: %.1f|Defense: %.1f", attackRatio, defenseRatio)
    }

    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action {
        if (gameState is LandCombatGame) {
            policy.forEach { option ->
                when (option) {
                    HeuristicOptions.ATTACK -> {
                        val attack = attackOption(gameState, playerRef)
                        if (attack !is NoAction) return attack
                    }
                    HeuristicOptions.WITHDRAW -> {
                        val withdraw = withdrawOption(gameState, playerRef)
                        if (withdraw !is NoAction) return withdraw
                    }
                }
            }
        }
        return InterruptibleWait(playerRef, 10)
    }

    private fun attackOption(gameState: LandCombatGame, player: Int): Action {
        val playerId = numberToPlayerID(player)
        // We will launch an assault on any visible city for which we meet the force requirements
        with(gameState.world) {
            val eligibleTargets: List<Pair<City, City>> = cities.filter { it.owner != playerId }
                    .flatMap { c ->
                        allRoutesFromCity[cities.indexOf(c)]?.map { Pair(cities[it.toCity], c) } ?: emptyList()
                    }
                    .filter { (attacker, target) -> attacker.owner == playerId && attacker.pop.size > target.pop.size * attackRatio }
                    .filter { (attacker, target) -> currentTransits.none { it.playerId == playerId && it.fromCity == cities.indexOf(attacker) && it.toCity == cities.indexOf(target) } }
            // given a choice of many targets, we pick one at random
            if (eligibleTargets.isNotEmpty()) {
                val (attacker, target) = eligibleTargets.random(rnd)
                val attackRef = cities.indexOf(attacker)
                val targetRef = cities.indexOf(target)
                val proportion = target.pop.size * attackRatio / attacker.pop.size
                return LaunchExpedition(playerId,
                        attackRef,
                        targetRef,
                        proportion,
                        gameState.world.params.OODALoop[player])
            }
        }
        return NoAction(player, gameState.world.params.OODALoop[player])
    }

    private fun withdrawOption(gameState: LandCombatGame, player: Int): Action {
        val playerId = numberToPlayerID(player)
        with(gameState.world) {
            val threatenedCities = cities.filter { c ->
                c.owner == playerId && c.pop.size > 0.0 &&
                        currentTransits.any { t -> t.toCity == cities.indexOf(c) && t.playerId != playerId && t.force.effectiveSize > c.pop.size * defenseRatio }
            }
            val escapeRoutes: List<Pair<Int, Int>> = threatenedCities.flatMap { c ->
                (allRoutesFromCity[cities.indexOf(c)] ?: emptyList())
                        .filter { r -> cities[r.toCity].owner == playerId && cities[r.toCity] !in threatenedCities }
                        .map { Pair(it.fromCity, it.toCity) }
            }
            if (escapeRoutes.isNotEmpty()) {
                val (fromRef, toRef) = escapeRoutes.random(rnd)
                return LaunchExpedition(
                        playerId,
                        fromRef,
                        toRef,
                        1.0,
                        gameState.world.params.OODALoop[player])
            }
        }

        return NoAction(player, gameState.world.params.OODALoop[player])
    }

    private fun reinforceOption(gameState: LandCombatGame, player: Int): Action {
        val playerId = numberToPlayerID(player)
        // the idea here is that we look for city not adjacent to a city that is a threat (at least defenseRation the force size of any enemy neighbour)
        // and that is next to a threatened friendly city (i.e. one which is less than defenseRatio force of an enemy neighbour)

        with(gameState.world) {
            val defendedCities = cities.withIndex().map { (i, c) ->
                val maxCityEnemy: Double = allRoutesFromCity[i]?.map { r -> cities[r.toCity] }?.filterNot { it.owner == playerId }?.map { it.pop.size }?.max()
                        ?: 0.00
                val maxTransitEnemy: Double = currentTransits.filter { t -> t.toCity == i && t.playerId != playerId && t.force.size > c.pop.size * defenseRatio }.map { it.force.size }.max()
                        ?: 0.00
                val maxEnemy = max(maxCityEnemy, maxTransitEnemy)
                Triple(i, c, maxEnemy)
            }.filter { (i, c, m) -> c.owner == playerId && c.pop.size > 0.00 && c.pop.size > m * defenseRatio }

            val threatenedCities = cities.withIndex().map { (i, c) ->
                val maxCityEnemy: Double = allRoutesFromCity[i]?.map { r -> cities[r.toCity] }?.filterNot { it.owner == playerId }?.map { it.pop.size }?.max()
                        ?: 0.00
                val maxTransitEnemy: Double = currentTransits.filter { t -> t.toCity == i && t.playerId != playerId && t.force.size > c.pop.size * defenseRatio }.map { it.force.size }.max()
                        ?: 0.00
                val maxEnemy = max(maxCityEnemy, maxTransitEnemy)
                Triple(i, c, maxEnemy)
            }.filter { (i, c, m) -> c.owner == playerId &&  c.pop.size < m * defenseRatio }

       //     defendedCities.filter{(i, c, m) ->
       //         allRoutesFromCity[i]?.
       //     }
            return NoAction(0, 1000)
        }
    }

    override fun getLastPlan(): List<Action> {
        return emptyList()
    }

    override fun reset(): SimpleActionPlayerInterface = this

    override fun getForwardModelInterface(): SimpleActionPlayerInterface = this

    override fun backPropagate(finalScore: Double, finalTime: Int) {}
}