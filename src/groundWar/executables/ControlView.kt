package groundWar.executables

import agents.UI.GUIAgent
import agents.UI.QueueNextMove
import groundWar.*
import java.awt.*
import java.io.BufferedReader
import java.io.FileReader
import javax.swing.*
import kotlin.reflect.full.memberProperties
import utilities.argParam
import java.util.*

fun main(args: Array<String>) {
    //Schedule a job for the event dispatch thread:
    //creating and showing this application's GUI.
    SwingUtilities.invokeLater { createAndShowGUI(args) }
}

fun createAndShowGUI(args: Array<String>) {
    val view = ControlView(args)
    view.create(JFrame())
}

const val testVersion = false

val defaultBlueAgent = """
        algorithm=MCTS
        timeBudget=300
        evalBudget=100000
        sequenceLength=12
        planningHorizon=100
        algoParams=C:3.0,actionFilter:core,rolloutPolicy:DoNothing,selectionPolicy:SIMPLE,discountFactor:0.99
        opponentModel=MCTS
    """.trimIndent()

val defaultRedAgent = """
        algorithm=RHEA
        timeBudget=300
        evalBudget=100000
        sequenceLength=12
        planningHorizon=100
        algoParams=probMutation:0.7,flipAtLeastOneValue,discountFactor:0.999
        opponentModel=DoNothing
    """.trimIndent()

class ControlView(args: Array<String>) {

    val params = EventGameParams()
    val propertyMap: MutableMap<String, Any?> = EventGameParams::class.memberProperties.map { it.name to it.get(params) }.toMap().toMutableMap()
    val parameterNames = listOf("nAttempts", "minConnections", "maxDistance", "width", "height", "startingForce", "lanchesterCoeff", "lanchesterExp",
            "fatigueRate", "percentFort", "fortAttackerDivisor", "fortDefenderExpBonus", "fogOfWar", "fogStrengthAssumption", "fogMemory", "speed",
            "OODALoop", "orderDelay", "controlLimit", "minAssaultFactor", "seed")
    var runningThread = Thread()

    class ParameterSetting(val text: String, val propertyMap: MutableMap<String, Any?>) : JPanel(FlowLayout(FlowLayout.LEFT)) {
        init {
            add(JLabel(text))
            val mapEntry = propertyMap[text]
            val displayText: String = when (mapEntry) {
                is IntArray -> mapEntry.joinToString()
                is DoubleArray -> mapEntry.joinToString { String.format("%.2f", it) }
                else -> mapEntry.toString()
            }
            val textField = JTextField(displayText, 10)
            add(textField)

            textField.inputVerifier = object : InputVerifier() {
                override fun verify(input: JComponent?): Boolean {
                    try {
                        val newValue = textField.getText()
                        propertyMap[text] = when (mapEntry) {
                            is IntArray -> newValue.split(", ").map(String::toInt).toIntArray()
                            is DoubleArray -> newValue.split(", ").map(String::toDouble).toDoubleArray()
                            is Int -> newValue.toInt()
                            is Long -> newValue.toLong()
                            is Double -> newValue.toDouble()
                            is Boolean -> newValue.toBoolean()
                            else -> newValue.toString()
                        }
                    } catch (e: Exception) {
                        return false
                    }
                    return true
                }
            }
        }
    }

