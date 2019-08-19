package groundWar.executables

import groundWar.*
import utilities.StatsCollator
import java.io.*
import kotlin.random.Random
import kotlin.streams.toList


fun main(args: Array<String>) {
    // args[0] is the number of games to run for each pair
    // args[1] is the directory that contains agentParams to use
    // args[2] is the file that contains intervalParams to use (this is used with a fixed set at the moment, it is not resampled for each game)
    val maxGames = if (args.size > 0) args[0].toInt() else 100
    val fileNames = File(args[1]).walk().maxDepth(1).filterNot { it.isDirectory }.toList()
    val allAgents = fileNames.map {
        println(it)
        val fileAsLines = BufferedReader(FileReader(it)).lines().toList()
        createAgentParamsFromString(fileAsLines)
    }.toList()

    val eventParams = if (args.size > 2) {
        val fileAsLines = BufferedReader(FileReader(args[2])).lines().toList()
        createIntervalParamsFromString(fileAsLines).sampleParams()
    } else null

    val rnd = Random(1)
    val seeds = (0 until maxGames).map { rnd.nextLong() }.toLongArray()
    var agentScores = Array(allAgents.size) { DoubleArray(allAgents.size) { 0.0 } }
    allAgents.withIndex().forEach { (i, agenti) ->
        allAgents.withIndex().forEach { (j, agentj) ->
            StatsCollator.clear()
            runGames(maxGames, agenti.createAgent("BLUE"), agentj.createAgent("RED"), eventParams = eventParams, worldSeeds = seeds)
            agentScores[i][j] += StatsCollator.getStatistics("BLUE_SCORE") / 2.0
            agentScores[j][i] -= StatsCollator.getStatistics("BLUE_SCORE") / 2.0
        }
    }

    val fileWriter = FileWriter("AgentRoundRobin.csv")
    fileWriter.write("," + fileNames.joinToString() { it.nameWithoutExtension } + "\n")
    agentScores.zip(fileNames).forEach { (scores, file) ->
        fileWriter.write(file.nameWithoutExtension + ", " + scores.joinToString() { d -> String.format("%.1f", d) } + "\n")
    }
    fileWriter.close()
}