package groundWar.executables

import evodef.*
import ggi.*
import groundWar.*
import mathFunctions.*
import ntbea.*
import olderGames.asteroids.AsteroidsEvaluator
import olderGames.planetWars.PlanetWarEvaluator
import utilities.*
import java.io.*
import kotlin.math.*
import kotlin.streams.toList
import kotlinx.coroutines.*

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
                |- search type <RHEA|MCTS|RHCA|Utility|Function>|<filename for searchSpace definition>
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
                |kExplore=      The k to use in NTBEA - defaults to 100.0
                |T=             The T parameter in EXP/LIN/INV/SQRT weight functions
                |hood=          The size of neighbourhood to look at in NTBEA. Default is min(50, |searchSpace|/100)
                |game=          PlanetWars/Asteroids/Hartmann3
            """.trimMargin()
    )

    fun <T> argParam(name: String, default: T): T {
        val fn: (String) -> T = when (default) {
            is Int -> { x: String -> x.toInt() as T }
            is Double -> { x: String -> x.toDouble() as T }
            is Boolean -> { x: String -> x.toBoolean() as T }
            else -> { x: String -> x as T }
        }
        val v: String? = args.find { it.startsWith(name + "=") }?.split("=")?.getOrNull(1)
        return if (v == null) default else fn(v)
    }

    if (args.size < 3) throw AssertionError("Must specify at least three parameters: RHEA/MCTS trials STD/EXP_MEAN/EXP_FULL:nn/EXP_SQRT:nn")
    val totalRuns = args[1].split("|")[0].toInt()
    val reportEvery = args[1].split("|").getOrNull(1)?.toInt() ?: totalRuns
    val repeats = argParam("repeat", 1)
    val agentParams = agentParamsFromCommandLine(args, "Agent")
    val fortVictory = args.contains("fortVictory")
    val game = argParam("game", "")
    val kExplore = argParam("kExplore", 100.0)
    val T = argParam("T", 30)
    val evalGames = argParam("evalGames", 1000)

    val searchSpace = when (args[0].split("|")[0]) {
        "RHEASimple" -> RHEASimpleSearchSpace(agentParamsFromCommandLine(args, "baseAgent", default = defaultRHEAAgent),
                fileName = args[0].split("|")[1])
        "Heuristic" -> HeuristicSearchSpace(agentParamsFromCommandLine(args, "baseAgent"),
                fileName = args[0].split("|")[1])
        "RHEA" -> RHEASearchSpace(agentParamsFromCommandLine(args, "baseAgent", default = defaultRHEAAgent),
                fileName = args[0].split("|")[1])
        "MCTS" -> MCTSSearchSpace(agentParamsFromCommandLine(args, "baseAgent", default = defaultMCTSAgent),
                fileName = args[0].split("|")[1])
        "RHCA" -> RHCASearchSpace(agentParamsFromCommandLine(args, "baseAgent", default = defaultRHCAAgent),
                fileName = args[0].split("|")[1])
        "Utility" -> UtilitySearchSpace(agentParams, scoreParamsFromCommandLine(args, "baseScore"),
                fileName = args[0].split("|")[1])
        "Function" -> FunctionSearchSpace(args[0].split("|")[1].toInt(), args[0].split("|")[2].toInt())
        else -> throw AssertionError("Unknown searchSpace " + args[0])
    }

    val params = {
        when (val fileName = argParam("GameParams", "")) {
            "" -> {
                println("No GameParams specified - using default values")
                EventGameParams()
            }
            else -> {
                val fileAsLines = BufferedReader(FileReader(fileName)).lines().toList()
                createIntervalParamsFromString(fileAsLines).sampleParams()
            }
        }
    }.invoke()

    val arg2 = args[2].split("_")

    val weightFunction: (Int) -> Double = when (arg2[0]) {
        "EXP" -> { visits: Int -> 1.0 - exp(-visits.toDouble() / T) }
        "LIN" -> { visits: Int -> min(visits.toDouble() / T, 1.0) }
        "INV" -> { visits: Int -> 1.0 - T / (T + visits.toDouble()) }
        "SQRT" -> { visits: Int -> 1.0 - sqrt(T / (T + visits.toDouble())) }
        else -> { visits: Int -> 1.0 - exp(-visits.toDouble() / T) }
        // we default to exponential
    }
    val minWeight = when {
        args[2].split(":").size > 1 -> args[2].split(":")[1].toDouble()
        else -> 0.0
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

    val neighbourhood = argParam("hood", min(50.0, stateSpaceSize * 0.01))
    val threadCount = argParam("CPU", 1)
    val use3Tuples = args.contains("useThreeTuples")

    val logger = EvolutionLogger()
    println("Search space consists of $stateSpaceSize states and $twoTupleSize possible 2-Tuples" +
            "${if (use3Tuples) " and $threeTupleSize possible 3-Tuples" else ""}:")
    (0 until searchSpace.nDims()).forEach {
        println(String.format("%20s has %d values %s", searchSpace.name(it), searchSpace.nValues(it),
                (0 until searchSpace.nValues(it)).map { i -> searchSpace.value(it, i) }.joinToString()))
    }

    val stuff = (1..repeats).map {
        val landscapeModel: LandscapeModel = when {
            args[2] == "GP" -> GaussianProcessSearch("GP", searchSpace)
            args[2] == "STD" -> NTupleSystem(searchSpace)
            arg2.size != 2 -> throw AssertionError("Invalid second argument " + args[2])
            arg2[1].startsWith("MEAN") -> NTupleSystemExp(searchSpace, T, minWeight, weightFunction, weightExplore = false, exploreWithSqrt = false)
            arg2[1].startsWith("FULL") -> NTupleSystemExp(searchSpace, T, minWeight, weightFunction, weightExplore = true, exploreWithSqrt = false)
            arg2[1].startsWith("SQRT") -> NTupleSystemExp(searchSpace, T, minWeight, weightFunction, weightExplore = false, exploreWithSqrt = true)
            else -> throw AssertionError("Unknown NTuple parameter: " + args[2])
        }

        if (landscapeModel is NTupleSystem) {
            landscapeModel.use3Tuple = use3Tuples
            landscapeModel.addTuples()
        }

        val searchFramework: EvoAlg = when (landscapeModel) {
            is NTupleSystem -> NTupleBanditEA(kExplore, neighbourhood.toInt())
            is GaussianProcessSearch -> {
                val retValue = GaussianProcessFramework()
                retValue.nSamples = 5
                retValue
            }
            else -> throw AssertionError("Unknown EvoAlg $landscapeModel")
        }
        searchFramework.model = landscapeModel
        val evaluator = when (game) {
            "Hartmann3" -> FunctionEvaluator(Hartmann3, searchSpace as FunctionSearchSpace)
            "Hartmann6" -> FunctionEvaluator(Hartmann6, searchSpace as FunctionSearchSpace)
            "Branin" -> FunctionEvaluator(Branin, searchSpace as FunctionSearchSpace)
            "GoldsteinPrice" -> FunctionEvaluator(GoldsteinPrice, searchSpace as FunctionSearchSpace)
            "PlanetWars" -> PlanetWarEvaluator(searchSpace as HopshackleSearchSpace<SimplePlayerInterface>, logger, agentParams)
            "Asteroids" -> AsteroidsEvaluator(searchSpace as HopshackleSearchSpace<SimplePlayerInterface>, logger)
            else -> GroundWarEvaluator(
                    searchSpace as HopshackleSearchSpace<SimpleActionPlayerInterface>,
                    params,
                    logger,
                    agentParams,
                    scoreFunctions = arrayOf(stringToScoreFunction(args.firstOrNull { it.startsWith("SCB|") }),
                            stringToScoreFunction(args.firstOrNull { it.startsWith("SCR|") })),
                    fortVictory = fortVictory
            )
        }
        Triple(evaluator, landscapeModel, searchFramework)
    }

    val context = newFixedThreadPoolContext(threadCount, "NTBEA")
    val scope = CoroutineScope(context)
    val startTime = System.currentTimeMillis()
    val deferredResults = (0 until repeats).map { i ->
        scope.async(context) {
            val r = runNTBEA(stuff[i].first, stuff[i].third, totalRuns, reportEvery, evalGames = evalGames, logResults = false)
            println(String.format("Recommended settings at time = %d have score %.3g +/- %.3g:\t%s\n %s",
                    ((System.currentTimeMillis() - startTime) / 1000).toInt(),
                    r.first, r.second,
                    stuff[i].second.bestOfSampled.joinToString(", ") { String.format("%.0f", it) },
                    stuff[i].second.bestOfSampled.withIndex().joinToString(separator = "") { (i, data) ->
                        String.format("\t%s:\t%s\n",
                                searchSpace.name(i),
                                when (val v = searchSpace.value(i, data.toInt())) {
                                    is Int -> String.format("%d", v)
                                    is Double -> String.format("%.3g", v)
                                    else -> v.toString()
                                })
                    }))
            Triple(r.first, r.second, stuff[i].second.bestOfSampled)
        }
    }
    runBlocking {
        val bestResult = deferredResults.map { it.await() }.sortedBy { -it.first }[0]
        println(String.format("Recommended settings have score %.3g +/- %.3g:\t%s\n %s",
                bestResult.first, bestResult.second,
                bestResult.third.joinToString(", ") { String.format("%.0f", it) },
                bestResult.third.withIndex().joinToString(separator = "") { (i, data) ->
                    String.format("\t%s:\t%s\n",
                            searchSpace.name(i),
                            when (val v = searchSpace.value(i, data.toInt())) {
                                is Int -> String.format("%d", v)
                                is Double -> String.format("%.3g", v)
                                else -> v.toString()
                            })
                }))

    }
}

/*
The final result will be held in searchFramework.landscapeModel.bestOfSample
 */
fun runNTBEA(evaluator: SolutionEvaluator,
             searchFramework: EvoAlg,
             totalRuns: Int, reportEvery: Int = totalRuns,
             evalGames: Int = 1000, logResults: Boolean = true
): Pair<Double, Double> {
    val landscapeModel = searchFramework.model
    val searchSpace = landscapeModel.searchSpace

    repeat(totalRuns / reportEvery) {
        evaluator.reset()
        searchFramework.runTrial(evaluator, reportEvery)

        // tuples gets cleared out
        if (logResults)
            println("Current best sampled point (using mean estimate): " + landscapeModel.bestOfSampled.joinToString() +
                    String.format(", %.3g", landscapeModel.getMeanEstimate(landscapeModel.bestOfSampled)))
        // println("Current best predicted point (using mean estimate): " + nTupleSystem.bestSolution.joinToString() +
        //         String.format(", %.3g", nTupleSystem.getMeanEstimate(nTupleSystem.bestSolution)))
        if (landscapeModel is NTupleSystem && logResults) {
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
    if (evalGames > 0) {
        StatsCollator.clear()
        val results = (1..evalGames).map {
            evaluator.evaluate(landscapeModel.bestOfSampled.map(Double::toInt).toIntArray())
        }
        if (logResults)
            println(StatsCollator.summaryString())
        val avg = results.average()
        val stdErr = sqrt(
                results.map { (it - avg).pow(2) }
                        .sum()
                        / (evalGames - 1)
        ) / sqrt(evalGames - 1.0)
        return Pair(avg, stdErr)
    } else {
        return Pair(landscapeModel.getMeanEstimate(landscapeModel.bestOfSampled) ?: 0.0, 0.0)
    }
}

class GroundWarEvaluator(val searchSpace: HopshackleSearchSpace<SimpleActionPlayerInterface>,
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

    override fun reset() {
        nEvals = 0
    }

    override fun nEvals() = nEvals
}

