package olderGames.asteroids

import groundWar.*
import groundWar.executables.*
import ggi.*
import evodef.*

class AsteroidsEvaluator(val searchSpace: HopshackleSearchSpace<SimplePlayerInterface>,
                         val logger: EvolutionLogger,
                         val opponentParams: AgentParams
) : SolutionEvaluator {

    var nSteps = 1000

    override fun optimalFound() = false

    override fun optimalIfKnown() = null

    override fun logger() = logger

    var nEvals = 0

    override fun evaluate(settings: IntArray): Double {
        return evaluate(searchSpace.convertSettings(settings))
    }

    override fun evaluate(settings: DoubleArray): Double {
        var finalScore = 0.0
        val gameState = AsteroidsGameState()
        val params = GameParameters().injectValues(DefaultParams())
        gameState.setParams(params).initForwardModel()
        val p1 = searchSpace.getAgent(settings)

        val actions = IntArray(1)
        var i = 0
        while (i < nSteps && !gameState.isTerminal()) {
            actions[0] = p1.getAction(gameState.copy(), 0)
            gameState.next(actions)
            i++
        }
     //   println("Game score  ${settings.joinToString()} is ${gameState.score()}")
        finalScore += gameState.score()

        nEvals++
        logger.log(finalScore, settings, false)
        return if (finalScore > 0.0) 1.0 else -1.0
    }

    override fun searchSpace() = searchSpace

    override fun reset() {
        nEvals = 0
    }

    override fun nEvals() = nEvals
}

