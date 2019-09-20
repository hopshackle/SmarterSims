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
import utilities.GaussianProcessFramework
import utilities.GaussianProcessSearch
import utilities.StatsCollator
import java.io.BufferedReader
import java.io.FileReader
import java.lang.AssertionError
import kotlin.math.min
import kotlin.reflect.KClass
import kotlin.streams.toList

val defaultMCTSAgent = """
        algorithm=MCTS
        timeBudget=50
        evalBudget=100000
        sequenceLength=12
        planningHorizon=100
        algoParams=C:3.0,maxActions:50,rolloutPolicy:DoNothing,selectionPolicy:SIMPLE,discountFactor:0.99
        opponentModel=DoNothing
    """.trimIndent()

val defaultRHEAAgent = """
        algorithm=RHEA
        timeBudget=50
        evalBudget=100000
        sequenceLength=4
        planningHorizon=100
        algoParams=probMutation:0.7,flipAtLeastOneValue,discountFactor:0.999
        opponentModel=DoNothing
    """.trimIndent()

fun agentParamsFromCommandLine(args: Array<String>, prefix: String, default: String = ""): AgentParams {
    val fileName = args.firstOrNull { it.startsWith(prefix) }?.split("=")?.get(1)
    if (fileName == null && args[0] == "Utility") throw AssertionError("Must specify a file for agent params (Agent=...) if using Utility Search space")
    return if (fileName == null) {
        if (default == "") {
            println("No data found for $prefix AgentParams: using default Heuristic")
            AgentParams("Heuristic", algoParams = "attack:3.0,defence:1.2,options:WITHDRAW|ATTACK")
        } else {
            println("No data found for $prefix AgentParams: using default")
            createAgentParamsFromString(default.split("\n"))
        }
    } else {
        val fileAsLines = BufferedReader(FileReader(fileName)).lines().toList()
        createAgentParamsFromString(fileAsLines)
    }
}

