package games.eventqueuegame

import java.awt.*
import javax.swing.*
import kotlin.reflect.full.memberProperties

fun main(args: Array<String>) {
    val view = ControlView()
    view.create(JFrame())
}

class ControlView {

    val params = EventGameParams()
    val propertyMap: MutableMap<String, Any?> = EventGameParams::class.memberProperties.map { it.name to it.get(params) }.toMap().toMutableMap()
    val parameterNames = listOf("nAttempts", "minConnections", "maxDistance", "width", "height", "startingForce", "lanchesterCoeff", "lanchesterExp",
            "percentFort", "fortAttackerDivisor", "fortDefenderExpBonus", "fogOfWar", "fogStrengthAssumption", "speed",
            "OODALoop", "minAssaultFactor", "seed")
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
        val setting = JPanel(GridLayout(0, 1))
        parameterNames.forEach {
            setting.add(ParameterSetting(it, propertyMap))
            //ParameterSetting(it, default.getOrElse(it, ""))
        }

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
                        percentFort = propertyMap["percentFort"] as Double,
                        fortAttackerDivisor = propertyMap["fortAttackerDivisor"] as Double,
                        fortDefenderExpBonus = propertyMap["fortDefenderExpBonus"] as Double,
                        fogOfWar = propertyMap["fogOfWar"] as Boolean,
                        fogStrengthAssumption = propertyMap["fogStrengthAssumption"] as DoubleArray,
                        speed = propertyMap["speed"] as DoubleArray,
                        OODALoop = propertyMap["OODALoop"] as IntArray,
                        minAssaultFactor = propertyMap["minAssaultFactor"] as DoubleArray,
                        seed = propertyMap["seed"] as Long
                )
                runningThread = Thread { runWithParams(simParams) }
                runningThread.start()
            }
        }
        val pauseButton = JButton("Pause")
        pauseButton.addActionListener{
            paused = true
        }

        buttonPanel.add(startButton)
        buttonPanel.add(pauseButton)
        setting.add(buttonPanel)

        frame.add(setting)
        frame.title = "Smarter Simulations"
        frame.setBounds(100, 100, 300, parameterNames.size * 30 + 100)
        frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        frame.isVisible = true
    }


}



