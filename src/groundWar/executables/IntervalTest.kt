package groundWar.executables

import agents.*
import agents.RHEA.*
import groundWar.*
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
    if (args.contains("--help") || args.contains("-h")) println(
            """The first three arguments must be
                |- number of simulations to run
                |- input file name with Interval details
                |- output file name to write results to
                |
                |Then there are a number of optional arguments:
                |SCB|       <The Blue Score function>
                |SCR|       <The Red Score function>
                |blue=      file with parameterisation of the blue AI agent
                |red=       file with parameterisation of the red AI agent
                |map=       JSON file with map to use for all simulations
                |VB=targets    <The Blue victory function will be to control all locations with VP>
                |VR=targets    <The Red victory function will be to control all locations with VP>
            """.trimMargin()
    )

    val simsToRun = if (args.size > 0) args[0].toInt() else 100
    val inputFile = if (args.size > 1) args[1] else ""
    val outputFile = if (args.size > 2) args[2] else "output.txt"

    if (inputFile == "") throw AssertionError("Must provide input file for intervals as third parameter")
    val fileAsLines = BufferedReader(FileReader(inputFile)).lines().toList()
    val intervalParams = createIntervalParamsFromString(fileAsLines)

    val blueScoreFunction = stringToScoreFunction(args.firstOrNull { it.startsWith("SCB") })
    val redScoreFunction = stringToScoreFunction(args.firstOrNull { it.startsWith("SCR") })

    val blueAgentParams = agentParamsFromCommandLine(args, "blue", default = defaultBlueAgent) // defaults from ControlView
    val redAgentParams = agentParamsFromCommandLine(args, "red", default = defaultRedAgent)

    val mapOverride: String = args.firstOrNull { it.startsWith("map") }?.split("=")?.get(1) ?: ""
    val targetMap = if (mapOverride == "") emptyMap() else {
        val mapFileAsLines = BufferedReader(FileReader(mapOverride)).readLines().joinToString("\n")
        victoryValuesFromJSON(mapFileAsLines)
    }

    val victoryFunctions: Map<PlayerId, (LandCombatGame) -> Boolean> = mapOf(
            PlayerId.Blue to (if (args.contains("VB=targets")) {
                allTargetsConquered(PlayerId.Blue, targetMap)
            } else { game: LandCombatGame -> defaultScoreFunctions[PlayerId.Blue]?.invoke(game, 0) ?: 0.00 > defaultScoreFunctions[PlayerId.Red]?.invoke(game, 1) ?: 0.00 }),
            PlayerId.Red to (if (args.contains("VR=targets")) {
                allTargetsConquered(PlayerId.Red, targetMap)
            } else { game: LandCombatGame -> defaultScoreFunctions[PlayerId.Red]?.invoke(game, 1) ?: 0.00 > defaultScoreFunctions[PlayerId.Blue]?.invoke(game, 0) ?: 0.00 })
    )

    val numberFormatter: (Any?) -> String = { it ->
        when (it) {
            null -> "null"
            is Int -> String.format("%d", it)
            is Double -> String.format("%.4g", it)
            else -> it.toString()
        }
    }
    val tMap: Map<String, (LandCombatGame) -> Number> = targetMap.map { (targetName, _) ->
        "${targetName}_BLUE" to { g: LandCombatGame -> if (g.world.cities.first { it.name == targetName }?.owner == PlayerId.Blue) 1 else 0 }
    }.toMap()
    val coreStats: Map<String, (LandCombatGame) -> Number> = mapOf(
            "BLUE_WINS" to { g: LandCombatGame -> if (victoryFunctions[PlayerId.Blue]?.invoke(g) == true) 1 else 0 },
            "RED_WINS" to { g: LandCombatGame -> if (victoryFunctions[PlayerId.Red]?.invoke(g) == true) 1 else 0 },
            "BLUE_FORCE" to { g: LandCombatGame -> simpleScoreFunction(0.0, 1.0, 0.0, 0.0).invoke(g, 0) },
            "RED_FORCE" to { g: LandCombatGame -> simpleScoreFunction(0.0, 1.0, 0.0, 0.0).invoke(g, 1) },
            "BLUE_CITIES" to { g: LandCombatGame -> simpleScoreFunction(1.0, 0.0, 0.0, 0.0).invoke(g, 0).toInt() },
            "BLUE_FORTS" to { g: LandCombatGame -> fortressScore(1.0).invoke(g, 0).toInt() },
            "RED_FORTS" to { g: LandCombatGame -> fortressScore(1.0).invoke(g, 1).toInt() },
            "RED_CITIES" to { g: LandCombatGame -> simpleScoreFunction(1.0, 0.0, 0.0, 0.0).invoke(g, 1).toInt() },
            "BLUE_SCORE" to { g: LandCombatGame -> blueScoreFunction(g, 0) },
            "RED_SCORE" to { g: LandCombatGame -> redScoreFunction(g, 1) },
            "GAME_LENGTH" to { g: LandCombatGame -> g.nTicks() },
            "COMPLETE_VICTORY" to { g: LandCombatGame -> if (g.nTicks() < 1000) 1 else 0 }
    )

    val statisticsToKeep = coreStats + tMap
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

    if (otherKeys.isNotEmpty()) throw AssertionError("We have not specified an interval for some game parameters $otherKeys")

    val fileWriter = FileWriter(outputFile)
    fileWriter.write(gameParamKeys.joinToString(separator = "\t", postfix = "\t"))
    fileWriter.write(statisticKeys.joinToString(separator = "\t", postfix = "\n"))
    StatsCollator.clear()
    repeat(simsToRun) {

        val params = intervalParams.sampleParams()
        val world = if (mapOverride == "") World(params = params) else {
            val worldFileAsLines = BufferedReader(FileReader(mapOverride)).readLines().joinToString("\n")
            createWorld(worldFileAsLines, params)
        }
        val game = LandCombatGame(world)
        game.scoreFunction[PlayerId.Blue] = blueScoreFunction
        game.scoreFunction[PlayerId.Red] = redScoreFunction

        if (args.contains("VB=targets")) game.victoryFunction[PlayerId.Blue] = allTargetsConquered(PlayerId.Blue, targetMap)
        if (args.contains("VR=targets")) game.victoryFunction[PlayerId.Red] = allTargetsConquered(PlayerId.Red, targetMap)

        val blueAgent = blueAgentParams.createAgent("BLUE")
        game.registerAgent(0, blueAgent)
        val redAgent = redAgentParams.createAgent("RED")
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