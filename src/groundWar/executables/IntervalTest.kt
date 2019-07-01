package groundWar.executables

import agents.*
import ggi.SimpleActionEvoAgent
import groundWar.*
import intervals.interval
import utilities.StatsCollator
import java.io.BufferedReader
import java.io.FileReader
import java.io.FileWriter
import kotlin.random.Random
import kotlin.reflect.full.memberProperties
import kotlin.streams.toList

fun main(args: Array<String>) {
    // the args input needs to be the Intervals that we wish to vary
    // or, better, a file that contains all the parameters and intervals
    // this cna have lines of the form:
    //      minConnections = 2              - a single parameter setting
    //      OODALoop = 10 : [5, 50]          - a single parameter setting for BLUE, and an Interval for RED

    // initially however I will hard-code this here

    val simsToRun = if (args.size > 0) args[0].toInt() else 100
    val inputFile = if (args.size > 1) args[1] else ""
    val outputFile = if (args.size > 2) args[2] else "output.txt"

    if (inputFile == "") throw AssertionError("Must provide input file for intervals as second parameter")
    val fileAsLines = BufferedReader(FileReader(inputFile)).lines().toList()
    val intervalParams = createIntervalParamsFromString(fileAsLines)


    val numberFormatter: (Any?) -> String = { it ->
        when (it) {
            null -> "null"
            is Int -> String.format("%d", it)
            is Double -> String.format("%.4g", it)
            else -> it.toString()
        }
    }

    val statisticsToKeep: Map<String, (LandCombatGame) -> Number> = mapOf(
            "BLUE_WINS" to { g: LandCombatGame -> if (g.score(0) - g.score(1) > 0.0) 1 else 0 },
            "RED_WINS" to { g: LandCombatGame -> if (g.score(0) - g.score(1) < 0.0) 1 else 0 },
            "BLUE_FORCE" to { g: LandCombatGame -> simpleScoreFunction(0.0, 1.0, 0.0, 0.0).invoke(g, 0) },
            "RED_FORCE" to { g: LandCombatGame -> simpleScoreFunction(0.0, 1.0, 0.0, 0.0).invoke(g, 1) },
            "BLUE_CITIES" to { g: LandCombatGame -> simpleScoreFunction(1.0, 0.0, 0.0, 0.0).invoke(g, 0).toInt() },
            "RED_CITIES" to { g: LandCombatGame -> simpleScoreFunction(1.0, 0.0, 0.0, 0.0).invoke(g, 1).toInt() },
            "DRAW" to { g: LandCombatGame -> if (g.score(0) == g.score(1)) 1 else 0 },
            "BLUE_SCORE" to { g: LandCombatGame -> g.score(0) },
            "RED_SCORE" to { g: LandCombatGame -> g.score(1) },
            "GAME_LENGTH" to { g: LandCombatGame -> g.nTicks() },
            "COMPLETE_VICTORY" to { g: LandCombatGame -> if (g.nTicks() < 1000) 1 else 0 }
    )
    val statisticKeys = statisticsToKeep.keys.toList()
    val gameParamNames = EventGameParams::class.memberProperties.map { it.name }.toList()
    val (gameParamKeys, otherKeys) = IntervalParams::class.memberProperties
            .partition { it.name in gameParamNames }.toList()
            .map {
                it.flatMap { x ->
                    when {
                        x.returnType.toString().startsWith("kotlin.collections.List") -> listOf("BLUE_" + x.name, "RED_" + x.name)
                        else -> listOf(x.name)
                    }
                }
            }

    if (otherKeys.size > 0) throw AssertionError("We have tried to specify an interval for a non-existent game parameter " + otherKeys.toString())

    val fileWriter = FileWriter(outputFile)
    fileWriter.write(gameParamKeys.joinToString(separator = "\t", postfix = "\t"))
    fileWriter.write(statisticKeys.joinToString(separator = "\t", postfix = "\n"))
    StatsCollator.clear()
    repeat(simsToRun) {

        val params = intervalParams.sampleParams()
        val game = LandCombatGame(World(params = params))
        game.scoreFunction[PlayerId.Blue] = compositeScoreFunction(
                simpleScoreFunction(5.0, 1.0, 0.0, -0.5),
                visibilityScore(0.0, 0.0)
                //   game.scoreFunction = specificTargetScoreFunction(50.0)
        )
        game.scoreFunction[PlayerId.Red] = compositeScoreFunction(
                simpleScoreFunction(5.0, 1.0, 0.0, -0.5),
                visibilityScore(0.0, 0.0)
                //   game.scoreFunction = specificTargetScoreFunction(50.0)
        )
        val blueOpponentModel =
                DoNothingAgent()
        //HeuristicAgent(2.0, 1.1)
        //        SimpleActionEvoAgent(SimpleEvoAgent(name = "OppEA", nEvals = 10, sequenceLength = 40, useMutationTransducer = false, probMutation = 0.1, horizon = params.planningHorizon))
        val blueAgent = SimpleActionEvoAgent(SimpleEvoAgent(nEvals = 200, timeLimit = 100, sequenceLength = 40,
                useMutationTransducer = false, probMutation = 0.1, useShiftBuffer = true,
                horizon = 100, opponentModel = blueOpponentModel)
        )
        game.registerAgent(0, blueAgent)
        val redAgent =
                SimpleActionEvoAgent(SimpleEvoAgent(nEvals = 200, timeLimit = 100, sequenceLength = 40,
                        useMutationTransducer = false, probMutation = 0.1, useShiftBuffer = true,
                        horizon = 100))
        //      MCTSTranspositionTableAgentMaster(MCTSParameters(timeLimit = 100, maxPlayouts = 1000, horizon = params.planningHorizon[1]), LandCombatStateFunction)
        //       HeuristicAgent(params.minAssaultFactor[1], 1.1)
        game.registerAgent(1, redAgent)

        game.next(1000)


        val propertyMap = EventGameParams::class.memberProperties.map { it.name to it.get(params) }.toMap().toMutableMap()
        fun propertyValue(key: String, position: Int): Number {
            return when (val array = (propertyMap[key])) {
                is IntArray -> array[position]
                is DoubleArray -> array[position]
                else -> throw AssertionError("Expecting Array")
            }
        }

        fileWriter.write(gameParamKeys.map { key ->
            when {
                key.startsWith("BLUE_") -> propertyValue(key.removePrefix("BLUE_"), 0)
                key.startsWith("RED_") -> propertyValue(key.removePrefix("RED_"), 1)
                else -> propertyMap.get(key)
            }
        }.joinToString(separator = "\t", postfix = "\t", transform = numberFormatter))

        val gameStats: Map<String, Number> = statisticKeys.map { it to statisticsToKeep.get(it)!!(game) }.toMap()

        fileWriter.write(statisticKeys.map(gameStats::get).joinToString(separator = "\t", postfix = "\n", transform = numberFormatter))
        fileWriter.flush()
        StatsCollator.addStatistics(gameStats)
    }
    fileWriter.close()
    println(StatsCollator.summaryString())
}