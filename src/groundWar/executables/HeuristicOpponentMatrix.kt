package groundWar.executables

import groundWar.*
import intervals.interval
import utilities.StatsCollator
import java.io.*
import kotlin.streams.toList


fun main(args: Array<String>) {

    val helpText = """
            args[0] is the number of games to run
            args[1], args[2] are the files that contains agentParams to use for Blue and Red respectively
            args[3] is the file that contains intervalParams to use (this is used with a fixed set at the moment, it is not resampled for each game)
            args[4] is the granularity to use in the grid (the number of points on each axis)
            args[5] is the Offence Interval, args[6] is the Defense Interval to use in the opponent model for the Blue player
            om=true/false specifies whether we are using O/D grid for the Opponent Model of Blue (true, default)
                or using the O/D grid to define the actual Opponent (i.e. the Red player - false)
    """.trimIndent()

    if (args.contains("-h")) println(helpText)
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
    val opponentModelChange = args.find { it.startsWith("om=") }?.split("=")?.get(1)?.toBoolean() ?: true

    val gridSize = args[4].toInt()
    val offenseInterval = interval(args[5]).sampleGrid(gridSize)
    val defenseInterval = interval(args[6]).sampleGrid(gridSize)
    val blueScoreFunction = stringToScoreFunction(args.firstOrNull { it.startsWith("SCB") })
    val redScoreFunction = stringToScoreFunction(args.firstOrNull { it.startsWith("SCR") })

    val fortVictory = args.contains("fortVictory")
    val mapOverride = args.find { it.startsWith("map=") }?.substring(4) ?: ""
    val baseParams = agentParams2.params
            .filterNot { it.contains("attack:") }
            .filterNot { it.contains("defence:") }
            .joinToString()
    for (offense in offenseInterval) {
        for (defence in defenseInterval) {
            val overrideBlue = if (opponentModelChange) mapOf("oppAttack" to offense, "oppDefense" to defence) else mapOf()
            val blueAgent = agentParams1.createAgent("BLUE", overrideBlue)
            val redAgent = if (opponentModelChange)
                agentParams2.createAgent("RED")
            else
                agentParams2.copy(algoParams = baseParams + String.format(", attack:%.1f, defence:%.1f", offense, defence)).createAgent("RED")
            StatsCollator.clear()
            runGames(maxGames, blueAgent, redAgent, intervalParams, logAllResults = false,
                    scoreFunctions = arrayOf(blueScoreFunction, redScoreFunction), mapOverride = mapOverride, fortVictory = fortVictory)
            println(String.format("O: %.2f, D: %.2f, Blue wins: %.1f%%", offense.toFloat(), defence.toFloat(), 100.0 * StatsCollator.getStatistics("BLUE_wins")))
        }
    }

}