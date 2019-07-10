package groundWar.executables

import agents.MCTS.*
import agents.*
import evodef.*
import ggi.*
import groundWar.*
import ntbea.*
import ntbeaOverride.*
import java.lang.AssertionError
import kotlin.random.Random

fun main(args: Array<String>) {
    val rnd = kotlin.random.Random(45)
    val ntbea = NTupleBanditEA(30.0, 50)
    ntbea.banditLandscapeModel = NTupleSystemOverride()
    ntbea.banditLandscapeModel.searchSpace = if (args[0] == "RHEA") RHEASearchSpace else MCTSSearchSpace
    ntbea.resetModelEachRun = false
    val opponentModel = when {
        args.size <= 2 -> SimpleActionDoNothing(1000)
        args[2] == "random" -> SimpleActionRandom
        else -> HeuristicAgent(args[2].toDouble(), args[3].toDouble(),
                args.withIndex().filter { it.index > 3 }.map { it.value }.map(HeuristicOptions::valueOf).toList())
    }
    val reportEvery = Math.min(1000, args[1].toInt())
    val logger = EvolutionLogger()
    repeat (args[1].toInt() / reportEvery) {
        val result = ntbea.runTrial(GroundWarEvaluator(args[0], logger, opponentModel), reportEvery)
        // tuples gets cleared out

        println(result.joinToString())

        println("Summary of 1-tuple statistics:")
        (ntbea.banditLandscapeModel as NTupleSystem).tuples.withIndex().take(ntbea.banditLandscapeModel.searchSpace.nDims()).forEach { (i, t) ->
            t.ntMap.toSortedMap().forEach { (k, v) ->
                println(String.format("\t%d, %s\t%d trials\t mean %.3g +/- %.2g", i, k, v.n(), v.mean(), v.stdErr()))
            }
        }
        println("\nSummary of 10 most tried full-tuple statistics:")
        (ntbea.banditLandscapeModel as NTupleSystem).tuples.find { it.tuple.size == ntbea.banditLandscapeModel.searchSpace.nDims() }
                ?.ntMap?.map { (k, v) -> k to v }?.sortedByDescending { (_, v) -> v.n() }?.take(10)?.forEach { (k, v) ->
            println(String.format("\t%s\t%d trials\t mean %.3g +/- %.2g", k, v.n(), v.mean(), v.stdErr()))
        }

    }
}

class GroundWarEvaluator(val type: String, val logger: EvolutionLogger, val opponentModel: SimpleActionPlayerInterface) : SolutionEvaluator {
    override fun optimalFound() = false

    override fun optimalIfKnown() = null

    override fun logger() = logger

    var nEvals = 0
    override fun evaluate(settings: IntArray): Double {
        val gameParams = EventGameParams()
        val blueAgent = when (type) {
            "RHEA" -> SimpleActionEvoAgent(
                    underlyingAgent = SimpleEvoAgent(
                            nEvals = 10000,
                            timeLimit = 50,
                            sequenceLength = RHEASearchSpace.values[0][settings[0]] as Int,
                            horizon = RHEASearchSpace.values[1][settings[1]] as Int,
                            useShiftBuffer = RHEASearchSpace.values[2][settings[2]] as Boolean,
                            probMutation = RHEASearchSpace.values[3][settings[3]] as Double,
                            flipAtLeastOneValue = RHEASearchSpace.values[4][settings[4]] as Boolean
                    ),
                    opponentModel = opponentModel //RHEASearchSpace.values[5][settings[5]] as SimpleActionPlayerInterface
            )
            "MCTS" -> MCTSTranspositionTableAgentMaster(MCTSParameters(
                    C = MCTSSearchSpace.values[3][settings[3]] as Double,
                    selectionMethod = MCTSSearchSpace.values[6][settings[6]] as MCTSSelectionMethod,
                    maxPlayouts = 10000,
                    timeLimit = 50,
                    horizon = MCTSSearchSpace.values[1][settings[1]] as Int,
                    pruneTree = MCTSSearchSpace.values[2][settings[2]] as Boolean,
                    maxDepth = MCTSSearchSpace.values[0][settings[0]] as Int,
                    maxActions = MCTSSearchSpace.values[4][settings[4]] as Int
            ),
                    stateFunction = LandCombatStateFunction,
                    rolloutPolicy = MCTSSearchSpace.values[5][settings[5]] as SimpleActionPlayerInterface,
                    opponentModel = opponentModel
            )
            else -> throw AssertionError("Unknown type " + type)
        }
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

    override fun searchSpace() = if (type == "RHEA") RHEASearchSpace else MCTSSearchSpace

    override fun reset() {}

    override fun nEvals() = nEvals
}

object RHEASearchSpace : SearchSpace {

    val values = arrayOf(
            arrayOf(4, 8, 12, 24, 48, 100, 200),                    // sequenceLength
            arrayOf(10, 25, 50, 100, 200, 400, 1000),               // horizon
            arrayOf(true, false),                                   // useShiftBuffer
            arrayOf(0.003, 0.01, 0.03, 0.1, 0.3, 0.5, 0.7),         // probMutation
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

object MCTSSearchSpace : SearchSpace {

    val values = arrayOf(
            arrayOf(1, 2, 3, 6, 12),                  // maxDepth (==sequenceLength)
            arrayOf(10, 25, 50, 100, 200, 400, 1000),               // horizon
            arrayOf(true, false),                           // pruneTree
            arrayOf(0.03, 0.1, 0.3, 1.0, 3.0, 10.0, 30.0, 100.0),           // C
            arrayOf(5, 10, 20, 40, 80),             // maxActions
            arrayOf(SimpleActionDoNothing(1000), SimpleActionRandom),                          // rolloutPolicy
            arrayOf(MCTSSelectionMethod.SIMPLE, MCTSSelectionMethod.ROBUST)  // selection policy
            /*       arrayOf(SimpleActionDoNothing(1000), SimpleActionRandom,    // opponentModel
                           HeuristicAgent(3.0, 1.2, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK)),
                           HeuristicAgent(10.0, 2.0, listOf(HeuristicOptions.WITHDRAW)),
                           HeuristicAgent(10.0, 2.0, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK))
                   ) */
    )

    override fun nValues(index: Int) = values[index].size

    override fun nDims() = values.size

}