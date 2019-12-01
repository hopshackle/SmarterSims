package olderGames.planetWars

import evodef.*
import ggi.*
import groundWar.*
import groundWar.executables.*
import utilities.StatsCollator
import java.io.*
import java.util.*
import kotlin.streams.*

fun main(args: Array<String>) {
    val sSpace = RHEASimpleSearchSpace(agentParamsFromCommandLine(args, "baseAgent", default = defaultRHEAAgent),
            fileName = args[0])
    val opponent = agentParamsFromCommandLine(args, "Agent")
    val fileName = args.firstOrNull { it.startsWith("settingsFile") }?.split("=")?.get(1)
    val settings = if (fileName == null) {
        throw AssertionError("No data found for $fileName")
    } else {
        val fileAsLines = BufferedReader(FileReader(fileName)).lines().toList()
        fileAsLines.map { it.split(",").map(String::trim).filterNot { s -> s == "" }.map(String::toInt) }
    }
    PlanetWarExhaustiveResult(sSpace, opponent, args[1].toInt(), settings)
}

class PlanetWarExhaustiveResult(val searchSpace: HopshackleSearchSpace<SimplePlayerInterface>,
                                val opponentParams: AgentParams, val maxGames: Int,
                                val specificSettingsToTry: List<List<Int>> = emptyList()
) {


    // We run through every possible setting in the searchSpace, and run maxGames, logging the average result

    val allRanges = (0 until searchSpace.nDims()).map { 0 until searchSpace.nValues(it) }

    fun addDimension(start: List<List<Int>>, next: IntRange): List<List<Int>> {
        return start.flatMap { list -> next.map { list + it } }
    }

    val allOptions = if (specificSettingsToTry.isEmpty()) {
        allRanges.fold(listOf(emptyList<Int>()), { acc, r -> addDimension(acc, r) })
    } else {
        specificSettingsToTry
    }

    val optionScores = allOptions.map {
        val evaluator = PlanetWarEvaluator(searchSpace, EvolutionLogger(), opponentParams)
        val params = it.toIntArray()
        val startTime = System.currentTimeMillis()
        //   StatsCollator.clear()
        val meanScore = (0..maxGames).map { evaluator.evaluate(params) }.average()
        println("${params.joinToString()} has mean score ${String.format("%.3f", meanScore)}, taking ${(System.currentTimeMillis() - startTime) / 1000} seconds")
        //   println(StatsCollator.summaryString())
        params to meanScore
    }
}