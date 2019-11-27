package mathFunctions

import evodef.*
import kotlin.AssertionError
import kotlin.random.Random

class FunctionEvaluator(val f: NTBEAFunction, val searchSpace: FunctionSearchSpace) : SolutionEvaluator {

    private val rnd = Random(System.currentTimeMillis())
    override fun optimalFound() = false

    override fun optimalIfKnown() = null

    override fun logger() = EvolutionLogger()

    var nEvals = 0

    override fun searchSpace() = searchSpace

    override fun reset() {
        nEvals = 0
    }

    override fun nEvals() = nEvals
    override fun evaluate(settings: IntArray): Double {
        return evaluate(searchSpace.convertSettings(settings))
    }

    override fun evaluate(settings: DoubleArray): Double {
        nEvals++
        return if (rnd.nextDouble() < f.functionValue(settings)) 1.0 else -1.0
    }
}

fun main(args: Array<String>) {
    val f = when (args[0]) {
        "Hartmann3" -> Hartmann3
        "Hartmann6" -> Hartmann6
        "Branin" -> Branin
        "GoldsteinPrice" -> GoldsteinPrice
        else -> throw AssertionError("Unknown function ${args[0]}")
    }
    FunctionExhaustiveResult(f, FunctionSearchSpace(f.dimension, args[1].toInt())).calculate()
}

class FunctionExhaustiveResult(val f: NTBEAFunction, val searchSpace: FunctionSearchSpace) {

    // We run through every possible setting in the searchSpace, and run maxGames, logging the average result
    val stateSpaceSize = (0 until searchSpace.nDims()).fold(1, { acc, i -> acc * searchSpace.nValues(i) })

    val allRanges = (0 until searchSpace.nDims()).map { 0 until searchSpace.nValues(it) }

    fun addDimension(start: List<List<Int>>, next: IntRange): List<List<Int>> {
        return start.flatMap { list -> next.map { list + it } }
    }

    val allOptions = allRanges.fold(listOf(emptyList<Int>()), { acc, r -> addDimension(acc, r) })

    fun calculate() {
        val optionScores = allOptions.map {
            val evaluator = FunctionEvaluator(f, searchSpace)
            val params = it.toIntArray()
            //   StatsCollator.clear()
            val perfectScore = f.functionValue(searchSpace.convertSettings(params))
            println("${params.joinToString()} has mean score ${String.format("%.3g", perfectScore)}")
            //   println(StatsCollator.summaryString())
            params to perfectScore
        }

        val sortedScores = optionScores.sortedBy { it.second }.reversed().take(50)
        println("Best scores are : \n" + sortedScores.joinToString("\n") { String.format("\t%.3g at %s", it.second, it.first.joinToString()) })
    }
}

class FunctionSearchSpace(val dimensions: Int, val valuesPerDimension: Int) : SearchSpace {

    val increment = 1.0 / valuesPerDimension
    override fun nDims(): Int {
        return dimensions
    }

    override fun nValues(i: Int): Int {
        return valuesPerDimension
    }

    override fun name(i: Int): String {
        return ""
    }

    override fun value(i: Int, j: Int): Double {
        return j * increment
    }

    fun convertSettings(settings: IntArray): DoubleArray {
        return settings.withIndex().map { value(it.index, it.value) }.toDoubleArray()
    }

}