fun main(args: Array<String>) {
    if (args.size < 3) throw AssertionError("Must specify at least three parameters: RHEA/MCTS trials STD/EXP_MEAN/EXP_FULL:nn/EXP_SQRT:nn")
    val totalRuns = args[1].split("|")[0].toInt()
    val reportEvery = args[1].split("|").getOrNull(1)?.toInt() ?: totalRuns

    val agentParams = agentParamsFromCommandLine(args, "Agent")

    val searchSpace = when (args[0].split("|")[0]) {
        "RHEA" -> RHEASearchSpace(agentParamsFromCommandLine(args, "baseAgent", default = defaultRHEAAgent),
                fileName = args[0].split("|")[1])
        //    "MCTS", "MCTS2" -> MCTSSearchSpace
        //    "RHCA" -> RHCASearchSpace
        //    "Utility" -> UtilitySearchSpace(agentParams)
        else -> throw AssertionError("Unknown searchSpace " + args[0])
    }

    val params = {
        when (val fileName = args.firstOrNull { it.startsWith("GameParams=") }?.split("=")?.get(1)) {
            null -> {
                println("No GameParams specified - using default values")
                EventGameParams()
            }
            else -> {
                val fileAsLines = BufferedReader(FileReader(fileName)).lines().toList()
                createIntervalParamsFromString(fileAsLines).sampleParams()
            }
        }
    }.invoke()

    val landscapeModel: LandscapeModel = when {
        args[2] == "GP" -> GaussianProcessSearch("GP", searchSpace)
        args[2] == "STD" -> NTupleSystem(searchSpace)
        args[2] in listOf("EXP_MEAN") -> NTupleSystemExp(searchSpace, 30, expWeightExplore = false)
        args[2].startsWith("EXP_FULL") -> {
            val minWeight = args[2].split(":")[1].toDouble()
            NTupleSystemExp(searchSpace, 30, minWeight)
        }
        args[2].startsWith("EXP_SQRT:") -> {
            val minWeight = args[2].split(":")[1].toDouble()
            NTupleSystemExp(searchSpace, 30, minWeight, exploreWithSqrt = true)
        }
        args[2].startsWith("EXP_SQRT") -> {
            NTupleSystemExp(searchSpace, 30, expWeightExplore = false, exploreWithSqrt = true)
        }
        else -> throw AssertionError("Unknown NTuple parameter: " + args[2])
    }
    val use3Tuples = args.contains("useThreeTuples")
    if (landscapeModel is NTupleSystem) {
        landscapeModel.use3Tuple = use3Tuples
        landscapeModel.addTuples()
    }

    val stateSpaceSize = (0 until searchSpace.nDims()).fold(1, { acc, i -> acc * searchSpace.nValues(i) })
    val twoTupleSize = (0 until searchSpace.nDims() - 1).map { i ->
        searchSpace.nValues(i) * (i + 1 until searchSpace.nDims()).map(searchSpace::nValues).sum()
    }.sum()
    val threeTupleSize = (0 until searchSpace.nDims() - 2).map { i ->
        searchSpace.nValues(i) * (i + 1 until searchSpace.nDims()).map { j ->
            searchSpace.nValues(j) * (j + 1 until searchSpace.nDims()).map(searchSpace::nValues).sum()
        }.sum()
    }.sum()

    val searchFramework: EvoAlg = when (landscapeModel) {
        is NTupleSystem -> NTupleBanditEA(100.0, min(50.0, stateSpaceSize * 0.01).toInt())
        is GaussianProcessSearch -> GaussianProcessFramework()
        else -> throw AssertionError("Unknown EvoAlg $landscapeModel")
    }
    searchFramework.model = landscapeModel

    val logger = EvolutionLogger()
    println("Search space consists of $stateSpaceSize states and $twoTupleSize possible 2-Tuples" +
            "${if (use3Tuples) " and $threeTupleSize possible 3-Tuples" else ""}:")
    (0 until searchSpace.nDims()).forEach {
        println(String.format("%20s has %d values %s", searchSpace.name(it), searchSpace.nValues(it),
                (0 until searchSpace.nValues(it)).map { i -> searchSpace.value(it, i) }.joinToString()))
    }

    val groundWarEvaluator = GroundWarEvaluator(
            searchSpace,
            params,
            logger,
            agentParams,
            scoreFunctions = arrayOf(stringToScoreFunction(args.firstOrNull { it.startsWith("SCB|") }),
                    stringToScoreFunction(args.firstOrNull { it.startsWith("SCR|") })),
            victoryFunction = stringToScoreFunction(args.firstOrNull { it.startsWith("V|") })
    )

    repeat(totalRuns / reportEvery) {
        groundWarEvaluator.nEvals = 0
        searchFramework.runTrial(groundWarEvaluator, reportEvery)

        // tuples gets cleared out
        println("Current best sampled point (using mean estimate): " + landscapeModel.bestOfSampled.joinToString() +
                String.format(", %.3g", landscapeModel.getMeanEstimate(landscapeModel.bestOfSampled)))
        // println("Current best predicted point (using mean estimate): " + nTupleSystem.bestSolution.joinToString() +
        //         String.format(", %.3g", nTupleSystem.getMeanEstimate(nTupleSystem.bestSolution)))
        if (landscapeModel is NTupleSystem) {
            val tuplesExploredBySize = (1..searchSpace.nDims()).map { size ->
                landscapeModel.tuples.filter { it.tuple.size == size }
                        .map { it.ntMap.size }.sum()
            }

            println("Tuples explored by size: ${tuplesExploredBySize.joinToString()}")
            println("Summary of 1-tuple statistics after ${landscapeModel.numberOfSamples()} samples:")
            landscapeModel.tuples.withIndex().take(searchSpace.nDims()).forEach { (i, t) ->
                t.ntMap.toSortedMap().forEach { (k, v) ->
                    println(String.format("\t%20s\t%s\t%d trials\t mean %.3g +/- %.2g", searchSpace.name(i), k, v.n(), v.mean(), v.stdErr()))
                }
            }
            println("\nSummary of 10 most tried full-tuple statistics:")
            landscapeModel.tuples.find { it.tuple.size == searchSpace.nDims() }
                    ?.ntMap?.map { (k, v) -> k to v }?.sortedByDescending { (_, v) -> v.n() }?.take(10)?.forEach { (k, v) ->
                println(String.format("\t%s\t%d trials\t mean %.3g +/- %.2g\t(NTuple estimate: %.3g)", k, v.n(), v.mean(), v.stdErr(), landscapeModel.getMeanEstimate(k.v)))
            }
            println("\nSummary of 5 highest valued full-tuple statistics:")
            landscapeModel.tuples.find { it.tuple.size == searchSpace.nDims() }
                    ?.ntMap?.map { (k, v) -> k to v }?.sortedByDescending { (_, v) -> v.mean() }?.take(3)?.forEach { (k, v) ->
                println(String.format("\t%s\t%d trials\t mean %.3g +/- %.2g\t(NTuple estimate: %.3g)", k, v.n(), v.mean(), v.stdErr(), landscapeModel.getMeanEstimate(k.v)))
            }
            println("")
        }
    }

    val bestAgent = searchSpace.getAgent(landscapeModel.bestOfSampled)
    StatsCollator.clear()
    runGames(1000,
            bestAgent,
            agentParams.createAgent("opponent"),
            eventParams = params)
    println(StatsCollator.summaryString())
}

