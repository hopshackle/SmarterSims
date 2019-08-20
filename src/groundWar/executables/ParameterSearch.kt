package groundWar.executables

import agents.MCTS.*
import agents.*
import agents.RHEA.RHCAAgent
import agents.RHEA.SimpleActionEvoAgent
import agents.RHEA.SimpleEvoAgent
import evodef.*
import ggi.*
import groundWar.*
import ntbea.*
import utilities.StatsCollator
import java.io.BufferedReader
import java.io.FileReader
import java.lang.AssertionError
import kotlin.math.min
import kotlin.random.Random
import kotlin.streams.toList

fun main(args: Array<String>) {
    if (args.size < 4) AssertionError("Must specify at least four parameters: 3-Tuples? RHEA/MCTS trials STD/EXP_MEAN/EXP_FULL:nn/EXP_SQRT:nn")
    val totalRuns = args[2].split("|")[0].toInt()
    val reportEvery = args[2].split("|").getOrNull(1)?.toInt() ?: totalRuns
    val searchSpace = when (args[1]) {
        "RHEA" -> RHEASearchSpace
        "MCTS", "MCTS2" -> MCTSSearchSpace
        "RHCA" -> RHCASearchSpace
        else -> throw AssertionError("Unknown searchSpace " + args[1])
    }
    val nTupleSystem = when {
        args[3] == "STD" -> NTupleSystem()
        args[3] == "EXP_MEAN" -> NTupleSystemExp(30, expWeightExplore = false)
        args[3].startsWith("EXP_FULL") -> {
            val minWeight = args[3].split(":")[1].toDouble()
            NTupleSystemExp(30, minWeight = minWeight)
        }
        args[3].startsWith("EXP_SQRT") -> {
            val minWeight = args[3].split(":")[1].toDouble()
            NTupleSystemExp(30, minWeight = minWeight, exploreWithSqrt = true)
        }
        else -> throw AssertionError("Unknown NTuple parameter: " + args[3])
    }
    val use3Tuples = args[0] == "true"
    nTupleSystem.use3Tuple = use3Tuples

    val params = if (args.size > 4) {
        val fileAsLines = BufferedReader(FileReader(args[4])).lines().toList()
        createIntervalParamsFromString(fileAsLines).sampleParams()
    } else EventGameParams()

    val stateSpaceSize = (0 until searchSpace.nDims()).fold(1, { acc, i -> acc * searchSpace.nValues(i) })
    val twoTupleSize = (0 until searchSpace.nDims() - 1).map { i ->
        searchSpace.nValues(i) * (i + 1 until searchSpace.nDims()).map(searchSpace::nValues).sum()
    }.sum()
    val threeTupleSize = (0 until searchSpace.nDims() - 2).map { i ->
        searchSpace.nValues(i) * (i + 1 until searchSpace.nDims()).map { j ->
            searchSpace.nValues(j) * (j + 1 until searchSpace.nDims()).map(searchSpace::nValues).sum()
        }.sum()
    }.sum()

    val ntbea = NTupleBanditEA(100.0, min(50.0, stateSpaceSize * 0.01).toInt())

    ntbea.banditLandscapeModel = nTupleSystem
    ntbea.banditLandscapeModel.searchSpace = searchSpace
    ntbea.resetModelEachRun = false
    val opponentModel: SimpleActionPlayerInterface? = when {
        args[1] == "MCTS2" -> null
        args.size <= 5 -> SimpleActionDoNothing(1000)
        args[5] == "random" -> SimpleActionRandom
        else -> HeuristicAgent(args[5].toDouble(), args[6].toDouble(),
                args.withIndex().filter { it.index > 6 }.map { it.value }.map(HeuristicOptions::valueOf).toList())
    }
    if (args[1] == "MCTS2" && args.size > 5)
        throw AssertionError("Opponent model not used in MCTS2")
    val logger = EvolutionLogger()
    println("Search space consists of $stateSpaceSize states and $twoTupleSize possible 2-Tuples" +
            "${if (use3Tuples) " and $threeTupleSize possible 3-Tuples" else ""}:")
    (0 until searchSpace.nDims()).forEach {
        println(String.format("%20s has %d values %s", searchSpace.name(it), searchSpace.nValues(it),
                (0 until searchSpace.nValues(it)).map { i -> searchSpace.value(it, i) }.joinToString()))
    }
    repeat(totalRuns / reportEvery) {
        ntbea.runTrial(GroundWarEvaluator(searchSpace, params, logger, opponentModel), reportEvery)
        // tuples gets cleared out
        println("Current best sampled point (using mean estimate): " + nTupleSystem.bestOfSampled.joinToString() +
                String.format(", %.3g", nTupleSystem.getMeanEstimate(nTupleSystem.bestOfSampled)))
        // println("Current best predicted point (using mean estimate): " + nTupleSystem.bestSolution.joinToString() +
        //         String.format(", %.3g", nTupleSystem.getMeanEstimate(nTupleSystem.bestSolution)))
        val tuplesExploredBySize = (1..searchSpace.nDims()).map { size ->
            nTupleSystem.tuples.filter { it.tuple.size == size }
                    .map { it.ntMap.size }.sum()
        }
        println("Tuples explored by size: ${tuplesExploredBySize.joinToString()}")
        println("Summary of 1-tuple statistics after ${nTupleSystem.numberOfSamples()} samples:")
        nTupleSystem.tuples.withIndex().take(searchSpace.nDims()).forEach { (i, t) ->
            t.ntMap.toSortedMap().forEach { (k, v) ->
                println(String.format("\t%20s\t%s\t%d trials\t mean %.3g +/- %.2g", searchSpace.name(i), k, v.n(), v.mean(), v.stdErr()))
            }
        }
        println("\nSummary of 10 most tried full-tuple statistics:")
        nTupleSystem.tuples.find { it.tuple.size == searchSpace.nDims() }
                ?.ntMap?.map { (k, v) -> k to v }?.sortedByDescending { (_, v) -> v.n() }?.take(10)?.forEach { (k, v) ->
            println(String.format("\t%s\t%d trials\t mean %.3g +/- %.2g\t(NTuple estimate: %.3g)", k, v.n(), v.mean(), v.stdErr(), nTupleSystem.getMeanEstimate(k.v)))
        }
        println("\nSummary of 5 highest valued full-tuple statistics:")
        nTupleSystem.tuples.find { it.tuple.size == searchSpace.nDims() }
                ?.ntMap?.map { (k, v) -> k to v }?.sortedByDescending { (_, v) -> v.mean() }?.take(3)?.forEach { (k, v) ->
            println(String.format("\t%s\t%d trials\t mean %.3g +/- %.2g\t(NTuple estimate: %.3g)", k, v.n(), v.mean(), v.stdErr(), nTupleSystem.getMeanEstimate(k.v)))
        }
        println("")
    }
    val bestAgent = GroundWarEvaluator(searchSpace, params, logger, opponentModel).getAgent(nTupleSystem.bestOfSampled)
    StatsCollator.clear()
    runGames(1000,
            bestAgent,
            HeuristicAgent(3.0, 1.2, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK)),
            eventParams = params)
    println(StatsCollator.summaryString())
}

