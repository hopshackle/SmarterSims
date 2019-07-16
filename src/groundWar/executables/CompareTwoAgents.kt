package groundWar.executables

import ggi.*
import groundWar.*
import utilities.*
import java.io.*
import kotlin.random.Random
import kotlin.streams.toList

fun main(args: Array<String>) {
    StatsCollator.clear()

    // args[0] is the number of games to run
    // args[1] is the file that contains agentParams to use
    // args[2] is the file that contains intervalParams to use (this is used with a fixed set at the moment, it is not resampled for each game)
    val maxGames = if (args.size > 0) args[0].toInt() else 100
    val agentParams = if (args.size > 1) {
        val fileAsLines = BufferedReader(FileReader(args[1])).lines().toList()
        createAgentParamsFromString(fileAsLines)
    } else AgentParams()
    val params = if (args.size > 2) {
        val fileAsLines = BufferedReader(FileReader(args[2])).lines().toList()
        createIntervalParamsFromString(fileAsLines).sampleParams()
    } else EventGameParams()

    val agents = HashMap<PlayerId, SimpleActionPlayerInterface>()
    agents[PlayerId.Blue] = agentParams.createAgent("BLUE")
      //      MCTSTranspositionTableAgentMaster(MCTSParameters(timeLimit = agentParams.timeBudget, maxPlayouts = agentParams.evalBudget, horizon = params.planningHorizon[0], pruneTree = true), LandCombatStateFunction)
    //  SimpleActionEvoAgent(SimpleEvoAgent(nEvals = 1000, timeLimit = 100, sequenceLength = 40, horizon = 100, useMutationTransducer = false, probMutation = 0.25, name = "BLUE")
    //  , opponentModel = HeuristicAgent(2.0, 1.0))
    //    HeuristicAgent(3.0, 1.2, listOf(HeuristicOptions.ATTACK, HeuristicOptions.WITHDRAW))

    agents[PlayerId.Red] = agentParams.createAgent("RED")
            //        MCTSTranspositionTableAgentMaster(MCTSParameters(timeLimit = 100, maxPlayouts = 1000, horizon = params.planningHorizon[1]), LandCombatStateFunction)
   //         SimpleActionEvoAgent(SimpleEvoAgent(nEvals = 10000, timeLimit = 500, sequenceLength = 40, horizon = params.planningHorizon[1], useMutationTransducer = false, useShiftBuffer = true, probMutation = 0.25, name = "RED"))
    //    HeuristicAgent(3.0, 1.0, listOf( HeuristicOptions.ATTACK, HeuristicOptions.WITHDRAW))


    val simpleScoreFunction = simpleScoreFunction(5.0, 1.0, -5.0, -1.0)
    val complexScoreFunction = compositeScoreFunction(
            simpleScoreFunction(5.0, 1.0, 0.0, -0.5),
            visibilityScore(1.0, 1.0)
    )

    var blueWins = 0
    var redWins = 0
    var draws = 0
    var blueScore = 0.0

    val startTime = java.util.Calendar.getInstance().timeInMillis
    for (r in 1..maxGames) {

        agents[PlayerId.Blue]?.reset()
        agents[PlayerId.Red]?.reset()

        val world = World(random = Random(seed = params.seed), params = params)
        val game = LandCombatGame(world)
        game.scoreFunction[PlayerId.Blue] = simpleScoreFunction
        game.scoreFunction[PlayerId.Red] = simpleScoreFunction
        game.registerAgent(0, agents[PlayerId.Blue] ?: SimpleActionDoNothing(1000))
        game.registerAgent(1, agents[PlayerId.Red] ?: SimpleActionDoNothing(1000))
        game.next(1000)
        val gameScore = simpleScoreFunction(5.0, 1.0, -5.0, -1.0)(game, 0)
        blueScore += gameScore
        println(String.format("Game %2d\tScore: %4.1f", r, gameScore))
        when {
            gameScore > 0.0 -> blueWins++
            gameScore < 0.0 -> redWins++
            else -> draws++
        }
        StatsCollator.addStatistics("BLUE_SCORE", gameScore)
    }
    println("$blueWins wins for Blue, $redWins for Red and $draws draws out of $maxGames in ${(java.util.Calendar.getInstance().timeInMillis - startTime) / maxGames} ms per game")
    println(StatsCollator.summaryString())
}
