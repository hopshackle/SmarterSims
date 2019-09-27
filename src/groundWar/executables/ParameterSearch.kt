package groundWar.executables

import agents.*
import agents.RHEA.*
import agents.MCTS.*
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

val defaultRHCAAgent = """
        algorithm=RHCA
        timeBudget=50
        evalBudget=100000
        sequenceLength=4
        planningHorizon=100
        algoParams=probMutation:0.7,flipAtLeastOneValue,discountFactor:0.999,populationSize=20,parentSize=1,evalsPerGeneration=20
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

fun scoreParamsFromCommandLine(args: Array<String>, prefix: String): ScoreParams {
    val fileName = args.firstOrNull { it.startsWith(prefix) }?.split("=")?.get(1)
    return if (fileName == null) {
        println("No data found for $prefix ScoreParams: using default")
        ScoreParams()
    } else {
        val fileAsLines = BufferedReader(FileReader(fileName)).lines().toList()
        createScoreParamsFromString(fileAsLines)
    }
}

fun main(args: Array<String>) {
    if (args.contains("--help") || args.contains("-h")) println(
            """The first three arguments must be
                |- search type <RHEA|MCTS|RHCA|Utility>|<filename for searchSpace definition>
                |- number of runs|report every
                |- search algorithm <STD|EXP_MEAN|EXP_SQRT|GP|EXP_FULL:dd> where dd is in [0.0, 1.0]
                |
                |Then there are a number of optional arguments:
                |baseAgent=     The filename for the baseAgent (from which the searchSpace definition deviates)
                |Agent=         The filename for the agent used as the opponent (for Utility, this is used on both sides!)
                |GameParams=    The filename with game params to use
                |useThreeTuples If specified and not using GP, then we use 3-tuples as well as 1-, 2- and N-tuples
                |SCR=           Score function for Red
                |SCB=           Score function for Blue      
                |fortVictory    If specified, then instead of using material advantage, we score a game only on difference in forts taken
            """.trimMargin()
    )

    if (args.size < 3) throw AssertionError("Must specify at least three parameters: RHEA/MCTS trials STD/EXP_MEAN/EXP_FULL:nn/EXP_SQRT:nn")
    val totalRuns = args[1].split("|")[0].toInt()
    val reportEvery = args[1].split("|").getOrNull(1)?.toInt() ?: totalRuns

    val agentParams = agentParamsFromCommandLine(args, "Agent")
    val fortVictory = args.contains("fortVictory")

    val searchSpace = when (args[0].split("|")[0]) {
        "RHEA" -> RHEASearchSpace(agentParamsFromCommandLine(args, "baseAgent", default = defaultRHEAAgent),
                fileName = args[0].split("|")[1])
        "MCTS" -> MCTSSearchSpace(agentParamsFromCommandLine(args, "baseAgent", default = defaultMCTSAgent),
                fileName = args[0].split("|")[1])
        "RHCA" -> RHCASearchSpace(agentParamsFromCommandLine(args, "baseAgent", default = defaultRHCAAgent),
                fileName = args[0].split("|")[1])
        "Utility" -> UtilitySearchSpace(agentParams, scoreParamsFromCommandLine(args, "baseScore"),
                fileName = args[0].split("|")[1])
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
        is GaussianProcessSearch -> {
            val retValue = GaussianProcessFramework()
            retValue.nSamples = 5
            retValue
        }
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
            fortVictory = fortVictory
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

    val bestAgent = when (landscapeModel) {
        is GaussianProcessSearch -> searchSpace.getAgent(landscapeModel.bestOfSampled)
        else -> searchSpace.getAgent(searchSpace.convertSettings(landscapeModel.bestOfSampled.map { it.toInt() }.toIntArray()))
    }
    StatsCollator.clear()
    runGames(1000,
            bestAgent,
            agentParams.createAgent("opponent"),
            scoreFunctions = arrayOf(stringToScoreFunction(args.firstOrNull { it.startsWith("SCB|") }),
                    stringToScoreFunction(args.firstOrNull { it.startsWith("SCR|") })),
            eventParams = params,
            fortVictory = fortVictory)
    println(StatsCollator.summaryString())
}

class GroundWarEvaluator(val searchSpace: HopshackleSearchSpace,
                         val params: EventGameParams,
                         val logger: EvolutionLogger,
                         val opponentParams: AgentParams,
                         val scoreFunctions: Array<(LandCombatGame, Int) -> Double> = arrayOf(finalScoreFunction, finalScoreFunction),
                         val fortVictory: Boolean
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
            val objectivePlayer = if (it == 1) 1 else 0

            if (fortVictory)
                game.victoryFunction[numberToPlayerID(objectivePlayer)] = allFortsConquered(numberToPlayerID(objectivePlayer))
            game.next(1000)

            finalScore += if (fortVictory)
                fortressScore(10.0)(game, objectivePlayer)
            else
                finalScoreFunction(game, objectivePlayer)
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


abstract class HopshackleSearchSpace(fileName: String) : SearchSpace {

    val searchDimensions: List<String> = if (fileName != "") FileReader(fileName).readLines() else emptyList()
    val searchKeys: List<String> = searchDimensions.map { it.split("=").first() }
    val searchTypes: List<KClass<*>> = searchKeys.map {
        types[it] ?: throw AssertionError("Unknown search variable $it")
    }
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
        return settings.zip(searchValues).map { (i, values) ->
            val v = values[i]
            when (v) {
                is Int -> v.toDouble()
                is Double -> values[i]
                is Boolean -> if (v) 1.0 else 0.0
                else -> settings[i].toDouble()      // if not numeric, default to the category
            } as Double
        }.toDoubleArray()
    }

    fun settingsToMap(settings: DoubleArray): Map<String, Any> {
        return settings.withIndex().map { (i, v) ->
            searchKeys[i] to when (searchTypes[i]) {
                Int::class -> (v + 0.5).toInt()
                Double::class -> v
                Boolean::class -> v > 0.5
                else -> throw AssertionError("Unsupported class ${searchTypes[i]}")
            }
        }.toMap()
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
                "sequenceLength" to Int::class,
                "horizon" to Int::class, "timeBudget" to Int::class,
                "opponentWithdraw" to Boolean::class, "opponentAttack" to Double::class, "opponentDefense" to Double::class)

    override fun getAgent(settings: DoubleArray): SimpleActionPlayerInterface {

        val settingsMap = settingsToMap(settings)
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
                opponentModel = defaultParams.getOpponentModel(settingsMap)
        )
    }
}

class RHCASearchSpace(val defaultParams: AgentParams, fileName: String) : HopshackleSearchSpace(fileName) {

    override val types: Map<String, KClass<*>>
        get() = mapOf("useShiftBuffer" to Boolean::class, "probMutation" to Double::class,
                "flipAtLeastOneValue" to Boolean::class, "discountFactor" to Double::class,
                "sequenceLength" to Int::class,
                "horizon" to Int::class, "populationSize" to Int::class, "parentSize" to Int::class,
                "evalsPerGeneration" to Int::class)

    override fun getAgent(settings: DoubleArray): SimpleActionPlayerInterface {
        val settingsMap = settingsToMap(settings)

        return RHCAAgent(
                timeLimit = settingsMap.getOrDefault("timeBudget", defaultParams.timeBudget) as Int,
                sequenceLength = settingsMap.getOrDefault("sequenceLength", defaultParams.sequenceLength) as Int,
                horizon = settingsMap.getOrDefault("horizon", defaultParams.planningHorizon) as Int,
                useShiftBuffer = settingsMap.getOrDefault("useShiftBuffer", defaultParams.params.contains("useShiftBuffer")) as Boolean,
                probMutation = settingsMap.getOrDefault("probMutation", defaultParams.getParam("probMutation", "0.1").toDouble()) as Double,
                flipAtLeastOneValue = settingsMap.getOrDefault("flipAtLeastOneValue", defaultParams.params.contains("flipAtLeastOneValue")) as Boolean,
                populationSize = settingsMap.getOrDefault("populationSize", defaultParams.getParam("populationSize", "10").toInt()) as Int,
                parentSize = settingsMap.getOrDefault("parentSize", defaultParams.getParam("parentSize", "1").toInt()) as Int,
                evalsPerGeneration = settingsMap.getOrDefault("evalsPerGeneration", defaultParams.getParam("evalsPerGeneration", "10").toInt()) as Int,
                discountFactor = settingsMap.getOrDefault("discountFactor", defaultParams.getParam("discountFactor", "1.0").toDouble()) as Double
        )
    }
}

class MCTSSearchSpace(val defaultParams: AgentParams, fileName: String) : HopshackleSearchSpace(fileName) {

    override val types: Map<String, KClass<*>>
        get() = mapOf("sequenceLength" to Int::class, "horizon" to Int::class, "pruneTree" to Boolean::class,
                "C" to Double::class, "maxActions" to Int::class, "rolloutPolicy" to SimpleActionPlayerInterface::class,
                "discountFactor" to Double::class, "opponentMCTS" to Boolean::class, "timeBudget" to Int::class,
                "opponentWithdraw" to Boolean::class, "opponentAttack" to Double::class, "opponentDefense" to Double::class)

    override fun getAgent(settings: DoubleArray): SimpleActionPlayerInterface {
        val settingsMap = settingsToMap(settings)
        return MCTSTranspositionTableAgentMaster(MCTSParameters(
                C = settingsMap.getOrDefault("C", defaultParams.getParam("C", "0.1").toDouble()) as Double,
                maxPlayouts = 10000,
                timeLimit = settingsMap.getOrDefault("timeBudget", defaultParams.timeBudget) as Int,
                horizon = settingsMap.getOrDefault("horizon", defaultParams.planningHorizon) as Int,
                pruneTree = settingsMap.getOrDefault("pruneTree", defaultParams.params.contains("pruneTree")) as Boolean,
                maxDepth = settingsMap.getOrDefault("sequenceLength", defaultParams.getParam("sequenceLength", "10").toInt()) as Int,
                maxActions = settingsMap.getOrDefault("maxActions", defaultParams.getParam("maxActions", "10").toInt()) as Int,
                discountRate = settingsMap.getOrDefault("discountFactor", defaultParams.getParam("discountFactor", "1.0").toDouble()) as Double
        ),
                stateFunction = LandCombatStateFunction,
                rolloutPolicy = settingsMap.getOrDefault("rolloutPolicy", SimpleActionDoNothing(1000)) as SimpleActionPlayerInterface,
                opponentModel = when {
                    (settingsMap.getOrDefault("opponentMCTS", false) as Boolean) -> null
                    defaultParams.opponentModel == "null" -> null
                    else -> defaultParams.getOpponentModel(settingsMap)
                }
        )
    }
}

class UtilitySearchSpace(val agentParams: AgentParams, val defaultScore: ScoreParams, fileName: String) : HopshackleSearchSpace(fileName) {
    override val types: Map<String, KClass<*>>
        get() = mapOf("visibilityNode" to Double::class, "visibilityArc" to Double::class, "theirCity" to Double::class,
                "ownForce" to Double::class, "theirForce" to Double::class, "fortressValue" to Double::class)

    override fun getAgent(settings: DoubleArray): SimpleActionPlayerInterface {
        return agentParams.createAgent("STD")
    }

    fun getScoreFunction(settings: DoubleArray): (LandCombatGame, Int) -> Double {
        val settingsMap = settingsToMap(settings)
        return compositeScoreFunction(
                visibilityScore(
                        settingsMap.getOrDefault("visibilityNode", defaultScore.nodeVisibility) as Double,
                        settingsMap.getOrDefault("visibilityArc", defaultScore.arcVisibility) as Double),
                simpleScoreFunction(
                        5.0,
                        settingsMap.getOrDefault("ownForce", defaultScore.ownForce) as Double,
                        settingsMap.getOrDefault("theirCity", defaultScore.theirCity) as Double,
                        settingsMap.getOrDefault("theirForce", defaultScore.theirForce) as Double),
                fortressScore(
                        settingsMap.getOrDefault("fortressValue", defaultScore.fortressValue) as Double)
        )
    }
}
