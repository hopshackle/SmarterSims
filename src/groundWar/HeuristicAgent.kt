package groundWar

import ggi.SimpleActionPlayerInterface
import ggi.Action
import ggi.ActionAbstractGameState
import ggi.NoAction
import kotlin.random.Random

enum class HeuristicOptions {
    ATTACK, WITHDRAW
}

class HeuristicAgent(val attackRatio: Double, val defenseRatio: Double, val policy: List<HeuristicOptions> = listOf(HeuristicOptions.ATTACK), val seed: Int = 1) : SimpleActionPlayerInterface {

    val rnd: Random = Random(seed)

    override fun getAgentType(): String = "HeuristicAgent"

    override fun getAction(gameState: ActionAbstractGameState, playerRef: Int): Action {
        if (gameState is LandCombatGame) {
            policy.forEach { option ->
                when (option) {
                    HeuristicOptions.ATTACK -> {
                        val attack = attackOption(gameState, playerRef)
                        if (attack != NoAction) return attack
                    }
                    HeuristicOptions.WITHDRAW -> {
                        val withdraw = withdrawOption(gameState, playerRef)
                        if (withdraw != NoAction) return withdraw
                    }
                }
            }
            return Wait(playerRef, 10)
        }
        return NoAction
    }

    private fun attackOption(gameState: LandCombatGame, player: Int): Action {
        val playerId = numberToPlayerID(player)
        // We will launch an assault on any visible city for which we meet the force requirements
        with(gameState.world) {
            val eligibleTargets: List<Pair<City, City>> = cities.filter { it.owner != playerId }
                    .flatMap { c ->
                        allRoutesFromCity[cities.indexOf(c)]?.map { Pair(cities[it.toCity], c) } ?: emptyList()
                    }
                    .filter { (attacker, target) -> attacker.owner == playerId && attacker.pop > target.pop * attackRatio }
                    .filter { (attacker, target) -> currentTransits.none { it.playerId == playerId && it.fromCity == cities.indexOf(attacker) && it.toCity == cities.indexOf(target) } }
            // given a choice of many targets, we pick one at random
            if (eligibleTargets.isNotEmpty()) {
                val (attacker, target) = eligibleTargets.random(rnd)
                val attackRef = cities.indexOf(attacker)
                val targetRef = cities.indexOf(target)
                val proportion = target.pop * attackRatio / attacker.pop * gameState.nActions()
                return LaunchExpedition(playerId,
                        attackRef,
                        targetCityCode(this, attackRef, targetRef),
                        proportion.toInt(),
                        gameState.world.params.OODALoop[player])
            }
        }
        return NoAction
    }

    private fun withdrawOption(gameState: LandCombatGame, player: Int): Action {
        val playerId = numberToPlayerID(player)
        with(gameState.world) {
            val threatenedCities = cities.filter { c ->
                c.owner == playerId && c.pop > 0.0 &&
                        currentTransits.any { t -> t.toCity == cities.indexOf(c) && t.playerId != playerId && t.nPeople > c.pop * defenseRatio }
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
                        targetCityCode(this, fromRef, toRef),
                        gameState.nActions() - 1,
                        gameState.world.params.OODALoop[player])
            }
        }

        return NoAction
    }

    fun targetCityCode(world: World, attacker: Int, target: Int): Int {
        val routes = world.allRoutesFromCity[attacker] ?: emptyList()
        val index = routes.indexOfFirst { it.toCity == target }
        return index
    }

    override fun getPlan(gameState: ActionAbstractGameState, playerRef: Int): List<Action> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun reset(): SimpleActionPlayerInterface = this

    override fun getForwardModelInterface(): SimpleActionPlayerInterface = this

    override fun backPropagate(finalScore: Double) {}
}