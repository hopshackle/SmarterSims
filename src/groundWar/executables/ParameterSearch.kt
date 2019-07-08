package groundWar.executables

import agents.SimpleEvoAgent
import evodef.*
import ggi.SimpleActionDoNothing
import ggi.SimpleActionEvoAgent
import ggi.SimpleActionPlayerInterface
import ggi.SimpleActionRandom
import groundWar.*
import ntbea.*
import kotlin.random.Random

fun main(args: Array<String>) {
    val ntbea = NTupleBanditEA(30.0, 10)
    ntbea.banditLandscapeModel = NTupleSystem()
    ntbea.banditLandscapeModel.searchSpace = RHEASearchSpace
    ntbea.setReportFrequency(50)
    val result = ntbea.runTrial(RHEAGroundWarEvaluator(), 5000)
    println(result.joinToString())

    (ntbea.banditLandscapeModel as NTupleSystem).tuples.withIndex().take(RHEASearchSpace.nDims()).forEach { (i, t) ->
        t.ntMap.forEach { (k, v) ->
            println(String.format("%d, %s : %d trials, mean %.3g, +/- %.2g", i, k, v.n(), v.mean(), v.stdErr()))
        }
    }
}

class RHEAGroundWarEvaluator : SolutionEvaluator {
    override fun optimalFound() = false

    override fun optimalIfKnown() = null

    val logger = EvolutionLogger()
    private val searchSpace = RHEASearchSpace

    override fun logger() = logger

    var nEvals = 0
    override fun evaluate(settings: IntArray): Double {
        val gameParams = EventGameParams()
        val blueAgent = SimpleActionEvoAgent(
                underlyingAgent = SimpleEvoAgent(
                        sequenceLength = RHEASearchSpace.values[0][settings[0]] as Int,
                        horizon = RHEASearchSpace.values[1][settings[1]] as Int,
                        useShiftBuffer = RHEASearchSpace.values[2][settings[2]] as Boolean,
                        probMutation = RHEASearchSpace.values[3][settings[3]] as Double,
                        flipAtLeastOneValue = RHEASearchSpace.values[4][settings[4]] as Boolean
                ),
                opponentModel = SimpleActionDoNothing(1000) //RHEASearchSpace.values[5][settings[5]] as SimpleActionPlayerInterface
        )
        val redAgent = HeuristicAgent(3.0, 1.2, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK))
        val scoreFunction = simpleScoreFunction(5.0, 1.0, -5.0, -1.0)
        val world = World(random = Random(seed = gameParams.seed), params = gameParams)
        val game = LandCombatGame(world)
        game.scoreFunction[PlayerId.Blue] = scoreFunction
        game.scoreFunction[PlayerId.Red] = scoreFunction
        game.registerAgent(0, blueAgent)
        game.registerAgent(1, redAgent)
        game.next(1000)
        nEvals++
        logger.log(game.score(0), settings, false)
        return game.score(0)
    }

    override fun searchSpace() = searchSpace

    override fun reset() {}

    override fun nEvals() = nEvals
}

object RHEASearchSpace : SearchSpace {

    val values = arrayOf(
            arrayOf(4, 8, 12, 24, 48, 100, 200),                  // sequenceLength
            arrayOf(10, 25, 50, 100, 200, 400, 1000),               // horizon
            arrayOf(true, false),                           // useShiftBuffer
            arrayOf(0.003, 0.01, 0.03, 0.1, 0.3, 0.5, 0.7),           // probMutation
            arrayOf(true, false)                           // flipAtLeastOne
     /*       arrayOf(SimpleActionDoNothing(1000), SimpleActionRandom,    // opponentModel
                    HeuristicAgent(3.0, 1.2, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK)),
                    HeuristicAgent(10.0, 2.0, listOf(HeuristicOptions.WITHDRAW)),
                    HeuristicAgent(10.0, 2.0, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK))
            ) */
    )

    override fun nValues(index: Int) = values[index].size

    override fun nDims() = values.size
}