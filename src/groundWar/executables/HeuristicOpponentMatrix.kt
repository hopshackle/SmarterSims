package groundWar.executables

import groundWar.*
import intervals.interval
import utilities.StatsCollator
import java.io.*
import kotlin.streams.toList


fun main(args: Array<String>) {

    // args[0] is the number of games to run
    // args[1], args[2] are the files that contains agentParams to use for Blue and Red respectively
    // args[3] is the file that contains intervalParams to use (this is used with a fixed set at the moment, it is not resampled for each game)
    // args[4] is the granularity to use in the gird (the number of points on each axis)
    // args[5] is the Offence Interval, args[6] is the Defense Interval to use in the opponent model for the Blue player
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

    val gridSize = args[4].toInt()
    val offenseInterval = interval(args[5]).sampleGrid(gridSize)
    val defenseInterval = interval(args[6]).sampleGrid(gridSize)
    val blueScoreFunction = stringToScoreFunction(args.firstOrNull { it.startsWith("SCB") })
    val redScoreFunction = stringToScoreFunction(args.firstOrNull { it.startsWith("SCR") })

    val fortVictory = args.contains("fortVictory")
    val mapOverride = args.find { it.startsWith("map=") }?.substring(4) ?: ""

    for (offense in offenseInterval) {
        for (defence in defenseInterval) {
            val override = mapOf("oppAttack" to offense, "oppDefense" to defence)
            val blueAgent = agentParams1.createAgent("BLUE", override)
            StatsCollator.clear()
            runGames(maxGames, blueAgent, agentParams2.createAgent("RED"), intervalParams, logAllResults = false,
                    scoreFunctions = arrayOf(blueScoreFunction, redScoreFunction), mapOverride = mapOverride, fortVictory = fortVictory)
            println(String.format("O: %.2f, D: %.2f, Blue wins: %.1f%%", offense, defence, 100.0 * StatsCollator.getStatistics("BLUE_wins")))
        }
    }

}