package olderGames.planetWars

import groundWar.*
import groundWar.executables.*
import ggi.*
import evodef.*

class PlanetWarEvaluator(val searchSpace: HopshackleSearchSpace<SimplePlayerInterface>,
                         val logger: EvolutionLogger,
                         val opponentParams: AgentParams
) : SolutionEvaluator {

    var nSteps = 200

    override fun optimalFound() = false

    override fun optimalIfKnown() = null

    override fun logger() = logger

    var nEvals = 0


    override fun searchSpace() = searchSpace

    override fun reset() {
        nEvals = 0
    }

    override fun nEvals() = nEvals
    override fun evaluate(settings: IntArray): Double {
        return evaluate(searchSpace.convertSettings(settings))
    }

    override fun evaluate(settings: DoubleArray): Double {
        val seedToUse = System.currentTimeMillis()

        var finalScore = 0.0
        val gameState = GameState().defaultState(seedToUse)
        val p1 = searchSpace.getAgent(settings)
        val p2 = opponentParams.createSimpleAgent("target")

        val actions = IntArray(2)
        var i = 0
        while (i < nSteps && !gameState.isTerminal()) {
            actions[0] = p1.getAction(gameState.copy(), 0)
            actions[1] = p2.getAction(gameState.copy(), 1)
            gameState.next(actions)
            i++
        }
     //   println("Game score  ${settings.joinToString()} is ${gameState.score()}")
        finalScore += gameState.score()
     //   println("Total ticks in game = " + i)

        nEvals++
        logger.log(finalScore, settings, false)
        return if (finalScore > 0.0) 1.0 else -1.0
    }

}

