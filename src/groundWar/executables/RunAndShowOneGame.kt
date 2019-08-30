package groundWar.executables

import agents.MCTS.*
import agents.RHEA.*
import ggi.SimpleActionPlayerInterface
import groundWar.*
import groundWar.views.*
import utilities.*
import java.awt.*
import java.io.*
import javax.swing.*
import kotlin.streams.toList

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
            orderDelay = intArrayOf(0, 0),
            minAssaultFactor = doubleArrayOf(2.0, 2.0),
            lanchesterCoeff = doubleArrayOf(0.05, 0.05),
            lanchesterExp = doubleArrayOf(0.5, 0.5),
            percentFort = 0.25,
            fortAttackerDivisor = 2.0,
            fortDefenderExpBonus = 0.1
    )

    val blueParams = if (args.size > 0) {
        val fileAsLines = BufferedReader(FileReader(args[0])).lines().toList()
        createAgentParamsFromString(fileAsLines)
    } else AgentParams()
    val redParams = if (args.size > 1) {
        val fileAsLines = BufferedReader(FileReader(args[1])).lines().toList()
        createAgentParamsFromString(fileAsLines)
    } else AgentParams()
    val scoreParams = args.firstOrNull { it.startsWith("SC|") }
    val scoreFunction = if (scoreParams != null) {
        val params = scoreParams.split("|").filterNot { it == "SC" }.map { it.toDouble() }
        compositeScoreFunction(
                simpleScoreFunction(params[1], params[3], params[2], params[4]),
                visibilityScore(params[5], params[6])
        )
    } else
        interimScoreFunction

    val blueAgent = blueParams.createAgent("BLUE")
    val redAgent = redParams.createAgent("RED")
    runWithParams(params, blueAgent, redAgent,
            iScoreFunction = scoreFunction,
            mapFile = if (args.size > 2) args[2] else "")
}

fun runWithParams(params: EventGameParams,
                  blueAgent: SimpleActionPlayerInterface,
                  redAgent: SimpleActionPlayerInterface,
                  iScoreFunction: (LandCombatGame, Int) -> Double = interimScoreFunction,
                  mapFile: String = "") {
    val fileAsLines = if (mapFile != "") BufferedReader(FileReader(mapFile)).readLines().joinToString("\n") else ""
    val world = if (fileAsLines == "") World(params = params) else createWorld(fileAsLines, params)
    if (mapFile != "" && !fileAsLines.startsWith("{")) {
        val output = FileWriter(mapFile.substringBefore('.') + ".json")
        output.write(world.toJSON().toString(1))
        output.close()
    }
    val targets = mapOf(PlayerId.Blue to listOf(0, 2, 4, 5), PlayerId.Red to listOf(0, 1, 3, 5))
    val game = LandCombatGame(world, targets = emptyMap())

    game.scoreFunction[PlayerId.Blue] = iScoreFunction
    game.scoreFunction[PlayerId.Red] = iScoreFunction
    StatsCollator.clear()

    game.registerAgent(0, blueAgent)
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
    val bluePlan = PlanView(blueAgent, game, 0)
    val redPlan = PlanView(redAgent, game, 1)
    multiView.add(bluePlan)
    multiView.add(redPlan)
    val frame = JEasyFrame(multiView, "Event Based Game")
    frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE

    while (!game.isTerminal()) {
        game.next(1)
        if (params.fogOfWar) {
            redView.game = game.copy(1)
            blueView.game = game.copy(0)
        }
        frame.title = String.format("Time: %d        Blue: %.0f        Red: %.0f", game.nTicks(), game.score(0), game.score(1))
        bluePlan.refresh()
        redPlan.refresh()
        multiView.repaint()
        do {
            Thread.sleep(100)
        } while (paused)
    }

    println(StatsCollator.summaryString())
}