class GroundWarEvaluator(val searchSpace: SearchSpace, val params: EventGameParams, val logger: EvolutionLogger, val opponentModel: SimpleActionPlayerInterface?) : SolutionEvaluator {
    override fun optimalFound() = false

    override fun optimalIfKnown() = null

    override fun logger() = logger

    var nEvals = 0

    fun getAgent(settings: IntArray): SimpleActionPlayerInterface {
        return when (searchSpace) {
            RHEASearchSpace -> SimpleActionEvoAgent(
                    underlyingAgent = SimpleEvoAgent(
                            nEvals = 10000,
                            timeLimit = 50,
                            useMutationTransducer = false,
                            sequenceLength = RHEASearchSpace.values[0][settings[0]] as Int,
                            horizon = RHEASearchSpace.values[1][settings[1]] as Int,
                            useShiftBuffer = RHEASearchSpace.values[2][settings[2]] as Boolean,
                            probMutation = RHEASearchSpace.values[3][settings[3]] as Double,
                            flipAtLeastOneValue = RHEASearchSpace.values[4][settings[4]] as Boolean,
                            discountFactor = RHEASearchSpace.values[5][settings[5]] as Double
                    ),
                    opponentModel = opponentModel
                            ?: SimpleActionDoNothing(1000) //RHEASearchSpace.values[5][settings[5]] as SimpleActionPlayerInterface
            )
            RHCASearchSpace -> RHCAAgent(
                    timeLimit = 50,
                    sequenceLength = RHCASearchSpace.values[0][settings[0]] as Int,
                    horizon = RHCASearchSpace.values[1][settings[1]] as Int,
                    useShiftBuffer = RHCASearchSpace.values[2][settings[2]] as Boolean,
                    probMutation = RHCASearchSpace.values[3][settings[3]] as Double,
                    flipAtLeastOneValue = RHCASearchSpace.values[4][settings[4]] as Boolean,
                    populationSize = RHCASearchSpace.values[5][settings[5]] as Int,
                    parentSize = RHCASearchSpace.values[6][settings[6]] as Int,
                    evalsPerGeneration = RHCASearchSpace.values[7][settings[7]] as Int,
                    discountFactor = RHCASearchSpace.values[8][settings[8]] as Double
            )
            MCTSSearchSpace -> MCTSTranspositionTableAgentMaster(MCTSParameters(
                    C = MCTSSearchSpace.values[3][settings[3]] as Double,
                    maxPlayouts = 10000,
                    timeLimit = 50,
                    horizon = MCTSSearchSpace.values[1][settings[1]] as Int,
                    pruneTree = MCTSSearchSpace.values[2][settings[2]] as Boolean,
                    maxDepth = MCTSSearchSpace.values[0][settings[0]] as Int,
                    maxActions = MCTSSearchSpace.values[4][settings[4]] as Int,
                    discountRate = MCTSSearchSpace.values[6][settings[6]] as Double
            ),
                    stateFunction = LandCombatStateFunction,
                    rolloutPolicy = MCTSSearchSpace.values[5][settings[5]] as SimpleActionPlayerInterface,
                    opponentModel = opponentModel
            )
            is UtilitySearchSpace -> {
                searchSpace.agentParams.createAgent("UtilitySearch")
            }
            else -> throw AssertionError("Unknown type " + searchSpace)
        }
    }

