package mathFunctions

import evodef.*
import ntbea.*
import utilities.StatsCollator
import kotlin.AssertionError
import kotlin.math.*


fun main(args: Array<String>) {
    val f = when (args[0]) {
        "Hartmann3" -> Hartmann3
        "Hartmann6" -> Hartmann6
        "Branin" -> Branin
        "GoldsteinPrice" -> GoldsteinPrice
        else -> throw AssertionError("Invalid first argument for function " + args[0])
    }
    val type = args[1]
    val iterations = args[2].split("|")
    val dimensions = args[3].toInt()
    FunctionReport(f).iterations(type, iterations[0].toInt(), iterations[1].toInt(), Pair(f.dimension, dimensions), args)
}

class FunctionReport(val f: NTBEAFunction) {
    fun iterations(type: String, runs: Int, iterPerRun: Int, searchDimensions: Pair<Int, Int>, args: Array<String>) {
        // here we want to run ParameterSearch several times (runs), and then
        // for each run we keep the final best sampled point...and its actual value under the function
        // We can then report the basic stats for imprecision and also actual value

        val searchSpace = FunctionSearchSpace(searchDimensions.first, searchDimensions.second)
        val kExplore = (args.find { it.startsWith("kExplore=") }?.split("=")?.get(1) ?: "100.0").toDouble()
        val minWeight = (args.find { it.startsWith("minWeight=") }?.split("=")?.get(1) ?: "0.0").toDouble()
        val T = (args.find { it.startsWith("T=") }?.split("=")?.get(1) ?: "30").toInt()
        val neighbourhood: Double = args.find { it.startsWith("hood=") }?.split("=")?.get(1)?.toDouble()
                ?: min(50.0, searchDimensions.second.toDouble().pow(searchDimensions.first) * 0.01)

        val weightFunction: (Int) -> Double = when (type) {
            "EXP" -> { visits: Int -> 1.0 - exp(-visits.toDouble() / T) }
            "LIN" -> { visits: Int -> min(visits.toDouble() / T, 1.0) }
            "INV" -> { visits: Int -> 1.0 - T / (T + visits.toDouble()) }
            "SQRT" -> { visits: Int -> 1.0 - sqrt(T / (T + visits.toDouble())) }
            else -> { visits: Int -> 1.0 - exp(-visits.toDouble() / T) }
            // we default to exponential
        }

        val landscapeModel: LandscapeModel = when (type) {
            "STD" -> NTupleSystem(searchSpace)
            else -> NTupleSystemExp(searchSpace, T, minWeight, weightFunction, weightExplore = false, exploreWithSqrt = false)
        }

        val use3Tuples = args.contains("useThreeTuples")
        if (landscapeModel is NTupleSystem) {
            landscapeModel.use3Tuple = use3Tuples
            landscapeModel.addTuples()
        }

        val searchFramework: EvoAlg = when (landscapeModel) {
            is NTupleSystem -> NTupleBanditEA(kExplore, neighbourhood.toInt())
            else -> throw AssertionError("Unknown EvoAlg $landscapeModel")
        }
        searchFramework.model = landscapeModel

        val evaluator = FunctionEvaluator(f, searchSpace)

        val fullRecord = mutableMapOf<String, Int>()
        StatsCollator.clear()
        repeat(runs) {
            evaluator.reset()
            landscapeModel.reset()
            searchFramework.runTrial(evaluator, iterPerRun)

            val choice = landscapeModel.bestOfSampled.map { it.toInt() }.toIntArray()
            fullRecord[choice.joinToString(",")] = fullRecord.getOrDefault(choice.joinToString(","), 0) + 1
            val actualValue = f.functionValue(searchSpace.convertSettings(choice))
            val predictedValue = landscapeModel.getMeanEstimate(landscapeModel.bestOfSampled) ?: 0.0
            StatsCollator.addStatistics("ActualValue", actualValue)
            StatsCollator.addStatistics("Delta", predictedValue - actualValue)
   //         println("Current best sampled point (using mean estimate): " + landscapeModel.bestOfSampled.joinToString() +
   //                 String.format(", %.3g", landscapeModel.getMeanEstimate(landscapeModel.bestOfSampled)))
        }

        println(StatsCollator.summaryString())
        val orderedByCount = fullRecord.toList().sortedBy { it.second }.reversed().take(10)
        println("10 most popular choices : \n" + orderedByCount.joinToString("\n") {
            String.format("\t%s - %d", it.first, it.second)
        })
    }
}


