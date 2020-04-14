package groundWar.executables

import ggi.*
import agents.*
import groundWar.*
import utilities.*
import java.io.*
import java.lang.AssertionError
import kotlin.math.*
import kotlin.streams.toList

fun main(args: Array<String>) {
    StatsCollator.clear()
    val helpText = """
            args[0] is the number of games to run
            args[1], args[2] are the files that contains agentParams to use for Blue and Red respectively
            args[3] is the file that contains intervalParams to use (this is used with a fixed set at the moment, it is not resampled for each game)
            fortVictory     will set insta-win condition being to occupy all forts (the default is all cities)
            map=            will specify a map to use (the same for all games)
            SCB/SCR=        the score functions to use for Blue and Red respectively (default is 1 per force unit, 5 per city)
            logAll=         log all game results (default is true, if false then just reports summary stats)
    """.trimIndent()

    if (args.contains("-h")) println(helpText)
    // args[0] is the number of games to run
    // args[1], args[2] is the file that contains agentParams to use
    // args[3] is the file that contains intervalParams to use (this is used with a fixed set at the moment, it is not resampled for each game)
    val maxGames = if (args.isNotEmpty()) args[0].toInt() else 100
    val agentParams1 = if (args.size > 1) {
        val fileAsLines = BufferedReader(FileReader(args[1])).lines().toList()
        createAgentParamsFromString(fileAsLines)
    } else AgentParams()
    val agentParams2 = if (args.size > 2) {
        val fileAsLines = BufferedReader(FileReader(args[2])).lines().toList()
        createAgentParamsFromString(fileAsLines)
    } else AgentParams()
    val intervalParams = if (args.size > 3) {
        val fileAsLines = BufferedReader(FileReader(args[3])).lines().toList()
        createIntervalParamsFromString(fileAsLines)
    } else null

    val blueScoreFunction = stringToScoreFunction(args.firstOrNull { it.startsWith("SCB") })
    val redScoreFunction = stringToScoreFunction(args.firstOrNull { it.startsWith("SCR") })
    val logAll = argParam(args, "logAll", true)

    val fortVictory = args.contains("fortVictory")
    val mapOverride = args.find { it.startsWith("map=") }?.substring(4) ?: ""
    StatsCollator.clear()
    runGames(maxGames, agentParams1.createAgent("BLUE"), agentParams2.createAgent("RED"), intervalParams,
            scoreFunctions = arrayOf(blueScoreFunction, redScoreFunction), mapOverride = mapOverride, fortVictory = fortVictory, logAllResults = logAll)
    println(StatsCollator.summaryString())
}

fun runGames(maxGames: Int, blueAgent: SimpleActionPlayerInterface, redAgent: SimpleActionPlayerInterface,
             intervalParams: IntervalParams? = null, eventParams: EventGameParams? = null, worldSeeds: LongArray = longArrayOf(),
             scoreFunctions: Array<(LandCombatGame, Int) -> Double> = arrayOf(interimScoreFunction, interimScoreFunction),
             mapOverride: String = "", logAllResults: Boolean = true,
             fortVictory: Boolean = false, blueTargetVictory: Boolean = true) {

    val agents = mapOf(PlayerId.Blue to blueAgent, PlayerId.Red to redAgent)

    val victoryFunctions: Array<(LandCombatGame, Int) -> Boolean> = when {
        blueTargetVictory -> {
            val targetMap = if (mapOverride == "") emptyMap() else {
                val mapFileAsLines = BufferedReader(FileReader(mapOverride)).readLines().joinToString("\n")
                victoryValuesFromJSON(mapFileAsLines)
            }
            arrayOf({ g, _ -> allTargetsConquered(PlayerId.Blue, targetMap).invoke(g) }, hasMaterialAdvantage)
        }
        fortVictory -> arrayOf({ g, _ -> allFortsConquered(PlayerId.Blue).invoke(g) }, hasMaterialAdvantage)
        else -> arrayOf(hasMaterialAdvantage, hasMaterialAdvantage)
    }


    for (r in 1..maxGames) {

        agents[PlayerId.Blue]?.reset()
        agents[PlayerId.Red]?.reset()

        val seedToUse = if (worldSeeds.isEmpty()) System.currentTimeMillis() else worldSeeds[(r - 1) % worldSeeds.size]
        val params = when {
            eventParams != null -> eventParams.copy(seed = seedToUse)
            intervalParams == null -> EventGameParams(seed = seedToUse)
            else -> intervalParams.sampleParams()
        }
        val world = if (mapOverride != "") {
            val worldFileAsLines = BufferedReader(FileReader(mapOverride)).readLines().joinToString("\n")
            createWorld(worldFileAsLines, params)
        } else
            World(params = params)

        val game = LandCombatGame(world)
        game.scoreFunction[PlayerId.Blue] = scoreFunctions[0]
        game.scoreFunction[PlayerId.Red] = scoreFunctions[1]
        if (fortVictory) {
            game.victoryFunction[PlayerId.Blue] = { g -> allFortsConquered(PlayerId.Blue).invoke(g) }
        }
        game.registerAgent(0, agents[numberToPlayerID(0)] ?: SimpleActionDoNothing(1000))
        game.registerAgent(1, agents[numberToPlayerID(1)] ?: SimpleActionDoNothing(1000))
        val startTime = System.currentTimeMillis()
        game.next(1000)
        val gameScore = finalScoreFunction(game, 0)
        val redScore = finalScoreFunction(game, 1)
        if (abs(abs(gameScore) - abs(redScore)) > 0.001) {
            throw AssertionError("Should be zero sum!")
        }

        val decisions = game.eventQueue.history.map(Event::action).filterIsInstance<MakeDecision>().partition { m -> m.playerRef == 0 }
        if (logAllResults)
            println(String.format("Game %2d\tScore: %6.1f\tCities: %2d\tRoutes: %2d\tseed: %d\tTime: %3d\tTicks: %4d\tDecisions: %d:%d", r, gameScore, world.cities.size, world.routes.size, params.seed,
                    System.currentTimeMillis() - startTime, game.nTicks(), decisions.first.size, decisions.second.size))

        StatsCollator.addStatistics("BLUE_victory", if (victoryFunctions[0](game, 0)) 1.0 else 0.0)
        StatsCollator.addStatistics("RED_victory", if (victoryFunctions[1](game, 1)) 1.0 else 0.0)
        StatsCollator.addStatistics("BLUE_wins", if (gameScore > 0.0) 1.0 else 0.0)
        StatsCollator.addStatistics("BLUE_SCORE", gameScore)
        StatsCollator.addStatistics("GameLength", game.nTicks())
        StatsCollator.addStatistics("ElapsedTime", System.currentTimeMillis() - startTime)
        StatsCollator.addStatistics("BLUE_Decisions", decisions.first.size)
        StatsCollator.addStatistics("RED_Decisions", decisions.second.size)
        StatsCollator.addStatistics("BLUE_LaunchExpedition", game.eventQueue.history
                .filter { it.action is LaunchExpedition && it.action.playerId == PlayerId.Blue }
                .count())
        StatsCollator.addStatistics("RED_LaunchExpedition", game.eventQueue.history
                .filter { it.action is LaunchExpedition && it.action.playerId == PlayerId.Red }
                .count())
        StatsCollator.addStatistics("BLUE_Wait", game.eventQueue.history
                .filter { it.action is InterruptibleWait && it.action.playerRef == 0 }
                .count())
        StatsCollator.addStatistics("RED_Wait", game.eventQueue.history
                .filter { it.action is InterruptibleWait && it.action.playerRef == 1 }
                .count())
    }
}
