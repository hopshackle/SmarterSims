package groundWar.executables

import agents.MCTS.*
import agents.RHEA.*
import agents.UI.GUIAgent
import agents.UI.NextMoveContainer
import agents.UI.QueueNextMove
import ggi.SimpleActionPlayerInterface
import groundWar.*
import groundWar.views.*
import math.Vec2d
import org.junit.jupiter.api.Order
import utilities.*
import java.awt.*
import java.awt.event.ActionListener
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.io.*
import javax.swing.*
import kotlin.math.*
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
            fatigueRate = doubleArrayOf(0.0, 0.0),
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
    val blueScoreParams = args.firstOrNull { it.startsWith("SCB|") }
    val blueScoreFunction = stringToScoreFunction(blueScoreParams)
    val redScoreParams = args.firstOrNull { it.startsWith("SCR|") }
    val redScoreFunction = stringToScoreFunction(redScoreParams)

    val blueAgent = blueParams.createAgent("BLUE")
    val redAgent = redParams.createAgent("RED")
    runWithParams(params, blueAgent, redAgent,
            blueScoreFunction = blueScoreFunction,
            redScoreFunction = redScoreFunction,
            mapFile = if (args.size > 2) args[2] else "")
}

fun runWithParams(params: EventGameParams,
                  blueAgent: SimpleActionPlayerInterface,
                  redAgent: SimpleActionPlayerInterface,
                  blueScoreFunction: (LandCombatGame, Int) -> Double = finalScoreFunction,
                  redScoreFunction: (LandCombatGame, Int) -> Double = finalScoreFunction,
                  blueVictoryFunction: ((LandCombatGame) -> Boolean)? = null,
                  showAgentPlans: Boolean = false,
                  mapFile: String = "") {
    val fileAsLines = if (mapFile != "") BufferedReader(FileReader(mapFile)).readLines().joinToString("\n") else ""
    val world = if (fileAsLines == "") World(params = params) else createWorld(fileAsLines, params)

    if (mapFile != "" && !fileAsLines.startsWith("{")) {
        val output = FileWriter(mapFile.substringBefore('.') + ".json")
        output.write(world.toJSON().toString(1))
        output.close()
    }
    val game = LandCombatGame(world)

    game.scoreFunction[PlayerId.Blue] = blueScoreFunction
    game.scoreFunction[PlayerId.Red] = redScoreFunction
    if (blueVictoryFunction != null)
        game.victoryFunction[PlayerId.Blue] = blueVictoryFunction

    StatsCollator.clear()

    game.registerAgent(0, blueAgent)
    game.registerAgent(1, redAgent)

    class ListComponent : JComponent() {
        init {
            background = Color.getHSBColor(0.7f, 1.0f, 1.0f)
            layout = FlowLayout(FlowLayout.CENTER, 5, 5)
        }
    }

    val mapDimension = if (world.imageFile != null && world.imageFile != "")
        Dimension(min(max(world.params.width, 500), 1000), min(max(world.params.height, 400), 1000))
    else
        Dimension(500, 400)

    val multiView = ListComponent()
    val omniView = WorldView(game, mapDimension)
    val redView = WorldView(game, mapDimension)
    val blueView = WorldView(game, mapDimension)
    val bluePlan = PlanView(blueAgent, game, 0)
    val redPlan = PlanView(redAgent, game, 1)
    if (blueAgent is GUIAgent) {
        // we have a human player, so just add their view, but with GUI below it
        val UIContainer = JSplitPane()
        UIContainer.orientation = JSplitPane.VERTICAL_SPLIT
        UIContainer.topComponent = if (params.fogOfWar) blueView else omniView
        val userInterface = LCGUI(blueAgent.moveDetails as QueueNextMove, world)
        UIContainer.bottomComponent = userInterface
        multiView.add(UIContainer)
        UIContainer.topComponent.addMouseListener(LandCombatGameMouseListener(if (params.fogOfWar) blueView else omniView, userInterface))
    } else {
        multiView.add(omniView)
        if (params.fogOfWar) {
            multiView.add(redView)
            multiView.add(blueView)
        }

        if (showAgentPlans) {
            multiView.add(bluePlan)
            multiView.add(redPlan)
        }
    }

    val frame = JEasyFrame(multiView, "Event Based Game")
    frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE

    while (!game.isTerminal()) {
        game.next(1)
        if (params.fogOfWar) {
            redView.game = game.copy(1)
            blueView.game = game.copy(0)
        }
        frame.title = String.format("Time: %d        Blue: %.0f        Red: %.0f", game.nTicks(), game.score(0), game.score(1))
        if (showAgentPlans) {
            bluePlan.refresh()
            redPlan.refresh()
        }
        multiView.repaint()
        do {
            Thread.sleep(100)
        } while (paused)
    }

    println(StatsCollator.summaryString())
}

