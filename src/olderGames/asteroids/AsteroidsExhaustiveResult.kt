package olderGames.asteroids

import evodef.*
import ggi.*
import groundWar.*
import groundWar.executables.*
import java.io.BufferedReader
import java.io.FileReader
import java.util.*
import kotlin.streams.toList

fun main(args: Array<String>) {
    val sSpace = RHEASimpleSearchSpace(agentParamsFromCommandLine(args, "baseAgent", default = defaultRHEAAgent),
            fileName = args[0])
    val fileName = args.firstOrNull { it.startsWith("settingsFile") }?.split("=")?.get(1)
    val settings = if (fileName == null) {
        throw AssertionError("No data found for $fileName")
    } else {
        val fileAsLines = BufferedReader(FileReader(fileName)).lines().toList()
        fileAsLines.map { it.split(",").map(String::trim).filterNot { s -> s == "" }.map(String::toInt) }
    }
    AsteroidsExhaustiveResult(sSpace, args[1].toInt(), settings)
}

class AsteroidsExhaustiveResult(val searchSpace: HopshackleSearchSpace<SimplePlayerInterface>,
                                val maxGames: Int,
                                val specificSettingsToTry: List<List<Int>> = emptyList()
) {

    // We run through every possible setting in the searchSpace, and run maxGames, logging the average result
    val allRanges = (0 until searchSpace.nDims()).map { 0 until searchSpace.nValues(it) }

    fun addDimension(start: List<List<Int>>, next: IntRange): List<List<Int>> {
        return start.flatMap { list -> next.map { list + it } }
    }

    val allOptions = if (specificSettingsToTry.isEmpty())
        allRanges.fold(listOf(emptyList<Int>()), { acc, r -> addDimension(acc, r) })
    else
        specificSettingsToTry

    val optionScores = allOptions.map {
        val evaluator = AsteroidsEvaluator(searchSpace, EvolutionLogger())
        val params = it.toIntArray()
        val meanScore = (0..maxGames).map { evaluator.evaluate(params) }.average()
        println("${params.joinToString()} has mean score ${String.format("%.3f", meanScore)}")
        params to meanScore
    }
}