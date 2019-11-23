package olderGames.planetWars

import evodef.*
import ggi.*
import groundWar.*
import groundWar.executables.*
import java.util.*

fun main(args: Array<String>) {
    val sSpace = RHEASimpleSearchSpace(agentParamsFromCommandLine(args, "baseAgent", default = defaultRHEAAgent),
            fileName = args[0])
    val opponent = agentParamsFromCommandLine(args, "Agent")
    PlanetWarExhaustiveResult(sSpace, opponent, args[1].toInt())
}

class PlanetWarExhaustiveResult(val searchSpace: HopshackleSearchSpace<SimplePlayerInterface>,
                                val opponentParams: AgentParams, val maxGames: Int
) {


    // We run through every possible setting in the searchSpace, and run maxGames, logging the average result
    val stateSpaceSize = (0 until searchSpace.nDims()).fold(1, { acc, i -> acc * searchSpace.nValues(i) })

    val allRanges = (0 until searchSpace.nDims()).map { 0 until searchSpace.nValues(it) }

    fun addDimension(start: List<List<Int>>, next: IntRange): List<List<Int>> {
        return start.flatMap { list -> next.map { list + it } }
    }

    val allOptions = allRanges.fold(listOf(emptyList<Int>()), { acc, r -> addDimension(acc, r) })

    val optionScores = allOptions.map {
        val evaluator = PlanetWarEvaluator(searchSpace, EvolutionLogger(), opponentParams)
        val params = it.toIntArray()
        val meanScore = (0..maxGames).map { evaluator.evaluate(params) }.average()
        println("${params.joinToString()} has mean score ${String.format("%.3f", meanScore)}")
        params to meanScore
    }
}