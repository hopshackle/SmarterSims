package mathFunctions

import evodef.*
import ggi.SimplePlayerInterface
import groundWar.executables.HopshackleSearchSpace
import groundWar.executables.RHEASearchSpace
import kotlin.math.exp
import kotlin.math.pow
import kotlin.random.Random

class Hartmann3Evaluator(val searchSpace: FunctionSearchSpace) : SolutionEvaluator {

    private val a: Array<DoubleArray> = arrayOf(
            doubleArrayOf(3.0, 10.0, 30.0),
            doubleArrayOf(0.1, 10.0, 35.0),
            doubleArrayOf(3.0, 10.0, 30.0),
            doubleArrayOf(0.1, 10.0, 35.0)
    )
    private val c = doubleArrayOf(1.0, 1.2, 3.0, 3.2)
    private val p = arrayOf(
            doubleArrayOf(0.3689, 0.1170, 0.2673),
            doubleArrayOf(0.4699, 0.4387, 0.7470),
            doubleArrayOf(0.1091, 0.8732, 0.5547),
            doubleArrayOf(0.03815, 0.5743, 0.8828)
    )

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

    fun functionValue(x: DoubleArray): Double {
        return (0 until 4).map { i ->
            c[i] * exp(-(0 until 3).map { j -> a[i][j] * (x[j] - p[i][j]).pow(2) }.sum())
        }.sum()
    }

    override fun evaluate(settings: DoubleArray): Double {
        nEvals++
        return if (rnd.nextDouble() < (functionValue(settings) / 4.0)) 1.0 else -1.0
    }
}

fun main(args: Array<String>) {
    HartmannExhaustiveResult(FunctionSearchSpace(args[0].toInt(), args[1].toInt())).calculate()
}

class HartmannExhaustiveResult(val searchSpace: FunctionSearchSpace) {

    // We run through every possible setting in the searchSpace, and run maxGames, logging the average result
    val stateSpaceSize = (0 until searchSpace.nDims()).fold(1, { acc, i -> acc * searchSpace.nValues(i) })

    val allRanges = (0 until searchSpace.nDims()).map { 0 until searchSpace.nValues(it) }

    fun addDimension(start: List<List<Int>>, next: IntRange): List<List<Int>> {
        return start.flatMap { list -> next.map { list + it } }
    }

    val allOptions = allRanges.fold(listOf(emptyList<Int>()), { acc, r -> addDimension(acc, r) })

    fun calculate() {
        val optionScores = allOptions.map {
            val evaluator = Hartmann3Evaluator(searchSpace)
            val params = it.toIntArray()
            //   StatsCollator.clear()
            val perfectScore = evaluator.functionValue(searchSpace.convertSettings(params))
            println("${params.joinToString()} has mean score ${String.format("%.3g", perfectScore)}")
            //   println(StatsCollator.summaryString())
            params to perfectScore
        }

        val sortedScores = optionScores.sortedBy { it.second }.reversed().take(50)
        println("Best scores are " + sortedScores.joinToString("\n") { String.format("%.3g at %s", it.second, it.first.joinToString()) })
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