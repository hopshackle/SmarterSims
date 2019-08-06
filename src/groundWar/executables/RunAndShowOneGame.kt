package groundWar.executables

import agents.MCTS.*
import agents.RHEA.*
import groundWar.*
import groundWar.views.WorldView
import utilities.*
import java.awt.*
import java.io.*
import javax.swing.*
import kotlin.random.Random

var paused: Boolean = false

fun main(args: Array<String>) {
    val params = EventGameParams(
            fogOfWar = false,
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

    runWithParams(params, if (args.size > 0) args[0] else "")
}

fun runWithParams(params: EventGameParams, mapFile: String = "") {
    val fileAsLines = if (mapFile != "") BufferedReader(FileReader(mapFile)).readLines().joinToString("\n") else ""
    val world = if (fileAsLines == "") World(params = params) else createWorld(fileAsLines, params)
    if (mapFile != "" && !fileAsLines.startsWith("{")) {
        val output = FileWriter(mapFile.substringBefore('.') + ".json")
        output.write(world.toJSON().toString(1))
        output.close()
    }
    val targets = mapOf(PlayerId.Blue to listOf(0, 2, 4, 5), PlayerId.Red to listOf(0, 1, 3, 5))
    val game = LandCombatGame(world, targets = emptyMap())

    game.scoreFunction[PlayerId.Blue] = interimScoreFunction
    game.scoreFunction[PlayerId.Red] = interimScoreFunction
    StatsCollator.clear()
    val blueOpponentModel =
            //         DoNothingAgent()
            HeuristicAgent(2.0, 1.1)
    //        SimpleActionEvoAgent(SimpleEvoAgent(name = "OppEA", nEvals = 10, sequenceLength = 40, useMutationTransducer = false, probMutation = 0.1, horizon = params.planningHorizon))
    val blueAgent =
    //    SimpleActionEvoAgent(SimpleEvoAgent(nEvals = 1000, timeLimit = 100, sequenceLength = 40,
    //    useMutationTransducer = false, probMutation = 0.1, useShiftBuffer = false,
            //    horizon = 200, opponentModel = blueOpponentModel))
            MCTSTranspositionTableAgentMaster(MCTSParameters(maxActions = 40, maxDepth = 12, C=0.03, timeLimit = 200, maxPlayouts = 2000, horizon = 200), LandCombatStateFunction,
                    opponentModel = null, name = "BLUE")

    game.registerAgent(0, blueAgent)
    val redAgent =
            SimpleActionEvoAgent(SimpleEvoAgent(nEvals = 20000, timeLimit = 100, sequenceLength = 12, useMutationTransducer = false,
                    probMutation = 0.1, horizon = 50,
                    //    MCTSTranspositionTableAgentMaster(MCTSParameters(maxActions = 40, timeLimit = 100, maxPlayouts = 2000, horizon = 100), LandCombatStateFunction,
                    name = "RED"),
                    opponentModel = HeuristicAgent(3.0, 1.2, listOf(HeuristicOptions.WITHDRAW, HeuristicOptions.ATTACK)))
    // HeuristicAgent(2.0, 1.1)
    game.registerAgent(1, redAgent)

    class ListComponent : JComponent() {
        init {
            background = Color.getHSBColor(0.7f, 1.0f, 1.0f)
            layout = FlowLayout(FlowLayout.CENTER, 5, 5)
        }
    }

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