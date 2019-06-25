package groundWar

import agents.SimpleEvoAgent
import utilities.JEasyFrame
import javax.swing.JFrame
import kotlin.random.Random

var paused: Boolean = false

fun main(args: Array<String>) {
    val params = EventGameParams(
            fogOfWar = true,
            fogStrengthAssumption = doubleArrayOf(5.0, 5.0),
            nAttempts = 15,
            citySeparation = 50,
            minConnections = 3,
            speed = doubleArrayOf(5.0, 5.0),
            OODALoop = intArrayOf(25, 25),
            minAssaultFactor = doubleArrayOf(2.0, 2.0),
            lanchesterCoeff = doubleArrayOf(0.05, 0.05),
            lanchesterExp = doubleArrayOf(0.5, 0.5),
            percentFort = 0.25,
            fortAttackerDivisor = 2.0,
            fortDefenderExpBonus = 0.1
    )

    runWithParams(params)

}

fun runWithParams(params: EventGameParams) {
    val world = World(random = Random(1), params = params)
    val targets = mapOf(PlayerId.Blue to listOf(0, 2, 4, 5), PlayerId.Red to listOf(0, 1, 3, 5))
    val game = LandCombatGame(world, targets = emptyMap())

    game.scoreFunction[PlayerId.Blue] = compositeScoreFunction(
            simpleScoreFunction(5.0, 1.0, 0.0, -0.5),
            visibilityScore(2.0, 1.0)
            //   game.scoreFunction = specificTargetScoreFunction(50.0)
    )
    game.scoreFunction[PlayerId.Red] = compositeScoreFunction(
            simpleScoreFunction(5.0, 1.0, 0.0, -0.5),
            visibilityScore(0.0, 0.0)
            //   game.scoreFunction = specificTargetScoreFunction(50.0)
    )
    StatsCollator.clear()
    val blueOpponentModel =
            //         DoNothingAgent()
            HeuristicAgent(2.0, 1.1)
    //        SimpleActionEvoAgent(SimpleEvoAgent(name = "OppEA", nEvals = 10, sequenceLength = 40, useMutationTransducer = false, probMutation = 0.1, horizon = params.planningHorizon))
    val blueAgent = SimpleActionEvoAgent(SimpleEvoAgent(nEvals = 1000, timeLimit = 100, sequenceLength = 40,
            useMutationTransducer = false, probMutation = 0.1, useShiftBuffer = false,
            horizon = 200, opponentModel = blueOpponentModel)
    )
    game.registerAgent(0, blueAgent)
    val redAgent =
    //      SimpleActionEvoAgent(SimpleEvoAgent(nEvals = 200, sequenceLength = 40, useMutationTransducer = false, probMutation = 0.1, horizon = params.planningHorizon))
            //      MCTSTranspositionTableAgentMaster(MCTSParameters(timeLimit = 100, maxPlayouts = 1000, horizon = params.planningHorizon[1]), LandCombatStateFunction)
            HeuristicAgent(2.0, 1.1)
    game.registerAgent(1, redAgent)

    val multiView = ListComponent()
    val omniView = WorldView(game)
    val redView = WorldView(game)
    val blueView = WorldView(game)
    multiView.add(omniView)
    if (params.fogOfWar) {
        multiView.add(redView)
        multiView.add(blueView)
    }
    //   val planView = PlanView(game.getAgent(0), game, 0)
//    multiView.add(planView)
    val frame = JEasyFrame(multiView, "Event Based Game")
    frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE

    while (!game.isTerminal()) {
        game.next(1)
        if (params.fogOfWar) {
            redView.game = game.copy(1)
            blueView.game = game.copy(0)
        }
        frame.title = String.format("Time: %d        Blue: %.0f        Red: %.0f", game.nTicks(), game.score(0), game.score(1))
        multiView.repaint()
        do {
            Thread.sleep(50)
        } while (paused)
    }

    println(StatsCollator.summaryString())
}