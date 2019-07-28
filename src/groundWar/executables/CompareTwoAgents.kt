package groundWar.executables

import ggi.*
import agents.*
import groundWar.*
import intervals.interval
import utilities.*
import java.io.*
import java.lang.AssertionError
import kotlin.math.*
import kotlin.random.Random
import kotlin.streams.toList

fun main(args: Array<String>) {
    StatsCollator.clear()

    // args[0] is the number of games to run
    // args[1] is the file that contains agentParams to use
    // args[2] is the file that contains intervalParams to use (this is used with a fixed set at the moment, it is not resampled for each game)
    val maxGames = if (args.size > 0) args[0].toInt() else 100
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

    runGames(maxGames, agentParams1.createAgent("BLUE"), agentParams2.createAgent("RED"), intervalParams)
}

fun runGames(maxGames: Int, blueAgent: SimpleActionPlayerInterface, redAgent: SimpleActionPlayerInterface, intervalParams: IntervalParams? = null, eventParams: EventGameParams? = null) {

    val simpleScoreFunction = simpleScoreFunction(5.0, 1.0, -5.0, -0.5)
    val complexScoreFunction = compositeScoreFunction(
            simpleScoreFunction(5.0, 1.0, 0.0, -0.5),
            visibilityScore(1.0, 1.0)
    )

    val agents = mapOf(PlayerId.Blue to blueAgent, PlayerId.Red to redAgent)
    var blueWins = 0
    var redWins = 0
    var draws = 0
    var blueScore = 0.0

    val startTime = java.util.Calendar.getInstance().timeInMillis
    for (r in 1..maxGames) {

        agents[PlayerId.Blue]?.reset()
        agents[PlayerId.Red]?.reset()

        val params = when {
            eventParams != null -> eventParams.copy(seed = System.currentTimeMillis())
            intervalParams == null -> EventGameParams(seed = System.currentTimeMillis())
            else -> intervalParams.sampleParams()
        }
        val world = World(params = params)

        val game = LandCombatGame(world)
        game.scoreFunction[PlayerId.Blue] = simpleScoreFunction
        game.scoreFunction[PlayerId.Red] = simpleScoreFunction
        val firstToAct = r % 2
        game.registerAgent(firstToAct, agents[numberToPlayerID(firstToAct)] ?: SimpleActionDoNothing(1000))
        game.registerAgent(1 - firstToAct, agents[numberToPlayerID(1 - firstToAct)] ?: SimpleActionDoNothing(1000))
        game.next(1000)
        val gameScore = simpleScoreFunction(5.0, 1.0, -5.0, -1.0)(game, 0)
        val redScore = simpleScoreFunction(5.0, 1.0, -5.0, -1.0)(game, 1)
        if (abs(abs(gameScore) - abs(redScore)) > 0.001) {
            throw AssertionError("Should be zero sum!")
        }
        blueScore += gameScore
        println(String.format("Game %2d\tScore: %6.1f\tCities: %2d, Routes: %2d, seed: %d", r, gameScore, world.cities.size, world.routes.size, params.seed))
        when {
            gameScore > 0.0 -> blueWins++
            gameScore < 0.0 -> redWins++
            else -> draws++
        }
        val decisions = game.eventQueue.history.map(Event::action).filterIsInstance<MakeDecision>().partition { m -> m.playerRef == 0 }
        StatsCollator.addStatistics("BLUE_SCORE", gameScore)
        StatsCollator.addStatistics("BLUE_Decisions", decisions.first.size)
        StatsCollator.addStatistics("RED_Decisions", decisions.second.size)
    }
    println("$blueWins wins for Blue, $redWins for Red and $draws draws out of $maxGames in ${(java.util.Calendar.getInstance().timeInMillis - startTime) / maxGames} ms per game")
    println(StatsCollator.summaryString())
}