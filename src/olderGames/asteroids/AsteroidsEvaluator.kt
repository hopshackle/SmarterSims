package olderGames.asteroids

import groundWar.*
import groundWar.executables.*
import ggi.*
import evodef.*
import utilities.StatsCollator

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
        val startTime = System.currentTimeMillis()
        val gameState = AsteroidsGameState()
        val params = GameParameters().injectValues(DefaultParams())
        gameState.setParams(params).initForwardModel()
        val p1 = searchSpace.getAgent(settings)

        val actions = IntArray(1)
        var i = 0
        var timeSpentOnAI = 0L
        var timeSpentOnGame = 0L
  //      StatsCollator.clear()
        while (i < nSteps && !gameState.isTerminal()) {
            val stepStartTime = System.currentTimeMillis()
            actions[0] = p1.getAction(gameState.copy(), 0)
            val actionChosenTime = System.currentTimeMillis()
            gameState.next(actions)
            i++
 //           timeSpentOnAI += (actionChosenTime - stepStartTime)
 //           timeSpentOnGame+= (System.currentTimeMillis() - actionChosenTime)
        }
 //       println("Game score  ${settings.joinToString()} is ${gameState.score()} in $nSteps steps and ${(System.currentTimeMillis() - startTime)/1000} seconds (${timeSpentOnAI/1000}/${timeSpentOnGame/1000})")
 //       println(StatsCollator.summaryString())
        finalScore += gameState.score()

        nEvals++
        logger.log(finalScore, settings, false)
        return finalScore
    }

    override fun searchSpace() = searchSpace

    override fun reset() {
        nEvals = 0
    }

    override fun nEvals() = nEvals
}