class IntVerifier(val input: JTextField, val range: IntRange) : InputVerifier() {
    override fun verify(input: JComponent?): Boolean {
        return try {
            val value = this.input.text.toInt()
            value in range
        } catch (e: Exception) {
            false
        }
    }

}

class LCGUI(val moveQueue: QueueNextMove, val world: World) : JSplitPane(VERTICAL_SPLIT) {
    val fromBox = JTextField("0", 3)
    val toBox = JTextField("0", 3)
    val percentageBox = JTextField("100", 3)

    init {
        world.cities.forEach{ println("${it.name} at ${it.location}")}

        val firstLine = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        fromBox.inputVerifier = IntVerifier(fromBox, 0..world.cities.size)
        toBox.inputVerifier = IntVerifier(fromBox, 0..world.cities.size)
        percentageBox.inputVerifier = IntVerifier(fromBox, 0..100)

        val submitButton = JButton("Submit")
        submitButton.addActionListener {
            // We take the data from the fields, and add it to the current list of orders
            moveQueue.actionQueue.add(Triple(fromBox.text.toInt(), toBox.text.toInt(), percentageBox.text.toInt() / 100.0))
            fromBox.text = "0"
            toBox.text = "0"
            percentageBox.text = "100"
        }
        val pauseButton = JButton("Pause")
        pauseButton.addActionListener {
            paused = !paused
            pauseButton.text = if (paused) "Re-start" else "Pause"
        }
        firstLine.add(JLabel("From: "))
        firstLine.add(fromBox)
        firstLine.add(JLabel("To: "))
        firstLine.add(toBox)
        firstLine.add(JLabel("%age (1-100): "))
        firstLine.add(percentageBox)
        firstLine.add(submitButton)
        firstLine.add(pauseButton)
        topComponent = firstLine
        val amountSlider = JSlider(SwingConstants.HORIZONTAL, 1, 100, 100)
        bottomComponent = amountSlider
        amountSlider.addChangeListener { percentageBox.text = amountSlider.value.toString() }
    }
}

class LandCombatGameMouseListener(val worldView: WorldView, val userInterface: LCGUI) : MouseListener {
    enum class OrderState {
        FROM, TO
    }

    var currentState = OrderState.FROM
    override fun mouseClicked(e: MouseEvent?) {
        if (e != null) {
            val xScale = worldView.getWidth() / userInterface.world.params.width.toDouble()
            val yScale = worldView.getHeight() / userInterface.world.params.height.toDouble()
            val clickPos = Vec2d(e.x.toDouble() / xScale, e.y.toDouble() / yScale)
            println("Click Position $clickPos")
            val nearestCity = userInterface.world.cities.withIndex()
                    .map { Triple(it.index, it.value, it.value.location.distanceTo(clickPos)) }
                    .minBy { it.third }
                    ?.first ?: -1
            if (nearestCity == -1) return
            when (currentState) {
                OrderState.FROM -> {
                    currentState = OrderState.TO
                    userInterface.fromBox.text = nearestCity.toString()
                }
                OrderState.TO -> {
                    currentState = OrderState.FROM
                    userInterface.toBox.text = nearestCity.toString()
                }
            }
        }
    }

    override fun mouseReleased(e: MouseEvent?) {
    }

    override fun mouseEntered(e: MouseEvent?) {
    }

    override fun mouseExited(e: MouseEvent?) {
    }

    override fun mousePressed(e: MouseEvent?) {
    }

}