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

    val agents = mapOf(PlayerId.Blue to blueAgent, PlayerId.Red to redAgent)
    var blueWins = 0
    var redWins = 0
    var draws = 0
    var blueScore = 0.0

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
        game.scoreFunction[PlayerId.Blue] = interimScoreFunction
        game.scoreFunction[PlayerId.Red] = interimScoreFunction
        game.registerAgent(0, agents[numberToPlayerID(0)] ?: SimpleActionDoNothing(1000))
        game.registerAgent(1, agents[numberToPlayerID(1)] ?: SimpleActionDoNothing(1000))
        val startTime = System.currentTimeMillis()
        game.next(1000)
        val gameScore = finalScoreFunction(game, 0)
        val redScore = finalScoreFunction(game, 1)
        if (abs(abs(gameScore) - abs(redScore)) > 0.001) {
            throw AssertionError("Should be zero sum!")
        }
        blueScore += gameScore
        val decisions = game.eventQueue.history.map(Event::action).filterIsInstance<MakeDecision>().partition { m -> m.playerRef == 0 }
        println(String.format("Game %2d\tScore: %6.1f\tCities: %2d\tRoutes: %2d\tseed: %d\tTime: %3d\tTicks: %4d\tDecisions: %d:%d", r, gameScore, world.cities.size, world.routes.size, params.seed,
                System.currentTimeMillis() - startTime, game.nTicks(), decisions.first.size, decisions.second.size))
        when {
            gameScore > 0.0 -> blueWins++
            gameScore < 0.0 -> redWins++
            else -> draws++
        }
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
    println("$blueWins wins for Blue, $redWins for Red and $draws draws out of $maxGames")
    println(StatsCollator.summaryString())
}