    fun create(frame: JFrame) {
        val displayPanel = JPanel(GridLayout(1, 2))
        val setting = JPanel(GridLayout(0, 1, 1, 1))
        parameterNames.forEach {
            setting.add(ParameterSetting(it, propertyMap))
        }
        val fileChooserPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val mapButton = JButton("Map ")
        fileChooserPanel.add(mapButton)
        val mapNameField = JTextField("", 10)
        fileChooserPanel.add(mapNameField)
        mapButton.addActionListener {
            val fileChooser = JFileChooser()
            if (fileChooser.showOpenDialog(setting) == JFileChooser.APPROVE_OPTION) {
                val selectedValue = fileChooser.selectedFile
                mapNameField.text = selectedValue.path
            }
        }
        setting.add(fileChooserPanel)

        val agentPlanBox = JCheckBox("Show Agent Plans?", false)

        val agentParameters = JPanel(GridLayout(0, 1, 1, 1))

        agentParameters.add(JLabel("Blue Agent:"))
        val blueAgentDetails = JTextArea(defaultBlueAgent, 25, 50)
        agentParameters.add(blueAgentDetails)
        agentParameters.add(JLabel("Red Agent:"))
        val redAgentDetails = JTextArea(defaultRedAgent, 25, 50)
        agentParameters.add(redAgentDetails)

        val buttonPanel = JPanel()
        val startButton = JButton("Start")
        startButton.addActionListener {
            if (paused) {
                paused = false
            } else {
                val simParams = EventGameParams(
                        nAttempts = propertyMap["nAttempts"] as Int,
                        minConnections = propertyMap["minConnections"] as Int,
                        maxDistance = propertyMap["maxDistance"] as Int,
                        width = propertyMap["width"] as Int,
                        height = propertyMap["height"] as Int,
                        startingForce = propertyMap["startingForce"] as IntArray,
                        lanchesterCoeff = propertyMap["lanchesterCoeff"] as DoubleArray,
                        lanchesterExp = propertyMap["lanchesterExp"] as DoubleArray,
                        fatigueRate = propertyMap["fatigueRate"] as DoubleArray,
                        percentFort = propertyMap["percentFort"] as Double,
                        fortAttackerDivisor = propertyMap["fortAttackerDivisor"] as Double,
                        fortDefenderExpBonus = propertyMap["fortDefenderExpBonus"] as Double,
                        fogOfWar = propertyMap["fogOfWar"] as Boolean,
                        fogStrengthAssumption = propertyMap["fogStrengthAssumption"] as DoubleArray,
                        fogMemory = propertyMap["fogMemory"] as IntArray,
                        speed = propertyMap["speed"] as DoubleArray,
                        OODALoop = propertyMap["OODALoop"] as IntArray,
                        orderDelay = propertyMap["orderDelay"] as IntArray,
                        controlLimit = propertyMap["controlLimit"] as IntArray,
                        minAssaultFactor = propertyMap["minAssaultFactor"] as DoubleArray,
                        seed = propertyMap["seed"] as Long
                )

                val blueAgent = if (blueAgentDetails.text.trim().toLowerCase() == "human")
                    GUIAgent(QueueNextMove())
                else
                    createAgentParamsFromString(blueAgentDetails.text.split("\n")).createAgent("BLUE")
                val redAgent = createAgentParamsFromString(redAgentDetails.text.split("\n")).createAgent("RED")
                val fileAsLines = if (mapNameField.text != "") BufferedReader(FileReader(mapNameField.text)).readLines().joinToString("\n") else ""
                val cityValues = if (fileAsLines == "") emptyMap() else victoryValuesFromJSON(fileAsLines)
                val cityValue = if (cityValues.isEmpty()) 5.0 else 5.0

                val scoreString = "SC|$cityValue|0|1|-0.5|0.1|0|0|-3|0"

                runningThread = Thread {
                    runWithParams(
                            simParams,
                            blueAgent,
                            redAgent,
                            blueScoreFunction = stringToScoreFunction(scoreString, cityValues),
                            redScoreFunction = stringToScoreFunction(scoreString, cityValues),
                            showAgentPlans = agentPlanBox.isSelected,
                            mapFile = mapNameField.text,
                            blueVictoryFunction = if (cityValues.isNotEmpty()) allTargetsConquered(PlayerId.Blue, cityValues) else null
                    )
                }
                runningThread.start()
            }
        }
        val pauseButton = JButton("Pause")
        pauseButton.addActionListener {
            paused = true
        }

        buttonPanel.add(startButton)
        buttonPanel.add(pauseButton)
        val optionPanel = JPanel()
        if (testVersion) optionPanel.add(agentPlanBox)
        setting.add(optionPanel)
        setting.add(buttonPanel)

        displayPanel.add(setting)
        displayPanel.add(agentParameters)
        frame.add(displayPanel)
        frame.title = "Smarter Simulations"
        frame.setBounds(100, 100, 600, parameterNames.size * 30 + 200)
        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        frame.isVisible = true
    }


}