    override fun evaluate(settings: IntArray): Double {
        val blueAgent = getAgent(settings)
        val redAgent = HeuristicAgent(3.0, 1.2, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK))
        val seedToUse = System.currentTimeMillis()

        var swappedRoles = false
        var netScore = 0.0
        var finalScore = 0.0
        repeat(2) {
            val world = World(params = params.copy(seed = seedToUse))
            val game = LandCombatGame(world)
            if (swappedRoles) {
                game.registerAgent(0, redAgent)
                game.registerAgent(1, blueAgent)
                game.scoreFunction[PlayerId.Red] = if (searchSpace is UtilitySearchSpace)
                    searchSpace.getScoreFunction(settings)
                else interimScoreFunction
                game.scoreFunction[PlayerId.Blue] = interimScoreFunction
            } else {
                game.registerAgent(0, blueAgent)
                game.registerAgent(1, redAgent)
                game.scoreFunction[PlayerId.Blue] = if (searchSpace is UtilitySearchSpace)
                    searchSpace.getScoreFunction(settings)
                else interimScoreFunction
                game.scoreFunction[PlayerId.Red] = interimScoreFunction
            }

            game.next(1000)
            swappedRoles = true
            netScore += game.score(if (swappedRoles) 1 else 0)
            finalScore += finalScoreFunction(game, if (swappedRoles) 1 else 0)
        }
        nEvals++
        //     println("Game score ${settings.joinToString()} is ${game.score(0).toInt()}")
        logger.log(finalScore / 2.0, settings, false)
        return finalScore / 2.0
    }

    override fun searchSpace() = searchSpace

    override fun reset() {}

    override fun nEvals() = nEvals
}


abstract class HopshackleSearchSpace : SearchSpace {
    abstract val names: Array<String>
    abstract val values: Array<Array<*>>

    override fun nValues(index: Int) = values[index].size
    override fun nDims() = values.size
    override fun name(index: Int) = names[index]
    override fun value(dimension: Int, index: Int) = values[dimension][index]
}

object RHEASearchSpace : HopshackleSearchSpace() {
    override val names: Array<String>
        get() = arrayOf("sequenceLength", "horizon", "useShiftBuffer", "probMutation", "flipAtLeastOne", "discountFactor")

