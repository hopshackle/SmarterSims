package games.eventqueuegame

import agents.*
import agents.MCTS.*
import ggi.SimpleActionPlayerInterface
import kotlin.random.Random

fun main() {
    StatsCollator.clear()
    val params = EventGameParams(
            fogOfWar = true,
            fogStrengthAssumption = doubleArrayOf(5.0, 5.0),
            nAttempts = 15,
            citySeparation = 50,
            minConnections = 3,
            speed = 5.0,
            planningHorizon = 200,
            OODALoop = intArrayOf(25, 25),
            minAssaultFactor = doubleArrayOf(2.0, 2.0),
            blueLanchesterCoeff = 0.05,
            redLanchesterCoeff = 0.05,
            blueLanchesterExp = 0.5,
            redLanchesterExp = 0.5,
            percentFort = 0.25,
            fortAttackerCoeffDivisor = 2.0,
            fortDefenderExpIncrease = 0.1)
    val agents = HashMap<PlayerId, SimpleActionPlayerInterface>()
    agents[PlayerId.Blue] = SimpleActionEvoAgent(SimpleEvoAgent(nEvals = 1000, timeLimit = 100, sequenceLength = 40, horizon = 100, useMutationTransducer = false, probMutation = 0.25, name = "BLUE")
            , opponentModel = HeuristicAgent(2.0, 1.0))
    agents[PlayerId.Red] = SimpleActionEvoAgent(SimpleEvoAgent(nEvals = 1000, timeLimit = 100, sequenceLength = 40, horizon = 100, useMutationTransducer = false, probMutation = 0.25, name = "RED"))
//    agents[PlayerId.Red] = MCTSTranspositionTableAgentMaster(MCTSParameters(maxPlayouts = 1000, timeLimit = 50, horizon = 100), LandCombatStateFunction)

    val scoreFunction = compositeScoreFunction(
            simpleScoreFunction(5.0, 1.0, 0.0, -0.5),
            visibilityScore(1.0, 1.0)
    )

    var blueWins = 0
    var redWins = 0
    var draws = 0
    var blueScore = 0.0
    val maxGames = 1000

    val startTime = java.util.Calendar.getInstance().timeInMillis
    val useConstantWorld = false
    val constantWorld = 1
    for (r in 1..maxGames) {

        agents[PlayerId.Blue]?.reset()
        agents[PlayerId.Red]?.reset()

        val world = World(random = Random(if (useConstantWorld) constantWorld else r), params = params)
        val game = LandCombatGame(world)
        game.scoreFunction[PlayerId.Blue] = scoreFunction
        game.scoreFunction[PlayerId.Red] = scoreFunction
        game.registerAgent(0, agents[PlayerId.Blue] ?: SimpleActionDoNothing)
        game.registerAgent(1, agents[PlayerId.Red] ?: SimpleActionDoNothing)
        game.next(1000)
        val gameScore = simpleScoreFunction(5.0, 1.0, -5.0, -1.0)(game, 0)
        blueScore += gameScore
        println(String.format("Game %2d\tScore: %4.1f", r, gameScore))
        when {
            gameScore > 0.0 -> blueWins++
            gameScore < 0.0 -> redWins++
            else -> draws++
        }
    }
    println("$blueWins wins for Blue (Avg Score of ${String.format("%.2f",blueScore / maxGames)}, $redWins for Red and $draws draws out of $maxGames in ${(java.util.Calendar.getInstance().timeInMillis - startTime) / maxGames} ms per game")
    println(StatsCollator.summaryString())
}