class GroundWarEvaluator(val searchSpace: HopshackleSearchSpace,
                         val params: EventGameParams,
                         val logger: EvolutionLogger,
                         val opponentParams: AgentParams,
                         val scoreFunctions: Array<(LandCombatGame, Int) -> Double> = arrayOf(finalScoreFunction, finalScoreFunction),
                         val victoryFunction: (LandCombatGame, Int) -> Double = finalScoreFunction
) : SolutionEvaluator {

    override fun optimalFound() = false

    override fun optimalIfKnown() = null

    override fun logger() = logger

    var nEvals = 0

    override fun evaluate(settings: IntArray): Double {
        return evaluate(searchSpace.convertSettings(settings))
    }

    override fun evaluate(settings: DoubleArray): Double {
        val seedToUse = System.currentTimeMillis()

        var finalScore = 0.0
        repeat(2) {
            val opponent = opponentParams.createAgent("target")
            val world = World(params = params.copy(seed = seedToUse))
            val game = LandCombatGame(world)
            if (it == 1) {
                if (searchSpace is UtilitySearchSpace) {
                    game.registerAgent(0, searchSpace.getAgent(doubleArrayOf())) // note that settings do not define this for UtilitySearch
                    game.registerAgent(1, searchSpace.getAgent(doubleArrayOf()))
                    game.scoreFunction[PlayerId.Red] = searchSpace.getScoreFunction(settings)
                    game.scoreFunction[PlayerId.Blue] = scoreFunctions[1]
                } else {
                    game.registerAgent(0, opponent)
                    game.registerAgent(1, searchSpace.getAgent(settings))
                    game.scoreFunction[PlayerId.Red] = scoreFunctions[0]
                    game.scoreFunction[PlayerId.Blue] = scoreFunctions[1]
                }
            } else {
                if (searchSpace is UtilitySearchSpace) {
                    game.registerAgent(0, searchSpace.getAgent(doubleArrayOf())) // note that settings do not define this for UtilitySearch
                    game.registerAgent(1, searchSpace.getAgent(doubleArrayOf()))
                    game.scoreFunction[PlayerId.Blue] = searchSpace.getScoreFunction(settings)
                    game.scoreFunction[PlayerId.Red] = scoreFunctions[1]
                } else {
                    game.registerAgent(0, searchSpace.getAgent(settings))
                    game.registerAgent(1, opponent)
                    game.scoreFunction[PlayerId.Red] = scoreFunctions[1]
                    game.scoreFunction[PlayerId.Blue] = scoreFunctions[0]
                }
            }

            game.next(1000)
            finalScore += victoryFunction(game, if (it == 1) 1 else 0)
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


abstract class HopshackleSearchSpace(val fileName: String) : SearchSpace {

    val searchDimensions: List<String> = if (fileName != "") FileReader(fileName).readLines() else emptyList()
    val searchKeys: List<String> = searchDimensions.map { it.split("=").first() }
    val searchTypes: List<KClass<*>> = searchKeys.map { types.getOrDefault(it, Int::class) }
    val searchValues: List<List<Any>> = searchDimensions.zip(searchTypes)
            .map { (allV, cl) ->
                allV.split("=")[1]      // get the stuff after the colon, which should be values to be searched
                        .split(",")
                        .map(String::trim).map {
                            when (cl) {
                                Int::class -> it.toInt()
                                Double::class -> it.toDouble()
                                Boolean::class -> it.toBoolean()
                                else -> throw AssertionError("Currently unsupported class $cl")
                            }
                        }
            }
    abstract val types: Map<String, KClass<*>>
    fun convertSettings(settings: IntArray): DoubleArray {
        val convertedSettings: DoubleArray = settings.zip(searchValues).map { (i, values) ->
            val v = values[i]
            when (v) {
                is Int -> v.toDouble()
                is Double -> values[i]
                is Boolean -> if (v) 1.0 else 0.0
                else -> settings[i].toDouble()      // if not numeric, default to the category
            } as Double
        }.toDoubleArray()
        return convertedSettings
    }

    abstract fun getAgent(settings: DoubleArray): SimpleActionPlayerInterface
    override fun nValues(i: Int) = searchValues[i].size
    override fun nDims() = searchValues.size
    override fun name(i: Int) = searchKeys[i]
    override fun value(d: Int, i: Int) = searchValues[d][i]
}

class RHEASearchSpace(val defaultParams: AgentParams, fileName: String) : HopshackleSearchSpace(fileName) {
    override val types: Map<String, KClass<*>>
        get() = mapOf("useShiftBuffer" to Boolean::class, "probMutation" to Double::class,
                "flipAtLeastOneValue" to Boolean::class, "discountFactor" to Double::class,
                "opponentModel" to SimpleActionPlayerInterface::class)

    /*  override val values: Array<Array<*>>
          get() = arrayOf(
                  arrayOf(3, 6, 12, 24, 48),                    // sequenceLength
                  arrayOf(50, 100, 200, 400),               // horizon
                  arrayOf(false, true),                                   // useShiftBuffer
                  arrayOf(0.003, 0.01, 0.03, 0.1, 0.3, 0.5, 0.7),         // probMutation
                  arrayOf(false, true),                           // flipAtLeastOne
                  arrayOf(1.0, 0.999, 0.99, 0.95),           // discount rate
                  arrayOf(SimpleActionDoNothing(1000), SimpleActionRandom,    // opponentModel
                          HeuristicAgent(3.0, 1.2, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK)),
                          HeuristicAgent(10.0, 2.0, listOf(HeuristicOptions.WITHDRAW)),
                          HeuristicAgent(2.0, 1.0, listOf(HeuristicOptions.ATTACK, HeuristicOptions.WITHDRAW))
                  )
          ) */

    override fun getAgent(settings: DoubleArray): SimpleActionPlayerInterface {
        val settingsMap: Map<String, Any> = settings.withIndex().map { (i, v) ->
            searchKeys[i] to when (searchTypes[i]) {
                Int::class -> (v + 0.5).toInt()
                Double::class -> v
                Boolean::class -> v > 0.5
                else -> throw AssertionError("Unsupported class ${searchTypes[i]}")
            }
        }.toMap()

        return SimpleActionEvoAgent(
                underlyingAgent = SimpleEvoAgent(
                        nEvals = 10000,
                        timeLimit = settingsMap.getOrDefault("timeBudget", defaultParams.timeBudget) as Int,
                        useMutationTransducer = false,
                        sequenceLength = settingsMap.getOrDefault("sequenceLength", defaultParams.sequenceLength) as Int,
                        horizon = settingsMap.getOrDefault("horizon", defaultParams.planningHorizon) as Int,
                        useShiftBuffer = settingsMap.getOrDefault("useShiftBuffer", defaultParams.params.contains("useShiftBuffer")) as Boolean,
                        probMutation = settingsMap.getOrDefault("probMutation", defaultParams.getParam("probMutation", "0.1").toDouble()) as Double,
                        flipAtLeastOneValue = settingsMap.getOrDefault("flipAtLeastOneValue", defaultParams.params.contains("flipAtLeastOneValue")) as Boolean,
                        discountFactor = settingsMap.getOrDefault("discountFactor", defaultParams.getParam("discountFactor", "1.0").toDouble()) as Double
                ),
                opponentModel = settingsMap.getOrDefault("opponentModel", SimpleActionDoNothing(1000)) as SimpleActionPlayerInterface
                //   opponentModel ?: SimpleActionDoNothing(1000) //RHEASearchSpace.values[5][settings[5]] as SimpleActionPlayerInterface
        )
    }

}

/*
object RHCASearchSpace : HopshackleSearchSpace() {

    override val names: Array<String>
        get() = arrayOf("sequenceLength", "horizon", "useShiftBuffer", "probMutation", "flipAtLeastOne", "populationSize", "parentSize", "evalsPerGeneration", "discountFactor")

    override val values: Array<Array<*>>
        get() = arrayOf(arrayOf(3, 6, 12, 24),                    // sequenceLength
                arrayOf(50, 100, 200, 400),               // horizon
                arrayOf(false, true),                                   // useShiftBuffer
                arrayOf(0.003, 0.01, 0.03, 0.1, 0.3, 0.5, 0.7),         // probMutation
                arrayOf(false, true),                           // flipAtLeastOne
                arrayOf(32, 64, 128),                       //populationSize
                arrayOf(1, 2, 4),                               // parentSize
                arrayOf(5, 10, 20, 30),                            // evalsPerGeneration
                arrayOf(1.0, 0.999, 0.99)            // discount rate
                /*       arrayOf(SimpleActionDoNothing(1000), SimpleActionRandom,    // opponentModel
                               HeuristicAgent(3.0, 1.2, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK)),
                               HeuristicAgent(10.0, 2.0, listOf(HeuristicOptions.WITHDRAW)),
                               HeuristicAgent(10.0, 2.0, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK))
                       ) */
        )
}

object MCTSSearchSpace : HopshackleSearchSpace() {

    override val names: Array<String>
        get() = arrayOf("maxDepth", "horizon", "pruneTree", "C", "maxActions", "rolloutPolicy", "discountFactor", "opponentModel")
    override val values: Array<Array<*>>
        get() = arrayOf(
                arrayOf(3, 6, 12),                  // maxDepth (==sequenceLength)
                arrayOf(50, 100, 200, 400),               // horizon
                arrayOf(false, true),                           // pruneTree
                arrayOf(0.03, 0.3, 3.0, 30.0),           // C
                arrayOf(20, 40, 80),             // maxActions
                arrayOf(SimpleActionDoNothing(1000), SimpleActionRandom),                          // rolloutPolicy
                arrayOf(1.0, 0.999, 0.99),            // discount rate
                arrayOf(SimpleActionDoNothing(1000), SimpleActionRandom,    // opponentModel
                        HeuristicAgent(3.0, 1.2, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK)),
                        HeuristicAgent(10.0, 2.0, listOf(HeuristicOptions.WITHDRAW)),
                        HeuristicAgent(2.0, 1.0, listOf(HeuristicOptions.ATTACK, HeuristicOptions.WITHDRAW))
                )
        )

    RHCASearchSpace -> RHCAAgent(
    timeLimit = timeBudget,
    sequenceLength = RHCASearchSpace.values[0][intSettings[0]] as Int,
    horizon = RHCASearchSpace.values[1][intSettings[1]] as Int,
    useShiftBuffer = RHCASearchSpace.values[2][intSettings[2]] as Boolean,
    probMutation = RHCASearchSpace.values[3][intSettings[3]] as Double,
    flipAtLeastOneValue = RHCASearchSpace.values[4][intSettings[4]] as Boolean,
    populationSize = RHCASearchSpace.values[5][intSettings[5]] as Int,
    parentSize = RHCASearchSpace.values[6][intSettings[6]] as Int,
    evalsPerGeneration = RHCASearchSpace.values[7][intSettings[7]] as Int,
    discountFactor = RHCASearchSpace.values[8][intSettings[8]] as Double
    )
    MCTSSearchSpace -> MCTSTranspositionTableAgentMaster(MCTSParameters(
    C = MCTSSearchSpace.values[3][intSettings[3]] as Double,
    maxPlayouts = 10000,
    timeLimit = timeBudget,
    horizon = MCTSSearchSpace.values[1][intSettings[1]] as Int,
    pruneTree = MCTSSearchSpace.values[2][intSettings[2]] as Boolean,
    maxDepth = MCTSSearchSpace.values[0][intSettings[0]] as Int,
    maxActions = MCTSSearchSpace.values[4][intSettings[4]] as Int,
    discountRate = MCTSSearchSpace.values[6][intSettings[6]] as Double
    ),
    stateFunction = LandCombatStateFunction,
    rolloutPolicy = MCTSSearchSpace.values[5][intSettings[5]] as SimpleActionPlayerInterface,
    opponentModel = MCTSSearchSpace.values[7][intSettings[7]] as SimpleActionPlayerInterface // opponentModel
    )
    is UtilitySearchSpace ->
    {
        searchSpace.agentParams.createAgent("UtilitySearch")
    }
    else -> throw AssertionError("Unknown type $searchSpace")


}
*/
class UtilitySearchSpace(val agentParams: AgentParams) : HopshackleSearchSpace("") {
    override val types: Map<String, KClass<*>>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun getAgent(settings: DoubleArray): SimpleActionPlayerInterface {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    val names: Array<String>
        get() = arrayOf("visibilityNode", "visibilityArc", "theirCity", "ownForce", "theirForce")
    val values: Array<Array<*>>
        get() = arrayOf(
                arrayOf(0.0, 0.1, 0.5, 1.0, 5.0),
                arrayOf(0.0, 0.1, 0.5, 1.0, 5.0),
                arrayOf(0.0, -1.0, -5.0, -10.0),
                arrayOf(0.0, 0.1, 0.5, 1.0, 3.0),
                arrayOf(0.0, -0.1, -0.5, -1.0, -3.0)
        )

    fun getScoreFunction(settings: DoubleArray): (LandCombatGame, Int) -> Double {
        val intSettings = settings.map { v -> (v + 0.5).toInt() }.toIntArray()
        return compositeScoreFunction(
                visibilityScore(values[0][intSettings[0]] as Double, values[1][intSettings[1]] as Double),
                simpleScoreFunction(5.0, values[3][intSettings[3]] as Double,
                        values[2][intSettings[2]] as Double, values[4][intSettings[4]] as Double)
        )
    }

}