    override val values: Array<Array<*>>
        get() = arrayOf(
                arrayOf(3, 6, 12, 24, 48),                    // sequenceLength
                arrayOf(50, 100, 200, 400, 1000),               // horizon
                arrayOf(false, true),                                   // useShiftBuffer
                arrayOf(0.003, 0.01, 0.03, 0.1, 0.3, 0.5, 0.7),         // probMutation
                arrayOf(false, true),                           // flipAtLeastOne
                arrayOf(1.0, 0.999, 0.99, 0.95)            // discount rate
                /*       arrayOf(SimpleActionDoNothing(1000), SimpleActionRandom,    // opponentModel
                               HeuristicAgent(3.0, 1.2, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK)),
                               HeuristicAgent(10.0, 2.0, listOf(HeuristicOptions.WITHDRAW)),
                               HeuristicAgent(10.0, 2.0, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK))
                       ) */
        )
}

object RHCASearchSpace : HopshackleSearchSpace() {

    override val names: Array<String>
        get() = arrayOf("sequenceLength", "horizon", "useShiftBuffer", "probMutation", "flipAtLeastOne", "populationSize", "parentSize", "evalsPerGeneration", "discountFactor")

    override val values: Array<Array<*>>
        get() = arrayOf(arrayOf(3, 6, 12, 24),                    // sequenceLength
                arrayOf(50, 100, 200, 400, 1000),               // horizon
                arrayOf(false, true),                                   // useShiftBuffer
                arrayOf(0.003, 0.01, 0.03, 0.1, 0.3, 0.5, 0.7),         // probMutation
                arrayOf(false, true),                           // flipAtLeastOne
                arrayOf(32, 64, 128),                       //populationSize
                arrayOf(1, 2, 4),                               // parentSize
                arrayOf(5, 10, 20, 30),                            // evalsPerGeneration
                arrayOf(1.0, 0.999, 0.99, 0.95)            // discount rate
                /*       arrayOf(SimpleActionDoNothing(1000), SimpleActionRandom,    // opponentModel
                               HeuristicAgent(3.0, 1.2, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK)),
                               HeuristicAgent(10.0, 2.0, listOf(HeuristicOptions.WITHDRAW)),
                               HeuristicAgent(10.0, 2.0, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK))
                       ) */
        )
}

object MCTSSearchSpace : HopshackleSearchSpace() {

    override val names: Array<String>
        get() = arrayOf("maxDepth", "horizon", "pruneTree", "C", "maxActions", "rolloutPolicy", "selectionPolicy", "discountFactor")
    override val values: Array<Array<*>>
        get() = arrayOf(
                arrayOf(3, 6, 12),                  // maxDepth (==sequenceLength)
                arrayOf(50, 100, 200, 400, 1000),               // horizon
                arrayOf(false, true),                           // pruneTree
                arrayOf(0.03, 0.3, 3.0, 30.0),           // C
                arrayOf(20, 40, 80),             // maxActions
                arrayOf(SimpleActionDoNothing(1000), SimpleActionRandom),                          // rolloutPolicy
                arrayOf(1.0, 0.999, 0.995, 0.99)            // discount rate
                /*       arrayOf(SimpleActionDoNothing(1000), SimpleActionRandom,    // opponentModel
                               HeuristicAgent(3.0, 1.2, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK)),
                               HeuristicAgent(10.0, 2.0, listOf(HeuristicOptions.WITHDRAW)),
                               HeuristicAgent(10.0, 2.0, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK))
                       ) */
        )
}

class UtilitySearchSpace(val agentParams: AgentParams) : HopshackleSearchSpace() {
    override val names: Array<String>
        get() = arrayOf("visibilityNode", "visibilityArc", "ownCity", "theirCity", "ownForce", "theirForce")
    override val values: Array<Array<*>>
        get() = arrayOf(
                arrayOf(0.0, 0.1, 0.5, 1.0, 5.0),
                arrayOf(0.0, 0.1, 0.5, 1.0, 5.0),
                arrayOf(0.0, 1.0, 5.0, 10.0),
                arrayOf(0.0, -1.0, -5.0, -10.0),
                arrayOf(0.0, 0.1, 0.5, 1.0, 3.0),
                arrayOf(0.0, -0.1, -0.5, -1.0, -3.0)
        )

    fun getScoreFunction(settings: IntArray): (LandCombatGame, Int) -> Double {
        return compositeScoreFunction(
                visibilityScore(values[0][settings[0]] as Double, values[1][settings[1]] as Double),
                simpleScoreFunction(values[2][settings[2]] as Double, values[4][settings[4]] as Double,
                        values[3][settings[3]] as Double, values[5][settings[5]] as Double)
        )

    }
}